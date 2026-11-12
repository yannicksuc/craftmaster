package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.Studio;
import fr.lordfinn.craftmaster.studio.StudioNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

/** Client side of the studio set: renderers, giant timer state, studio screen settings. */
public final class StudioClient {

    private StudioClient() {}

    public static void init() {
        EntityRendererRegistry.register(Studio.STUDIO_SCREEN, ImageScreenRenderer::new);
        BlockEntityRendererRegistry.register(Studio.SPOTLIGHT_BE, SpotlightRenderer::new);
        BlockEntityRendererRegistry.register(Studio.TIMER_DISPLAY_BE, TimerDisplayRenderer::new);
        BlockEntityRendererRegistry.register(Studio.TIMER_TABLET_BE, TimerTabletRenderer::new);
        BlockEntityRendererRegistry.register(Studio.DESK_BE, DeskRenderer::new);
        BlockEntityRendererRegistry.register(Studio.PRIZE_TABLE_BE, PrizeTableRenderer::new);
        BlockEntityRendererRegistry.register(Studio.GONG_BE, GongRenderer::new);
        BlockEntityRendererRegistry.register(Studio.STANCHION_BE, StanchionRenderer::new);
        // LordFinn's furniture from the server (throne, contestants' chair, studio lights, softboxes, frame)
        BlockEntityRendererRegistry.register(fr.lordfinn.craftmaster.studio.Furniture.BE, FurnitureRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(StudioNetwork.TimerState.TYPE, (payload, ctx) -> StudioTimerClient.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(StudioNetwork.OpenScreen.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.level != null && mc.level.getEntity(payload.entityId()) instanceof ImageScreenEntity screen)
                mc.gui.setScreen(new ImageScreenSettings(screen));
        });
        ClientPlayNetworking.registerGlobalReceiver(StudioNetwork.OpenTablet.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.level != null && mc.level.getBlockEntity(payload.pos()) instanceof fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity tablet)
                mc.gui.setScreen(new TabletScreen(tablet));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StudioTimerClient.clear();
            client.execute(ScreenImages::clear);
            client.execute(ScreenVideos::clear);
        });
        // Videos on the studio screens (WaterMedia): sound by distance, players of screens out of sight stopped
        ClientTickEvents.END_CLIENT_TICK.register(ScreenVideos::tick);
    }
}
