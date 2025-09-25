package dev.mitryp.telebridge.domain.interfaces;

import java.util.List;

public interface MinecraftBridge {
    void broadcast(String message);

    List<String> onlineNames();
}
