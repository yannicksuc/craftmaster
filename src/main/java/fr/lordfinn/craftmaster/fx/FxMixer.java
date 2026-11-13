package fr.lordfinn.craftmaster.fx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smooth on / off of the screen effects: each effect has an intensity in [0, 1] that moves linearly towards 1 while
 * it is wanted and towards 0 otherwise, over the fade time given when it was (un)wanted. The shaders read this
 * intensity (a fisheye bends a little more each frame, a round mask closes like an iris). Pure logic (client side
 * uses it every frame); time in seconds.
 *
 * <p>It also keeps, for each wanted effect, the seconds since it was switched on ({@link #sinceOn}): the focus pull
 * plays its move from there.
 */
public final class FxMixer {

    /** Default fade, in seconds. */
    public static final float DEFAULT_FADE = 0.6f;

    private final Map<String, Float> intensity = new HashMap<>();
    private final Map<String, Float> rate = new HashMap<>();
    private final Map<String, Boolean> wanted = new HashMap<>();
    private final Map<String, Float> since = new HashMap<>();

    /**
     * Wanted effects from now on. Effects that change state fade over {@code fadeSeconds} (0 = at once); the others
     * keep their running fade.
     */
    public void want(Collection<String> ids, float fadeSeconds) {
        for (ScreenFx.Kind k : ScreenFx.ORDER) {
            boolean on = ids.contains(k.id());
            if (wanted.getOrDefault(k.id(), false) != on) {
                wanted.put(k.id(), on);
                if (on) since.put(k.id(), 0f);
                else since.remove(k.id());
                rate.put(k.id(), fadeSeconds <= 0.001f ? Float.POSITIVE_INFINITY : 1f / fadeSeconds);
            }
        }
    }

    /** Moves every intensity towards its target by {@code dt} seconds. */
    public void advance(float dt) {
        if (dt <= 0) return;
        since.replaceAll((id, s) -> s + dt);
        for (ScreenFx.Kind k : ScreenFx.ORDER) {
            String id = k.id();
            float target = wanted.getOrDefault(id, false) ? 1f : 0f;
            float now = intensity.getOrDefault(id, 0f);
            if (now == target) continue;
            float step = rate.getOrDefault(id, 1f / DEFAULT_FADE) * dt;
            now = target > now ? Math.min(target, now + step) : Math.max(target, now - step);
            if (now <= 0f) intensity.remove(id);
            else intensity.put(id, now);
        }
    }

    /** Current intensity of an effect, 0 when off. */
    public float intensity(String id) {
        return intensity.getOrDefault(id, 0f);
    }

    /** Seconds since the effect was switched on (0 when it is not wanted). */
    public float sinceOn(String id) {
        return since.getOrDefault(id, 0f);
    }

    /** Starts the "since on" clock again for these effects when they are wanted (a new shot replays its move). */
    public void restart(Collection<String> ids) {
        for (String id : ids) if (since.containsKey(id)) since.put(id, 0f);
    }

    /** Effects to draw this frame (intensity above 0), in drawing order. */
    public List<String> visible() {
        List<String> out = new ArrayList<>();
        for (ScreenFx.Kind k : ScreenFx.ORDER) if (intensity.getOrDefault(k.id(), 0f) > 0f) out.add(k.id());
        return out;
    }

    /** Smoothed intensity for the shaders (ease in-out), so the start and the end of a fade are soft. */
    public static float eased(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public void clear() {
        intensity.clear();
        rate.clear();
        wanted.clear();
        since.clear();
    }
}
