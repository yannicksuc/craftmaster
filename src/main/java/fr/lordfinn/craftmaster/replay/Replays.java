package fr.lordfinn.craftmaster.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.data.Spot;
import fr.lordfinn.craftmaster.show.ShowEvents;
import fr.lordfinn.craftmaster.trial.TrialZones;
import fr.lordfinn.craftmaster.trial.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Replays of the show with Server Replay (optional): every round ("manche"), or every game ("partie"), is recorded
 * from its start to its end, one file per contestant (what their game received: their view, their HUD) and one
 * file for the round's area (fixed chunks around its cameras, zones and teleport points: every player in it, seen
 * from anywhere). Files: Server Replay's folders, one sub-folder per recording named by {@link ReplayArea#folder}.
 *
 * <p>What CraftMaster adds to the files: the show's state (shot on air, cameras, timer, scores…) at the start of
 * each file and whenever it changes, the overlay cues and the free camera, in the area's file (Server Replay's
 * chunk recorder only gets what is sent to everyone) and in the players' files when their game has no CraftMaster;
 * and markers on the timeline for the show's events (round start and end, points, envelopes, buzzers, reveal…).
 * An index of the recordings is kept in {@code <recordings>/craftmaster.json}.
 *
 * <p>Without Server Replay nothing here does anything ({@link ServerReplayBridge#available()}).
 */
public final class Replays {

    private Replays() {}

    /** When to record: each round, each game (from its first round until the game changes or ends), never. */
    public static final String MANCHE = "manche", PARTIE = "partie", OFF = "off";

    /** Settings, {@code config/craftmaster/replays.json}. */
    public static final class Settings {
        public String mode = MANCHE;
        /**
         * A whole file per contestant. Off by default: the épreuve's zones are recorded, and a contestant who leaves
         * every zone gets a file of their own until they are back (the safety net).
         */
        public boolean players = false;
        /** One file per zone of the épreuve. */
        public boolean zone = true;
        /** Chunks added around the area, and the widest area (chunks). */
        public int margin = 2, maxSpan = 40;
        /**
         * File format: "flashback" (default: pieces with a snapshot each, seek both ways, at most 10 s lost in a
         * crash), "replaymod", or "" (Server Replay's default_encoding).
         */
        public String format = "flashback";
        /**
         * Flashback pieces (seconds, a multiple of 10, 10 to 300): the most a crash can take from a file. Each piece
         * starts with a snapshot of the world: measured, the zone costs ~15 MB/h at 10 s; a player file ~50 MB/h
         * at 10 s (and its chunk cache grows fast while recording), hence 60 s by default for them.
         */
        public int zonePieceSeconds = 10, playerPieceSeconds = 10;
        /** Replays older than this many days are deleted (0 = kept). */
        public int keepDays = 0;
        /** CraftMaster's replays never take more than this (GB, 0 = no limit): the oldest go first. */
        public double maxGb = 0;
        /** Below this much free space (GB) on the recordings' disk, the organisers are warned. */
        public double minFreeGb = 5;
    }

    /**
     * One file of the recording: a zone of the épreuve (fixed chunks, everyone in them) or a player (what their game
     * receives: their view, their HUD). A player file is either for the whole recording ({@code players} setting) or
     * an "outing": started when the player left every recorded zone, stopped 10 s after they came back.
     */
    private static final class Rec {
        final boolean zone, outing;
        /** The zone's name ("zone" when deduced), or the player's name. */
        final String name;
        /** Its folder, under Server Replay's chunks/ or players/ folder. */
        final String folder;
        final ReplayArea.Chunks area;
        final UUID player;
        /** Seconds into the recording when it started (outings start later). */
        final long fromSeconds;
        /** Its file's name without extension (Server Replay names it after its start), once known. */
        String file;
        ServerReplayBridge.Recorder rec;
        boolean needsState = true;
        /** An outing: back inside a zone since (ms), -1 while outside. */
        long insideSince = -1;
        Double tick0;
        final List<long[]> clock = new ArrayList<>();
        /**
         * When in the 10 s its pieces are cut (ticks): each piece starts with a snapshot of the world, done on the
         * server's thread. Measured: 30 to 130 ms when two zones and a player cut together (over a tick's 50 ms);
         * spread, 17 to 25 ms for a zone, 28 to 46 ms for a player, never two in the same tick.
         */
        int phase;
        /** A trial zone's corner (block), the same point of each cloned arena; null for a deduced area. */
        int[] origin;

        Rec(boolean zone, boolean outing, String name, String folder, ReplayArea.Chunks area, UUID player, long fromSeconds,
            ServerReplayBridge.Recorder rec) {
            this.zone = zone;
            this.outing = outing;
            this.name = name;
            this.folder = folder;
            this.area = area;
            this.player = player;
            this.fromSeconds = fromSeconds;
            this.rec = rec;
            Path loc = ServerReplayBridge.location(rec);
            if (loc != null) {
                String n = loc.getFileName().toString();
                this.file = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
            }
        }

        boolean active() {
            return rec != null;
        }

        /** In its chunks (zones). */
        boolean contains(String world, double x, double z) {
            if (area == null || !area.world().equals(world)) return false;
            int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
            return cx >= area.fromX() && cx <= area.toX() && cz >= area.fromZ() && cz <= area.toZ();
        }
    }

    /** What is being recorded now. */
    private static final class Session {
        final String key, label;
        final int season, partie, number;
        final String mancheId;
        final long startedAt = System.currentTimeMillis();
        /** Every file of this recording, the stopped ones too (the index lists them all). */
        final List<Rec> recs = new ArrayList<>();
        /** Its part (2, 3…: restarted after a server restart or a reprise) and the recording it follows. */
        int part = 1;
        String follows;
        /** Round running at the last tick (to mark its end in a game's file). */
        String lastManche;
        final JsonArray markers = new JsonArray();

        Session(String key, String label, int season, int partie, int number, String mancheId) {
            this.key = key;
            this.label = label;
            this.season = season;
            this.partie = partie;
            this.number = number;
            this.mancheId = mancheId;
        }

        double seconds() {
            return (System.currentTimeMillis() - startedAt) / 1000.0;
        }

        List<Rec> active() {
            return recs.stream().filter(Rec::active).toList();
        }

        List<Rec> zones() {
            return recs.stream().filter(r -> r.zone).toList();
        }

        /** The player's file being recorded now (whole or outing), or null. */
        Rec playerRec(UUID id) {
            for (Rec r : recs) if (!r.zone && r.active() && id.equals(r.player)) return r;
            return null;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings settings = new Settings();
    private static Path settingsFile;
    private static Session session;
    /** Stopped by hand: not restarted automatically until the round (or game) changes. */
    private static String suppressed;
    /** Last game recorded in "partie" mode, kept going between its rounds. */
    private static String partieKey;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("craftmaster/replays.json")));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop(server, "arrêt du serveur"));
        ServerTickEvents.END_SERVER_TICK.register(Replays::tick);
        ShowEvents.register(new Markers());
        ReplayMoments.register();
    }

    public static Settings settings() {
        return settings;
    }

    public static boolean available() {
        return ServerReplayBridge.available();
    }

    public static boolean recording() {
        return session != null;
    }

    /** The season being recorded (0 when nothing is). */
    static int recordingSeason() {
        Session ses = session;
        return ses == null ? 0 : ses.season;
    }

    /** A (non archived) contestant of the season being recorded. */
    static boolean isContestant(String name) {
        Session ses = session;
        Season s = ses == null || CraftMaster.data() == null ? null : CraftMaster.data().season(ses.season);
        if (s == null) return false;
        for (SeasonPlayer sp : s.players.values()) if (!sp.archived && sp.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    // ---------------------------------------------------------------- settings

    private static void load(Path file) {
        settingsFile = file;
        settings = new Settings();
        if (Files.exists(file)) {
            try {
                Settings s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
                if (s != null) settings = s;
            } catch (IOException | RuntimeException e) {
                CraftMaster.LOG.warn("[Replay] {} illisible, réglages par défaut : {}", file, e.toString());
            }
        }
        if (!List.of(MANCHE, PARTIE, OFF).contains(settings.mode)) settings.mode = MANCHE;
        if (settings.format == null) settings.format = "";
        ServerReplayBridge.setFormat(settings.format);
        markInterrupted();
        ReplayDisk.autoPurge();
    }

    public static void save() {
        if (settingsFile == null) return;
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Replay] Réglages non enregistrés : {}", e.toString());
        }
    }

    // ---------------------------------------------------------------- automatic start and stop

    /** The server of the last tick (for the markers called without one). */
    private static MinecraftServer lastServer;

    private static void tick(MinecraftServer server) {
        lastServer = server;
        if (!available()) return;
        CraftMasterData data = CraftMaster.data();
        if (data == null) return;
        String want = OFF.equals(settings.mode) ? (session != null && session.key.startsWith("main:") ? session.key : null) : wantedKey(data);
        if (want != null && want.equals(suppressed)) want = null;
        else if (suppressed != null && !suppressed.equals(want)) suppressed = null;
        if (session != null && !session.key.equals(want)) stop(server, "fin");
        if (session == null && want != null) start(server, data, want);
        if (session == null) return;
        // A round of the game being recorded ends ("partie" mode; in "manche" mode its file ends with it)
        String current = data.live().current == null ? null : data.live().current.manche();
        if (session.number == 0 && session.lastManche != null && !session.lastManche.equals(current)) marker("Fin de la manche", 0xF2C14E, ReplayMoments.ROUND, null);
        session.lastManche = current;

        // Once a second: players joining (whole files), players leaving every zone (outings)
        if (server.getTickCount() % 20 == 0) {
            addPlayers(server, data);
            safetyNet(server, data);
            ReplayMoments.everySecond(server);
        }
        // The show's state (and the boss bars already shown) go first into every new file
        ShowState state = null;
        for (Rec r : session.active()) {
            if (!r.needsState || !ServerReplayBridge.ready(r.rec)) continue;
            if (state == null) state = Director.build(server);
            ServerReplayBridge.record(r.rec, new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(state));
            if (!r.zone) addBossBars(server, r);
            r.needsState = false;
            noteTick0(server, r);
        }
        if (server.getTickCount() % PIECE_TICKS == 0) everyTenSeconds(server, session);
        // Pieces: each file at its own moment of the 10 s
        long tick = server.getTickCount();
        for (Rec r : session.active())
            if (!r.needsState && due(tick - r.phase, r.zone ? settings.zonePieceSeconds : settings.playerPieceSeconds)) {
                ServerReplayBridge.cutPiece(r.rec);
            }
    }

    /** Ten seconds, in ticks: the longest a crash can take from a Flashback recording, and the clock samples' step. */
    static final int PIECE_TICKS = 200;

    /**
     * Every 10 s: each Flashback file closes its piece (written to the disk, with a snapshot to jump to) instead of
     * every 5 min; each file's clock is sampled against the server's ticks; the index is rewritten.
     */
    private static void everyTenSeconds(MinecraftServer server, Session ses) {
        long tick = server.getTickCount();
        for (Rec r : ses.active()) if (!r.needsState) sample(r, tick);
        writeIndex(ses, true);
        // The disk, every 5 min of recording
        if (tick % (PIECE_TICKS * 30) == 0) {
            String warn = ReplayDisk.lowSpaceWarning();
            if (warn != null) notifyAdmins(server, warn);
        }
    }

    /** A piece of {@code seconds} ends at this tick (300 s and more: Arcade's own 5 min). */
    private static boolean due(long tick, int seconds) {
        if (seconds >= 300) return false;
        int every = Math.max(1, Math.round(seconds / 10f)) * PIECE_TICKS;
        return tick % every == 0;
    }

    private static void sample(Rec r, long tick) {
        long ms = ServerReplayBridge.recordingMs(r.rec);
        if (ms >= 0) r.clock.add(new long[]{ms, tick});
    }

    /**
     * The boss bars this player sees already (round timer, stopwatches) go into their new file: it has their later
     * updates but not their creation, which makes a game watching the file stop with an error.
     */
    private static void addBossBars(MinecraftServer server, Rec r) {
        ServerPlayer p = server.getPlayerList().getPlayer(r.player);
        if (p == null) return;
        List<net.minecraft.server.level.ServerBossEvent> bars = new ArrayList<>(fr.lordfinn.craftmaster.trial.Judging.bossBars());
        if (fr.lordfinn.craftmaster.show.Show.timerBar() != null) bars.add(fr.lordfinn.craftmaster.show.Show.timerBar());
        for (net.minecraft.server.level.ServerBossEvent b : bars)
            if (b.isVisible() && b.getPlayers().contains(p))
                ServerReplayBridge.record(r.rec, net.minecraft.network.protocol.game.ClientboundBossEventPacket.createAddPacket(b));
    }

    /**
     * Notes which server tick the file's time 0 is: the files of one recording start a few tenths of a second
     * apart, and the replay's timer and the switch from one file to another line up on the server's ticks.
     */
    private static void noteTick0(MinecraftServer server, Rec r) {
        long ms = ServerReplayBridge.recordingMs(r.rec);
        if (ms >= 0) {
            r.tick0 = server.getTickCount() - ms / 50.0;
            r.clock.add(new long[]{ms, server.getTickCount()});
        }
    }

    /** The recording the show calls for now, or null: "s1:p3:pont" (round) or "s1:p3" (game). */
    private static String wantedKey(CraftMasterData data) {
        Live.Current cur = data.live().current;
        if (MANCHE.equals(settings.mode)) {
            partieKey = null;
            return cur == null ? null : "s" + cur.season() + ":p" + cur.partie() + ":" + cur.manche();
        }
        if (cur != null) return partieKey = "s" + cur.season() + ":p" + cur.partie();
        // Between two rounds of the game: keep going while it is the active game and not finished
        if (partieKey == null) return null;
        String[] k = partieKey.split(":");
        Season s = data.season(Integer.parseInt(k[0].substring(1)));
        Partie p = s == null ? null : s.parties.get(Integer.parseInt(k[1].substring(1)));
        if (p == null || p.archived || Partie.TERMINEE.equals(p.status) || !Integer.valueOf(p.id).equals(s.activePartie)) return partieKey = null;
        return partieKey;
    }

    /** Starts by hand (also with the mode "off"): the current round, else the active game. */
    public static String startNow(MinecraftServer server) {
        if (!available()) return "Server Replay n'est pas installé sur ce serveur.";
        if (session != null) return "Déjà en cours : " + session.label + ".";
        CraftMasterData data = CraftMaster.data();
        Live.Current cur = data.live().current;
        String key;
        if (cur != null) key = MANCHE.equals(settings.mode) ? "s" + cur.season() + ":p" + cur.partie() + ":" + cur.manche() : "s" + cur.season() + ":p" + cur.partie();
        else {
            Season s = data.activeSeason();
            Partie p = s == null ? null : s.activePartie();
            if (p == null) return "Aucune manche en cours ni partie active.";
            key = "s" + s.id + ":p" + p.id;
        }
        suppressed = null;
        if (OFF.equals(settings.mode)) key = "main:" + key;
        else if (PARTIE.equals(settings.mode) || cur == null) partieKey = key;
        start(server, data, key);
        return session == null ? "Rien n'a pu démarrer (voir le journal du serveur)." : "Enregistrement : " + session.label + ".";
    }

    /** Stops by hand; not restarted automatically before the next round (or game). */
    public static String stopNow(MinecraftServer server) {
        if (session == null) return "Aucun enregistrement CraftMaster en cours.";
        String label = session.label;
        suppressed = session.key;
        stop(server, "arrêt manuel");
        return "Enregistrement arrêté et sauvegardé : " + label + ".";
    }

    private static void start(MinecraftServer server, CraftMasterData data, String key) {
        String k = key.startsWith("main:") ? key.substring(5) : key;
        String[] parts = k.split(":", 3);
        int seasonId = Integer.parseInt(parts[0].substring(1)), partieId = Integer.parseInt(parts[1].substring(1));
        Season s = data.season(seasonId);
        Partie p = s == null ? null : s.parties.get(partieId);
        if (p == null) return;
        Manche m = parts.length > 2 ? p.manche(parts[2]) : null;
        int number = m == null ? 0 : p.number(m);
        String label = "saison " + seasonId + ", " + p.name + (m == null ? "" : ", manche " + number + " « " + m.name + " »");
        // The same round recorded before (stopped by a restart, or emptied): this one is its next part
        JsonObject before = lastOf(key);
        int part = before == null ? 1 : (before.has("part") ? before.get("part").getAsInt() : 1) + 1;
        if (part > 1) label += " (partie " + part + ")";
        Session ses = new Session(key, label, seasonId, partieId, number, m == null ? "" : m.id);
        ses.part = part;
        ses.follows = before == null ? null : before.get("id").getAsString();
        session = ses;
        try {
            if (settings.zone) startZones(server, ses, s, p, m);
            if (settings.players) addPlayers(server, data);
        } catch (RuntimeException e) {
            CraftMaster.LOG.error("[Replay] Démarrage de l'enregistrement « {} »", label, e);
        }
        if (ses.recs.isEmpty()) CraftMaster.LOG.warn("[Replay] Rien à enregistrer pour {}", label);
        ReplayMoments.reset();
        marker(m == null ? "Partie : " + p.name : "Manche " + number + " — " + m.name, 0xF2C14E, ReplayMoments.ROUND, null);
        // A game recording started during one of its rounds: that round's marker too
        Live.Current cur = data.live().current;
        Manche now = m == null && cur != null && cur.partie() == partieId ? p.manche(cur.manche()) : null;
        if (now != null) marker("Manche " + p.number(now) + " — " + now.name, 0xF2C14E, ReplayMoments.ROUND, null);
        List<String> zoneNames = ses.zones().stream().map(r -> r.name + " " + r.area.describe()).toList();
        long players = ses.recs.stream().filter(r -> !r.zone).count();
        CraftMaster.LOG.info("[Replay] Enregistrement de {} : zone(s) {}, {} joueur(s)", label, zoneNames.isEmpty() ? "aucune" : zoneNames, players);
        writeIndex(ses, true);
        String low = ReplayDisk.lowSpaceWarning();
        if (low != null) notifyAdmins(server, low);
        notifyAdmins(server, "● Replay : enregistrement de " + label + " (" + (ses.zones().isEmpty() ? "pas de zone"
                : ses.zones().size() + " zone(s)") + (players > 0 ? ", " + players + " joueur(s)" : "") + ").");
    }

    /**
     * The épreuve's zones: those given to the round ({@code /cm manche zones}), else the trial zones its cameras film
     * ({@code /cm cam lieu … <zone>}); one recording each. Without any: one area deduced from its cameras and teleport
     * points (or around its players), named "zone".
     */
    static List<Zone> zonesOf(Manche m) {
        List<Zone> out = new ArrayList<>();
        for (String n : m.zones) {
            Zone z = TrialZones.zones().get(n);
            if (z != null && !out.contains(z)) out.add(z);
        }
        if (!out.isEmpty()) return out;
        for (CameraSpot c : Cameras.list().lieu(m.id).all()) {
            Zone z = c.zone().isEmpty() ? null : TrialZones.zones().get(c.zone());
            if (z != null && !out.contains(z)) out.add(z);
        }
        return out;
    }

    private static void startZones(MinecraftServer server, Session ses, Season s, Partie p, Manche m) {
        List<Manche> manches = m != null ? List.of(m) : p.activeManches();
        List<Zone> zones = new ArrayList<>();
        for (Manche r : manches) for (Zone z : zonesOf(r)) if (!zones.contains(z)) zones.add(z);
        for (Zone z : zones) {
            List<ReplayArea.Box> boxes = new ArrayList<>();
            boxes.add(new ReplayArea.Box(z.world(), z.x0(), z.z0(), z.x1(), z.z1()));
            Vec3 camera = null;
            for (Manche r : manches)
                for (CameraSpot c : Cameras.list().lieu(r.id).all()) {
                    if (!c.zone().equals(z.name()) || !c.world().equals(z.world())) continue;
                    boxes.add(ReplayArea.Box.point(c.world(), c.x(), c.z()));
                    if (camera == null && (CameraSpot.WIDE.equals(c.role()) || c.number() == 1)) camera = new Vec3(c.x(), c.y(), c.z());
                }
            if (camera == null) camera = new Vec3((z.x0() + z.x1()) / 2.0, z.y1() + 2, (z.z0() + z.z1()) / 2.0);
            Rec r = startZone(server, ses, z.name(), ReplayArea.of(boxes, settings.margin, settings.maxSpan), camera);
            // Its corner: cloned arenas, one per contestant, have their free camera at the same place in each
            if (r != null) r.origin = new int[]{z.x0(), z.y0(), z.z0()};
        }
        if (!zones.isEmpty()) return;
        // No zone given: one area around the round's arenas (reference and copies), cameras and teleport points (else
        // its players): a round played in arenas is recorded even before anyone is back (server restart)
        List<ReplayArea.Box> boxes = new ArrayList<>();
        Vec3 camera = null;
        for (Manche r : manches)
            for (fr.lordfinn.craftmaster.trial.kit.Arena a : fr.lordfinn.craftmaster.trial.kit.TrialRun.arenas(r.id)) {
                boxes.add(new ReplayArea.Box(a.world, a.x0, a.z0, a.x1, a.z1));
                for (int[] o : a.liveCopies().values()) boxes.add(new ReplayArea.Box(a.world, a.x0 + o[0], a.z0 + o[2], a.x1 + o[0], a.z1 + o[2]));
                if (camera == null) camera = new Vec3((a.x0 + a.x1) / 2.0, a.y1 + 4, (a.z0 + a.z1) / 2.0);
            }
        for (Manche r : manches) {
            for (CameraSpot c : Cameras.list().lieu(r.id).all()) {
                boxes.add(ReplayArea.Box.point(c.world(), c.x(), c.z()));
                if (camera == null && (CameraSpot.WIDE.equals(c.role()) || c.number() == 1)) camera = new Vec3(c.x(), c.y(), c.z());
            }
            for (Spot t : r.tp) boxes.add(ReplayArea.Box.point(t.world(), t.x(), t.z()));
        }
        if (boxes.isEmpty())
            for (ServerPlayer pl : cast(server, s))
                boxes.add(ReplayArea.Box.point(pl.level().dimension().identifier().toString(), pl.getX(), pl.getZ()));
        startZone(server, ses, "zone", ReplayArea.of(boxes, settings.margin, settings.maxSpan), camera);
    }

    private static Rec startZone(MinecraftServer server, Session ses, String name, ReplayArea.Chunks area, Vec3 camera) {
        if (area == null) return null;
        ServerLevel level = Busts.level(server, area.world());
        if (level == null) {
            CraftMaster.LOG.warn("[Replay] Monde « {} » introuvable : pas d'enregistrement de la zone {}", area.world(), name);
            return null;
        }
        String folder = ReplayArea.folder(ses.season, ses.partie, ses.number, ses.mancheId, name.equals("zone") ? null : "zone-" + name);
        if (ServerReplayBridge.hasChunkRecorder(folder)) {
            CraftMaster.LOG.warn("[Replay] Une zone « {} » s'enregistre déjà", folder);
            return null;
        }
        ServerReplayBridge.Recorder rec = ServerReplayBridge.startChunks(level, new ChunkPos(area.fromX(), area.fromZ()),
                new ChunkPos(area.toX(), area.toZ()), ServerReplayBridge.chunkFolder().resolve(folder), folder, camera);
        if (rec == null) return null;
        Rec r = new Rec(true, false, name, folder, area, null, Math.round(ses.seconds()), rec);
        add(ses, r);
        return r;
    }

    /** Adds a file, its pieces cut at a moment of the 10 s the others leave free (as far as possible). */
    private static void add(Session ses, Rec r) {
        int used = (int) ses.recs.stream().filter(Rec::active).count();
        // 0, 100, 50, 150, 25, 125, 75, 175, ... ticks: the cuts as far apart as the number of files allows
        int slot = Integer.reverse(used) >>> 24;
        r.phase = slot * PIECE_TICKS / 256;
        ses.recs.add(r);
    }

    private static List<ServerPlayer> cast(MinecraftServer server, Season s) {
        List<ServerPlayer> out = new ArrayList<>();
        if (s == null) return out;
        for (SeasonPlayer sp : s.players.values()) {
            if (sp.archived) continue;
            ServerPlayer pl = server.getPlayerList().getPlayerByName(sp.name);
            if (pl != null) out.add(pl);
        }
        return out;
    }

    /** Recorders Server Replay closed itself (the player left): stopped here too. */
    private static void forgetClosed(MinecraftServer server, Session ses) {
        for (Rec r : ses.recs) {
            if (r.zone || !r.active()) continue;
            ServerPlayer pl = server.getPlayerList().getPlayer(r.player);
            if (pl == null || !ServerReplayBridge.isRecording(pl)) r.rec = null;
        }
    }

    /** {@code players} setting: a whole file per contestant (a new one when they come back). */
    private static void addPlayers(MinecraftServer server, CraftMasterData data) {
        Session ses = session;
        if (ses == null) return;
        forgetClosed(server, ses);
        if (!settings.players) return;
        for (ServerPlayer pl : cast(server, data.season(ses.season))) {
            if (ses.playerRec(pl.getUUID()) != null) continue;
            // Already recorded by someone else (/replay start players): left alone
            if (ServerReplayBridge.isRecording(pl)) continue;
            startPlayer(ses, pl, false);
        }
    }

    private static Rec startPlayer(Session ses, ServerPlayer pl, boolean outing) {
        String name = pl.getGameProfile().name();
        String folder = ReplayArea.folder(ses.season, ses.partie, ses.number, ses.mancheId, name);
        try {
            ServerReplayBridge.Recorder rec = ServerReplayBridge.startPlayer(pl, ServerReplayBridge.playerFolder().resolve(folder));
            if (rec == null) return null;
            Rec r = new Rec(false, outing, name, folder, null, pl.getUUID(), Math.round(ses.seconds()), rec);
            add(ses, r);
            return r;
        } catch (RuntimeException e) {
            CraftMaster.LOG.error("[Replay] Enregistrement de {} impossible", name, e);
            return null;
        }
    }

    /** Back inside a zone this long before an outing's file stops. */
    private static final long BACK_MS = 10_000;

    /**
     * The safety net: a contestant in none of the recorded zones is recorded on their own (their view) until they
     * have been back inside for 10 s; leaving and coming back are markers on the timeline. Not needed when every
     * contestant has a whole file anyway.
     */
    private static void safetyNet(MinecraftServer server, CraftMasterData data) {
        Session ses = session;
        if (ses == null || settings.players) return;
        List<Rec> zones = ses.zones().stream().filter(Rec::active).toList();
        if (zones.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer pl : cast(server, data.season(ses.season))) {
            if (pl.isSpectator()) continue;
            String world = pl.level().dimension().identifier().toString();
            boolean inside = zones.stream().anyMatch(z -> z.contains(world, pl.getX(), pl.getZ()));
            Rec out = ses.playerRec(pl.getUUID());
            String name = pl.getGameProfile().name();
            if (!inside) {
                if (out == null && !ServerReplayBridge.isRecording(pl)) {
                    if (startPlayer(ses, pl, true) != null) marker(name + " hors zone", ReplayMoments.colorOf(name), ReplayMoments.OUTING, name);
                } else if (out != null) out.insideSince = -1;
            } else if (out != null && out.outing) {
                if (out.insideSince < 0) out.insideSince = now;
                else if (now - out.insideSince >= BACK_MS) {
                    marker(name + " revient dans la zone", ReplayMoments.colorOf(name), ReplayMoments.BACK, name);
                    ServerReplayBridge.stop(out.rec);
                    out.rec = null;
                }
            }
        }
    }

    private static void logDrift(MinecraftServer server, Rec r) {
        long ms = ServerReplayBridge.recordingMs(r.rec);
        if (r.tick0 == null || ms < 0) return;
        double ticksMs = (server.getTickCount() - r.tick0) * 50;
        CraftMaster.LOG.info("[Replay] {} : {} s de fichier, {} s de ticks, écart {} ms", r.folder,
                ms / 1000, Math.round(ticksMs / 1000), Math.round(ticksMs - ms));
    }

    /** The round recording that ended last: what « Revoir le replay » plays, and when its files are written. */
    public record Finished(String label, java.time.Instant started, List<String> views, java.util.concurrent.CompletableFuture<Void> saved) {}

    private static Finished lastFinished;

    public static Finished lastFinished() {
        return lastFinished;
    }

    /**
     * The show resumes (/cm reprise): a recording left without any file (restarted while nobody was there and the
     * round had no zone) is closed, and the next tick starts the round's recording again as its next part.
     */
    public static void onResume(MinecraftServer server) {
        Session ses = session;
        if (ses != null && ses.active().isEmpty()) stop(server, "reprise, enregistrement vide");
        tick(server);
    }

    private static void stop(MinecraftServer server, String why) {
        Session ses = session;
        if (ses == null) return;
        marker(ses.number > 0 ? "Fin de la manche" : "Fin de la partie", 0xF2C14E, ReplayMoments.ROUND, null);
        session = null;
        List<java.util.concurrent.CompletableFuture<?>> saving = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Rec r : ses.active()) {
            // How far each file's clock went from the server's ticks since its start (lag makes ticks slower than time)
            if (server != null) logDrift(server, r);
            java.util.concurrent.CompletableFuture<?> f = ServerReplayBridge.stop(r.rec);
            if (f != null) saving.add(f);
            views.add(r.name);
            r.rec = null;
        }
        writeIndex(ses, false);
        if (ses.number > 0 && !views.isEmpty()) {
            lastFinished = new Finished(ses.label, java.time.Instant.ofEpochMilli(ses.startedAt), views,
                    java.util.concurrent.CompletableFuture.allOf(saving.toArray(new java.util.concurrent.CompletableFuture[0])));
            if (server != null) fr.lordfinn.craftmaster.trial.kit.TrialRun.replayFinished(server);
        }
        if (server != null) ReplayDisk.autoPurge();
        CraftMaster.LOG.info("[Replay] Fin de l'enregistrement de {} ({}, {} s)", ses.label, why, Math.round(ses.seconds()));
        if (server != null) notifyAdmins(server, "● Replay : " + ses.label + " sauvegardé (" + Math.round(ses.seconds()) + " s).");
    }

    // ---------------------------------------------------------------- show payloads and markers

    /**
     * Called by the direction after sending a payload to every player that has the mod ({@code toAll}) or only to
     * the broadcast games: copies it into the area's file and into the players' files that did not get it.
     */
    public static void onPayload(MinecraftServer server, CustomPacketPayload payload, boolean toAll) {
        Session ses = session;
        if (ses == null) return;
        var packet = new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload);
        for (Rec r : ses.active()) {
            if (r.needsState) continue;
            if (!r.zone && toAll && canReceive(server, r, payload)) continue;
            ServerReplayBridge.record(r.rec, packet);
        }
    }

    private static boolean canReceive(MinecraftServer server, Rec r, CustomPacketPayload payload) {
        ServerPlayer p = server.getPlayerList().getPlayer(r.player);
        return p != null && ServerPlayNetworking.canSend(p, payload.type());
    }

    /** A named marker, now, on every file being recorded (shown on the replay's timeline): a flag of the console. */
    public static void marker(String name, int rgb) {
        marker(name, rgb, ReplayMoments.FLAG, null);
    }

    /**
     * A key moment, now, on every file being recorded: its name, colour, kind ({@link ReplayMoments}, the icon on the
     * replay's bar) and the player it is about (null: no one), with the zone that player is in. The files keep the
     * name and colour; the index all of it, found again by the name.
     */
    public static void marker(String name, int rgb, String kind, String player) {
        Session ses = session;
        if (ses == null) return;
        for (Rec r : ses.active()) ServerReplayBridge.marker(r.rec, name, rgb);
        JsonObject m = new JsonObject();
        m.addProperty("t", Math.round(ses.seconds() * 10) / 10.0);
        m.addProperty("name", name);
        m.addProperty("color", String.format(Locale.ROOT, "#%06X", rgb));
        if (kind != null && !kind.isEmpty()) m.addProperty("kind", kind);
        if (player != null) {
            m.addProperty("player", player);
            String zone = zoneOf(ses, player);
            if (zone != null) m.addProperty("zone", zone);
        }
        ses.markers.add(m);
    }

    /** The zone of the épreuve the player is in now, or null (offline, outside them all). */
    private static String zoneOf(Session ses, String player) {
        MinecraftServer server = lastServer;
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayerByName(player);
        if (p == null) return null;
        for (Rec r : ses.zones())
            if (r.active() && r.contains(p.level().dimension().identifier().toString(), p.getX(), p.getZ())) return r.name;
        return null;
    }

    private static final class Markers implements ShowEvents.Listener {
        @Override
        public void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {
            marker(player + " " + (delta > 0 ? "+" : "") + delta, ReplayMoments.colorOf(player), ReplayMoments.SCORE, player);
        }

        @Override
        public void onSpotlight(MinecraftServer server, String player, String reason) {
            String what = switch (reason == null ? "" : reason) {
                case "envelope" -> "ouvre son enveloppe";
                case "buzzer" -> "buzze";
                case "" -> "(projecteur)";
                default -> reason == null ? "(projecteur)" : reason;
            };
            marker(player + " " + what, ReplayMoments.colorOf(player), ReplayMoments.SPOTLIGHT, player);
        }

        @Override
        public void onEnvelopeOpened(MinecraftServer server, ServerPlayer player, int season, String mancheId) {
            String name = player.getGameProfile().name();
            marker(name + " ouvre son enveloppe", ReplayMoments.colorOf(name), ReplayMoments.SPOTLIGHT, name);
        }

        /** A new round inside a game being recorded ("partie" mode); in "manche" mode its file starts with it. */
        @Override
        public void onMancheStarted(MinecraftServer server, int season, int number, String name) {
            if (session != null && session.number == 0) marker("Manche " + number + " — " + name, 0xF2C14E, ReplayMoments.ROUND, null);
        }

        @Override
        public void onTaskRevealed(MinecraftServer server, int season, String mancheId, ServerPlayer by) {
            marker("Tâche révélée" + (by == null ? "" : " (" + by.getGameProfile().name() + ")"), 0xAB47BC, ReplayMoments.TASK, null);
        }

        @Override
        public void onRevealStarted(MinecraftServer server, int season) {
            marker("Révélation du classement", 0xF2C14E, ReplayMoments.REVEAL, null);
        }

        @Override
        public void onPublished(MinecraftServer server, int season) {
            marker("Scores publiés", 0xF2C14E, ReplayMoments.REVEAL, null);
        }
    }

    // ---------------------------------------------------------------- index and status

    /**
     * Writes this recording's entry of {@code <recordings>/craftmaster.json} (folders, area, markers, clock samples),
     * replacing the previous version of it: written when the recording starts and every 10 s, so that after a crash
     * the index still lists it (marked "interrupted" at the next start) up to its last 10 seconds.
     */
    private static void writeIndex(Session ses, boolean inProgress) {
        try {
            Path file = indexFile();
            JsonArray all = readIndex(file);
            String id = ses.key + "@" + ses.startedAt;
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("label", ses.label);
            o.addProperty("season", ses.season);
            o.addProperty("partie", ses.partie);
            o.addProperty("manche", ses.number);
            o.addProperty("mancheId", ses.mancheId);
            o.addProperty("started", java.time.Instant.ofEpochMilli(ses.startedAt).toString());
            o.addProperty("seconds", Math.round(ses.seconds()));
            o.addProperty("key", ses.key);
            if (ses.part > 1) {
                o.addProperty("part", ses.part);
                if (ses.follows != null) o.addProperty("follows", ses.follows);
            }
            if (inProgress) o.addProperty("inProgress", true);
            JsonArray zones = new JsonArray(), players = new JsonArray();
            for (Rec r : ses.recs) {
                JsonObject e = new JsonObject();
                e.addProperty("name", r.name);
                e.addProperty("folder", (r.zone ? "chunks/" : "players/") + r.folder);
                if (r.file != null) e.addProperty("file", r.file);
                if (r.zone) {
                    e.addProperty("world", r.area.world());
                    e.addProperty("from", r.area.fromX() + "," + r.area.fromZ());
                    e.addProperty("to", r.area.toX() + "," + r.area.toZ());
                    if (r.origin != null) e.addProperty("origin", r.origin[0] + "," + r.origin[1] + "," + r.origin[2]);
                } else {
                    if (r.outing) e.addProperty("outing", true);
                    e.addProperty("at", r.fromSeconds);
                }
                if (r.tick0 != null) e.addProperty("tick0", Math.round(r.tick0 * 100) / 100.0);
                JsonArray clock = new JsonArray();
                for (long[] c : r.clock) {
                    JsonArray pair = new JsonArray();
                    pair.add(c[0]);
                    pair.add(c[1]);
                    clock.add(pair);
                }
                e.add("clock", clock);
                (r.zone ? zones : players).add(e);
            }
            o.add("zones", zones);
            // The first zone also as "zone": indexes read by older versions
            if (!zones.isEmpty()) o.add("zone", zones.get(0));
            o.add("players", players);
            o.add("markers", ses.markers);
            boolean replaced = false;
            for (int k = 0; k < all.size(); k++) {
                JsonObject e = all.get(k).getAsJsonObject();
                if (e.has("id") && e.get("id").getAsString().equals(id)) {
                    all.set(k, o);
                    replaced = true;
                }
            }
            if (!replaced) all.add(o);
            writeIndexFile(file, all);
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index des replays non écrit : {}", e.toString());
        }
    }

    static JsonArray readIndex(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonArray();
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (RuntimeException e) {
            Files.move(file, file.resolveSibling("craftmaster.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new JsonArray();
        }
    }

    /** Written next to it then moved over it: a crash during the write never leaves half an index. */
    static synchronized void writeIndexFile(Path file, JsonArray all) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("craftmaster.json.tmp");
        Files.writeString(tmp, GSON.toJson(all), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** At server start: recordings the index still shows in progress were cut by a crash or a kill. */
    private static void markInterrupted() {
        if (!available()) return;
        try {
            Path file = indexFile();
            JsonArray all = readIndex(file);
            int n = 0;
            for (var e : all) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("inProgress")) {
                    o.remove("inProgress");
                    o.addProperty("interrupted", true);
                    n++;
                }
            }
            if (n > 0) {
                writeIndexFile(file, all);
                CraftMaster.LOG.warn("[Replay] {} enregistrement(s) interrompu(s) par un arrêt brutal : Server Replay récupère leurs fichiers", n);
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index des replays illisible : {}", e.toString());
        }
    }

    /** {@code <recordings>/craftmaster.json}: Server Replay's folders' parent. */
    static Path indexFile() {
        return ServerReplayBridge.chunkFolder().toAbsolutePath().normalize().getParent().resolve("craftmaster.json");
    }

    /** The newest index entry of this recording key (an earlier part of the same round), or null. */
    private static JsonObject lastOf(String key) {
        try {
            JsonArray all = readIndex(indexFile());
            for (int i = all.size() - 1; i >= 0; i--) {
                JsonObject o = all.get(i).getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "";
                if (id.startsWith(key + "@")) return o;
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Replay] Index illisible : {}", e.toString());
        }
        return null;
    }

    public static List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("Server Replay : " + (available() ? "installé" : "absent (rien n'est enregistré)"));
        out.add("Mode : " + settings.mode + " · joueurs " + (settings.players ? "oui" : "non") + " · zone " + (settings.zone ? "oui" : "non")
                + " · marge " + settings.margin + " chunk(s)");
        Session ses = session;
        if (ses == null) out.add("Aucun enregistrement en cours.");
        else {
            out.add("En cours depuis " + Math.round(ses.seconds()) + " s : " + ses.label);
            for (Rec r : ses.active())
                out.add("  " + (r.zone ? "zone " + r.name + " — " + r.area.describe() : r.name + (r.outing ? " (hors zone)" : "")) + " → " + r.folder);
        }
        return out;
    }

    static void notifyAdmins(MinecraftServer server, String text) {
        var msg = net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (fr.lordfinn.craftmaster.Permissions.isAdmin(p)) p.sendSystemMessage(msg);
    }
}
