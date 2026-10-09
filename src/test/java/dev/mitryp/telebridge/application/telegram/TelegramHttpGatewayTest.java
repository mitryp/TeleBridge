package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonParser;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.testing.FakeBotApi;
import dev.mitryp.telebridge.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelegramHttpGatewayTest {
    private final FakeBotApi api = new FakeBotApi();
    private final TelegramHttpGateway gateway =
            new TelegramHttpGateway(Fixtures::config, api, new TelegramSender(api, Fixtures::config));

    private static String update(int updateId, String chatId, String messageFields) {
        return "{\"update_id\":" + updateId + ",\"message\":{\"chat\":{\"id\":" + chatId + "}," + messageFields + "}}";
    }

    @Test
    void parsesMessagesFromTheBridgedChatOnly() throws Exception {
        String from = "\"from\":{\"id\":77,\"username\":\"steve\",\"first_name\":\"Steve\",\"last_name\":\"S\"}";
        api.then(() -> JsonParser.parseString("[" +
                update(10, Fixtures.CHAT_ID, from + ",\"message_id\":5,\"date\":1700000000,\"text\":\"/say hi\","
                        + "\"message_thread_id\":3,\"reply_to_message\":{\"message_id\":4}") + "," +
                update(11, "-555", from + ",\"message_id\":6,\"date\":1700000000,\"text\":\"elsewhere\"") + "," +
                update(12, Fixtures.CHAT_ID, from + ",\"message_id\":7,\"date\":1700000000,\"sticker\":{}") +
                "]"));

        List<TelegramInboundMessage> got = new ArrayList<>();
        gateway.pollOnce(got::add);

        assertEquals(1, got.size());
        TelegramInboundMessage m = got.get(0);
        assertEquals("/say hi", m.text);
        assertEquals("steve", m.tgUsernameOrNull);
        assertEquals("Steve S", m.displayName);
        assertEquals(77, m.userId);
        assertEquals(5, m.messageId);
        assertEquals(3, m.threadId);
        assertEquals(4, m.replyToMessageId);
        assertEquals(1700000000L, m.date);
    }

    @Test
    void acknowledgesProcessedUpdatesOnNextPoll() throws Exception {
        api.then(() -> JsonParser.parseString("[" + update(41, "-555", "\"message_id\":1,\"date\":1,\"text\":\"x\"") + "]"))
                .then(() -> JsonParser.parseString("[]"));

        gateway.pollOnce(m -> {
        });
        gateway.pollOnce(m -> {
        });

        var polls = api.calls("getUpdates");
        assertNull(polls.get(0).params().get("offset"));
        assertEquals("42", polls.get(1).params().get("offset"));
    }

    @Test
    void cachesBotUsername() throws Exception {
        api.then(() -> JsonParser.parseString("{\"username\":\"bridge_bot\"}"));
        assertEquals("bridge_bot", gateway.botUsername());
        assertEquals("bridge_bot", gateway.botUsername());
        assertEquals(1, api.calls("getMe").size());
    }
}
