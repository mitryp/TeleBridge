package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.LinkCodes;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.domain.models.TelegramLink;

public final class LinkCommand implements TelegramCommand {
    private final LinkRepository links;
    private final LinkCodes codes;
    private final MinecraftBridge mc;
    private final TelegramGateway tg;
    private final PendingPrompts prompts;

    public LinkCommand(LinkRepository links, LinkCodes codes, MinecraftBridge mc, TelegramGateway tg, PendingPrompts prompts) {
        this.links = links;
        this.codes = codes;
        this.mc = mc;
        this.tg = tg;
        this.prompts = prompts;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        if (args.isEmpty()) {
            prompts.ask(in, "Enter the code from /tglink in Minecraft", "123456", answer -> link(answer, answer.text));
            return;
        }
        link(in, args);
    }

    private void link(TelegramInboundMessage in, String code) {
        TelegramLink existing = links.findByTg(in.userId, in.tgUsernameOrNull);
        if (existing != null) {
            reply(in, "You are already linked to " + existing.mcName + ". Use /unlink first.");
            return;
        }
        if (codes.lockedOut(in.userId)) {
            reply(in, "Too many wrong codes. Try again in 10 minutes.");
            return;
        }

        String mcName = codes.redeem(in.userId, code);
        if (mcName == null) {
            reply(in, "Invalid or expired code. Run /tglink in Minecraft to get a new one.");
            return;
        }
        if (links.findByMc(mcName) != null) {
            reply(in, mcName + " is already linked to another Telegram account.");
            return;
        }

        links.link(in.userId, in.tgUsernameOrNull, mcName);
        TelegramLink link = links.findByMc(mcName);
        reply(in, "Linked to Minecraft player " + mcName + ".");
        mc.tell(mcName, "Linked to Telegram " + link.tgLabel() + ".");
    }

    private void reply(TelegramInboundMessage in, String text) {
        tg.sendReply(text, in.messageId, in.threadId);
    }
}
