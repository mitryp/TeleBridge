package dev.mitryp.telebridge.domain.interfaces;

import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface TelegramGateway {
    void sendService(String plainText);

    void sendReply(String plainText, Integer replyMessageId, Integer threadId);

    /** Sends a force-reply prompt; {@code onSent} receives the prompt's message id. */
    void sendPrompt(String plainText, String placeholder, Integer replyMessageId, Integer threadId, IntConsumer onSent);

    void delete(int messageId);

    String botUsername() throws IOException, InterruptedException;

    /** Long-poll Telegram and deliver each update's text (if any) to the consumer. */
    void pollOnce(Consumer<TelegramInboundMessage> consumer) throws IOException, InterruptedException;
}
