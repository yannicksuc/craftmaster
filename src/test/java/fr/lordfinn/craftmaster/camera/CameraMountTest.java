package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.camera.CameraMount.Kind;
import fr.lordfinn.craftmaster.camera.CameraMount.Support;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraMountTest {

    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    @Test
    void topFacesStandTheCamera() {
        assertEquals(CameraMount.FLOOR, CameraMount.choose(UP, Support.BLOCK));
        // Any top face, sturdy or not, as before the mounts
        assertEquals(CameraMount.FLOOR, CameraMount.choose(UP, null));
        assertEquals(Kind.TRIPOD, CameraMount.choose(UP, Support.TRIPOD).kind());
        assertEquals(Kind.ROD_TOP, CameraMount.choose(UP, Support.ROD).kind());
    }

    @Test
    void undersideHangsIt() {
        CameraMount ceiling = CameraMount.choose(DOWN, Support.BLOCK);
        assertEquals(Kind.CEILING, ceiling.kind());
        assertTrue(ceiling.hung());
        assertEquals(Kind.ROD_UNDER, CameraMount.choose(DOWN, Support.ROD).kind());
        assertNull(CameraMount.choose(DOWN, null));
        assertNull(CameraMount.choose(DOWN, Support.TRIPOD));
    }

    @Test
    void sidesPutItOnAnArmTowardTheSupport() {
        // Clicked the south face of a wall: the camera is south of it, its arm goes north
        CameraMount wall = CameraMount.choose(SOUTH, Support.BLOCK);
        assertEquals(Kind.WALL, wall.kind());
        assertEquals(NORTH, wall.side());
        CameraMount rod = CameraMount.choose(WEST, Support.ROD);
        assertEquals(Kind.ROD_SIDE, rod.kind());
        assertEquals(EAST, rod.side());
        assertNull(CameraMount.choose(EAST, null));
        assertNull(CameraMount.choose(EAST, Support.TRIPOD));
    }

    @Test
    void lensAboveOrBelowTheAnchor() {
        assertEquals(1.5, CameraMount.FLOOR.lensDy(), 1e-9);
        assertTrue(CameraMount.choose(UP, Support.ROD).lensDy() > 0);
        assertTrue(CameraMount.choose(DOWN, Support.ROD).lensDy() < 0);
        assertEquals(CameraMount.MOUNTED_LENS, CameraMount.choose(SOUTH, Support.BLOCK).lensDy(), 1e-9);
        assertTrue(CameraMount.FLOOR.legs());
        assertFalse(CameraMount.choose(UP, Support.TRIPOD).legs());
    }

    @Test
    void anchors() {
        // Floor: where it was clicked
        assertArrayEquals(new double[]{3.2, 64, -7.7}, CameraMount.FLOOR.anchor(3, 63, -8, 3.2, 64, -7.7), 1e-9);
        // Stand / rod: the top of the block, in its middle
        assertArrayEquals(new double[]{3.5, 65, -7.5}, CameraMount.choose(UP, Support.TRIPOD).anchor(3, 64, -8, 3.1, 65, -7.9), 1e-9);
        // Under a rod: its underside; under a ceiling: the end of the drop pole, under the click
        assertArrayEquals(new double[]{3.5, 70, -7.5}, CameraMount.choose(DOWN, Support.ROD).anchor(3, 70, -8, 3.4, 70, -7.6), 1e-9);
        assertArrayEquals(new double[]{3.25, 69.5, -7.75}, CameraMount.choose(DOWN, Support.BLOCK).anchor(3, 70, -8, 3.25, 70, -7.75), 1e-9);
        // Wall at x = 10, clicked on its east face: the camera stands in the block east of it, on its arm's tube
        CameraMount wall = CameraMount.choose(EAST, Support.BLOCK);
        assertArrayEquals(new double[]{11.5, 64 + 9 / 16.0, 0.5}, wall.anchor(10, 64, 0, 11, 64.3, 0.2), 1e-9);
        CameraMount rodSide = CameraMount.choose(NORTH, Support.ROD);
        assertArrayEquals(new double[]{0.5, 64 + 9 / 16.0, -0.5}, rodSide.anchor(0, 64, 0, 0.5, 64.5, 0), 1e-9);
    }

    @Test
    void supportBlockFromTheAnchor() {
        int[][] blocks = {{3, 64, -8}, {-4, 70, 12}};
        for (int[] b : blocks) {
            for (int face = 0; face < 6; face++) {
                for (Support support : Support.values()) {
                    CameraMount m = CameraMount.choose(face, support);
                    if (m == null || m.legs()) continue;
                    double[] a = m.anchor(b[0], b[1], b[2], b[0] + 0.3, b[1] + (face == UP ? 1 : 0), b[2] + 0.6);
                    assertArrayEquals(b, m.supportBlock(a[0], a[1], a[2]), m.id() + " on " + b[0] + "," + b[1] + "," + b[2]);
                }
            }
        }
        assertNull(CameraMount.FLOOR.supportBlock(0, 64, 0));
    }

    @Test
    void idsRoundTripAndOldCamerasStayOnTheFloor() {
        for (int face = 0; face < 6; face++)
            for (Support support : Support.values()) {
                CameraMount m = CameraMount.choose(face, support);
                if (m != null) assertEquals(m, CameraMount.parse(m.id()));
            }
        assertEquals("wall:north", CameraMount.choose(SOUTH, Support.BLOCK).id());
        assertEquals(CameraMount.FLOOR, CameraMount.parse(""));
        assertEquals(CameraMount.FLOOR, CameraMount.parse(null));
        assertEquals(CameraMount.FLOOR, CameraMount.parse("wall"));
        assertEquals(CameraMount.FLOOR, CameraMount.parse("wall:up"));
        assertEquals(CameraMount.FLOOR, CameraMount.parse("banana"));
    }

    @Test
    void spotsKeepTheirMount() {
        CameraMount rod = CameraMount.choose(DOWN, Support.ROD);
        CameraSpot s = new CameraSpot(1, "A", "", "w", 0, 64, 0, 0, 0, 0, null, CameraSpot.STUDIO, "", rod);
        assertEquals(rod, s.withRotation(40, 10).mount());
        assertEquals(rod, s.withName("B").withFov(40).withMotion("pan").withRole("large").mount());
        assertEquals(rod, s.withLieu("pont", "", 2).mount());
        // Moved to the player's eyes (/cam here): back on a floor tripod
        assertEquals(CameraMount.FLOOR, s.withPlace("w", 1, 2, 3, 0, 0).mount());
        // Old constructor: on the floor
        assertEquals(CameraMount.FLOOR, new CameraSpot(1, "A", "", "w", 0, 64, 0, 0, 0, 0, null).mount());
    }
}
