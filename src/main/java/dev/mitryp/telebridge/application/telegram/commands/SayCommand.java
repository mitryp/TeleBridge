package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.application.telegram.PendingPrompts;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;

public final class SayCommand implements TelegramCommand {
    private final MinecraftBridge mc;
    private final NameResolver names;
    private final PendingPrompts prompts;

    public SayCommand(MinecraftBridge mc, NameResolver names, PendingPrompts prompts) {
        this.mc = mc;
        this.names = names;
        this.prompts = prompts;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        if (args.isEmpty()) {
            prompts.ask(in, "What do you want to say?", "Message to Minecraft", answer -> say(answer, answer.text));
            return;
        }
        say(in, args);
    }

    private void say(TelegramInboundMessage from, String text) {
        mc.broadcast("[" + names.resolveEffective(from) + "] " + text);
    }
}
