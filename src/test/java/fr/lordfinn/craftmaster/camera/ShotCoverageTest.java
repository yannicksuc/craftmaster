package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.camera.ShotCoverage.Body;
import fr.lordfinn.craftmaster.camera.ShotCoverage.Lens;
import fr.lordfinn.craftmaster.camera.ShotCoverage.Seen;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ShotCoverageTest {

    /** A lens at eye height looking towards +z (yaw 0), 70°. */
    private static final Lens LENS = new Lens(0, 1.6, 0, 0, 0, 70);
    private static final ShotCoverage.Sight CLEAR = (a, b) -> true;

    private static Body at(String name, double x, double z) {
        return new Body(name, x, 0, z, 0.6, 1.8);
    }

    @Test
    void closerFillsMoreOfThePicture() {
        double far = ShotCoverage.share(LENS, ShotCoverage.ASPECT, at("a", 0, 12));
        double mid = ShotCoverage.share(LENS, ShotCoverage.ASPECT, at("a", 0, 4));
        assertTrue(far > 0 && far < mid, far + " < " + mid);
        // Behind the lens or out of the frame: nothing
        assertEquals(0, ShotCoverage.share(LENS, ShotCoverage.ASPECT, at("a", 0, -4)));
        assertEquals(0, ShotCoverage.share(LENS, ShotCoverage.ASPECT, at("a", 20, 4)));
        // Right on the lens: everything
        assertEquals(1, ShotCoverage.share(LENS, ShotCoverage.ASPECT, at("a", 0, 0.5)));
    }

    @Test
    void weightGrowsThenFallsWhenTooClose() {
        assertEquals(0, ShotCoverage.weight(0.005));
        assertTrue(ShotCoverage.weight(0.05) < ShotCoverage.weight(0.15));
        assertEquals(1, ShotCoverage.weight(ShotCoverage.BEST), 1e-9);
        assertTrue(ShotCoverage.weight(0.3) < ShotCoverage.weight(ShotCoverage.BEST));
        assertEquals(0, ShotCoverage.weight(ShotCoverage.TOO_CLOSE));
        assertEquals(0, ShotCoverage.weight(1));
    }

    @Test
    void hiddenBehindABlockDoesNotCount() {
        Body b = at("a", 0, 5);
        // A wall hides the chest and the feet
        ShotCoverage.Sight wall = (from, to) -> to[1] > 1.5;
        assertFalse(ShotCoverage.visible(LENS, b, wall));
        assertTrue(ShotCoverage.visible(LENS, b, CLEAR));
        CameraSpot cam = new CameraSpot(1, "", "", "w", 0, 1.6, 0, 0f, 0f, 70f, CameraSpot.FIXED);
        assertTrue(ShotCoverage.judge(cam, LENS, ShotCoverage.ASPECT, List.of(b), wall).players().isEmpty());
    }

    @Test
    void picksTheBetterFramedMoreOften() {
        CameraSpot good = new CameraSpot(1, "", "", "w", 0, 0, 0, 0f, 0f, 70f, CameraSpot.FIXED), poor = new CameraSpot(2, "", "", "w", 0, 0, 0, 0f, 0f, 70f, CameraSpot.FIXED);
        List<Seen> seen = List.of(new Seen(good, List.of("Ensuri"), 1), new Seen(poor, List.of("Ensuri"), 0.2),
                new Seen(new CameraSpot(3, "", "", "w", 0, 0, 0, 0f, 0f, 70f, CameraSpot.FIXED), List.of("Cyllu"), 1));
        Map<Integer, Integer> count = new HashMap<>();
        Random r = new Random(3);
        for (int i = 0; i < 600; i++) count.merge(ShotCoverage.pickFor(seen, List.of("ensuri"), r).number(), 1, Integer::sum);
        assertNull(count.get(3));
        assertTrue(count.get(1) > 3 * count.get(2), count.toString());
        assertNull(ShotCoverage.pickFor(seen, List.of("Personne"), r));
    }

    @Test
    void silenceTakesTheCameraSeeingTheMostPlayers() {
        CameraSpot one = new CameraSpot(1, "", "", "w", 0, 0, 0, 0f, 0f, 70f, CameraSpot.FIXED), three = new CameraSpot(2, "", "", "w", 0, 0, 0, 0f, 0f, 70f, CameraSpot.FIXED);
        List<Seen> seen = List.of(new Seen(one, List.of("a"), 1), new Seen(three, List.of("a", "b", "c"), 0.6));
        assertEquals(2, ShotCoverage.pickMost(seen, new Random(1)).number());
        assertNull(ShotCoverage.pickMost(List.of(new Seen(one, List.of(), 0)), new Random(1)));
    }
}
