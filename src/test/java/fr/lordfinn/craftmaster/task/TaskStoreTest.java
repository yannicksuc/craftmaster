package fr.lordfinn.craftmaster.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Reveals, hidden notes, torn pieces and the Twitch chat reader (chantier D). */
class TaskStoreTest {

    @TempDir
    Path dir;

    private TaskStore store() {
        TaskStore s = new TaskStore(dir.resolve("tasks.json"), LoggerFactory.getLogger("test"));
        s.load();
        return s;
    }

    @Test
    void revealsAreKeptAndFirstOneWins() {
        TaskStore s = store();
        assertFalse(s.isRevealed(1, "pont"));
        assertTrue(s.setRevealed(1, "pont", 1000, "Ensuri"));
        assertFalse(s.setRevealed(1, "pont", 2000, "Cyllu"), "already revealed");
        assertEquals(new TaskStore.Reveal(1000, "Ensuri"), s.reveal(1, "pont"));
        assertFalse(s.isRevealed(2, "pont"), "per season");
        assertArrayEquals(new String[]{"1", "pont"}, s.lastRevealed());

        TaskStore again = store();
        assertTrue(again.isRevealed(1, "pont"), "saved in tasks.json");
        assertTrue(again.hide(1, "pont"));
        assertFalse(again.hide(1, "pont"));
        assertNull(again.lastRevealed());
        again.setRevealed(1, "a", 1, null);
        again.setRevealed(1, "b", 1, null);
        again.setRevealed(2, "c", 1, null);
        assertEquals(2, again.hideAll(1));
        assertTrue(again.isRevealed(2, "c"));
    }

    @Test
    void notesHidingPlacesAndFinds() {
        TaskStore s = store();
        Note n = s.createNote("Indice n°1 !", 5);
        assertEquals("indice-n-1", n.id);
        assertEquals("indice-n-1-2", s.createNote("Indice n°1", 6).id, "ids stay unique");
        n.spots.add(new Note.Spot("minecraft:overworld", 1, 64, 2, null, Note.Method.UNDER, 0));
        n.spots.add(new Note.Spot("minecraft:overworld", 0, 0, 0, "uuid-1", Note.Method.BEHIND, 0));
        s.changed();
        assertEquals(1, s.spotsAt("minecraft:overworld", 1, 64, 2).size());
        assertTrue(s.spotsAt("minecraft:overworld", 0, 0, 0).isEmpty(), "the painting's spot is an entity's");
        assertEquals(1, s.spotsOn("uuid-1").size());
        assertTrue(s.recordFind(n, "Ensuri", 0, "under", 100));
        assertFalse(s.recordFind(n, "ensuri", 0, "under", 200), "once per player");
        assertTrue(n.foundBy("Ensuri", 0));

        TaskStore again = store();
        Note m = again.note("Indice n°1 !");
        assertNotNull(m, "found by title too");
        assertEquals(Note.Method.UNDER, m.spots.getFirst().method());
        assertTrue(m.spots.getFirst().method().sneak);
        assertEquals(1, m.finds.size());
        m.spots.getFirst().takenBy = "Ensuri";
        again.resetNote(m);
        assertTrue(m.finds.isEmpty());
        assertNull(m.spots.getFirst().takenBy);
        assertTrue(again.deleteNote(m.id));
    }

    @Test
    void whoMayRead() {
        Note n = new Note("x", "X");
        assertTrue(n.readableBy("Anyone", false));
        n.readers = Note.CAST;
        assertFalse(n.readableBy("Spectateur", false));
        assertTrue(n.readableBy("Ensuri", true));
        n.readers = Note.PLAYERS;
        n.players = List.of("Ensuri");
        assertTrue(n.readableBy("ensuri", false));
        assertFalse(n.readableBy("Cyllu", true));
    }

    @Test
    void piecesAreHiddenInTurn() {
        Note n = new Note("x", "X");
        n.pieces = 3;
        assertEquals(0, n.nextPieceToHide());
        n.spots.add(new Note.Spot("w", 0, 0, 0, null, Note.Method.BOOK, 0));
        assertEquals(1, n.nextPieceToHide());
        n.spots.add(new Note.Spot("w", 1, 0, 0, null, Note.Method.BOOK, 1));
        n.spots.add(new Note.Spot("w", 2, 0, 0, null, Note.Method.BOOK, 2));
        assertEquals(2, n.nextPieceToHide(), "all hidden: the last one again");
    }

    @Test
    void tornTextPutsBackTogether() {
        String text = "Le trésor est caché sous le troisième arbre\nà gauche du puits, derrière la grosse pierre moussue.";
        for (int n = 1; n <= 6; n++) {
            List<String> parts = Note.split(text, n);
            assertEquals(n, parts.size());
            for (String p : parts) assertFalse(p.isBlank(), n + " pieces: " + parts);
            assertEquals(text.replaceAll("\\s+", " "), String.join(" ", parts).replaceAll("\\s+", " "));
        }
        // Shorter than the pieces: some stay empty, nothing is lost
        List<String> few = Note.split("deux mots", 4);
        assertEquals(4, few.size());
        assertEquals("deux mots", String.join(" ", few).strip().replaceAll("\\s+", " "));
        // Balanced: no piece more than twice another on a long text
        List<String> parts = Note.split("mot ".repeat(60).strip(), 3);
        int min = parts.stream().mapToInt(String::length).min().orElse(0), max = parts.stream().mapToInt(String::length).max().orElse(0);
        assertTrue(max <= 2 * min, parts.toString());
    }

    @Test
    void brokenFileIsKeptAside() throws Exception {
        Files.writeString(dir.resolve("tasks.json"), "{ pas du json");
        TaskStore s = store();
        assertTrue(s.notes().isEmpty());
        assertTrue(Files.exists(dir.resolve("tasks.json.broken")));
    }

    @Test
    void twitchChatLines() {
        assertEquals("lordfinn", TwitchIrc.channel("https://www.twitch.tv/LordFinn"));
        assertEquals("lordfinn", TwitchIrc.channel("#LordFinn"));
        assertEquals("", TwitchIrc.channel("pas une chaîne"));
        assertTrue(TwitchIrc.anonymousNick(new Random(1)).startsWith("justinfan"));
        assertEquals("PONG :tmi.twitch.tv", TwitchIrc.reply("PING :tmi.twitch.tv"));
        assertNull(TwitchIrc.reply(":tmi.twitch.tv 001 justinfan1 :Welcome"));
        TwitchIrc.Message m = TwitchIrc.parse(":viewer42!viewer42@viewer42.tmi.twitch.tv PRIVMSG #lordfinn :!tâche stp");
        assertEquals(new TwitchIrc.Message("lordfinn", "viewer42", "!tâche stp"), m);
        assertEquals("Bob", TwitchIrc.parse("@badge-info=;color=#FF0000 :Bob!bob@bob.tmi.twitch.tv PRIVMSG #x :hello :)").user());
        assertEquals("hello :)", TwitchIrc.parse("@a=b :Bob!bob@bob.tmi.twitch.tv PRIVMSG #x :hello :)").text());
        assertNull(TwitchIrc.parse(":tmi.twitch.tv 366 justinfan1 #lordfinn :End of /NAMES list"));
        assertTrue(TwitchIrc.isCommand("!Tache", List.of("!tache")));
        assertTrue(TwitchIrc.isCommand("!tâche please", List.of("!tache")));
        assertFalse(TwitchIrc.isCommand("je veux la !tache", List.of("!tache")));
        assertTrue(TwitchIrc.cooledDown(0, 1000, 30_000));
        assertFalse(TwitchIrc.cooledDown(1000, 20_000, 30_000));
        assertTrue(TwitchIrc.cooledDown(1000, 31_000, 30_000));
        assertEquals(List.of("NICK justinfan1", "JOIN #lordfinn"), TwitchIrc.login("justinfan1", "lordfinn"));
    }
}
