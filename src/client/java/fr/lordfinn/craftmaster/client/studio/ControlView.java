package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.camera.CameraNetwork;
import fr.lordfinn.craftmaster.client.broadcast.DirectorCamera;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.studio.SpotlightNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Taking control of a studio camera or a spotlight: the organiser's view moves to its lens and the mouse frames
 * it, the player standing still meanwhile. A click (or Sneak) keeps the new aim and sends it to the server, Escape
 * gives up (the pause screen opening ends it too). The player's own look is restored afterwards.
 */
public final class ControlView {

    private ControlView() {}

    private enum Kind { CAMERA, SPOT, STEP }

    private static Kind kind;
    private static String cameraKey, stepPath;
    private static int stepIndex;
    private static BlockPos spot;
    private static Vec3 eye;
    private static float fov;
    private static String label = "";
    private static float savedYaw, savedPitch;
    private static Vec3 savedPos;
    private static boolean attackWas, useWas, shiftWas;
    private static int startTick;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ControlView::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> kind = null);
        HudElementRegistry.addLast(CraftMaster.id("control_view"), ControlView::hud);
    }

    public static boolean active() {
        return kind != null;
    }

    /** The spotlight looked through right now (its head and beam are not drawn). */
    public static boolean controllingSpot(BlockPos pos) {
        return kind == Kind.SPOT && pos.equals(spot);
    }

    /** The camera looked through right now (its own prop is not drawn: the view is inside it). */
    public static boolean controlling(String key) {
        return kind == Kind.CAMERA && key.equals(cameraKey);
    }

    /** Looks through camera {@code key} (lens, aim, zoom) until validated. */
    public static void startCamera(String key, String name, Vec3 lens, float yaw, float pitch, float zoom) {
        begin(Kind.CAMERA, Component.translatable("hud.craftmaster.control.camera", name).getString(), lens, yaw, pitch, zoom);
        cameraKey = key;
    }

    /** Looks from a travelling's step (its place, aim and zoom) until validated: the step's new angle. */
    public static void startStep(String camera, String path, int index, Vec3 at, float yaw, float pitch, float zoom) {
        begin(Kind.STEP, Component.translatable("hud.craftmaster.control.step", index + 1, path).getString(), at, yaw, pitch, zoom);
        cameraKey = camera;
        stepPath = path;
        stepIndex = index;
    }

    /** Looks along the spotlight at {@code pos} (from just in front of its lens) until validated. */
    public static void startSpot(BlockPos pos, Vec3 pivot, float yaw, float pitch) {
        begin(Kind.SPOT, Component.translatable("hud.craftmaster.control.spot").getString(), pivot, yaw, pitch, 0);
        spot = pos;
    }

    private static void begin(Kind k, String text, Vec3 at, float yaw, float pitch, float zoom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.setScreen(null);
        kind = k;
        label = text;
        eye = at;
        fov = zoom;
        savedYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();
        savedPos = mc.player.position();
        look(mc, yaw, pitch);
        attackWas = mc.options.keyAttack.isDown();
        useWas = mc.options.keyUse.isDown();
        shiftWas = mc.options.keyShift.isDown();
        startTick = mc.player.tickCount;
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
    }

    /** The view while in control, else null (see CameraMixin). */
    public static DirectorCamera.Pose pose(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == null || mc.player == null) return null;
        float yaw = mc.player.getViewYRot(partialTick), pitch = mc.player.getViewXRot(partialTick);
        Vec3 at = eye;
        if (kind == Kind.SPOT) {
            // Just in front of the lens, along the beam: the spotlight's own head does not fill the view
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            at = eye.add(-Math.sin(y) * Math.cos(p) * 0.45, -Math.sin(p) * 0.45, Math.cos(y) * Math.cos(p) * 0.45);
        }
        return new DirectorCamera.Pose(at, yaw, pitch, fov);
    }

    private static void tick(Minecraft mc) {
        if (kind == null) return;
        if (mc.player == null) {
            kind = null;
            return;
        }
        // The player stands still meanwhile (the keys would walk them away)
        mc.player.setPos(savedPos);
        mc.player.setDeltaMovement(Vec3.ZERO);
        // The pause screen (Escape): given up; another screen: kept as it was
        if (mc.gui.screen() != null) {
            finish(mc, false);
            return;
        }
        boolean attack = mc.options.keyAttack.isDown(), use = mc.options.keyUse.isDown(), shift = mc.options.keyShift.isDown();
        boolean pressed = (attack && !attackWas) || (use && !useWas) || (shift && !shiftWas);
        attackWas = attack;
        useWas = use;
        shiftWas = shift;
        if (pressed && mc.player.tickCount - startTick > 4) finish(mc, true);
    }

    private static void finish(Minecraft mc, boolean keep) {
        Kind was = kind;
        kind = null;
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        look(mc, savedYaw, savedPitch);
        // The click that validated must not also hit or use something
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        if (!keep) return;
        JsonObject angle = new JsonObject();
        angle.addProperty("yaw", Math.round(yaw * 10) / 10f);
        angle.addProperty("pitch", Math.round(pitch * 10) / 10f);
        JsonObject o = new JsonObject();
        o.addProperty("what", "angle");
        o.add("value", angle);
        if (was == Kind.CAMERA) {
            o.addProperty("key", cameraKey);
            if (ClientPlayNetworking.canSend(CameraNetwork.Edit.TYPE)) ClientPlayNetworking.send(new CameraNetwork.Edit(o.toString()));
        } else if (was == Kind.STEP) {
            JsonObject e = new JsonObject();
            e.addProperty("camera", cameraKey);
            e.addProperty("path", stepPath);
            e.addProperty("action", "set");
            e.addProperty("index", stepIndex);
            e.addProperty("field", "angle");
            e.add("value", angle);
            e.addProperty("select", stepIndex);
            if (ClientPlayNetworking.canSend(fr.lordfinn.craftmaster.camera.TravellingNetwork.Edit.TYPE))
                ClientPlayNetworking.send(new fr.lordfinn.craftmaster.camera.TravellingNetwork.Edit(e.toString()));
        } else {
            o.addProperty("x", spot.getX());
            o.addProperty("y", spot.getY());
            o.addProperty("z", spot.getZ());
            if (ClientPlayNetworking.canSend(SpotlightNetwork.Edit.TYPE)) ClientPlayNetworking.send(new SpotlightNetwork.Edit(o.toString()));
        }
    }

    private static void hud(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (kind == null) return;
        Minecraft mc = Minecraft.getInstance();
        float yaw = mc.player == null ? 0 : mc.player.getYRot(), pitch = mc.player == null ? 0 : mc.player.getXRot();
        String line1 = label + "  ·  " + Math.round(net.minecraft.util.Mth.wrapDegrees(yaw)) + "° / " + Math.round(pitch) + "°";
        String line2 = Component.translatable("hud.craftmaster.control.hint").getString();
        int w = Math.max(mc.font.width(line1), mc.font.width(line2)) + 16;
        int x = (g.guiWidth() - w) / 2, y = 8;
        fr.lordfinn.craftmaster.client.ui.Ui.panel(g, mc.font, x, y, w, 30, null);
        g.centeredText(mc.font, line1, g.guiWidth() / 2, y + 6, Theme.GOLD);
        g.centeredText(mc.font, line2, g.guiWidth() / 2, y + 18, Theme.TEXT_DIM);
        // A small cross in the middle: what the lens is pointed at
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        g.fill(cx - 4, cy, cx + 5, cy + 1, 0xC0FFFFFF);
        g.fill(cx, cy - 4, cx + 1, cy + 5, 0xC0FFFFFF);
    }
}
