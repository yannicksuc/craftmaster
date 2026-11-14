package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.camera.CameraEntity;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.PathMarkerItem;
import fr.lordfinn.craftmaster.camera.TravellingNetwork;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.broadcast.ShowStateClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The travellings' steps in the world, for the organisers: a point at each step of the travellings of the cameras
 * showing their ghost (or of the travelling whose marker is held). The point under the crosshair becomes a ghost
 * camera (where the step stands, looking its way), and a right-click on it opens the editor on that step. A step
 * just added shows its ghost camera for a few seconds.
 */
public final class TravellingMarkers {

    private TravellingMarkers() {}

    /** A step under the crosshair: its travelling, its index, its camera. */
    public record Hover(String path, int index, String camera) {}

    private static Hover hover;
    private static boolean useWas;
    /** Step counts seen per travelling, to notice a step just added. */
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    /** "path#index" → until when (ms) its ghost camera shows. */
    private static final Map<String, Long> FRESH = new HashMap<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TravellingMarkers::tick);
    }

    public static Hover hover() {
        return hover;
    }

    /** Is a step's ghost camera shown (hovered, selected in the editor, or just added)? */
    public static boolean showsGhost(String path, int index) {
        if (hover != null && hover.path.equals(path) && hover.index == index) return true;
        TravellingEdit.StepRef h = TravellingEdit.hovered();
        if (h != null && h.is(path, index)) return true;
        if (TravellingScreen.editing(path, index)) return true;
        Long until = FRESH.get(path + "#" + index);
        return until != null && until > System.currentTimeMillis();
    }

    /** The travellings whose steps are shown: of cameras with their ghost on, and the marker held. */
    public static Set<String> shownPaths(Minecraft mc) {
        Set<String> out = new HashSet<>();
        if (mc.player == null || mc.level == null || Broadcast.active() || !fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) return out;
        Set<String> ghostCameras = new HashSet<>();
        for (var e : mc.level.entitiesForRendering()) if (e instanceof CameraEntity c && c.showsGhost()) ghostCameras.add(c.cameraKey());
        for (CameraPath p : ShowStateClient.allPaths()) if (!p.camera().isEmpty() && ghostCameras.contains(p.camera())) out.add(p.name());
        String held = PathMarkerItem.path(mc.player.getMainHandItem());
        if (held != null && !held.isEmpty()) out.add(CameraPath.normalizeName(held));
        String edited = TravellingScreen.editedPath();
        if (edited != null) out.add(edited);
        return out;
    }

    /** The travellings edited in the world: of each camera showing its ghost, the one picked (TravellingEdit), and the marker held. */
    public static Set<String> editedPaths(Minecraft mc) {
        Set<String> out = new HashSet<>();
        if (mc.player == null || mc.level == null || Broadcast.active() || !fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) return out;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof CameraEntity c && c.showsGhost()) {
                String p = TravellingEdit.edited(c.cameraKey());
                if (p != null) out.add(p);
            }
        String held = PathMarkerItem.path(mc.player.getMainHandItem());
        if (held != null && !held.isEmpty() && ShowStateClient.path(CameraPath.normalizeName(held)) != null) out.add(CameraPath.normalizeName(held));
        return out;
    }

    private static void tick(Minecraft mc) {
        hover = null;
        if (mc.player == null || mc.level == null) return;
        Set<String> shown = shownPaths(mc);
        // Steps just added: their ghost shows for 3 seconds
        for (String name : shown) {
            CameraPath p = ShowStateClient.path(name);
            if (p == null) continue;
            Integer before = COUNTS.put(name, p.steps().size());
            if (before != null && p.steps().size() > before) FRESH.put(name + "#" + (p.steps().size() - 1), System.currentTimeMillis() + 3000);
        }
        // With Axiom on, its gizmos take the clicks (client/axiom/TravellingGizmos)
        if (mc.gui.screen() != null || shown.isEmpty() || TravellingEdit.gizmosActive()) {
            useWas = mc.options.keyUse.isDown();
            return;
        }
        // The step under the crosshair (within 48 blocks, a point of 0.35 block)
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1);
        double best = 48;
        for (String name : shown) {
            CameraPath p = ShowStateClient.path(name);
            if (p == null) continue;
            for (int i = 0; i < p.steps().size(); i++) {
                CameraPath.Step s = p.steps().get(i);
                Vec3 to = new Vec3(s.x() - eye.x, s.y() - eye.y, s.z() - eye.z);
                double along = to.dot(look);
                if (along <= 0.3 || along > best) continue;
                double off = to.subtract(look.scale(along)).length();
                if (off <= 0.35 + along * 0.01) {
                    best = along;
                    hover = new Hover(p.name(), i, p.camera());
                }
            }
        }
        // A right-click on it: the editor, on that step
        boolean use = mc.options.keyUse.isDown();
        boolean pressed = use && !useWas;
        useWas = use;
        if (pressed && hover != null && !hover.camera.isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("camera", hover.camera);
            o.addProperty("path", hover.path);
            o.addProperty("action", "open");
            o.addProperty("select", hover.index);
            if (ClientPlayNetworking.canSend(TravellingNetwork.Edit.TYPE)) ClientPlayNetworking.send(new TravellingNetwork.Edit(o.toString()));
        }
    }
}
