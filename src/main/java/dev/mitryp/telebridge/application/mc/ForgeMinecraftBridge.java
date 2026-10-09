package dev.mitryp.telebridge.application.mc;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ForgeMinecraftBridge implements MinecraftBridge {
    @Override
    public void broadcast(String message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> server.getPlayerList().broadcastSystemMessage(Component.literal(message), false));
    }

    @Override
    public void tell(String playerName, String message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        });
    }

    @Override
    public CompletableFuture<List<String>> runCommand(String command, String issuer) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return CompletableFuture.completedFuture(List.of("Server is not running."));

        CompletableFuture<List<String>> result = new CompletableFuture<>();
        server.execute(() -> {
            List<String> output = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    output.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                // Echoes the command to online operators, like console commands.
                @Override
                public boolean shouldInformAdmins() {
                    return true;
                }
            };
            ServerLevel level = server.overworld();
            CommandSourceStack source = new CommandSourceStack(capture, Vec3.atLowerCornerOf(level.getSharedSpawnPos()),
                    Vec2.ZERO, level, 4, "Telegram", Component.literal("Telegram " + issuer), server, null);
            try {
                server.getCommands().performPrefixedCommand(source, command);
                result.complete(output);
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    @Override
    public double averageTickMs() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? -1 : server.getAverageTickTime();
    }

    @Override
    public List<String> onlineNames() {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return List.of();
        return srv.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getName).map(Component::getString).collect(Collectors.toList());
    }
}
