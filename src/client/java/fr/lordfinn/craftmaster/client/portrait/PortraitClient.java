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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PREVIEWS.clear();
            PoseLibraries.setClient(PoseLibrary.withPresets());
            client.execute(PortraitSkins::clear);
        });
    }

    public static Pose previewPose(int entityId) {
        return PREVIEWS.get(entityId);
    }

    static void setPreview(int entityId, Pose pose) {
        if (pose == null) PREVIEWS.remove(entityId);
        else PREVIEWS.put(entityId, pose);
    }
}
