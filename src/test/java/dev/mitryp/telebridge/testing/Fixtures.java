package dev.mitryp.telebridge.testing;

import dev.mitryp.telebridge.domain.models.TelebridgeConfig;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.time.Instant;
import java.util.Set;

public final class Fixtures {
    public static final String CHAT_ID = "-1001234567890";
    public static final String BOT_USERNAME = "bridge_bot";
    public static final long ADMIN_ID = 1000;
    public static final long USER_ID = 2000;

    public static TelebridgeConfig config() {
        return config(false);
    }

    public static TelebridgeConfig config(boolean markdown) {
        return new TelebridgeConfig(true, "TOKEN", CHAT_ID, markdown,
                true, true, true, true, true,
                true, 20, "/", Set.of(ADMIN_ID));
    }

    public static TelegramInboundMessage message(long userId, String username, int messageId, String text) {
        return reply(userId, username, messageId, text, null);
    }

    public static TelegramInboundMessage reply(long userId, String username, int messageId, String text, Integer replyTo) {
        return new TelegramInboundMessage(text, username, "Display " + userId, userId, messageId, null, replyTo,
                Instant.now().getEpochSecond());
    }
}
