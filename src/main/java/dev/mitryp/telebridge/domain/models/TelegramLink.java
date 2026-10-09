package dev.mitryp.telebridge.domain.models;

/** A Telegram account linked to a Minecraft player. {@code tgUserId} is null for legacy links made by username only. */
public final class TelegramLink {
    public final Long tgUserId;
    public final String tgUsername;
    public final String mcName;

    public TelegramLink(Long tgUserId, String tgUsername, String mcName) {
        this.tgUserId = tgUserId;
        this.tgUsername = tgUsername;
        this.mcName = mcName;
    }

    public String tgLabel() {
        return tgUsername != null ? "@" + tgUsername : "Telegram user " + tgUserId;
    }
}
