package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.LinkCodes;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.data.repositories.JsonLinkRepository;
import dev.mitryp.telebridge.testing.FakeGateway;
import dev.mitryp.telebridge.testing.FakeMinecraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static dev.mitryp.telebridge.testing.Fixtures.ADMIN_ID;
import static dev.mitryp.telebridge.testing.Fixtures.USER_ID;
import static dev.mitryp.telebridge.testing.Fixtures.message;
import static dev.mitryp.telebridge.testing.Fixtures.reply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkCommandTest {
    @TempDir
    Path dir;

    private final FakeGateway tg = new FakeGateway();
    private final FakeMinecraft mc = new FakeMinecraft();
    private final LinkCodes codes = new LinkCodes();
    private final PendingPrompts prompts = new PendingPrompts(tg);
    private JsonLinkRepository links;
    private LinkCommand command;

    @BeforeEach
    void setUp() {
        links = new JsonLinkRepository(dir.resolve("links.json"));
        command = new LinkCommand(links, codes, mc, tg, prompts);
    }

    @Test
    void validCodeLinksTheSenderAndTellsThePlayer() {
        command.handle(codes.issue("Steve"), message(USER_ID, "steve_tg", 1, "/link"));

        assertEquals("Steve", links.findByTg(USER_ID, null).mcName);
        assertEquals("Linked to Minecraft player Steve.", tg.lastReply());
        assertEquals(List.of(new FakeMinecraft.Tell("Steve", "Linked to Telegram @steve_tg.")), mc.tells);
    }

    @Test
    void wrongCodeIsRejected() {
        codes.issue("Steve");
        command.handle("000000x", message(USER_ID, "u", 1, "/link"));

        assertNull(links.findByMc("Steve"));
        assertEquals("Invalid or expired code. Run /tglink in Minecraft to get a new one.", tg.lastReply());
    }

    @Test
    void alreadyLinkedTelegramAccountMustUnlinkFirst() {
        links.link(USER_ID, "u", "Alex");
        command.handle(codes.issue("Steve"), message(USER_ID, "u", 1, "/link"));

        assertNull(links.findByMc("Steve"));
        assertEquals("You are already linked to Alex. Use /unlink first.", tg.lastReply());
    }

    @Test
    void playerLinkedToAnotherAccountIsNotTakenOver() {
        links.link(ADMIN_ID, "other", "Steve");
        command.handle(codes.issue("Steve"), message(USER_ID, "u", 1, "/link"));

        assertEquals(ADMIN_ID, links.findByMc("Steve").tgUserId);
        assertEquals("Steve is already linked to another Telegram account.", tg.lastReply());
    }

    @Test
    void lockedOutUserCannotRedeemEvenACorrectCode() {
        for (int i = 0; i < 5; i++) command.handle("bad", message(USER_ID, "u", i, "/link"));
        command.handle(codes.issue("Steve"), message(USER_ID, "u", 9, "/link"));

        assertNull(links.findByMc("Steve"));
        assertEquals("Too many wrong codes. Try again in 10 minutes.", tg.lastReply());
    }

    @Test
    void bareLinkPromptsAndTheAnswerIsTheCode() {
        command.handle("", message(USER_ID, "u", 1, "/link"));
        FakeGateway.Prompt prompt = tg.prompts.get(0);
        assertEquals("Enter the code from /tglink in Minecraft", prompt.text());

        prompts.answer(prompt.id(), reply(USER_ID, "u", 2, codes.issue("Steve"), prompt.id()));

        assertEquals("Steve", links.findByTg(USER_ID, null).mcName);
    }
}
