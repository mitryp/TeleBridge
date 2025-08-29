package dev.mitryp.telebridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Telebridge.MODID)
public class Telebridge {
    public static final String MODID = "telebridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    // small executor for HTTP posts (off the game thread)
    private static final ExecutorService TELE_EXEC = new ThreadPoolExecutor(
            1, 3, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256),
            r -> {
                Thread t = new Thread(r, "TeleBridge-HTTP");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private static final AtomicBoolean POLLER_RUNNING = new AtomicBoolean(false);
    private static Thread pollerThread;

    public Telebridge() {
        // Register config (Forge will create config/telebridge-common.toml)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Subscribe to game events
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[TeleBridge] Loaded. Telegram bridge {}.",
                Config.telegramEnabled ? "ENABLED" : "DISABLED");
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!Config.telegramEnabled || Config.telegramBotToken.isBlank() || Config.telegramChatId.isBlank()) return;

        String playerName = event.getPlayer().getName().getString();
        String rawMsg = event.getMessage().getString();

        String line = "<" + playerName + "> " + rawMsg;
        String text = Config.telegramUseMarkdownV2 ? escapeMarkdownV2(line) : line;

        TELE_EXEC.submit(() -> {
            try {
                sendTelegram(text, Config.telegramUseMarkdownV2 ? "MarkdownV2" : null);
            } catch (Exception e) {
                logException(e);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Config.telegramEnabled || !Config.serviceJoinQuit) return;
        String name = event.getEntity().getName().getString();
        sendService("> " + name + " joined the game");
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!Config.telegramEnabled || !Config.serviceJoinQuit) return;
        String name = event.getEntity().getName().getString();
        sendService("> " + name + " left the game");
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!Config.telegramEnabled || !Config.serviceDeaths) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Try to use the same death message the server would broadcast
        String deathMsg = sp.getCombatTracker().getDeathMessage().getString();
        if (deathMsg.isBlank()) {
            String name = sp.getName().getString();
            String cause = event.getSource() != null ? event.getSource().getMsgId() : "unknown";
            deathMsg = name + " died (" + cause + ")";
        }
        sendService("> " + deathMsg);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        if (Config.telegramEnabled && Config.serviceStartStop) {
            sendService("> Server starting");
        }

        if (Config.inboundEnabled && pollerThread == null) {
            POLLER_RUNNING.set(true);
            pollerThread = new Thread(Telebridge::runPoller, "TeleBridge-Poller");
            pollerThread.setDaemon(true);
            pollerThread.start();
            LOGGER.info("[TeleBridge] Inbound poller started.");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        if (Config.telegramEnabled && Config.serviceStartStop) {
            sendService("> Server stopping");
        }

        POLLER_RUNNING.set(false);
        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }
        LOGGER.info("[TeleBridge] Inbound poller stopped.");
    }

    /* --- Poller loop --- */
    private static void runPoller() {
        long offset = 0; // Telegram update offset
        Gson gson = new Gson();

        while (POLLER_RUNNING.get()) {
            try {
                // build long-poll URL
                HttpURLConnection conn = getHttpURLConnection(offset);

                int code = conn.getResponseCode();
                if (code / 100 == 2) {
                    try (var r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject obj = gson.fromJson(r, JsonObject.class);
                        if (obj != null && obj.has("ok") && obj.get("ok").getAsBoolean() && obj.has("result")) {
                            JsonArray arr = obj.getAsJsonArray("result");
                            for (JsonElement el : arr) {
                                JsonObject up = el.getAsJsonObject();
                                long updateId = up.get("update_id").getAsLong();
                                offset = updateId + 1;

                                if (!up.has("message")) continue;
                                JsonObject msg = up.getAsJsonObject("message");

                                // chat filter
                                JsonObject chat = msg.getAsJsonObject("chat");
                                String chatIdStr = chat.get("id").getAsLong() + "";
                                if (!chatIdStr.equals(Config.telegramChatId)) continue;

                                // text only
                                if (!msg.has("text")) continue;
                                String text = msg.get("text").getAsString();

                                // sender info
                                JsonObject from = msg.getAsJsonObject("from");
                                String tgUsername = from.has("username") ? from.get("username").getAsString() : null;
                                String display = from.has("first_name") ? from.get("first_name").getAsString() : "TG";
                                if (from.has("last_name"))
                                    display = display + " " + from.get("last_name").getAsString();

                                // Command handling: /say
                                String prefix = Config.inboundCmdPrefix == null ? "/" : Config.inboundCmdPrefix;
                                if (text.startsWith(prefix + "say ")) {
                                    String payload = text.substring((prefix + "say ").length()).trim();
                                    if (!payload.isEmpty()) {
                                        // Prefer MC name if linked
                                        String mc = TgLinks.resolveMcFromTg(tgUsername);
                                        String nameForMc = (mc != null) ? mc : (tgUsername != null ? "@" + tgUsername : display);

                                        broadcastToMc("[" + nameForMc + "] " + payload);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // non-2xx -> small backoff
                    Thread.sleep(2000);
                }
                conn.disconnect();
            } catch (InterruptedException ie) {
                // stopping
            } catch (Exception ex) {
                // network hiccup -> brief backoff
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(long offset) throws IOException {
        String url = "https://api.telegram.org/bot" + Config.telegramBotToken +
                "/getUpdates?timeout=" + Config.inboundPollSeconds +
                "&allowed_updates=message";
        if (offset > 0) url += "&offset=" + offset;

        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout((Config.inboundPollSeconds + 5) * 1000);
        return conn;
    }

    private static void broadcastToMc(String message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        });
    }

    /* ------------ Telegram helpers (no extra deps) ------------ */

    private static void sendService(String plainText) {
        if (Config.telegramBotToken.isBlank() || Config.telegramChatId.isBlank()) return;
        String text = Config.telegramUseMarkdownV2 ? escapeMarkdownV2(plainText) : plainText;

        TELE_EXEC.submit(() -> {
            try {
                sendTelegram(text, Config.telegramUseMarkdownV2 ? "MarkdownV2" : null);
            } catch (Exception e) {
                logException(e);
            }
        });
    }

    private static void sendTelegram(String text, String parseMode) throws IOException {
        String url = "https://api.telegram.org/bot" + Config.telegramBotToken + "/sendMessage";

        StringBuilder body = new StringBuilder();
        body.append("chat_id=").append(URLEncoder.encode(Config.telegramChatId, StandardCharsets.UTF_8));
        body.append("&text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        if (parseMode != null) {
            body.append("&parse_mode=").append(URLEncoder.encode(parseMode, StandardCharsets.UTF_8));
            body.append("&disable_web_page_preview=true");
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        try (var os = conn.getOutputStream()) {
            os.write(payload);
        }
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            String err = new String(conn.getErrorStream() != null ? conn.getErrorStream().readAllBytes() : new byte[0], StandardCharsets.UTF_8);
            throw new IOException("Telegram HTTP " + code + " " + err);
        }
        conn.disconnect();
    }

    private static String escapeMarkdownV2(String s) {
        // https://core.telegram.org/bots/api#markdownv2-style
        String specials = "_*[]()~`>#+-=|{}.!";
        StringBuilder out = new StringBuilder(s.length() * 2);
        for (char c : s.toCharArray()) {
            if (specials.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    private static void logException(Exception e) {
        LOGGER.warn("[TeleBridge] Failed to post to Telegram: {}", e.toString());
    }
}
