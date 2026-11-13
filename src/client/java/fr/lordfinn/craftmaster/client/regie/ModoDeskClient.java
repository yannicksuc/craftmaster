package fr.lordfinn.craftmaster.client.regie;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.regie.ModoDesk;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The moderators' director in game (docs/wiki/RegieModos.md): key ; (or /regie) opens {@link ModoDeskScreen} on this
 * game only. Never on a broadcast game (F8): the screen closes as soon as the broadcast mode is on.
 */
public final class ModoDeskClient {

    private ModoDeskClient() {}

    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.modo_desk", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            59 /* ; */, CraftMasterClient.CATEGORY);

    /** Asked to open, waiting for the server's first state. */
    private static boolean asked;

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        ClientTickEvents.END_CLIENT_TICK.register(ModoDeskClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> asked = false);
        ClientPlayNetworking.registerGlobalReceiver(ModoDesk.DeskState.TYPE, (p, ctx) -> accept(ctx.client(), p.json()));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            for (String name : java.util.List.of("regie", "craftMasterRegie", "craftmasterregie"))
                d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(name).executes(c -> {
                    open(Minecraft.getInstance());
                    return 1;
                }));
        });
    }

    private static void tick(Minecraft mc) {
        while (KEY.consumeClick()) open(mc);
        if (mc.gui.screen() instanceof ModoDeskScreen s && Broadcast.active()) s.onClose();
    }

    public static void open(Minecraft mc) {
        if (mc.player == null || Broadcast.active()) return;
        if (!ClientPlayNetworking.canSend(ModoDesk.DeskOpen.TYPE)) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
            return;
        }
        asked = true;
        ClientPlayNetworking.send(new ModoDesk.DeskOpen(true));
    }

    static void close() {
        asked = false;
        if (ClientPlayNetworking.canSend(ModoDesk.DeskOpen.TYPE)) ClientPlayNetworking.send(new ModoDesk.DeskOpen(false));
    }

    private static void accept(Minecraft mc, String json) {
        JsonObject state = JsonParser.parseString(json).getAsJsonObject();
        boolean any = state.getAsJsonArray("rights").size() > 0;
        if (mc.gui.screen() instanceof ModoDeskScreen s) {
            if (any) s.update(state);
            else {
                s.onClose();
                if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.modo_desk.closed").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (!asked) return;
        asked = false;
        if (!any) {
            close();
            if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable(state.get("paused").getAsBoolean()
                    ? "message.craftmaster.modo_desk.paused" : "message.craftmaster.modo_desk.refused").withStyle(ChatFormatting.RED));
            return;
        }
        if (Broadcast.active()) {
            close();
            return;
        }
        mc.gui.setScreen(new ModoDeskScreen(state));
    }
}
