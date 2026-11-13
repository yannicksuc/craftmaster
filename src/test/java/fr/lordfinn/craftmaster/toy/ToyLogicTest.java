package fr.lordfinn.craftmaster.toy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Task toys: physics, fragile egg, golf cup, colours, tallies, balloons, graduated glass. */
class ToyLogicTest {

    // ---------------------------------------------------------------- physics

    @Test
    void bounceReversesDampsAndDiesOut() {
        assertEquals(0.36, ToyPhysics.bounce(-0.5, 0.72), 1e-9);
        assertEquals(-0.36, ToyPhysics.bounce(0.5, 0.72), 1e-9);
        // A slow bounce stops (no jitter on the ground)
        assertEquals(0, ToyPhysics.bounce(-0.1, 0.5), 1e-9);
        assertEquals(0, ToyPhysics.bounce(-1, 0), 1e-9);
    }

    @Test
    void impactSpeedStandsForAFallHeight() {
        // v² = 2 g h: a fall of 2 blocks ends at sqrt(0.16) = 0.4 b/t
        assertEquals(2, ToyPhysics.fallHeightFor(0.4), 1e-9);
        assertEquals(0, ToyPhysics.fallHeightFor(0), 1e-9);
    }

    @Test
    void eggBreaksPastItsFragility() {
        double twoBlocks = Math.sqrt(2 * ToyPhysics.GRAVITY * 2);
        double threeBlocks = Math.sqrt(2 * ToyPhysics.GRAVITY * 3);
        assertFalse(ToyPhysics.eggBreaks(twoBlocks, 2));
        assertTrue(ToyPhysics.eggBreaks(threeBlocks, 2));
        assertFalse(ToyPhysics.eggBreaks(threeBlocks, 4));
        // Thin shell (0): any real knock, but not being set down gently
        assertTrue(ToyPhysics.eggBreaks(0.2, 0));
        assertFalse(ToyPhysics.eggBreaks(0.05, 0));
    }

    @Test
    void throwChargeIsBowLike() {
        assertEquals(0, ToyPhysics.charge(0), 1e-9);
        assertEquals(1, ToyPhysics.charge(20), 1e-9);
        assertEquals(1, ToyPhysics.charge(200), 1e-9);
        assertTrue(ToyPhysics.charge(5) > 0.15 && ToyPhysics.charge(5) < 0.25);
        assertEquals(0.25, ToyPhysics.throwSpeed(ToyKind.DUCK, 0), 1e-9);
        assertEquals(ToyKind.BALL.maxThrowSpeed, ToyPhysics.throwSpeed(ToyKind.BALL, 40), 1e-9);
    }

    @Test
    void golfCupCatchesSlowBallsPassingOverIt() {
        double r = 3.5 / 16;
        // Rolling straight across the centre, slowly: in
        assertTrue(ToyPhysics.dropsInCup(0, 0.5, 1, 0.5, 0.5, 0.5, r, 0.2));
        // Its path passes over the cup even though it stops on neither side of it
        assertTrue(ToyPhysics.dropsInCup(0.1, 0.5, 0.9, 0.55, 0.5, 0.5, r, 0.3));
        // Too fast: skims over
        assertFalse(ToyPhysics.dropsInCup(0, 0.5, 1, 0.5, 0.5, 0.5, r, 0.9));
        // Off to the side
        assertFalse(ToyPhysics.dropsInCup(0, 0.9, 1, 0.9, 0.5, 0.5, r, 0.1));
        // The faster, the more central it must be
        assertTrue(ToyPhysics.dropsInCup(0, 0.65, 1, 0.65, 0.5, 0.5, r, 0.05));
        assertFalse(ToyPhysics.dropsInCup(0, 0.65, 1, 0.65, 0.5, 0.5, r, 0.4));
    }

    @Test
    void distancesAndFrenchDecimals() {
        assertEquals(5, ToyPhysics.flatDistance(0, 0, 3, 4), 1e-9);
        assertEquals("7,4", ToyPhysics.blocks(7.38));
    }

    @Test
    void dyeColoursAndFrenchAgreement() {
        assertEquals("red", ToyPhysics.DYES[ToyPhysics.nearestDye(0xB02E26)]);
        assertEquals("yellow", ToyPhysics.DYES[ToyPhysics.nearestDye(0xF6C632)]);
        // A colour between two dyes goes to the nearest
        assertEquals("blue", ToyPhysics.DYES[ToyPhysics.nearestDye(0x3A46B0)]);
        int blue = 11, white = 0;
        assertEquals("bleu", ToyPhysics.colourFr(blue, false));
        assertEquals("bleue", ToyPhysics.colourFr(blue, true));
        assertEquals("blanche", ToyPhysics.colourFr(white, true));
        assertEquals("une balle de golf", ToyKind.GOLF_BALL.withArticle());
        assertEquals("un canard en plastique", ToyKind.DUCK.withArticle());
        assertEquals(ToyKind.EGG, ToyKind.byId("fragile_egg"));
        assertNull(ToyKind.byId("nope"));
    }

    // ---------------------------------------------------------------- tallies

    @Test
    void tallyCountsPerPlayerIgnoringCase() {
        ToyTally t = new ToyTally();
        assertTrue(t.isEmpty());
        assertEquals(1, t.found("Ensuri", "canard en plastique jaune"));
        assertEquals(2, t.found("ensuri", "canard en plastique rouge"));
        assertEquals(3, t.found("ENSURI", "canard en plastique jaune"));
        assertEquals(1, t.found("Cyllu", "canard en plastique jaune"));
        assertEquals(List.of("Ensuri", "Cyllu"), t.players());
        assertEquals("trouvé(s) 3 : 2 canard en plastique jaune, 1 canard en plastique rouge", t.describe("Ensuri").get(0));
    }

    @Test
    void tallyKeepsBestThrowAndCatches() {
        ToyTally t = new ToyTally();
        assertTrue(t.thrown("Juste", 7.5));
        assertFalse(t.thrown("Juste", 6));
        assertTrue(t.thrown("Juste", 9.25));
        assertEquals(9.25, t.bestThrow("juste"), 1e-9);
        assertEquals(1, t.caught("Juste", "tasse", 4));
        assertEquals(2, t.caught("Juste", "tasse", 6.5));
        assertEquals(1, t.caught("Juste", "seau", 2));
        assertEquals(3, t.caughtTotal("Juste"));
        assertEquals(6.5, t.bestCatch("Juste"), 1e-9);
        assertTrue(t.describe("Juste").contains("plus long lancer : 9,3 blocs"));
    }

    @Test
    void tallyGolfPopsEggsAndReset() {
        ToyTally t = new ToyTally();
        assertEquals(3, t.holed("Arreaubaz", 3));
        assertEquals(7, t.holed("Arreaubaz", 4));
        assertEquals(List.of(3, 4), t.holes("arreaubaz"));
        assertEquals(1, t.popped("Arreaubaz"));
        assertEquals(2, t.popped("Arreaubaz"));
        assertEquals(1, t.brokenEgg("Arreaubaz"));
        List<String> d = t.describe("Arreaubaz");
        assertTrue(d.contains("golf : 3 + 4 = 7 coup(s)"));
        assertTrue(d.contains("ballons crevés : 2"));
        assertTrue(d.contains("œufs cassés : 1"));
        t.reset();
        assertTrue(t.isEmpty());
        assertEquals(0, t.foundTotal("Arreaubaz"));
    }

    // ---------------------------------------------------------------- balloons

    @Test
    void balloonGrowsSlowerAsItFillsAndBurstsAtItsLimit() {
        float s = Balloons.MIN;
        float first = Balloons.puff(s) - s;
        int puffs = 0;
        float burstAt = Balloons.burstAt(0.5);
        while (!Balloons.bursts(s, burstAt) && puffs < 200) {
            s = Balloons.puff(s);
            puffs++;
        }
        assertTrue(puffs > 10 && puffs < 60, "puffs: " + puffs);
        float last = Balloons.puff(Balloons.MAX * 0.9f) - Balloons.MAX * 0.9f;
        assertTrue(last < first);
        assertEquals(Balloons.MAX * 0.75f, Balloons.burstAt(0), 1e-6);
        assertTrue(Balloons.burstAt(0.999) <= Balloons.MAX);
        assertEquals(0, Balloons.fullness(Balloons.MIN), 1e-6);
        assertEquals(1, Balloons.fullness(Balloons.MAX), 1e-6);
        assertTrue(Balloons.puffPitch(Balloons.MIN) > Balloons.puffPitch(Balloons.MAX));
    }

    // ---------------------------------------------------------------- graduated glass

    @Test
    void glassPoursTakesAndOverflows() {
        GlassLevel g = new GlassLevel(1000);
        assertEquals(0, g.stage());
        assertEquals(0, g.signal());
        assertEquals(250, g.pour(GlassLevel.BOTTLE));
        assertEquals(2, g.stage());
        assertEquals(3, g.signal());
        assertEquals(750, g.pour(GlassLevel.BUCKET));
        assertTrue(g.full());
        assertEquals(0, g.pour(50));
        assertEquals(15, g.signal());
        assertEquals(8, g.stage());
        assertTrue(g.take(GlassLevel.BUCKET));
        assertFalse(g.take(GlassLevel.BOTTLE));
        g.pour(GlassLevel.TRICKLE);
        assertEquals(1, g.stage());
        assertEquals(1, g.signal());
    }

    @Test
    void glassReadingWithTheLine() {
        GlassLevel g = new GlassLevel(1000);
        g.pour(650);
        assertEquals("650 mL / 1 000 mL", g.reading());
        g.setLine(500);
        assertEquals("650 mL / 1 000 mL · trait 500 mL (+150 mL)", g.reading());
        g.set(500);
        assertTrue(g.reading().endsWith("(pile !)"));
        g.set(420);
        assertTrue(g.reading().endsWith("(−80 mL)"));
        g.setLine(-5);
        assertEquals(-1, g.line());
        assertEquals("12,5 L", GlassLevel.format(12_500));
    }
}
