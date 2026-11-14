package fr.lordfinn.craftmaster.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PublishDiffTest {

    @Test
    void roundsThatMovedTotalsAndRanks() {
        Map<String, Map<String, Integer>> before = Map.of("A", Map.of("Pont", 5), "B", Map.of("Pont", 3));
        Map<String, Map<String, Integer>> after = Map.of("A", Map.of("Pont", 5), "B", Map.of("Pont", 3, "Tour", 4));
        Map<String, PublishDiff.Change> d = PublishDiff.diff(before, after, Set.of("A", "B", "C"));
        PublishDiff.Change b = d.get("B");
        assertEquals(1, b.rounds().size());
        assertEquals(new PublishDiff.Round("Tour", 0, 4), b.rounds().getFirst());
        assertEquals(3, b.totalBefore());
        assertEquals(7, b.totalAfter());
        assertEquals(2, b.rankBefore());
        assertEquals(1, b.rankAfter());
        PublishDiff.Change a = d.get("A");
        assertTrue(a.rounds().isEmpty());
        assertEquals(2, a.rankAfter());
        assertTrue(a.changed(), "lost a place");
        PublishDiff.Change c = d.get("C");
        assertEquals(0, c.totalAfter());
        assertEquals(3, c.rankAfter(), "a player without points is ranked, at 0");
        assertFalse(c.changed());
    }

    @Test
    void firstPublishHasNoRankBefore() {
        Map<String, PublishDiff.Change> d = PublishDiff.diff(null, Map.of("A", Map.of("Pont", -2)), Set.of("A"));
        assertEquals(0, d.get("A").rankBefore());
        assertEquals(-2, d.get("A").rounds().getFirst().delta());
    }

    @Test
    void tiesShareARank() {
        Map<String, Integer> r = PublishDiff.ranks(Map.of("A", Map.of("x", 3), "B", Map.of("x", 3), "C", Map.of("x", 1)), new java.util.LinkedHashSet<>(java.util.List.of("A", "B", "C")));
        assertEquals(1, r.get("A"));
        assertEquals(1, r.get("B"));
        assertEquals(3, r.get("C"));
    }
}
