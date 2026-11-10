package fr.lordfinn.craftmaster.trial;

import net.minecraft.server.MinecraftServer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What happened with the task objects since the server started (buttons pressed, buzzers, props touched first,
 * zone exits), newest first, for {@code /craftMasterTrial log}. In memory only, like the undo journal.
 */
public final class TrialLog {

    private TrialLog() {}

    public static final int SIZE = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> LINES = new ArrayDeque<>();

    public static synchronized void add(MinecraftServer server, String line) {
        LINES.addFirst(LocalTime.now().format(TIME) + "  " + line);
        while (LINES.size() > SIZE) LINES.removeLast();
    }

    /** The {@code n} latest lines, newest first. */
    public static synchronized List<String> latest(int n) {
        List<String> out = new ArrayList<>();
        for (String l : LINES) {
            if (out.size() >= n) break;
            out.add(l);
        }
        return out;
    }

    public static synchronized void clear() {
        LINES.clear();
    }

    /** "14:03:07" for an epoch time in ms. */
    public static String clock(long epochMs) {
        return java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(TIME);
    }
}
