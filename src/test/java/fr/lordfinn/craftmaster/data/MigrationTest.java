package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Version 1 files (Paper plugin, mod 2.0) are converted to version 2 on load without changing what anyone sees. */
class MigrationTest {

    @TempDir
    Path dir;

    private CraftMasterData load(String yaml) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        CraftMasterData d = new CraftMasterData(file, LoggerFactory.getLogger("test"));
        d.load(null);
        return d;
    }

    /** As written by the Paper plugin: quoted keys, numbers as text, a score on a task no longer listed. */
    private static final String PLUGIN_FILE = """
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
                  Ancienne: 2
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

    /** As written by the mod 2.0: hidden season, dimension ids, unquoted keys. */
    private static final String MOD_2_FILE = """
            # CraftMaster - saisons, scores et busts
            seasons:
              4:
                tasks: [Le Pont, Tour]
                hidden: true
              5:
                tasks: [A]
            scores:
              4:
                Ensuri: {Le Pont: 5, Tour: 7}
                Cyllu: {Le Pont: 13}
            busts:
              4:
                1: {world: 'minecraft:overworld', x: 10, y: 64, z: -3}
                2: {world: 'minecraft:overworld', x: 12, y: 64, z: -3}
            """;

    @Test
    void pluginTasksBecomeTheRoundsOfPartie1() throws IOException {
        CraftMasterData d = load(PLUGIN_FILE);
        Season s = d.season(3);
        assertEquals("Saison 3", s.name);
        assertEquals(1, s.parties.size());
        Partie p = s.activePartie();
        assertEquals("Partie 1", p.name);
        assertEquals(List.of("Pont", "Défi"), d.tasks(3));
        assertEquals(List.of("pont", "defi", "ancienne"), p.manches.stream().map(r -> r.id).toList(), "stable ids from the names");
        assertTrue(p.manche("ancienne").archived, "a task removed from the list stays as an archived round");
        assertEquals(9, d.total(3, "LordFinn"), "its points still count");
        assertEquals(9, d.total(3, "Notch"), "numbers saved as text still count");
        assertEquals(List.of("LordFinn", "Notch"), s.players.keySet().stream().toList(), "scored players join the cast");
        assertEquals(new CraftMasterData.BustPos("world", 0, -60, 5), d.bust(3, 1));
        assertEquals(3, d.activeSeason().id);
    }

    @Test
    void currentScoresArePublishedSoNothingChangesForPlayers() throws IOException {
        CraftMasterData d = load(MOD_2_FILE);
        assertEquals(d.ranking(4, false), d.ranking(4, true));
        assertEquals("Cyllu", d.playerAtRank(4, 1, true));
        assertEquals(12, d.publishedTotal(4, "Ensuri"));
        assertNotNull(d.season(5).published, "even a season without scores is published (empty)");
    }

    @Test
    void mod2FileKeepsHiddenSeasonsAndBusts() throws IOException {
        CraftMasterData d = load(MOD_2_FILE);
        assertTrue(d.isHidden(4));
        assertFalse(d.isHidden(5));
        assertEquals(List.of(1, 2), d.bustRanks(4));
        assertEquals(new CraftMasterData.BustPos("minecraft:overworld", 12, 64, -3), d.bust(4, 2));
        assertEquals(List.of("Le Pont", "Tour"), d.tasks(4));
        assertEquals(5, d.score(4, "Le Pont", "Ensuri"));
    }

    @Test
    void theOriginalIsKeptAndTheFileRewrittenOnce() throws IOException {
        load(MOD_2_FILE);
        Path file = dir.resolve("config.yml");
        assertEquals(MOD_2_FILE, Files.readString(dir.resolve("config.yml.v1"), StandardCharsets.UTF_8));
        String v2 = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(v2.contains("version: 2"), v2);

        // Loading the converted file again changes nothing
        CraftMasterData again = new CraftMasterData(file, LoggerFactory.getLogger("test"));
        again.load(null);
        again.save();
        assertEquals(v2, Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(again.history().isEmpty(), "loading is not an undoable change");
    }

    @Test
    void anEmptyFileIsNotMigrated() throws IOException {
        CraftMasterData d = load("");
        assertTrue(d.seasons().isEmpty());
        assertFalse(Files.exists(dir.resolve("config.yml.v1")));
    }
}
