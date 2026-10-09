package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.interfaces.TelegramGateway;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

public final class SayCommand implements TelegramCommand {
    private final MinecraftBridge mc;
    private final NameResolver names;
    private final TelegramGateway tg;
    private final PendingPrompts prompts;

    public SayCommand(MinecraftBridge mc, NameResolver names, TelegramGateway tg, PendingPrompts prompts) {
        this.mc = mc;
        this.names = names;
        this.tg = tg;
        this.prompts = prompts;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        if (!args.isEmpty()) {
            say(in, args);
            return;
        }
        tg.sendPrompt("What do you want to say?", "Message to Minecraft", in.messageId, in.threadId,
                promptId -> prompts.register(promptId, in.userId, answer -> {
                    tg.delete(promptId);
                    say(answer, answer.text);
                }));
    }

    private void say(TelegramInboundMessage from, String text) {
        mc.broadcast("[" + names.resolveEffective(from.tgUsernameOrNull, from.displayName) + "] " + text);
    }
}
