package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.application.telegram.TelegramApi.TelegramApiException;
import dev.mitryp.telebridge.domain.interfaces.ConfigProvider;
import dev.mitryp.telebridge.utils.Markdown;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Delivers outbound messages in order on a single thread. Service lines arriving within a short window
 * are merged into one message to stay under Telegram's per-group rate limit; 429s are retried after {@code retry_after}.
 */
public final class TelegramSender {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long BATCH_WINDOW_MS = 1000;
    private static final int MAX_BATCH_CHARS = 3000; // Telegram's limit is 4096 after Markdown escaping
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public sealed interface Outbound permits Text, Delete {
    }

    /** {@code onSent} receives the sent message's id. */
    public record Text(String text, Integer replyTo, Integer threadId, String forceReplyPlaceholder,
                       IntConsumer onSent) implements Outbound {
        public static Text service(String text) {
            return new Text(text, null, null, null, null);
        }

        boolean batchable() {
            return replyTo == null && threadId == null && forceReplyPlaceholder == null && onSent == null;
        }
    }

    public record Delete(int messageId) implements Outbound {
    }

    private final BotApi api;
    private final ConfigProvider cfg;
    private final BlockingQueue<Outbound> queue = new LinkedBlockingQueue<>(1000);
    private Outbound carried;
    private Thread thread;
    private volatile boolean stopping;

    public TelegramSender(BotApi api, ConfigProvider cfg) {
        this.api = api;
        this.cfg = cfg;
    }

    public void enqueue(Outbound o) {
        if (!queue.offer(o)) LOGGER.warn("[TeleBridge] Outbound queue full, dropping a message");
    }

    public synchronized void start() {
        if (thread != null) return;
        stopping = false;
        thread = new Thread(this::loop, "TeleBridge-Sender");
        thread.setDaemon(true);
        thread.start();
    }

    /** Delivers what is already queued, waiting at most {@code grace}, then stops. */
    public synchronized void stop(Duration grace) {
        if (thread == null) return;
        stopping = true;
        try {
            thread.join(grace.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            LOGGER.warn("[TeleBridge] Shutting down before all messages were delivered");
            thread.interrupt();
            queue.clear();
        }
        thread = null;
    }

    private void loop() {
        try {
            while (true) {
                Outbound next = carried != null ? carried : queue.poll(200, TimeUnit.MILLISECONDS);
                carried = null;
                if (next == null) {
                    if (stopping) return;
                    continue;
                }
                if (next instanceof Text t && t.batchable()) next = batch(t);
                deliver(next);
            }
        } catch (InterruptedException ignored) {
        }
    }

    private Text batch(Text first) throws InterruptedException {
        StringBuilder text = new StringBuilder(first.text());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BATCH_WINDOW_MS);
        while (true) {
            long left = stopping ? 0 : deadline - System.nanoTime();
            Outbound o = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : queue.poll();
            if (o == null) break;
            if (!(o instanceof Text t && t.batchable()) || text.length() + 1 + t.text().length() > MAX_BATCH_CHARS) {
                carried = o;
                break;
            }
            text.append('\n').append(t.text());
        }
        return Text.service(text.toString());
    }

    private void deliver(Outbound o) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                if (o instanceof Text t) {
                    JsonElement sent = api.call("sendMessage", params(t), TIMEOUT);
                    if (t.onSent() != null) t.onSent().accept(sent.getAsJsonObject().get("message_id").getAsInt());
                } else if (o instanceof Delete d) {
                    api.call("deleteMessage", Map.of(
                            "chat_id", cfg.get().telegramChatId,
                            "message_id", String.valueOf(d.messageId())), TIMEOUT);
                }
                return;
            } catch (TelegramApiException e) {
                if (!e.retryable() || attempt >= MAX_ATTEMPTS) {
                    LOGGER.warn("[TeleBridge] {}", e.getMessage());
                    return;
                }
                Thread.sleep(e.retryAfterSeconds > 0 ? e.retryAfterSeconds * 1000L : 2000L * attempt);
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    LOGGER.warn("[TeleBridge] {}", e.getMessage());
                    return;
                }
                Thread.sleep(2000L * attempt);
            }
        }
    }

    private Map<String, String> params(Text t) {
        var c = cfg.get();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("chat_id", c.telegramChatId);
        p.put("text", c.telegramUseMarkdownV2 ? Markdown.escapeV2ServiceAware(t.text()) : t.text());
        p.put("disable_web_page_preview", "true");
        if (c.telegramUseMarkdownV2) p.put("parse_mode", "MarkdownV2");
        if (t.replyTo() != null) {
            p.put("reply_to_message_id", String.valueOf(t.replyTo()));
            p.put("allow_sending_without_reply", "true");
        }
        if (t.threadId() != null) p.put("message_thread_id", String.valueOf(t.threadId()));
        if (t.forceReplyPlaceholder() != null) {
            JsonObject markup = new JsonObject();
            markup.addProperty("force_reply", true);
            markup.addProperty("selective", true);
            markup.addProperty("input_field_placeholder", t.forceReplyPlaceholder());
            p.put("reply_markup", markup.toString());
            p.put("disable_notification", "true");
        }
        return p;
    }
}
