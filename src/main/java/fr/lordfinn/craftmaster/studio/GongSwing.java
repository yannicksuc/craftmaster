package fr.lordfinn.craftmaster.studio;

/** The gong disc's swing after a strike: a damped pendulum, pushed away from the striker first. Pure, unit tested. */
public final class GongSwing {

    private GongSwing() {}

    /** Length of the swing after a strike (ticks). */
    public static final int SWING_TICKS = 60;

    /** Swing angle (degrees) {@code ticks} after a strike, 0 outside the swing. */
    public static float angle(double ticks) {
        if (ticks < 0 || ticks > SWING_TICKS) return 0;
        double t = ticks / 20.0;
        return (float) (-14 * Math.exp(-1.6 * t) * Math.sin(2 * Math.PI * 1.1 * t));
    }
}
