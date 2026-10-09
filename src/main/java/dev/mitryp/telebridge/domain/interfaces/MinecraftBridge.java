package dev.mitryp.telebridge.domain.interfaces;

import java.util.List;

public interface MinecraftBridge {
    void broadcast(String message);

    List<String> onlineNames();

    /** Messages the player if they are online. */
    void tell(String playerName, String message);
}
