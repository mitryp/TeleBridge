package dev.mitryp.telebridge.application.services;

import dev.mitryp.telebridge.domain.interfaces.LinkRepository;

public final class NameResolver {
    private final LinkRepository links;

    public NameResolver(LinkRepository links) {
        this.links = links;
    }

    public String resolveEffective(String tgUsernameOrNull, String displayName) {
        String linked = links.resolveMcFromTg(tgUsernameOrNull);
        if (linked != null && !linked.isBlank()) return linked;
        if (tgUsernameOrNull != null && !tgUsernameOrNull.isBlank()) return "@" + tgUsernameOrNull;
        return (displayName != null && !displayName.isBlank()) ? displayName : "TG";
    }
}
