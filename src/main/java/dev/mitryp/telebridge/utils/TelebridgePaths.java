package dev.mitryp.telebridge.utils;

import java.nio.file.Path;

public final class TelebridgePaths {
    public static Path linksFile() {
        return Path.of("config", "telebridge-links.json");
    }
}
