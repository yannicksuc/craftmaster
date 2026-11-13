package fr.lordfinn.craftmaster.fx;

/**
 * Clock of the "stop_motion" effect: the picture is only refreshed {@link #FPS} times per second, like an animation
 * shot frame by frame. While the effect fades in or out the refresh rate rises (at intensity 0.5, twice as often),
 * so it eases in instead of freezing at once. Pure logic, the client asks it every frame.
 */
public final class StopMotionClock {

    /** Pictures per second at full intensity. */
    public static final float FPS = 7f;

    private float phase;
    private int image;
    private boolean first = true;

    /**
     * Advances by {@code dt} seconds at this intensity. True when this frame must take a new picture (always the first
     * frame after {@link #reset}, so a stale held picture is never shown).
     */
    public boolean advance(float dt, float intensity) {
        if (first) {
            first = false;
            phase = 0;
            image++;
            return true;
        }
        phase += Math.max(0f, dt) * FPS / Math.max(intensity, 0.05f);
        if (phase < 1f) return false;
        phase -= (float) Math.floor(phase);
        image++;
        return true;
    }

    /** Number of the picture held now (changes at every new picture; seeds the jitter). */
    public int image() {
        return image;
    }

    /** The next frame takes a new picture (effect switched on again, window resized). */
    public void reset() {
        first = true;
    }
}
