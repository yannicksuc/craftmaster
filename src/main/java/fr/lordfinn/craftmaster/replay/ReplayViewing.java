package fr.lordfinn.craftmaster.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Watching the recordings with Server Replay's viewer (on the server: the player watching needs nothing but the
 * game, and CraftMaster to get the playback bar). {@code /cmreplay voir} lists the recordings of the index,
 * {@code /cmreplay voir <n> [zone|joueur]} plays one of their files.
 *
 * <p>While a replay plays the viewer cuts its player off the real world: nothing the server sends reaches them,
 * and only commands get through, to the viewer's own command tree. CraftMaster adds {@code /replay view cm …}
 * there (seek to a time, speed, pause, switch to another file of the same recording, close), which the client's
 * playback bar sends, and sends that bar a {@link ReplayStatus} five times a second past the viewer's filter.
 *
 * <p>Files line up on the server's ticks: each file's time 0 is noted in the index ({@code tick0}), so switching
 * from the zone to a player's view lands at the same instant, and the round's timer is replayed exactly.
 */
public final class ReplayViewing {

    private ReplayViewing() {}

    /** One file of a recording: "zone" or a player, its folder (relative to the recordings), its time 0 (tick, -1). */
    public record View(String name, String folder, double tick0) {}

    /** One recording of the index. */
    public record Entry(String label, Instant started, int seconds, List<View> views, Map<String, Integer> colors) {
        View view(String name) {
            for (View v : views) if (v.name().equalsIgnoreCase(name)) return v;
            return null;
        }
    }

    /** A replay started from here: which recording and file it is. */
    private record Watch(Entry entry, View view) {}

    /** A switch to another file, waiting for the old viewer to close. */
    private record Switch(UUID player, Entry entry, View view, long targetMs, boolean paused, float speed, long at) {}

    private static final Map<UUID, Watch> WATCHING = new HashMap<>();
    private static final List<Switch> SWITCHES = new ArrayList<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    /** Viewers to close at that time (a file switch). */
    private static final Map<UUID, Long> CLOSING = new HashMap<>();

    public static void register() {
        ReplayNetwork.registerTypes();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommandDispatcher<CommandSourceStack> d = ServerReplayBridge.available() ? ServerReplayBridge.viewerCommands() : null;
            if (d == null) return;
            registerViewerCommands(d);
            // Not on the server's ticks: a server whose only player watches a replay counts as empty and pauses
            // its ticks after a minute (the viewer takes the player off the list), while its tasks still run
            clock = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CraftMaster replay bar");
                t.setDaemon(true);
                return t;
            });
            clock.scheduleAtFixedRate(() -> server.execute(() -> pulse(server)), 200, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (clock != null) clock.shutdownNow();
            clock = null;
            WATCHING.clear();
            SWITCHES.clear();
            JUMPS.clear();
        });
    }

    private static java.util.concurrent.ScheduledExecutorService clock;

    // ---------------------------------------------------------------- the index

    /** The recordings of the index, newest first. */
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (!ServerReplayBridge.available()) return out;
        Path file = Replays.indexFile();
        if (!Files.exists(file)) return out;
        try {
            JsonArray all = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : all) {
                JsonObject o = e.getAsJsonObject();
                List<View> views = new ArrayList<>();
                if (o.has("zone")) {
                    JsonObject z = o.getAsJsonObject("zone");
                    views.add(new View("zone", z.get("folder").getAsString(), z.has("tick0") ? z.get("tick0").getAsDouble() : -1));
                }
                if (o.has("players")) for (JsonElement p : o.getAsJsonArray("players")) {
                    if (p.isJsonPrimitive()) {
                        // Index written before the files' ticks were noted: "players/cm-s1-p1-m1-le-pont-ensuri"
                        String f = p.getAsString();
                        views.add(new View(f.substring(f.lastIndexOf('-') + 1), f, -1));
                    } else {
                        JsonObject po = p.getAsJsonObject();
                        views.add(new View(po.get("name").getAsString(), po.get("folder").getAsString(), po.has("tick0") ? po.get("tick0").getAsDouble() : -1));
                    }
                }
                // The markers' colours (the ReplayMod format keeps only their names)
                Map<String, Integer> colors = new HashMap<>();
                if (o.has("markers")) for (JsonElement m : o.getAsJsonArray("markers")) {
                    JsonObject mo = m.getAsJsonObject();
                    try {
                        colors.put(mo.get("name").getAsString(), Integer.parseInt(mo.get("color").getAsString().substring(1), 16));
                    } catch (RuntimeException ignored) {
                        // No colour: the default one
                    }
                }
                out.add(new Entry(o.get("label").getAsString(), Instant.parse(o.get("started").getAsString()),
                        o.get("seconds").getAsInt(), views, colors));
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Index des replays illisible : {}", e.toString());
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** The .mcpr of this file of the recording: the one saved closest to the recording's end (a folder may hold several). */
    static Path file(Entry entry, View view) {
        String f = view.folder();
        int slash = f.indexOf('/');
        Path dir = (f.startsWith("chunks/") ? ServerReplayBridge.chunkFolder() : ServerReplayBridge.playerFolder()).resolve(slash >= 0 ? f.substring(slash + 1) : f);
        if (!Files.isDirectory(dir)) return null;
        long end = entry.started().toEpochMilli() + entry.seconds() * 1000L;
        Path best = null;
        long bestGap = Long.MAX_VALUE;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!p.getFileName().toString().endsWith(".mcpr")) continue;
                long gap = Math.abs(Files.getLastModifiedTime(p).toMillis() - end);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = p;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return best;
    }

    // ---------------------------------------------------------------- watching

    /** {@code /cmreplay voir <n> [vue]}: plays file {@code viewName} (default: the zone) of recording n (1 = newest). */
    public static String watch(ServerPlayer player, int n, String viewName) {
        if (!ServerReplayBridge.viewerAvailable()) return "Le lecteur de Server Replay n'est pas disponible sur ce serveur.";
        if (ServerReplayBridge.viewerOf(player) != null) return "Tu regardes déjà un replay (/replay view close pour le fermer).";
        List<Entry> all = entries();
        if (n < 1 || n > all.size()) return all.isEmpty() ? "Aucun enregistrement dans l'index." : "Pas d'enregistrement n°" + n + " (1 à " + all.size() + ").";
        Entry e = all.get(n - 1);
        View v = viewName == null || viewName.isBlank() ? (e.views().isEmpty() ? null : e.views().getFirst()) : e.view(viewName);
        if (v == null) return "Pas de vue « " + viewName + " » dans cet enregistrement (" + names(e) + ").";
        return open(player, e, v, 0, false, 1) ? "Replay : " + e.label() + " — " + label(v) + "." : "Fichier introuvable : " + v.folder() + ".";
    }

    private static boolean open(ServerPlayer player, Entry e, View v, long ms, boolean paused, float speed) {
        Path file = file(e, v);
        if (file == null) return false;
        ServerReplayBridge.Viewer viewer = ServerReplayBridge.view(file, player);
        if (viewer == null) return false;
        WATCHING.put(player.getUUID(), new Watch(e, v));
        SENT.remove(player.getUUID());
        if (speed != 1) ServerReplayBridge.setSpeed(viewer, speed);
        // The viewer starts its playback a moment later (and a jump before that is lost): jump then, pause after
        if (ms > 0 || paused) JUMPS.put(player.getUUID(), new Jump(ms, paused, System.currentTimeMillis(), false));
        return true;
    }

    /** A position to reach once a viewer just opened has started playing. */
    private record Jump(long ms, boolean paused, long since, boolean jumped) {}

    private static final Map<UUID, Jump> JUMPS = new HashMap<>();

    private static void applyJumps(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<UUID, Jump>> it = JUMPS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Jump> e = it.next();
            Jump j = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ServerReplayBridge.Viewer v = p == null ? null : ServerReplayBridge.viewerOf(p);
            if (v == null) {
                if (now - j.since() > 10_000) it.remove();
                continue;
            }
            long at = ServerReplayBridge.progressMs(v);
            if (!j.jumped()) {
                // Started: its clock moved (or it has been a while)
                if (at <= 0 && now - j.since() < 3_000) continue;
                if (j.ms() > 0) ServerReplayBridge.jumpTo(v, j.ms());
                if (!j.paused()) it.remove();
                else e.setValue(new Jump(j.ms(), true, now, true));
                continue;
            }
            // Paused only once there (a paused viewer does not play its way to the time asked)
            if (at < j.ms() - 100 && now - j.since() < 5_000) continue;
            it.remove();
            ServerReplayBridge.setPaused(v, true);
        }
    }

    static String names(Entry e) {
        List<String> n = new ArrayList<>();
        for (View v : e.views()) n.add(v.name());
        return String.join(", ", n);
    }

    private static String label(View v) {
        return v.name().equals("zone") ? "la zone de l'épreuve" : "la vue de " + v.name();
    }

    /** Five times a second, on the server's thread: pending file switches, then the bar's status to every viewer. */
    private static void pulse(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = CLOSING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> c = it.next();
            if (now < c.getValue()) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(c.getKey());
            ServerReplayBridge.Viewer v = p == null ? null : ServerReplayBridge.viewerOf(p);
            if (v != null) ServerReplayBridge.close(v);
        }
        for (Iterator<Switch> it = SWITCHES.iterator(); it.hasNext(); ) {
            Switch s = it.next();
            if (now < s.at()) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(s.player());
            if (p == null) {
                it.remove();
                continue;
            }
            // Wait for the old viewer to be gone (it closes asynchronously)
            if (ServerReplayBridge.viewerOf(p) != null) {
                if (now - s.at() < 5_000) continue;
                it.remove();
                continue;
            }
            it.remove();
            if (!open(p, s.entry(), s.view(), s.targetMs(), s.paused(), s.speed()))
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Replay : fichier introuvable (" + s.view().folder() + ")."));
        }
        applyJumps(server, now);
        List<ServerReplayBridge.Viewer> viewers = ServerReplayBridge.viewers();
        java.util.Set<UUID> alive = new java.util.HashSet<>();
        for (ServerReplayBridge.Viewer v : viewers) {
            ServerPlayer p = ServerReplayBridge.player(v);
            if (p == null) continue;
            alive.add(p.getUUID());
            if (!ServerPlayNetworking.canSend(p, ReplayStatus.TYPE)) continue;
            int sent = SENT.merge(p.getUUID(), 1, Integer::sum);
            ServerReplayBridge.sendToViewer(v, new ClientboundCustomPayloadPacket(status(p, v, sent % 10 == 1)));
        }
        // Closed (/replay view close, left): forgotten, unless a switch is about to reopen it
        WATCHING.keySet().removeIf(id -> !alive.contains(id) && SWITCHES.stream().noneMatch(s -> s.player().equals(id)));
        SENT.keySet().retainAll(alive);
    }

    private static ReplayStatus status(ServerPlayer p, ServerReplayBridge.Viewer v, boolean withMarkers) {
        Watch w = WATCHING.get(p.getUUID());
        List<Marker> markers = null;
        if (withMarkers) {
            markers = new ArrayList<>();
            for (ServerReplayBridge.FileMarker m : ServerReplayBridge.markers(v)) {
                Integer c = w == null ? null : w.entry().colors().get(m.name());
                markers.add(new Marker(m.ms(), m.name(), c != null ? c : m.color()));
            }
        }
        List<String> views = new ArrayList<>();
        if (w != null) for (View o : w.entry().views()) views.add(o.name());
        return new ReplayStatus(w == null ? "Replay" : w.entry().label(), w == null ? "" : w.view().name(), views,
                ServerReplayBridge.progressMs(v), ServerReplayBridge.durationMs(v), ServerReplayBridge.paused(v),
                ServerReplayBridge.speed(v), w == null ? -1 : w.view().tick0(), markers);
    }

    // ---------------------------------------------------------------- /replay view cm … (the playback bar)

    private static void registerViewerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("replay").then(literal("view").then(literal("cm")
                .then(literal("seek").then(argument("ms", LongArgumentType.longArg(0)).executes(c -> withViewer(c, v ->
                        seek(v, LongArgumentType.getLong(c, "ms"))))))
                .then(literal("speed").then(argument("x", FloatArgumentType.floatArg(0.05f, 16f)).executes(c -> withViewer(c, v ->
                        ServerReplayBridge.setSpeed(v, FloatArgumentType.getFloat(c, "x"))))))
                .then(literal("pause").executes(c -> withViewer(c, v -> ServerReplayBridge.setPaused(v, true))))
                .then(literal("play").executes(c -> withViewer(c, v -> ServerReplayBridge.setPaused(v, false))))
                .then(literal("toggle").executes(c -> withViewer(c, v -> ServerReplayBridge.setPaused(v, !ServerReplayBridge.paused(v)))))
                .then(literal("vue").then(argument("vue", StringArgumentType.greedyString()).executes(c -> withViewer(c, v ->
                        switchView(c.getSource().getServer(), v, StringArgumentType.getString(c, "vue"))))))
                .then(literal("close").executes(c -> withViewer(c, ServerReplayBridge::close))))));
        CraftMaster.LOG.info("[Replay] Barre de lecture CraftMaster branchée sur le lecteur de Server Replay.");
    }

    private static int withViewer(CommandContext<CommandSourceStack> c, java.util.function.Consumer<ServerReplayBridge.Viewer> action) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerReplayBridge.Viewer v = p == null ? null : ServerReplayBridge.viewerOf(p);
        if (v == null) return 0;
        try {
            action.accept(v);
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Replay] Commande du lecteur : {}", e.toString());
        }
        return 1;
    }

    /**
     * Goes to {@code ms}: forward, the viewer plays its way there quickly. Backward, it starts again from the
     * beginning anyway, and once a file has played to its end it does not move at all: the file is opened again at
     * that time instead, as for a switch (same wait, and it always works).
     */
    private static void seek(ServerReplayBridge.Viewer v, long ms) {
        ServerPlayer p = ServerReplayBridge.player(v);
        Watch w = p == null ? null : WATCHING.get(p.getUUID());
        if (w != null && ms < ServerReplayBridge.progressMs(v)) reopen(p, v, w, w.view(), ms);
        else ServerReplayBridge.jumpTo(v, ms);
    }

    /** Same instant, other file of the recording: closes this viewer, the switch opens the other one a moment later. */
    private static void switchView(MinecraftServer server, ServerReplayBridge.Viewer v, String name) {
        ServerPlayer p = ServerReplayBridge.player(v);
        Watch w = p == null ? null : WATCHING.get(p.getUUID());
        if (w == null) return;
        View to = w.entry().view(name);
        if (to == null || to.equals(w.view())) return;
        reopen(p, v, w, to, alignedMs(ServerReplayBridge.progressMs(v), w.view().tick0(), to.tick0()));
    }

    private static void reopen(ServerPlayer p, ServerReplayBridge.Viewer v, Watch w, View to, long target) {
        boolean paused = ServerReplayBridge.paused(v);
        float speed = ServerReplayBridge.speed(v);
        SWITCHES.removeIf(s -> s.player().equals(p.getUUID()));
        // Stopped first, closed a moment later: nothing of it still on its way when the other file starts
        ServerReplayBridge.setPaused(v, true);
        SWITCHES.add(new Switch(p.getUUID(), w.entry(), to, target, paused, speed, System.currentTimeMillis() + 400));
        CLOSING.put(p.getUUID(), System.currentTimeMillis() + 250);
    }

    /** Time {@code ms} of a file starting at tick {@code fromTick0}, in a file starting at {@code toTick0} (same instant). */
    static long alignedMs(long ms, double fromTick0, double toTick0) {
        if (fromTick0 < 0 || toTick0 < 0) return ms;
        return Math.max(0, Math.round(ms + (fromTick0 - toTick0) * 50));
    }
}
