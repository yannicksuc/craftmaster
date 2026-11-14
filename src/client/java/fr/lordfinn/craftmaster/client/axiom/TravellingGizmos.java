package fr.lordfinn.craftmaster.client.axiom;

import com.google.gson.JsonObject;
import com.moulberry.axiom.AxiomClient;
import com.moulberry.axiom.UserAction;
import com.moulberry.axiom.gizmo.Gizmo;
import com.moulberry.axiom.hooks.ScreenRenderHook;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiom.utils.InputHelper;
import fr.lordfinn.craftmaster.camera.CameraEntity;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.client.broadcast.DirectorCamera;
import fr.lordfinn.craftmaster.client.broadcast.ShowStateClient;
import fr.lordfinn.craftmaster.client.studio.TravellingEdit;
import fr.lordfinn.craftmaster.client.studio.TravellingMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The travellings edited in the world with Axiom's own gizmos, when Axiom is on (its editor mode): the same tool,
 * the same feel as moving a display entity. Loaded only with Axiom (mixins in craftmaster.axiom.mixins.json).
 * <ul>
 *   <li>each step of the edited travelling (see {@link TravellingEdit#edited}) is a cube in the travelling's colour;
 *       hovered, it shows its ghost camera; a click selects it;</li>
 *   <li>the selected step gets the full gizmo: arrows / planes to move it, rings to turn it (the horizontal ring:
 *       left / right, the side ring: up / down);</li>
 *   <li>a "+" cube above each step: drag it toward the next step for a new step after it, toward the previous one
 *       for a step before it; the "+" above the camera (when the travelling starts elsewhere) adds one at the end;</li>
 *   <li>a click on the dotted path adds a step there (the path's own place and aim, its travel time shared);</li>
 *   <li>right click on a step: the travelling editor on it; Delete: remove the selected step; Esc: deselect.</li>
 * </ul>
 */
public final class TravellingGizmos {

    private static final TravellingGizmos INSTANCE = new TravellingGizmos();
    /** The "+" above a step or the camera (blocks). */
    private static final double HANDLE_UP = 0.75;
    /** A "+" moved less than this is a click, not a drag (blocks). */
    private static final double MIN_DRAG = 0.35;

    /** Dev client (DevInput): the left button held, in place of the real one (null: the real one). */
    public static Boolean devLeft;

    /** Dev client (DevInput): runs one of Axiom's actions as if the player did it. */
    public static void devAction(String name) {
        UserAction.valueOf(name).call(null);
    }

    public static TravellingGizmos get() {
        return INSTANCE;
    }

    private TravellingGizmos() {}

    /** A "+" handle: of step {@code index} of {@code path} (-1: the camera's, adds at the end). */
    private record Handle(String path, int index, Vec3 home) {}

    /** A point of a path under the crosshair. */
    private record TrailHit(CameraPath path, double t, CameraPath.Pose pose) {}

    private Map<String, Handle> handles = new HashMap<>();
    /** The "+" being dragged, the plane it moves on (through its place, facing the view at the click), where it is. */
    private String dragging;
    private Vec3 dragPoint, dragNormal, dragAt;
    private Gizmo selection;
    private TravellingEdit.StepRef selectionOf;
    private boolean selectionGrabbed;
    private float startYaw, startPitch;
    private Gizmo.GizmoRotation lastRotation;

    private TravellingEdit.StepRef hoverCube;
    private String hoverHandle;
    private TrailHit hoverTrail;
    private boolean active;

    // ---------------------------------------------------------------- render (every frame)

    public void render(AxiomWorldRenderContext rc) {
        hoverCube = null;
        hoverHandle = null;
        hoverTrail = null;
        TravellingEdit.hover(null);
        Minecraft mc = Minecraft.getInstance();
        Set<String> edited = mc.player == null || !AxiomClient.isAxiomActive() ? Set.of() : TravellingMarkers.editedPaths(mc);
        active = !edited.isEmpty();
        if (!active) {
            clear();
            return;
        }
        TravellingEdit.gizmosDrawn();
        Vec3 look = Tool.getLookDirection();
        boolean leftDown = devLeft != null ? devLeft : Tool.isMouseDown(0);
        boolean ctrl = InputHelper.isCtrlOrCmdDownRaw(mc.getWindow().handle());
        boolean visible = !mc.gui.hud.isHidden();
        TravellingEdit.Preview preview = null;

        TravellingEdit.StepRef sel = TravellingEdit.selected();
        if (sel != null && (!edited.contains(sel.path()) || ShowStateClient.path(sel.path()) == null
                || sel.index() >= ShowStateClient.path(sel.path()).steps().size())) {
            TravellingEdit.select(null, 0);
            sel = null;
        }

        // The selected step: the full gizmo
        if (sel != null) {
            CameraPath p = ShowStateClient.path(sel.path());
            CameraPath.Pose v = TravellingEdit.pose(p, sel.index());
            Vec3 at = new Vec3(v.x(), v.y(), v.z());
            if (selection == null || !sel.equals(selectionOf)) {
                selection = selectionGizmo(at, TravellingEdit.colourOf(p) & 0xFFFFFF);
                selectionOf = sel;
                selectionGrabbed = false;
            }
            if (!selection.isGrabbed()) {
                selection.moveToVec(at);
                selection.localRotation = new Quaternionf().rotationY((float) Math.toRadians(-v.yaw()));
            }
            selection.setAxisDirections(rc.x() > at.x, rc.y() > at.y, rc.z() > at.z);
            selection.update(rc.nanos(), look, leftDown, ctrl, true);
            if (visible) selection.render(rc, ctrl);
            followSelection(p, sel.index(), v);
        } else {
            selection = null;
            selectionOf = null;
            selectionGrabbed = false;
        }

        // The steps (points drawn by CameraRenderer) and their "+" (Axiom draws a gizmo's centre cube only with its
        // arrows: these are picked here, by the crosshair)
        Map<String, Handle> nextHandles = new HashMap<>();
        java.util.List<TravellingEdit.HandleDot> dots = new java.util.ArrayList<>();
        Vec3 eye = mc.player.getEyePosition(rc.partialTick());
        boolean selectionHovered = selection != null && selection.isHovered();
        boolean busy = selectionHovered || selectionGrabbed || dragging != null;
        double bestStep = 64, bestHandle = 64;
        for (String name : edited) {
            CameraPath p = ShowStateClient.path(name);
            if (p == null) continue;
            for (int i = 0; i < p.steps().size(); i++) {
                CameraPath.Pose v = TravellingEdit.pose(p, i);
                Vec3 at = new Vec3(v.x(), v.y(), v.z());
                nextHandles.put(name + "#" + i, new Handle(name, i, at.add(0, HANDLE_UP, 0)));
                if (busy || look == null || sel != null && sel.is(name, i)) continue;
                double along = pick(eye, look, at, 0.22);
                if (along < bestStep) {
                    bestStep = along;
                    hoverCube = new TravellingEdit.StepRef(name, i);
                }
            }
            // The camera's own "+" (adds at the end), unless the travelling starts right at the camera: its first
            // step's "+" is then the camera's
            CameraEntity cam = camera(mc, p.camera());
            if (cam != null && !p.isEmpty()) {
                Vec3 lens = cam.lens();
                CameraPath.Step first = p.steps().getFirst();
                if (lens.distanceToSqr(first.x(), first.y(), first.z()) > 0.25)
                    nextHandles.put(name + "#cam", new Handle(name, -1, lens.add(0, HANDLE_UP + 0.25, 0)));
            }
        }
        handles = nextHandles;
        if (!busy && look != null)
            for (Map.Entry<String, Handle> e : handles.entrySet()) {
                double along = pick(eye, look, e.getValue().home(), 0.16);
                if (along < bestHandle) {
                    bestHandle = along;
                    hoverHandle = e.getKey();
                }
            }
        // A "+" in front of a step wins (it is the smaller target)
        if (hoverHandle != null && bestHandle <= bestStep + 0.5) hoverCube = null;
        else hoverHandle = null;
        if (hoverCube != null) TravellingEdit.hover(hoverCube);

        // A "+" being dragged (held since its click): it follows the crosshair on the plane facing the view; the new
        // step's ghost shows there; let go, the step is added
        if (dragging != null) {
            Handle h = handles.get(dragging);
            CameraPath p = h == null ? null : ShowStateClient.path(h.path());
            if (p == null) dragging = null;
            else {
                double denom = look == null ? 0 : look.dot(dragNormal);
                if (denom > 1e-3) dragAt = eye.add(look.scale(dragPoint.subtract(eye).dot(dragNormal) / denom));
                if (leftDown) {
                    CameraPath.Pose aim = aimFor(p, h.index());
                    preview = new TravellingEdit.Preview(h.path(), new CameraPath.Pose(dragAt.x, dragAt.y, dragAt.z, aim.yaw(), aim.pitch(), aim.fov()));
                } else {
                    if (dragAt.distanceTo(h.home()) >= MIN_DRAG) dropHandle(h, dragAt);
                    dragging = null;
                }
            }
        }
        for (Map.Entry<String, Handle> e : handles.entrySet()) {
            Handle h = e.getValue();
            boolean held = e.getKey().equals(dragging);
            Vec3 at = held ? dragAt : h.home();
            dots.add(new TravellingEdit.HandleDot(h.path(), at.x, at.y, at.z, held || e.getKey().equals(hoverHandle)));
        }
        TravellingEdit.handles(dots);

        // The dotted path under the crosshair (nothing else hovered or held): the step a click would add
        if (preview == null && hoverHandle == null && hoverCube == null && !busy && look != null) {
            hoverTrail = trailHit(edited, eye, look);
            if (hoverTrail != null) preview = new TravellingEdit.Preview(hoverTrail.path.name(), hoverTrail.pose);
        }
        TravellingEdit.preview(preview);
    }

    /** The selected step follows its gizmo: moved / turned live, sent when let go. */
    private void followSelection(CameraPath p, int index, CameraPath.Pose v) {
        boolean grabbed = selection.isGrabbed();
        if (grabbed && !selectionGrabbed) {
            startYaw = v.yaw();
            startPitch = v.pitch();
            lastRotation = null;
        }
        if (grabbed) {
            Gizmo.GizmoRotation r = selection.peekGizmoRotation();
            if (r != null) lastRotation = r;
            CameraPath.Pose now = turned(selection.getTargetVec(), r, v.fov());
            TravellingEdit.drag(p.name(), index, now);
            feedback(index, p.steps().size(), now);
        } else if (selectionGrabbed) {
            Gizmo.GizmoRotation r = selection.popRotation();
            CameraPath.Pose now = turned(selection.getTargetVec(), r != null ? r : lastRotation, v.fov());
            TravellingEdit.drag(p.name(), index, now);
            TravellingEdit.drop(p.name(), index);
            TravellingEdit.send(p.camera(), p.name(), "pose", TravellingEdit.poseJson(index, now));
            feedback(index, p.steps().size(), now);
            lastRotation = null;
        }
        selectionGrabbed = grabbed;
    }

    /** The pose at {@code at}, turned from the angle at the grab by the gizmo's ring (none: unchanged). */
    private CameraPath.Pose turned(Vec3 at, Gizmo.GizmoRotation r, float fov) {
        float yaw = startYaw, pitch = startPitch;
        if (r != null) {
            float deg = (float) Math.toDegrees(r.radians());
            if (r.axis() == Direction.Axis.Y) yaw = startYaw - deg;
            else if (r.axis() == Direction.Axis.X) pitch = Math.max(-90, Math.min(90, startPitch + deg));
        }
        return new CameraPath.Pose(at.x, at.y, at.z, yaw, pitch, fov);
    }

    /**
     * A "+" dropped at {@code to}: from the camera, a step at the end; from step i, after it when moved toward the
     * next step (or away from the previous one), before it otherwise.
     */
    private void dropHandle(Handle h, Vec3 to) {
        CameraPath p = ShowStateClient.path(h.path());
        if (p == null) return;
        int n = p.steps().size();
        int at;
        if (h.index() < 0) at = n;
        else {
            int i = h.index();
            Vec3 here = stepVec(p, i);
            Vec3 axis = i + 1 < n && i > 0 ? stepVec(p, i + 1).subtract(stepVec(p, i - 1))
                    : i + 1 < n ? stepVec(p, i + 1).subtract(here)
                    : i > 0 ? here.subtract(stepVec(p, i - 1)) : null;
            boolean after = axis == null || axis.lengthSqr() < 1e-6 || to.subtract(here).dot(axis) >= 0;
            at = after ? i + 1 : i;
        }
        CameraPath.Pose aim = aimFor(p, h.index());
        CameraPath.Pose pose = new CameraPath.Pose(to.x, to.y, to.z, aim.yaw(), aim.pitch(), aim.fov());
        TravellingEdit.send(p.camera(), p.name(), "insertAt", TravellingEdit.poseJson(at, pose));
        TravellingEdit.select(p.name(), at);
        TravellingEdit.drag(p.name(), at, pose);
        TravellingEdit.drop(p.name(), at);
    }

    /** The angle a new step takes from a "+": its step's, or the last step's for the camera's. */
    private static CameraPath.Pose aimFor(CameraPath p, int index) {
        int i = index < 0 ? p.steps().size() - 1 : Math.min(index, p.steps().size() - 1);
        CameraPath.Pose v = TravellingEdit.pose(p, Math.max(0, i));
        return v == null ? new CameraPath.Pose(0, 0, 0, 0, 0, 0) : v;
    }

    private static Vec3 stepVec(CameraPath p, int i) {
        CameraPath.Pose v = TravellingEdit.pose(p, i);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** The point of an edited path closest to the crosshair, within a small distance of it. */
    private static TrailHit trailHit(Set<String> edited, Vec3 eye, Vec3 look) {
        TrailHit best = null;
        double bestAlong = 64;
        for (String name : edited) {
            CameraPath p = ShowStateClient.path(name);
            if (p == null || p.steps().size() < 2) continue;
            for (float[] pt : TravellingEdit.trail(TravellingEdit.asDrawn(p))) {
                Vec3 to = new Vec3(pt[0] - eye.x, pt[1] - eye.y, pt[2] - eye.z);
                double along = to.dot(look);
                if (along < 0.5 || along > bestAlong) continue;
                double off = to.subtract(look.scale(along)).length();
                if (off > 0.2 + along * 0.006) continue;
                bestAlong = along;
                best = new TrailHit(p, pt[3], null);
            }
        }
        if (best == null) return null;
        CameraPath drawn = TravellingEdit.asDrawn(best.path);
        CameraPath.Pose pose = drawn.sample(best.t, DirectorCamera::playerPosition);
        return new TrailHit(best.path, best.t, pose);
    }

    /** A click on the path: a step there, between the two steps around it, sharing their travel time. */
    private void insertOnTrail(TrailHit hit) {
        CameraPath p = TravellingEdit.asDrawn(hit.path);
        double[] w = p.where(hit.t);
        if (w[1] < 0) return;
        int at = (int) w[0] + 1;
        JsonObject o = TravellingEdit.poseJson(at, hit.pose);
        o.addProperty("split", Math.max(0.05, Math.min(0.95, w[1])));
        if (hit.pose.fov() > 0) o.addProperty("fov", hit.pose.fov());
        TravellingEdit.send(p.camera(), p.name(), "insertAt", o);
        TravellingEdit.select(p.name(), at);
        TravellingEdit.drag(p.name(), at, hit.pose);
        TravellingEdit.drop(p.name(), at);
    }

    // ---------------------------------------------------------------- Axiom's clicks and keys

    /** Asked before Axiom's own handlers (UserActionMixin). */
    public UserAction.ActionResult callAction(UserAction action, Object argument) {
        if (!active) return UserAction.ActionResult.NOT_HANDLED;
        TravellingEdit.StepRef sel = TravellingEdit.selected();
        switch (action) {
            case LEFT_MOUSE -> {
                if (selection != null && selection.isHovered()) {
                    selection.leftClick();
                    return UserAction.ActionResult.USED_STOP;
                }
                if (hoverHandle != null) {
                    Handle h = handles.get(hoverHandle);
                    Vec3 look = Tool.getLookDirection();
                    if (h != null && look != null) {
                        dragging = hoverHandle;
                        dragPoint = h.home();
                        dragNormal = look;
                        dragAt = h.home();
                    }
                    return UserAction.ActionResult.USED_STOP;
                }
                if (hoverCube != null) {
                    TravellingEdit.select(hoverCube.path(), hoverCube.index());
                    CameraPath p = ShowStateClient.path(hoverCube.path());
                    if (p != null) feedback(hoverCube.index(), p.steps().size(), TravellingEdit.pose(p, hoverCube.index()));
                    return UserAction.ActionResult.USED_STOP;
                }
                if (hoverTrail != null) {
                    insertOnTrail(hoverTrail);
                    return UserAction.ActionResult.USED_STOP;
                }
                if (sel != null) {
                    // Deselect, and do not break the block behind
                    TravellingEdit.select(null, 0);
                    return UserAction.ActionResult.USED_STOP;
                }
            }
            case RIGHT_MOUSE -> {
                TravellingEdit.StepRef on = hoverCube != null ? hoverCube : selection != null && selection.isHovered() ? sel : null;
                if (on != null) {
                    CameraPath p = ShowStateClient.path(on.path());
                    // A step right at the camera: the right click stays the camera's (its menu)
                    CameraEntity cam = p == null ? null : camera(Minecraft.getInstance(), p.camera());
                    CameraPath.Pose v = p == null ? null : TravellingEdit.pose(p, on.index());
                    if (cam != null && v != null && cam.lens().distanceToSqr(v.x(), v.y(), v.z()) < 0.09) return UserAction.ActionResult.NOT_HANDLED;
                    if (p != null) {
                        JsonObject o = new JsonObject();
                        o.addProperty("select", on.index());
                        TravellingEdit.send(p.camera(), p.name(), "open", o);
                    }
                    return UserAction.ActionResult.USED_STOP;
                }
            }
            case DELETE -> {
                if (sel != null) {
                    CameraPath p = ShowStateClient.path(sel.path());
                    if (p != null && p.steps().size() > 1) {
                        JsonObject o = new JsonObject();
                        o.addProperty("index", sel.index());
                        o.addProperty("quiet", true);
                        TravellingEdit.send(p.camera(), p.name(), "remove", o);
                    }
                    TravellingEdit.select(null, 0);
                    return UserAction.ActionResult.USED_STOP;
                }
            }
            case ESCAPE -> {
                if (sel != null) {
                    TravellingEdit.select(null, 0);
                    return UserAction.ActionResult.USED_STOP;
                }
            }
            default -> {}
        }
        return UserAction.ActionResult.NOT_HANDLED;
    }

    // ---------------------------------------------------------------- helpers

    private static void feedback(int index, int count, CameraPath.Pose v) {
        if (v == null) return;
        ScreenRenderHook.setOverlayText(Component.translatable("overlay.craftmaster.travelling.step", index + 1, count,
                String.format(Locale.ROOT, "%.2f %.2f %.2f", v.x(), v.y(), v.z()), Math.round(v.yaw()), Math.round(v.pitch())));
    }

    private static CameraEntity camera(Minecraft mc, String key) {
        if (mc.level == null || key == null || key.isEmpty()) return null;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof CameraEntity c && c.cameraKey().equals(key)) return c;
        return null;
    }

    /** How far along the view a point is, if the crosshair passes within {@code radius} of it (grows with distance). */
    private static double pick(Vec3 eye, Vec3 look, Vec3 at, double radius) {
        Vec3 to = at.subtract(eye);
        double along = to.dot(look);
        if (along < 0.3 || along > 64) return Double.MAX_VALUE;
        return to.subtract(look.scale(along)).length() <= radius + along * 0.012 ? along : Double.MAX_VALUE;
    }

    /** The selected step: arrows and planes to move it, rings to turn it (1° / 1/16 block steps). */
    private static Gizmo selectionGizmo(Vec3 at, int colour) {
        Gizmo g = new Gizmo(at);
        g.enableAxes = true;
        g.enableRotation = true;
        g.yAxisRotationOnly = false;
        g.rotationSnapRadians = (float) Math.toRadians(1.0);
        g.minVisualScale = 2.0f;
        g.translationSnapping = 16;
        g.centerColour = colour;
        return g;
    }

    private void clear() {
        handles.clear();
        dragging = null;
        TravellingEdit.handles(java.util.List.of());
        selection = null;
        selectionOf = null;
        selectionGrabbed = false;
        TravellingEdit.preview(null);
    }
}
