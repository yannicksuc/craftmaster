package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftMasterDataTest {

    @TempDir
    Path dir;

    private Path file;
    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        file = dir.resolve("config/craftmaster/config.yml");
        data = new CraftMasterData(file, LoggerFactory.getLogger("test"));
        data.load(null);
    }

    /** Reads the file again from disk, like a server restart. */
    private CraftMasterData reloaded() {
        CraftMasterData d = new CraftMasterData(file, LoggerFactory.getLogger("test"));
        d.load(null);
        return d;
    }

    // ---------------------------------------------------------------- plugin compatibility (version 1 files)

    /** A config.yml written by the Paper plugin (quoted numeric keys, Bukkit world name). */
    private static final String PLUGIN_FILE = """
            # CraftMaster - config par défaut
            seasons:
              '3':
                tasks:
                - Pont
                - Défi
            scores:
              '3':
                LordFinn:
                  Pont: 4
                  Défi: 3
                Notch:
                  Pont: '9'
            busts:
              '3':
                '1':
                  world: world
                  x: 0
                  y: -60
                  z: 5
            """;

    @Test
    void importsThePluginFileOnFirstStart() throws IOException {
        Path legacy = dir.resolve("plugins/CraftMaster/config.yml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, PLUGIN_FILE, StandardCharsets.UTF_8);
        Path fresh = dir.resolve("other/config.yml");
        CraftMasterData d = new CraftMasterData(fresh, LoggerFactory.getLogger("test"));
        d.load(legacy);

        assertTrue(Files.exists(fresh), "the plugin file is copied next to the mod's config");
        assertTrue(Files.exists(dir.resolve("other/config.yml.v1")), "the version 1 file is kept before migrating");
        assertEquals(List.of("Pont", "Défi"), d.tasks(3));
        assertEquals(7, d.total(3, "LordFinn"));
        assertEquals(9, d.total(3, "Notch"), "numbers saved as text still count");
        assertEquals(new CraftMasterData.BustPos("world", 0, -60, 5), d.bust(3, 1));
    }

    @Test
    void readsUnquotedNumericKeys() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "seasons:\n  1:\n    tasks: [A]\nscores:\n  1:\n    Bob: {A: 2}\n", StandardCharsets.UTF_8);
        CraftMasterData d = reloaded();
        assertEquals(List.of("A"), d.tasks(1));
        assertEquals(2, d.score(1, "A", "Bob"));
        assertEquals(List.of("1"), d.seasons());
    }

    @Test
    void anUnreadableFileStartsEmptyInsteadOfCrashing() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "seasons: [not: valid", StandardCharsets.UTF_8);
        CraftMasterData d = reloaded();
        assertTrue(d.seasons().isEmpty());
        assertTrue(d.ranking(1).isEmpty());
        assertEquals("seasons: [not: valid", Files.readString(file.resolveSibling("config.yml.broken"), StandardCharsets.UTF_8),
                "the unreadable file is kept aside before anything overwrites it");
    }

    // ---------------------------------------------------------------- tasks

    @Test
    void addRemoveAndRenameTasks() {
        assertTrue(data.addTask(1, "Pont"));
        assertFalse(data.addTask(1, "Pont"), "no duplicates");
        assertTrue(data.addTask(1, "Tour"));
        assertTrue(data.renameTask(1, "Pont", "Le Pont"));
        assertEquals(List.of("Le Pont", "Tour"), data.tasks(1), "a renamed task keeps its place");
        assertFalse(data.renameTask(1, "Absent", "X"));
        assertTrue(data.removeTask(1, "Tour"));
        assertFalse(data.removeTask(1, "Tour"));
        assertEquals(List.of("Le Pont"), reloaded().tasks(1), "every change is saved");
    }

    @Test
    void renamingATaskMovesItsScores() {
        data.addTask(1, "Pont");
        data.setScore(1, "Pont", "LordFinn", 4);
        data.renameTask(1, "Pont", "Le Pont");
        assertEquals(4, data.score(1, "Le Pont", "LordFinn"));
        assertEquals(Map.of("Le Pont", 4), data.scores(1, "LordFinn"));
    }

    @Test
    void renamingOntoAnExistingTaskMergesTheScores() {
        data.addTask(1, "A");
        data.addTask(1, "B");
        data.setScore(1, "A", "Bob", 2);
        data.setScore(1, "B", "Bob", 3);
        data.renameTask(1, "A", "B");
        assertEquals(List.of("B"), data.tasks(1));
        assertEquals(5, data.score(1, "B", "Bob"));
    }

    @Test
    void removingATaskKeepsItsScoresInTheTotalLikeThePlugin() {
        data.addTask(1, "A");
        data.setScore(1, "A", "Bob", 5);
        data.removeTask(1, "A");
        assertEquals(5, data.total(1, "Bob"));
    }

    // ---------------------------------------------------------------- scores and ranking

    @Test
    void rankingSortsByTotalAndKeepsTiesInOrder() {
        data.setScore(2, "A", "Alice", 3);
        data.setScore(2, "A", "Bob", 5);
        data.setScore(2, "A", "Carl", 3);
        data.setScore(2, "B", "Alice", 1);
        List<CraftMasterData.Entry> r = data.ranking(2);
        assertEquals(List.of("Bob", "Alice", "Carl"), r.stream().map(CraftMasterData.Entry::player).toList());
        assertEquals(4, r.get(1).total());
        assertEquals("Bob", data.playerAtRank(2, 1));
        assertNull(data.playerAtRank(2, 4));
        assertNull(data.playerAtRank(2, 0));
        assertTrue(data.ranking(99).isEmpty());
    }

    @Test
    void addScoreAddsAndTakesAwayPoints() {
        assertEquals(3, data.addScore(1, "A", "Bob", 3));
        assertEquals(1, data.addScore(1, "A", "Bob", -2));
        assertEquals(1, reloaded().score(1, "A", "Bob"));
    }

    @Test
    void removeScoreDropsAPlayerWithoutScores() {
        data.setScore(1, "A", "Bob", 2);
        data.setScore(1, "B", "Bob", 1);
        assertTrue(data.removeScore(1, "A", "Bob"));
        assertEquals(List.of("Bob"), data.players(1));
        assertTrue(data.removeScore(1, "B", "Bob"));
        assertTrue(data.players(1).isEmpty(), "out of the ranking");
        assertFalse(data.removeScore(1, "B", "Bob"));
    }

    // ---------------------------------------------------------------- seasons

    @Test
    void allSeasonsListsSeasonsFromTasksScoresAndBusts() {
        data.addTask(2, "A");
        data.setScore(10, "A", "Bob", 1);
        data.setBust(1, 1, new CraftMasterData.BustPos("minecraft:overworld", 0, 0, 0));
        assertEquals(List.of(1, 2, 10), data.allSeasons(), "numerically sorted");
    }

    @Test
    void hiddenSeasonsAreSaved() {
        data.addTask(1, "A");
        assertFalse(data.isHidden(1));
        data.setHidden(1, true);
        assertTrue(reloaded().isHidden(1));
        assertEquals(List.of("A"), reloaded().tasks(1), "hiding a season keeps its tasks");
        data.setHidden(1, false);
        assertFalse(reloaded().isHidden(1));
    }

    // ---------------------------------------------------------------- busts

    @Test
    void bustsAreStoredPerSeasonAndRank() {
        CraftMasterData.BustPos pos = new CraftMasterData.BustPos("minecraft:overworld", 1, 64, -3);
        data.setBust(1, 2, pos);
        data.setBust(1, 1, pos);
        assertEquals(List.of(1, 2), data.bustRanks(1));
        assertEquals(pos, reloaded().bust(1, 2));
        assertTrue(data.removeBust(1, 2));
        assertFalse(data.removeBust(1, 2));
        assertTrue(data.removeBust(1, 1));
        assertTrue(data.bustSeasons().isEmpty(), "an empty season disappears");
        assertNull(data.bust(1, 1));
    }

    @Test
    void savedFileIsVersion2WithRoundsInTheActiveGame() throws IOException {
        data.addTask(1, "Pont");
        data.setScore(1, "Pont", "LordFinn", 4);
        String yaml = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("version: 2"), yaml);
        assertTrue(yaml.contains("parties:\n      '1':\n        name: Partie 1"), "the game is created on its own: " + yaml);
        assertTrue(yaml.contains("- id: pont\n          name: Pont"), yaml);
        assertTrue(yaml.contains("scores:\n          pont:\n            LordFinn: 4"), yaml);
    }

    @Test
    void scoresOnAnUnknownTaskCreateAnArchivedRound() {
        data.setScore(1, "Bonus", "Bob", 2);
        assertTrue(data.tasks(1).isEmpty(), "not listed");
        assertTrue(data.manche(1, "Bonus").archived);
        assertEquals(2, data.total(1, "Bob"));
        assertTrue(data.addTask(1, "Bonus"), "adding it lists it again");
        assertEquals(List.of("Bonus"), data.tasks(1));
        assertEquals(2, data.score(1, "Bonus", "Bob"), "with its points");
    }
}
