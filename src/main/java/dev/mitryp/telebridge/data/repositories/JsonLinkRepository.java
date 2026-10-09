package dev.mitryp.telebridge.data.repositories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import dev.mitryp.telebridge.domain.interfaces.LinkRepository;
import dev.mitryp.telebridge.domain.models.TelegramLink;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Links keyed by Telegram user id. Files from 0.5.0 and earlier (a flat username → MC name map) are read as
 * legacy links, each upgraded to an id link the first time that username is seen.
 */
public final class JsonLinkRepository implements LinkRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LEGACY_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private static final class FileData {
        int version = 2;
        List<TelegramLink> links = new ArrayList<>();
        /** Lowercase username without '@' → MC name. */
        Map<String, String> legacy = new HashMap<>();
    }

    private final Path file;
    private FileData data = new FileData();

    public JsonLinkRepository(Path file) {
        this.file = file;
        load();
    }

    @Override
    public synchronized TelegramLink findByTg(long tgUserId, String tgUsernameOrNull) {
        for (TelegramLink l : data.links) {
            if (l.tgUserId == tgUserId) return l;
        }
        if (tgUsernameOrNull == null) return null;

        String mcName = data.legacy.remove(normalize(tgUsernameOrNull));
        if (mcName == null) return null;
        TelegramLink upgraded = new TelegramLink(tgUserId, tgUsernameOrNull, mcName);
        data.links.add(upgraded);
        save();
        return upgraded;
    }

    @Override
    public synchronized TelegramLink findByMc(String mcName) {
        for (TelegramLink l : data.links) {
            if (l.mcName.equals(mcName)) return l;
        }
        for (var e : data.legacy.entrySet()) {
            if (e.getValue().equals(mcName)) return new TelegramLink(null, e.getKey(), mcName);
        }
        return null;
    }

    @Override
    public synchronized void link(long tgUserId, String tgUsernameOrNull, String mcName) {
        data.links.add(new TelegramLink(tgUserId, tgUsernameOrNull, mcName));
        save();
    }

    @Override
    public synchronized TelegramLink unlinkByTg(long tgUserId, String tgUsernameOrNull) {
        TelegramLink link = findByTg(tgUserId, tgUsernameOrNull);
        if (link != null) {
            data.links.remove(link);
            save();
        }
        return link;
    }

    @Override
    public synchronized TelegramLink unlinkByMc(String mcName) {
        TelegramLink link = findByMc(mcName);
        if (link == null) return null;
        if (link.tgUserId == null) data.legacy.remove(link.tgUsername);
        else data.links.remove(link);
        save();
        return link;
    }

    private static String normalize(String username) {
        String norm = username.startsWith("@") ? username.substring(1) : username;
        return norm.toLowerCase(Locale.ROOT).trim();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(r, JsonElement.class);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("version")) {
                data = GSON.fromJson(obj, FileData.class);
            } else {
                Map<String, String> legacy = GSON.fromJson(obj, LEGACY_TYPE);
                legacy.forEach((user, mc) -> data.legacy.put(normalize(user), mc));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[TeleBridge] Could not read {}: {}", file, e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("[TeleBridge] Could not write {}: {}", file, e.toString());
        }
    }
}
