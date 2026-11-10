package fr.lordfinn.craftmaster.client.portrait;

/**
 * A smooth game clock for the portraits' motion (sway, rearrangement, highlight pulse)
 * and the curtains' slide, in ticks.
 * <p>
 * The client's game time only moves when a tick runs or when the server sends the time, so "game time + partial
 * tick" can stall and jump. This clock runs on real time (20 ticks per second, to the nanosecond, every frame),
 * anchored to the last game time seen and pulled toward it gently, so it never jumps; it snaps only when it is far
 * off (joining a world, a /time change).
 */
public final class PortraitClock {

    private PortraitClock() {}

    private static final double NANOS_PER_TICK = 5.0e7;
    private static long syncGameTime = Long.MIN_VALUE, syncNanos, lastNanos;
    private static double estimate;

    /** Current time in ticks (same origin as the level's game time). */
    public static double ticks(long gameTime) {
        long now = System.nanoTime();
        if (gameTime != syncGameTime) {
            syncGameTime = gameTime;
            syncNanos = now;
        }
        double anchor = syncGameTime + (now - syncNanos) / NANOS_PER_TICK;
        if (lastNanos == 0 || Math.abs(anchor - estimate) > 40) {
            estimate = anchor;
        } else {
            estimate += (now - lastNanos) / NANOS_PER_TICK;
            estimate += (anchor - estimate) * 0.05;
        }
        lastNanos = now;
        return estimate;
    }
}
