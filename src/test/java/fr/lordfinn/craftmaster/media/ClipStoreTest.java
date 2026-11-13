package fr.lordfinn.craftmaster.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ClipStoreTest {

    @TempDir
    Path dir;

    private Path upload(String content) throws Exception {
        Path f = Files.createTempFile(dir, "up", ".part");
        Files.writeString(f, content);
        return f;
    }

    @Test
    void addListRenameDeleteAndReload() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 11, 13, 3, 16, 2);
        ClipStore s = new ClipStore(dir.resolve("clips"));
        ClipStore.Clip a = s.add(upload("aaa"), "", "LordFinn", 6000, "direct", t);
        ClipStore.Clip b = s.add(upload("bbbb"), "Le saut", "LordFinn", 4000, "replay", t);
        assertEquals("clip-20261113-031602", a.id);
        assertEquals("clip-20261113-031602-2", b.id);
        assertEquals("Clip 13/11 03h16", a.name);
        assertEquals(4, b.size);
        assertSame(b, s.byNumber(0));
        assertSame(a, s.byNumber(-1));
        assertSame(a, s.byNumber(1));
        assertNull(s.byNumber(3));
        assertTrue(s.rename(a.id, " Chute "));

        ClipStore again = new ClipStore(dir.resolve("clips"));
        assertEquals(2, again.list().size());
        assertEquals("Chute", again.get(a.id).name);
        assertTrue(again.delete(a.id));
        assertFalse(Files.exists(again.file(a.id)));
        assertEquals(1, new ClipStore(dir.resolve("clips")).list().size());
    }

    @Test
    void channels() {
        TwitchChannels c = new TwitchChannels(dir.resolve("twitch.json"));
        assertTrue(c.link("Ensuri", "https://twitch.tv/Ensuri_TV"));
        assertFalse(c.link("Juste", "pas une chaîne"));
        assertEquals("ensuri_tv", c.channelOf("ensuri"));
        assertEquals("ensuri_tv", c.channelOf("twitch.tv/ensuri_tv"));
        assertEquals("Ensuri", c.playerOf("ENSURI_TV"));
        assertEquals("ensuri_tv", new TwitchChannels(dir.resolve("twitch.json")).channelOf("Ensuri"));
        assertTrue(c.unlink("ensuri"));
        assertNull(c.channelOf("Ensuri"));
    }
}
