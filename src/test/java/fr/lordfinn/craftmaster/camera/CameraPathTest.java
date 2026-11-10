package fr.lordfinn.craftmaster.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recorded travellings: timing, holds, easing, spline smoothness, angles the short way. */
class CameraPathTest {

    private static CameraPath.Step step(double x, double z, float yaw, double travel, double hold, String curve) {
        return new CameraPath.Step(x, 70, z, yaw, 10, 0, travel, hold, curve);
    }

    private static CameraPath path(CameraPath.Step... steps) {
        return new CameraPath("Le Pont !", "pont", "minecraft:overworld", List.of(steps));
    }

    @Test
    void namesAndDefaultsAreNormalized() {
        CameraPath p = path();
        assertEquals("le-pont", p.name());
        assertEquals("pont", p.lieu());
        assertEquals(CameraPath.LINEAR, CameraPath.normalizeCurve("Linéaire"));
        assertEquals(CameraPath.SMOOTH, CameraPath.normalizeCurve(null));
        CameraPath.Step s = new CameraPath.Step(0, 0, 0, 0, 120, 300, -4, Double.NaN, "x");
        assertEquals(90, s.pitch());
        assertEquals(CameraSpot.MAX_FOV, s.fov());
        assertEquals(0, s.travel());
        assertEquals(0, s.hold());
        assertEquals(CameraPath.SMOOTH, s.curve());
    }

    @Test
    void durationAndArrivalsCountTravelsAndHolds() {
        CameraPath p = path(step(0, 0, 0, 99, 1, null), step(10, 0, 0, 3, 2, null), step(20, 0, 0, 4, 0, null));
        // The first step's travel is ignored: 1 + 3 + 2 + 4
        assertEquals(10, p.duration(), 1e-9);
        assertEquals(0, p.arrival(0), 1e-9);
        assertEquals(4, p.arrival(1), 1e-9);
        assertEquals(10, p.arrival(2), 1e-9);
    }

    @Test
    void stepsAreReachedExactlyAndHoldsStayStill() {
        CameraPath p = path(step(0, 0, 0, 0, 1, null), step(10, 0, 0, 3, 2, null), step(20, 5, 90, 4, 0, null));
        assertEquals(0, p.sample(-1).x(), 1e-9);
        assertEquals(0, p.sample(0.5).x(), 1e-9);
        assertEquals(10, p.sample(4).x(), 1e-6);
        assertEquals(10, p.sample(5).x(), 1e-9);
        assertEquals(10, p.sample(6).x(), 1e-6);
        assertEquals(20, p.sample(10).x(), 1e-6);
        assertEquals(5, p.sample(100).z(), 1e-9);
        assertEquals(90, p.sample(100).yaw(), 1e-4);
    }

    @Test
    void smoothCurveStartsAndLandsGently() {
        CameraPath p = path(step(0, 0, 0, 0, 0, null), step(10, 0, 0, 4, 0, null));
        double early = p.sample(0.1).x(), mid = p.sample(2.1).x() - p.sample(2).x();
        // Eased: the first tenth of a second moves far less than a tenth of a second in the middle
        assertTrue(early < mid / 4, early + " vs " + mid);
        double late = p.sample(4).x() - p.sample(3.9).x();
        assertTrue(late < mid / 4);
        CameraPath lin = path(step(0, 0, 0, 0, 0, null), step(10, 0, 0, 4, 0, CameraPath.LINEAR));
        assertEquals(2.5, lin.sample(1).x(), 1e-6);
    }

    @Test
    void movesWithoutJumpsThroughIntermediateSteps() {
        CameraPath p = path(step(0, 0, 0, 0, 0, null), step(6, 4, 30, 2, 0, null), step(9, 12, 80, 3, 0, null),
                step(3, 20, 150, 2.5, 0, null), step(3, 20, 150, 1, 0, null));
        double dt = 1 / 120.0, maxStep = 0;
        CameraPath.Pose prev = p.sample(0);
        for (double t = dt; t <= p.duration() + dt; t += dt) {
            CameraPath.Pose cur = p.sample(t);
            double d = Math.sqrt(Math.pow(cur.x() - prev.x(), 2) + Math.pow(cur.z() - prev.z(), 2));
            maxStep = Math.max(maxStep, d);
            assertTrue(Math.abs(cur.yaw() - prev.yaw()) < 3, "yaw jump at " + t);
            assertTrue(Double.isFinite(cur.x()) && Double.isFinite(cur.z()));
            prev = cur;
        }
        // Never more than ~0.2 block per 1/120 s (about 24 blocks/s), so no teleport-like jump
        assertTrue(maxStep < 0.2, "max step " + maxStep);
    }

    @Test
    void velocityHasNoBigJumpAtIntermediateSteps() {
        // Even spacing and timing: the speed through step 2 is continuous
        CameraPath p = path(step(0, 0, 0, 0, 0, null), step(5, 0, 0, 2, 0, null), step(10, 0, 0, 2, 0, null), step(15, 0, 0, 2, 0, null));
        double h = 1e-3;
        double before = (p.sample(2 - h).x() - p.sample(2 - 2 * h).x()) / h;
        double after = (p.sample(2 + 2 * h).x() - p.sample(2 + h).x()) / h;
        assertEquals(before, after, Math.abs(before) * 0.05 + 0.05);
    }

    @Test
    void yawTurnsTheShortWay() {
        CameraPath p = path(step(0, 0, 350, 0, 0, null), step(1, 0, 10, 2, 0, CameraPath.LINEAR));
        float mid = p.sample(1).yaw();
        float wrapped = ((mid % 360) + 360) % 360;
        // Through 0°, not back through 180°
        assertTrue(wrapped > 355 || wrapped < 5, "mid yaw " + mid);
    }

    @Test
    void zoomIsInterpolatedOnlyWhenSomeStepHasOne() {
        CameraPath none = path(step(0, 0, 0, 0, 0, null), step(1, 0, 0, 2, 0, null));
        assertEquals(0, none.sample(1).fov());
        CameraPath zoom = new CameraPath("z", null, "w", List.of(new CameraPath.Step(0, 0, 0, 0, 0, 0, 0, 0, CameraPath.LINEAR),
                new CameraPath.Step(1, 0, 0, 0, 0, 30, 2, 0, CameraPath.LINEAR)));
        assertEquals(70, zoom.sample(0).fov(), 1e-4);
        assertEquals(30, zoom.sample(2).fov(), 1e-4);
        float f = zoom.sample(1).fov();
        assertTrue(f > 30 && f < 70);
    }

    @Test
    void repeatedPointsDoNotBreakTheSpline() {
        CameraPath p = path(step(1, 1, 0, 0, 0, null), step(1, 1, 0, 2, 0, null), step(1, 1, 45, 2, 0, null));
        for (double t = 0; t <= 4; t += 0.25) {
            CameraPath.Pose s = p.sample(t);
            assertEquals(1, s.x(), 1e-6);
            assertTrue(Float.isFinite(s.yaw()));
        }
    }

    @Test
    void easingEnds() {
        assertEquals(0, CameraPath.ease(0, true, true), 1e-12);
        assertEquals(1, CameraPath.ease(1, true, true), 1e-12);
        assertEquals(0.5, CameraPath.ease(0.5, true, true), 1e-12);
        assertEquals(0.3, CameraPath.ease(0.3, false, false), 1e-12);
        assertTrue(CameraPath.ease(0.1, true, false) < 0.1);
        assertTrue(CameraPath.ease(0.1, false, true) > 0.1);
    }
}
