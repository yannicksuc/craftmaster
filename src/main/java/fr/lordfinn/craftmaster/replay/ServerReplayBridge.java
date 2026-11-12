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
            return (boolean) hasPlayer.invoke(null, player.getUUID(), defaultFormat.invoke(config()));
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
            Object r = createPlayer.invoke(null, player, folder, defaultFormat.invoke(cfg), createSettings.invoke(cfg));
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
            Object r = createChunks.invoke(null, area, folder, defaultFormat.invoke(cfg), createSettings.invoke(cfg), camera, name);
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

    private static RuntimeException wrap(ReflectiveOperationException e) {
        Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
        return cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
    }
}
