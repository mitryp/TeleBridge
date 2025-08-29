package dev.mitryp.telebridge;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
        if (!Config.telegramEnabled || !Config.serviceStartStop) return;
        sendService("> Server starting");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        if (!Config.telegramEnabled || !Config.serviceStartStop) return;
        sendService("> Server stopping");
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
