package fr.lordfinn.craftmaster.studio;

/**
 * The spotlights' light effects: a factor (0..1) on the intensity at a time, the same on every client (the time is
 * the world's game time, the phase spreads a group into a chase).
 */
public final class SpotlightEffects {

    private SpotlightEffects() {}

    /**
     * @param effect  "respiration" (slow breath), "pulse" (beat), "strobe" (flashes), "scintille" (flicker),
     *                "chenillard" (on in turn across a group, by phase); anything else: 1
     * @param seconds the world's time in seconds
     */
    public static float factor(String effect, float speed, float phase, double seconds) {
        if (effect == null || effect.isEmpty()) return 1;
        double t = seconds * speed;
        return switch (effect) {
            case "respiration" -> (float) (0.55 + 0.45 * Math.sin(2 * Math.PI * (t / 4.0 + phase)));
            case "pulse" -> {
                double x = frac(t + phase);
                yield (float) (0.25 + 0.75 * Math.exp(-6 * x));
            }
            case "strobe" -> frac(t * 8 + phase) < 0.25 ? 1 : 0;
            case "scintille" -> {
                long n = (long) Math.floor(t * 12 + phase * 977);
                long h = (n * 0x9E3779B97F4A7C15L) ^ (n >>> 7);
                yield 0.6f + 0.4f * ((h >>> 11) & 0xFF) / 255f;
            }
            case "chenillard" -> {
                double x = frac(t / 2.0 - phase);
                yield x < 0.3 ? 1 : (float) Math.max(0.05, 1 - (x - 0.3) * 4);
            }
            default -> 1;
        };
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }
}
