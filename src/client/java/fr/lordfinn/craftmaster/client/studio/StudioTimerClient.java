package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.studio.StudioNetwork;

/**
 * What the giant timers show, from the server's {@link StudioNetwork.TimerState} packets: the running timer, or
 * "time's up" (0:00 blinking red for a few seconds), or the current round's set duration, or nothing.
 */
public final class StudioTimerClient {

    private StudioTimerClient() {}

    /** How long "0:00" blinks after the time is up (ms). */
    private static final long TIME_UP_MS = 8000;

    public enum Mode { RUNNING, TIME_UP, READY, IDLE }

    /** What to draw: the digits, their mode, the round's title ("" when none). */
    public record View(String clock, int seconds, Mode mode, String title) {}

    private static StudioNetwork.TimerState state = new StudioNetwork.TimerState(false, -1, 0, "", 0);
    private static long timeUpAt = Long.MIN_VALUE / 2;

    public static void receive(StudioNetwork.TimerState s) {
        // Running to stopped in the last second: the time ran out (a manual stop happens earlier)
        if (state.running() && !s.running() && state.remaining() <= 1) timeUpAt = System.currentTimeMillis();
        if (s.running()) timeUpAt = Long.MIN_VALUE / 2;
        state = s;
    }

    public static void clear() {
        state = new StudioNetwork.TimerState(false, -1, 0, "", 0);
        timeUpAt = Long.MIN_VALUE / 2;
    }

    public static View view() {
        StudioNetwork.TimerState s = state;
        String title = s.manche().isEmpty() ? "" : s.number() > 0 ? s.number() + " · " + s.manche() : s.manche();
        if (s.running()) return new View(fr.lordfinn.craftmaster.studio.SevenSegment.clock(s.remaining()), s.remaining(), Mode.RUNNING, title);
        if (System.currentTimeMillis() - timeUpAt < TIME_UP_MS) return new View("0:00", 0, Mode.TIME_UP, title);
        if (s.duration() > 0) return new View(fr.lordfinn.craftmaster.studio.SevenSegment.clock(s.duration()), s.duration(), Mode.READY, title);
        return new View("-:--", -1, Mode.IDLE, title);
    }
}
