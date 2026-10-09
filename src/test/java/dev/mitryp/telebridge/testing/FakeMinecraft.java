package dev.mitryp.telebridge.testing;

import dev.mitryp.telebridge.domain.interfaces.MinecraftBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeMinecraft implements MinecraftBridge {
    public record Tell(String player, String message) {
    }

    public final List<String> broadcasts = new CopyOnWriteArrayList<>();
    public final List<Tell> tells = new CopyOnWriteArrayList<>();
    public final List<String> commands = new CopyOnWriteArrayList<>();
    public List<String> online = new ArrayList<>();
    public List<String> commandOutput = List.of();
    public double tickMs = 50;

    @Override
    public void broadcast(String message) {
        broadcasts.add(message);
    }

    @Override
    public List<String> onlineNames() {
        return online;
    }

    @Override
    public void tell(String playerName, String message) {
        tells.add(new Tell(playerName, message));
    }

    @Override
    public CompletableFuture<List<String>> runCommand(String command, String issuer) {
        commands.add(command);
        return CompletableFuture.completedFuture(commandOutput);
    }

    @Override
    public double averageTickMs() {
        return tickMs;
    }
}
