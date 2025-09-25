package dev.mitryp.telebridge.domain.interfaces;

import dev.mitryp.telebridge.domain.models.TelebridgeConfig;

public interface ConfigProvider {
    TelebridgeConfig get();
}
