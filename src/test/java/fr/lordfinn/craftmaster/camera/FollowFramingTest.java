package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.camera.FollowFraming.Params;
import fr.lordfinn.craftmaster.camera.FollowFraming.Plan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FollowFramingTest {

    @Test
    void azimuthZeroIsInFrontOfThePlayer() {
        // Facing +z (yaw 0), no elevation: the camera is on +z
        double[] d = FollowFraming.direction(new Params(0, 0, 3, 0, 1), 0);
        assertEquals(0, d[0], 1e-9);
        assertEquals(0, d[1], 1e-9);
        assertEquals(1, d[2], 1e-9);
        // From above: up, still in front
        double[] high = FollowFraming.direction(new Params(0, 45, 3, 0, 1), 0);
        assertTrue(high[1] > 0.7 && high[2] > 0.7);
        // 180: behind
        assertEquals(-1, FollowFraming.direction(new Params(180, 0, 3, 0, 1), 0)[2], 1e-9);
    }

    @Test
    void glideTurnsTheShortWayRound() {
        Params a = new Params(170, 0, 3, 0, 1), b = new Params(-170, 20, 5, 0, 1);
        Params mid = a.lerp(b, 0.5);
        assertEquals(180, ((mid.azimuth() % 360) + 360) % 360, 1e-9);
        assertEquals(10, mid.elevation(), 1e-9);
        assertEquals(4, mid.distance(), 1e-9);
    }

    @Test
    void easeStartsAndStopsGently() {
        assertEquals(0, FollowFraming.ease(0), 1e-12);
        assertEquals(1, FollowFraming.ease(1), 1e-12);
        assertEquals(0.5, FollowFraming.ease(0.5), 1e-12);
        assertTrue(FollowFraming.ease(0.05) < 0.002);
        assertEquals(1, FollowFraming.ease(3), 1e-12);
    }

    @Test
    void heldFramingMovesSlowlyAndStaysBounded() {
        Plan wide = FollowFraming.PLANS.stream().filter(p -> p.dolly() != 0).findFirst().orElseThrow();
        Params later = FollowFraming.params(wide, 1000);
        assertEquals(wide.distance() + Math.signum(wide.dolly()) * FollowFraming.DOLLY_MAX, later.distance(), 1e-9);
        for (Plan p : FollowFraming.PLANS) {
            assertTrue(Math.abs(p.orbit()) <= 3, p.name() + " orbits too fast");
            assertTrue(p.elevation() >= FollowFraming.MIN_ELEVATION && p.elevation() <= FollowFraming.MAX_ELEVATION);
            assertTrue(FollowFraming.params(p, 12).distance() >= FollowFraming.MIN_DISTANCE);
        }
    }

    @Test
    void neverPicksTheSameFramingTwiceInARow() {
        Random r = new Random(4);
        Plan current = FollowFraming.PLANS.getFirst(), previous = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            Plan next = FollowFraming.choose(current, previous, p -> 1, p -> 1, r);
            assertNotSame(current, next);
            assertNotSame(previous, next);
            seen.add(next.name());
            previous = current;
            current = next;
        }
        assertTrue(seen.size() >= FollowFraming.PLANS.size() - 1, "framings vary: " + seen);
    }

    @Test
    void picksOnlyFramingsWithRoomElseTheRoomiest() {
        Random r = new Random(1);
        Plan current = FollowFraming.PLANS.getFirst();
        // A corridor: only the profile has room
        for (int i = 0; i < 20; i++)
            assertEquals("profil", FollowFraming.choose(current, null, p -> p.name().equals("profil") ? 1 : 0.3, p -> 1, r).name());
        // Nothing has room: the most free blocks (0.5 × 7 for the wide shot beats 0.5 × the others)
        assertEquals("plan large", FollowFraming.choose(current, null, p -> 0.5, p -> 1, r).name());
    }

    @Test
    void weightSteersTheChoice() {
        Random r = new Random(7);
        // Walking: nothing from behind when its weight is zero
        for (int i = 0; i < 50; i++) {
            Plan p = FollowFraming.choose(null, null, q -> 1, q -> Math.abs(q.azimuth()) >= 120 ? 0 : 1, r);
            assertTrue(Math.abs(p.azimuth()) < 120, p.name());
        }
    }
}
