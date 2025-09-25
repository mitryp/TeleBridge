package dev.mitryp.telebridge.domain.interfaces;

import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.function.Consumer;

public interface TelegramGateway {
    void sendService(String plainText);

    void sendReply(String plainText, Integer replyMessageId, Integer threadId);

    /** Long-poll Telegram and deliver each update's text (if any) to the consumer. */
    void pollOnce(Consumer<TelegramInboundMessage> consumer) throws Exception;
}
