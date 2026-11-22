package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.show.Spectators;
import fr.lordfinn.craftmaster.trial.Judging;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The small live picture of the path of the contestant being watched. The server decides who gets it: the organisers
 * and the real spectators, never a contestant (one who is drawing, or one who finished in a trial where they stay on
 * the ground), so a path is simply not sent to them. Only the new points travel; the whole path once when the viewer
 * moves to another player; nothing at all when nobody is drawing.
 */
public final class DrawingPreview {

    private DrawingPreview() {}

    /** Server → client: a piece of the path ({@code player} empty = nothing to show). */
    public record Trace(String player, boolean reset, boolean live, int w, int d, float[] xz) implements CustomPacketPayload {
        public static final Type<Trace> TYPE = new Type<>(CraftMaster.id("drawing_trace"));
        public static final StreamCodec<FriendlyByteBuf, Trace> CODEC = CustomPacketPayload.codec((t, b) -> {
            b.writeUtf(t.player, 64);
            b.writeBoolean(t.reset);
            b.writeBoolean(t.live);
            b.writeVarInt(t.w);
            b.writeVarInt(t.d);
            b.writeVarInt(t.xz.length);
            for (float f : t.xz) b.writeFloat(f);
        }, b -> {
            String player = b.readUtf(64);
            boolean reset = b.readBoolean(), live = b.readBoolean();
            int w = b.readVarInt(), d = b.readVarInt(), n = Math.min(b.readVarInt(), MAX_BATCH * 2);
            float[] xz = new float[n];
            for (int i = 0; i < n; i++) xz[i] = b.readFloat();
            return new Trace(player, reset, live, w, d, xz);
        });

        @Override
        public Type<Trace> type() { return TYPE; }
    }

    /** Client → server: this game shows the show's feed (F7) right now. */
    public record Feed(boolean on) implements CustomPacketPayload {
        public static final Type<Feed> TYPE = new Type<>(CraftMaster.id("drawing_feed"));
        public static final StreamCodec<FriendlyByteBuf, Feed> CODEC = CustomPacketPayload.codec((p, b) -> b.writeBoolean(p.on), b -> new Feed(b.readBoolean()));

        @Override
        public Type<Feed> type() { return TYPE; }
    }

    /** Most points in one packet. */
    private static final int MAX_BATCH = 4000;
    public static final List<String> CORNERS = List.of("hg", "hd", "bg", "bd");
    public static final Map<String, Integer> SIZES = new LinkedHashMap<>(Map.of("petit", 96, "moyen", 132, "grand", 180));

    static final class Prefs {
        boolean on = true;
        String coin = "bd";
        String taille = "moyen";
    }

    static final class Saved {
        /** On the broadcast games (F8): off until the master turns it on. */
        boolean antenne;
        Map<String, Prefs> joueurs = new LinkedHashMap<>();
    }

    private record Sent(String player, int points, boolean live) {}

    private static Saved saved = new Saved();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static final Set<UUID> ON_FEED = new HashSet<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/apercu.json");
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(saved));
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Aperçu] réglages non enregistrés : {}", e.getMessage());
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Trace.TYPE, Trace.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Feed.TYPE, Feed.CODEC);
        try {
            if (Files.exists(file())) saved = GSON.fromJson(Files.readString(file()), Saved.class);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Aperçu] réglages illisibles : {}", e.getMessage());
        }
        if (saved == null) saved = new Saved();
        if (saved.joueurs == null) saved.joueurs = new LinkedHashMap<>();
        ServerPlayNetworking.registerGlobalReceiver(Feed.TYPE, (p, ctx) -> {
            if (p.on()) ON_FEED.add(ctx.player().getUUID());
            else ON_FEED.remove(ctx.player().getUUID());
        });
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> server.execute(() -> pushPrefs(h.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            SENT.remove(h.getPlayer().getUUID());
            ON_FEED.remove(h.getPlayer().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 4 == 0) tick(server);
        });
    }

    private static Prefs prefs(ServerPlayer p) {
        return saved.joueurs.computeIfAbsent(p.getUUID().toString(), k -> new Prefs());
    }

    private static void pushPrefs(ServerPlayer p) {
        if (!ServerPlayNetworking.canSend(p, BroadcastNetwork.ShowCue.TYPE)) return;
        Prefs f = prefs(p);
        ServerPlayNetworking.send(p, new BroadcastNetwork.ShowCue(BroadcastNetwork.Cues.PREVIEW, f.on ? "on" : "off", f.coin,
                String.valueOf(SIZES.getOrDefault(f.taille, 132)), 0));
    }

    // ---------------------------------------------------------------- who sees what

    /** May this game receive paths at all: an organiser who is not drawing, a spectator, nobody else. */
    public static boolean mayView(ServerPlayer v) {
        String name = Judging.contestant(v);
        if (name != null && Drawing.drawing(name)) return false;
        if (Permissions.isAdmin(v) || name == null) return true;
        return Spectators.eligible(v);
    }

    /** The contestant this game is looking at, or null. */
    private static String target(ServerPlayer v) {
        if (!mayView(v) || !prefs(v).on) return null;
        boolean f8 = Director.broadcasters().contains(v.getUUID());
        if (f8 && !saved.antenne) return null;
        String watched = Spectators.watchedBy(v);
        if (watched != null) return watched;
        if (f8 || ON_FEED.contains(v.getUUID()) || Spectators.watchingFeed(v)) {
            var shot = Director.shot();
            if (shot.kind() == BroadcastNetwork.SHOT_FOLLOW || shot.kind() == BroadcastNetwork.SHOT_POV) return shot.player();
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (!Drawing.anyTrace()) {
            if (!SENT.isEmpty()) {
                for (UUID id : List.copyOf(SENT.keySet())) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) clear(p);
                }
                SENT.clear();
            }
            return;
        }
        for (ServerPlayer v : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(v, Trace.TYPE)) continue;
            String t = target(v);
            Drawing.View view = t == null ? null : Drawing.view(t);
            Sent s = SENT.get(v.getUUID());
            if (view == null) {
                if (s != null) {
                    clear(v);
                    SENT.remove(v.getUUID());
                }
                continue;
            }
            int n = view.points().size();
            boolean whole = s == null || !s.player().equalsIgnoreCase(t) || n < s.points();
            if (!whole && n == s.points() && view.live() == s.live()) continue;
            send(v, t, view, whole ? 0 : s.points());
            SENT.put(v.getUUID(), new Sent(t, n, view.live()));
        }
    }

    private static void clear(ServerPlayer p) {
        ServerPlayNetworking.send(p, new Trace("", true, false, 0, 0, new float[0]));
    }

    private static void send(ServerPlayer v, String player, Drawing.View view, int from) {
        List<float[]> pts = view.points();
        boolean reset = from == 0;
        int i = from;
        do {
            int to = Math.min(pts.size(), i + MAX_BATCH);
            float[] xz = new float[(to - i) * 2];
            for (int k = i; k < to; k++) {
                xz[(k - i) * 2] = pts.get(k)[0];
                xz[(k - i) * 2 + 1] = pts.get(k)[1];
            }
            ServerPlayNetworking.send(v, new Trace(player, reset, view.live(), view.w(), view.d(), xz));
            reset = false;
            i = to;
        } while (i < pts.size());
    }

    /** For the N menu. */
    public static JsonObject state(ServerPlayer p) {
        JsonObject o = new JsonObject();
        Prefs f = prefs(p);
        o.addProperty("permis", mayView(p));
        o.addProperty("on", f.on);
        o.addProperty("coin", f.coin);
        o.addProperty("taille", f.taille);
        o.addProperty("antenne", saved.antenne);
        return o;
    }

    // ---------------------------------------------------------------- command

    /** {@code /cm apercu [on|off|basculer | coin <…> | taille <…> | antenne on|off | statut]}. */
    public static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name)
                .executes(c -> say(c, describe(c.getSource().getPlayerOrException())))
                .then(literal("on").executes(c -> setOn(c, true)))
                .then(literal("off").executes(c -> setOn(c, false)))
                .then(literal("basculer").executes(c -> setOn(c, !prefs(c.getSource().getPlayerOrException()).on)))
                .then(literal("coin").then(argument("coin", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CORNERS, b)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String v = StringArgumentType.getString(c, "coin").toLowerCase();
                            if (!CORNERS.contains(v)) return fail(c, "Coin : " + String.join(", ", CORNERS) + " (haut/bas, gauche/droite).");
                            prefs(p).coin = v;
                            save();
                            pushPrefs(p);
                            return say(c, describe(p));
                        })))
                .then(literal("taille").then(argument("taille", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SIZES.keySet(), b)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String v = StringArgumentType.getString(c, "taille").toLowerCase();
                            if (!SIZES.containsKey(v)) return fail(c, "Taille : " + String.join(", ", SIZES.keySet()) + ".");
                            prefs(p).taille = v;
                            save();
                            pushPrefs(p);
                            return say(c, describe(p));
                        })))
                .then(literal("antenne").requires(Permissions.ADMIN)
                        .then(literal("on").executes(c -> antenne(c, true)))
                        .then(literal("off").executes(c -> antenne(c, false))))
                .then(literal("statut").requires(Permissions.ADMIN).executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    List<String> out = new ArrayList<>();
                    SENT.forEach((id, s) -> {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        out.add((p == null ? id.toString() : p.getGameProfile().name()) + " → " + s.player() + " (" + s.points() + " points)");
                    });
                    return say(c, "Aperçu sur la diffusion : " + (saved.antenne ? "oui" : "non") + " · reçoivent un tracé : " + (out.isEmpty() ? "personne" : String.join(", ", out)));
                }));
    }

    private static String describe(ServerPlayer p) {
        Prefs f = prefs(p);
        return "Aperçu du tracé : " + (f.on ? "affiché" : "masqué") + ", coin " + f.coin + ", " + f.taille
                + (mayView(p) ? "" : " (pas pour un candidat pendant l'épreuve)") + ".";
    }

    private static int setOn(CommandContext<CommandSourceStack> c, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        prefs(p).on = on;
        save();
        pushPrefs(p);
        return say(c, describe(p));
    }

    private static int antenne(CommandContext<CommandSourceStack> c, boolean on) {
        saved.antenne = on;
        save();
        return say(c, "Aperçu du tracé sur la diffusion (F8) : " + (on ? "activé" : "coupé") + ".");
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendFailure(Component.literal(text));
        return 0;
    }
}
