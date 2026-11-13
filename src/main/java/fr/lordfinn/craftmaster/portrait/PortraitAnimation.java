package fr.lordfinn.craftmaster.portrait;

/**
 * Timeline of a portrait changing column, shared by the server (which plans it) and the clients (which draw it
 * from the synced start tick, without any further packet). Pure logic, unit-tested.
 * <ol>
 *   <li>{@link #STEP_TICKS}: the portrait leaves the row, forward or backward depending on its plane;</li>
 *   <li>{@code slideTicks}: it slides to its new column, every moving portrait with the same duration and easing;</li>
 *   <li>{@link #STEP_TICKS}: it comes back into the row.</li>
 * </ol>
 */
public final class PortraitAnimation {

    private PortraitAnimation() {}

    /** Duration of stepping out of the row and back in. */
    public static final int STEP_TICKS = 16;
    /** Distance between two planes, in blocks (a frame plus room for a figure leaning out). */
    public static final double PLANE_GAP = 0.7;
    /** The start is set a little in the future so every client gets the data before it begins. */
    public static final int LEAD_TICKS = 6;

    /** Slide duration for the longest move of a rearrangement, in ticks (about 1.5 block per second, 1.5 to 6 s). */
    public static int slideTicks(double longestMoveBlocks) {
        return (int) Math.max(30, Math.min(120, Math.round(longestMoveBlocks * 13)));
    }

    public static int totalTicks(int slideTicks) {
        return 2 * STEP_TICKS + slideTicks;
    }

    /** Smooth start and stop. */
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * Where the portrait is at {@code t} ticks after the start.
     *
     * @return {lateral progress from the old column to the new one (0..1), how far out of the row it is (0..1)}
     */
    public static double[] progress(double t, int slideTicks) {
        if (t <= 0) return new double[]{0, 0};
        if (t < STEP_TICKS) return new double[]{0, ease(t / STEP_TICKS)};
        t -= STEP_TICKS;
        if (t < slideTicks) return new double[]{ease(t / slideTicks), 1};
        t -= slideTicks;
        if (t < STEP_TICKS) return new double[]{1, 1 - ease(t / STEP_TICKS)};
        return new double[]{1, 0};
    }

    /** String length (blocks) from {@code from} to {@code to}, started at {@code start} for {@code length} ticks, at {@code ticks}. */
    public static double hoisted(double from, double to, long start, int length, double ticks) {
        double t = length <= 0 ? 1 : (ticks - start) / length;
        if (t >= 1) return to;
        if (t <= 0) return from;
        return from + (to - from) * t * t * (3 - 2 * t); // starts and stops gently, like a winch
    }
}
