package dev.mitryp.telebridge.domain.interfaces;

public interface LinkRepository {
    String resolveMcFromTg(String tgUserOrNull);

    void link(String tgUsername, String mcName);

    String unlinkByMc(String mcName);

    String findTgByMc(String mcName);

    default boolean isLinked(String tgUsername) {
        return resolveMcFromTg(tgUsername) != null;
    }
}
