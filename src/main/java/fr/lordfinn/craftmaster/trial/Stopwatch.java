package fr.lordfinn.craftmaster.trial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stopwatches of the contestants: each one runs on its own (a solo attempt while the others wait), started and
 * stopped by the judge, a task prop or the voice. Times are in milliseconds of the server's clock; pure (the
 * clock is passed in), so it can be tested.
 */
public final class Stopwatch {

    /** The server's stopwatches (one per contestant), shared by the actions, commands, props and screens. */
    public static final Stopwatch SHARED = new Stopwatch();

    /** Start time of each running stopwatch, by player, in start order. */
    private final Map<String, Long> running = new LinkedHashMap<>();
    /** Last time measured per player (kept after the stop, shown until the next start). */
    private final Map<String, Long> last = new LinkedHashMap<>();

    /** Starts (or restarts from 0) the player's stopwatch. */
    public void start(String player, long now) {
        running.put(player, now);
        last.remove(player);
    }

    /** Stops it: the time measured, or -1 if it was not running. */
    public long stop(String player, long now) {
        Long t0 = running.remove(player);
        if (t0 == null) return -1;
        long elapsed = Math.max(0, now - t0);
        last.put(player, elapsed);
        return elapsed;
    }

    /** Elapsed time of a running stopwatch (it keeps running), or -1. */
    public long peek(String player, long now) {
        Long t0 = running.get(player);
        return t0 == null ? -1 : Math.max(0, now - t0);
    }

    public boolean isRunning(String player) {
        return running.containsKey(player);
    }

    /** Last measured time, or -1. */
    public long last(String player) {
        return last.getOrDefault(player, -1L);
    }

    public void reset(String player) {
        running.remove(player);
        last.remove(player);
    }

    public void resetAll() {
        running.clear();
        last.clear();
    }

    /** Players whose stopwatch runs, in start order. */
    public List<String> runningPlayers() {
        return new ArrayList<>(running.keySet());
    }

    /** Player name exactly as stored, ignoring case; null if unknown. */
    public String find(String player) {
        for (String p : running.keySet()) if (p.equalsIgnoreCase(player)) return p;
        for (String p : last.keySet()) if (p.equalsIgnoreCase(player)) return p;
        return null;
    }

    /** 42 300 ms → "42,3 s"; 62 500 → "1:02,5"; 3 723 000 → "1:02:03,0". Tenths, French comma. */
    public static String format(long ms) {
        if (ms < 0) ms = 0;
        long tenths = Math.round(ms / 100.0);
        long seconds = tenths / 10, t = tenths % 10;
        if (seconds < 60) return seconds + "," + t + " s";
        long minutes = seconds / 60, s = seconds % 60;
        if (minutes < 60) return minutes + ":" + two(s) + "," + t;
        return minutes / 60 + ":" + two(minutes % 60) + ":" + two(s) + "," + t;
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
