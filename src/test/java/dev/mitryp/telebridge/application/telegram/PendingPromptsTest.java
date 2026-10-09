package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.testing.FakeGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.mitryp.telebridge.testing.Fixtures.ADMIN_ID;
import static dev.mitryp.telebridge.testing.Fixtures.USER_ID;
import static dev.mitryp.telebridge.testing.Fixtures.message;
import static dev.mitryp.telebridge.testing.Fixtures.reply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingPromptsTest {
    private final FakeGateway tg = new FakeGateway();
    private final PendingPrompts prompts = new PendingPrompts(tg);
    private final List<String> answers = new ArrayList<>();

    private int ask() {
        prompts.ask(message(USER_ID, "u", 10, "/say"), "What?", "hint", a -> answers.add(a.text));
        return tg.prompts.get(tg.prompts.size() - 1).id();
    }

    @Test
    void promptRepliesToTheRequest() {
        ask();
        FakeGateway.Prompt p = tg.prompts.get(0);
        assertEquals("What?", p.text());
        assertEquals("hint", p.placeholder());
        assertEquals(10, p.replyTo());
    }

    @Test
    void answerIsDeliveredAndPromptDeleted() {
        int id = ask();
        prompts.answer(id, reply(USER_ID, "u", 11, "hi", id));
        assertEquals(List.of("hi"), answers);
        assertEquals(List.of(id), tg.deleted);
    }

    @Test
    void onlyTheRequesterCanAnswer() {
        int id = ask();
        prompts.answer(id, reply(ADMIN_ID, "other", 11, "hijack", id));
        assertTrue(answers.isEmpty());

        prompts.answer(id, reply(USER_ID, "u", 12, "mine", id));
        assertEquals(List.of("mine"), answers);
    }

    @Test
    void promptIsSingleUse() {
        int id = ask();
        prompts.answer(id, reply(USER_ID, "u", 11, "one", id));
        prompts.answer(id, reply(USER_ID, "u", 12, "two", id));
        assertEquals(List.of("one"), answers);
    }

    @Test
    void repliesToOtherMessagesAreIgnored() {
        ask();
        prompts.answer(9999, reply(USER_ID, "u", 11, "hi", 9999));
        assertTrue(answers.isEmpty());
    }
}
