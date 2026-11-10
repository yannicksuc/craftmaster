package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.camera.CameraPath;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * The last show state received from the server (every client with the mod gets it, in broadcast mode or not), and
 * the clock to count the round timer down smoothly between two states.
 */
public final class ShowStateClient {

    private static ShowState state = ShowState.EMPTY;
    private static CameraList cameras = new CameraList();
    private static CameraList focused = new CameraList();
    private static Map<String, CameraPath> paths = Map.of();
    /** Real time (ms) when the state arrived: the timer is counted from there. */
    private static long receivedAt;

    private ShowStateClient() {}

    public static ShowState get() {
        return state;
    }

    /** Every camera, of every lieu. */
    public static CameraList cameras() {
        return cameras;
    }

    /** The cameras the keys 1–9 address now (the director's focus). */
    public static CameraList focused() {
        return focused;
    }

    public static CameraPath path(String name) {
        return paths.get(name);
    }

    static void set(ShowState s) {
        state = s;
        cameras = new CameraList(s.cameras());
        focused = cameras.group(s.focus());
        Map<String, CameraPath> p = new HashMap<>();
        for (CameraPath c : s.paths()) p.put(c.name(), c);
        paths = p;
        receivedAt = System.currentTimeMillis();
    }

    static void clear() {
        set(ShowState.EMPTY);
    }

    /** Seconds left on the round timer (fractional, for smooth animations), or -1 when none runs. */
    public static double timerSeconds() {
        if (state.timerEnd() < 0) return -1;
        double elapsedTicks = (System.currentTimeMillis() - receivedAt) / 50.0;
        double left = state.timerEnd() - state.serverTick() - elapsedTicks;
        return Math.max(0, left / 20.0);
    }

    /** Share of the timer left, 1 → 0. */
    public static double timerProgress() {
        if (state.timerEnd() < 0 || state.timerTotal() <= 0) return 0;
        return Math.max(0, Math.min(1, timerSeconds() * 20 / state.timerTotal()));
    }

    /** The current dimension of the local game, as the cameras name it. */
    static String world(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }
}
