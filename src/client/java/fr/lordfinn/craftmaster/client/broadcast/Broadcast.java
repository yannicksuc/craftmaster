package fr.lordfinn.craftmaster.client.broadcast;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.BroadcastMode;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.CameraRequest;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Broadcast mode of this game (the one OBS captures for Twitch): toggled with a key (F8 by default) or
 * {@code /craftMasterBroadcast} ({@code /diffusion}). In broadcast mode the game shows the director's shot instead
 * of the player's view, hides the normal interface (hand, hotbar, crosshair, chat unless asked) and draws the TV
 * graphics ({@link BroadcastOverlay}). Following the director needs nothing; the keys 1–9 and the mouse wheel ask
 * the server for a cut, which it only does for those allowed to drive the cameras.
 */
public final class Broadcast {

    private Broadcast() {}

    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.craftmaster.broadcast", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F8, CraftMasterClient.CATEGORY);

    private static final Gson GSON = new Gson();

    private static boolean on;
    private static boolean showChat;
    private static long lastScroll;

    /** Vanilla HUD parts hidden in broadcast mode (the titles stay: countdown, reveal). */
    private static final List<Identifier> HIDDEN = List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR,
            VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR_FOREGROUND, VanillaHudElements.HELD_ITEM_TOOLTIP,
            VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR, VanillaHudElements.SLEEP,
            VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE,
            VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);

    public static void init() {
        KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);
        for (Identifier id : HIDDEN) HudElementRegistry.replaceElement(id, vanilla -> hiddenInBroadcast(vanilla, false));
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, vanilla -> hiddenInBroadcast(vanilla, true));
        HudElementRegistry.addLast(CraftMaster.id("broadcast"), BroadcastOverlay::extract);

        ClientPlayNetworking.registerGlobalReceiver(ShowState.TYPE, (payload, ctx) -> ShowStateClient.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(ShowCue.TYPE, (payload, ctx) -> {
            if (Cues.THEME.equals(payload.kind())) {
                if ("stop".equals(payload.title())) ThemeSound.stop();
                else ThemeSound.play();
            } else {
                BroadcastOverlay.cue(payload);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Still in broadcast mode after a reconnection: the new server must know
            if (on) client.execute(() -> sendMode(true));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ShowStateClient.clear();
            DirectorCamera.reset();
            BroadcastOverlay.reset();
            ThemeSound.stop();
        }));
        // Before the game handles its keys: in broadcast mode the hotbar keys pick a camera
        ClientTickEvents.START_CLIENT_TICK.register(Broadcast::tick);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
    }

    private static HudElement hiddenInBroadcast(HudElement vanilla, boolean chat) {
        return (g, delta) -> {
            if (!active() || (chat && showChat)) vanilla.extractRenderState(g, delta);
        };
    }

    /** Broadcast mode on, in a world. */
    public static boolean active() {
        return on && Minecraft.getInstance().level != null;
    }

    public static void set(boolean value) {
        Minecraft mc = Minecraft.getInstance();
        if (on == value) return;
        on = value;
        sendMode(on);
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(on
                ? "Mode diffusion activé (" + TOGGLE_KEY.getTranslatedKeyMessage().getString() + " pour quitter)"
                : "Mode diffusion désactivé").withStyle(ChatFormatting.GOLD));
        if (!on) ThemeSound.stop();
    }

    private static void sendMode(boolean value) {
        if (ClientPlayNetworking.canSend(BroadcastMode.TYPE)) ClientPlayNetworking.send(new BroadcastMode(value));
    }

    private static void tick(Minecraft mc) {
        while (TOGGLE_KEY.consumeClick()) set(!on);
        if (!active() || mc.gui.screen() != null) return;
        for (int i = 0; i < 9; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) cutTo(String.valueOf(i + 1));
        }
    }

    /** Mouse wheel in broadcast mode (from the mixin): previous / next camera. True if consumed. */
    public static boolean onScroll(double amount) {
        if (!active() || amount == 0) return false;
        long now = System.currentTimeMillis();
        if (now - lastScroll < 180) return true;
        lastScroll = now;
        BroadcastNetwork.Shot shot = ShowStateClient.get().shot();
        CameraSpot s = ShowStateClient.cameras().step(shot.kind() == BroadcastNetwork.SHOT_CAMERA ? shot.camera() : -1, amount > 0 ? -1 : 1);
        if (s != null) cutTo(String.valueOf(s.number()));
        return true;
    }

    private static void cutTo(String camera) {
        Minecraft mc = Minecraft.getInstance();
        if (ShowStateClient.cameras().resolve(camera) == null) {
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("Pas de caméra " + camera).withStyle(ChatFormatting.GRAY));
            return;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "camera.cut");
        a.put("camera", camera);
        request(a);
    }

    static void request(Map<String, Object> action) {
        if (ClientPlayNetworking.canSend(CameraRequest.TYPE)) ClientPlayNetworking.send(new CameraRequest(GSON.toJson(action)));
    }

    // ---------------------------------------------------------------- client commands

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        for (String name : List.of("craftMasterBroadcast", "craftmasterbroadcast", "diffusion")) d.register(tree(name));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
        return literal(name)
                .executes(c -> {
                    set(!on);
                    return 1;
                })
                .then(literal("on").executes(c -> {
                    set(true);
                    return 1;
                }))
                .then(literal("off").executes(c -> {
                    set(false);
                    return 1;
                }))
                .then(literal("chat")
                        .then(literal("on").executes(c -> {
                            showChat = true;
                            c.getSource().sendFeedback(Component.literal("Chat visible en mode diffusion.").withStyle(ChatFormatting.GOLD));
                            return 1;
                        }))
                        .then(literal("off").executes(c -> {
                            showChat = false;
                            c.getSource().sendFeedback(Component.literal("Chat masqué en mode diffusion.").withStyle(ChatFormatting.GOLD));
                            return 1;
                        })))
                .then(literal("camera").then(argument("camera", StringArgumentType.greedyString()).executes(c -> {
                    cutTo(StringArgumentType.getString(c, "camera"));
                    return 1;
                })))
                .then(literal("status").executes(c -> {
                    ShowState st = ShowStateClient.get();
                    c.getSource().sendFeedback(Component.literal("Diffusion " + (on ? "activée" : "désactivée") + " · "
                            + st.cameras().size() + " caméra(s) · plan : " + describe(st.shot())
                            + (st.auto() ? " · réalisation auto" : "")).withStyle(ChatFormatting.GOLD));
                    return 1;
                }));
    }

    private static String describe(BroadcastNetwork.Shot s) {
        return switch (s.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> {
                CameraSpot spot = ShowStateClient.cameras().get(s.camera());
                yield spot == null ? "caméra " + s.camera() : spot.label();
            }
            case BroadcastNetwork.SHOT_FOLLOW -> "on suit " + s.player();
            default -> "aucun (vue du joueur)";
        };
    }
}
