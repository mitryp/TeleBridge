package dev.mitryp.telebridge.domain.models;

@SuppressWarnings("ClassCanBeRecord")
public final class TelegramInboundMessage {
    public final String text;
    public final String tgUsernameOrNull;
    public final String displayName;
    public final long userId;
    public final int messageId;
    public final Integer threadId;
    public final Integer replyToMessageId;
    /** Unix seconds. */
    public final long date;

    public TelegramInboundMessage(String text, String tgUsernameOrNull, String displayName, long userId,
                                  int messageId, Integer threadId, Integer replyToMessageId, long date) {
        this.text = text;
        this.tgUsernameOrNull = tgUsernameOrNull;
        this.displayName = displayName;
        this.userId = userId;
        this.messageId = messageId;
        this.threadId = threadId;
        this.replyToMessageId = replyToMessageId;
        this.date = date;
    }
}
