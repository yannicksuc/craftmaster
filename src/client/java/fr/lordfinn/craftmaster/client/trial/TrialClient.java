package fr.lordfinn.craftmaster.client.trial;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.trial.BoardEntity;
import fr.lordfinn.craftmaster.trial.PropConfig;
import fr.lordfinn.craftmaster.trial.TrialContent;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import fr.lordfinn.craftmaster.trial.TrialPropEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client side of the tasks (chantier H): renderers, the prop / results / board screens, the results key. */
public final class TrialClient {

    private TrialClient() {}

    /** Opens the results sheet of the current round (organisers). */
    public static final KeyMapping RESULTS_KEY = new KeyMapping("key.craftmaster.results", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N, CraftMasterClient.CATEGORY);

    public static void init() {
        EntityRendererRegistry.register(TrialContent.TRIAL_PROP, TrialPropRenderer::new);
        EntityRendererRegistry.register(TrialContent.BOARD, BoardRenderer::new);
        BlockEntityRendererRegistry.register(TrialContent.PLINTH_ENTITY, PlinthRenderer::new);
        KeyMappingHelper.registerKeyMapping(RESULTS_KEY);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (RESULTS_KEY.consumeClick()) {
                if (ClientPlayNetworking.canSend(TrialNetwork.RequestSheet.TYPE)) ClientPlayNetworking.send(new TrialNetwork.RequestSheet(""));
                else if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
            }
            BoardTextures.endFrame();
        });

        ClientPlayNetworking.registerGlobalReceiver(TrialNetwork.OpenProp.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.level == null || !(mc.level.getEntity(p.entityId()) instanceof TrialPropEntity prop)) return;
            PropConfig config = PropConfig.fromJson(p.config());
            if (mc.gui.screen() instanceof PropScreen s && s.prop() == prop) s.update(config, p.stats(), p.give());
            else mc.gui.setScreen(new PropScreen(prop, config, p.stats(), p.give()));
        });
        ClientPlayNetworking.registerGlobalReceiver(TrialNetwork.OpenBoard.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.level != null && mc.level.getEntity(p.entityId()) instanceof BoardEntity board) mc.gui.setScreen(new BoardTextScreen(board));
        });
        ClientPlayNetworking.registerGlobalReceiver(TrialNetwork.Sheet.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.gui.screen() instanceof ResultsScreen s) {
                // A refresh of another round than the one shown is ignored (the screen asks for its own)
                String id = com.google.gson.JsonParser.parseString(p.json()).getAsJsonObject().getAsJsonObject("manche").get("id").getAsString();
                if (p.open() || id.equals(s.mancheId())) s.update(p.json());
            } else if (p.open()) {
                mc.gui.setScreen(new ResultsScreen(p.json()));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BoardTextures::clear));
    }
}
