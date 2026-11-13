package fr.lordfinn.craftmaster.toy;

import java.util.Locale;

/**
 * The maths of the toys, kept free of Minecraft classes so it is tested: bounces, the fall height an impact
 * stands for (fragile egg), the charge of a throw, the golf cup, colours. Units: blocks and ticks.
 */
public final class ToyPhysics {

    private ToyPhysics() {}

    /** Gravity of the toys, blocks per tick² (items and snowballs use 0.03 to 0.04). */
    public static final double GRAVITY = 0.04;
    /** Below this speed a bounce dies out (no endless jitter on the ground). */
    public static final double BOUNCE_MIN = 0.08;

    /** The speed along an axis after hitting a wall or the ground: reversed and damped, or stopped. */
    public static double bounce(double speed, double restitution) {
        double out = -speed * restitution;
        return Math.abs(out) < BOUNCE_MIN ? 0 : out;
    }

    /** The height of a free fall that ends at this speed: v² = 2 g h. */
    public static double fallHeightFor(double impactSpeed) {
        return impactSpeed * impactSpeed / (2 * GRAVITY);
    }

    /**
     * Does an egg break? {@code fragility} is the highest fall it survives, in blocks: 0 breaks at the least shock
     * (any impact faster than a gentle set-down).
     */
    public static boolean eggBreaks(double impactSpeed, double fragility) {
        if (fragility <= 0) return impactSpeed > 0.12;
        return fallHeightFor(impactSpeed) > fragility + 0.05;
    }

    /** Bow-like charge: 0 → 1 over a second, faster at the start. */
    public static double charge(int ticksUsed) {
        double t = ticksUsed / 20.0;
        return Math.min(1, (t * t + 2 * t) / 3);
    }

    /** Speed of a throw: a short click is a gentle toss, a full charge is the kind's top speed. */
    public static double throwSpeed(ToyKind kind, int ticksUsed) {
        return 0.25 + charge(ticksUsed) * (kind.maxThrowSpeed - 0.25);
    }

    /**
     * Golf: does a ball moving from (x0, z0) to (x1, z1) this tick pass close enough to the cup's centre (cx, cz)?
     * The closest point of the segment counts (a quick ball still drops), but a ball too fast skims over it.
     */
    public static boolean dropsInCup(double x0, double z0, double x1, double z1, double cx, double cz, double radius, double speed) {
        if (speed > 0.7) return false;
        double dx = x1 - x0, dz = z1 - z0;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((cx - x0) * dx + (cz - z0) * dz) / len2));
        double px = x0 + dx * t - cx, pz = z0 + dz * t - cz;
        // The faster the ball, the more of the cup it needs to hit
        double r = radius * (1 - speed * 0.8);
        return px * px + pz * pz <= r * r;
    }

    /** Horizontal distance, for throws. */
    public static double flatDistance(double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** "1,5 bloc", "7,4 blocs" (French: singular below 2). */
    public static String blocksText(double d) {
        return blocks(d) + (Math.abs(d) < 2 ? " bloc" : " blocs");
    }

    /** "7,4" (French decimal comma, one decimal). */
    public static String blocks(double d) {
        return String.format(Locale.FRANCE, "%.1f", d);
    }

    // ---------------------------------------------------------------- colours

    /** The 16 dye colours of the game (DyeColor ids and their texture colour), in the game's order. */
    public static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final int[] DYE_RGB = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    private static final String[] FR_M = {"blanc", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "gris",
            "gris clair", "cyan", "violet", "bleu", "marron", "vert", "rouge", "noir"};
    private static final String[] FR_F = {"blanche", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "grise",
            "gris clair", "cyan", "violette", "bleue", "marron", "verte", "rouge", "noire"};

    /** The dye colour closest to an RGB colour (mixed dyes give in-between colours), as an index in {@link #DYES}. */
    public static int nearestDye(int rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < DYE_RGB.length; i++) {
            long dr = ((rgb >> 16) & 255) - ((DYE_RGB[i] >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((DYE_RGB[i] >> 8) & 255);
            long db = (rgb & 255) - (DYE_RGB[i] & 255);
            long d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** French colour adjective, agreeing with the noun. */
    public static String colourFr(int dye, boolean female) {
        return (female ? FR_F : FR_M)[Math.max(0, Math.min(15, dye))];
    }
}
