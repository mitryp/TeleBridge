package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.data.repositories.JsonLinkRepository;
import dev.mitryp.telebridge.testing.FakeGateway;
import dev.mitryp.telebridge.testing.FakeMinecraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static dev.mitryp.telebridge.testing.Fixtures.USER_ID;
import static dev.mitryp.telebridge.testing.Fixtures.message;
import static dev.mitryp.telebridge.testing.Fixtures.reply;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SayCommandTest {
    @TempDir
    Path dir;

    private final FakeGateway tg = new FakeGateway();
    private final FakeMinecraft mc = new FakeMinecraft();
    private final PendingPrompts prompts = new PendingPrompts(tg);
    private JsonLinkRepository links;
    private SayCommand say;

    @BeforeEach
    void setUp() {
        links = new JsonLinkRepository(dir.resolve("links.json"));
        say = new SayCommand(mc, new NameResolver(links), prompts);
    }

    @Test
    void unlinkedUserSpeaksAsTelegramUsername() {
        say.handle("hi", message(USER_ID, "steve_tg", 1, "/say hi"));
        assertEquals(List.of("[@steve_tg] hi"), mc.broadcasts);
    }

    @Test
    void linkedUserSpeaksAsPlayer() {
        links.link(USER_ID, "steve_tg", "Steve");
        say.handle("hi", message(USER_ID, "steve_tg", 1, "/say hi"));
        assertEquals(List.of("[Steve] hi"), mc.broadcasts);
    }

    @Test
    void userWithoutUsernameSpeaksAsDisplayName() {
        say.handle("hi", message(USER_ID, null, 1, "/say hi"));
        assertEquals(List.of("[Display " + USER_ID + "] hi"), mc.broadcasts);
    }

    @Test
    void bareSayPromptsAndBroadcastsTheAnswer() {
        say.handle("", message(USER_ID, "steve_tg", 1, "/say"));
        FakeGateway.Prompt prompt = tg.prompts.get(0);
        assertEquals("What do you want to say?", prompt.text());

        prompts.answer(prompt.id(), reply(USER_ID, "steve_tg", 2, "hello there", prompt.id()));

        assertEquals(List.of("[@steve_tg] hello there"), mc.broadcasts);
        assertEquals(List.of(prompt.id()), tg.deleted);
    }
}
