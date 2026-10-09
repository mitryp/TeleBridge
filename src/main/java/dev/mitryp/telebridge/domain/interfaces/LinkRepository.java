package dev.mitryp.telebridge.domain.interfaces;

import dev.mitryp.telebridge.domain.models.TelegramLink;

public interface LinkRepository {
    /** Upgrades a legacy username link to this user id on first sight. */
    TelegramLink findByTg(long tgUserId, String tgUsernameOrNull);

    TelegramLink findByMc(String mcName);

    void link(long tgUserId, String tgUsernameOrNull, String mcName);

    TelegramLink unlinkByTg(long tgUserId, String tgUsernameOrNull);

    TelegramLink unlinkByMc(String mcName);
}
