package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.TravellingNetwork;
import fr.lordfinn.craftmaster.client.broadcast.ShowStateClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an organiser edits of the travellings in the world, shared by the camera menu, the travelling editor, the
 * renderer and the world tools (client/axiom): the colour of each travelling (always the same order for a camera),
 * the travelling edited per camera (its first one unless picked in the camera menu), the step selected, the pose a
 * step has while being dragged, and the ghost of a step about to be added.
 */
public final class TravellingEdit {

    private TravellingEdit() {}

    /** Travelling colours in order (ARGB): orange, turquoise, violet, pink, blue, green, yellow, red. */
    private static final int[] PALETTE = {0xFFFFA63D, 0xFF2EC4A0, 0xFFA88BFF, 0xFFFF6FA8, 0xFF4FA3FF, 0xFF8BD450, 0xFFFFD84A, 0xFFFF6B5A};

    /** Camera key → the travelling edited in the world. */
    private static final Map<String, String> EDITED = new HashMap<>();

    /** A step of a travelling. */
    public record StepRef(String path, int index) {
        public boolean is(String p, int i) {
            return path.equals(p) && index == i;
        }
    }

    /** The ghost of a step about to be added (a "+" being dragged, a point of the path under the crosshair). */
    public record Preview(String path, CameraPath.Pose pose) {}

    /** A "+" drawn in the world (TravellingGizmos): of travelling {@code path}, hovered or held. */
    public record HandleDot(String path, double x, double y, double z, boolean lit) {}

    private static StepRef selected, hovered;
    private static List<HandleDot> handles = List.of();
    private static Preview preview;
    /** Steps being dragged / just dropped: their pose until the server's copy catches up. */
    private static final Map<StepRef, CameraPath.Pose> OVERRIDES = new HashMap<>();
    private static final Map<StepRef, Long> OVERRIDE_UNTIL = new HashMap<>();
    /** Last frame the world tools (Axiom's gizmos) drew the steps: the renderer then leaves the points to them. */
    private static long gizmoFrame;

    // ---------------------------------------------------------------- colours

    public static int colour(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    /** A travelling's colour: its rank among its camera's travellings. */
    public static int colourOf(CameraPath p) {
        if (p == null) return PALETTE[0];
        List<CameraPath> mine = CameraPath.ofCamera(ShowStateClient.allPaths(), p.camera());
        return colour(Math.max(0, mine.indexOf(p)));
    }

    public static int colourOf(String path) {
        return colourOf(ShowStateClient.path(path));
    }

    // ---------------------------------------------------------------- edited travelling

    /** The travelling of camera {@code key} edited in the world: the one picked, else its first (null: none). */
    public static String edited(String key) {
        List<CameraPath> mine = CameraPath.ofCamera(ShowStateClient.allPaths(), key);
        if (mine.isEmpty()) return null;
        String picked = EDITED.get(key);
        if (picked != null) for (CameraPath p : mine) if (p.name().equals(picked)) return picked;
        return mine.getFirst().name();
    }

    public static void setEdited(String key, String path) {
        if (key == null || path == null) return;
        if (!path.equals(EDITED.put(key, path)) && selected != null && !selected.path.equals(path)) selected = null;
    }

    // ---------------------------------------------------------------- selection

    public static StepRef selected() {
        return selected;
    }

    public static boolean isSelected(String path, int index) {
        return selected != null && selected.is(path, index) || TravellingScreen.editing(path, index);
    }

    public static void select(String path, int index) {
        selected = path == null ? null : new StepRef(path, index);
    }

    public static StepRef hovered() {
        return hovered;
    }

    public static void hover(StepRef ref) {
        hovered = ref;
    }

    public static List<HandleDot> handles() {
        return gizmosActive() ? handles : List.of();
    }

    public static void handles(List<HandleDot> h) {
        handles = h;
    }

    public static Preview preview() {
        return preview;
    }

    public static void preview(Preview p) {
        preview = p;
    }

    // ---------------------------------------------------------------- poses being edited

    /** The step's pose as drawn: dragged / just dropped, else its own. */
    public static CameraPath.Pose pose(CameraPath p, int index) {
        StepRef ref = new StepRef(p.name(), index);
        CameraPath.Pose o = OVERRIDES.get(ref);
        if (o != null) {
            Long until = OVERRIDE_UNTIL.get(ref);
            CameraPath.Step s = index < p.steps().size() ? p.steps().get(index) : null;
            boolean caughtUp = s != null && Math.abs(s.x() - o.x()) < 1e-3 && Math.abs(s.y() - o.y()) < 1e-3 && Math.abs(s.z() - o.z()) < 1e-3
                    && Math.abs(s.yaw() - o.yaw()) < 0.05 && Math.abs(s.pitch() - o.pitch()) < 0.05;
            if (until == null || (!caughtUp && until > System.currentTimeMillis())) return o;
            OVERRIDES.remove(ref);
            OVERRIDE_UNTIL.remove(ref);
        }
        if (index < 0 || index >= p.steps().size()) return null;
        CameraPath.Step s = p.steps().get(index);
        return new CameraPath.Pose(s.x(), s.y(), s.z(), s.yaw(), s.pitch(), s.fov());
    }

    /** While dragging: the step is drawn there. */
    public static void drag(String path, int index, CameraPath.Pose pose) {
        StepRef ref = new StepRef(path, index);
        OVERRIDES.put(ref, pose);
        OVERRIDE_UNTIL.remove(ref);
    }

    /** Dropped: kept there until the server's copy has it (2 s at most). */
    public static void drop(String path, int index) {
        StepRef ref = new StepRef(path, index);
        if (OVERRIDES.containsKey(ref)) OVERRIDE_UNTIL.put(ref, System.currentTimeMillis() + 2000);
    }

    public static void cancelDrag(String path, int index) {
        StepRef ref = new StepRef(path, index);
        OVERRIDES.remove(ref);
        OVERRIDE_UNTIL.remove(ref);
    }

    // ---------------------------------------------------------------- the path as drawn

    /** The travelling with the steps being dragged where they are drawn. */
    public static CameraPath asDrawn(CameraPath p) {
        List<CameraPath.Step> steps = null;
        for (int i = 0; i < p.steps().size(); i++) {
            if (!OVERRIDES.containsKey(new StepRef(p.name(), i))) continue;
            CameraPath.Pose o = pose(p, i);
            if (o == null) continue;
            if (steps == null) steps = new java.util.ArrayList<>(p.steps());
            steps.set(i, steps.get(i).withPlace(o.x(), o.y(), o.z(), o.yaw(), o.pitch()));
        }
        return steps == null ? p : p.withSteps(steps);
    }

    private static final Map<String, CameraPath> TRAIL_OF = new HashMap<>();
    private static final Map<String, List<float[]>> TRAIL = new HashMap<>();

    /** Points along a path (x, y, z, time), kept while it does not change. */
    public static List<float[]> trail(CameraPath p) {
        if (!p.equals(TRAIL_OF.get(p.name()))) {
            TRAIL_OF.put(p.name(), p);
            TRAIL.put(p.name(), trailPoints(p));
        }
        return TRAIL.get(p.name());
    }

    /** Points along a path, at least 0.3 block apart: x, y, z, time (s). */
    private static List<float[]> trailPoints(CameraPath p) {
        List<float[]> out = new java.util.ArrayList<>();
        double total = p.duration();
        if (p.steps().size() < 2 || total <= 0) return out;
        double length = 0;
        for (int i = 1; i < p.steps().size(); i++) {
            var a = p.steps().get(i - 1);
            var b = p.steps().get(i);
            length += Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()) + (a.z() - b.z()) * (a.z() - b.z()));
        }
        int n = (int) Math.max(24, Math.min(1800, length / 0.13));
        double lx = Double.NaN, ly = 0, lz = 0;
        for (int k = 1; k < n; k++) {
            double t = total * k / n;
            var v = p.sample(t);
            // Not on top of each other while holding on a step, nor closer than 0.3 block
            if (!Double.isNaN(lx) && (v.x() - lx) * (v.x() - lx) + (v.y() - ly) * (v.y() - ly) + (v.z() - lz) * (v.z() - lz) < 0.09) continue;
            out.add(new float[]{(float) v.x(), (float) v.y(), (float) v.z(), (float) t});
            lx = v.x();
            ly = v.y();
            lz = v.z();
        }
        return out;
    }


    // ---------------------------------------------------------------- world tools

    public static void gizmosDrawn() {
        gizmoFrame = System.currentTimeMillis();
    }

    /** Are the steps edited with the world tools right now (Axiom on)? */
    public static boolean gizmosActive() {
        return System.currentTimeMillis() - gizmoFrame < 250;
    }

    /** Sends an edit of travelling {@code path} of camera {@code key} (the server checks the permission). */
    public static void send(String key, String path, String action, JsonObject extra) {
        JsonObject o = extra == null ? new JsonObject() : extra;
        o.addProperty("camera", key);
        o.addProperty("path", path);
        o.addProperty("action", action);
        if (ClientPlayNetworking.canSend(TravellingNetwork.Edit.TYPE)) ClientPlayNetworking.send(new TravellingNetwork.Edit(o.toString()));
    }

    /** A step's pose as the server takes it ("pose" / "insertAt"). */
    public static JsonObject poseJson(int index, CameraPath.Pose p) {
        JsonObject o = new JsonObject();
        o.addProperty("index", index);
        o.addProperty("x", Math.round(p.x() * 1000) / 1000.0);
        o.addProperty("y", Math.round(p.y() * 1000) / 1000.0);
        o.addProperty("z", Math.round(p.z() * 1000) / 1000.0);
        o.addProperty("yaw", Math.round(p.yaw() * 10) / 10.0);
        o.addProperty("pitch", Math.round(p.pitch() * 10) / 10.0);
        o.addProperty("quiet", true);
        return o;
    }
}
