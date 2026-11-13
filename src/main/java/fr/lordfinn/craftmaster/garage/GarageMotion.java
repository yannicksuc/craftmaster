package fr.lordfinn.craftmaster.garage;

/**
 * The garage door's movement, the same on both sides: the server frees and blocks the doorway with it, the clients
 * draw the panel with it every frame.
 * <p>
 * The door's opening {@code s} is how far it has risen, in blocks (0 = closed, {@code height} = open). Each block of
 * the panel rises with it; once it reaches the top of the doorway (the line under the motor) it goes in there, as if
 * the door were rolling up into the lintel: only the part still under the line is drawn, squeezed into it.
 */
public final class GarageMotion {

    private GarageMotion() {}

    /** Default speed: ticks per block (a 3-block-high door opens in 1.8 s). */
    public static final int DEFAULT_TICKS_PER_BLOCK = 12;
    public static final int MIN_TICKS_PER_BLOCK = 4;
    public static final int MAX_TICKS_PER_BLOCK = 60;
    /**
     * A cell of the doorway stops blocking when the panel's bottom is this close to its top (blocks), and blocks
     * again as soon as the panel comes down that far into it.
     */
    public static final double CELL_MARGIN = 0.1;

    /** Soft start and stop (sine ease in-out), {@code u} from 0 to 1. */
    public static double ease(double u) {
        if (u <= 0) return 0;
        if (u >= 1) return 1;
        return 0.5 - 0.5 * Math.cos(Math.PI * u);
    }

    /** Ticks to move from {@code from} to {@code to} (blocks of opening), at least 4. */
    public static int duration(double from, double to, int ticksPerBlock) {
        return Math.max(4, (int) Math.round(Math.abs(to - from) * ticksPerBlock));
    }

    /**
     * The opening at {@code time} (ticks, may be fractional) of a movement from {@code from} to {@code to} started at
     * {@code start}, lasting {@code duration} ticks.
     */
    public static double opening(double from, double to, double start, int duration, double time) {
        if (duration <= 0) return to;
        return from + (to - from) * ease((time - start) / duration);
    }

    /**
     * Where a block of the panel is drawn: its bottom and height relative to the bottom of the doorway, or null
     * once it has gone into the lintel.
     *
     * @param row    the block's height in the closed door (0 = lowest row of the door)
     * @param height height of the doorway (top of the highest block of the door, from its lowest row)
     * @param s      the door's opening
     */
    public static double[] slice(int row, int height, double s) {
        double bottom = row + s;
        if (bottom >= height - 1e-4) return null;
        double top = Math.min(bottom + 1, height);
        return new double[]{bottom, top - bottom};
    }

    /**
     * Whether a cell of the doorway is in the way at opening {@code s}: the panel's lowest block in that column
     * ({@code columnBottom}, its row in the closed door) has not yet risen past it.
     *
     * @param row the cell's row (0 = lowest row of the door)
     */
    public static boolean blocks(int row, int columnBottom, double s) {
        return columnBottom + s < row + 1 - CELL_MARGIN;
    }
}
