package fr.lordfinn.craftmaster.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.broadcast.CameraRenderer;
import fr.lordfinn.craftmaster.client.curtain.CurtainClient;
import fr.lordfinn.craftmaster.client.regie.VoiceRegie;
import fr.lordfinn.craftmaster.client.portrait.PortraitClient;
import fr.lordfinn.craftmaster.network.Network;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;

public final class CraftMasterClient implements ClientModInitializer {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CraftMaster.id("main"));

    /** Opens / closes the ranking screen. */
    public static final KeyMapping RANKING_KEY = new KeyMapping("key.craftmaster.ranking", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/, InputConstants.KEY_K, CATEGORY);

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModContent.SEAT, NoopRenderer::new);
        PortraitClient.init();
        // Studio set (chantier F)
        fr.lordfinn.craftmaster.client.studio.StudioClient.init();
        CurtainClient.init();
        // Dev environment only: scripted mouse / keys for testing screens (docs/wiki/Tests.md)
        DevInput.init();
        KeyMappingHelper.registerKeyMapping(RANKING_KEY);
        VoiceRegie.init();
        // Moderators' director (key ;): orders checked by the server, never shown on the broadcast
        fr.lordfinn.craftmaster.client.regie.ModoDeskClient.init();
        // Chantier H: task props, results sheet, drawing boards, plinth
        fr.lordfinn.craftmaster.client.trial.TrialClient.init();
        // TV direction (chantier E): broadcast mode, director's shots, overlay, camera tripods
        Broadcast.init();
        // Replays (Server Replay's viewer): playback bar and direction
        fr.lordfinn.craftmaster.client.replay.ReplayClient.init();
        EntityRendererRegistry.register(ModContent.CAMERA, CameraRenderer::new);
        // Tasks, envelopes, hidden notes (chantier D)
        fr.lordfinn.craftmaster.client.task.TaskClient.init();
        // Screen effects (fx/): post effects on the view, wearable glasses and masks
        fr.lordfinn.craftmaster.client.fx.FxClient.init();
        // Referee's score entry screen (key U), player frame in the HUD, StreamDeck API (after TaskClient: config)
        fr.lordfinn.craftmaster.client.score.ScoreEntryClient.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (RANKING_KEY.consumeClick()) openRanking(mc, -1);
        });

        ClientPlayNetworking.registerGlobalReceiver(Network.Ranking.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            // The player frame in the HUD follows every ranking received
            fr.lordfinn.craftmaster.client.score.PlayerFrameHud.accept(payload);
            if (mc.gui.screen() instanceof RankingScreen screen && screen.season() == payload.season()) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new RankingScreen(payload));
        });
    }

    public static void openRanking(Minecraft mc, int season) {
        if (ClientPlayNetworking.canSend(Network.RequestRanking.TYPE)) {
            ClientPlayNetworking.send(new Network.RequestRanking(season));
        } else if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
        }
    }
}
