package fr.lordfinn.craftmaster.client.replay;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * The replay director on the game watching a replay (Server Replay's viewer, started by {@code /cmreplay voir}):
 * the server says where the playback is ({@link ReplayStatus}, five times a second); this game shows the playback
 * bar at the bottom ({@link ReplayBar}), sends the bar's orders back ({@code /replay view cm …}, the only commands
 * the viewer lets through), and directs the picture ({@link ReplayDirector}) with the broadcast camera.
 *
 * <p>Keys while a replay plays (all in the controls, CraftMaster category): B play / pause, ← → 5 s back / forward
 * (Shift: 30 s), ↑ ↓ faster / slower, Page Up / Page Down previous / next marker, G camera mode (Direct, Auto,
 * Manuel, Libre), M the mouse on the timeline, H no bar at all; 1–9, the wheel, 0 (wide shot) and Tab (players) cut
 * here as in the live. The same orders come from the voice control and the Stream Deck ({@link ReplayControl}).
 * On screen there is only the thin bar, for a few seconds after an order ({@link ReplayBar}): the picture is the
 * viewers'.
 */
public final class ReplayClient {

    private ReplayClient() {}

    public static final KeyMapping PLAY_KEY = key("replay_play", InputConstants.KEY_B);
    public static final KeyMapping BACK_KEY = key("replay_back", InputConstants.KEY_LEFT);
    public static final KeyMapping FORWARD_KEY = key("replay_forward", InputConstants.KEY_RIGHT);
    public static final KeyMapping FASTER_KEY = key("replay_faster", InputConstants.KEY_UP);
    public static final KeyMapping SLOWER_KEY = key("replay_slower", InputConstants.KEY_DOWN);
    public static final KeyMapping PREV_MARKER_KEY = key("replay_prev_marker", InputConstants.KEY_PAGEUP);
    public static final KeyMapping NEXT_MARKER_KEY = key("replay_next_marker", InputConstants.KEY_PAGEDOWN);
    public static final KeyMapping CAMERA_KEY = key("replay_camera", InputConstants.KEY_G);
    public static final KeyMapping PANEL_KEY = key("replay_panel", InputConstants.KEY_M);
    public static final KeyMapping HIDE_KEY = key("replay_hide", InputConstants.KEY_H);
    private static final List<KeyMapping> KEYS = List.of(PLAY_KEY, BACK_KEY, FORWARD_KEY, FASTER_KEY, SLOWER_KEY, PREV_MARKER_KEY,
            NEXT_MARKER_KEY, CAMERA_KEY, PANEL_KEY, HIDE_KEY);
    /** The speeds the arrows step through. */
    static final float[] SPEEDS = {0.25f, 0.5f, 1, 2, 4, 8};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.craftmaster." + name, InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/, code, CraftMasterClient.CATEGORY);
    }

    private static ReplayStatus status;
    /** Real time (ms) the status arrived, and the replay time it said then. */
    private static long statusAt;
    private static long statusMs;
    private static List<Marker> markers = List.of();
    /** A seek or a pause sent and not yet confirmed: the bar shows it right away. */
    private static long expectMs = -1, expectUntil;
    private static Boolean expectPaused;
    private static boolean wasActive, broadcastBefore, hidden;
    /** The panel is open, or was until a loading screen took its place (a seek back, a file switch): it comes back. */
    static boolean panelWanted;
    static long lastInput;

    public static void init() {
        for (KeyMapping k : KEYS) KeyMappingHelper.registerKeyMapping(k);
        ClientPlayNetworking.registerGlobalReceiver(ReplayStatus.TYPE, (payload, ctx) -> onStatus(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> {
            status = null;
            switchingUntil = 0;
        }));
        ClientTickEvents.START_CLIENT_TICK.register(ReplayClient::tick);
        HudElementRegistry.addLast(CraftMaster.id("replay_bar"), ReplayBar::extract);
        // /cmbarre: the panel for the mouse (same as the key M), handled on this game (the viewer lets no command through but its own)
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("cmbarre").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!active()) {
                        c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Aucun replay en cours (/cmreplay voir)."));
                        return 0;
                    }
                    // After the chat screen closes
                    mc.execute(() -> openPanel(mc));
                    return 1;
                })));
    }

    private static void onStatus(ReplayStatus s) {
        long now = System.currentTimeMillis();
        // Smooth clock: keep ours unless the server's is far from it (seek, file switch, a hiccup)
        long mine = status == null ? -1 : nowMs();
        if (status != null && (!s.view().equals(status.view()) || s.progress() < status.progress() - 1_000)) switchingUntil = 0;
        status = s;
        if (s.markers() != null) markers = List.copyOf(s.markers());
        if (expectMs >= 0 && now < expectUntil && Math.abs(s.progress() - expectMs) > 400) {
            statusMs = expectMs;
        } else {
            expectMs = -1;
            statusMs = mine >= 0 && !s.paused() && Math.abs(mine - s.progress()) < 300 ? mine : s.progress();
        }
        if (expectPaused != null && now < expectUntil && expectPaused != s.paused()) {
            // Not applied yet
        } else expectPaused = null;
        statusAt = now;
    }

    /** A replay is playing on this game (and its CraftMaster bar is wanted). */
    public static boolean active() {
        if (status == null) return false;
        long now = System.currentTimeMillis();
        // A file switch: the old viewer closes, the other starts a few seconds later (nothing arrives meanwhile)
        if (now < switchingUntil) return true;
        return Minecraft.getInstance().level != null && now - statusAt < 2_000;
    }

    /** Until when a file switch is under way. */
    private static long switchingUntil;

    public static ReplayStatus status() {
        return status;
    }

    public static boolean paused() {
        return expectPaused != null ? expectPaused : status != null && status.paused();
    }

    /** Where the playback is now (ms in the file), counted smoothly between two statuses. */
    public static long nowMs() {
        if (status == null) return 0;
        long t = statusMs;
        if (!paused()) t += Math.round((System.currentTimeMillis() - statusAt) * status.speed());
        return Math.max(0, Math.min(status.duration() > 0 ? status.duration() : Long.MAX_VALUE, t));
    }

    /** The server tick the playback is at (the round's timer counts from it), or -1 when the file does not say. */
    public static double serverTick() {
        if (!active() || status.tick0() < 0) return -1;
        return status.tick0() + nowMs() / 50.0;
    }

    public static List<Marker> markers() {
        return markers;
    }

    static boolean hidden() {
        return hidden;
    }

    // ---------------------------------------------------------------- orders (keys, voice, Stream Deck, mouse)

    static void send(String sub) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand("replay view cm " + sub);
        lastInput = System.currentTimeMillis();
    }

    /** Goes to {@code ms} in the file. */
    public static void seek(long ms) {
        if (status == null) return;
        ms = Math.max(0, Math.min(status.duration(), ms));
        // Backward, the server opens the file again (a few seconds without status): still the same replay
        if (ms < nowMs()) switchingUntil = System.currentTimeMillis() + 8_000;
        send("seek " + ms);
        expectMs = ms;
        expectUntil = System.currentTimeMillis() + 4_000;
        statusMs = ms;
        statusAt = System.currentTimeMillis();
    }

    /** {@code ms} forward (negative: back), with its caption. */
    public static void step(long ms) {
        seek(nowMs() + ms);
        ReplayBar.caption((ms < 0 ? "◀ " : "▶ ") + Math.abs(ms) / 1000 + " s");
    }

    public static void togglePause() {
        setPaused(!paused());
    }

    public static void setPaused(boolean p) {
        statusMs = nowMs();
        statusAt = System.currentTimeMillis();
        send(p ? "pause" : "play");
        expectPaused = p;
        expectUntil = System.currentTimeMillis() + 2_000;
        ReplayBar.caption(p ? "Pause" : "Lecture");
    }

    public static void speed(float x) {
        x = Math.max(SPEEDS[0], Math.min(SPEEDS[SPEEDS.length - 1], x));
        send("speed " + String.format(Locale.ROOT, "%.2f", x));
        ReplayBar.caption("×" + ReplayBar.speed(x));
    }

    public static void stepSpeed(int dir) {
        float cur = status == null ? 1 : status.speed();
        int i = 0;
        for (int j = 1; j < SPEEDS.length; j++) if (Math.abs(SPEEDS[j] - cur) < Math.abs(SPEEDS[i] - cur)) i = j;
        speed(SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, i + dir))]);
    }

    /** Another file of the recording ("zone" or a player), same instant. False if there is no such file. */
    public static boolean view(String name) {
        if (status == null) return false;
        String found = null;
        for (String v : status.views()) if (v.equalsIgnoreCase(name)) found = v;
        if (found == null) return false;
        if (found.equalsIgnoreCase(status.view())) return true;
        send("vue " + found);
        switchingUntil = System.currentTimeMillis() + 8_000;
        ReplayBar.caption(found.equals("zone") ? "Zone de l'épreuve" : "Écran de " + found);
        return true;
    }

    public static void close() {
        send("close");
    }

    /** The marker after (or before) the playback's time; false if there is none. */
    public static boolean marker(int dir) {
        long now = nowMs();
        Marker pick = null;
        for (Marker m : markers) {
            if (dir > 0 && m.ms() > now + 300) {
                pick = m;
                break;
            }
            // Backwards: a marker just passed (less than 2 s ago) is skipped, to be able to go further back
            if (dir < 0 && m.ms() < now - 2_000) pick = m;
        }
        if (pick != null) {
            seek(pick.ms());
            ReplayBar.caption(pick.name());
            return true;
        }
        if (dir < 0) {
            seek(0);
            ReplayBar.caption("Début");
            return true;
        }
        return false;
    }

    public static void setMode(ReplayDirector.Mode m) {
        ReplayDirector.setMode(m);
        Broadcast.set(m != ReplayDirector.Mode.FREE);
        ReplayBar.caption("Réalisation : " + m.label);
    }

    // ---------------------------------------------------------------- keys

    private static void tick(Minecraft mc) {
        boolean act = active();
        if (act != wasActive) {
            wasActive = act;
            if (act) {
                // The replay starts: the broadcast camera shows the direction (what this game was doing comes back after)
                broadcastBefore = Broadcast.active();
                ReplayDirector.reset();
                lastInput = System.currentTimeMillis();
                if (!broadcastBefore) Broadcast.set(true);
            } else {
                ReplayDirector.reset();
                if (!broadcastBefore && Broadcast.active()) Broadcast.set(false);
                markers = List.of();
            }
        }
        if (act && panelWanted && mc.gui.screen() == null && mc.player != null) openPanel(mc);
        if (!act) panelWanted = false;
        if (!act || mc.gui.screen() != null) {
            for (KeyMapping k : KEYS) while (k.consumeClick()) {}
            return;
        }
        boolean shift = mc.hasShiftDown();
        while (PLAY_KEY.consumeClick()) togglePause();
        while (BACK_KEY.consumeClick()) step(shift ? -30_000 : -5_000);
        while (FORWARD_KEY.consumeClick()) step(shift ? 30_000 : 5_000);
        while (FASTER_KEY.consumeClick()) stepSpeed(1);
        while (SLOWER_KEY.consumeClick()) stepSpeed(-1);
        while (PREV_MARKER_KEY.consumeClick()) marker(-1);
        while (NEXT_MARKER_KEY.consumeClick()) marker(1);
        while (CAMERA_KEY.consumeClick()) {
            ReplayDirector.Mode[] all = ReplayDirector.Mode.values();
            setMode(all[(ReplayDirector.mode().ordinal() + (shift ? all.length - 1 : 1)) % all.length]);
        }
        while (HIDE_KEY.consumeClick()) {
            hidden = !hidden;
            if (!hidden) lastInput = System.currentTimeMillis();
        }
        while (PANEL_KEY.consumeClick()) openPanel(mc);
    }

    static void openPanel(Minecraft mc) {
        panelWanted = true;
        mc.gui.setScreen(new ReplayScreen());
    }

    static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }
}
