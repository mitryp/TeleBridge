package dev.mitryp.telebridge.application.mc;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.stream.Collectors;

public final class ForgeMinecraftBridge implements MinecraftBridge {
    @Override
    public void broadcast(String message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> server.getPlayerList().broadcastSystemMessage(Component.literal(message), false));
    }

    @Override
    public List<String> onlineNames() {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return List.of();
        return srv.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getName).map(Component::getString).collect(Collectors.toList());
    }
}
