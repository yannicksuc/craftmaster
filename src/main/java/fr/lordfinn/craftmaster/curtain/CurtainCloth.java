package fr.lordfinn.craftmaster.curtain;

/**
 * Shape of a curtain's fabric while it slides (pure math, shared by the client renderer and the tests).
 * <p>
 * A curtain is a row of pleats, 4 per block column ({@link #PLEATS_PER_BLOCK}), 4 px wide, alternating between a
 * front and a back position: exactly the look of the closed block model. It opens in two halves that slide to the
 * sides: each half gathers toward its outer edge, its pleats getting narrower and deeper, until the whole half is
 * a bundle of deep folds against the side ({@link #bundleWidth}). Closing does the opposite.
 * <p>
 * Coordinates: pixels (1/16 block). {@code x} runs along the curtain from its first column's outer edge (lowest
 * world x or z), {@code z} across it from the block's back face; the cloth hangs around z = 8.
 */
public final class CurtainCloth {

    private CurtainCloth() {}

    public static final int PLEATS_PER_BLOCK = 4;
    public static final double PLEAT = 16.0 / PLEATS_PER_BLOCK;
    /** An open half keeps this share of its width as a bundle of folds against the side... */
    public static final double BUNDLE_RATIO = 0.16;
    /** ...but never less than this (px), nor more than the half itself. */
    public static final double MIN_BUNDLE = 6;

    /** One pleat: along the curtain [x0, x1], across it [z0, z1] (px). */
    public record Pleat(double x0, double x1, double z0, double z1) {
        public double width() {
            return x1 - x0;
        }
    }

    /** Width (px) of the bundle an open half gathers into, for a half {@code half} px wide. */
    public static double bundleWidth(double half) {
        return Math.min(half, Math.max(MIN_BUNDLE, half * BUNDLE_RATIO));
    }

    /**
     * Pleat {@code j} (0 = first, along x) of a curtain of {@code columns} block columns, {@code open} of the way
     * open (0 closed, 1 open; slightly outside [0, 1] for the sway).
     */
    public static Pleat pleat(int j, int columns, double open) {
        int total = columns * PLEATS_PER_BLOCK, perHalf = total / 2;
        double half = columns * 16 / 2.0;
        double f = Math.max(-0.1, Math.min(1.05, open));
        double span = half - f * (half - bundleWidth(half));
        double w = span / perHalf;
        boolean left = j < perHalf;
        int i = left ? j : total - 1 - j;            // index counted from the outer edge of its half
        double a = i * w, b = (i + 1) * w;
        double x0 = left ? a : 2 * half - b, x1 = left ? b : 2 * half - a;
        // Deeper folds as the fabric gathers: closed = the block model (z 6..8 / 8..10), open = 4..8 / 8..12
        double g = Math.max(0, Math.min(1, f));
        double offset = (j % 2 == 0 ? -1 : 1) * (1 + g);
        double depth = 2 + 2 * g;
        return new Pleat(x0, x1, 8 + offset - depth / 2, 8 + offset + depth / 2);
    }

    /** Ease in and out (smoothstep): the fabric starts and stops softly, without a long dead start. */
    public static double ease(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t * t * (3 - 2 * t);
    }

    /**
     * Ticks the slide takes for a curtain of {@code columns} columns: like the server's column-by-column switch
     * ({@link Curtains}), one step per column from the middle, plus one so the last column has flipped.
     */
    public static int durationTicks(int columns, int stepTicks) {
        long steps = Math.round((columns - 1) / 2.0);
        return (int) ((steps + 1) * stepTicks);
    }

    /**
     * How open the fabric is during a slide from {@code from} to {@code to} started {@code elapsed} ticks ago and
     * lasting {@code duration} ticks.
     */
    public static double progress(double from, double to, double elapsed, double duration) {
        if (duration <= 0) return to;
        return from + (to - from) * ease(elapsed / duration);
    }
}
