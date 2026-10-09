package dev.mitryp.telebridge.domain.interfaces;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MinecraftBridge {
    void broadcast(String message);

    List<String> onlineNames();

    /** Messages the player if they are online. */
    void tell(String playerName, String message);

    /** Runs a server command as an operator; completes with the command's output lines. */
    CompletableFuture<List<String>> runCommand(String command, String issuer);

    /** Average tick time in milliseconds, or -1 if the server is not running. */
    double averageTickMs();
}
