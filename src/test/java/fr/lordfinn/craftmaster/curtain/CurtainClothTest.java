package fr.lordfinn.craftmaster.curtain;

import fr.lordfinn.craftmaster.curtain.CurtainCloth.Pleat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurtainClothTest {

    private static final double EPS = 1e-9;

    @Test
    void closedCurtainIsTheBlockModel() {
        // 4 pleats of 4 px per block, alternating z 6..8 / 8..10 like curtain_template.json
        for (int columns : new int[]{1, 2, 7, 15}) {
            for (int j = 0; j < columns * 4; j++) {
                Pleat p = CurtainCloth.pleat(j, columns, 0);
                assertEquals(j * 4, p.x0(), EPS, "pleat " + j + " of " + columns);
                assertEquals(j * 4 + 4, p.x1(), EPS);
                assertEquals(j % 2 == 0 ? 6 : 8, p.z0(), EPS);
                assertEquals(j % 2 == 0 ? 8 : 10, p.z1(), EPS);
            }
        }
    }

    @Test
    void openCurtainGathersAgainstBothSides() {
        int columns = 15;
        double width = columns * 16, half = width / 2, bundle = CurtainCloth.bundleWidth(half);
        assertTrue(bundle >= CurtainCloth.MIN_BUNDLE && bundle < half / 4);
        for (int j = 0; j < columns * 4; j++) {
            Pleat p = CurtainCloth.pleat(j, columns, 1);
            boolean left = j < columns * 2;
            if (left) assertTrue(p.x1() <= bundle + EPS, "left pleat " + j + " in the left bundle");
            else assertTrue(p.x0() >= width - bundle - EPS, "right pleat " + j + " in the right bundle");
            // Deeper folds once gathered
            assertEquals(4, p.z1() - p.z0(), EPS);
        }
        // The middle is clear
        assertTrue(CurtainCloth.pleat(columns * 2 - 1, columns, 1).x1() < half - 50);
    }

    @Test
    void pleatsStayInOrderAndTouchWhileSliding() {
        int columns = 9;
        for (double f = 0; f <= 1; f += 0.05) {
            for (int j = 1; j < columns * 4; j++) {
                if (j == columns * 2) continue;                     // the parting in the middle
                Pleat a = CurtainCloth.pleat(j - 1, columns, f), b = CurtainCloth.pleat(j, columns, f);
                assertEquals(a.x1(), b.x0(), 1e-9, "no gap nor overlap at f=" + f);
                assertTrue(b.width() > 0);
            }
            // Both halves are mirror images
            Pleat first = CurtainCloth.pleat(0, columns, f), last = CurtainCloth.pleat(columns * 4 - 1, columns, f);
            assertEquals(first.x0(), columns * 16 - last.x1(), EPS);
            assertEquals(first.width(), last.width(), EPS);
        }
    }

    @Test
    void slideIsContinuousAndEased() {
        // Small time steps give small moves (no column-sized jumps) and the fabric starts and stops softly
        int columns = 15;
        double previous = CurtainCloth.pleat(29, columns, 0).x1();
        double biggest = 0;
        int duration = CurtainCloth.durationTicks(columns, Curtains.STEP_TICKS);
        for (double t = 0.25; t <= duration; t += 0.25) {
            double x = CurtainCloth.pleat(29, columns, CurtainCloth.progress(0, 1, t, duration)).x1();
            biggest = Math.max(biggest, Math.abs(x - previous));
            previous = x;
        }
        assertTrue(biggest < 3, "the leading edge moves less than 3 px per quarter tick, moved " + biggest);
        assertEquals(0, CurtainCloth.ease(0), EPS);
        assertEquals(1, CurtainCloth.ease(1), EPS);
        assertTrue(CurtainCloth.ease(0.05) < 0.01 && CurtainCloth.ease(0.95) > 0.99);
        assertEquals(0.5, CurtainCloth.ease(0.5), EPS);
    }

    @Test
    void durationMatchesTheServersColumns() {
        // The server switches one column every STEP_TICKS from the middle: 15 columns = 7 steps after the first
        assertEquals(8 * Curtains.STEP_TICKS, CurtainCloth.durationTicks(15, Curtains.STEP_TICKS));
        assertEquals(Curtains.STEP_TICKS, CurtainCloth.durationTicks(1, Curtains.STEP_TICKS));
        // Reversing midway goes back from where the fabric is
        assertEquals(0.5, CurtainCloth.progress(0.5, 0, 0, 10), EPS);
        assertEquals(0, CurtainCloth.progress(0.5, 0, 10, 10), EPS);
    }
}
