package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReplayViewingTest {

    @Test
    void markersKeepTheirKindPlayerAndZone() {
        var o = com.google.gson.JsonParser.parseString("""
                {"label": "Manche 1", "started": "2026-11-13T10:00:00Z", "seconds": 60,
                 "zones": [{"name": "A", "folder": "f"}],
                 "markers": [{"t": 3, "name": "Ensuri meurt", "color": "#E57373", "kind": "death", "player": "Ensuri", "zone": "A"},
                             {"t": 5, "name": "Vieux repère", "color": "#FFFFFF"}]}""").getAsJsonObject();
        ReplayViewing.Entry e = ReplayViewing.parse(o);
        Marker m = e.colors().get("Ensuri meurt");
        assertEquals("death", m.kind());
        assertEquals("Ensuri", m.player());
        assertEquals("A", m.zone());
        assertEquals(0xE57373, m.color());
        assertEquals("", e.colors().get("Vieux repère").kind());
    }

    @Test
    void sameInstantInAnotherFile() {
        // The zone's file started at tick 1000, the player's 3 ticks later: 10 s into the zone = 9.85 s into the player's
        ReplayClock zone = new ReplayClock(1000, List.of()), player = new ReplayClock(1003, List.of());
        assertEquals(9_850, ReplayViewing.alignedMs(10_000, zone, player));
        assertEquals(10_150, ReplayViewing.alignedMs(10_000, player, zone));
        // Unknown clock (older index): same time
        assertEquals(10_000, ReplayViewing.alignedMs(10_000, ReplayClock.UNKNOWN, zone));
        assertEquals(0, ReplayViewing.alignedMs(50, zone, new ReplayClock(1010, List.of())));
    }

    @Test
    void lagDoesNotDrift() {
        // A lagging server: 600 s of file but only 11 000 ticks (550 s). The samples keep the timer right
        ReplayClock c = new ReplayClock(1000, List.of(new long[]{0, 1000}, new long[]{300_000, 6500}, new long[]{600_000, 12_000}));
        assertEquals(12_000, c.tickAt(600_000), 1e-9);
        assertEquals(6500 + 2750, c.tickAt(450_000), 1e-9);
        assertEquals(450_000, c.msAt(9250));
        // Without samples it would be 1000 + 12 000 = 13 000 at the end: 50 s off
        assertEquals(13_000, new ReplayClock(1000, List.of()).tickAt(600_000), 1e-9);
        // The client's tick0, valid from the current time on
        assertEquals(12_000 - 600_000 / 50.0, ReplayViewing.tick0At(c, 600_000), 1e-9);
        assertEquals(-1, ReplayViewing.tick0At(ReplayClock.UNKNOWN, 5_000), 1e-9);
    }

    @Test
    void filesAreMatchedByTheirStartInTheirName() {
        long t = ReplayViewing.nameTime("2026-11-13--01-43-20.zip");
        assertEquals(java.time.LocalDateTime.of(2026, 11, 13, 1, 43, 20).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), t);
        assertEquals(-1, ReplayViewing.nameTime("recording.mcpr"));
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
