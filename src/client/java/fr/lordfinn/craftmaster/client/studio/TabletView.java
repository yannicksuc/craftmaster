package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.studio.TabletClock;

import java.time.LocalTime;

/**
 * What a timer tablet shows right now: the digits, their colour and brightness (blinking), the label. Shared by
 * the renderer and the settings screen's preview.
 */
public final class TabletView {

    private TabletView() {}

    /** How long "time's up" blinks (ms). */
    private static final long OVER_BLINK_MS = 8000;

    /** {@code bright}: 1 lit, lower = dimmed (blink / ready), {@code label} "" = none. */
    public record View(String text, int rgb, float bright, String label) {}

    public static View of(TabletClock.Settings s, TabletClock clock, long overAt, long now) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI * 2));
        float blink = (now / 400) % 2 == 0 ? 1f : 0.25f;
        String label = s.showLabel ? s.label : "";
        return switch (s.mode) {
            case CLOCK -> new View(TabletClock.clock(LocalTime.now(), s.format), s.color.rgb, 1, label);
            case STOPWATCH -> new View(TabletClock.format(clock.elapsed(now), s.format, false), s.color.rgb, clock.running() ? 1 : 0.8f, label);
            case TIMER -> {
                long left = clock.remaining(s, now);
                String text = TabletClock.format(left, s.format, true);
                boolean over = left == 0 && clock.elapsed(now) > 0;
                if (over) yield new View(text, s.warnColor.rgb, s.blinkWhenDone && now - overAt < OVER_BLINK_MS ? blink : 1, label);
                boolean warn = clock.running() && s.warnSeconds > 0 && left <= s.warnSeconds * 1000L;
                if (warn) yield new View(text, s.warnColor.rgb, 0.6f + 0.4f * pulse, label);
                yield new View(text, s.color.rgb, clock.running() ? 1 : 0.8f, label);
            }
            case SHOW -> {
                StudioTimerClient.View v = StudioTimerClient.view();
                String l = s.showLabel ? (s.label.isEmpty() ? v.title() : s.label) : "";
                // The show's timer is known to the second: tenths would be made up
                TabletClock.Format f = s.format == TabletClock.Format.TENTHS || s.format == TabletClock.Format.HUNDREDTHS
                        ? TabletClock.Format.SECONDS : s.format == TabletClock.Format.AUTO ? TabletClock.Format.MINUTES : s.format;
                yield switch (v.mode()) {
                    case RUNNING -> {
                        boolean warn = s.warnSeconds > 0 && v.seconds() <= s.warnSeconds;
                        yield new View(TabletClock.format(v.seconds() * 1000L, f, true), warn ? s.warnColor.rgb : s.color.rgb,
                                warn ? 0.6f + 0.4f * pulse : 1, l);
                    }
                    case TIME_UP -> new View(TabletClock.format(0, f, true), s.warnColor.rgb, s.blinkWhenDone ? blink : 1, l);
                    case READY -> new View(TabletClock.format(v.seconds() * 1000L, f, true), s.color.rgb, 0.6f, l);
                    case IDLE -> new View("-:--", s.color.rgb, 0.35f, l);
                };
            }
        };
    }

    /** The colour scaled toward the dark screen by {@code bright}. */
    public static int lit(int rgb, float bright, int off) {
        float t = Math.max(0, Math.min(1, bright));
        int r = (int) (((rgb >> 16) & 255) * t + ((off >> 16) & 255) * (1 - t));
        int g = (int) (((rgb >> 8) & 255) * t + ((off >> 8) & 255) * (1 - t));
        int b = (int) ((rgb & 255) * t + (off & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Unlit segments: the digit colour very dark. */
    public static int ghost(int rgb) {
        return lit(rgb, 0.1f, 0x050505);
    }
}
