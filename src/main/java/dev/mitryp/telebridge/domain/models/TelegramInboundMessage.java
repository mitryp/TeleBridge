package dev.mitryp.telebridge.domain.models;

@SuppressWarnings("ClassCanBeRecord")
public final class TelegramInboundMessage {
    public final String text;
    public final String tgUsernameOrNull;
    public final String displayName;
    public final Integer replyMessageId;
    public final Integer threadId;

    public TelegramInboundMessage(String text, String tgUsernameOrNull, String displayName, Integer replyMessageId, Integer threadId) {
        this.text = text;
        this.tgUsernameOrNull = tgUsernameOrNull;
        this.displayName = displayName;
        this.replyMessageId = replyMessageId;
        this.threadId = threadId;
    }
}
