package fr.lordfinn.craftmaster.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LetterMotionTest {

    @Test
    void panelsGrowWithTheText() {
        assertEquals(3, LetterMotion.panels(10));
        assertEquals(3, LetterMotion.panels(LetterMotion.height(3)));
        assertEquals(4, LetterMotion.panels(LetterMotion.height(3) + 1));
        assertEquals(LetterMotion.TOP + 2 * LetterMotion.PANEL, LetterMotion.height(3));
    }

    @Test
    void topFlipsUpThenBottomDown() {
        // Closed: the top panel lies over the middle (its outside), the bottom one folded up over it
        LetterMotion.Projection top = LetterMotion.project(0, 0);
        assertFalse(top.front());
        assertEquals(LetterMotion.TOP, top.y0(), 1e-4);
        assertEquals(2 * LetterMotion.TOP, top.y1(), 1e-4, "its edge (the seal) across the middle panel");
        LetterMotion.Projection bottom = LetterMotion.project(2, 0);
        assertFalse(bottom.front());
        assertEquals(LetterMotion.TOP, bottom.y0(), 1e-4);
        // Open: flat, in place, front
        assertEquals(new LetterMotion.Projection(0, LetterMotion.TOP, true, 1), LetterMotion.project(0, 1));
        LetterMotion.Projection b = LetterMotion.project(2, 1);
        assertTrue(b.front());
        assertEquals(LetterMotion.panelTop(2) + LetterMotion.PANEL, b.y1(), 1e-3);
        // The top one opens first, the bottom one follows, the extra ones after
        assertTrue(LetterMotion.open(0, 0.4f) > LetterMotion.open(2, 0.4f));
        assertEquals(0, LetterMotion.open(3, LetterMotion.BOTTOM_START));
        assertEquals(1, LetterMotion.open(4, LetterMotion.end(5)), 1e-6);
        assertEquals(1, LetterMotion.open(2, LetterMotion.end(3)), 1e-6);
        assertEquals(1, LetterMotion.zoom(LetterMotion.end(3) + 0.2f), 1e-6);
    }

    @Test
    void edgeOnHalfway() {
        LetterMotion.Projection p = LetterMotion.project(0, 0.5f);
        assertEquals(0, p.y1() - p.y0(), 1e-3);
        assertEquals(0, p.light(), 1e-3);
    }

    @Test
    void wholePixels() {
        // 1080p: 3 screen px per letter px for the open letter, twice that closed
        assertEquals(3, LetterMotion.openScale(1920, 1080, 3));
        assertTrue(LetterMotion.closedScale(1920, 1080) >= 5);
        // Always at least 1, even in a tiny window
        assertEquals(1, LetterMotion.openScale(320, 240, 3));
        // A long letter: same scale as a 3-panel one (it scrolls instead)
        assertEquals(LetterMotion.openScale(1920, 1080, 3), LetterMotion.openScale(1920, 1080, 7));
        assertEquals(0, LetterMotion.maxScroll(200, 400));
        assertTrue(LetterMotion.maxScroll(LetterMotion.height(6), 360) > 0);
    }

    @Test
    void sealResistsThenPops() {
        LetterMotion.Seal s = new LetterMotion.Seal();
        assertTrue(LetterMotion.Seal.hit(5, 5));
        assertFalse(LetterMotion.Seal.hit(40, 0));
        s.grab(2, 3);
        assertFalse(s.drag(2, 3 - 10));
        assertTrue(s.offsetY() < 0 && s.offsetY() > -10, "follows, but less than the mouse");
        float t1 = s.tension();
        assertFalse(s.drag(2, 3 - 20));
        assertTrue(s.tension() > t1);
        assertTrue(s.drag(2, 3 - 30), "pops past the limit");
        assertTrue(s.popped());
        assertTrue(s.flight(0.8f)[1] > s.flight(0.1f)[1], "then falls");
        assertEquals(0, s.flight(1f)[3], 1e-6, "and fades");
    }

    @Test
    void releasedTooEarlySpringsBack() {
        LetterMotion.Seal s = new LetterMotion.Seal();
        s.grab(0, 0);
        s.drag(0, -15);
        s.release();
        for (int i = 0; i < 60; i++) s.update(1 / 60f, false);
        assertFalse(s.popped());
        assertEquals(0, s.offsetY(), 1e-3);
        assertEquals(0, s.tension(), 1e-3);
    }

    @Test
    void clickOrHeldKeyPullsItOff() {
        LetterMotion.Seal click = new LetterMotion.Seal();
        click.grab(1, 1);
        click.release();
        boolean popped = false;
        for (int i = 0; i < 60 && !popped; i++) popped = click.update(1 / 60f, false);
        assertTrue(popped, "a click pulls it off by itself");
        LetterMotion.Seal key = new LetterMotion.Seal();
        for (int i = 0; i < 20; i++) key.update(1 / 60f, true);
        assertFalse(key.popped());
        for (int i = 0; i < 40; i++) key.update(1 / 60f, true);
        assertTrue(key.popped(), "held long enough");
    }

    @Test
    void lockedSealNeverPops() {
        LetterMotion.Seal s = new LetterMotion.Seal();
        s.lock(true);
        s.grab(0, 0);
        assertFalse(s.drag(0, -200));
        for (int i = 0; i < 120; i++) s.update(1 / 60f, true);
        assertFalse(s.popped());
    }
}
