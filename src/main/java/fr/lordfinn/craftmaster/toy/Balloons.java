package fr.lordfinn.craftmaster.toy;

/**
 * Balloon rules (pure, tested): it grows by puffs while the use button is held, a little less with each puff
 * (rubber gets stiff), and bursts past a limit drawn at random for each balloon, between 75 % and 100 % of the
 * largest size, so nobody knows exactly when it will go.
 */
public final class Balloons {

    private Balloons() {}

    /** Size of a new (flat) balloon and of the largest one, in blocks (diameter). */
    public static final float MIN = 0.3f, MAX = 2.4f;
    /** A puff every this many ticks while held. */
    public static final int PUFF_TICKS = 6;
    /** Below this size, using it on a block inflates it instead of putting it down. */
    public static final float PLACEABLE = 0.45f;

    /** The size after one more puff. */
    public static float puff(float size) {
        float s = Math.max(MIN, size);
        float step = 0.12f * (1.15f - (s - MIN) / (MAX - MIN) * 0.6f);
        return Math.min(MAX * 1.05f, s + step);
    }

    /** The bursting size of a balloon, from a random number in [0, 1). */
    public static float burstAt(double random) {
        return (float) (MAX * (0.75 + 0.25 * random));
    }

    public static boolean bursts(float size, float burstAt) {
        return size >= burstAt;
    }

    /** How full it is, for the hand model and messages: 0 (flat) to 1 (largest). */
    public static float fullness(float size) {
        return Math.max(0, Math.min(1, (size - MIN) / (MAX - MIN)));
    }

    /** Pitch of the puff sound: high when small, low when big. */
    public static float puffPitch(float size) {
        return 1.6f - fullness(size) * 0.9f;
    }
}
