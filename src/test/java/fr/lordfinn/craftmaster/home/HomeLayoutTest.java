package fr.lordfinn.craftmaster.home;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HomeLayoutTest {

    private static final double W = 480, H = 270, FW = 18 / 16.0, FH = 24 / 16.0;

    @Test
    void projectIsTheInverseOfUnproject() {
        double f = HomeLayout.focal(14, H);
        Vector3d p = HomeLayout.unproject(400, 60, 17, W, H, f);
        assertEquals(-17, p.z, 1e-9);
        double[] s = HomeLayout.project(p, W, H, f);
        assertEquals(400, s[0], 1e-9);
        assertEquals(60, s[1], 1e-9);
        // The centre of the screen is straight ahead
        Vector3d c = HomeLayout.unproject(W / 2, H / 2, 5, W, H, f);
        assertEquals(0, c.x, 1e-12);
        assertEquals(0, c.y, 1e-12);
    }

    @Test
    void focalMatchesTheFieldOfView() {
        double f = HomeLayout.focal(90, H);
        assertEquals(H / 2, f, 1e-9);
        assertTrue(HomeLayout.focal(14, H) > HomeLayout.focal(60, H), "a narrow field is a long focal");
    }

    private static List<Vector3d> defaultRow(int count, double f) {
        return HomeLayout.row(count, 0.85 * W, 0.27 * H, 0.36 * W, 0.2 * H, 0.36 * H, FW, FH, 1.2, 0.8, W, H, f);
    }

    @Test
    void nearestFrameIsWhereAndAsBigAsAsked() {
        double f = HomeLayout.focal(14, H);
        List<Vector3d> row = defaultRow(5, f);
        assertEquals(5, row.size());
        double[] s = HomeLayout.project(row.getFirst(), W, H, f);
        assertEquals(0.85 * W, s[0], 1e-6);
        assertEquals(0.27 * H, s[1], 1e-6);
        // Its height on screen: f · frame height / depth
        assertEquals(0.36 * H, f * FH / -row.getFirst().z, 1e-6);
    }

    @Test
    void rowIsStraightEvenAndRecedes() {
        double f = HomeLayout.focal(14, H);
        List<Vector3d> row = defaultRow(5, f);
        // Evenly spaced in camera space, the asked distance apart
        for (int i = 1; i < row.size(); i++) assertEquals(1.2 * FW, row.get(i).distance(row.get(i - 1)), 1e-6);
        // Farthest ≈ 80% of the nearest on screen (depth ratio)
        assertEquals(0.8, row.getFirst().z / row.getLast().z, 1e-6);
        // Each frame farther than the previous one: smaller on screen (both its top and bottom lines converge)
        for (int i = 1; i < row.size(); i++) assertTrue(-row.get(i).z > -row.get(i - 1).z);
        // The projected centres lie on the line from the first frame toward the vanishing zone, in order
        double[] a = HomeLayout.project(row.getFirst(), W, H, f);
        double ux = 0.36 * W - a[0], uy = 0.2 * H - a[1];
        double prev = 0;
        for (Vector3d p : row) {
            double[] s = HomeLayout.project(p, W, H, f);
            double cross = (s[0] - a[0]) * uy - (s[1] - a[1]) * ux;
            assertEquals(0, cross / Math.hypot(ux, uy), 1e-6, "on the line");
            double along = ((s[0] - a[0]) * ux + (s[1] - a[1]) * uy) / Math.hypot(ux, uy);
            assertTrue(along >= prev - 1e-9, "toward the vanishing zone");
            prev = along;
        }
        // Weak perspective: the row stays on screen, to the left of the first frame
        double[] last = HomeLayout.project(row.getLast(), W, H, f);
        assertTrue(last[0] > 0 && last[0] < a[0]);
    }

    @Test
    void smallRowsAndSpacingTooSmallForTheDepth() {
        double f = HomeLayout.focal(14, H);
        assertTrue(defaultRow(0, f).isEmpty());
        assertEquals(1, defaultRow(1, f).size());
        // The depth difference alone is longer than the asked spacing: the frames stack straight behind
        List<Vector3d> row = HomeLayout.row(3, 400, 70, 170, 50, 90, FW, FH, 0.01, 0.5, W, H, f);
        assertEquals(3, row.size());
        assertTrue(Double.isFinite(row.getLast().x));
        assertEquals(0.5, row.getFirst().z / row.getLast().z, 1e-6);
    }

    @Test
    void facingYawPointsTheNormalAtTheEye() {
        assertEquals(0, HomeLayout.facingYaw(new Vector3d(0, 3, -10)), 1e-9);
        double yaw = HomeLayout.facingYaw(new Vector3d(4, 0, -10));
        assertTrue(yaw < 0, "on the right: turned toward the left to face the eye");
        // The turned normal (sin, cos) points back at the eye: along -p
        double nx = Math.sin(Math.toRadians(yaw)), nz = Math.cos(Math.toRadians(yaw));
        assertEquals(-4 / Math.hypot(4, 10), nx, 1e-9);
        assertEquals(10 / Math.hypot(4, 10), nz, 1e-9);
    }

    @Test
    void swayStaysAroundTheBaseAngle() {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double t = 0; t < 60; t += 0.05) {
            double a = HomeLayout.swayAngle(35, 6, t, 2);
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertTrue(min >= 29 - 1e-9 && max <= 41 + 1e-9);
        assertTrue(max - min > 11, "the whole amplitude is used");
        // Each frame its own rhythm
        assertNotEquals(HomeLayout.swayAngle(35, 6, 3, 0), HomeLayout.swayAngle(35, 6, 3, 1), 1e-6);
        // Smooth: no jump between two frames of a 60 fps screen
        assertTrue(Math.abs(HomeLayout.swayAngle(35, 6, 10, 1) - HomeLayout.swayAngle(35, 6, 10 + 1 / 60.0, 1)) < 0.1);
    }

    @Test
    void coverFillsTheScreenWithMargins() {
        double[] c = HomeLayout.cover(1920, 1009, 480, 270, 1.12);
        assertTrue(c[0] >= 480 * 1.12 - 1e-9 && c[1] >= 270 * 1.12 - 1e-9);
        assertEquals(1920 / 1009.0, c[0] / c[1], 1e-9, "aspect kept");
        assertEquals((c[0] - 480) / 2, c[2], 1e-9);
        // A tall screen: the height decides
        double[] t = HomeLayout.cover(1920, 1009, 300, 400, 1);
        assertEquals(400, t[1], 1e-9);
        assertTrue(t[0] > 300);
    }

    @Test
    void easingConvergesWithoutOvershoot() {
        double v = 0;
        for (int i = 0; i < 600; i++) {
            double next = HomeLayout.ease(v, 1, 1 / 60.0, 5);
            assertTrue(next >= v && next <= 1);
            v = next;
        }
        assertEquals(1, v, 1e-6);
        // Frame rate independent: two half steps ≈ one step
        double one = HomeLayout.ease(0, 1, 0.1, 5);
        double two = HomeLayout.ease(HomeLayout.ease(0, 1, 0.05, 5), 1, 0.05, 5);
        assertEquals(one, two, 1e-12);
    }

    @Test
    void mouseIsNormalised() {
        assertArrayEquals(new double[]{0, 0}, HomeLayout.mouse(240, 135, W, H), 1e-12);
        assertArrayEquals(new double[]{-1, 1}, HomeLayout.mouse(-50, 999, W, H), 1e-12);
    }

    @Test
    void hazeTintFadesTowardTheColour() {
        assertEquals(0xFFFFFFFF, HomeLayout.hazeTint(0x200808, 0));
        assertEquals(0xFF200808, HomeLayout.hazeTint(0x200808, 1));
        int half = HomeLayout.hazeTint(0x000000, 0.5);
        assertEquals(128, half >> 16 & 255, 1);
    }
}
