package fr.lordfinn.craftmaster.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetTrackerTest {

    /** Walks along +x at {@code speed} for {@code seconds}, 60 samples a second. */
    private static double walk(TargetTracker t, double speed, double seconds) {
        double x = 0;
        t.update(0, 64, 0, 0);
        for (int i = 0; i < seconds * 60; i++) {
            x += speed / 60;
            t.update(x, 64, 0, 1 / 60.0);
        }
        return x;
    }

    @Test
    void doesNotLagBehindAWalkingPlayer() {
        TargetTracker lead = new TargetTracker(0.3, 0.3, 0.25, 1.5);
        double x = walk(lead, 4.3, 5);
        // Ahead of the player by about the extra lead (4.3 × 0.25 ≈ 1.1), never behind
        assertTrue(lead.x() > x + 0.8 && lead.x() < x + 1.5, lead.x() + " vs " + x);
        assertEquals(4.3, lead.speed(), 0.05);
        // Plain smoothing (what the cameras did) lags by v × tau
        TargetTracker none = new TargetTracker(0.3, 0.3, -0.3, 0);
        double x2 = walk(none, 4.3, 5);
        assertTrue(none.x() < x2 - 1.0, "smoothing alone lags");
    }

    @Test
    void leadIsCapped() {
        TargetTracker t = new TargetTracker(0.2, 0.2, 2, 1);
        double x = walk(t, 20, 3);
        assertTrue(t.x() - x < 1.3, "capped at about 1 block ahead");
    }

    @Test
    void settlesOnAStoppedPlayerWithoutOvershootLater() {
        TargetTracker t = new TargetTracker(0.3, 0.3, 0.25, 1.5);
        double x = walk(t, 4, 3);
        for (int i = 0; i < 240; i++) t.update(x, 64, 0, 1 / 60.0);
        assertEquals(x, t.x(), 0.02);
        assertEquals(0, t.speed(), 0.01);
    }

    @Test
    void teleportSnaps() {
        TargetTracker t = new TargetTracker(0.3, 0.3, 0.25, 1.5);
        walk(t, 4, 1);
        t.update(500, 70, 500, 1 / 60.0);
        assertEquals(500, t.x(), 1e-9);
        assertEquals(70, t.y(), 1e-9);
        assertEquals(0, t.speed(), 1e-9);
    }

    @Test
    void jumpsAreNotLed() {
        TargetTracker t = new TargetTracker(0.3, 0.3, 0.25, 1.5);
        t.update(0, 64, 0, 0);
        for (int i = 0; i < 60; i++) t.update(0, 64 + (i < 30 ? i / 30.0 : 2 - i / 30.0), 0, 1 / 60.0);
        assertTrue(t.y() <= 65, "no overshoot upwards");
        assertEquals(0, t.speed(), 1e-9);
    }
}
