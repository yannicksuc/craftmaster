package fr.lordfinn.craftmaster.client.replay;

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Contestant;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.camera.CameraSpot;

import java.util.ArrayList;
import java.util.List;

/**
 * Who directs a replay on this game (the broadcast camera, F8, shows its shots):
 * <ul>
 *   <li>{@link Mode#DIRECT}: the shots chosen live, as recorded; where the live had none (no broadcast then), the
 *   automatic direction below;</li>
 *   <li>{@link Mode#AUTO}: recomputed from the recording's markers, like the live automatic direction: who scores
 *   or is put in the spotlight (envelope, buzzer, projecteur) is followed for 7 or 9 s, the wide shot otherwise;</li>
 *   <li>{@link Mode#MANUAL}: the keys 1–9 and the mouse wheel (cameras of the focused lieu), 0 the wide shot, Tab a
 *   follow shot on the next contestant (Shift: through their eyes), here on this game only;</li>
 *   <li>{@link Mode#FREE}: no direction, this game's own spectator camera (broadcast mode off).</li>
 * </ul>
 */
public final class ReplayDirector {

    public enum Mode {
        DIRECT("Direct"), AUTO("Auto"), MANUAL("Manuel"), FREE("Libre");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** Serials of the shots decided here: far from the live ones, so each change is seen as a new shot. */
    private static int serial = 1_000_000;

    private static Mode mode = Mode.DIRECT;
    private static Shot manual;
    private static String autoKey = "";
    private static Shot autoShot = Shot.NONE;
    private static String lastPlayer = "";

    private ReplayDirector() {}

    public static Mode mode() {
        return mode;
    }

    static void setMode(Mode m) {
        mode = m;
        if (m != Mode.MANUAL) manual = null;
    }

    static void reset() {
        mode = Mode.DIRECT;
        manual = null;
        autoKey = "";
        autoShot = Shot.NONE;
        lastPlayer = "";
    }

    /** The shot this game shows instead of the recorded one, or null (the recording's own). */
    public static Shot override(ShowState recorded) {
        if (!ReplayClient.active()) return null;
        return switch (mode) {
            case FREE -> null;
            case MANUAL -> manual;
            case AUTO -> auto(recorded);
            case DIRECT -> recorded.shot().kind() == BroadcastNetwork.SHOT_NONE && recorded.sequence().isEmpty() ? auto(recorded) : null;
        };
    }

    // ---------------------------------------------------------------- manual

    private static void take(Shot s) {
        manual = s;
        mode = Mode.MANUAL;
    }

    private static Shot shot(int kind, String camera, String player, int transition) {
        return new Shot(kind, camera, player, transition, ++serial, "", List.of());
    }

    /** A camera of the focus ("3") or any camera key ("studio:2"). True if there was one. */
    public static boolean camera(String ref) {
        CameraList focused = fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.focused();
        CameraSpot spot = ref.matches("\\d+") ? focused.get(Integer.parseInt(ref)) : fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.cameras().get(ref);
        if (spot == null) return false;
        take(shot(BroadcastNetwork.SHOT_CAMERA, spot.key(), "", BroadcastNetwork.CUT));
        return true;
    }

    /** The wide shot of the focus (else of the studio). */
    public static boolean wide() {
        CameraSpot w = wideSpot();
        if (w == null) return false;
        take(shot(BroadcastNetwork.SHOT_CAMERA, w.key(), "", BroadcastNetwork.CROSSFADE));
        return true;
    }

    /** Follow shot (or through the eyes, {@code pov}) on the next contestant ({@code step} 1 / -1), or on {@code name}. */
    public static String player(String name, int step, boolean pov) {
        List<String> names = contestants();
        if (names.isEmpty()) return null;
        String who = name;
        if (who == null) {
            int i = names.indexOf(lastPlayer);
            who = names.get(Math.floorMod(i < 0 ? (step > 0 ? 0 : -1) : i + step, names.size()));
        }
        lastPlayer = who;
        take(shot(pov ? BroadcastNetwork.SHOT_POV : BroadcastNetwork.SHOT_FOLLOW, "", who, BroadcastNetwork.CUT));
        return who;
    }

    /** The contestants of the recorded show (as in the state), else the players in sight. */
    public static List<String> contestants() {
        List<String> out = new ArrayList<>();
        for (Contestant c : fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.recorded().contestants()) out.add(c.name());
        return out;
    }

    private static CameraSpot wideSpot() {
        CameraList focused = fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.focused();
        CameraSpot w = focused.wide();
        if (w == null) w = fr.lordfinn.craftmaster.client.broadcast.ShowStateClient.cameras().group(CameraSpot.STUDIO).wide();
        if (w == null && !focused.isEmpty()) w = focused.all().getFirst();
        return w;
    }

    // ---------------------------------------------------------------- automatic

    private static Shot auto(ShowState recorded) {
        String player = fr.lordfinn.craftmaster.replay.ReplayAuto.player(ReplayClient.markers(), ReplayClient.nowMs(), names(recorded));
        String key;
        int kind;
        String camera = "";
        if (player != null) {
            key = "p:" + player;
            kind = BroadcastNetwork.SHOT_FOLLOW;
        } else {
            CameraSpot w = wideSpot();
            if (w == null) return null;
            key = "c:" + w.key();
            kind = BroadcastNetwork.SHOT_CAMERA;
            camera = w.key();
        }
        if (!key.equals(autoKey)) {
            autoKey = key;
            autoShot = shot(kind, camera, player == null ? "" : player, player == null ? BroadcastNetwork.CROSSFADE : BroadcastNetwork.CUT);
        }
        return autoShot;
    }

    private static List<String> names(ShowState s) {
        List<String> out = new ArrayList<>();
        for (Contestant c : s.contestants()) out.add(c.name());
        return out;
    }
}
