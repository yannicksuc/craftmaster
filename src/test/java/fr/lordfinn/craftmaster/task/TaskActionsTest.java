package fr.lordfinn.craftmaster.task;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Task;
import fr.lordfinn.craftmaster.show.Actions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Task texts in the data (chantier D): the task actions, the file, undo, revealing on start. */
class TaskActionsTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
        Actions.Outcome o = run(a("season.create", "name", "Saison 4"), a("player.add", "pseudo", "Ensuri"),
                a("manche.create", "name", "Le Pont", "timer", 180), a("manche.create", "name", "La Tour"));
        assertTrue(o.ok(), o.lines().toString());
    }

    private static Map<String, Object> a(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    @SafeVarargs
    private Actions.Outcome run(Map<String, Object>... actions) {
        return Actions.apply(data, List.of(actions));
    }

    private Manche pont() {
        return data.activeSeason().activePartie().manches.getFirst();
    }

    @Test
    void setWritesTheTaskAndKeepsWhatIsNotGiven() {
        Actions.Outcome o = run(a("task.set", "manche", "Le Pont", "title", "Le pont le plus long",
                "text", "Construisez un pont.\r\n\r\n\r\nIl doit tenir.  ", "rules", "Pas de TNT | • Une seule rive", "wax", "or"));
        assertTrue(o.ok(), o.lines().toString());
        Task t = pont().task;
        assertEquals("Le pont le plus long", t.title);
        assertEquals("Construisez un pont.\n\nIl doit tenir.", t.text);
        assertEquals(List.of("Pas de TNT", "Une seule rive"), t.rules);
        assertEquals("gold", t.wax);
        assertEquals(180, t.shownSeconds(pont()), "no own duration: the round's timer");
        assertTrue(o.lines().getFirst().startsWith("Tâche de « Le Pont » :"), o.lines().toString());

        assertTrue(run(a("task.set", "manche", "Le Pont", "seconds", 90.0, "revealOnStart", "non")).ok());
        assertEquals("Le pont le plus long", pont().task.title, "fields not given are kept");
        assertEquals(90, pont().task.shownSeconds(pont()));
        assertFalse(pont().task.revealOnStart);
    }

    @Test
    void appendAddsAParagraphForDictation() {
        run(a("task.set", "manche", "Le Pont", "text", "Premier."));
        run(a("task.set", "manche", "Le Pont", "text", "Second.", "append", true));
        assertEquals("Premier.\n\nSecond.", pont().task.text);
    }

    @Test
    void invalidValuesChangeNothing() {
        Actions.Outcome o = run(a("task.set", "manche", "Le Pont", "title", "X", "wax", "fluo"));
        assertFalse(o.ok());
        assertTrue(o.lines().getFirst().contains("cire"), o.lines().toString());
        assertNull(pont().task);
        assertFalse(run(a("task.set", "manche", "Le Pont")).ok(), "nothing to change");
        assertFalse(run(a("task.set", "manche", "Inconnue", "title", "X")).ok());
        assertFalse(run(a("task.reveal", "manche", "Le Pont")).ok(), "no task written yet");
        assertFalse(run(a("envelope.give", "manche", "Le Pont")).ok(), "no envelope without a task");
    }

    @Test
    void savedInTheFileAndReadBack() throws Exception {
        run(a("task.set", "manche", "Le Pont", "title", "Titre", "text", "Ligne 1\nLigne 2", "rules", List.of("R1", "R2"), "wax", "blue"));
        String yaml = Files.readString(dir.resolve("config.yml"));
        assertTrue(yaml.contains("task:"), yaml);
        CraftMasterData again = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        again.load(null);
        Task t = again.activeSeason().activePartie().manches.getFirst().task;
        assertEquals("Titre", t.title);
        assertEquals("Ligne 1\nLigne 2", t.text);
        assertEquals(List.of("R1", "R2"), t.rules);
        assertEquals("blue", t.wax);
        assertTrue(t.revealOnStart);
        assertNull(again.activeSeason().activePartie().manches.get(1).task, "a round without a task has none");
    }

    @Test
    void oldFilesWithoutTasksStillLoad() throws Exception {
        Files.writeString(dir.resolve("old.yml"), """
                version: 2
                seasons:
                  '1':
                    name: Saison 1
                    parties:
                      '1':
                        name: Partie 1
                        status: preparation
                        manches:
                        - id: pont
                          name: Le Pont
                        scores: {}
                """);
        CraftMasterData old = new CraftMasterData(dir.resolve("old.yml"), LoggerFactory.getLogger("test"));
        old.load(null);
        assertNull(old.season(1).activePartie().manches.getFirst().task);
    }

    @Test
    void undoTakesTheTextBack() {
        run(a("task.set", "manche", "Le Pont", "title", "Avant"));
        run(a("task.set", "manche", "Le Pont", "title", "Après"));
        assertEquals("Après", pont().task.title);
        data.undo(1);
        assertEquals("Avant", pont().task.title);
        run(a("task.clear", "manche", "Le Pont"));
        assertNull(pont().task);
        data.undo(1);
        assertEquals("Avant", pont().task.title);
    }

    @Test
    void revealAndShowAreEffectsNotData() {
        run(a("task.set", "manche", "Le Pont", "title", "T", "text", "x"));
        int before = data.history().size();
        Actions.Outcome o = run(a("task.reveal", "manche", "Le Pont"), a("task.show", "manche", "Le Pont", "seconds", 8));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals(before, data.history().size(), "a reveal is not in the undo journal");
        assertInstanceOf(Actions.TaskRevealed.class, o.effects().get(0));
        assertTrue(((Actions.TaskRevealed) o.effects().get(0)).announce());
        assertEquals(new Actions.TaskShown(1, pont().id, 8), o.effects().get(1));
        assertFalse(run(a("task.show", "manche", "Le Pont", "seconds", 500)).ok());
    }

    @Test
    void startingTheRoundRevealsItsTaskUnlessEnvelopeOnly() {
        run(a("task.set", "manche", "Le Pont", "title", "T", "text", "x"));
        Actions.Outcome o = run(a("manche.start", "manche", "Le Pont"));
        assertTrue(o.effects().stream().anyMatch(e -> e instanceof Actions.TaskRevealed r && !r.announce()), o.effects().toString());
        run(a("task.set", "manche", "La Tour", "title", "T2", "revealOnStart", false));
        o = run(a("manche.start", "manche", "next"));
        assertTrue(o.effects().stream().noneMatch(e -> e instanceof Actions.TaskRevealed), o.effects().toString());
    }

    @Test
    void envelopesForNamedPlayersOrTheCast() {
        run(a("task.set", "manche", "Le Pont", "title", "T"));
        Actions.Outcome o = run(a("envelope.give", "manche", "Le Pont", "players", "ensuri", "wax", "noir"));
        assertTrue(o.ok(), o.lines().toString());
        assertEquals(new Actions.EnvelopesGiven(1, pont().id, List.of("Ensuri"), "black"), o.effects().getFirst());
        o = run(a("envelope.give", "manche", "Le Pont", "players", List.of("tous")));
        assertEquals(List.of(), ((Actions.EnvelopesGiven) o.effects().getFirst()).players(), "empty = the online cast");
    }

    @Test
    void cleaningTextsAndRules() {
        assertEquals("a\nb\n\nc", Task.cleanText("  a\\nb\n\n\n\nc  \n"));
        assertEquals(List.of("un", "deux", "trois"), Task.parseRules("- un\n* deux | trois |  "));
    }
}
