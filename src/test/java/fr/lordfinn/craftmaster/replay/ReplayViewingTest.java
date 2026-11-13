package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReplayViewingTest {

    @Test
    void sameInstantInAnotherFile() {
        // The zone's file started at tick 1000, the player's 3 ticks later: 10 s into the zone = 9.85 s into the player's
        assertEquals(9_850, ReplayViewing.alignedMs(10_000, 1000, 1003));
        assertEquals(10_150, ReplayViewing.alignedMs(10_000, 1003, 1000));
        // Unknown time 0 (older index): same time
        assertEquals(10_000, ReplayViewing.alignedMs(10_000, -1, 1000));
        assertEquals(0, ReplayViewing.alignedMs(50, 1000, 1010));
    }

    @Test
    void kotlinDurations() {
        assertEquals(1_500, ServerReplayBridge.durationMs(1_500_000_000L << 1));
        assertEquals(1_500, ServerReplayBridge.durationMs((1_500L << 1) | 1));
    }

    @Test
    void automaticDirectionFollowsTheLastPlayerInTheirTime() {
        List<String> cast = List.of("Ensuri", "Ens", "Joueur2");
        List<Marker> m = List.of(new Marker(1_000, "Manche 1 — Le Pont", 0), new Marker(2_000, "Ensuri +3", 0),
                new Marker(5_000, "Joueur2 ouvre son enveloppe", 0), new Marker(20_000, "Ens -2", 0), new Marker(30_000, "Joueur2 (projecteur)", 0));
        assertNull(ReplayAuto.player(m, 1_500, cast));
        assertEquals("Ensuri", ReplayAuto.player(m, 3_000, cast));
        assertEquals("Joueur2", ReplayAuto.player(m, 6_000, cast));
        assertEquals("Joueur2", ReplayAuto.player(m, 13_900, cast));
        assertNull(ReplayAuto.player(m, 14_100, cast));
        // Points taken away: no close shot
        assertNull(ReplayAuto.player(m, 21_000, cast));
        assertEquals("Joueur2", ReplayAuto.player(m, 31_000, cast));
        // A marker that is only the name (older files): a spotlight
        assertEquals("Ens", ReplayAuto.player(List.of(new Marker(1_000, "Ens", 0)), 9_000, cast));
    }
}
