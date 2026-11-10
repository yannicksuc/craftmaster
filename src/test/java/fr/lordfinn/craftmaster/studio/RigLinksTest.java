package fr.lordfinn.craftmaster.studio;

import org.junit.jupiter.api.Test;

import static fr.lordfinn.craftmaster.studio.RigLinks.*;
import static org.junit.jupiter.api.Assertions.*;

/** Rig rods: what they link to when placed, what is drawn, and how links turn with structures. */
class RigLinksTest {

    private static int of(int... sides) {
        int m = 0;
        for (int s : sides) m |= bit(s);
        return m;
    }

    @Test
    void placedRodLinksToEveryRodAround() {
        // A beam's next rod, beside a tower and above a tripod
        assertEquals(of(WEST, DOWN), placement(of(WEST, DOWN), 0, WEST, false));
        assertEquals(of(NORTH, SOUTH, EAST, WEST), placement(of(NORTH, SOUTH, EAST, WEST), 0, NORTH, false));
    }

    @Test
    void sneakingLinksOnlyToTheClickedBlock() {
        assertEquals(of(WEST), placement(of(WEST, NORTH, DOWN), 0, WEST, true));
        assertEquals(of(UP), placement(0, of(UP), UP, true));      // under a ceiling: its plate
        assertEquals(0, placement(of(NORTH), 0, WEST, true));     // against air / something it does not link to
        assertEquals(0, placement(of(NORTH), 0, -1, true));
    }

    @Test
    void solidBlocksOnlyWhenPlacedAgainstOrAtABeamEnd() {
        // On the floor: mounted on it (a lone rod, drawn upright)
        assertEquals(of(DOWN), placement(0, of(DOWN, NORTH), DOWN, false));
        // A beam rod placed against a rod with a wall on its other side: mounted on the wall too
        assertEquals(of(WEST, EAST), placement(of(WEST), of(EAST, DOWN), WEST, false));
        // Walls around that it was not placed against and that do not end a beam: no plate
        assertEquals(of(UP), placement(of(UP), of(NORTH, SOUTH), UP, false));
    }

    @Test
    void straightRodsGoThroughTheirBlock() {
        assertEquals(of(DOWN, UP), arms(0));               // lone: upright, like the original rod
        assertEquals(of(DOWN, UP), arms(of(DOWN)));        // on a tripod, top of a tower
        assertEquals(of(EAST, WEST), arms(of(EAST)));      // end of a beam
        assertEquals(of(NORTH, SOUTH), arms(of(NORTH, SOUTH)));
        assertFalse(knuckle(0));
        assertFalse(knuckle(of(EAST, WEST)));
    }

    @Test
    void junctionsDrawOnlyTheirArmsAndAClamp() {
        int corner = of(DOWN, EAST);
        assertEquals(corner, arms(corner));
        assertTrue(knuckle(corner));
        int t = of(WEST, EAST, DOWN);
        assertEquals(t, arms(t));
        assertTrue(knuckle(t));
        int cross3d = of(NORTH, SOUTH, WEST, EAST, UP, DOWN);
        assertEquals(cross3d, arms(cross3d));
        assertTrue(knuckle(cross3d));
        assertTrue(knuckle(of(NORTH, EAST)));
    }

    @Test
    void everyLinkIsDrawn() {
        for (int links = 0; links < 64; links++) assertEquals(links, arms(links) & links, "links " + links);
    }

    @Test
    void linksTurnAndMirrorWithStructures() {
        assertEquals(of(EAST, UP), rotateY(of(NORTH, UP), 1));
        assertEquals(of(SOUTH), rotateY(of(NORTH), 2));
        assertEquals(of(WEST), rotateY(of(NORTH), 3));
        assertEquals(of(NORTH), rotateY(of(WEST), 1));
        for (int links = 0; links < 64; links++) assertEquals(links, rotateY(links, 4));
        assertEquals(of(SOUTH, EAST), mirror(of(NORTH, EAST), true));
        assertEquals(of(NORTH, WEST, DOWN), mirror(of(NORTH, EAST, DOWN), false));
    }
}
