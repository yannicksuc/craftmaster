package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Players, busts and labels see the published scores; organisers the live ones. */
class PublishedScoresTest {

    @TempDir
    Path dir;

    private CraftMasterData data;

    @BeforeEach
    void setUp() {
        data = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        data.load(null);
        data.addTask(1, "Pont");
        data.addTask(1, "Tour");
    }

    @Test
    void nothingIsPublishedBeforeTheFirstPublish() {
        data.setScore(1, "Pont", "Bob", 3);
        assertEquals(1, data.ranking(1, false).size());
        assertTrue(data.ranking(1, true).isEmpty());
        assertNull(data.playerAtRank(1, 1, true));
        assertEquals(3, data.season(1).unpublishedDelta("Bob"));
    }

    @Test
    void publishingFreezesACopy() {
        data.setScore(1, "Pont", "Bob", 3);
        data.setScore(1, "Pont", "Alice", 5);
        data.publish(1);
        data.setScore(1, "Tour", "Bob", 4);
        assertEquals(List.of("Bob", "Alice"), data.ranking(1, false).stream().map(CraftMasterData.Entry::player).toList());
        assertEquals(List.of("Alice", "Bob"), data.ranking(1, true).stream().map(CraftMasterData.Entry::player).toList());
        assertEquals(3, data.publishedTotal(1, "Bob"));
        assertEquals(4, data.season(1).unpublishedDelta("Bob"));
        assertEquals(0, data.season(1).unpublishedDelta("Alice"));
        data.publish(1);
        assertEquals("Bob", data.playerAtRank(1, 1, true));
    }

    @Test
    void publishedScoresAreLabelledByRound() {
        data.setScore(1, "Pont", "Bob", 3);
        data.setScore(1, "Tour", "Bob", 1);
        data.publish(1);
        assertEquals(Map.of("Pont", 3, "Tour", 1), data.season(1).published.scores().get("Bob"));
    }

    @Test
    void severalGamesAddUpAndArePrefixed() {
        data.setScore(1, "Pont", "Bob", 3);
        data.change("Partie 2", () -> {
            Season s = data.season(1);
            Partie p2 = new Partie(2, "Finale");
            Manche r = new Manche(s.newMancheId("Pont"), "Pont");
            p2.manches.add(r);
            p2.setScore(r.id, "Bob", 10);
            s.parties.put(2, p2);
        });
        assertEquals("pont-2", data.season(1).parties.get(2).manches.getFirst().id, "ids are unique in the season");
        assertEquals(13, data.total(1, "Bob"));
        assertEquals(List.of("Partie 1 · Pont", "Partie 1 · Tour", "Finale · Pont"), data.season(1).activeLabels());
        data.publish(1);
        assertEquals(13, data.publishedTotal(1, "Bob"));
    }

    @Test
    void publishedScoresSurviveARestart() {
        data.setScore(1, "Pont", "Bob", 3);
        data.publish(1);
        data.setScore(1, "Pont", "Bob", 8);
        CraftMasterData d = new CraftMasterData(dir.resolve("config.yml"), LoggerFactory.getLogger("test"));
        d.load(null);
        assertEquals(3, d.publishedTotal(1, "Bob"));
        assertEquals(8, d.total(1, "Bob"));
    }
}
