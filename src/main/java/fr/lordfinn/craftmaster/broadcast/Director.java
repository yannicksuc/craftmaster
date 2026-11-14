package fr.lordfinn.craftmaster.broadcast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.BroadcastMode;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.CameraRequest;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Contestant;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.FreeCamera;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Tile;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraPaths;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.camera.Lieux;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Reveal;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The TV director (server side, runtime state): which shot is on air, the default transition, the automatic
 * direction, the overlay options, and the <b>focus</b> — the lieu (studio or round) and zone whose cameras the keys
 * 1–9, the wheel and "caméra 2" address; it sends the show state ({@link ShowState}) to every client with the mod and
 * the one-off overlay cues ({@link ShowCue}). Broadcast games in spectator mode are kept near the shot so their
 * chunks and entities are loaded. Everything is driven by the show actions of {@link BroadcastActions} (commands,
 * voice, broadcast keys), by the show's events (automatic direction) and by the sequences ({@link Credits},
 * {@link MancheIntros}).
 *
 * <p><b>Focus</b>: follows the show by itself — the running round's cameras from its start until the reveal (if it
 * has any), the studio's otherwise (intro, reveal, credits) — unless the operator chose a lieu ({@code camera.focus});
 * a zone of the round can be selected on top ("zone d'Ensuri", Tab on the broadcast game). The choice is dropped when
 * the show moves on to another phase.
 *
 * <p><b>Seam for a smarter automatic direction</b>: every shot goes on air through {@link #take(MinecraftServer,
 * ShotRequest)}, and the automatic choice of shots is a {@link Planner} ({@link #setPlanner}); the default one
 * ({@code AutoDirection}) cuts to whoever scores or is in the spotlight. A planner can use {@link Lieux#zoneOf},
 * {@link Lieux#camerasFor}, {@link #focus()}, and the {@link BroadcastNetwork#SHOT_POV} / {@link
 * BroadcastNetwork#SHOT_FREE} shots ({@link #moveFree}).
 */
public final class Director {

    private Director() {}

    private static final Gson GSON = new Gson();

    /** An automatic close shot (scorer, spotlight) lasts this long before going back to the wide shot. */
    private static final int AUTO_SCORE_TICKS = 7 * 20, AUTO_SPOTLIGHT_TICKS = 9 * 20;
    /** Spectator broadcast games further than this from the shot are moved closer (blocks). */
    private static final double KEEP_NEAR = 24;
    /** At most this many tiles in a split screen. */
    public static final int MAX_TILES = SplitLayouts.MAX;

    /** Who asks for a shot: the operator (commands, voice, keys), the automatic direction, a sequence (credits, intro). */
    public enum Source { MANUAL, AUTO, SEQUENCE }

    /**
     * A shot to put on air. {@code camera}: camera key ({@link BroadcastNetwork#SHOT_CAMERA}), path name
     * ({@link BroadcastNetwork#SHOT_PATH}) or lieu ({@link BroadcastNetwork#SHOT_MOSAIC}); {@code player} for follow /
     * POV shots; {@code transition} -1 = the default one; {@code effect} "" = none.
     */
    public record ShotRequest(int kind, String camera, String player, int transition, String effect, List<Tile> tiles, Source source) {
        public static ShotRequest camera(CameraSpot s, int transition, String effect, Source source) {
            return new ShotRequest(BroadcastNetwork.SHOT_CAMERA, s.key(), "", transition, effect, List.of(), source);
        }

        public static ShotRequest follow(String player, int transition, String effect, Source source) {
            return new ShotRequest(BroadcastNetwork.SHOT_FOLLOW, "", player, transition, effect, List.of(), source);
        }
    }

    /**
     * The automatic direction: chooses shots on the show's events and every tick while it is on (and no sequence
     * runs). Puts them on air with {@link #take} and {@link Source#AUTO}.
     */
    public interface Planner extends ShowEvents.Listener {
        /** Every server tick while the automatic direction is active. */
        default void tick(MinecraftServer server) {}
    }

    private static Shot shot = Shot.NONE;
    private static int serial;
    private static long shotStartTick;
    private static int defaultTransition = BroadcastNetwork.CUT;
    private static boolean auto, letterbox;
    /** The automatic direction also cuts on who speaks (voice chat), when there is one. */
    private static boolean voiceCuts = true;
    /** Last cut by an operator (tick): the voice leaves them the air for a moment. */
    private static long lastManualTick = Long.MIN_VALUE / 2;
    private static int scoresMode = BroadcastNetwork.SCORES_AUTO;
    private static final Set<UUID> broadcasters = new HashSet<>();
    /** Tick when an automatic close shot goes back to the wide shot (-1 = none pending). */
    private static long autoRevertAt = -1;
    /** The automatic direction is holding the podium shot during the reveal. */
    private static boolean revealHold;
    private static ShowState lastSent;
    private static boolean dirty = true;
    /**
     * Lieu chosen by the operator (null = follow the show), zone selected in the lieu ("" = the lieu's own cameras,
     * null = not chosen: the lieu's own, or its first zone when it has only zone cameras).
     */
    private static String lieuOverride;
    private static String zoneSel;
    private static String lastPhase = CameraSpot.STUDIO;
    private static FreeCamera free = new FreeCamera(0, 0, 0, 0, 0, 0, 0);
    private static Planner planner;

    public static void register() {
        BroadcastNetwork.registerTypes();
        FollowPrefs.register();
        ServerPlayNetworking.registerGlobalReceiver(BroadcastMode.TYPE, (payload, ctx) -> {
            if (payload.on()) broadcasters.add(ctx.player().getUUID());
            else broadcasters.remove(ctx.player().getUUID());
            if (payload.on() && shot.kind() == BroadcastNetwork.SHOT_FREE) ServerPlayNetworking.send(ctx.player(), free);
        });
        ServerPlayNetworking.registerGlobalReceiver(CameraRequest.TYPE, (payload, ctx) -> request(ctx.player(), payload.json()));
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            dirty = true;
            send(handler.getPlayer(), build(server));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            broadcasters.remove(handler.getPlayer().getUUID());
            dirty = true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
        // The round's intro first: it may take the air before the automatic direction reacts to the start
        ShowEvents.register(new MancheIntros.AutoPlay());
        setPlanner(new AutoDirection());
    }

    /** Replaces the automatic direction's planner (the default one cuts to who scores, who is in the spotlight…). */
    public static void setPlanner(Planner p) {
        if (planner != null) ShowEvents.unregister(planner);
        planner = p;
        if (p != null) ShowEvents.register(p);
    }

    private static void reset() {
        Credits.stop(null);
        MancheIntros.stop(null);
        shot = Shot.NONE;
        auto = letterbox = false;
        scoresMode = BroadcastNetwork.SCORES_AUTO;
        defaultTransition = BroadcastNetwork.CUT;
        broadcasters.clear();
        autoRevertAt = -1;
        revealHold = false;
        lastSent = null;
        lieuOverride = null;
        zoneSel = null;
        lastPhase = CameraSpot.STUDIO;
        dirty = true;
    }

    // ---------------------------------------------------------------- state

    public static Shot shot() {
        return shot;
    }

    /** Server tick when the shot on air started. */
    public static long shotStartTick() {
        return shotStartTick;
    }

    public static boolean autoDirection() {
        return auto;
    }

    public static boolean voiceCuts() {
        return voiceCuts;
    }

    public static void setVoiceCuts(boolean on) {
        voiceCuts = on;
    }

    public static int defaultTransition() {
        return defaultTransition;
    }

    public static Set<UUID> broadcasters() {
        return broadcasters;
    }

    /** A sequence (credits, round intro) holds the air: the automatic direction waits. */
    public static boolean sequenceRunning() {
        return !Credits.running().isEmpty() || MancheIntros.running() != null || MancheIntros.launching();
    }

    // ---------------------------------------------------------------- focus

    /**
     * The lieu the show is in: a round's intro → that round; credits or reveal → the studio; a running round that
     * has cameras → that round; else the studio.
     */
    public static String phaseLieu() {
        String intro = MancheIntros.running();
        if (intro != null) return Cameras.list().lieu(intro).isEmpty() ? CameraSpot.STUDIO : intro;
        if (!Credits.running().isEmpty() || Reveal.running()) return CameraSpot.STUDIO;
        CraftMasterData data = CraftMaster.data();
        Manche m = data == null ? null : data.currentManche();
        if (m != null && !Cameras.list().lieu(m.id).isEmpty()) return m.id;
        return CameraSpot.STUDIO;
    }

    /** The lieu the keys address: the operator's choice, else {@link #phaseLieu()}. */
    public static String focusLieu() {
        return lieuOverride != null ? lieuOverride : phaseLieu();
    }

    /**
     * The group the keys 1–9, the wheel, "caméra 2" and the camera item address: the focus lieu, or its selected
     * zone; a round with zone cameras only starts on its first zone.
     */
    public static String focus() {
        String lieu = focusLieu();
        if (CameraSpot.STUDIO.equals(lieu)) return lieu;
        if (zoneSel != null) return CameraSpot.group(lieu, zoneSel);
        CameraList list = Cameras.list();
        if (!list.hasGroup(lieu)) {
            List<String> zones = list.zones(lieu);
            if (!zones.isEmpty()) return CameraSpot.group(lieu, zones.getFirst());
        }
        return lieu;
    }

    /**
     * Sets the focus: {@code lieu} null keeps the lieu, "auto" follows the show again; {@code zone} null keeps the
     * zone, "" = the lieu's own cameras, "auto" = not chosen (the lieu's own, else its first zone), "next" / "prev" =
     * the next / previous zone that has cameras.
     */
    public static void setFocus(MinecraftServer server, String lieu, String zone) {
        if (lieu != null) {
            String l = lieu.equals("auto") ? null : lieu;
            if (l == null ? lieuOverride != null : !l.equals(focusLieu())) zoneSel = null;
            lieuOverride = l;
        }
        if (zone != null) {
            if (zone.equals("next") || zone.equals("prev")) {
                List<String> zones = Cameras.list().zones(focusLieu());
                if (!zones.isEmpty()) {
                    int i = zones.indexOf(CameraSpot.zoneOf(focus()));
                    int n = zones.size();
                    zoneSel = zones.get(((i + (zone.equals("next") ? 1 : -1)) % n + n) % n);
                }
            } else {
                zoneSel = zone.equals("auto") ? null : zone;
            }
        }
        sync(server);
    }

    /** "le studio", "la manche Le Pont", "la zone ensuri (Le Pont)" */
    public static String focusName() {
        String g = focus();
        String l = CameraSpot.lieuOf(g), z = CameraSpot.zoneOf(g);
        if (CameraSpot.STUDIO.equals(l)) return "le studio";
        return z.isEmpty() ? "la manche " + Lieux.lieuName(l) : "la zone " + z + " (" + Lieux.lieuName(l) + ")";
    }

    /** A camera from a reference, relative to the focus (see {@link CameraList#resolve(String, String, Map)}). */
    public static CameraSpot resolve(MinecraftServer server, String ref) {
        return Lieux.resolve(server, ref, focus());
    }

    // ---------------------------------------------------------------- on air

    /** The camera list changed (placed, moved, renamed…): the clients get it, a removed camera leaves the air. */
    public static void camerasChanged(MinecraftServer server) {
        if (shot.kind() == BroadcastNetwork.SHOT_CAMERA && Cameras.list().get(shot.camera()) == null) shot = Shot.NONE;
        sync(server);
    }

    /** The travellings changed: the clients get them; a deleted travelling on air gives way to the wide shot. */
    public static void pathsChanged(MinecraftServer server) {
        if (shot.kind() == BroadcastNetwork.SHOT_PATH && CameraPaths.byName().get(shot.camera()) == null && !wide(server, BroadcastNetwork.CUT))
            shot = Shot.NONE;
        sync(server);
    }

    /** A camera changed its key (moved to another lieu): the shot on air keeps it. */
    public static void cameraMoved(String oldKey, String newKey) {
        if (shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera().equals(oldKey))
            shot = new Shot(shot.kind(), newKey, shot.player(), shot.transition(), shot.serial(), shot.effect(), shot.tiles());
    }

    /**
     * The one way a shot goes on air. An automatic request is ignored while a sequence holds the air; a manual or
     * automatic one stops nothing (a manual cut during the credits is allowed, the credits go on).
     */
    public static boolean take(MinecraftServer server, ShotRequest r) {
        if (r.source() == Source.AUTO && sequenceRunning()) return false;
        if (r.source() == Source.MANUAL) {
            autoRevertAt = -1;
            lastManualTick = server.getTickCount();
        }
        int transition = r.transition() < 0 ? defaultTransition : r.transition();
        shot = new Shot(r.kind(), r.camera(), r.player(), transition, ++serial, r.effect(), r.tiles());
        shotStartTick = server.getTickCount();
        // The dust line of a travelling being set up never goes on air
        if (r.kind() == BroadcastNetwork.SHOT_PATH) fr.lordfinn.craftmaster.camera.PathRecording.hide(r.camera());
        sync(server);
        return true;
    }

    /** Puts camera {@code spot} on air ({@code transition} -1 = the default one). */
    public static void cut(MinecraftServer server, CameraSpot spot, int transition) {
        take(server, ShotRequest.camera(spot, transition, "", Source.SEQUENCE));
    }

    /** Follow shot on a player. */
    public static void follow(MinecraftServer server, String player, int transition) {
        take(server, ShotRequest.follow(player, transition, "", Source.SEQUENCE));
    }

    /** The wide shot of the focus (its "large" camera, else its first; the lieu's, then the studio's). */
    public static CameraSpot wideSpot() {
        CameraList list = Cameras.list();
        String g = focus();
        CameraSpot w = list.group(CameraSpot.lieuOf(g)).wide();
        if (w == null) w = list.group(g).wide();
        if (w == null) w = list.group(CameraSpot.STUDIO).wide();
        return w;
    }

    /** The wide shot (see {@link #wideSpot()}); false if there is no camera at all. */
    public static boolean wide(MinecraftServer server, int transition) {
        CameraSpot w = wideSpot();
        if (w == null) return false;
        if (shot.kind() != BroadcastNetwork.SHOT_CAMERA || !shot.camera().equals(w.key())) cut(server, w, transition);
        return true;
    }

    /**
     * The tiles of a split screen of {@code lieu}: one per zone that has cameras (its wide camera, labelled with the
     * zone's players), or, with fewer than two zones, the cameras of the focus group. At most {@link #MAX_TILES}.
     */
    public static List<Tile> mosaicTiles(String lieu) {
        CameraList list = Cameras.list();
        List<Tile> tiles = new ArrayList<>();
        List<String> zones = new ArrayList<>(list.zones(lieu));
        zones.remove("");
        if (zones.size() >= 2) {
            for (String z : zones) {
                CameraSpot w = list.group(CameraSpot.group(lieu, z)).wide();
                List<String> players = Lieux.playersOf(z);
                if (w != null) tiles.add(new Tile(w.key(), players.isEmpty() ? z.replace('-', ' ') : String.join(" & ", players)));
                if (tiles.size() == MAX_TILES) break;
            }
        } else {
            String g = CameraSpot.lieuOf(focus()).equals(lieu) ? focus() : lieu;
            List<CameraSpot> cams = list.group(g).all();
            if (cams.size() < 2) cams = list.lieu(lieu).all();
            for (CameraSpot s : cams) {
                tiles.add(new Tile(s.key(), s.name()));
                if (tiles.size() == MAX_TILES) break;
            }
        }
        return tiles;
    }

    /** Layout of the next split screens (the directors' choice; {@link SplitLayouts}). */
    private static String splitLayout = SplitLayouts.FRAMES;

    public static String splitLayout() {
        return splitLayout;
    }

    /** The cameras on air: the split screen's (main shot first), the one camera, or none (follow, travelling…). */
    public static List<String> onAirCameras() {
        if (shot.kind() == BroadcastNetwork.SHOT_CAMERA) return List.of(shot.camera());
        if (shot.kind() == BroadcastNetwork.SHOT_MOSAIC) return shot.tiles().stream().map(Tile::camera).toList();
        return List.of();
    }

    /**
     * Puts these cameras on air (the first one is the main shot): two or more as a split screen in {@code layout}
     * (null = the directors' current choice), one as a plain cut. For the automatic direction and sequences too
     * (e.g. one shot per map during a round). Keeps at most {@link SplitLayouts#MAX}; false if there is none.
     */
    public static boolean split(MinecraftServer server, List<CameraSpot> spots, String layout, int transition, String effect, Source source,
                                List<String> lines) {
        List<Tile> tiles = new ArrayList<>();
        for (CameraSpot s : spots) if (s != null) tiles.add(new Tile(s.key(), s.name().isEmpty() ? s.role() : s.name()));
        return splitTiles(server, tiles, layout, transition, effect, source, lines);
    }

    /**
     * Like {@link #split}, with tiles: a camera key, or {@link SplitLayouts#followKey} ({@code "follow:<player>"}) for a
     * tile that follows a player (the broadcast game frames it as a follow shot). The main tile first; 2 to
     * {@link SplitLayouts#MAX} (duplicates, unknown cameras and players not connected are left out); a single one is a
     * plain cut (camera) or follow shot. Labels: the tile's own, else the camera's name or the player's name.
     */
    public static boolean splitTiles(MinecraftServer server, List<Tile> tiles, String layout, int transition, String effect, Source source,
                                     List<String> lines) {
        List<Tile> list = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Tile t : tiles) {
            if (t == null || list.size() == SplitLayouts.MAX || list.stream().anyMatch(o -> o.camera().equals(t.camera()))) continue;
            String player = SplitLayouts.followed(t.camera());
            if (player != null) {
                ServerPlayer p = findPlayer(server, player);
                if (p == null) continue;
                String who = p.getGameProfile().name();
                list.add(new Tile(SplitLayouts.followKey(who), t.label().isEmpty() ? who : t.label()));
                names.add("suivi de " + who);
            } else {
                CameraSpot s = Cameras.list().get(t.camera());
                if (s == null) continue;
                list.add(new Tile(s.key(), !t.label().isEmpty() ? t.label() : s.name().isEmpty() ? s.role() : s.name()));
                names.add(s.label());
            }
        }
        if (list.isEmpty()) return false;
        if (list.size() == 1) {
            String player = SplitLayouts.followed(list.getFirst().camera());
            if (player != null) take(server, ShotRequest.follow(player, transition, effect, source));
            else take(server, ShotRequest.camera(Cameras.list().get(list.getFirst().camera()), transition, effect, source));
            lines.add("À l'antenne : " + (player != null ? "on suit " + player : "caméra " + names.getFirst()) + withEffect(effect) + ".");
            return true;
        }
        String l = layout == null ? splitLayout : SplitLayouts.of(layout);
        // The lieu of the shot: the first camera's (follow tiles only: the focus')
        String lieu = focusLieu();
        for (Tile t : list) {
            CameraSpot s = SplitLayouts.followed(t.camera()) == null ? Cameras.list().get(t.camera()) : null;
            if (s != null) {
                lieu = s.lieu();
                break;
            }
        }
        take(server, new ShotRequest(BroadcastNetwork.SHOT_MOSAIC, lieu == null ? "" : lieu, l, transition, effect, list, source));
        lines.add("À l'antenne : écran partagé en " + SplitLayouts.label(l) + " — " + String.join(", ", names)
                + (l.equals(SplitLayouts.MAIN) ? " (principal : " + names.getFirst() + ")" : "") + withEffect(effect) + ".");
        return true;
    }

    /**
     * Ctrl+click on a shot in the directors' grids ({@code camera.cut} with {@code pair}): a camera not on air joins the
     * split screen (or makes one with the camera on air), a camera on air leaves it. False when there is no camera on
     * air to join (follow shot, travelling) or it is the only one: a plain cut then.
     */
    private static boolean pair(MinecraftServer server, CameraSpot spot, int transition, String effect, List<String> lines) {
        List<String> on = onAirCameras();
        if (on.isEmpty() || on.equals(List.of(spot.key()))) return false;
        Map<String, Object> a = new HashMap<>();
        a.put(on.contains(spot.key()) ? "remove" : "add", spot.key());
        splitAction(server, a, transition, effect, lines);
        return true;
    }

    /** The tile key a reference means: a camera (number, name, key) or "follow:<player>"; null (and a line) if none. */
    private static String tileKey(MinecraftServer server, String ref, List<String> lines) {
        String player = SplitLayouts.followed(ref);
        if (player != null) {
            ServerPlayer p = findPlayer(server, player);
            if (p == null) lines.add("⚠ " + player + " n'est pas connecté : impossible de le suivre.");
            return p == null ? null : SplitLayouts.followKey(p.getGameProfile().name());
        }
        CameraSpot s = resolve(server, ref);
        if (s == null) lines.add("⚠ Aucune caméra « " + ref + " » dans " + focusName() + ".");
        return s == null ? null : s.key();
    }

    /** {@code camera.split} (fields checked by {@link BroadcastActions}). */
    private static void splitAction(MinecraftServer server, Map<String, Object> a, int transition, String effect, List<String> lines) {
        if (a.get("layout") instanceof String l) {
            splitLayout = l;
            sync(server);
        }
        List<String> keys = new ArrayList<>(onAirCameras());
        boolean onlyLayout = true;
        if (a.get("cameras") instanceof List<?> refs) {
            onlyLayout = false;
            keys.clear();
            for (Object r : refs) {
                String k = tileKey(server, String.valueOf(r), lines);
                if (k == null) return;
                if (!keys.contains(k)) keys.add(k);
            }
        }
        for (String field : List.of("remove", "add", "main")) {
            if (!(a.get(field) instanceof String ref)) continue;
            onlyLayout = false;
            String k = tileKey(server, ref, lines);
            if (k == null) return;
            switch (field) {
                case "remove" -> keys.remove(k);
                case "add" -> {
                    if (keys.contains(k)) continue;
                    if (keys.size() >= SplitLayouts.MAX) {
                        lines.add("⚠ " + SplitLayouts.MAX + " plans au plus dans l'écran partagé : retires-en un d'abord.");
                        return;
                    }
                    keys.add(k);
                }
                default -> {
                    keys.remove(k);
                    keys.addFirst(k);
                }
            }
        }
        if (Boolean.FALSE.equals(a.get("enabled"))) {
            onlyLayout = false;
            if (keys.size() > 1) keys = new ArrayList<>(keys.subList(0, 1));
        }
        // Only the layout said, and no split screen on air: it is for the next one
        if (onlyLayout && keys.size() < 2) {
            lines.add("Écran partagé : « " + SplitLayouts.label(splitLayout) + " » pour le prochain.");
            return;
        }
        List<Tile> tiles = new ArrayList<>();
        for (String k : keys) tiles.add(new Tile(k, ""));
        if (!splitTiles(server, tiles, null, transition, effect, Source.MANUAL, lines))
            lines.add("⚠ Aucune caméra à l'antenne : choisis d'abord un plan.");
    }

    /** Moves the free camera ({@link BroadcastNetwork#SHOT_FREE}): sent to the broadcast games, which glide to it. */
    public static void moveFree(MinecraftServer server, FreeCamera pose) {
        free = pose;
        for (UUID id : broadcasters) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && ServerPlayNetworking.canSend(p, FreeCamera.TYPE)) ServerPlayNetworking.send(p, pose);
        }
        fr.lordfinn.craftmaster.replay.Replays.onPayload(server, pose, false);
    }

    /** Sends the state now (next tick) to every client. */
    public static void sync(MinecraftServer server) {
        dirty = true;
    }

    public static void cue(MinecraftServer server, ShowCue cue) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (ServerPlayNetworking.canSend(p, ShowCue.TYPE)) ServerPlayNetworking.send(p, cue);
        fr.lordfinn.craftmaster.replay.Replays.onPayload(server, cue, true);
    }

    private static void send(ServerPlayer p, ShowState state) {
        if (ServerPlayNetworking.canSend(p, ShowState.TYPE)) ServerPlayNetworking.send(p, state);
    }

    /** The show's state as the clients see it: published scores only, nothing secret. */
    public static ShowState build(MinecraftServer server) {
        CraftMasterData data = CraftMaster.data();
        int seasonId = 0, partieId = 0, mancheNumber = 0;
        String seasonName = "", partieName = "", mancheId = "", mancheName = "";
        boolean hidden = false;
        List<Contestant> contestants = new ArrayList<>();
        if (data != null) {
            Season s = data.activeSeason();
            if (s != null) {
                seasonId = s.id;
                seasonName = s.name;
                hidden = s.hidden;
                Partie p = s.activePartie();
                if (p != null) {
                    partieId = p.id;
                    partieName = p.name;
                }
                List<CraftMasterData.Entry> ranking = hidden ? List.of() : data.ranking(s.id, true);
                for (SeasonPlayer sp : s.players.values()) {
                    if (sp.archived) continue;
                    int rank = 0, points = 0;
                    for (int i = 0; i < ranking.size(); i++)
                        if (ranking.get(i).player().equals(sp.name)) {
                            rank = i + 1;
                            points = ranking.get(i).total();
                        }
                    contestants.add(new Contestant(sp.name, points, rank, server.getPlayerList().getPlayerByName(sp.name) != null));
                }
            }
            Manche m = data.currentManche();
            Live.Current cur = data.live().current;
            if (m != null && cur != null) {
                Season cs = data.season(cur.season());
                Partie cp = cs == null ? null : cs.parties.get(cur.partie());
                mancheId = m.id;
                mancheName = m.name;
                mancheNumber = cp == null ? 0 : cp.number(m);
            }
        }
        String timerName = Show.timerName();
        // The strip would give the ranking away during the staged reveal
        int scores = Reveal.running() ? BroadcastNetwork.SCORES_OFF : scoresMode;
        String sequence = MancheIntros.running() != null || MancheIntros.launching() ? "manche" : Credits.running();
        return new ShowState(server.getTickCount(), seasonId, seasonName, hidden, partieId, partieName, mancheId, mancheNumber,
                mancheName, timerName == null ? "" : timerName, Show.timerEndTick(), Show.timerTotalTicks(), contestants,
                Cameras.list().all(), shot, auto, letterbox, scores, master(server), sequence, focus(), CameraPaths.all());
    }

    /** The show's host for the credits: an online organiser who is not a contestant, else "LordFinn". */
    static String master(MinecraftServer server) {
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String name = p.getGameProfile().name();
            if (Permissions.isAdmin(p) && (s == null || s.player(name) == null) && !broadcasters.contains(p.getUUID())) return name;
        }
        return "LordFinn";
    }

    // ---------------------------------------------------------------- tick

    private static void tick(MinecraftServer server) {
        Credits.tick(server);
        MancheIntros.tick(server);
        long now = server.getTickCount();
        // The show moved to another lieu (round started, reveal, credits): the operator's choice is dropped
        String phase = phaseLieu();
        if (!phase.equals(lastPhase)) {
            lastPhase = phase;
            lieuOverride = null;
            zoneSel = null;
            sync(server);
            if (auto && !sequenceRunning() && !revealHold) {
                CameraSpot onAir = shot.kind() == BroadcastNetwork.SHOT_CAMERA ? Cameras.list().get(shot.camera()) : null;
                if (onAir == null || !onAir.lieu().equals(phase)) wide(server, BroadcastNetwork.CROSSFADE);
            }
        }
        if (autoRevertAt >= 0 && now >= autoRevertAt) {
            autoRevertAt = -1;
            if (auto && !sequenceRunning()) wide(server, BroadcastNetwork.CROSSFADE);
        }
        if (revealHold && !Reveal.running()) {
            revealHold = false;
            if (auto) autoRevertAt = now + 60;
        }
        if (auto && !sequenceRunning() && planner != null) {
            try {
                planner.tick(server);
            } catch (RuntimeException e) {
                CraftMaster.LOG.error("[Réalisation] Erreur de la réalisation automatique", e);
            }
        }
        if (now % 10 == 0 || dirty) {
            ShowState state = build(server);
            if (dirty || lastSent == null || !state.unclocked().equals(lastSent.unclocked())) {
                dirty = false;
                lastSent = state;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, state);
                fr.lordfinn.craftmaster.replay.Replays.onPayload(server, state, true);
            }
        }
        if (now % 20 == 0) keepBroadcastersNear(server);
    }

    /**
     * Spectator broadcast games are moved under the shot's position so the server sends them its chunks and
     * entities (their view comes from the camera, not from where they stand). Other games are left alone.
     */
    private static void keepBroadcastersNear(MinecraftServer server) {
        if (broadcasters.isEmpty()) return;
        ServerLevel level = null;
        Vec3 target = null;
        switch (shot.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> {
                CameraSpot s = Cameras.list().get(shot.camera());
                if (s != null) {
                    level = Busts.level(server, s.world());
                    target = new Vec3(s.x(), s.y(), s.z());
                }
            }
            case BroadcastNetwork.SHOT_FOLLOW, BroadcastNetwork.SHOT_POV -> {
                ServerPlayer p = server.getPlayerList().getPlayerByName(shot.player());
                if (p != null) {
                    level = p.level();
                    target = p.position();
                }
            }
            case BroadcastNetwork.SHOT_PATH -> {
                CameraPath path = CameraPaths.get(shot.camera());
                if (path != null && !path.isEmpty()) {
                    CameraPath.Pose pose = path.sample((server.getTickCount() - shotStartTick) / 20.0);
                    level = Busts.level(server, path.world());
                    target = new Vec3(pose.x(), pose.y(), pose.z());
                }
            }
            case BroadcastNetwork.SHOT_MOSAIC -> {
                // Between the tiles: within view distance of all of them in a set of neighbouring rooms
                double x = 0, y = 0, z = 0;
                int n = 0;
                for (Tile t : shot.tiles()) {
                    CameraSpot s = Cameras.list().get(t.camera());
                    if (s == null) continue;
                    if (level == null) level = Busts.level(server, s.world());
                    x += s.x();
                    y += s.y();
                    z += s.z();
                    n++;
                }
                if (n > 0) target = new Vec3(x / n, y / n, z / n);
            }
            case BroadcastNetwork.SHOT_FREE -> {
                level = server.overworld();
                target = new Vec3(free.x(), free.y(), free.z());
            }
            default -> {}
        }
        if (level == null || target == null) return;
        for (UUID id : broadcasters) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.gameMode() != GameType.SPECTATOR) continue;
            if (p.level() == level && p.position().distanceTo(target) < KEEP_NEAR) continue;
            // A few blocks below the lens: out of every shot, chunks and entities loaded all the same
            p.teleportTo(level, target.x, target.y - 4, target.z, Set.of(), p.getYRot(), p.getXRot(), false);
        }
    }

    // ---------------------------------------------------------------- actions

    /** Plays a TV direction action (already checked by {@link BroadcastActions}); writes what happened in {@code lines}. */
    public static void play(MinecraftServer server, Map<String, Object> a, List<String> lines) {
        String type = String.valueOf(a.get("type"));
        int transition = a.get("transition") instanceof Integer t ? t : -1;
        String effect = a.get("effect") instanceof String e ? e : "";
        switch (type) {
            case "camera.cut" -> {
                String ref = String.valueOf(a.get("camera"));
                CameraSpot spot = resolve(server, ref);
                if (spot == null) {
                    lines.add("⚠ Aucune caméra « " + ref + " » dans " + focusName()
                            + (Cameras.list().isEmpty() ? " (aucune caméra posée)." : " (studio:2, ensuri:1… pour une autre)."));
                    return;
                }
                if (Boolean.TRUE.equals(a.get("pair")) && pair(server, spot, transition, effect, lines)) return;
                take(server, ShotRequest.camera(spot, transition, effect, Source.MANUAL));
                lines.add("À l'antenne : caméra " + spot.label() + withEffect(effect) + ".");
            }
            case "camera.follow" -> {
                String name = String.valueOf(a.get("player"));
                ServerPlayer p = findPlayer(server, name);
                if (p == null) {
                    lines.add("⚠ " + name + " n'est pas connecté : impossible de le suivre.");
                    return;
                }
                String who = p.getGameProfile().name();
                // In a round: the keys now address the cameras of their zone
                String zone = Lieux.zoneOf(server, who, focusLieu());
                if (zone != null && !zone.equals(focus())) setFocus(server, null, CameraSpot.zoneOf(zone));
                take(server, ShotRequest.follow(who, transition, effect, Source.MANUAL));
                lines.add("À l'antenne : on suit " + who + withEffect(effect) + (zone != null ? " (zone " + CameraSpot.zoneOf(zone) + ")" : "") + ".");
            }
            case "camera.pov" -> {
                String name = String.valueOf(a.get("player"));
                ServerPlayer p = findPlayer(server, name);
                if (p == null) {
                    lines.add("⚠ " + name + " n'est pas connecté.");
                    return;
                }
                take(server, new ShotRequest(BroadcastNetwork.SHOT_POV, "", p.getGameProfile().name(), transition, effect, List.of(), Source.MANUAL));
                lines.add("À l'antenne : la vue de " + p.getGameProfile().name() + withEffect(effect) + ".");
            }
            case "camera.path" -> {
                String ref = String.valueOf(a.get("path"));
                CameraPath path = CameraPaths.get(ref);
                if (path == null || path.isEmpty()) {
                    lines.add("⚠ " + (path == null ? "Aucun trajet « " + ref + " »" : "Le trajet « " + path.name() + " » n'a aucune étape")
                            + " (/cam trajets pour la liste).");
                    return;
                }
                take(server, new ShotRequest(BroadcastNetwork.SHOT_PATH, path.name(), "", transition, effect, List.of(), Source.MANUAL));
                lines.add("À l'antenne : trajet « " + path.name() + " » (" + CameraPath.seconds(path.duration()) + ")" + withEffect(effect) + ".");
            }
            case "camera.mosaic" -> {
                if (Boolean.FALSE.equals(a.get("enabled"))) {
                    if (shot.kind() == BroadcastNetwork.SHOT_MOSAIC) wide(server, transition < 0 ? BroadcastNetwork.CROSSFADE : transition);
                    lines.add("Écran partagé retiré.");
                    return;
                }
                String lieu = a.get("lieu") instanceof String l ? Lieux.lieu(l) : focusLieu();
                if (lieu == null) {
                    lines.add("⚠ Lieu « " + a.get("lieu") + " » inconnu (studio ou une manche).");
                    return;
                }
                List<Tile> tiles = mosaicTiles(lieu);
                if (tiles.size() < 2) {
                    lines.add("⚠ Il faut au moins deux caméras (ou deux zones avec des caméras) dans " + Lieux.lieuName(lieu) + " pour l'écran partagé.");
                    return;
                }
                if (a.get("layout") instanceof String l) splitLayout = l;
                take(server, new ShotRequest(BroadcastNetwork.SHOT_MOSAIC, lieu, splitLayout, transition, effect, tiles, Source.MANUAL));
                lines.add("À l'antenne : écran partagé (" + tiles.size() + " : " + String.join(", ", tiles.stream().map(Tile::label).toList()) + ").");
            }
            case "camera.split" -> splitAction(server, a, transition, effect, lines);
            case "camera.focus" -> focusAction(server, a, lines);
            case "camera.lieu" -> lieuAction(server, a, lines);
            case "camera.goto" -> {
                String name = String.valueOf(a.get("player"));
                ServerPlayer p = findPlayer(server, name);
                if (p == null) {
                    lines.add("⚠ " + name + " n'est pas connecté.");
                    return;
                }
                String who = p.getGameProfile().name();
                String zone = Lieux.zoneOf(server, who, focusLieu());
                if (zone != null && !zone.equals(focus())) setFocus(server, null, CameraSpot.zoneOf(zone));
                showPlayer(server, p, Source.MANUAL);
                CameraSpot on = shot.kind() == BroadcastNetwork.SHOT_CAMERA ? Cameras.list().get(shot.camera()) : null;
                lines.add("À l'antenne : on va voir " + who + (on != null ? " (caméra " + on.label() + ")" : " (on le suit)") + ".");
            }
            case "camera.stay" -> {
                if (Boolean.FALSE.equals(a.get("enabled")) || a.get("player") == null) {
                    FollowPrefs.stay(server, "");
                    lines.add("La réalisation ne reste plus sur un joueur.");
                    return;
                }
                String name = String.valueOf(a.get("player"));
                ServerPlayer p = findPlayer(server, name);
                if (p == null) {
                    lines.add("⚠ " + name + " n'est pas connecté.");
                    return;
                }
                String who = p.getGameProfile().name();
                FollowPrefs.stay(server, who);
                take(server, ShotRequest.follow(who, BroadcastNetwork.CUT, "", Source.MANUAL));
                lines.add("On reste sur " + who + " (« repasse en auto » pour reprendre).");
            }
            case "camera.framing" -> {
                String size = String.valueOf(a.get("size"));
                int k = switch (size) {
                    case "wider" -> 1;
                    case "tighter" -> -1;
                    default -> 0;
                };
                FollowPrefs.size(server, k);
                lines.add("Cadrage automatique " + (k > 0 ? "plus large." : k < 0 ? "plus serré." : "normal."));
            }
            case "camera.avoid" -> {
                String ref = String.valueOf(a.get("camera"));
                CameraSpot spot = resolve(server, ref);
                if (spot == null) {
                    lines.add("⚠ Aucune caméra « " + ref + " » dans " + focusName() + ".");
                    return;
                }
                boolean on = !Boolean.FALSE.equals(a.get("enabled"));
                FollowPrefs.avoid(server, spot.key(), on);
                if (on && shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera().equals(spot.key()))
                    AutoDirection.mostPlayers(server, BroadcastNetwork.CROSSFADE);
                lines.add((on ? "La réalisation automatique évite la caméra " : "La réalisation automatique peut reprendre la caméra ") + spot.label() + ".");
            }
            case "camera.auto" -> {
                auto = (Boolean) a.get("enabled");
                autoRevertAt = -1;
                // « Repasse en auto »: no player held, framing as it comes (the avoided cameras stay avoided)
                if (auto) FollowPrefs.reset(server);
                if (auto && shot.kind() == BroadcastNetwork.SHOT_NONE) wide(server, BroadcastNetwork.CROSSFADE);
                sync(server);
                lines.add("Réalisation automatique " + (auto ? "activée." : "désactivée."));
            }
            case "camera.voice" -> {
                voiceCuts = (Boolean) a.get("enabled");
                sync(server);
                lines.add("Réalisation automatique sur qui parle : " + (voiceCuts ? "oui." : "non."));
            }
            case "camera.transition" -> {
                defaultTransition = (Integer) a.get("transition");
                lines.add("Transition par défaut : " + BroadcastActions.transitionName(defaultTransition) + ".");
            }
            case "broadcast.lowerthird" -> {
                String player = (String) a.get("player");
                String text = (String) a.get("text");
                String sub = (String) a.getOrDefault("subtitle", "");
                int ticks = (Integer) a.get("seconds") * 20;
                if (player != null) {
                    ServerPlayer p = findPlayer(server, player);
                    if (p != null) player = p.getGameProfile().name();
                }
                cue(server, new ShowCue(Cues.LOWER_THIRD, text != null ? text : player, sub, player == null ? "" : player, ticks));
                lines.add("Synthé : " + (text != null ? text : player) + (sub.isEmpty() ? "" : " — " + sub) + ".");
            }
            case "broadcast.task" -> {
                cue(server, new ShowCue(Cues.TASK, "", "", "", (Integer) a.get("seconds") * 20));
                lines.add("Carte de la tâche à l'écran.");
            }
            case "broadcast.scores" -> {
                String mode = (String) a.get("mode");
                switch (mode) {
                    case "on" -> scoresMode = BroadcastNetwork.SCORES_ON;
                    case "off" -> scoresMode = BroadcastNetwork.SCORES_OFF;
                    case "auto" -> scoresMode = BroadcastNetwork.SCORES_AUTO;
                    default -> cue(server, new ShowCue(Cues.SCORES, "", "", "", (Integer) a.get("seconds") * 20));
                }
                sync(server);
                lines.add(switch (mode) {
                    case "on" -> "Bandeau des scores toujours affiché.";
                    case "off" -> "Bandeau des scores masqué.";
                    case "auto" -> "Bandeau des scores : après chaque publication.";
                    default -> "Bandeau des scores à l'écran.";
                });
            }
            case "broadcast.letterbox" -> {
                letterbox = (Boolean) a.get("enabled");
                sync(server);
                lines.add("Bandes cinéma " + (letterbox ? "affichées." : "retirées."));
            }
            case "show.intro" -> lines.add(Credits.start(server, true));
            case "show.outro" -> lines.add(Credits.start(server, false));
            case "show.stop" -> {
                boolean credits = Credits.stop(server), intro = MancheIntros.stop(server);
                lines.add(credits || intro ? (intro ? "Intro de manche arrêtée." : "Générique arrêté.") : "Aucun générique en cours.");
            }
            // Resolved by show.Actions (it knows the rounds): {manche: id}
            case "intro.play" -> lines.add(MancheIntros.play(server, String.valueOf(a.get("manche")),
                    a.get("from") instanceof Number n ? n.intValue() : 1));
            default -> lines.add("⚠ Action de réalisation inconnue : " + type);
        }
    }

    private static String withEffect(String effect) {
        return effect.isEmpty() ? "" : ", effet « " + effect + " »";
    }

    /** camera.focus {lieu?, zone?, player?}: which lieu / zone the keys address. */
    private static void focusAction(MinecraftServer server, Map<String, Object> a, List<String> lines) {
        String lieuRef = (String) a.get("lieu"), zoneRef = (String) a.get("zone"), player = (String) a.get("player");
        String lieu = null;
        if (lieuRef != null) {
            if (CameraList.fold(lieuRef).equals("auto")) lieu = "auto";
            else {
                lieu = Lieux.lieu(lieuRef);
                if (lieu == null) {
                    // "caméras de l'équipe bleue" said as a lieu: a zone of the focused round
                    String g = Cameras.list().place(lieuRef, focus(), Lieux.places(server, focus()), false);
                    if (g != null && !CameraSpot.zoneOf(g).isEmpty()) {
                        lieu = CameraSpot.lieuOf(g);
                        zoneRef = CameraSpot.zoneOf(g);
                    } else {
                        lines.add("⚠ Lieu « " + lieuRef + " » inconnu : studio ou une manche (id, nom ou surnom).");
                        return;
                    }
                }
            }
        }
        String zone = null;
        if (player != null) {
            ServerPlayer p = findPlayer(server, player);
            String who = p != null ? p.getGameProfile().name() : player;
            String l = lieu == null || lieu.equals("auto") ? focusLieu() : lieu;
            String g = Lieux.zoneOf(server, who, l);
            if (g == null) {
                lines.add("⚠ Aucune caméra pour la zone de " + who + " dans " + Lieux.lieuName(l) + " (/cam lieu <caméra> " + l + " " + who + ").");
                return;
            }
            zone = CameraSpot.zoneOf(g);
        } else if (zoneRef != null) {
            String z = CameraList.fold(zoneRef);
            zone = switch (z) {
                case "next", "suivante", "+" -> "next";
                case "prev", "precedente", "-" -> "prev";
                default -> Lieux.zone(zoneRef);
            };
        } else if (lieu != null) {
            // The lieu alone: its own cameras (or its first zone when it only has zone cameras)
            zone = "auto";
        }
        setFocus(server, lieu, zone);
        String g = focus();
        int n = Cameras.list().group(g).size();
        lines.add("Caméras : " + focusName() + " (" + (n == 0 ? "aucune caméra encore" : n + " caméra" + (n > 1 ? "s" : "") + ", touches 1–" + Math.min(9, maxNumber(g))) + ")"
                + (lieuOverride == null ? "" : " — choisi à la main jusqu'au prochain temps de l'émission") + ".");
        // Saying a zone also shows it: its wide camera on air
        if ((player != null || zoneRef != null) && n > 0 && Boolean.TRUE.equals(a.get("cut"))) {
            CameraSpot w = Cameras.list().group(g).wide();
            take(server, ShotRequest.camera(w, -1, "", Source.MANUAL));
            lines.add("À l'antenne : caméra " + w.label() + ".");
        }
    }

    private static int maxNumber(String group) {
        int max = 1;
        for (CameraSpot s : Cameras.list().group(group).all()) max = Math.max(max, s.number());
        return max;
    }

    /** camera.lieu {camera, lieu, zone?}: moves a camera to the studio or a round (and a zone of it). */
    private static void lieuAction(MinecraftServer server, Map<String, Object> a, List<String> lines) {
        String ref = String.valueOf(a.get("camera"));
        CameraSpot spot = resolve(server, ref);
        if (spot == null) {
            lines.add("⚠ Aucune caméra « " + ref + " » dans " + focusName() + ".");
            return;
        }
        String lieuRef = String.valueOf(a.get("lieu"));
        String lieu = Lieux.lieu(lieuRef);
        if (lieu == null) {
            lines.add("⚠ Lieu « " + lieuRef + " » inconnu : studio ou une manche de la saison (id, nom ou surnom).");
            return;
        }
        String zone = a.get("zone") instanceof String z ? Lieux.zone(z) : "";
        if (CameraSpot.STUDIO.equals(lieu) && !zone.isEmpty()) {
            lines.add("⚠ Le studio n'a pas de zones : une zone appartient à une manche.");
            return;
        }
        String before = spot.label();
        CameraSpot moved = Cameras.move(server, spot.key(), lieu, zone);
        boolean trial = zone.isEmpty() || Lieux.trialZone(zone) != null;
        lines.add("Caméra " + before + " → " + moved.placeName() + " (" + Lieux.lieuName(lieu) + "), numéro " + moved.number() + " : "
                + moved.key() + (trial ? "" : " · « " + zone + " » n'est pas une zone d'épreuve (/craftMasterZone) : étiquette libre") + ".");
    }

    /** Exact name, then name ignoring case, then a cast nickname. */
    static ServerPlayer findPlayer(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) return p;
        for (ServerPlayer q : server.getPlayerList().getPlayers()) if (q.getGameProfile().name().equalsIgnoreCase(name)) return q;
        CraftMasterData data = CraftMaster.data();
        Season s = data == null ? null : data.activeSeason();
        SeasonPlayer sp = s == null ? null : s.player(name);
        return sp == null ? null : server.getPlayerList().getPlayerByName(sp.name);
    }

    /** A broadcast key (1–9, wheel, Tab, 0) or a client command: runs the action if the player may drive the cameras. */
    @SuppressWarnings("unchecked")
    private static void request(ServerPlayer player, String json) {
        MinecraftServer server = player.level().getServer();
        if (!Permissions.canDriveCameras(player)) {
            player.sendOverlayMessage(Component.literal("Piloter les caméras est réservé à la réalisation (permission craftmaster:camera).")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Map<String, Object> action;
        try {
            action = GSON.fromJson(json, Map.class);
        } catch (JsonSyntaxException e) {
            return;
        }
        Object type = action == null ? null : action.get("type");
        if (!(type instanceof String t) || !BroadcastActions.handles(t)) return;
        ActionExecutor.Result r = ActionExecutor.execute(server, List.of(action));
        if (!r.lines().isEmpty())
            player.sendOverlayMessage(Component.literal(r.lines().getLast()).withStyle(r.ok() ? ChatFormatting.GOLD : ChatFormatting.RED));
    }

    /** The players a shot may show: in the world, not spectators, not the broadcast games. */
    static List<ServerPlayer> inSight(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (!p.isSpectator() && !broadcasters.contains(p.getUUID())) out.add(p);
        return out;
    }

    /**
     * On {@code p}: a placed camera that sees them well (the more of the picture they fill the likelier, never one
     * they are on), else the follow shot (its framings change by themselves, and cut to such cameras too).
     */
    static boolean showPlayer(MinecraftServer server, ServerPlayer p, Source source) {
        CameraSpot cam = CameraChoice.showing(List.of(p));
        if (cam != null) return take(server, ShotRequest.camera(cam, BroadcastNetwork.CUT, "", source));
        return take(server, ShotRequest.follow(p.getGameProfile().name(), BroadcastNetwork.CUT, "", source));
    }

    // ---------------------------------------------------------------- automatic direction

    /**
     * The default planner: cuts to whoever scores or is put in the spotlight — in a round, on the nearest camera of
     * their zone when it has some, else the follow shot —, to the podium during the reveal, wide otherwise.
     */
    private static final class AutoDirection implements Planner {

        private static boolean active() {
            return auto && !sequenceRunning();
        }

        // ------------------------------------------------------------ cutting on who speaks

        /** An operator's cut keeps the air this long before the voice cuts again (ticks). */
        private static final long MANUAL_PAUSE = 20 * 20;
        /** A camera frames someone within this distance (blocks) and this part of its field of view. */
        private static final double FRAME_DISTANCE = 14, FRAME_SHARE = 0.42;

        private final SpeakerCuts speech = new SpeakerCuts();
        /** The serial of the last shot the voice put on air (another one = someone else cut). */
        private int voiceSerial = -1;

        @Override
        public void tick(MinecraftServer server) {
            long tick = server.getTickCount();
            if (!voiceCuts || !Speaking.available() || tick % 2 != 0) return;
            // « Reste sur Ensuri »: the voice does not cut away
            if (!FollowPrefs.stay().isEmpty()) return;
            long now = System.currentTimeMillis();
            // Who is heard and can be shown by a camera of what is on (studio, the round's lieu)
            List<String> talking = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator() || broadcasters.contains(p.getUUID())) continue;
                if (Speaking.talking(p.getUUID(), now) && !framing(server, List.of(p)).isEmpty()) talking.add(p.getGameProfile().name());
            }
            if (shot.serial() != voiceSerial && voiceSerial != -2) {
                // Someone else cut (operator, score, sequence): the voice starts again from there
                speech.otherShot(now);
                voiceSerial = -2;
            }
            SpeakerCuts.Decision d = speech.update(now, talking);
            // The air is held: an automatic close shot (score, spotlight), the reveal, an operator a moment ago
            if (d.kind() == SpeakerCuts.Kind.NONE || autoRevertAt >= 0 || revealHold || MancheIntros.launching()
                    || tick - lastManualTick < MANUAL_PAUSE) return;
            boolean taken = switch (d.kind()) {
                case SOLO -> {
                    ServerPlayer p = findPlayer(server, d.who().getFirst());
                    if (p == null) yield false;
                    yield showPlayer(server, p, Source.AUTO);
                }
                case GROUP -> {
                    List<ServerPlayer> ps = new ArrayList<>();
                    for (String n : d.who()) {
                        ServerPlayer p = findPlayer(server, n);
                        if (p != null) ps.add(p);
                    }
                    CameraSpot cam = CameraChoice.showing(ps);
                    yield cam == null ? mostPlayers(server, BroadcastNetwork.CUT)
                            : take(server, ShotRequest.camera(cam, BroadcastNetwork.CUT, "", Source.AUTO));
                }
                case WIDE -> mostPlayers(server, BroadcastNetwork.CROSSFADE);
                default -> false;
            };
            if (taken) {
                speech.aired(now, d);
                voiceSerial = shot.serial();
            }
        }

        /** The silent moments: the camera that sees the most contestants, else the wide shot. */
        static boolean mostPlayers(MinecraftServer server, int transition) {
            CameraSpot cam = CameraChoice.most(inSight(server));
            if (cam == null) {
                CameraSpot w = wideSpot();
                if (w == null || !FollowPrefs.avoided(w.key())) return wide(server, transition);
                // The wide camera is avoided: another one of the focus
                for (CameraSpot s : Cameras.list().group(focus()).all()) if (!FollowPrefs.avoided(s.key())) { cam = s; break; }
                if (cam == null) return false;
            }
            if (shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera().equals(cam.key())) return true;
            return take(server, ShotRequest.camera(cam, transition, "", Source.AUTO));
        }

        /**
         * The cameras of what is on (the studio, or the round's lieu) that frame all these players — close enough,
         * well inside their field of view —, the tightest first (the wide shot comes last).
         */
        private static List<CameraSpot> framing(MinecraftServer server, List<ServerPlayer> players) {
            if (players.isEmpty()) return List.of();
            List<CameraSpot> out = new ArrayList<>();
            for (CameraSpot s : Cameras.list().lieu(phaseLieu()).all()) {
                double fov = s.fov() > 0 ? s.fov() : 70;
                double half = Math.toRadians(fov) * FRAME_SHARE;
                double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
                double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
                boolean all = true;
                for (ServerPlayer p : players) {
                    if (!p.level().dimension().identifier().toString().equals(s.world())) { all = false; break; }
                    double dx = p.getX() - s.x(), dy = p.getEyeY() - 0.2 - s.y(), dz = p.getZ() - s.z();
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < 0.5 || d > FRAME_DISTANCE || Math.acos(Math.max(-1, Math.min(1, (dx * fx + dy * fy + dz * fz) / d))) > half) {
                        all = false;
                        break;
                    }
                }
                if (all) out.add(s);
            }
            // Tightest first: what the frame covers at the players' distance
            out.sort(java.util.Comparator.comparingDouble(s -> {
                double far = 0;
                for (ServerPlayer p : players) far = Math.max(far, Math.sqrt(p.distanceToSqr(s.x(), s.y(), s.z())));
                return far * Math.tan(Math.toRadians(s.fov() > 0 ? s.fov() : 70) / 2);
            }));
            return out;
        }

        /** The close shot on a player: a camera of their zone in the round, else following them. */
        private static void closeOn(MinecraftServer server, String player) {
            // « Reste sur Ensuri »: no cut to someone else
            if (!FollowPrefs.stay().isEmpty() && !FollowPrefs.stay().equalsIgnoreCase(player)) return;
            List<CameraSpot> cams = Lieux.camerasFor(server, player, phaseLieu());
            if (!cams.isEmpty()) take(server, ShotRequest.camera(cams.getFirst(), BroadcastNetwork.CUT, "", Source.AUTO));
            else take(server, ShotRequest.follow(player, BroadcastNetwork.CUT, "", Source.AUTO));
        }

        @Override
        public void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {
            cue(server, new ShowCue(Cues.POINTS, (delta > 0 ? "+" : "") + delta, manche, player, 70));
            if (!active() || revealHold || delta <= 0) return;
            if (server.getPlayerList().getPlayerByName(player) == null) return;
            closeOn(server, player);
            autoRevertAt = server.getTickCount() + AUTO_SCORE_TICKS;
        }

        @Override
        public void onSpotlight(MinecraftServer server, String player, String reason) {
            if (!active() || revealHold) return;
            ServerPlayer p = findPlayer(server, player);
            if (p == null) return;
            String name = p.getGameProfile().name();
            closeOn(server, name);
            String sub = switch (reason == null ? "" : reason) {
                case "envelope" -> "ouvre son enveloppe";
                case "buzzer" -> "a buzzé !";
                default -> reason == null ? "" : reason;
            };
            cue(server, new ShowCue(Cues.LOWER_THIRD, name, sub, name, AUTO_SPOTLIGHT_TICKS - 20));
            autoRevertAt = server.getTickCount() + AUTO_SPOTLIGHT_TICKS;
        }

        /** A contestant opening a sealed envelope (chantier D) is an "all eyes on them" moment. */
        @Override
        public void onEnvelopeOpened(MinecraftServer server, ServerPlayer player, int season, String mancheId) {
            onSpotlight(server, player.getGameProfile().name(), "envelope");
        }

        @Override
        public void onMancheStarted(MinecraftServer server, int season, int number, String name) {
            if (!active()) return;
            autoRevertAt = -1;
            wide(server, BroadcastNetwork.CROSSFADE);
        }

        @Override
        public void onRevealStarted(MinecraftServer server, int season) {
            if (!active()) return;
            autoRevertAt = -1;
            revealHold = true;
            CameraSpot podium = Cameras.list().group(CameraSpot.STUDIO).withRole(CameraSpot.PODIUM);
            if (podium != null) take(server, ShotRequest.camera(podium, BroadcastNetwork.CROSSFADE, "", Source.AUTO));
            else wide(server, BroadcastNetwork.CROSSFADE);
        }

        @Override
        public void onPublished(MinecraftServer server, int season) {
            if (Reveal.running()) return;
            cue(server, new ShowCue(Cues.SCORES, "", "", "", 14 * 20));
        }
    }
}
