package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Stops the server after a warning; the container's restart policy starts it again. */
public final class RestartCommand implements TelegramCommand {
    private static final int DELAY_SECONDS = 10;

    private final MinecraftBridge mc;
    private final TelegramGateway tg;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    public RestartCommand(MinecraftBridge mc, TelegramGateway tg) {
        this.mc = mc;
        this.tg = tg;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        if (!args.equals("confirm")) {
            reply(in, "This restarts the server for everyone. Send /restart confirm to proceed.");
            return;
        }
        if (scheduled.getAndSet(true)) {
            reply(in, "A restart is already scheduled.");
            return;
        }

        String issuer = ServerCommand.issuer(in);
        mc.broadcast("Server restarting in " + DELAY_SECONDS + " seconds (requested by " + issuer + " via Telegram)");
        reply(in, "Restarting in " + DELAY_SECONDS + " seconds.");
        CompletableFuture.delayedExecutor(DELAY_SECONDS, TimeUnit.SECONDS)
                .execute(() -> mc.runCommand("stop", issuer));
    }

    private void reply(TelegramInboundMessage in, String text) {
        tg.sendReply(text, in.messageId, in.threadId);
    }
}
