package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.testing.FakeGateway;
import dev.mitryp.telebridge.testing.FakeMinecraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static dev.mitryp.telebridge.testing.Fixtures.ADMIN_ID;
import static dev.mitryp.telebridge.testing.Fixtures.message;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandsTest {
    private final FakeGateway tg = new FakeGateway();
    private final FakeMinecraft mc = new FakeMinecraft();

    private void run(ServerCommand command, String args) {
        command.handle(args, message(ADMIN_ID, "admin", 1, "/x " + args));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Steve                 | kick Steve",
            "Steve griefing again  | kick Steve griefing again",
    })
    void kickBuildsCommand(String args, String expected) {
        run(ServerCommand.kick(mc, tg), args.strip());
        assertEquals(List.of(expected), mc.commands);
    }

    @Test
    void kickReasonIsKeptOnOneLine() {
        run(ServerCommand.kick(mc, tg), "Steve bye\nnow");
        assertEquals(List.of("kick Steve bye now"), mc.commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "@a", "@e[type=player]", "Steve;op", "name_that_is_far_too_long"})
    void kickRejectsSelectorsAndInvalidNames(String args) {
        run(ServerCommand.kick(mc, tg), args);
        assertTrue(mc.commands.isEmpty());
        assertEquals("Usage: /kick <player> [reason]", tg.lastReply());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "list          | keyauth list",
            "reset Steve   | keyauth reset Steve",
    })
    void keyauthBuildsCommand(String args, String expected) {
        run(ServerCommand.keyauth(mc, tg), args.strip());
        assertEquals(List.of(expected), mc.commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "reset", "reset @a", "list extra", "reset Steve extra", "delete Steve"})
    void keyauthRejectsAnythingElse(String args) {
        run(ServerCommand.keyauth(mc, tg), args);
        assertTrue(mc.commands.isEmpty());
    }

    @Test
    void saveTakesNoArguments() {
        run(ServerCommand.save(mc, tg), "now");
        assertTrue(mc.commands.isEmpty());
        run(ServerCommand.save(mc, tg), "");
        assertEquals(List.of("save-all"), mc.commands);
    }

    @Test
    void repliesWithCommandOutput() {
        mc.commandOutput = List.of("Saving the game", "Saved the game");
        run(ServerCommand.save(mc, tg), "");
        assertEquals("Saving the game\nSaved the game", tg.lastReply());
    }

    @Test
    void silentCommandRepliesDone() {
        run(ServerCommand.save(mc, tg), "");
        assertEquals("Done.", tg.lastReply());
    }

    @Test
    void restartNeedsConfirmation() {
        var restart = new RestartCommand(mc, tg);
        restart.handle("", message(ADMIN_ID, "admin", 1, "/restart"));
        assertTrue(mc.broadcasts.isEmpty());
        assertEquals("This restarts the server for everyone. Send /restart confirm to proceed.", tg.lastReply());
    }

    @Test
    void restartWarnsPlayersOnce() {
        var restart = new RestartCommand(mc, tg);
        restart.handle("confirm", message(ADMIN_ID, "admin", 1, "/restart confirm"));
        restart.handle("confirm", message(ADMIN_ID, "admin", 2, "/restart confirm"));

        assertEquals(List.of("Server restarting in 10 seconds (requested by @admin via Telegram)"), mc.broadcasts);
        assertEquals("A restart is already scheduled.", tg.lastReply());
    }

    @ParameterizedTest
    @CsvSource({"12.5, 'TPS 20.0, MSPT 12.5, 2 online'", "100, 'TPS 10.0, MSPT 100.0, 2 online'"})
    void tpsIsCappedAtTwenty(double mspt, String expected) {
        mc.tickMs = mspt;
        mc.online = List.of("a", "b");
        new TpsCommand(mc, tg).handle("", message(ADMIN_ID, "admin", 1, "/tps"));
        assertEquals(expected, tg.lastReply());
    }
}
