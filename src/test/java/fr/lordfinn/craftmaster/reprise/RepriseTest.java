package fr.lordfinn.craftmaster.reprise;

import fr.lordfinn.craftmaster.trial.BuzzerLock;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepriseTest {

    @Test
    void stopwatchStandsStillWhileFrozenAndGoesOnFromThere() {
        Stopwatch w = new Stopwatch();
        w.start("Ensuri", 1_000);
        w.freeze(43_000);
        assertEquals(42_000, w.peek("Ensuri", 100_000));
        // Started during the freeze: zero until the go
        w.start("Gab", 50_000);
        assertEquals(0, w.peek("Gab", 90_000));
        w.thaw(200_000);
        assertFalse(w.frozen());
        assertEquals(42_500, w.peek("Ensuri", 200_500));
        assertEquals(500, w.peek("Gab", 200_500));
        assertEquals(43_000, w.stop("Ensuri", 201_000));
    }

    @Test
    void stopwatchRestoredAfterARestartKeepsItsTime() {
        Stopwatch before = new Stopwatch();
        before.start("Ensuri", 0);
        before.start("Gab", 0);
        before.stop("Gab", 12_300);
        Map<String, Long> running = before.runningTimes(61_000);

        Stopwatch after = new Stopwatch();
        after.freeze(5_000_000);
        after.restore(running, before.lastTimes(), 5_000_000);
        assertEquals(61_000, after.peek("Ensuri", 5_900_000));
        assertEquals(12_300, after.last("Gab"));
        after.thaw(6_000_000);
        assertEquals(62_000, after.peek("Ensuri", 6_001_000));
    }

    @Test
    void buzzerDelaysIgnoreTheFreeze() {
        BuzzerLock lock = new BuzzerLock();
        lock.press("Ensuri", 10_000);
        lock.freeze(10_400);
        assertEquals(400, lock.sinceFirst(99_000));
        lock.thaw(500_000);
        BuzzerLock.Press second = lock.press("Gab", 500_100);
        assertEquals(500, second.delayMs());

        BuzzerLock restored = new BuzzerLock();
        restored.restore(lock.presses(), 700, 1_000_000);
        assertEquals("Ensuri", restored.winner());
        assertEquals(1_000, restored.press("Ana", 1_000_300).delayMs());
    }

    @Test
    void stateRoundTripsThroughJson() {
        RepriseState s = new RepriseState();
        s.season = 2;
        s.partie = 3;
        s.manche = "pont";
        s.timer = new RepriseState.Timer("Le Pont", 6000, 4321);
        s.stopwatches.put("Ensuri", 61_234L);
        s.buzzes.add(new RepriseState.Buzz(1, "Gab", 0));
        s.sinceFirstBuzz = 900;
        s.tablets.add(new RepriseState.Tablet("minecraft:overworld", 1, 64, -3, true, 25_700));

        RepriseState r = RepriseState.fromJson(s.toJson());
        assertNotNull(r);
        assertTrue(r.sameRound(2, 3, "pont"));
        assertFalse(r.sameRound(2, 3, "autre"));
        assertEquals(4321, r.timer.leftTicks);
        assertEquals(61_234L, r.stopwatches.get("Ensuri"));
        assertEquals("Gab", r.buzzes.getFirst().player);
        assertEquals(List.of("minecraft:overworld 1 64 -3"), r.tablets.stream().map(RepriseState.Place::key).toList());
        assertTrue(r.tablets.getFirst().running);
        assertEquals(25_700, r.tablets.getFirst().elapsedMs);
        assertFalse(r.empty());
    }

    @Test
    void brokenOrEmptyFilesGiveNothing() {
        assertNull(RepriseState.fromJson(""));
        assertNull(RepriseState.fromJson("{ pas du json"));
        RepriseState r = RepriseState.fromJson("{\"tablets\":null,\"stopwatches\":{\"A\":null}}");
        assertNotNull(r);
        assertTrue(r.tablets.isEmpty());
        assertTrue(r.stopwatches.isEmpty());
        assertTrue(r.empty());
    }
}
