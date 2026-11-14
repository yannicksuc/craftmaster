package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import fr.lordfinn.craftmaster.portrait.PoseLibrary;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.PortraitNetwork;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Client side of the portraits: renderer, pose library received from the server, screens, editor preview. */
public final class PortraitClient {

    private PortraitClient() {}

    /** Poses being edited, drawn on their portrait instead of its own pose (live preview of the editor). */
    private static final Map<Integer, Pose> PREVIEWS = new HashMap<>();

    public static void init() {
        EntityRendererRegistry.register(ModContent.PORTRAIT, PortraitRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(PortraitNetwork.Poses.TYPE, (payload, ctx) -> {
            try {
                PoseLibraries.setClient(PoseLibrary.fromJson(payload.json()));
            } catch (Exception e) {
                CraftMaster.LOG.warn("[Portraits] Poses reçues illisibles", e);
            }
            if (ctx.client().gui.screen() instanceof PoseEditorScreen editor) editor.libraryChanged();
            if (ctx.client().gui.screen() instanceof PortraitScreen screen) screen.libraryChanged();
        });
        ClientPlayNetworking.registerGlobalReceiver(PortraitNetwork.Open.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.level == null || !(mc.level.getEntity(payload.entityId()) instanceof PortraitEntity portrait)) return;
            mc.gui.setScreen(payload.poseEditor() ? new PoseEditorScreen(portrait, null) : new PortraitScreen(portrait));
        });
        // Organisers: a right click on a portrait out of the game's reach (they hang high above the stage) opens it too
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PortraitClient::farClick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PREVIEWS.clear();
            PoseLibraries.setClient(PoseLibrary.withPresets());
            client.execute(PortraitSkins::clear);
        });
    }

    private static boolean useWasDown;

    /** On a right-click press: the portrait under the crosshair up to {@link PortraitNetwork#FAR_REACH}, if nothing nearer is hit. */
    private static void farClick(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown();
        boolean pressed = down && !useWasDown;
        useWasDown = down;
        if (!pressed || mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (!fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) return;
        // Within reach the game already does it (and a block or an entity in front wins)
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) return;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1);
        double reach = PortraitNetwork.FAR_REACH;
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(reach));
        net.minecraft.world.phys.EntityHitResult hit = portraitAlong(mc, eye, end);
        if (hit == null) return;
        double portrait = hit.getLocation().distanceTo(eye);
        net.minecraft.world.phys.BlockHitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && block.getLocation().distanceTo(eye) < portrait) return;
        if (ClientPlayNetworking.canSend(PortraitNetwork.OpenFar.TYPE)) ClientPlayNetworking.send(new PortraitNetwork.OpenFar(hit.getEntity().getId()));
    }

    /**
     * The nearest portrait on the segment eye → end, or null. Every loaded portrait is tried: the game's entity
     * search goes by the sections around the entity's position (the hook, far above a long string's frame).
     */
    static net.minecraft.world.phys.EntityHitResult portraitAlong(Minecraft mc, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 end) {
        if (mc.level == null) return null;
        PortraitEntity best = null;
        net.minecraft.world.phys.Vec3 at = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PortraitEntity p) || p.isRemoved()) continue;
            var box = p.getBoundingBox();
            var hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestD) {
                bestD = d;
                best = p;
                at = hit.get();
            }
        }
        return best == null ? null : new net.minecraft.world.phys.EntityHitResult(best, at);
    }

    /** After the game's aim: a portrait within reach, nearer than what the game found, is what the player aims at. */
    public static void pickInReach(Minecraft mc, float partialTick) {
        net.minecraft.world.entity.Entity viewer = mc.getCameraEntity();
        if (viewer == null || mc.player == null || mc.level == null) return;
        net.minecraft.world.phys.Vec3 eye = viewer.getEyePosition(partialTick);
        double reach = mc.player.entityInteractionRange();
        net.minecraft.world.phys.Vec3 end = eye.add(viewer.getViewVector(partialTick).scale(reach));
        net.minecraft.world.phys.EntityHitResult hit = portraitAlong(mc, eye, end);
        if (hit == null) return;
        net.minecraft.world.phys.HitResult now = mc.hitResult;
        if (now != null && now.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && now.getLocation().distanceToSqr(eye) <= hit.getLocation().distanceToSqr(eye)) return;
        mc.hitResult = hit;
        mc.crosshairPickEntity = hit.getEntity();
    }

    public static Pose previewPose(int entityId) {
        return PREVIEWS.get(entityId);
    }

    static void setPreview(int entityId, Pose pose) {
        if (pose == null) PREVIEWS.remove(entityId);
        else PREVIEWS.put(entityId, pose);
    }
}
