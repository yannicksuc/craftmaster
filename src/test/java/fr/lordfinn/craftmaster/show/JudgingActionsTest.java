package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The judging actions of chantier H (results sheet, scale, stopwatches), applied without a server. */
class JudgingActionsTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
        Actions.Outcome o = run(
                a("season.create", "name", "Saison 4"),
                a("player.add", "pseudo", "JustEdible"),
                a("player.add", "pseudo", "Ensuri", "aliases", "Ensu"),
                a("player.add", "pseudo", "Cyllu"),
                a("player.add", "pseudo", "Arreaubaz"),
                a("manche.create", "name", "Le Pont"),
                a("manche.create", "name", "La Tour"),
                a("manche.start", "manche", "Le Pont"));
        assertTrue(o.ok(), o.lines().toString());
    }

    @AfterEach
    void tearDown() {
        Stopwatch.SHARED.resetAll();
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

    private int score(String player) {
        return data.activeSeason().activePartie().score(pont().id, player);
    }

    @Test
    void saidTimesFillTheSheetAndTheScaleGivesThePoints() {
        Actions.Outcome o = run(
                a("result.set", "player", "Ensu", "value", 42.0, "kind", "time"),
                a("result.set", "player", "Cyllu", "value", "1:05"),
                a("result.set", "player", "JustEdible", "value", "42"),
                a("result.dq", "player", "Arreaubaz"));
        assertTrue(o.ok(), o.lines().toString());
        ResultSheet r = pont().results;
        assertEquals(ResultSheet.Kind.TIME, r.kind);
        assertEquals(65.0, r.entries.get("Cyllu").value());
        assertTrue(o.effects().stream().anyMatch(e -> e instanceof Actions.ResultRecorded rr && rr.player().equals("Ensuri") && rr.text().equals("42,0 s")));

        o = run(a("results.apply"));
        assertTrue(o.ok(), o.lines().toString());
        // Ensuri and JustEdible tie at 42 s: 5 each, Cyllu third: 3, Arreaubaz disqualified: 0
        assertEquals(5, score("Ensuri"));
        assertEquals(5, score("JustEdible"));
        assertEquals(3, score("Cyllu"));
        assertEquals(0, score("Arreaubaz"));
        assertTrue(o.lines().getLast().startsWith("Barème 5-4-3-2-1 appliqué"), o.lines().toString());

        // One undo takes the whole scale back
        assertTrue(run(a("undo")).ok());
        assertEquals(0, score("Ensuri"));
        assertFalse(data.activeSeason().activePartie().hasScore(pont().id, "Ensuri"));
        assertEquals(42.0, pont().results.entries.get("Ensuri").value(), "the sheet itself stays");
    }

    @Test
    void sheetSettingsAndErrors() {
        assertTrue(run(a("results.config", "kind", "nombre", "order", "lower", "scale", "3 2 1")).ok());
        ResultSheet r = pont().results;
        assertEquals(ResultSheet.Kind.COUNT, r.kind);
        assertTrue(r.lowerWins());
        assertEquals("3,2,1", r.scale);
        assertFalse(run(a("results.config", "scale", "trois")).ok());
        assertFalse(run(a("results.apply")).ok(), "an empty sheet gives nothing");
        assertTrue(run(a("result.set", "player", "Ensuri", "value", 4)).ok());
        assertFalse(run(a("result.set", "player", "Cyllu", "value", "beaucoup")).ok());
        // A count sheet with values does not silently become a time sheet
        assertFalse(run(a("result.set", "player", "Cyllu", "value", 3, "kind", "time")).ok());
        assertTrue(run(a("result.set", "player", "Cyllu", "value", 9, "manche", "La Tour")).ok());
        assertEquals(9.0, data.activeSeason().activePartie().manches.get(1).results.entries.get("Cyllu").value());
        assertTrue(run(a("results.apply", "scale", "10,1")).ok());
        assertEquals(10, score("Ensuri"));
        assertFalse(run(a("result.dq", "player", "Ensuri", "dq", false)).ok(), "not disqualified");
        assertTrue(run(a("result.remove", "player", "Ensuri")).ok());
        assertFalse(run(a("result.remove", "player", "Ensuri")).ok());
        assertTrue(run(a("results.clear")).ok());
        assertTrue(pont().results.entries.isEmpty());
    }

    @Test
    void sheetIsSavedAndFollowsARename() {
        assertTrue(run(a("result.set", "player", "Ensuri", "value", "12,5", "kind", "distance")).ok());
        assertTrue(run(a("player.update", "pseudo", "Ensuri", "newPseudo", "Ensuri2")).ok());
        assertEquals(12.5, pont().results.entries.get("Ensuri2").value());
        data.save();
        CraftMasterData back = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        back.load(null);
        Manche m = back.activeSeason().activePartie().manches.getFirst();
        assertEquals(ResultSheet.Kind.DISTANCE, m.results.kind);
        assertEquals(12.5, m.results.entries.get("Ensuri2").value());
    }

    @Test
    void stoppingAStopwatchWritesTheTime() {
        Actions.Outcome o = run(a("stopwatch.start", "player", "Ensu"));
        assertTrue(o.ok());
        assertTrue(o.effects().contains(new Actions.StopwatchStarted("Ensuri")));
        // The world part (ActionExecutor) starts it; done by hand here
        Stopwatch.SHARED.start("Ensuri", System.currentTimeMillis() - 42_000);
        o = run(a("stopwatch.stop", "player", "Ensuri"));
        assertTrue(o.ok(), o.lines().toString());
        double v = pont().results.entries.get("Ensuri").value();
        assertTrue(v >= 42 && v < 43, "time written: " + v);
        assertEquals(ResultSheet.Kind.TIME, pont().results.kind);
        assertTrue(o.effects().stream().anyMatch(e -> e instanceof Actions.StopwatchStopped s && s.player().equals("Ensuri")));
        assertFalse(run(a("stopwatch.stop", "player", "Cyllu")).ok(), "not running");
        // Not recorded on request
        Stopwatch.SHARED.start("Cyllu", System.currentTimeMillis() - 1_000);
        assertTrue(run(a("stopwatch.stop", "player", "Cyllu", "record", false)).ok());
        assertNull(pont().results.entries.get("Cyllu"));
        assertTrue(run(a("buzzer.reset"), a("stopwatch.reset")).ok());
    }
}
