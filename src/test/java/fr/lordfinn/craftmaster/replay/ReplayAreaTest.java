package fr.lordfinn.craftmaster.replay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReplayAreaTest {

    @Test
    void boxAroundPiecesWithMargin() {
        ReplayArea.Chunks c = ReplayArea.of(List.of(
                ReplayArea.Box.point("minecraft:overworld", 5, 5),
                new ReplayArea.Box("minecraft:overworld", -20, 40, 30, 70)), 2, 40);
        // x: -20 → chunk -2, 30 → 1 ; z: 5 → 0, 70 → 4 ; then 2 chunks around
        assertEquals(new ReplayArea.Chunks("minecraft:overworld", -4, -2, 3, 6), c);
        assertEquals(8, c.width());
    }

    @Test
    void worldWithMostPiecesWins() {
        ReplayArea.Chunks c = ReplayArea.of(List.of(
                ReplayArea.Box.point("minecraft:the_nether", 1000, 1000),
                ReplayArea.Box.point("minecraft:overworld", 0, 0),
                ReplayArea.Box.point("minecraft:overworld", 16, 16)), 0, 40);
        assertEquals(new ReplayArea.Chunks("minecraft:overworld", 0, 0, 1, 1), c);
    }

    @Test
    void tooWideIsCutAroundTheCentre() {
        ReplayArea.Chunks c = ReplayArea.of(List.of(
                ReplayArea.Box.point("w", 0, 0), ReplayArea.Box.point("w", 16 * 99, 0)), 0, 10);
        assertEquals(10, c.width());
        assertEquals(45, c.fromX());
        assertNull(ReplayArea.of(List.of(), 2, 40));
    }

    @Test
    void folderNames() {
        assertEquals("cm-s1-p3-m2-le-pont-ensuri", ReplayArea.folder(1, 3, 2, "le-pont", "Ensuri"));
        assertEquals("cm-s1-p3-m2-epreuve-a", ReplayArea.folder(1, 3, 2, "Épreuve à", null));
        assertEquals("cm-s2-p1", ReplayArea.folder(2, 1, 0, "", null));
        assertEquals("cm-s2-p1-lord_finn", ReplayArea.folder(2, 1, 0, null, "Lord_Finn"));
    }
}
