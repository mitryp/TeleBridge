package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

public final class TpsCommand implements TelegramCommand {
    private final MinecraftBridge mc;
    private final TelegramGateway tg;

    public TpsCommand(MinecraftBridge mc, TelegramGateway tg) {
        this.mc = mc;
        this.tg = tg;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        double mspt = mc.averageTickMs();
        String text = mspt < 0
                ? "Server is not running."
                : String.format("TPS %.1f, MSPT %.1f, %d online", Math.min(20.0, 1000.0 / Math.max(mspt, 1e-3)), mspt, mc.onlineNames().size());
        tg.sendReply(text, in.messageId, in.threadId);
    }
}
