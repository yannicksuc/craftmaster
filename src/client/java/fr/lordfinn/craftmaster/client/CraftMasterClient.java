package fr.lordfinn.craftmaster.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
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
    public static final KeyMapping RANKING_KEY = new KeyMapping("key.craftmaster.ranking", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModContent.SEAT, NoopRenderer::new);
        PortraitClient.init();
        // Studio set (chantier F)
        fr.lordfinn.craftmaster.client.studio.StudioClient.init();
        KeyMappingHelper.registerKeyMapping(RANKING_KEY);
        VoiceRegie.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (RANKING_KEY.consumeClick()) openRanking(mc, -1);
        });

        ClientPlayNetworking.registerGlobalReceiver(Network.Ranking.TYPE, (payload, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.gui.screen() instanceof RankingScreen screen && screen.season() == payload.season()) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new RankingScreen(payload));
        });
    }

    static void openRanking(Minecraft mc, int season) {
        if (ClientPlayNetworking.canSend(Network.RequestRanking.TYPE)) {
            ClientPlayNetworking.send(new Network.RequestRanking(season));
        } else if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
        }
    }
}
