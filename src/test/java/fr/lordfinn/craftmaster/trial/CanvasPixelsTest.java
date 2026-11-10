package fr.lordfinn.craftmaster.trial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Drawing boards: strokes, brush, bucket, compact encoding. */
class CanvasPixelsTest {

    @Test
    void blankBoardEncodesInAFewBytes() {
        CanvasPixels p = new CanvasPixels(64, 32);
        assertEquals("2048a", p.encode());
        assertTrue(p.isBlank());
    }

    @Test
    void roundTrip() {
        CanvasPixels p = new CanvasPixels(16, 16);
        p.set(0, 0, 3);
        p.set(1, 0, 3);
        p.set(15, 15, 16);
        String text = p.encode();
        assertEquals("2d253aq", text);
        CanvasPixels back = CanvasPixels.decode(16, 16, text);
        assertEquals(3, back.get(1, 0));
        assertEquals(16, back.get(15, 15));
        assertEquals(0, back.get(2, 0));
        assertEquals(text, back.encode());
    }

    @Test
    void lineHasNoGapsAndKeepsItsEnds() {
        CanvasPixels p = new CanvasPixels(32, 32);
        p.line(2, 3, 20, 9, 0, 5);
        assertEquals(5, p.get(2, 3));
        assertEquals(5, p.get(20, 9));
        // One pixel per column along a mostly horizontal line
        for (int x = 2; x <= 20; x++) {
            boolean found = false;
            for (int y = 0; y < 32; y++) found |= p.get(x, y) == 5;
            assertTrue(found, "column " + x);
        }
    }

    @Test
    void brushAndBounds() {
        CanvasPixels p = new CanvasPixels(8, 8);
        assertTrue(p.dot(0, 0, 1, 2));
        assertEquals(2, p.get(1, 0));
        assertEquals(2, p.get(1, 1));
        assertEquals(0, p.get(2, 2));
        assertFalse(p.set(8, 0, 1));
        assertFalse(p.set(0, 0, 17));
        assertFalse(p.dot(0, 0, 0, 2), "same colour: nothing changes");
    }

    @Test
    void bucketFillsOnlyTheEnclosedArea() {
        CanvasPixels p = new CanvasPixels(10, 10);
        // A closed square from (2,2) to (6,6)
        p.line(2, 2, 6, 2, 0, 1);
        p.line(6, 2, 6, 6, 0, 1);
        p.line(6, 6, 2, 6, 0, 1);
        p.line(2, 6, 2, 2, 0, 1);
        assertTrue(p.fill(4, 4, 9));
        assertEquals(9, p.get(3, 3));
        assertEquals(9, p.get(5, 5));
        assertEquals(1, p.get(2, 4));
        assertEquals(0, p.get(0, 0));
        assertEquals(0, p.get(8, 8));
        assertFalse(p.fill(4, 4, 9));
    }

    @Test
    void garbageAndResize() {
        CanvasPixels p = CanvasPixels.decode(4, 4, "99999zzz");
        assertEquals("16a", p.encode());
        CanvasPixels q = new CanvasPixels(4, 4);
        q.set(3, 3, 4);
        q.set(0, 0, 2);
        CanvasPixels r = q.resized(2, 2);
        assertEquals(2, r.get(0, 0));
        assertEquals(2, r.width());
    }
}
