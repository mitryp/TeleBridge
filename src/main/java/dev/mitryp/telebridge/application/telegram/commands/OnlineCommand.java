package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;

import java.util.StringJoiner;

public final class OnlineCommand implements TelegramCommand {
    private final MinecraftBridge mc;
    private final TelegramGateway tg;

    public OnlineCommand(MinecraftBridge mc, TelegramGateway tg) {
        this.mc = mc;
        this.tg = tg;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        var list = mc.onlineNames();
        StringJoiner j = new StringJoiner("\n");
        j.add("Current online:");
        list.forEach(j::add);
        tg.sendReply(j.toString(), in.replyMessageId, in.threadId);
    }
}
