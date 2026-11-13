package fr.lordfinn.craftmaster.media;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.Studio;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the clips and the studio screens' videos: receives the clips the organisers' games recorded, sends
 * a clip in pieces to every game that needs it, and puts something on a named screen (a clip, a web link, a
 * contestant's live stream). The commands ({@link MediaCommands}), the voice control and the Stream Deck all go
 * through {@link #show}, so a later web control room can too.
 */
public final class MediaServer {

    private MediaServer() {}

    /** Largest clip accepted (bytes): about 4 minutes of 1080p. */
    public static final long MAX_CLIP = 300L * 1024 * 1024;
    /** Pieces sent per player per tick when a game fetches a clip (256 KiB each: 10 MiB/s). */
    private static final int PIECES_PER_TICK = 2;

    private static ClipStore store;
    private static TwitchChannels channels;

    private static final class Upload {
        final UUID player;
        final String name, source;
        final long size;
        final int durationMs;
        final Path file;
        RandomAccessFile out;
        long received;

        Upload(UUID player, String name, long size, int durationMs, String source, Path file) {
            this.player = player;
            this.name = name;
            this.size = size;
            this.durationMs = durationMs;
            this.source = source;
            this.file = file;
        }
    }

    private static final Map<String, Upload> UPLOADS = new HashMap<>();
    private record Send(String clip, Path file, long total, long[] next) {}
    private static final Map<UUID, ArrayDeque<Send>> SENDING = new HashMap<>();

    public static ClipStore clips() {
        if (store == null) store = new ClipStore(FabricLoader.getInstance().getGameDir().resolve("craftmaster/clips"));
        return store;
    }

    public static TwitchChannels channels() {
        if (channels == null) channels = new TwitchChannels(FabricLoader.getInstance().getConfigDir().resolve("craftmaster/twitch.json"));
        return channels;
    }

    public static void register() {
        MediaNetwork.register();
        ServerPlayNetworking.registerGlobalReceiver(MediaNetwork.UploadBegin.TYPE, (p, ctx) -> begin(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(MediaNetwork.UploadPiece.TYPE, (p, ctx) -> piece(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(MediaNetwork.UploadEnd.TYPE, (p, ctx) -> end(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(MediaNetwork.Fetch.TYPE, (p, ctx) -> fetch(ctx.player(), p.clip()));
        ServerTickEvents.END_SERVER_TICK.register(MediaServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> forget(h.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (String k : List.copyOf(UPLOADS.keySet())) abort(k);
            SENDING.clear();
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> MediaCommands.register(d));
    }

    // ---------------------------------------------------------------- receiving clips

    private static String key(ServerPlayer p, int upload) {
        return p.getUUID() + "/" + upload;
    }

    private static void begin(ServerPlayer player, MediaNetwork.UploadBegin p) {
        if (!Permissions.canShowImages(player)) {
            reply(player, p.upload(), "error", "", "Tu n'as pas le droit d'envoyer des clips (craftmaster:image).");
            return;
        }
        if (p.size() <= 0 || p.size() > MAX_CLIP) {
            reply(player, p.upload(), "error", "", "Clip trop lourd (" + MAX_CLIP / 1024 / 1024 + " Mo au plus).");
            return;
        }
        String k = key(player, p.upload());
        abort(k);
        try {
            Path dir = clips().dir().resolve("incoming");
            Files.createDirectories(dir);
            Path f = dir.resolve(player.getUUID() + "-" + p.upload() + ".part");
            Upload u = new Upload(player.getUUID(), p.name(), p.size(), p.durationMs(), p.source(), f);
            u.out = new RandomAccessFile(f.toFile(), "rw");
            u.out.setLength(0);
            UPLOADS.put(k, u);
            reply(player, p.upload(), "ready", "", "");
        } catch (IOException e) {
            reply(player, p.upload(), "error", "", "Le serveur ne peut pas écrire le clip : " + e.getMessage());
        }
    }

    private static void piece(ServerPlayer player, MediaNetwork.UploadPiece p) {
        Upload u = UPLOADS.get(key(player, p.upload()));
        if (u == null || p.offset() != u.received || u.received + p.data().length > u.size) return;
        try {
            u.out.write(p.data());
            u.received += p.data().length;
        } catch (IOException e) {
            abort(key(player, p.upload()));
            reply(player, p.upload(), "error", "", "Écriture du clip impossible : " + e.getMessage());
        }
    }

    private static void end(ServerPlayer player, MediaNetwork.UploadEnd p) {
        String k = key(player, p.upload());
        Upload u = UPLOADS.remove(k);
        if (u == null) return;
        try {
            u.out.close();
            if (u.received != u.size) {
                Files.deleteIfExists(u.file);
                reply(player, p.upload(), "error", "", "Clip incomplet (" + u.received + " / " + u.size + " octets) : renvoie-le.");
                return;
            }
            ClipStore.Clip c = clips().add(u.file, u.name, player.getGameProfile().name(), u.durationMs, u.source, LocalDateTime.now());
            CraftMaster.LOG.info("[Clips] {} reçu de {} ({} Mo, {} s)", c.id, c.author, c.size / 1024 / 1024, c.durationMs / 1000);
            reply(player, p.upload(), "done", c.id, c.name);
        } catch (IOException e) {
            reply(player, p.upload(), "error", "", "Clip non enregistré : " + e.getMessage());
        }
    }

    private static void abort(String k) {
        Upload u = UPLOADS.remove(k);
        if (u == null) return;
        try {
            u.out.close();
            Files.deleteIfExists(u.file);
        } catch (IOException ignored) {
            // already gone
        }
    }

    private static void reply(ServerPlayer player, int upload, String state, String clip, String message) {
        if (ServerPlayNetworking.canSend(player, MediaNetwork.UploadReply.TYPE))
            ServerPlayNetworking.send(player, new MediaNetwork.UploadReply(upload, state, clip, message));
    }

    // ---------------------------------------------------------------- sending clips

    private static void fetch(ServerPlayer player, String id) {
        ClipStore.Clip c = ClipLinks.validId(id) ? clips().get(id) : null;
        Path f = c == null ? null : clips().file(id);
        if (c == null || !Files.isRegularFile(f)) {
            ServerPlayNetworking.send(player, new MediaNetwork.Piece(id == null ? "" : id, 0, 0, new byte[0], "clip introuvable sur le serveur"));
            return;
        }
        ArrayDeque<Send> q = SENDING.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        for (Send s : q) if (s.clip.equals(id)) return;
        try {
            q.add(new Send(id, f, Files.size(f), new long[]{0}));
        } catch (IOException e) {
            ServerPlayNetworking.send(player, new MediaNetwork.Piece(id, 0, 0, new byte[0], "lecture impossible sur le serveur"));
        }
    }

    private static void tick(MinecraftServer server) {
        if (SENDING.isEmpty()) return;
        var it = SENDING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            ArrayDeque<Send> q = e.getValue();
            if (player == null || q.isEmpty()) {
                it.remove();
                continue;
            }
            Send s = q.peek();
            try (RandomAccessFile in = new RandomAccessFile(s.file.toFile(), "r")) {
                for (int i = 0; i < PIECES_PER_TICK && s.next[0] < s.total; i++) {
                    int n = (int) Math.min(MediaNetwork.DOWNLOAD_PIECE, s.total - s.next[0]);
                    byte[] data = new byte[n];
                    in.seek(s.next[0]);
                    in.readFully(data);
                    ServerPlayNetworking.send(player, new MediaNetwork.Piece(s.clip, s.total, s.next[0], data, ""));
                    s.next[0] += n;
                }
            } catch (IOException ex) {
                ServerPlayNetworking.send(player, new MediaNetwork.Piece(s.clip, s.total, s.next[0], new byte[0], "lecture impossible sur le serveur"));
                s.next[0] = s.total;
            }
            if (s.next[0] >= s.total) q.poll();
        }
    }

    private static void forget(UUID player) {
        SENDING.remove(player);
        for (String k : List.copyOf(UPLOADS.keySet())) if (k.startsWith(player + "/")) abort(k);
    }

    // ---------------------------------------------------------------- screens

    /** The loaded studio screens, in every dimension. */
    public static List<ImageScreenEntity> screens(MinecraftServer server) {
        List<ImageScreenEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) out.addAll(level.getEntities(Studio.STUDIO_SCREEN, e -> !e.isRemoved()));
        return out;
    }

    /** The screens with this name (several screens can share one: they show the same thing). "tout"/"all": every named one. */
    public static List<ImageScreenEntity> screens(MinecraftServer server, String name) {
        String n = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        List<ImageScreenEntity> out = new ArrayList<>();
        for (ImageScreenEntity s : screens(server)) {
            String sn = s.screenName().toLowerCase(Locale.ROOT);
            if (sn.isEmpty()) continue;
            if (n.equals("tout") || n.equals("all") || n.equals("toutes") || sn.equals(n)) out.add(s);
        }
        return out;
    }

    /** The names given to screens, sorted, once each. */
    public static List<String> screenNames(MinecraftServer server) {
        return screens(server).stream().map(ImageScreenEntity::screenName).filter(s -> !s.isEmpty()).distinct().sorted().toList();
    }

    /** What to show: kind + its argument, as the commands, the voice and the Stream Deck give it. */
    public enum Kind { CLIP, LINK, LIVE, IMAGE, OFF }

    /** Result of {@link #show}: done, or why not (French, shown as is). */
    public record Result(boolean ok, String message, String url) {
        static Result error(String m) { return new Result(false, m, ""); }
    }

    /**
     * Puts something on the screens named {@code screen}.
     * <ul>
     *   <li>CLIP: a clip of the server's list: its id, its number (1 = oldest), "dernier" / "last" / "0"
     *   (newest), "-1" (the one before)...</li>
     *   <li>LINK / IMAGE: a web address (Twitch clip, video, picture...).</li>
     *   <li>LIVE: a connected contestant's name (their linked Twitch channel), or a channel name.</li>
     *   <li>OFF: back to the test card.</li>
     * </ul>
     */
    public static Result show(MinecraftServer server, String screen, Kind kind, String arg) {
        List<ImageScreenEntity> targets = screens(server, screen);
        if (targets.isEmpty()) {
            List<String> names = screenNames(server);
            return Result.error("Aucune télé nommée « " + screen + " »" + (names.isEmpty()
                    ? " (nomme-en une : /cmtele nommer <nom>, en la regardant)." : ". Télés : " + String.join(", ", names) + "."));
        }
        String url;
        String what;
        switch (kind) {
            case OFF -> { url = ""; what = "la mire"; }
            case CLIP -> {
                ClipStore.Clip c = clip(arg);
                if (c == null) return Result.error(clips().list().isEmpty() ? "Aucun clip sur le serveur (enregistre-en un : F9 puis F10)."
                        : "Clip « " + arg + " » introuvable (/cmclip liste).");
                url = ClipLinks.of(c.id);
                what = "le clip « " + c.name + " »";
            }
            case LIVE -> {
                String channel = liveChannel(server, arg);
                if (channel == null) return Result.error("Pas de chaîne Twitch pour « " + arg + " » : /cmchaine lier <joueur> <chaîne>.");
                if (channel.isEmpty()) return Result.error(arg + " n'est pas connecté.");
                url = "https://www.twitch.tv/" + channel;
                what = "le direct de " + channel;
            }
            default -> {
                String a = arg == null ? "" : arg.strip();
                String err = fr.lordfinn.craftmaster.studio.ImageRequest.urlError(a);
                if (err != null) return Result.error(err);
                url = a;
                what = a;
            }
        }
        for (ImageScreenEntity s : targets) s.setUrl(url);
        return new Result(true, "Télé « " + screen + " » : " + what + ".", url);
    }

    /** A clip from a number, "dernier"/"last", or an id. */
    public static ClipStore.Clip clip(String arg) {
        String a = arg == null ? "" : arg.strip().toLowerCase(Locale.ROOT);
        if (a.isEmpty() || a.equals("dernier") || a.equals("last") || a.equals("latest")) return clips().byNumber(0);
        if (a.equals("avant-dernier") || a.equals("previous")) return clips().byNumber(-1);
        try {
            return clips().byNumber(Integer.parseInt(a));
        } catch (NumberFormatException e) {
            return ClipLinks.validId(a) ? clips().get(a) : null;
        }
    }

    /**
     * The channel to show for a contestant: their linked channel if they are connected ("" if not connected), the
     * text itself if it is a channel name nobody is linked to, null when nothing fits.
     */
    static String liveChannel(MinecraftServer server, String who) {
        if (who == null || who.isBlank()) return null;
        String linked = channels().channelOf(who);
        if (linked != null) {
            boolean online = server.getPlayerList().getPlayers().stream().anyMatch(p -> p.getGameProfile().name().equalsIgnoreCase(channels().playerOf(who)));
            return online ? linked : "";
        }
        // A connected player without a linked channel: say so rather than guess a channel of the same name
        boolean isPlayer = server.getPlayerList().getPlayers().stream().anyMatch(p -> p.getGameProfile().name().equalsIgnoreCase(who.strip()));
        if (isPlayer) return null;
        String asChannel = ClipLinks.twitchChannel(who);
        return asChannel.isEmpty() ? null : asChannel;
    }
}
