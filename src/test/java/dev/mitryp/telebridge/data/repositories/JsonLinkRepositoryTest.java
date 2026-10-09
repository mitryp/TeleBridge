package dev.mitryp.telebridge.data.repositories;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLinkRepositoryTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config").resolve("links.json");
    }

    private void writeLegacy() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"Steve_TG\":\"Steve\",\"alex\":\"Alex\"}");
    }

    @Test
    void missingFileStartsEmptyAndLinkCreatesIt() {
        var repo = new JsonLinkRepository(file());
        assertNull(repo.findByMc("Steve"));

        repo.link(1, "steve_tg", "Steve");
        assertTrue(Files.exists(file()));
        assertEquals("Steve", new JsonLinkRepository(file()).findByTg(1, null).mcName);
    }

    @Test
    void legacyLinkIsVisibleByMcName() throws Exception {
        writeLegacy();
        var link = new JsonLinkRepository(file()).findByMc("Steve");
        assertNull(link.tgUserId);
        assertEquals("@steve_tg", link.tgLabel());
    }

    @Test
    void legacyLinkUpgradesToIdOnFirstSight() throws Exception {
        writeLegacy();
        var repo = new JsonLinkRepository(file());

        assertNull(repo.findByTg(7, null));
        assertEquals("Steve", repo.findByTg(7, "@STEVE_tg").mcName);

        var reloaded = new JsonLinkRepository(file());
        assertEquals("Steve", reloaded.findByTg(7, null).mcName);
        assertEquals(7L, reloaded.findByMc("Steve").tgUserId);

        JsonObject saved = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertEquals(2, saved.get("version").getAsInt());
        assertFalse(saved.getAsJsonObject("legacy").has("steve_tg"));
        assertTrue(saved.getAsJsonObject("legacy").has("alex"));
    }

    @Test
    void unlinkRemovesLegacyAndIdLinks() throws Exception {
        writeLegacy();
        var repo = new JsonLinkRepository(file());
        repo.link(9, "bob", "Bob");

        assertEquals("@alex", repo.unlinkByMc("Alex").tgLabel());
        assertNull(repo.findByMc("Alex"));
        assertEquals("Bob", repo.unlinkByTg(9, "bob").mcName);
        assertNull(new JsonLinkRepository(file()).findByMc("Bob"));
    }

    @Test
    void unreadableFileIsTreatedAsEmpty() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{not json");
        assertNull(new JsonLinkRepository(file()).findByMc("Steve"));
    }
}
