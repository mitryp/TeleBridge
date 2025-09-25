package dev.mitryp.telebridge.data.config;

import dev.mitryp.telebridge.domain.models.TelebridgeConfig;

public final class TelebridgeConfigHolder {
    private static volatile TelebridgeConfig SNAPSHOT = TelebridgeConfig.defaults();

    public static TelebridgeConfig get() { return SNAPSHOT; }

    public static void set(TelebridgeConfig cfg) { SNAPSHOT = cfg; }
}
