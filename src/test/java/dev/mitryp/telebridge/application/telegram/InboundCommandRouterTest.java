package dev.mitryp.telebridge.application.telegram;

import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.testing.FakeGateway;
import dev.mitryp.telebridge.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.mitryp.telebridge.testing.Fixtures.ADMIN_ID;
import static dev.mitryp.telebridge.testing.Fixtures.BOT_USERNAME;
import static dev.mitryp.telebridge.testing.Fixtures.USER_ID;
import static dev.mitryp.telebridge.testing.Fixtures.message;
import static dev.mitryp.telebridge.testing.Fixtures.reply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundCommandRouterTest {
    private final FakeGateway tg = new FakeGateway();
    private final PendingPrompts prompts = new PendingPrompts(tg);
    private final List<String> said = new ArrayList<>();
    private final List<String> adminRuns = new ArrayList<>();
    private InboundCommandRouter router;

    @BeforeEach
    void setUp() {
        router = new InboundCommandRouter(prompts, tg, Fixtures::config)
                .register("say", "Say", (args, in) -> said.add(args))
                .register("online", "Online", (args, in) -> said.add("online"))
                .registerAdmin("kick", "Kick", (args, in) -> adminRuns.add(args));
    }

    private void route(TelegramInboundMessage in) {
        router.route(in, BOT_USERNAME);
    }

    @Test
    void passesArgumentsToHandler() {
        route(message(USER_ID, "u", 1, "/say hello  world"));
        assertEquals(List.of("hello  world"), said);
    }

    @Test
    void argumentsMayStartOnNextLine() {
        route(message(USER_ID, "u", 1, "/say\nhello"));
        assertEquals(List.of("hello"), said);
    }

    @Test
    void acceptsOwnBotSuffixCaseInsensitively() {
        route(message(USER_ID, "u", 1, "/online@Bridge_Bot"));
        assertEquals(List.of("online"), said);
    }

    @Test
    void ignoresCommandsForOtherBots() {
        route(message(USER_ID, "u", 1, "/online@other_bot"));
        assertTrue(said.isEmpty());
    }

    @Test
    void ignoresUnknownCommandsAndPlainText() {
        route(message(USER_ID, "u", 1, "/nope"));
        route(message(USER_ID, "u", 2, "just chatting"));
        assertTrue(said.isEmpty());
        assertTrue(tg.replies.isEmpty());
    }

    @Test
    void refusesAdminCommandForOthers() {
        route(message(USER_ID, "u", 1, "/kick Steve"));
        assertTrue(adminRuns.isEmpty());
        assertEquals("This command is for server admins.", tg.lastReply());
    }

    @Test
    void runsAdminCommandForAdmins() {
        route(message(ADMIN_ID, "admin", 1, "/kick Steve"));
        assertEquals(List.of("Steve"), adminRuns);
    }

    @Test
    void menuHidesAdminCommandsUnlessAsked() {
        assertEquals(List.of("say", "online"), router.menu(false).stream().map(InboundCommandRouter.MenuEntry::command).toList());
        assertEquals(List.of("say", "online", "kick"), router.menu(true).stream().map(InboundCommandRouter.MenuEntry::command).toList());
    }

    @Test
    void plainReplyAnswersPrompt() {
        List<String> answers = new ArrayList<>();
        prompts.ask(message(USER_ID, "u", 1, "/say"), "Q?", "hint", a -> answers.add(a.text));
        int promptId = tg.prompts.get(0).id();

        route(reply(USER_ID, "u", 2, "hello", promptId));

        assertEquals(List.of("hello"), answers);
    }

    @Test
    void commandReplyToPromptRunsAsCommand() {
        List<String> answers = new ArrayList<>();
        prompts.ask(message(USER_ID, "u", 1, "/say"), "Q?", "hint", a -> answers.add(a.text));
        int promptId = tg.prompts.get(0).id();

        route(reply(USER_ID, "u", 2, "/online", promptId));

        assertTrue(answers.isEmpty());
        assertEquals(List.of("online"), said);
    }
}
