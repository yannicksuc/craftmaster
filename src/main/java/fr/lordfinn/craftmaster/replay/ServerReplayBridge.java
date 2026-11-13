package fr.lordfinn.craftmaster.replay;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optional link to Server Replay (senseiwells, MIT; the recording itself is Arcade's {@code arcade-replay}, shipped
 * inside its jar). Everything goes through reflection, so CraftMaster neither needs Server Replay to build nor to
 * run: without it {@link #available()} is false and nothing else here is called.
 *
 * <p>Calls (Server Replay 3.5.x, Arcade 0.13): {@code ServerReplay.getConfig()} (its config.json: format, folders,
 * ignored packets…), {@code ReplayPlayerRecorders.create(player, folder, format, settings)},
 * {@code ReplayChunkRecorders.create(area, folder, format, settings, cameraPos, name)}, and on a recorder
 * {@code start(StartingMode)}, {@code stop(save)}, {@code record(packet)}, {@code addMarker(name, colour)},
 * {@code isInitialized()}. A recording is saved as {@code <folder>/<date-time>.mcpr} (ReplayMod format by default).
 */
public final class ServerReplayBridge {

    private ServerReplayBridge() {}

    /** One running recorder (an Arcade {@code ReplayRecorder}). */
    public record Recorder(Object handle) {}

    private static Boolean available;
    private static Method getConfig, defaultFormat, createSettings, playerPath, chunkPath;
    private static Method createPlayer, hasPlayer, createChunks, hasChunks;
    private static Constructor<?> chunkArea;
    private static Method start, stop, record, addMarker, isInitialized;
    private static Object startMode;

    /** Server Replay is installed and its API is the one this class knows. Checked once. */
    public static boolean available() {
        if (available == null) available = FabricLoader.getInstance().isModLoaded("server-replay") && link();
        return available;
    }

    private static boolean link() {
        try {
            ClassLoader cl = ServerReplayBridge.class.getClassLoader();
            Class<?> serverReplay = Class.forName("me.senseiwells.replay.ServerReplay", true, cl);
            Class<?> config = Class.forName("me.senseiwells.replay.config.ReplayConfig", true, cl);
            Class<?> format = Class.forName("net.casual.arcade.replay.io.ReplayFormat", true, cl);
            Class<?> settings = Class.forName("net.casual.arcade.replay.recorder.settings.RecorderSettings", true, cl);
            Class<?> players = Class.forName("net.casual.arcade.replay.recorder.player.ReplayPlayerRecorders", true, cl);
            Class<?> chunks = Class.forName("net.casual.arcade.replay.recorder.chunk.ReplayChunkRecorders", true, cl);
            Class<?> area = Class.forName("net.casual.arcade.replay.recorder.chunk.ChunkArea", true, cl);
            Class<?> recorder = Class.forName("net.casual.arcade.replay.recorder.ReplayRecorder", true, cl);
            Class<?> mode = Class.forName("net.casual.arcade.replay.recorder.ReplayRecorder$StartingMode", true, cl);

            getConfig = serverReplay.getMethod("getConfig");
            defaultFormat = config.getMethod("getDefaultReplayFormat");
            createSettings = config.getMethod("createSettings");
            playerPath = config.getMethod("getPlayerRecordingPath");
            chunkPath = config.getMethod("getChunkRecordingPath");
            createPlayer = players.getMethod("create", ServerPlayer.class, Path.class, format, settings);
            hasPlayer = players.getMethod("has", java.util.UUID.class, format);
            createChunks = chunks.getMethod("create", area, Path.class, format, settings, Vec3.class, String.class);
            hasChunks = chunks.getMethod("has", String.class);
            chunkArea = area.getConstructor(ServerLevel.class, ChunkPos.class, ChunkPos.class);
            start = recorder.getMethod("start", mode);
            stop = recorder.getMethod("stop", boolean.class);
            record = recorder.getMethod("record", Packet.class);
            addMarker = recorder.getMethod("addMarker", String.class, int.class);
            isInitialized = recorder.getMethod("isInitialized");
            for (Object c : mode.getEnumConstants()) if (((Enum<?>) c).name().equals("Start")) startMode = c;
            if (startMode == null) throw new NoSuchFieldException("StartingMode.Start");
            CraftMaster.LOG.info("[Replay] Server Replay trouvé : enregistrement automatique des manches possible.");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            CraftMaster.LOG.warn("[Replay] Server Replay est installé mais son API n'est pas celle attendue (3.5.x) : "
                    + "pas d'enregistrement automatique ({})", e.toString());
            return false;
        }
    }

    /** Format asked by CraftMaster: "flashback", "replay_mod", or "" for Server Replay's own setting. */
    private static String wantedFormat = "";

    public static void setFormat(String name) {
        wantedFormat = name == null ? "" : name;
    }

    /** The format files are written in: CraftMaster's choice, else Server Replay's {@code default_encoding}. */
    private static Object format(Object cfg) throws ReflectiveOperationException {
        if (!wantedFormat.isEmpty()) {
            Class<?> f = defaultFormat.getReturnType();
            for (Object c : f.getEnumConstants())
                if (((Enum<?>) c).name().replace("_", "").equalsIgnoreCase(wantedFormat.replace("_", ""))) return c;
        }
        return defaultFormat.invoke(cfg);
    }

    private static Object config() throws ReflectiveOperationException {
        return getConfig.invoke(null);
    }

    /** Server Replay's folder for player recordings (its {@code player_recording_path}). */
    public static Path playerFolder() {
        try {
            return (Path) playerPath.invoke(config());
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /** Server Replay's folder for chunk recordings (its {@code chunk_recording_path}). */
    public static Path chunkFolder() {
        try {
            return (Path) chunkPath.invoke(config());
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /** Whether this player is being recorded already (by us or by {@code /replay start players}). */
    public static boolean isRecording(ServerPlayer player) {
        try {
            return (boolean) hasPlayer.invoke(null, player.getUUID(), format(config()));
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    public static boolean hasChunkRecorder(String name) {
        try {
            return (boolean) hasChunks.invoke(null, name);
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /** Starts recording what this player receives into {@code folder}; null if it could not start. */
    public static Recorder startPlayer(ServerPlayer player, Path folder) {
        try {
            Object cfg = config();
            Object r = createPlayer.invoke(null, player, folder, format(cfg), createSettings.invoke(cfg));
            return (boolean) start.invoke(r, startMode) ? new Recorder(r) : null;
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /**
     * Starts a fixed recording of the chunks {@code from}..{@code to} (corners included) of {@code level}, named
     * {@code name} (unique among the running chunk recorders), the replay camera starting at {@code camera}.
     */
    public static Recorder startChunks(ServerLevel level, ChunkPos from, ChunkPos to, Path folder, String name, Vec3 camera) {
        try {
            Object cfg = config();
            Object area = chunkArea.newInstance(level, from, to);
            Object r = createChunks.invoke(null, area, folder, format(cfg), createSettings.invoke(cfg), camera, name);
            return (boolean) start.invoke(r, startMode) ? new Recorder(r) : null;
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /** Stops and saves; the future completes (with the compressed size) once the file is written. */
    @SuppressWarnings("unchecked")
    public static CompletableFuture<Long> stop(Recorder r) {
        try {
            return (CompletableFuture<Long>) stop.invoke(r.handle(), true);
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Ready to take packets (its first snapshot of the world is written). */
    public static boolean ready(Recorder r) {
        try {
            return (boolean) isInitialized.invoke(r.handle());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Writes a packet into this recording only (nobody receives it). */
    public static void record(Recorder r, Packet<?> packet) {
        try {
            record.invoke(r.handle(), packet);
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Replay] Paquet non enregistré : {}", e.toString());
        }
    }

    /** A named marker on the replay's timeline, now, in {@code rgb}. */
    public static void marker(Recorder r, String name, int rgb) {
        try {
            addMarker.invoke(r.handle(), name, rgb);
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Replay] Repère non enregistré : {}", e.toString());
        }
    }

    /** How long this recording has run (ms, pauses excluded): the time a packet recorded now gets in the file. */
    public static long recordingMs(Recorder r) {
        try {
            if (recordingLength == null) recordingLength = r.handle().getClass().getMethod("getRecordingLength-UwyO8pc");
            return durationMs((long) recordingLength.invoke(r.handle()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static Method recordingLength;
    private static java.lang.reflect.Field recorderWriter, fbTicks, fbSinceSnapshot;
    private static boolean pieceLinkFailed;

    /**
     * Makes a Flashback recording close its current piece now (written to the disk with a snapshot) instead of after
     * 5 minutes (Arcade's fixed 6000 ticks): Arcade starts a new piece when {@code ticks - ticksSinceLastSnapshot}
     * reaches 6000, so the latter is moved back. Nothing for other formats. False if it could not.
     */
    public static boolean cutPiece(Recorder r) {
        if (pieceLinkFailed) return false;
        try {
            if (recorderWriter == null) {
                Class<?> rc = Class.forName("net.casual.arcade.replay.recorder.ReplayRecorder", true, ServerReplayBridge.class.getClassLoader());
                recorderWriter = rc.getDeclaredField("writer");
                recorderWriter.setAccessible(true);
            }
            Object w = recorderWriter.get(r.handle());
            if (w == null || !w.getClass().getSimpleName().equals("FlashbackWriter")) return false;
            if (fbTicks == null) {
                fbTicks = w.getClass().getDeclaredField("ticks");
                fbTicks.setAccessible(true);
                fbSinceSnapshot = w.getClass().getDeclaredField("ticksSinceLastSnapshot");
                fbSinceSnapshot.setAccessible(true);
            }
            fbSinceSnapshot.setInt(w, fbTicks.getInt(w) - 6000);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            pieceLinkFailed = true;
            CraftMaster.LOG.warn("[Replay] Morceaux Flashback de 10 s impossibles (Arcade a changé ?) : 5 min par morceau ({})", e.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------- the viewer (/replay view)

    /** One replay being watched (an Arcade {@code ReplayViewer}). */
    public record Viewer(Object handle) {}

    /** A marker read from the file being watched. */
    public record FileMarker(String name, long ms, int color) {}

    private static Boolean viewerLinked;
    private static Method vHideProgress;
    private static Method vCreate, vAll, vStart, vClose, vPlayer, vPaused, vSpeed, vSetSpeed, vSetPaused, vJump, vMarkers;
    private static Method mName, mTime, mColor, rDuration, sendDirect;
    private static java.lang.reflect.Field vProgress, vReader;
    private static Object viewerDispatcher;

    /** Server Replay's viewer is there and its API is the one this class knows. Checked once. */
    public static boolean viewerAvailable() {
        if (viewerLinked == null) viewerLinked = available() && linkViewer();
        return viewerLinked;
    }

    private static boolean linkViewer() {
        try {
            ClassLoader cl = ServerReplayBridge.class.getClassLoader();
            Class<?> viewers = Class.forName("net.casual.arcade.replay.viewer.ReplayViewers", true, cl);
            Class<?> viewer = Class.forName("net.casual.arcade.replay.viewer.ReplayViewer", true, cl);
            Class<?> marker = Class.forName("net.casual.arcade.replay.util.ReplayMarker", true, cl);
            Class<?> reader = Class.forName("net.casual.arcade.replay.io.reader.ReplayReader", true, cl);
            Class<?> commands = Class.forName("net.casual.arcade.replay.viewer.ReplayViewerCommands", true, cl);
            Class<?> viewable = Class.forName("net.casual.arcade.replay.ducks.ReplayViewable", true, cl);
            vCreate = viewers.getMethod("create", Path.class, ServerPlayer.class);
            vAll = viewers.getMethod("viewers");
            vStart = viewer.getMethod("start");
            // stop(), as /replay view close: close() alone leaves the player off the server and the replay's things on their screen
            vClose = viewer.getMethod("stop");
            vPlayer = viewer.getMethod("getPlayer");
            vPaused = viewer.getMethod("getPaused");
            vSpeed = viewer.getMethod("getSpeedMultiplier");
            vSetSpeed = viewer.getMethod("setSpeed", float.class);
            vSetPaused = viewer.getMethod("setPaused", boolean.class);
            vJump = viewer.getMethod("jumpTo-LRDsOJo", long.class);
            vMarkers = viewer.getMethod("getMarkers");
            vHideProgress = viewer.getMethod("hideProgress");
            vProgress = viewer.getDeclaredField("progress");
            vProgress.setAccessible(true);
            vReader = viewer.getDeclaredField("reader");
            vReader.setAccessible(true);
            rDuration = reader.getMethod("getDuration-UwyO8pc");
            mName = marker.getMethod("getName");
            mTime = marker.getMethod("getTimestamp-UwyO8pc");
            mColor = marker.getMethod("getColor");
            sendDirect = viewable.getMethod("arcade_sendReplayViewerPacket", Packet.class);
            java.lang.reflect.Field d = commands.getDeclaredField("dispatcher");
            d.setAccessible(true);
            viewerDispatcher = d.get(null);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Lecteur de Server Replay non reconnu : pas de barre de lecture CraftMaster ({})", e.toString());
            return false;
        }
    }

    /**
     * The command tree Server Replay's viewer runs while a replay plays (the only commands that reach the server
     * then); CraftMaster adds its {@code /replay view cm …} to it. Null without the viewer.
     */
    @SuppressWarnings("unchecked")
    public static com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> viewerCommands() {
        return viewerAvailable() ? (com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>) viewerDispatcher : null;
    }

    /** Plays this file to this player (who leaves the world until {@link #close}); null if it failed. */
    public static Viewer view(Path file, ServerPlayer player) {
        try {
            Object v = vCreate.invoke(null, file, player);
            if (v == null) return null;
            vStart.invoke(v);
            return new Viewer(v);
        } catch (ReflectiveOperationException e) {
            throw wrap(e);
        }
    }

    /** Every replay being watched now. */
    public static List<Viewer> viewers() {
        List<Viewer> out = new ArrayList<>();
        if (!viewerAvailable()) return out;
        try {
            for (Object v : (Collection<?>) vAll.invoke(null)) out.add(new Viewer(v));
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Replay] Lecteurs illisibles : {}", e.toString());
        }
        return out;
    }

    public static Viewer viewerOf(ServerPlayer player) {
        for (Viewer v : viewers()) if (player.equals(player(v))) return v;
        return null;
    }

    public static ServerPlayer player(Viewer v) {
        try {
            return (ServerPlayer) vPlayer.invoke(v.handle());
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Position in the file (ms): the time of the last packet played. */
    public static long progressMs(Viewer v) {
        try {
            return durationMs(vProgress.getLong(v.handle()));
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public static long durationMs(Viewer v) {
        try {
            return durationMs((long) rDuration.invoke(vReader.get(v.handle())));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    public static boolean paused(Viewer v) {
        try {
            return (boolean) vPaused.invoke(v.handle());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static float speed(Viewer v) {
        try {
            return (float) vSpeed.invoke(v.handle());
        } catch (ReflectiveOperationException e) {
            return 1;
        }
    }

    public static void setSpeed(Viewer v, float speed) {
        call(vSetSpeed, v, speed);
    }

    public static void setPaused(Viewer v, boolean paused) {
        call(vSetPaused, v, paused);
    }

    /** Goes to {@code ms} in the file (backwards: the viewer replays from the start, quickly). */
    public static boolean jumpTo(Viewer v, long ms) {
        try {
            return (boolean) vJump.invoke(v.handle(), Math.max(0, ms) * 1_000_000L << 1);
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Replay] Saut impossible : {}", e.toString());
            return false;
        }
    }

    /** Hides the viewer's own progress bar (a boss bar): CraftMaster's bar replaces it. */
    public static void hideProgress(Viewer v) {
        call(vHideProgress, v);
    }

    /** Stops watching: the player comes back to the server's world. */
    public static void close(Viewer v) {
        call(vClose, v);
    }

    public static List<FileMarker> markers(Viewer v) {
        List<FileMarker> out = new ArrayList<>();
        try {
            for (Object m : (List<?>) vMarkers.invoke(v.handle()))
                out.add(new FileMarker((String) mName.invoke(m), durationMs((long) mTime.invoke(m)), (int) mColor.invoke(m)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A file without markers, or unreadable ones: the bar just has none
        }
        out.sort(java.util.Comparator.comparingLong(FileMarker::ms));
        return out;
    }

    /** Sends a packet to the player watching, past the viewer's filter (which drops what the real world sends). */
    public static void sendToViewer(Viewer v, Packet<?> packet) {
        ServerPlayer p = player(v);
        if (p == null) return;
        try {
            sendDirect.invoke(p.connection, packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not viewing any more (closed this tick)
        }
    }

    private static void call(Method m, Viewer v, Object... args) {
        try {
            m.invoke(v.handle(), args);
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Replay] {} : {}", m.getName(), e.toString());
        }
    }

    /** A Kotlin {@code Duration} (an inline class: a long, nanoseconds or, with the low bit set, milliseconds) in ms. */
    static long durationMs(long raw) {
        return (raw & 1) == 0 ? (raw >> 1) / 1_000_000L : raw >> 1;
    }

    private static RuntimeException wrap(ReflectiveOperationException e) {
        Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
        return cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
    }
}
