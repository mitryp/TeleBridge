package dev.mitryp.telebridge.application.services;

import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.models.TelegramInboundMessage;
import dev.mitryp.telebridge.domain.models.TelegramLink;

public final class NameResolver {
    private final LinkRepository links;

    public NameResolver(LinkRepository links) {
        this.links = links;
    }

    public String resolveEffective(TelegramInboundMessage in) {
        TelegramLink linked = links.findByTg(in.userId, in.tgUsernameOrNull);
        if (linked != null) return linked.mcName;
        if (in.tgUsernameOrNull != null && !in.tgUsernameOrNull.isBlank()) return "@" + in.tgUsernameOrNull;
        return (in.displayName != null && !in.displayName.isBlank()) ? in.displayName : "TG";
    }
}
