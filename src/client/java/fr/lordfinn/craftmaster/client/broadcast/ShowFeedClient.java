package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The show's feed in place of one's own view (a key, F7 by default): the picture of the director's shot with the
 * show's graphics, while the player keeps playing — moving, clicking, chat, inventory, as in first person (their
 * own body is seen in the shot). The server forces it during the big moments (credits, round intros, the reveal):
 * then the key does not leave it, and the player stands still unless the organisers allow moving.
 */
public final class ShowFeedClient {

    private ShowFeedClient() {}

    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.show_feed", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_F7, CraftMasterClient.CATEGORY);

    private static boolean toggled;
    /** Forced by the server: "off", "freeze" or "move". */
    private static String forced = "off";

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        ClientTickEvents.START_CLIENT_TICK.register(ShowFeedClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> forced = "off"));
    }

    /** The feed is on screen (not in broadcast mode, which shows it anyway, its own way). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && !Broadcast.active() && (toggled || !"off".equals(forced));
    }

    /** Forced and standing still: no moving, no clicking (chat and menus stay). */
    public static boolean frozen() {
        return active() && "freeze".equals(forced);
    }

    /** From the server's cue. */
    static void force(String state) {
        Minecraft mc = Minecraft.getInstance();
        boolean was = !"off".equals(forced);
        forced = state == null || state.isBlank() ? "off" : state;
        boolean now = !"off".equals(forced);
        if (was != now && mc.player != null && !Broadcast.active() && !toggled)
            mc.player.sendOverlayMessage(Component.translatable(now ? "message.craftmaster.feed_forced" : "message.craftmaster.feed_released")
                    .withStyle(ChatFormatting.GOLD));
        DirectorCamera.applyEffect();
    }

    private static void tick(Minecraft mc) {
        while (KEY.consumeClick()) {
            if (mc.player == null) continue;
            if (!"off".equals(forced)) {
                // Forced: the key cannot leave it (it will still be on, or off, when the moment ends)
                toggled = !toggled;
                mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.feed_forced_key").withStyle(ChatFormatting.GOLD));
                continue;
            }
            toggled = !toggled;
            mc.player.sendOverlayMessage(Component.translatable(toggled ? "message.craftmaster.feed_on" : "message.craftmaster.feed_off",
                    KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.GOLD));
            DirectorCamera.applyEffect();
        }
        if (frozen() && mc.gui.screen() == null) {
            // Standing still: the movement keys let go, clicks dropped
            for (KeyMapping k : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                    mc.options.keyJump, mc.options.keySprint}) k.setDown(false);
            while (mc.options.keyAttack.consumeClick()) {}
            while (mc.options.keyUse.consumeClick()) {}
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
        }
    }
}
