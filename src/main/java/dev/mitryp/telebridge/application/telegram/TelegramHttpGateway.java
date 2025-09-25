package dev.mitryp.telebridge.application.telegram;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelebridgeConfig;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.utils.Markdown;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class TelegramHttpGateway implements TelegramGateway {
    private final ConfigProvider cfg;
    private final Executor executor;
    private volatile long offset = 0;
    private static final Gson GSON = new Gson();

    public TelegramHttpGateway(ConfigProvider cfg, Executor executor) {
        this.cfg = cfg;
        this.executor = executor;
    }

    @Override
    public void sendService(String plainText) {
        var c = cfg.get();
        if (!c.hasOutbound()) return;
        executor.execute(() -> {
            try {
                sendTelegram(plainText, null, null);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void sendReply(String plainText, Integer replyMessageId, Integer threadId) {
        var c = cfg.get();
        if (!c.hasOutbound()) return;
        executor.execute(() -> {
            try {
                sendTelegram(plainText, replyMessageId, threadId);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void pollOnce(Consumer<TelegramInboundMessage> consumer) throws Exception {
        var c = cfg.get();
        if (!c.inboundEnabled) return;
        System.out.println("Starting single poll");

        String url = "https://api.telegram.org/bot" + c.telegramBotToken +
                "/getUpdates?timeout=" + c.inboundPollSeconds + "&allowed_updates=message" +
                (offset > 0 ? "&offset=" + offset : "");

        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout((c.inboundPollSeconds + 5) * 1000);

        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            conn.disconnect();
            return;
        }

        try (var r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj == null || !obj.has("ok") || !obj.get("ok").getAsBoolean()) return;
            JsonArray arr = obj.getAsJsonArray("result");
            for (JsonElement el : arr) {
                JsonObject up = el.getAsJsonObject();
                offset = up.get("update_id").getAsLong() + 1;
                if (!up.has("message")) continue;

                JsonObject msg = up.getAsJsonObject("message");
                if (!msg.has("text")) continue;

                JsonObject chat = msg.getAsJsonObject("chat");
                String chatIdStr = chat.get("id").getAsLong() + "";
                if (!chatIdStr.equals(c.telegramChatId)) continue;

                String text = msg.get("text").getAsString();
                JsonObject from = msg.getAsJsonObject("from");
                String tgUser = from.has("username") ? from.get("username").getAsString() : null;
                String display = (from.has("first_name") ? from.get("first_name").getAsString() : "TG") +
                        (from.has("last_name") ? (" " + from.get("last_name").getAsString()) : "");
                Integer messageId = msg.get("message_id").getAsInt();
                Integer threadId = (msg.has("message_thread_id") ? msg.get("message_thread_id").getAsInt() : null);

                consumer.accept(new TelegramInboundMessage(text, tgUser, display.trim(), messageId, threadId));
            }
        } finally {
            conn.disconnect();
        }
    }

    private void sendTelegram(String text, Integer replyMessageId, Integer threadId) throws IOException {
        var c = cfg.get();
        String url = "https://api.telegram.org/bot" + c.telegramBotToken + "/sendMessage";
        String payload = buildBody(text, c.telegramUseMarkdownV2, c.telegramChatId, replyMessageId, threadId);

        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            // Swallow in production; could log or throw
        }
        conn.disconnect();
    }

    private static String buildBody(String text, boolean mdV2, String chatId, Integer replyId, Integer threadId) {
        StringBuilder b = new StringBuilder();
        b.append("chat_id=").append(URLEncoder.encode(chatId, StandardCharsets.UTF_8));
        b.append("&text=").append(URLEncoder.encode(mdV2 ? Markdown.escapeV2ServiceAware(text) : text, StandardCharsets.UTF_8));
        if (mdV2) b.append("&parse_mode=MarkdownV2&disable_web_page_preview=true");
        if (replyId != null)
            b.append("&reply_to_message_id=").append(replyId).append("&allow_sending_without_reply=true");
        if (threadId != null) b.append("&message_thread_id=").append(threadId);
        return b.toString();
    }


}
