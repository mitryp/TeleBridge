package dev.mitryp.telebridge.data.repositories;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class JsonLinkRepository implements LinkRepository {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private final Path file;
    private final Map<String, String> tgToMc = new HashMap<>(); // key: @username (lowercase, no '@')

    public JsonLinkRepository(Path file) {
        this.file = file;
        load();
    }

    @Override
    public synchronized String resolveMcFromTg(String tgUserOrNull) {
        if (tgUserOrNull == null) return null;
        return tgToMc.get(tgUserOrNull.toLowerCase(Locale.ROOT));
    }

    @Override
    public synchronized void link(String tgUsername, String mcName) {
        if (tgUsername == null || tgUsername.isBlank() || mcName == null || mcName.isBlank()) return;
        tgToMc.put(normalize(tgUsername), mcName);
        save();
    }

    @Override
    public synchronized String unlinkByMc(String mcName) {
        if (mcName == null) return null;
        String key = tgToMc.entrySet().stream()
                .filter(e -> mcName.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (key != null) {
            tgToMc.remove(key);
            save();
        }

        return key; // may be null
    }

    @Override
    public synchronized String findTgByMc(String mcName) {
        return tgToMc.entrySet().stream().filter(e -> e.getValue().equals(mcName)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private String normalize(String raw) {
        String norm = raw.startsWith("@") ? raw.substring(1) : raw;
        return norm.toLowerCase(Locale.ROOT).trim();
    }

    private void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file))
                try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                    Map<String, String> m = GSON.fromJson(r, MAP_TYPE);
                    if (m != null) tgToMc.putAll(m);
                }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(tgToMc, MAP_TYPE, w);
        } catch (IOException ignored) {
        }
    }
}
