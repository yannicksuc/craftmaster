package fr.lordfinn.craftmaster.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.network.MenuNetwork;
import fr.lordfinn.craftmaster.trial.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The CraftMaster menu on this game: key N (or {@code /cm menu} from the server) opens {@link MainMenuScreen}, which
 * asks the server for the show's state once a second; the organisers' debug lines ({@link Debug.Lines}) are drawn
 * at the top left, never in broadcast mode.
 */
public final class MenuClient {

    private MenuClient() {}

    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.menu", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_N, CraftMasterClient.CATEGORY);

    /** The last state received (null before the first). */
    static JsonObject state;
    static String stateJson = "";
    /** The last system messages (the answers of the commands sent from the menu). */
    static final Deque<Component> ANSWERS = new ArrayDeque<>();
    private static List<String> debugLines = List.of();

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        ClientPlayNetworking.registerGlobalReceiver(MenuNetwork.Open.TYPE, (payload, ctx) -> open(payload.page()));
        ClientPlayNetworking.registerGlobalReceiver(MenuNetwork.State.TYPE, (payload, ctx) -> {
            if (payload.json().equals(stateJson)) return;
            stateJson = payload.json();
            try {
                state = JsonParser.parseString(payload.json()).getAsJsonObject();
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("[Menu] état illisible : {}", e.toString());
                return;
            }
            if (Minecraft.getInstance().gui.screen() instanceof MainMenuScreen m) m.stateChanged();
        });
        ClientPlayNetworking.registerGlobalReceiver(Debug.Lines.TYPE, (payload, ctx) -> debugLines = payload.lines());
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            state = null;
            stateJson = "";
            debugLines = List.of();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !(Minecraft.getInstance().gui.screen() instanceof MainMenuScreen)) return;
            ANSWERS.addFirst(message);
            while (ANSWERS.size() > 3) ANSWERS.removeLast();
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY.consumeClick()) if (mc.gui.screen() == null) open("");
            if (mc.gui.screen() instanceof MainMenuScreen && mc.player != null && mc.player.tickCount % 20 == 0) request();
        });
        HudElementRegistry.addLast(CraftMaster.id("debug_hud"), MenuClient::debugHud);
    }

    public static boolean available() {
        return ClientPlayNetworking.canSend(MenuNetwork.Request.TYPE);
    }

    public static void open(String page) {
        Minecraft mc = Minecraft.getInstance();
        if (!available()) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Ce serveur n'a pas CraftMaster : pas de menu ici."));
            return;
        }
        ANSWERS.clear();
        request();
        mc.gui.setScreen(new MainMenuScreen(page));
    }

    /** The last state the server sent (null before the first), for the Stream Deck's camera keys. */
    public static JsonObject state() {
        return state;
    }

    public static void request() {
        if (available()) ClientPlayNetworking.send(new MenuNetwork.Request());
    }

    /** Sends {@code /cm <command>} as typed in the chat (rights and answers as usual). */
    public static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand("cm " + command);
        // The state follows right away (the next second would feel slow)
        request();
    }

    private static void debugHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (debugLines.isEmpty() || Broadcast.active()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : debugLines) w = Math.max(w, mc.font.width(l));
        int x = 4, y = 4, h = debugLines.size() * 10 + 4;
        g.fill(x - 2, y - 2, x + w + 4, y + h - 2, 0x90000000);
        g.outline(x - 2, y - 2, w + 6, h, Theme.BORDER_DARK);
        for (String l : debugLines) {
            g.text(mc.font, Component.literal(l), x + 1, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }
}
