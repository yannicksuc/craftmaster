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
        /** One file per contestant. */
        public boolean players = true;
        /** One file for the round's area. */
        public boolean zone = true;
        /** Chunks added around the area, and the widest area (chunks). */
        public int margin = 2, maxSpan = 40;
    }

    /** One recorded player. */
    private static final class PlayerRec {
        final String name, folder;
        ServerReplayBridge.Recorder rec;
        boolean needsState = true;

        PlayerRec(String name, String folder, ServerReplayBridge.Recorder rec) {
            this.name = name;
            this.folder = folder;
            this.rec = rec;
        }
    }

    /** What is being recorded now. */
    private static final class Session {
        final String key, label;
        final int season, partie, number;
        final String mancheId;
        final long startedAt = System.currentTimeMillis();
        ServerReplayBridge.Recorder zone;
        String zoneFolder;
        ReplayArea.Chunks area;
        boolean zoneNeedsState = true;
        /** Round running at the last tick (to mark its end in a game's file). */
        String lastManche;
        final Map<UUID, PlayerRec> players = new LinkedHashMap<>();
        final List<String> playerFolders = new ArrayList<>();
        /** Server tick at each file's time 0 (folder → tick; the zone under ""), measured when its state goes in. */
        final Map<String, Double> tick0 = new LinkedHashMap<>();
        final Map<String, String> folderPlayer = new LinkedHashMap<>();
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

    private static void tick(MinecraftServer server) {
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
        if (session.number == 0 && session.lastManche != null && !session.lastManche.equals(current)) marker("Fin de la manche", 0xF2C14E);
        session.lastManche = current;

        // Players joining (or coming back) are recorded in the same folder; the show's state goes into new files
        if (server.getTickCount() % 20 == 0) addPlayers(server, data);
        ShowState state = null;
        if (session.zone != null && session.zoneNeedsState && ServerReplayBridge.ready(session.zone)) {
            state = Director.build(server);
            ServerReplayBridge.record(session.zone, new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(state));
            session.zoneNeedsState = false;
            noteTick0(server, session, "", session.zone);
        }
        for (PlayerRec r : session.players.values()) {
            if (r.rec == null || !r.needsState || !ServerReplayBridge.ready(r.rec)) continue;
            if (state == null) state = Director.build(server);
            ServerReplayBridge.record(r.rec, new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(state));
            addBossBars(server, r);
            r.needsState = false;
            noteTick0(server, session, r.folder, r.rec);
        }
    }

    /**
     * The boss bars this player sees already (round timer, stopwatches) go into their new file: it has their later
     * updates but not their creation, which makes a game watching the file stop with an error.
     */
    private static void addBossBars(MinecraftServer server, PlayerRec r) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(r.name);
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
    private static void noteTick0(MinecraftServer server, Session ses, String folder, ServerReplayBridge.Recorder rec) {
        long ms = ServerReplayBridge.recordingMs(rec);
        if (ms >= 0) ses.tick0.put(folder, server.getTickCount() - ms / 50.0);
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
        Session ses = new Session(key, label, seasonId, partieId, number, m == null ? "" : m.id);
        session = ses;
        try {
            if (settings.zone) startZone(server, ses, s, p, m);
            if (settings.players) addPlayers(server, data);
        } catch (RuntimeException e) {
            CraftMaster.LOG.error("[Replay] Démarrage de l'enregistrement « {} »", label, e);
        }
        if (ses.zone == null && ses.players.isEmpty()) {
            CraftMaster.LOG.warn("[Replay] Rien à enregistrer pour {}", label);
        }
        marker(m == null ? "Partie : " + p.name : "Manche " + number + " — " + m.name, 0xF2C14E);
        // A game recording started during one of its rounds: that round's marker too
        Live.Current cur = data.live().current;
        Manche now = m == null && cur != null && cur.partie() == partieId ? p.manche(cur.manche()) : null;
        if (now != null) marker("Manche " + p.number(now) + " — " + now.name, 0xF2C14E);
        CraftMaster.LOG.info("[Replay] Enregistrement de {} : zone {}, {} joueur(s)", label,
                ses.area == null ? "aucune" : ses.area.describe(), ses.players.size());
        notifyAdmins(server, "● Replay : enregistrement de " + label + " (" + ses.players.size() + " joueur(s)"
                + (ses.zone != null ? " + la zone" : "") + ").");
    }

    private static void startZone(MinecraftServer server, Session ses, Season s, Partie p, Manche m) {
        List<ReplayArea.Box> boxes = new ArrayList<>();
        Vec3 camera = null;
        List<Manche> manches = m != null ? List.of(m) : p.activeManches();
        for (Manche r : manches) {
            for (CameraSpot c : Cameras.list().lieu(r.id).all()) {
                boxes.add(ReplayArea.Box.point(c.world(), c.x(), c.z()));
                if (camera == null && (CameraSpot.WIDE.equals(c.role()) || c.number() == 1)) camera = new Vec3(c.x(), c.y(), c.z());
                if (!c.zone().isEmpty()) {
                    Zone z = TrialZones.zones().get(c.zone());
                    if (z != null) boxes.add(new ReplayArea.Box(z.world(), z.x0(), z.z0(), z.x1(), z.z1()));
                }
            }
            for (Spot t : r.tp) boxes.add(ReplayArea.Box.point(t.world(), t.x(), t.z()));
        }
        // A round with nothing placed yet: around its players, where they stand now
        if (boxes.isEmpty())
            for (ServerPlayer pl : cast(server, s))
                boxes.add(ReplayArea.Box.point(pl.level().dimension().identifier().toString(), pl.getX(), pl.getZ()));
        ReplayArea.Chunks area = ReplayArea.of(boxes, settings.margin, settings.maxSpan);
        if (area == null) return;
        ServerLevel level = Busts.level(server, area.world());
        if (level == null) {
            CraftMaster.LOG.warn("[Replay] Monde « {} » introuvable : pas d'enregistrement de zone", area.world());
            return;
        }
        String folder = ReplayArea.folder(ses.season, ses.partie, ses.number, ses.mancheId, null);
        if (ServerReplayBridge.hasChunkRecorder(folder)) {
            CraftMaster.LOG.warn("[Replay] Une zone « {} » s'enregistre déjà", folder);
            return;
        }
        ses.zone = ServerReplayBridge.startChunks(level, new ChunkPos(area.fromX(), area.fromZ()), new ChunkPos(area.toX(), area.toZ()),
                ServerReplayBridge.chunkFolder().resolve(folder), folder, camera);
        if (ses.zone != null) {
            ses.zoneFolder = folder;
            ses.area = area;
        }
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

    private static void addPlayers(MinecraftServer server, CraftMasterData data) {
        Session ses = session;
        if (ses == null || !settings.players) return;
        // Recorders Server Replay closed itself (the player left): forget them, a new file starts if they return
        ses.players.values().removeIf(r -> {
            ServerPlayer pl = server.getPlayerList().getPlayerByName(r.name);
            return pl == null || !ServerReplayBridge.isRecording(pl);
        });
        for (ServerPlayer pl : cast(server, data.season(ses.season))) {
            if (ses.players.containsKey(pl.getUUID())) continue;
            // Already recorded by someone else (/replay start players): left alone
            if (ServerReplayBridge.isRecording(pl)) continue;
            String name = pl.getGameProfile().name();
            String folder = ReplayArea.folder(ses.season, ses.partie, ses.number, ses.mancheId, name);
            try {
                ServerReplayBridge.Recorder rec = ServerReplayBridge.startPlayer(pl, ServerReplayBridge.playerFolder().resolve(folder));
                if (rec == null) continue;
                ses.players.put(pl.getUUID(), new PlayerRec(name, folder, rec));
                if (!ses.playerFolders.contains(folder)) ses.playerFolders.add(folder);
                ses.folderPlayer.put(folder, name);
            } catch (RuntimeException e) {
                CraftMaster.LOG.error("[Replay] Enregistrement de {} impossible", name, e);
            }
        }
    }

    private static void stop(MinecraftServer server, String why) {
        Session ses = session;
        if (ses == null) return;
        marker(ses.number > 0 ? "Fin de la manche" : "Fin de la partie", 0xF2C14E);
        session = null;
        if (ses.zone != null) ServerReplayBridge.stop(ses.zone);
        for (PlayerRec r : ses.players.values()) if (r.rec != null) ServerReplayBridge.stop(r.rec);
        writeIndex(ses);
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
        if (ses.zone != null && !ses.zoneNeedsState) ServerReplayBridge.record(ses.zone, packet);
        for (Map.Entry<UUID, PlayerRec> e : ses.players.entrySet()) {
            PlayerRec r = e.getValue();
            if (r.rec == null || r.needsState) continue;
            if (toAll && canReceive(server, r, payload)) continue;
            ServerReplayBridge.record(r.rec, packet);
        }
    }

    private static boolean canReceive(MinecraftServer server, PlayerRec r, CustomPacketPayload payload) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(r.name);
        return p != null && ServerPlayNetworking.canSend(p, payload.type());
    }

    /** A named marker, now, on every file being recorded (shown on the replay's timeline). */
    public static void marker(String name, int rgb) {
        Session ses = session;
        if (ses == null) return;
        if (ses.zone != null) ServerReplayBridge.marker(ses.zone, name, rgb);
        for (PlayerRec r : ses.players.values()) if (r.rec != null) ServerReplayBridge.marker(r.rec, name, rgb);
        JsonObject m = new JsonObject();
        m.addProperty("t", Math.round(ses.seconds() * 10) / 10.0);
        m.addProperty("name", name);
        m.addProperty("color", String.format(Locale.ROOT, "#%06X", rgb));
        ses.markers.add(m);
    }

    private static final class Markers implements ShowEvents.Listener {
        @Override
        public void onScoreGiven(MinecraftServer server, int season, String player, int delta, String manche) {
            marker(player + " " + (delta > 0 ? "+" : "") + delta, delta >= 0 ? 0x4CAF50 : 0xE53935);
        }

        @Override
        public void onSpotlight(MinecraftServer server, String player, String reason) {
            String what = switch (reason == null ? "" : reason) {
                case "envelope" -> "ouvre son enveloppe";
                case "buzzer" -> "buzze";
                case "" -> "(projecteur)";
                default -> reason == null ? "(projecteur)" : reason;
            };
            marker(player + " " + what, 0x42A5F5);
        }

        @Override
        public void onEnvelopeOpened(MinecraftServer server, ServerPlayer player, int season, String mancheId) {
            marker(player.getGameProfile().name() + " ouvre son enveloppe", 0x42A5F5);
        }

        /** A new round inside a game being recorded ("partie" mode); in "manche" mode its file starts with it. */
        @Override
        public void onMancheStarted(MinecraftServer server, int season, int number, String name) {
            if (session != null && session.number == 0) marker("Manche " + number + " — " + name, 0xF2C14E);
        }

        @Override
        public void onTaskRevealed(MinecraftServer server, int season, String mancheId, ServerPlayer by) {
            marker("Tâche révélée" + (by == null ? "" : " (" + by.getGameProfile().name() + ")"), 0xAB47BC);
        }

        @Override
        public void onRevealStarted(MinecraftServer server, int season) {
            marker("Révélation du classement", 0xF2C14E);
        }

        @Override
        public void onPublished(MinecraftServer server, int season) {
            marker("Scores publiés", 0xF2C14E);
        }
    }

    // ---------------------------------------------------------------- index and status

    /** Appends this recording to {@code <recordings>/craftmaster.json} (folders, area, markers). */
    private static void writeIndex(Session ses) {
        try {
            Path file = indexFile();
            JsonArray all = new JsonArray();
            if (Files.exists(file)) {
                try {
                    all = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
                } catch (RuntimeException e) {
                    Files.move(file, file.resolveSibling("craftmaster.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            JsonObject o = new JsonObject();
            o.addProperty("label", ses.label);
            o.addProperty("season", ses.season);
            o.addProperty("partie", ses.partie);
            o.addProperty("manche", ses.number);
            o.addProperty("mancheId", ses.mancheId);
            o.addProperty("started", java.time.Instant.ofEpochMilli(ses.startedAt).toString());
            o.addProperty("seconds", Math.round(ses.seconds()));
            if (ses.zoneFolder != null) {
                JsonObject z = new JsonObject();
                z.addProperty("folder", "chunks/" + ses.zoneFolder);
                z.addProperty("world", ses.area.world());
                z.addProperty("from", ses.area.fromX() + "," + ses.area.fromZ());
                z.addProperty("to", ses.area.toX() + "," + ses.area.toZ());
                Double t0 = ses.tick0.get("");
                if (t0 != null) z.addProperty("tick0", Math.round(t0 * 100) / 100.0);
                o.add("zone", z);
            }
            JsonArray players = new JsonArray();
            for (String f : ses.playerFolders) {
                JsonObject pl = new JsonObject();
                pl.addProperty("name", ses.folderPlayer.getOrDefault(f, f));
                pl.addProperty("folder", "players/" + f);
                Double t0 = ses.tick0.get(f);
                if (t0 != null) pl.addProperty("tick0", Math.round(t0 * 100) / 100.0);
                players.add(pl);
            }
            o.add("players", players);
            o.add("markers", ses.markers);
            all.add(o);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index des replays non écrit : {}", e.toString());
        }
    }

    /** {@code <recordings>/craftmaster.json}: Server Replay's folders' parent. */
    static Path indexFile() {
        return ServerReplayBridge.chunkFolder().toAbsolutePath().normalize().getParent().resolve("craftmaster.json");
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
            if (ses.zone != null) out.add("  zone " + ses.zoneFolder + " — " + ses.area.describe());
            for (PlayerRec r : ses.players.values()) out.add("  " + r.name + " → " + r.folder);
        }
        return out;
    }

    private static void notifyAdmins(MinecraftServer server, String text) {
        var msg = net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (fr.lordfinn.craftmaster.Permissions.isAdmin(p)) p.sendSystemMessage(msg);
    }
}
