package dev.mitryp.telebridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    public Telebridge(FMLJavaModLoadingContext context) {
        // Register config (Forge will create config/telebridge-common.toml)
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

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
                sendService(text);
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
                if (code / 100 != 2) {
                    // non-2xx -> small backoff
                    Thread.sleep(2000);
                    conn.disconnect();
                    continue;
                }

                try (var r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = gson.fromJson(r, JsonObject.class);
                    if (obj == null || !obj.has("ok") || !obj.get("ok").getAsBoolean() || !obj.has("result")) {
                        conn.disconnect();
                        continue;
                    }

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

                        int messageId = msg.get("message_id").getAsInt();
                        Integer threadId = (msg.has("message_thread_id") ? msg.get("message_thread_id").getAsInt() : null);

                        handleTelegramCommand(text, tgUsername, display, messageId, threadId);
                    }
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

    // Decide which name to show in MC for a Telegram sender.
    // Prefers linked MC name, then @username, then the display name we got from Telegram.
    private static String resolveEffectiveName(String tgUsernameOrNull, String displayName) {
        String linked = TgLinks.resolveMcFromTg(tgUsernameOrNull);
        if (linked != null && !linked.isBlank()) return linked;
        if (tgUsernameOrNull != null && !tgUsernameOrNull.isBlank()) return "@" + tgUsernameOrNull;
        return displayName != null && !displayName.isBlank() ? displayName : "TG";
    }

    // Centralized command handler for Telegram messages.
    // text = full message text from Telegram
    // displayName = already-built human-readable display name ("First Last", etc.)
    private static void handleTelegramCommand(String text, String tgUsernameOrNull, String displayName, Integer replyMessageId, Integer threadId) {
        // Normalize
        if (text == null || text.isBlank()) return;
        String prefix = (Config.inboundCmdPrefix == null || Config.inboundCmdPrefix.isBlank()) ? "/" : Config.inboundCmdPrefix;

        if (!text.startsWith(prefix)) {
            return; // not a command; ignore (or you could forward plain messages if you want)
        }

        // Simple split: "/cmd args…"
        String withoutPrefix = text.substring(prefix.length()).trim();
        int sp = withoutPrefix.indexOf(' ');
        String cmd = (sp == -1 ? withoutPrefix : withoutPrefix.substring(0, sp)).toLowerCase();
        String args = (sp == -1 ? "" : withoutPrefix.substring(sp + 1)).trim();

        switch (cmd) {
            case "say" -> handleSayCommand(args, tgUsernameOrNull, displayName);
            case "online" -> handleOnlineCommand(replyMessageId, threadId);

            // add new commands here:
            // case "tps" -> { /* compute & broadcast TPS */ }
            // case "list" -> { /* list online players */ }
            // case "whisper" -> { /* /whisper <mcName> <msg> */ }
            // default: (optional) broadcast or ignore unknown commands
            default -> {
                // Optional feedback to MC or Telegram; currently we ignore unknown commands.
            }
        }
    }

    private static void handleSayCommand(String message, String tgUsernameOrNull, String displayName) {
        if (message.isEmpty()) {
            return;
        }

        String nameForMc = resolveEffectiveName(tgUsernameOrNull, displayName);
        broadcastToMc("[" + nameForMc + "] " + message);
    }

    private static void handleOnlineCommand(Integer replyMessageId, Integer threadId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        var players = server.getPlayerList().getPlayers();
        var names = players.stream().map(Player::getName).map(Component::getString).toList();

        var repr = "Current online:\n" +
                String.join("\n", names);

        sendService(repr, replyMessageId, threadId);
    }

    /* ------------ Telegram helpers (no extra deps) ------------ */

    private static void sendService(String plainText, Integer replyMessageId, Integer threadId) {
        if (Config.telegramBotToken.isBlank() || Config.telegramChatId.isBlank()) return;

        TELE_EXEC.submit(() -> {
            try {
                sendTelegram(plainText, Config.telegramUseMarkdownV2 ? "MarkdownV2" : null, replyMessageId, threadId);
            } catch (Exception e) {
                logException(e);
            }
        });
    }

    private static void sendService(String plainText) {
        sendService(plainText, null, null);
    }

    private static void sendTelegram(String text, String parseMode, Integer replyMessageId, Integer threadId) throws IOException {
        String url = "https://api.telegram.org/bot" + Config.telegramBotToken + "/sendMessage";

        StringBuilder body = new StringBuilder();
        body.append("chat_id=").append(URLEncoder.encode(Config.telegramChatId, StandardCharsets.UTF_8));
        body.append("&text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        if (parseMode != null) {
            body.append("&parse_mode=").append(URLEncoder.encode(parseMode, StandardCharsets.UTF_8));
            body.append("&disable_web_page_preview=true");
        }

        if (replyMessageId != null) {
            body.append("&reply_to_message_id=").append(URLEncoder.encode(String.valueOf(replyMessageId), StandardCharsets.UTF_8));
            // Optional: allow sending even if the original was deleted
            body.append("&allow_sending_without_reply=true");
        }

        if (threadId != null) {
            body.append("&message_thread_id=").append(URLEncoder.encode(String.valueOf(threadId), StandardCharsets.UTF_8));
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

        LOGGER.info("Escaped: " + out);

        return out.toString();
    }

    private static void logException(Exception e) {
        LOGGER.warn("[TeleBridge] Failed to post to Telegram: {}", e.toString());
    }
}
