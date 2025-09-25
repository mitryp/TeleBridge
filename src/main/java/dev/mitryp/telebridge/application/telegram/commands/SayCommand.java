package dev.mitryp.telebridge.application.telegram.commands;

import dev.mitryp.telebridge.application.services.NameResolver;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import dev.mitryp.telebridge.domain.interfaces.TelegramCommand;

public final class SayCommand implements TelegramCommand {
    private final MinecraftBridge mc;
    private final NameResolver names;

    public SayCommand(MinecraftBridge mc, NameResolver names) {
        this.mc = mc;
        this.names = names;
    }

    @Override
    public void handle(String args, TelegramInboundMessage in) {
        if (args == null || args.isEmpty()) return;
        String name = names.resolveEffective(in.tgUsernameOrNull, in.displayName);
        mc.broadcast("[" + name + "] " + args);
    }
}
