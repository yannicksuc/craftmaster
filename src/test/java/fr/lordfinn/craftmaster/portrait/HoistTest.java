package fr.lordfinn.craftmaster.portrait;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoistTest {

    @Test
    void theStringGoesFromItsLengthToTheNewOneGently() {
        // 2 blocks to 6 blocks, from tick 100 for 40 ticks
        assertEquals(2, PortraitAnimation.hoisted(2, 6, 100, 40, 90), 1e-9, "before: where it was");
        assertEquals(2, PortraitAnimation.hoisted(2, 6, 100, 40, 100), 1e-9);
        assertEquals(4, PortraitAnimation.hoisted(2, 6, 100, 40, 120), 1e-9, "halfway");
        assertEquals(6, PortraitAnimation.hoisted(2, 6, 100, 40, 140), 1e-9);
        assertEquals(6, PortraitAnimation.hoisted(2, 6, 100, 40, 500), 1e-9, "after: stays there");
        double early = PortraitAnimation.hoisted(2, 6, 100, 40, 104) - 2, late = 6 - PortraitAnimation.hoisted(2, 6, 100, 40, 136);
        assertTrue(early < 0.4 && Math.abs(early - late) < 1e-9, "starts and stops slowly, symmetrically");
        assertEquals(1, PortraitAnimation.hoisted(5, 1, 0, 20, 20), 1e-9, "raising works the same way");
        assertEquals(3, PortraitAnimation.hoisted(0, 3, 0, 0, 0), 1e-9, "no duration: there at once");
    }
}
