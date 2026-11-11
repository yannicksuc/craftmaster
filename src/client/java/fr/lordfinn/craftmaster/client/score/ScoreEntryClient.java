package fr.lordfinn.craftmaster.client.score;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.network.ScoreEntryNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client side of the score entry screen: key U, the board from the server, the player frame and the StreamDeck. */
public final class ScoreEntryClient {

    private ScoreEntryClient() {}

    /** Opens the referee's score entry screen (organisers). */
    public static final KeyMapping SCORES_KEY = new KeyMapping("key.craftmaster.scores", InputConstants.Type.KEYBOARD, InputConstants.KEY_U,
            CraftMasterClient.CATEGORY);

    public static void init() {
        KeyMappingHelper.registerKeyMapping(SCORES_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (SCORES_KEY.consumeClick()) open(mc);
        });
        ClientPlayNetworking.registerGlobalReceiver(ScoreEntryNetwork.Board.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.gui.screen() instanceof ScoreEntryScreen s) s.update(p.json());
            else if (p.open()) mc.gui.setScreen(new ScoreEntryScreen(p.json()));
        });
        PlayerFrameHud.init();
        StreamDeckServer.start();
    }

    /** Asks the server for the active season's board (the screen opens when it arrives). */
    public static void open(Minecraft mc) {
        if (mc.gui.screen() instanceof ScoreEntryScreen) return;
        if (ClientPlayNetworking.canSend(ScoreEntryNetwork.RequestBoard.TYPE)) {
            ClientPlayNetworking.send(new ScoreEntryNetwork.RequestBoard(ScoreEntryNetwork.ACTIVE, true));
        } else if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
        }
    }
}
