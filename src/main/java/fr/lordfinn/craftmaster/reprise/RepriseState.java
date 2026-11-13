package fr.lordfinn.craftmaster.reprise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the show's clocks stood at, saved every second in {@code config/craftmaster/reprise.json} so that a server
 * restarted after a crash puts every timer back where it was (pure, unit tested). Durations only — the time spent
 * while the server was down never counts.
 */
public final class RepriseState {

    /** Epoch ms of the save (to tell the organisers how old it is). */
    public long savedAt;
    /** The show was frozen when this was saved (waiting for the go). */
    public boolean frozen;
    /** The round being played: season, game, round id (null = none). */
    public Integer season, partie;
    public String manche;
    /** The round timer (boss bar), null if none ran. */
    public Timer timer;
    /** Contestants' running stopwatches: time counted so far (ms), in start order. */
    public Map<String, Long> stopwatches = new LinkedHashMap<>();
    /** Their last measured times (ms). */
    public Map<String, Long> lastTimes = new LinkedHashMap<>();
    /** Who buzzed since the last reset, the delay since the first press (ms, -1 = nobody), the lit buzzers. */
    public List<Buzz> buzzes = new ArrayList<>();
    public long sinceFirstBuzz = -1;
    public List<Place> litBuzzers = new ArrayList<>();
    /** Every timer tablet with its time. */
    public List<Tablet> tablets = new ArrayList<>();

    /** The round timer: its round's name, its length and the ticks left (20 a second). */
    public static final class Timer {
        public String name = "";
        public long totalTicks, leftTicks;

        public Timer() {}

        public Timer(String name, long totalTicks, long leftTicks) {
            this.name = name;
            this.totalTicks = totalTicks;
            this.leftTicks = leftTicks;
        }
    }

    public static final class Buzz {
        public int order;
        public String player = "";
        public long delayMs;

        public Buzz() {}

        public Buzz(int order, String player, long delayMs) {
            this.order = order;
            this.player = player;
            this.delayMs = delayMs;
        }
    }

    /** A block: dimension id and position. */
    public static class Place {
        public String dim = "";
        public int x, y, z;

        public Place() {}

        public Place(String dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String key() {
            return dim + " " + x + " " + y + " " + z;
        }
    }

    /** A timer tablet: was it running (it starts again at the go), and the time it had counted (ms). */
    public static final class Tablet extends Place {
        public boolean running;
        public long elapsedMs;

        public Tablet() {}

        public Tablet(String dim, int x, int y, int z, boolean running, long elapsedMs) {
            super(dim, x, y, z);
            this.running = running;
            this.elapsedMs = elapsedMs;
        }
    }

    /** Is this the round being played now? */
    public boolean sameRound(Integer season, Integer partie, String manche) {
        return Objects.equals(this.season, season) && Objects.equals(this.partie, partie) && Objects.equals(this.manche, manche);
    }

    /** Nothing to put back: no round, no clock. */
    public boolean empty() {
        return manche == null && timer == null && stopwatches.isEmpty() && buzzes.isEmpty() && tablets.stream().noneMatch(t -> t.running);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Reads a save; a missing, empty or broken file gives null. */
    public static RepriseState fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            RepriseState s = GSON.fromJson(json, RepriseState.class);
            if (s == null) return null;
            if (s.stopwatches == null) s.stopwatches = new LinkedHashMap<>();
            if (s.lastTimes == null) s.lastTimes = new LinkedHashMap<>();
            if (s.buzzes == null) s.buzzes = new ArrayList<>();
            if (s.litBuzzers == null) s.litBuzzers = new ArrayList<>();
            if (s.tablets == null) s.tablets = new ArrayList<>();
            s.stopwatches.values().removeIf(Objects::isNull);
            s.lastTimes.values().removeIf(Objects::isNull);
            s.buzzes.removeIf(b -> b == null || b.player == null);
            s.litBuzzers.removeIf(p -> p == null || p.dim == null);
            s.tablets.removeIf(t -> t == null || t.dim == null);
            if (s.timer != null && s.timer.name == null) s.timer.name = "";
            return s;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }
}
