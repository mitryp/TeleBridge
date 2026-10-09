package dev.mitryp.telebridge.application.telegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mitryp.telebridge.application.telegram.TelegramApi.TelegramApiException;
import dev.mitryp.telebridge.application.telegram.TelegramSender.Delete;
import dev.mitryp.telebridge.application.telegram.TelegramSender.Text;
import dev.mitryp.telebridge.testing.FakeBotApi;
import dev.mitryp.telebridge.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramSenderTest {
    private final FakeBotApi api = new FakeBotApi();

    /** Queues everything before starting, so batching is deterministic, then drains. */
    private void deliver(boolean markdown, TelegramSender.Outbound... items) {
        TelegramSender sender = new TelegramSender(api, () -> Fixtures.config(markdown));
        for (var o : items) sender.enqueue(o);
        sender.start();
        sender.stop(Duration.ofSeconds(10));
    }

    private void deliver(TelegramSender.Outbound... items) {
        deliver(false, items);
    }

    private List<String> sentTexts() {
        return api.calls("sendMessage").stream().map(c -> c.params().get("text")).toList();
    }

    @Test
    void mergesQueuedServiceLines() {
        deliver(Text.service("a"), Text.service("b"), Text.service("c"));
        assertEquals(List.of("a\nb\nc"), sentTexts());
    }

    @Test
    void keepsOrderAroundReplies() {
        deliver(Text.service("a"), new Text("reply", 7, null, null, null), Text.service("b"));
        assertEquals(List.of("a", "reply", "b"), sentTexts());
        assertEquals("7", api.calls("sendMessage").get(1).params().get("reply_to_message_id"));
    }

    @Test
    void splitsBatchesAtSizeLimit() {
        String line = "x".repeat(1000);
        deliver(Text.service(line), Text.service(line), Text.service(line), Text.service(line));
        List<String> texts = sentTexts();
        assertEquals(2, texts.size());
        assertTrue(texts.stream().allMatch(t -> t.length() <= 3000));
        assertEquals(4 * 1000, texts.stream().mapToInt(t -> t.replace("\n", "").length()).sum());
    }

    @Test
    void retriesAfterRateLimit() {
        api.then(() -> {
            throw new TelegramApiException("sendMessage", 429, "Too Many Requests", 1);
        });
        deliver(Text.service("a"));
        assertEquals(List.of("a", "a"), sentTexts());
    }

    @Test
    void dropsMessageOnClientErrorAndContinues() {
        api.then(() -> {
            throw new TelegramApiException("sendMessage", 400, "Bad Request", 0);
        });
        deliver(new Text("bad", 1, null, null, null), new Text("good", 2, null, null, null));
        assertEquals(List.of("bad", "good"), sentTexts());
    }

    @Test
    void promptCarriesForceReplyAndReportsItsId() {
        api.then(() -> JsonParser.parseString("{\"message_id\": 42}"));
        List<Integer> ids = new ArrayList<>();
        deliver(new Text("What?", 5, null, "hint", ids::add));

        assertEquals(List.of(42), ids);
        JsonObject markup = JsonParser.parseString(api.calls.get(0).params().get("reply_markup")).getAsJsonObject();
        assertTrue(markup.get("force_reply").getAsBoolean());
        assertTrue(markup.get("selective").getAsBoolean());
        assertEquals("hint", markup.get("input_field_placeholder").getAsString());
    }

    @Test
    void deletesMessages() {
        deliver(new Delete(99));
        assertEquals("99", api.calls("deleteMessage").get(0).params().get("message_id"));
        assertEquals(Fixtures.CHAT_ID, api.calls("deleteMessage").get(0).params().get("chat_id"));
    }

    @Test
    void escapesMarkdownPerLineKeepingQuotes() {
        deliver(true, Text.service("> Steve joined the game"), Text.service("<Steve> 1+1=2!"));
        assertEquals(List.of("> Steve joined the game\n<Steve\\> 1\\+1\\=2\\!"), sentTexts());
        assertEquals("MarkdownV2", api.calls.get(0).params().get("parse_mode"));
    }
}
