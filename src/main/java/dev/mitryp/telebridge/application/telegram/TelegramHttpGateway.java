package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.mitryp.telebridge.application.telegram.TelegramSender.Delete;
import dev.mitryp.telebridge.application.telegram.TelegramSender.Text;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class TelegramHttpGateway implements TelegramGateway {
    private final ConfigProvider cfg;
    private final TelegramApi api;
    private final TelegramSender sender;
    private volatile long offset = 0;
    private String botUsernameToken;
    private String botUsername;

    public TelegramHttpGateway(ConfigProvider cfg, TelegramApi api, TelegramSender sender) {
        this.cfg = cfg;
        this.api = api;
        this.sender = sender;
    }

    @Override
    public void sendService(String plainText) {
        if (cfg.get().hasOutbound()) sender.enqueue(Text.service(plainText));
    }

    @Override
    public void sendReply(String plainText, Integer replyMessageId, Integer threadId) {
        if (cfg.get().hasOutbound()) sender.enqueue(new Text(plainText, replyMessageId, threadId, null, null));
    }

    @Override
    public void sendPrompt(String plainText, String placeholder, Integer replyMessageId, Integer threadId, IntConsumer onSent) {
        if (cfg.get().hasOutbound()) sender.enqueue(new Text(plainText, replyMessageId, threadId, placeholder, onSent));
    }

    @Override
    public void delete(int messageId) {
        if (cfg.get().hasOutbound()) sender.enqueue(new Delete(messageId));
    }

    @Override
    public synchronized String botUsername() throws IOException, InterruptedException {
        String token = cfg.get().telegramBotToken;
        if (!token.equals(botUsernameToken)) {
            botUsername = api.call("getMe", Map.of(), Duration.ofSeconds(10)).getAsJsonObject().get("username").getAsString();
            botUsernameToken = token;
        }
        return botUsername;
    }

    @Override
    public void pollOnce(Consumer<TelegramInboundMessage> consumer) throws IOException, InterruptedException {
        var c = cfg.get();
        Map<String, String> params = new HashMap<>();
        params.put("timeout", String.valueOf(c.inboundPollSeconds));
        params.put("allowed_updates", "[\"message\"]");
        if (offset > 0) params.put("offset", String.valueOf(offset));

        JsonArray updates = api.call("getUpdates", params, Duration.ofSeconds(c.inboundPollSeconds + 10)).getAsJsonArray();
        for (JsonElement el : updates) {
            JsonObject up = el.getAsJsonObject();
            offset = up.get("update_id").getAsLong() + 1;
            if (!up.has("message")) continue;

            JsonObject msg = up.getAsJsonObject("message");
            if (!msg.has("text") || !msg.has("from")) continue;

            JsonObject chat = msg.getAsJsonObject("chat");
            if (!String.valueOf(chat.get("id").getAsLong()).equals(c.telegramChatId)) continue;

            JsonObject from = msg.getAsJsonObject("from");
            String tgUser = from.has("username") ? from.get("username").getAsString() : null;
            String display = (from.has("first_name") ? from.get("first_name").getAsString() : "TG") +
                    (from.has("last_name") ? (" " + from.get("last_name").getAsString()) : "");
            Integer threadId = msg.has("message_thread_id") ? msg.get("message_thread_id").getAsInt() : null;
            Integer replyTo = msg.has("reply_to_message")
                    ? msg.getAsJsonObject("reply_to_message").get("message_id").getAsInt()
                    : null;

            consumer.accept(new TelegramInboundMessage(
                    msg.get("text").getAsString(), tgUser, display.trim(), from.get("id").getAsLong(),
                    msg.get("message_id").getAsInt(), threadId, replyTo, msg.get("date").getAsLong()));
        }
    }
}
