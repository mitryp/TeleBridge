package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.domain.models.TelegramLink;

public final class UnlinkCommand implements TelegramCommand {
    private final LinkRepository links;
    private final TelegramGateway tg;

    public UnlinkCommand(LinkRepository links, TelegramGateway tg) {
        this.links = links;
        this.tg = tg;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        TelegramLink removed = links.unlinkByTg(in.userId, in.tgUsernameOrNull);
        tg.sendReply(removed == null ? "Your Telegram account is not linked." : "Unlinked from " + removed.mcName + ".",
                in.messageId, in.threadId);
    }
}
