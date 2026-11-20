package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.menu.MenuClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The game master's voice on screen ({@code MasterVoice} on the server): for him, his mode and who hears him, always
 * at the top of his screen while it is not off; for a player he talks to, a small « 🎙 LordFinn te parle ». Never on
 * the broadcast picture. The key (F6 by default, in the controls) goes to the next mode.
 */
public final class VoiceHud {

    private VoiceHud() {}

    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.voice_mode", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_F6, CraftMasterClient.CATEGORY);

    private static String line = "";
    private static boolean host;

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        HudElementRegistry.addLast(CraftMaster.id("master_voice"), VoiceHud::extract);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY.consumeClick()) {
                // The organisers only (the menu's state says who is one)
                com.google.gson.JsonObject st = MenuClient.state();
                if (mc.player != null && st != null && st.has("admin") && st.get("admin").getAsBoolean()) MenuClient.command("vocal mode suivant");
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> line = ""));
    }

    /** From the server: the line to show ("" = nothing), {@code role} host | player. */
    static void set(String text, String role) {
        line = text == null ? "" : text;
        host = "host".equals(role);
    }

    public static String line() {
        return line;
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (line.isEmpty() || Broadcast.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Component c = Component.literal("🎙 " + line);
        int w = font.width(c);
        int x = host ? (g.guiWidth() - w) / 2 : g.guiWidth() - w - 6;
        int y = host ? 4 : 30;
        g.fill(x - 4, y - 3, x + w + 4, y + 11, 0x90000000);
        g.text(font, c, x, y, host ? 0xFFFFD27F : 0xFF7FFFD4, true);
    }
}
