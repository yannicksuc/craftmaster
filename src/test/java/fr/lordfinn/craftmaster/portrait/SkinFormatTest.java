package fr.lordfinn.craftmaster.portrait;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkinFormatTest {

    @Test
    void acceptsClassicAndHdSkins() {
        assertEquals(1, SkinFormat.scale(64, 64));
        assertEquals(1, SkinFormat.scale(64, 32));
        assertEquals(2, SkinFormat.scale(128, 128));
        assertEquals(4, SkinFormat.scale(256, 256));
        assertEquals(8, SkinFormat.scale(512, 512));
        assertEquals(16, SkinFormat.scale(1024, 1024));
        assertEquals(3, SkinFormat.scale(192, 192));
        assertEquals(2, SkinFormat.scale(128, 64), "HD legacy");
        assertTrue(SkinFormat.legacy(128, 64));
        assertFalse(SkinFormat.legacy(128, 128));
    }

    @Test
    void rejectsWhatIsNotASkin() {
        assertEquals(0, SkinFormat.scale(32, 32));
        assertEquals(0, SkinFormat.scale(100, 100));
        assertEquals(0, SkinFormat.scale(128, 96));
        assertEquals(0, SkinFormat.scale(64, 128));
        assertEquals(0, SkinFormat.scale(2048, 2048), "too big");
        assertEquals(0, SkinFormat.scale(0, 0));
    }

    @Test
    void slimProbesScaleWithTheSkin() {
        assertArrayEquals(new int[]{54, 20}, SkinFormat.slimProbes(1)[0]);
        assertArrayEquals(new int[]{55, 31}, SkinFormat.slimProbes(1)[1]);
        // At 4×, the middle of the same texels
        assertArrayEquals(new int[]{218, 82}, SkinFormat.slimProbes(4)[0]);
        assertArrayEquals(new int[]{222, 126}, SkinFormat.slimProbes(4)[1]);
        for (int k = 1; k <= SkinFormat.MAX_SCALE; k++)
            for (int[] p : SkinFormat.slimProbes(k)) assertTrue(p[0] < 64 * k && p[1] < 32 * k);
    }

    @Test
    void legacyCopiesStayInsideTheImage() {
        for (int k : new int[]{1, 2, 8}) {
            for (int[] c : SkinFormat.legacyCopies(k)) {
                int size = 64 * k;
                assertTrue(c[0] >= 0 && c[0] + c[4] <= size && c[1] + c[5] <= size / 2, "source in the legacy half");
                int dx = c[0] + c[2], dy = c[1] + c[3];
                assertTrue(dx >= 0 && dx + c[4] <= size && dy >= size / 2 && dy + c[5] <= size, "target in the new half");
            }
        }
        assertArrayEquals(new int[]{8, 32, 32, 64, 8, 8}, SkinFormat.legacyCopies(2)[0]);
    }
}
