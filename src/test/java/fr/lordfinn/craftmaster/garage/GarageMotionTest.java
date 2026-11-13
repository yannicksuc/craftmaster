package fr.lordfinn.craftmaster.garage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GarageMotionTest {

    @Test
    void easeStartsAndEndsSoftly() {
        assertEquals(0, GarageMotion.ease(-1));
        assertEquals(0, GarageMotion.ease(0));
        assertEquals(0.5, GarageMotion.ease(0.5), 1e-9);
        assertEquals(1, GarageMotion.ease(1));
        assertEquals(1, GarageMotion.ease(3));
        // Slow at both ends, fastest in the middle
        assertTrue(GarageMotion.ease(0.1) < 0.1);
        assertTrue(GarageMotion.ease(0.9) > 0.9);
    }

    @Test
    void durationFollowsTheDistance() {
        assertEquals(36, GarageMotion.duration(0, 3, 12));
        assertEquals(18, GarageMotion.duration(3, 1.5, 12));
        // Never shorter than 4 ticks, even turning back right at the start
        assertEquals(4, GarageMotion.duration(0, 0.05, 12));
    }

    @Test
    void openingGoesFromOneEndToTheOther() {
        assertEquals(0, GarageMotion.opening(0, 3, 100, 36, 90));
        assertEquals(0, GarageMotion.opening(0, 3, 100, 36, 100));
        assertEquals(1.5, GarageMotion.opening(0, 3, 100, 36, 118), 1e-9);
        assertEquals(3, GarageMotion.opening(0, 3, 100, 36, 136));
        assertEquals(3, GarageMotion.opening(0, 3, 100, 36, 500));
        // Closing from half way
        assertEquals(0, GarageMotion.opening(1.5, 0, 0, 18, 18));
    }

    @Test
    void blocksGoIntoTheLintel() {
        // 3-high door: closed, every block is whole where it was built
        assertArrayEquals(new double[]{0, 1}, GarageMotion.slice(0, 3, 0), 1e-9);
        assertArrayEquals(new double[]{2, 1}, GarageMotion.slice(2, 3, 0), 1e-9);
        // Half a block up: the top row is half in the lintel, squeezed under it
        assertArrayEquals(new double[]{2.5, 0.5}, GarageMotion.slice(2, 3, 0.5), 1e-9);
        assertArrayEquals(new double[]{0.5, 1}, GarageMotion.slice(0, 3, 0.5), 1e-9);
        // One block up: the top row is gone
        assertNull(GarageMotion.slice(2, 3, 1));
        // Open: nothing left
        assertNull(GarageMotion.slice(0, 3, 3));
        // The visible part never goes above the doorway
        for (double s = 0; s < 3; s += 0.07)
            for (int row = 0; row < 3; row++) {
                double[] sl = GarageMotion.slice(row, 3, s);
                if (sl != null) assertTrue(sl[0] + sl[1] <= 3 + 1e-9);
            }
    }

    @Test
    void cellsAreFreedFromTheBottom() {
        // Closed: every cell of the doorway blocks
        assertTrue(GarageMotion.blocks(0, 0, 0));
        assertTrue(GarageMotion.blocks(2, 0, 0));
        // The bottom cell frees once the panel has nearly risen out of it
        assertTrue(GarageMotion.blocks(0, 0, 0.8));
        assertFalse(GarageMotion.blocks(0, 0, 0.95));
        assertTrue(GarageMotion.blocks(1, 0, 0.95));
        // Open: none
        for (int row = 0; row < 3; row++) assertFalse(GarageMotion.blocks(row, 0, 3));
        // A column starting higher (an arch): its lowest block counts
        assertFalse(GarageMotion.blocks(0, 1, 0));
        assertTrue(GarageMotion.blocks(1, 1, 0));
    }
}
