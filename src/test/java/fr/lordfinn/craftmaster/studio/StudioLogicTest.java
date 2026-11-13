package fr.lordfinn.craftmaster.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure logic of the studio set: spotlight aiming, seven-segment digits, /image arguments, gong swing. */
class StudioLogicTest {

    // ---------------------------------------------------------------- spotlight aim

    @Test
    void anglesFollowMinecraftConventions() {
        float[] south = SpotlightAim.angles(0, 0, 1);
        assertEquals(0, south[0], 1e-4);
        assertEquals(0, south[1], 1e-4);
        assertEquals(90, SpotlightAim.angles(-1, 0, 0)[0], 1e-4);   // west
        assertEquals(-90, SpotlightAim.angles(1, 0, 0)[0], 1e-4);   // east
        assertEquals(180, Math.abs(SpotlightAim.angles(0, 0, -1)[0]), 1e-4);
        assertEquals(45, SpotlightAim.angles(0, -1, 1)[1], 1e-4);   // down is positive
        assertEquals(SpotlightAim.MAX_PITCH, SpotlightAim.angles(0, -1, 0)[1], 1e-4);
    }

    @Test
    void directionAndAnglesRoundTrip() {
        for (float yaw = -170; yaw < 180; yaw += 37) {
            for (float pitch = -80; pitch <= 80; pitch += 20) {
                double[] d = SpotlightAim.direction(yaw, pitch);
                assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
                float[] a = SpotlightAim.angles(d[0], d[1], d[2]);
                assertEquals(0, SpotlightAim.wrap(a[0] - yaw), 1e-3);
                assertEquals(pitch, a[1], 1e-3);
            }
        }
    }

    @Test
    void approachTakesTheShortWayRound() {
        // 170 -> -170 goes through 180 (20 degrees), not through 0 (340 degrees)
        assertEquals(-180, SpotlightAim.approach(170, -170, 10), 1e-4);
        assertEquals(-170, SpotlightAim.approach(170, -170, 30), 1e-4);
        assertEquals(15, SpotlightAim.approach(10, 40, 5), 1e-4);
    }

    @Test
    void easeConvergesWithoutOvershoot() {
        float a = 0;
        for (int i = 0; i < 200; i++) {
            float next = SpotlightAim.ease(a, 90, 1 / 60.0, 4, 25);
            assertTrue(next >= a && next <= 90);
            a = next;
        }
        assertEquals(90, a, 1e-4);
    }

    // ---------------------------------------------------------------- giant timer

    @Test
    void sevenSegmentDigits() {
        assertEquals(Integer.bitCount(SevenSegment.segments('8')), 7);
        assertEquals(SevenSegment.B | SevenSegment.C, SevenSegment.segments('1'));
        assertEquals(SevenSegment.G, SevenSegment.segments('-'));
        assertEquals(0, SevenSegment.segments(':'));
        for (char c = '0'; c <= '9'; c++) assertNotEquals(0, SevenSegment.segments(c));
    }

    @Test
    void clockFormat() {
        assertEquals("0:07", SevenSegment.clock(7));
        assertEquals("3:00", SevenSegment.clock(180));
        assertEquals("12:30", SevenSegment.clock(750));
        assertEquals("1:02:03", SevenSegment.clock(3723));
        assertEquals("--:--", SevenSegment.clock(-1));
    }

    // ---------------------------------------------------------------- /image

    @Test
    void imageDefaultsLikeThePlugin() throws Exception {
        ImageRequest r = ImageRequest.parse("https://example.org/a.png");
        assertEquals(5, r.width());
        assertEquals(3, r.height());
        assertEquals("https://example.org/a.png", r.url());
    }

    @Test
    void imageSizes() throws Exception {
        assertEquals(new ImageRequest("http://x.fr/i.jpg", 4, 2), ImageRequest.parse("http://x.fr/i.jpg 4 2"));
        // Width only: keeps the 5:3 look
        assertEquals(6, ImageRequest.parse("http://x.fr/i.jpg 10").height());
        // Clamped to 64 x 36
        ImageRequest big = ImageRequest.parse("http://x.fr/i.jpg 100 100");
        assertEquals(ImageRequest.MAX_WIDTH, big.width());
        assertEquals(ImageRequest.MAX_HEIGHT, big.height());
        assertEquals(1, ImageRequest.parse("http://x.fr/i.jpg 0 0").width());
    }

    @Test
    void imageRefusals() {
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse(""));
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse("ftp://x.fr/a.png"));
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse("file:///C:/secret.png"));
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse("https://x.fr/a.png large 2"));
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse("https://x.fr/a b.png 2 2"));
        assertThrows(ImageRequest.Invalid.class, () -> ImageRequest.parse("https:///nohost.png"));
        assertNotNull(ImageRequest.urlError("https://" + "a".repeat(ImageRequest.MAX_URL_LENGTH)));
        assertNull(ImageRequest.urlError("HTTPS://Example.org/Image.PNG?x=1&y=2"));
    }

    // ---------------------------------------------------------------- gong

    @Test
    void gongSwingDampsOut() {
        assertEquals(0, GongSwing.angle(-1));
        assertEquals(0, GongSwing.angle(0), 1e-6);
        assertEquals(0, GongSwing.angle(GongSwing.SWING_TICKS + 1));
        float first = 0;
        for (int t = 0; t < 10; t++) first = Math.max(first, Math.abs(GongSwing.angle(t)));
        float late = 0;
        for (int t = 40; t < 60; t++) late = Math.max(late, Math.abs(GongSwing.angle(t)));
        assertTrue(first > 5 && late < first / 3, first + " / " + late);
    }
}
