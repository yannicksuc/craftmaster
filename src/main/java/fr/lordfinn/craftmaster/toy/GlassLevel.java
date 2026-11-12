package fr.lordfinn.craftmaster.toy;

import java.util.Locale;

/**
 * The water in a graduated container, in millilitres (pure, tested). A bucket is 1000 mL, a bottle 250 mL;
 * water running into it from above adds a trickle. The organisers can draw a line (a target level) and the
 * reading says how far the water is from it.
 */
public final class GlassLevel {

    public static final int BUCKET = 1000, BOTTLE = 250, TRICKLE = 50;
    /** Visual levels of the block (0 = empty, 8 = full). */
    public static final int STAGES = 8;

    private final int capacity;
    private int ml;
    /** The line drawn by an organiser, or -1. */
    private int line = -1;

    public GlassLevel(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int ml() {
        return ml;
    }

    public int line() {
        return line;
    }

    public void set(int ml) {
        this.ml = Math.max(0, Math.min(capacity, ml));
    }

    public void setLine(int line) {
        this.line = line < 0 ? -1 : Math.min(capacity, line);
    }

    /** Pours water in; returns what went in (the rest overflows). */
    public int pour(int amount) {
        int in = Math.max(0, Math.min(amount, capacity - ml));
        ml += in;
        return in;
    }

    /** Takes water out if there is that much; returns true if taken. */
    public boolean take(int amount) {
        if (ml < amount) return false;
        ml -= amount;
        return true;
    }

    public boolean full() {
        return ml >= capacity;
    }

    /** The visual stage: the smallest non-zero amount already shows a little water. */
    public int stage() {
        if (ml <= 0) return 0;
        return Math.max(1, Math.min(STAGES, (int) Math.round(ml * (double) STAGES / capacity)));
    }

    /** Comparator strength, 0 to 15. */
    public int signal() {
        if (ml <= 0) return 0;
        return Math.max(1, (int) Math.floor(ml * 15.0 / capacity));
    }

    /** "650 mL / 1 000 mL", then the line: "trait : 500 mL (+150)". */
    public String reading() {
        String s = format(ml) + " / " + format(capacity);
        if (line >= 0) {
            int d = ml - line;
            s += " · trait " + format(line) + " (" + (d == 0 ? "pile !" : (d > 0 ? "+" : "−") + format(Math.abs(d))) + ")";
        }
        return s;
    }

    public static String format(int ml) {
        if (ml >= 10_000) return String.format(Locale.FRANCE, "%.1f L", ml / 1000.0);
        return String.format(Locale.FRANCE, "%,d mL", ml).replace(' ', ' ').replace(' ', ' ');
    }
}
