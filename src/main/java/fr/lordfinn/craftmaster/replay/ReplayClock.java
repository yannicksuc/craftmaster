package fr.lordfinn.craftmaster.replay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A file's clock against the server's ticks: samples {ms in the file, server tick} taken every 10 s while it was
 * recorded. A file's time runs on the wall clock, the show's timers on the server's ticks; when the server lags they
 * part (measured: ~1 s per hour at 20 TPS, more under load). Between two samples it is linear, past the ends one
 * tick is 50 ms. Older files only have their first tick ({@code tick0}); the oldest none at all ({@link #known()}).
 */
public final class ReplayClock {

    private final List<long[]> samples;
    private final double tick0;

    /** @param tick0 the tick at the file's time 0, or -1; @param samples {ms, tick} pairs, any order */
    public ReplayClock(double tick0, List<long[]> samples) {
        this.tick0 = tick0;
        this.samples = new ArrayList<>(samples == null ? List.of() : samples);
        this.samples.sort(Comparator.comparingLong(s -> s[0]));
    }

    public static final ReplayClock UNKNOWN = new ReplayClock(-1, List.of());

    public boolean known() {
        return tick0 >= 0 || !samples.isEmpty();
    }

    /** The server tick at {@code ms} in the file, or NaN when unknown. */
    public double tickAt(long ms) {
        if (samples.isEmpty()) return tick0 < 0 ? Double.NaN : tick0 + ms / 50.0;
        long[] first = samples.getFirst(), last = samples.getLast();
        if (ms <= first[0]) return first[1] - (first[0] - ms) / 50.0;
        if (ms >= last[0]) return last[1] + (ms - last[0]) / 50.0;
        for (int i = 1; i < samples.size(); i++) {
            long[] a = samples.get(i - 1), b = samples.get(i);
            if (ms <= b[0]) {
                if (b[0] == a[0]) return b[1];
                return a[1] + (b[1] - a[1]) * (ms - a[0]) / (double) (b[0] - a[0]);
            }
        }
        return last[1];
    }

    /** The time in the file (ms) at server tick {@code tick}, or -1 when unknown. */
    public long msAt(double tick) {
        if (samples.isEmpty()) return tick0 < 0 ? -1 : Math.round((tick - tick0) * 50);
        long[] first = samples.getFirst(), last = samples.getLast();
        if (tick <= first[1]) return Math.round(first[0] - (first[1] - tick) * 50);
        if (tick >= last[1]) return Math.round(last[0] + (tick - last[1]) * 50);
        for (int i = 1; i < samples.size(); i++) {
            long[] a = samples.get(i - 1), b = samples.get(i);
            if (tick <= b[1]) {
                if (b[1] == a[1]) return b[0];
                return Math.round(a[0] + (b[0] - a[0]) * (tick - a[1]) / (b[1] - a[1]));
            }
        }
        return last[0];
    }
}
