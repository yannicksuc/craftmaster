package fr.lordfinn.craftmaster.portrait;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortraitAnimationTest {

    @Test
    void threePhases() {
        int slide = 40, step = PortraitAnimation.STEP_TICKS;
        assertArrayEquals(new double[]{0, 0}, PortraitAnimation.progress(-5, slide), "before the start: in the row, old column");
        double[] half = PortraitAnimation.progress(step / 2.0, slide);
        assertEquals(0, half[0]);
        assertEquals(0.5, half[1], 1e-9, "stepping out");
        assertArrayEquals(new double[]{0.5, 1}, PortraitAnimation.progress(step + slide / 2.0, slide), 1e-9, "sliding, out of the row");
        double[] back = PortraitAnimation.progress(step + slide + step / 2.0, slide);
        assertEquals(1, back[0]);
        assertEquals(0.5, back[1], 1e-9, "stepping back in");
        assertArrayEquals(new double[]{1, 0}, PortraitAnimation.progress(PortraitAnimation.totalTicks(slide) + 1, slide));
    }

    @Test
    void progressNeverGoesBack() {
        int slide = 55;
        double lastSlide = 0;
        for (double t = 0; t <= PortraitAnimation.totalTicks(slide); t += 0.25) {
            double s = PortraitAnimation.progress(t, slide)[0];
            assertTrue(s >= lastSlide - 1e-12, "the lateral move is monotonic (same easing for all: no overtaking)");
            lastSlide = s;
        }
    }

    @Test
    void slideDurationGrowsWithDistanceWithinBounds() {
        assertEquals(30, PortraitAnimation.slideTicks(0.5));
        assertTrue(PortraitAnimation.slideTicks(5) > PortraitAnimation.slideTicks(3));
        assertEquals(120, PortraitAnimation.slideTicks(50));
    }
}
