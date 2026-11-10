package fr.lordfinn.craftmaster.broadcast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.BroadcastMode;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.CameraRequest;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Contestant;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The TV director (server side, runtime state): which shot is on air, the default transition, the automatic
 * direction, the overlay options; it sends the show state ({@link ShowState}) to every client with the mod and the
 * one-off overlay cues ({@link ShowCue}). Broadcast games in spectator mode are kept near the shot so their chunks
 * and entities are loaded. Everything is driven by the show actions of {@link BroadcastActions} (commands, voice,
 * broadcast keys) and by the show's events (automatic direction).
 */
public final class Director {

    private Director() {}

    private static final Gson GSON = new Gson();

    /** An automatic close shot (scorer, spotlight) lasts this long before going back to the wide shot. */
    private static final int AUTO_SCORE_TICKS = 7 * 20, AUTO_SPOTLIGHT_TICKS = 9 * 20;
    /** Spectator broadcast games further than this from the shot are moved closer (blocks). */
    private static final double KEEP_NEAR = 24;

    private static Shot shot = Shot.NONE;
    private static int serial;
    private static int defaultTransition = BroadcastNetwork.CUT;
    private static boolean auto, letterbox;
    private static int scoresMode = BroadcastNetwork.SCORES_AUTO;
    private static final Set<UUID> broadcasters = new HashSet<>();
    /** Tick when an automatic close shot goes back to the wide shot (-1 = none pending). */
    private static long autoRevertAt = -1;
    /** The automatic direction is holding the podium shot during the reveal. */
    private static boolean revealHold;
    private static ShowState lastSent;
    private static boolean dirty = true;

    public static void register() {
        BroadcastNetwork.registerTypes();
        ServerPlayNetworking.registerGlobalReceiver(BroadcastMode.TYPE, (payload, ctx) -> {
            if (payload.on()) broadcasters.add(ctx.player().getUUID());
            else broadcasters.remove(ctx.player().getUUID());
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
        ShowEvents.register(new AutoDirection());
    }

    private static void reset() {
        Credits.stop(null);
        shot = Shot.NONE;
        auto = letterbox = false;
        scoresMode = BroadcastNetwork.SCORES_AUTO;
        defaultTransition = BroadcastNetwork.CUT;
        broadcasters.clear();
        autoRevertAt = -1;
        revealHold = false;
        lastSent = null;
        dirty = true;
    }

    // ---------------------------------------------------------------- state

    public static Shot shot() {
        return shot;
    }

    public static boolean autoDirection() {
        return auto;
    }

    public static int defaultTransition() {
        return defaultTransition;
    }

    public static Set<UUID> broadcasters() {
        return broadcasters;
    }

    /** The camera list changed (placed, moved, renamed…): the clients get it, a removed camera leaves the air. */
    public static void camerasChanged(MinecraftServer server) {
        if (shot.kind() == BroadcastNetwork.SHOT_CAMERA && Cameras.get(shot.camera()) == null) shot = Shot.NONE;
        sync(server);
    }

    /** Puts camera {@code spot} on air ({@code transition} -1 = the default one). */
    public static void cut(MinecraftServer server, CameraSpot spot, int transition) {
        shot = new Shot(BroadcastNetwork.SHOT_CAMERA, spot.number(), "", transition < 0 ? defaultTransition : transition, ++serial);
        sync(server);
    }

    /** Follow shot on a player. */
    public static void follow(MinecraftServer server, String player, int transition) {
        shot = new Shot(BroadcastNetwork.SHOT_FOLLOW, 0, player, transition < 0 ? defaultTransition : transition, ++serial);
        sync(server);
    }

    /** The wide shot (camera "large", else the first one); false if the studio has no camera. */
    public static boolean wide(MinecraftServer server, int transition) {
        CameraSpot w = Cameras.list().wide();
        if (w == null) return false;
        if (shot.kind() != BroadcastNetwork.SHOT_CAMERA || shot.camera() != w.number()) cut(server, w, transition);
        return true;
    }

    /** Sends the state now (next tick) to every client. */
    public static void sync(MinecraftServer server) {
        dirty = true;
    }

    public static void cue(MinecraftServer server, ShowCue cue) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (ServerPlayNetworking.canSend(p, ShowCue.TYPE)) ServerPlayNetworking.send(p, cue);
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
        return new ShowState(server.getTickCount(), seasonId, seasonName, hidden, partieId, partieName, mancheId, mancheNumber,
                mancheName, timerName == null ? "" : timerName, Show.timerEndTick(), Show.timerTotalTicks(), contestants,
                Cameras.list().all(), shot, auto, letterbox, scores, master(server), Credits.running());
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
        long now = server.getTickCount();
        if (autoRevertAt >= 0 && now >= autoRevertAt) {
            autoRevertAt = -1;
            if (auto && Credits.running().isEmpty()) wide(server, BroadcastNetwork.CROSSFADE);
        }
        if (revealHold && !Reveal.running()) {
            revealHold = false;
            if (auto) autoRevertAt = now + 60;
        }
        if (now % 10 == 0 || dirty) {
            ShowState state = build(server);
            if (dirty || lastSent == null || !state.unclocked().equals(lastSent.unclocked())) {
                dirty = false;
                lastSent = state;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, state);
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
        if (shot.kind() == BroadcastNetwork.SHOT_CAMERA) {
            CameraSpot s = Cameras.get(shot.camera());
            if (s != null) {
                level = Busts.level(server, s.world());
                target = new Vec3(s.x(), s.y(), s.z());
            }
        } else if (shot.kind() == BroadcastNetwork.SHOT_FOLLOW) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(shot.player());
            if (p != null) {
                level = p.level();
                target = p.position();
            }
        }
        if (level == null) return;
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
        switch (type) {
            case "camera.cut" -> {
                String ref = String.valueOf(a.get("camera"));
                CameraSpot spot = Cameras.list().resolve(ref);
                if (spot == null) {
                    lines.add("⚠ Aucune caméra « " + ref + " »" + (Cameras.list().isEmpty() ? " (aucune caméra posée)." : "."));
                    return;
                }
                autoRevertAt = -1;
                cut(server, spot, transition);
                lines.add("À l'antenne : caméra " + spot.label() + ".");
            }
            case "camera.follow" -> {
                String name = String.valueOf(a.get("player"));
                ServerPlayer p = findPlayer(server, name);
                if (p == null) {
                    lines.add("⚠ " + name + " n'est pas connecté : impossible de le suivre.");
                    return;
                }
                autoRevertAt = -1;
                follow(server, p.getGameProfile().name(), transition);
                lines.add("À l'antenne : on suit " + p.getGameProfile().name() + ".");
            }
            case "camera.auto" -> {
                auto = (Boolean) a.get("enabled");
                autoRevertAt = -1;
                if (auto && shot.kind() == BroadcastNetwork.SHOT_NONE) wide(server, BroadcastNetwork.CROSSFADE);
                sync(server);
                lines.add("Réalisation automatique " + (auto ? "activée." : "désactivée."));
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
            case "show.stop" -> lines.add(Credits.stop(server) ? "Générique arrêté." : "Aucun générique en cours.");
            default -> lines.add("⚠ Action de réalisation inconnue : " + type);
        }
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

    /** A broadcast key (1–9, wheel) or a client command: runs the action if the player may drive the cameras. */
    @SuppressWarnings("unchecked")
    private static void request(ServerPlayer player, String json) {
        MinecraftServer server = player.level().getServer();
        if (!Permissions.canDriveCameras(player)) {
            player.sendOverlayMessage(Component.literal("Piloter les caméras est réservé à la réalisation (craftmaster.camera).")
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

    // ---------------------------------------------------------------- automatic direction

    /** Cuts to whoever scores or is put in the spotlight, to the podium during the reveal, wide otherwise. */
    private static final class AutoDirection implements ShowEvents.Listener {

        private static boolean active() {
            return auto && Credits.running().isEmpty();
        }

        @Override
        public void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {
            cue(server, new ShowCue(Cues.POINTS, (delta > 0 ? "+" : "") + delta, manche, player, 70));
            if (!active() || revealHold || delta <= 0) return;
            if (server.getPlayerList().getPlayerByName(player) == null) return;
            follow(server, player, BroadcastNetwork.CUT);
            autoRevertAt = server.getTickCount() + AUTO_SCORE_TICKS;
        }

        @Override
        public void onSpotlight(MinecraftServer server, String player, String reason) {
            if (!active() || revealHold) return;
            ServerPlayer p = findPlayer(server, player);
            if (p == null) return;
            String name = p.getGameProfile().name();
            follow(server, name, BroadcastNetwork.CUT);
            String sub = switch (reason == null ? "" : reason) {
                case "envelope" -> "ouvre son enveloppe";
                case "buzzer" -> "a buzzé !";
                default -> reason == null ? "" : reason;
            };
            cue(server, new ShowCue(Cues.LOWER_THIRD, name, sub, name, AUTO_SPOTLIGHT_TICKS - 20));
            autoRevertAt = server.getTickCount() + AUTO_SPOTLIGHT_TICKS;
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
            CameraSpot podium = Cameras.list().withRole(CameraSpot.PODIUM);
            if (podium != null) cut(server, podium, BroadcastNetwork.CROSSFADE);
            else wide(server, BroadcastNetwork.CROSSFADE);
        }

        @Override
        public void onPublished(MinecraftServer server, int season) {
            if (Reveal.running()) return;
            cue(server, new ShowCue(Cues.SCORES, "", "", "", 14 * 20));
        }
    }
}
