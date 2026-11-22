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
import fr.lordfinn.craftmaster.client.replay.ReplayClient;
import fr.lordfinn.craftmaster.client.replay.ReplayDirector;
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

    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.craftmaster.broadcast", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_F8, CraftMasterClient.CATEGORY);
    /** In broadcast mode: next zone of the round (Shift: previous); the keys 1–9 then pick that zone's cameras. */
    public static final KeyMapping ZONE_KEY = new KeyMapping("key.craftmaster.broadcast_zone", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_TAB, CraftMasterClient.CATEGORY);
    /** In broadcast mode: split screen of the zones on / off. */
    public static final KeyMapping MOSAIC_KEY = new KeyMapping("key.craftmaster.broadcast_mosaic", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.KEY_0, CraftMasterClient.CATEGORY);

    private static final Gson GSON = new Gson();

    private static boolean on;
    private static boolean showChat;
    private static long lastScroll;

    /** Vanilla HUD parts hidden in broadcast mode (the titles stay: countdown, reveal). */
    private static final List<Identifier> HIDDEN = List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR,
            VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL, /*? if >=26.3 {*/VanillaHudElements.INFO_BAR_FOREGROUND, /*?}*/VanillaHudElements.HELD_ITEM_TOOLTIP,
            VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR, VanillaHudElements.SLEEP,
            VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE,
            VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);

    /**
     * Also hidden with the show's feed on screen (F7): the crosshair, and the hotbar with what goes with it (hearts,
     * food, armour, experience, held item's name): the picture stays the show's. Chat, titles and messages stay.
     */
    private static final List<Identifier> HIDDEN_WITH_FEED = List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
            VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
            VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
            /*? if >=26.3 {*/VanillaHudElements.INFO_BAR_FOREGROUND, /*?}*/VanillaHudElements.HELD_ITEM_TOOLTIP);

    public static void init() {
        ReadyClient.init();
        KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);
        KeyMappingHelper.registerKeyMapping(ZONE_KEY);
        KeyMappingHelper.registerKeyMapping(MOSAIC_KEY);
        for (Identifier id : HIDDEN)
            HudElementRegistry.replaceElement(id, vanilla -> HIDDEN_WITH_FEED.contains(id) ? hiddenWithFeed(vanilla) : hiddenInBroadcast(vanilla, false));
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, vanilla -> hiddenInBroadcast(vanilla, true));
        HudElementRegistry.addLast(CraftMaster.id("broadcast"), BroadcastOverlay::extract);
        // Launch of a round after its intro: above everything, players and broadcast alike
        // The task typed at the bottom during an intro's last shots (broadcast only)
        HudElementRegistry.addLast(CraftMaster.id("typed_task"), TypedTask::extract);
        HudElementRegistry.addLast(CraftMaster.id("blackout"), Blackout::extract);

        ClientPlayNetworking.registerGlobalReceiver(ShowState.TYPE, (payload, ctx) -> ShowStateClient.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(BroadcastNetwork.FreeCamera.TYPE, (payload, ctx) -> DirectorCamera.freeCamera(payload));
        ClientPlayNetworking.registerGlobalReceiver(fr.lordfinn.craftmaster.broadcast.FollowPrefs.Payload.TYPE, (payload, ctx) -> FollowCamera.prefs(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(ShowCue.TYPE, (payload, ctx) -> {
            if (Cues.THEME.equals(payload.kind())) {
                if ("stop".equals(payload.title())) ThemeSound.stop();
                else ThemeSound.play(payload.subtitle());
            } else if (Cues.BROADCAST.equals(payload.kind())) {
                set("on".equals(payload.title()));
            } else if (Cues.PREVIEW.equals(payload.kind())) {
                DrawingPreviewHud.prefs(payload.title(), payload.subtitle(), payload.player());
            } else if (Cues.RELEASE.equals(payload.kind())) {
                set(false);
                ShowFeedClient.release();
            } else if (Cues.BLACKOUT.equals(payload.kind())) {
                Blackout.start(payload);
            } else if (Cues.BLACKOUT_OPEN.equals(payload.kind())) {
                Blackout.open(payload.ticks());
            } else if (Cues.RIGHTS.equals(payload.kind())) {
                Rights.set("admin".equals(payload.title()), "feed".equals(payload.subtitle()));
            } else if (Cues.VOICE.equals(payload.kind())) {
                VoiceHud.set(payload.title(), payload.subtitle());
            } else if (Cues.FEED.equals(payload.kind())) {
                ShowFeedClient.force(payload.title());
            } else if (Cues.TASK_TYPE.equals(payload.kind())) {
                TypedTask.start(payload);
            } else if (Cues.PATH_PREVIEW.equals(payload.kind())) {
                DirectorCamera.preview(payload.title(), payload.ticks());
            } else {
                if (Cues.CLEAR.equals(payload.kind())) TypedTask.reset();
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
            Blackout.reset();
            TypedTask.reset();
            ThemeSound.stop();
        }));
        // Before the game handles its keys: in broadcast mode the hotbar keys pick a camera
        ClientTickEvents.START_CLIENT_TICK.register(Broadcast::tick);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
        ShowFeedClient.init();
        VoiceHud.init();
        DrawingPreviewHud.init();
        InventoryCamClient.init();
        ThumbCapture.register();
        fr.lordfinn.craftmaster.client.regie.RegieThumbs.register();
    }

    private static HudElement hiddenInBroadcast(HudElement vanilla, boolean chat) {
        return (g, delta) -> {
            if (!active() || (chat && showChat)) vanilla.extractRenderState(g, delta);
        };
    }

    /** Hidden in broadcast mode and with the show's feed on screen (the crosshair would aim at nothing seen). */
    private static HudElement hiddenWithFeed(HudElement vanilla) {
        return (g, delta) -> {
            if (!active() && !ShowFeedClient.active()) vanilla.extractRenderState(g, delta);
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
        if (!on) {
            ThemeSound.stop();
            Mosaic.prepare(false);
        }
        DirectorCamera.applyEffect();
    }

    private static void sendMode(boolean value) {
        if (ClientPlayNetworking.canSend(BroadcastMode.TYPE)) ClientPlayNetworking.send(new BroadcastMode(value));
    }

    private static void tick(Minecraft mc) {
        while (TOGGLE_KEY.consumeClick()) {
            // The key is the organisers' (and the camera accounts'); the others get it from the master (/cm tv diffusion tous on)
            if (Rights.admin()) set(!on);
            else if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(on
                    ? "Le mode diffusion a été lancé par l'animateur : il le coupera."
                    : "Le mode diffusion est lancé par l'animateur.").withStyle(ChatFormatting.GOLD));
        }
        if (!active() || mc.gui.screen() != null) {
            // Keys pressed out of broadcast mode are not kept for later
            while (ZONE_KEY.consumeClick()) {}
            while (MOSAIC_KEY.consumeClick()) {}
            return;
        }
        for (int i = 0; i < 9; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) cutTo(String.valueOf(i + 1));
        }
        while (ZONE_KEY.consumeClick()) zone(mc.hasShiftDown() ? "prev" : "next");
        while (MOSAIC_KEY.consumeClick()) {
            if (ReplayClient.active()) {
                // In a replay: the wide shot, here
                ReplayDirector.wide();
                continue;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("type", "camera.mosaic");
            if (DirectorCamera.shown().kind() == BroadcastNetwork.SHOT_MOSAIC) a.put("enabled", false);
            request(a);
        }
    }

    /** Next / previous zone of the round (the keys 1–9 then address its cameras). */
    private static void zone(String which) {
        if (ReplayClient.active()) {
            // In a replay: the next contestant (Shift: the previous one, through their eyes), here
            ReplayDirector.player(null, "prev".equals(which) ? -1 : 1, "prev".equals(which));
            return;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "camera.focus");
        a.put("zone", which);
        request(a);
    }

    /** Mouse wheel in broadcast mode (from the mixin): previous / next camera, with Shift the zone, closer / further on a follow shot. True if consumed. */
    public static boolean onScroll(double amount) {
        if (!active() || amount == 0) return false;
        // Follow shot on screen: the wheel moves its camera closer or further (FollowCamera); Shift keeps the zones
        if (!Minecraft.getInstance().hasShiftDown() && FollowCamera.zoom(amount)) return true;
        long now = System.currentTimeMillis();
        if (now - lastScroll < 180) return true;
        lastScroll = now;
        if (Minecraft.getInstance().hasShiftDown()) {
            zone(amount > 0 ? "prev" : "next");
            return true;
        }
        BroadcastNetwork.Shot shot = ShowStateClient.get().shot();
        CameraSpot s = ShowStateClient.focused().step(shot.kind() == BroadcastNetwork.SHOT_CAMERA ? shot.camera() : "", amount > 0 ? -1 : 1);
        if (s != null) cutTo(s.key());
        return true;
    }

    /** Asks the server for a cut ("3" = camera 3 of the focus; a key or any reference the server understands). */
    private static void cutTo(String camera) {
        Minecraft mc = Minecraft.getInstance();
        if (ReplayClient.active()) {
            // In a replay the server's director is not there: the cut is made on this game
            ReplayDirector.camera(camera);
            return;
        }
        if (camera.matches("\\d+") && ShowStateClient.focused().get(Integer.parseInt(camera)) == null) {
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("Pas de caméra " + camera + " ici ("
                    + CameraSpot.placeName(ShowStateClient.get().focus()) + ")").withStyle(ChatFormatting.GRAY));
            return;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "camera.cut");
        a.put("camera", camera);
        request(a);
    }

    public static void request(Map<String, Object> action) {
        if (ClientPlayNetworking.canSend(CameraRequest.TYPE)) ClientPlayNetworking.send(new CameraRequest(GSON.toJson(action)));
    }

    // ---------------------------------------------------------------- client commands

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        for (String name : List.of("diffusion")) d.register(tree(name));
    }

    /** /diffusion on|off by the player himself: the organisers' and the camera accounts' only (the others get it from the master). */
    private static int own(boolean value) {
        Minecraft mc = Minecraft.getInstance();
        if (!Rights.admin()) {
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("Le mode diffusion est lancé par l'animateur.").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        set(value);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
        return literal(name)
                .executes(c -> own(!on))
                .then(literal("on").executes(c -> own(true)))
                .then(literal("off").executes(c -> own(false)))
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
                .then(literal("zone").then(argument("zone", StringArgumentType.greedyString()).executes(c -> {
                    zone(StringArgumentType.getString(c, "zone"));
                    return 1;
                })))
                .then(literal("status").executes(c -> {
                    ShowState st = ShowStateClient.get();
                    c.getSource().sendFeedback(Component.literal("Diffusion " + (on ? "activée" : "désactivée") + " · "
                            + st.cameras().size() + " caméra(s) · touches 1–9 : " + CameraSpot.placeName(st.focus())
                            + " (" + ShowStateClient.focused().size() + ") · plan : " + describe(st.shot())
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
            case BroadcastNetwork.SHOT_PATH -> "trajet " + s.camera();
            case BroadcastNetwork.SHOT_MOSAIC -> "écran partagé (" + s.tiles().size() + ")";
            case BroadcastNetwork.SHOT_POV -> "vue de " + s.player();
            case BroadcastNetwork.SHOT_FREE -> "caméra libre";
            default -> "aucun (vue du joueur)";
        } + (s.effect().isEmpty() ? "" : ", effet " + s.effect());
    }
}
