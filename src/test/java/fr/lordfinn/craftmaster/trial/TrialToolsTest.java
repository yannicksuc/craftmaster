package fr.lordfinn.craftmaster.trial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Stopwatches, zones, buzzers, prop settings and memory, weights. */
class TrialToolsTest {

    @Test
    void stopwatchesRunOnTheirOwn() {
        Stopwatch w = new Stopwatch();
        w.start("Ensuri", 1_000);
        w.start("Cyllu", 5_000);
        assertEquals(List.of("Ensuri", "Cyllu"), w.runningPlayers());
        assertEquals(9_000, w.peek("Ensuri", 10_000));
        assertTrue(w.isRunning("Ensuri"));
        assertEquals(41_500, w.stop("Ensuri", 42_500));
        assertFalse(w.isRunning("Ensuri"));
        assertEquals(41_500, w.last("Ensuri"));
        assertEquals(-1, w.stop("Ensuri", 50_000));
        assertEquals(-1, w.peek("JustEdible", 50_000));
        assertEquals("Cyllu", w.find("cyllu"));
        // A restart clears the last time
        w.start("Ensuri", 60_000);
        assertEquals(-1, w.last("Ensuri"));
        w.resetAll();
        assertTrue(w.runningPlayers().isEmpty());
    }

    @Test
    void stopwatchFormat() {
        assertEquals("0,0 s", Stopwatch.format(0));
        assertEquals("42,3 s", Stopwatch.format(42_300));
        assertEquals("42,4 s", Stopwatch.format(42_351));
        assertEquals("59,9 s", Stopwatch.format(59_940));
        assertEquals("1:00,0", Stopwatch.format(59_960));
        assertEquals("1:02,5", Stopwatch.format(62_500));
        assertEquals("1:02:03,0", Stopwatch.format(3_723_000));
    }

    @Test
    void zonesIncludeBothCornerBlocks() {
        Zone z = new Zone("Cuisine", "minecraft:overworld", 10, 64, 10, 0, 60, 0, List.of(), true);
        assertEquals(0, z.x0());
        assertEquals(10, z.x1());
        assertEquals(11, z.sizeX());
        assertTrue(z.contains("minecraft:overworld", 0, 60, 0));
        assertTrue(z.contains("minecraft:overworld", 10.99, 64.5, 10.5));
        assertFalse(z.contains("minecraft:overworld", 11.01, 62, 5));
        assertFalse(z.contains("minecraft:the_nether", 5, 62, 5));
        assertEquals(0, z.distanceOutside(5, 62, 5));
        assertEquals(2, z.distanceOutside(13, 62, 5), 1e-9);
    }

    @Test
    void zonesWatchTheirPlayersOrEveryContestant() {
        List<String> cast = List.of("Ensuri", "Cyllu");
        Zone all = new Zone("a", "w", 0, 0, 0, 1, 1, 1, List.of(), true);
        assertTrue(all.watches("ensuri", cast));
        assertFalse(all.watches("LordFinn", cast));
        Zone one = all.withPlayers(List.of("Cyllu"));
        assertFalse(one.watches("Ensuri", cast));
        assertTrue(one.watches("Cyllu", cast));
        assertFalse(one.withEnabled(false).watches("Cyllu", cast));
        assertEquals("la-cuisine-2", Zone.normalizeName("La Cuisine #2"));
        assertEquals("epreuve", Zone.normalizeName("Épreuve"));
    }

    @Test
    void firstBuzzWinsTheOthersAreRecorded() {
        BuzzerLock b = new BuzzerLock();
        assertFalse(b.locked());
        BuzzerLock.Press p1 = b.press("Ensuri", 1_000);
        assertTrue(p1.first());
        assertTrue(b.locked());
        assertNull(b.press("ensuri", 1_100), "a second press of the same player is ignored");
        BuzzerLock.Press p2 = b.press("Cyllu", 1_250);
        assertEquals(2, p2.order());
        assertEquals(250, p2.delayMs());
        assertEquals("Ensuri", b.winner());
        b.reset();
        assertFalse(b.locked());
        assertTrue(b.press("Cyllu", 5_000).first());
    }

    @Test
    void propSettingsRoundTripAndAreClamped() {
        PropConfig c = new PropConfig();
        c.name = "Le coffre";
        c.scale = 50;
        c.yaw = -90;
        c.message = "Bravo {player} !";
        c.teleport = true;
        c.tpX = 1.5;
        c.pulseDy = 99;
        c.endAttempt = true;
        PropConfig back = PropConfig.fromJson(c.toJson());
        assertEquals("Le coffre", back.name);
        assertEquals(8f, back.scale);
        assertEquals(270f, back.yaw);
        assertEquals("Bravo {player} !", back.message);
        assertTrue(back.teleport);
        assertEquals(1.5, back.tpX);
        assertEquals(16, back.pulseDy);
        assertTrue(back.endAttempt);
        // Garbage keeps the defaults
        assertEquals(1f, PropConfig.fromJson("not json").scale);
        assertEquals(1f, PropConfig.fromJson("{\"scale\":\"big\"}").scale);
    }

    @Test
    void propMemoryCountsAndRemembersTheFirst() {
        PropStats s = new PropStats();
        PropStats.Use u = s.use("Ensuri", 1_000, false, 500);
        assertTrue(u.firstEver());
        assertTrue(u.firstForPlayer());
        assertTrue(s.use("Ensuri", 1_200, false, 500).refused(), "cooldown");
        PropStats.Use again = s.use("Ensuri", 1_600, false, 500);
        assertFalse(again.refused());
        assertFalse(again.firstForPlayer());
        assertEquals(2, again.playerCount());
        PropStats.Use other = s.use("Cyllu", 1_700, false, 500);
        assertFalse(other.firstEver());
        assertTrue(other.firstForPlayer());
        assertEquals(3, s.total());
        assertEquals("Ensuri", s.first());
        assertEquals(List.of("Ensuri", "Cyllu"), s.order());
        assertTrue(s.use("Cyllu", 9_000, true, 0).refused(), "once per player");

        PropStats back = PropStats.fromJson(s.toJson());
        assertEquals("Ensuri", back.first());
        assertEquals(1_000, back.firstTime());
        assertEquals(2, back.clicks().get("Ensuri"));
        assertEquals(3, back.total());
        back.reset();
        assertEquals("", back.first());
        assertEquals(0, back.total());
    }

    @Test
    void weights() {
        assertTrue(Weights.perItem("minecraft:netherite_block", true) > Weights.perItem("minecraft:iron_block", true));
        assertTrue(Weights.perItem("minecraft:iron_block", true) > Weights.perItem("minecraft:iron_ingot", false));
        assertTrue(Weights.perItem("minecraft:feather", false) < 0.01);
        assertEquals(Weights.perItem("minecraft:gold_ingot", false) * 3, Weights.of("minecraft:gold_ingot", 3, false), 1e-9);
        assertEquals(50.0, Weights.perItem("mymod:mystery_block", true));
        assertEquals(0.5, Weights.perItem("mymod:thing", false));
    }
}
