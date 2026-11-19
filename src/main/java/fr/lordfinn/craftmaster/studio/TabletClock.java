package fr.lordfinn.craftmaster.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalTime;
import java.util.Locale;

/**
 * The timer tablet's brain (pure, unit tested): its settings — what it counts, how it shows it, what starts and
 * stops it, how it looks — and its own timer's state. Times in milliseconds; the caller gives "now".
 */
public final class TabletClock {

    /** What the tablet shows. */
    public enum Mode {
        /** Counts down from {@link Settings#seconds} to 0. */
        TIMER,
        /** Counts up from 0 (a stopwatch). */
        STOPWATCH,
        /** The time of day (the viewer's clock). */
        CLOCK,
        /** The show's round timer (/cm manche lancer, the voice: what the giant timer shows by default). */
        SHOW
    }

    /** How the time is written. */
    public enum Format {
        /** M:SS, with tenths under 10 seconds ("9.4") for a timer, M:SS.d for a stopwatch under a minute. */
        AUTO,
        /** M:SS ("3:07"), H:MM:SS past an hour. */
        MINUTES,
        /** Seconds and tenths ("25.7", "187.3"). */
        TENTHS,
        /** M:SS.cc ("1:07.42"). */
        HUNDREDTHS,
        /** Whole seconds ("187"). */
        SECONDS
    }

    /** Redstone signal on the tablet's block. */
    public enum Redstone { OFF, TOGGLE, RUN_WHILE_POWERED, RESTART }

    /** Where it stands (also a block state property: its name in the blockstate file). */
    public enum Pose implements net.minecraft.util.StringRepresentable {
        STAND, WALL, FLAT, EASEL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum DigitColor {
        RED(0xFF2A20), AMBER(0xFFA020), GREEN(0x40FF60), BLUE(0x40B0FF), WHITE(0xF4F0E8), GOLD(0xFFC83C);
        public final int rgb;
        DigitColor(int rgb) { this.rgb = rgb; }
    }

    public enum FrameColor {
        // Same order as the rows of textures/entity/timer_tablet.png (scripts/gen_tablet.py)
        BLACK(0x242428), WALNUT(0x4E3020), OAK(0xA27E4C), GOLD(0xDEA830), STEEL(0xA2A6B0), WHITE(0xE2DCD2), RED(0x9C1C28);
        public final int rgb;
        FrameColor(int rgb) { this.rgb = rgb; }
    }

    /** The passe-partout between the frame and the screen (like a photo frame). */
    public enum Mat {
        // Same order as the passe-partout rows of the tablet texture; NONE last
        WHITE(0xF2EFE6), CREAM(0xE8D9B0), BLACK(0x2C2C30), NONE(-1);
        public final int rgb;
        Mat(int rgb) { this.rgb = rgb; }
    }

    public enum EndSound { NONE, BEEP, BELL, GONG, BUZZER }

    /** Every setting (saved with the block, edited in the settings screen). */
    public static final class Settings {
        /** Biggest tablet (the giant timer goes further: {@link #fromJson(String, int)}). */
        public static final int MAX_SIZE = 4;

        public Mode mode = Mode.TIMER;
        public Format format = Format.AUTO;
        /** Timer duration. */
        public int seconds = 60;
        /** Text under the digits ("" = none; in SHOW mode the round's name is used when empty). */
        public String label = "";
        public boolean showLabel = true;
        public DigitColor color = DigitColor.RED;
        /** Last seconds of a timer drawn in this colour and blinking (0 = never). */
        public int warnSeconds = 10;
        public DigitColor warnColor = DigitColor.RED;
        /** Unlit segments faintly visible, like a real display. */
        public boolean ghostSegments = true;
        public FrameColor frame = FrameColor.BLACK;
        public Mat mat = Mat.WHITE;
        /** Tablet: 1 = 12 × 9 pixels of frame, up to 4; giant timer: blocks high, up to 6. */
        public int size = 1;
        public Pose pose = Pose.STAND;
        /** Easel tilt / wall tilt in degrees (EASEL and STAND only: 0 upright). */
        public int tilt = 15;
        // ---- triggers
        /** Anyone's right click starts / pauses it (organisers open the settings unless they sneak). */
        public boolean clickToggles = false;
        public Redstone redstone = Redstone.TOGGLE;
        /** Restarts when a round starts (/cm manche lancer, the voice). */
        public boolean startOnRound = false;
        /** Pauses when a round ends. */
        public boolean stopOnRoundEnd = false;
        /** Pauses when the show's timer is stopped (/timer/stop, "arrête le chrono", /cm manche chrono stop). */
        public boolean stopWithTimer = true;
        /** Gives a redstone signal (comparator) once the timer is over. */
        public boolean signalWhenDone = true;
        public EndSound endSound = EndSound.BEEP;
        /** Blinks "0:00" when a timer is over. */
        public boolean blinkWhenDone = true;
        /**
         * Shows the time of the contestant who plays in this arena copy: their stopwatch, from their letter to their
         * finish (« La pomme »), drawn like a stopwatch. No click, round, redstone or group control then.
         */
        public boolean followContestant = false;
        /** Tablets with the same group are started / paused together ("" = alone). */
        public String group = "";

        public Settings copy() {
            return fromJson(toJson(), Integer.MAX_VALUE);
        }

        public String toJson() {
            return GSON.toJson(this);
        }

        /** Reads a tablet's settings, every missing or unknown field keeping its default, values clamped. */
        public static Settings fromJson(String json) {
            return fromJson(json, MAX_SIZE);
        }

        /** The same, the size up to {@code maxSize}. */
        public static Settings fromJson(String json, int maxSize) {
            Settings s;
            try {
                s = json == null || json.isBlank() ? new Settings() : GSON.fromJson(json, Settings.class);
            } catch (RuntimeException e) {
                s = new Settings();
            }
            if (s == null) s = new Settings();
            Settings d = new Settings();
            if (s.mode == null) s.mode = d.mode;
            if (s.format == null) s.format = d.format;
            if (s.color == null) s.color = d.color;
            if (s.warnColor == null) s.warnColor = d.warnColor;
            if (s.frame == null) s.frame = d.frame;
            if (s.mat == null) s.mat = d.mat;
            if (s.pose == null) s.pose = d.pose;
            if (s.redstone == null) s.redstone = d.redstone;
            if (s.endSound == null) s.endSound = d.endSound;
            if (s.label == null) s.label = "";
            if (s.group == null) s.group = "";
            s.label = s.label.length() > 40 ? s.label.substring(0, 40) : s.label;
            s.group = s.group.trim().toLowerCase(Locale.ROOT);
            if (s.group.length() > 24) s.group = s.group.substring(0, 24);
            s.seconds = Math.max(1, Math.min(99 * 3600, s.seconds));
            s.warnSeconds = Math.max(0, Math.min(600, s.warnSeconds));
            s.size = Math.max(1, Math.min(maxSize, s.size));
            s.tilt = Math.max(0, Math.min(60, s.tilt));
            return s;
        }
    }

    private static final Gson GSON = new GsonBuilder().create();

    // ---------------------------------------------------------------- own timer state

    private boolean running;
    /** Time counted before the current run (ms). */
    private long banked;
    /** When the current run started (the caller's clock, ms). */
    private long startedAt;

    public boolean running() {
        return running;
    }

    /** Time counted so far (ms). */
    public long elapsed(long now) {
        return banked + (running ? Math.max(0, now - startedAt) : 0);
    }

    public void start(long now) {
        if (running) return;
        running = true;
        startedAt = now;
    }

    public void pause(long now) {
        if (!running) return;
        banked = elapsed(now);
        running = false;
    }

    public void toggle(long now) {
        if (running) pause(now);
        else start(now);
    }

    public void reset() {
        running = false;
        banked = 0;
    }

    /** From zero, running. */
    public void restart(long now) {
        reset();
        start(now);
    }

    /** Sets the time counted so far (ms), keeping running or not (to sync a copy). */
    public void set(boolean running, long elapsed, long now) {
        this.running = running;
        this.banked = running ? 0 : elapsed;
        this.startedAt = now - (running ? elapsed : 0);
    }

    /** A timer's time left (ms, never below 0). */
    public long remaining(Settings s, long now) {
        return Math.max(0, s.seconds * 1000L - elapsed(now));
    }

    /** Is a timer over (all its time counted)? */
    public boolean done(Settings s, long now) {
        return s.mode == Mode.TIMER && remaining(s, now) == 0;
    }

    /** The milliseconds the digits show: time left for a timer, time counted for a stopwatch. */
    public long shown(Settings s, long now) {
        return s.mode == Mode.TIMER ? remaining(s, now) : elapsed(now);
    }

    // ---------------------------------------------------------------- text

    /** The digits for {@code ms} in {@code format} ({@code countdown}: AUTO shows tenths in the last 10 s). */
    public static String format(long ms, Format format, boolean countdown) {
        long t = Math.max(0, ms);
        return switch (format) {
            case MINUTES -> minutes(countdown ? ceilSeconds(t) : t / 1000);
            case TENTHS -> (countdown ? ceilTenths(t) / 10 : t / 1000) + "." + (countdown ? ceilTenths(t) % 10 : (t / 100) % 10);
            case HUNDREDTHS -> {
                long cs = t / 10;
                yield minutes(cs / 100) + "." + two(cs % 100);
            }
            case SECONDS -> String.valueOf(countdown ? ceilSeconds(t) : t / 1000);
            case AUTO -> {
                if (countdown && t < 10_000) {
                    long d = ceilTenths(t);
                    yield d / 10 + "." + d % 10;
                }
                if (!countdown && t < 60_000) yield t / 1000 + "." + (t / 100) % 10;
                yield minutes(countdown ? ceilSeconds(t) : t / 1000);
            }
        };
    }

    /** A countdown shows 0:01 until it really reaches zero. */
    private static long ceilSeconds(long ms) {
        return (ms + 999) / 1000;
    }

    private static long ceilTenths(long ms) {
        return (ms + 99) / 100;
    }

    /** 187 → "3:07", 3725 → "1:02:05". */
    public static String minutes(long seconds) {
        long h = seconds / 3600, m = (seconds / 60) % 60, s = seconds % 60;
        return h > 0 ? h + ":" + two(m) + ":" + two(s) : m + ":" + two(s);
    }

    /** The time of day: "14:05" (MINUTES / AUTO / SECONDS), "14:05:09" with seconds (TENTHS, HUNDREDTHS). */
    public static String clock(LocalTime t, Format format) {
        String hm = two(t.getHour()) + ":" + two(t.getMinute());
        return format == Format.TENTHS || format == Format.HUNDREDTHS ? hm + ":" + two(t.getSecond()) : hm;
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** "1:30" or "90" → seconds (m:ss, h:mm:ss or seconds); -1 if unreadable. */
    public static int parseDuration(String text) {
        if (text == null) return -1;
        String[] parts = text.trim().replace(',', '.').split(":");
        try {
            int total = 0;
            for (String p : parts) {
                if (p.isEmpty()) return -1;
                total = total * 60 + (int) Math.round(Double.parseDouble(p));
            }
            return total > 0 ? total : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
