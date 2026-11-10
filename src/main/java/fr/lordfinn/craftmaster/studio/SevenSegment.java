package fr.lordfinn.craftmaster.studio;

/**
 * Seven-segment digits of the giant timer. Segments are bits: a (top) = 1, b (top right) = 2, c (bottom right) = 4,
 * d (bottom) = 8, e (bottom left) = 16, f (top left) = 32, g (middle) = 64. Pure, unit tested.
 */
public final class SevenSegment {

    private SevenSegment() {}

    public static final int A = 1, B = 2, C = 4, D = 8, E = 16, F = 32, G = 64;
    private static final int[] DIGITS = {
            A | B | C | D | E | F,      // 0
            B | C,                      // 1
            A | B | G | E | D,          // 2
            A | B | G | C | D,          // 3
            F | G | B | C,              // 4
            A | F | G | C | D,          // 5
            A | F | G | E | C | D,      // 6
            A | B | C,                  // 7
            A | B | C | D | E | F | G,  // 8
            A | B | C | D | F | G,      // 9
    };

    /** Segments lit for a character: a digit, '-' (middle bar), anything else blank. */
    public static int segments(char c) {
        if (c >= '0' && c <= '9') return DIGITS[c - '0'];
        if (c == '-') return G;
        return 0;
    }

    /**
     * What the timer shows for a number of seconds: "M:SS" under an hour ("0:07", "12:30"), "H:MM:SS" above,
     * "--:--" for a negative value (no timer).
     */
    public static String clock(int seconds) {
        if (seconds < 0) return "--:--";
        int h = seconds / 3600, m = (seconds / 60) % 60, s = seconds % 60;
        if (h > 0) return h + ":" + two(m) + ":" + two(s);
        return m + ":" + two(s);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
