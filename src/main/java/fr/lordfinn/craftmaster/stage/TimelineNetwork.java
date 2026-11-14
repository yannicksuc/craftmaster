package fr.lordfinn.craftmaster.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.block.CurtainBlock;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.media.MediaServer;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import fr.lordfinn.craftmaster.studio.SpotlightFollow;
import fr.lordfinn.craftmaster.studio.Spotlights;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The in-game timeline editor's link with the server (organisers only).
 * <pre>
 * client ← State(json)   the timeline open in the editor, the names to pick from, what plays where (on open, after
 *                        each change, and the playhead 4 times a second while something plays)
 * client → Edit(json)    {"action": "save"|"open"|"play"|"stop"|"seek"|"capture"|"close"|"delete"|"duplicate", …}
 * </pre>
 */
public final class TimelineNetwork {

    private TimelineNetwork() {}

    private static final int MAX_JSON = 512 * 1024;

    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(CraftMaster.id("timeline_state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new State(new String(buf.readByteArray(MAX_JSON), StandardCharsets.UTF_8)));

        @Override
        public Type<State> type() { return TYPE; }
    }

    public record Edit(String json) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(CraftMaster.id("timeline_edit"));
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Edit(new String(buf.readByteArray(MAX_JSON), StandardCharsets.UTF_8)));

        @Override
        public Type<Edit> type() { return TYPE; }
    }

    /** Editors open: player → the timeline they edit. */
    private static final Map<UUID, String> OPEN = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!Permissions.isAdmin(player)) return;
            try {
                handle(player, JsonParser.parseString(p.json()).getAsJsonObject());
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("[Scénographie] Éditeur : {}", e.toString());
                message(player, "Éditeur : " + e.getMessage());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> OPEN.remove(h.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (OPEN.isEmpty() || server.getTickCount() % 5 != 0) return;
            Map<String, Double> playing = Sequencer.playing(server);
            if (playing.isEmpty() && server.getTickCount() % 40 != 0) return;
            JsonObject o = new JsonObject();
            o.addProperty("kind", "playhead");
            o.add("playing", playingJson(playing));
            String json = o.toString();
            for (UUID id : List.copyOf(OPEN.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) OPEN.remove(id);
                else if (ServerPlayNetworking.canSend(p, State.TYPE)) ServerPlayNetworking.send(p, new State(json));
            }
        });
    }

    /** Opens the editor on {@code name} (null: the first timeline, or a new one). False without the mod. */
    public static boolean open(ServerPlayer player, String name) {
        if (!ServerPlayNetworking.canSend(player, State.TYPE)) return false;
        String k = name == null ? null : Scenography.key(name);
        if (k == null || !Sequences.all().containsKey(k)) {
            if (Sequences.all().isEmpty()) Sequences.create("ma sequence", 10);
            k = k != null && Sequences.all().containsKey(k) ? k : Sequences.all().keySet().iterator().next();
        }
        OPEN.put(player.getUUID(), k);
        send(player, k, null);
        return true;
    }

    private static void handle(ServerPlayer player, JsonObject o) {
        MinecraftServer server = player.level().getServer();
        String action = o.has("action") ? o.get("action").getAsString() : "";
        String name = o.has("name") ? Scenography.key(o.get("name").getAsString()) : OPEN.getOrDefault(player.getUUID(), "");
        switch (action) {
            case "open" -> open(player, name);
            case "close" -> OPEN.remove(player.getUUID());
            case "save" -> {
                Sequence s = Sequences.parse(o.get("sequence").toString());
                Sequences.put(name, s);
                OPEN.put(player.getUUID(), name);
                // No echo of the whole timeline: the editor already has it (would fight the user's next edit)
            }
            case "new" -> {
                String base = name.isEmpty() ? "sequence" : name, k = base;
                for (int i = 2; Sequences.all().containsKey(k); i++) k = base + " " + i;
                Sequences.create(k, o.has("duree") ? o.get("duree").getAsDouble() : 10);
                open(player, k);
            }
            case "duplicate" -> {
                String base = name + " copie", k = base;
                for (int i = 2; Sequences.all().containsKey(k); i++) k = base + " " + i;
                Sequences.duplicate(name, k);
                open(player, k);
            }
            case "rename" -> {
                String to = o.get("to").getAsString();
                String msg = Sequences.rename(name, to);
                message(player, msg);
                open(player, Sequences.all().containsKey(Scenography.key(to)) ? Scenography.key(to) : name);
            }
            case "delete" -> {
                message(player, Sequences.delete(name));
                open(player, null);
            }
            case "play" -> {
                if (o.has("sequence")) Sequences.put(name, Sequences.parse(o.get("sequence").toString()));
                message(player, Sequencer.play(server, name, o.has("t") ? o.get("t").getAsDouble() : 0));
            }
            case "stop" -> message(player, Sequencer.stop(o.has("all") && o.get("all").getAsBoolean() ? null : name));
            case "seek" -> {
                Sequence s = o.has("sequence") ? Sequences.parse(o.get("sequence").toString()) : Sequences.get(name);
                if (s != null) Sequencer.seek(server, s, o.get("t").getAsDouble());
            }
            case "capture" -> {
                // The live stage, for a track's target, as a keypoint: "record what I set by hand"
                Sequence.Track tr = Sequences.GSON.fromJson(o.get("track"), Sequence.Track.class);
                Sequence.Key k = capture(server, tr);
                JsonObject r = new JsonObject();
                r.addProperty("kind", "captured");
                r.addProperty("track", o.has("index") ? o.get("index").getAsInt() : -1);
                r.addProperty("t", o.has("t") ? o.get("t").getAsDouble() : 0);
                r.add("key", k == null ? null : JsonParser.parseString(Sequences.GSON.toJson(k)));
                if (k == null) message(player, "Rien à capturer pour « " + tr.cible + " ».");
                else ServerPlayNetworking.send(player, new State(r.toString()));
            }
            default -> {
            }
        }
    }

    private static void send(ServerPlayer player, String name, String note) {
        MinecraftServer server = player.level().getServer();
        JsonObject o = new JsonObject();
        o.addProperty("kind", "open");
        o.addProperty("name", name);
        Sequence s = Sequences.get(name);
        o.add("sequence", s == null ? null : JsonParser.parseString(Sequences.json(s)));
        JsonArray names = new JsonArray();
        Sequences.all().keySet().forEach(names::add);
        o.add("names", names);
        o.add("catalog", catalog(server));
        o.add("playing", playingJson(Sequencer.playing(server)));
        if (note != null) o.addProperty("note", note);
        ServerPlayNetworking.send(player, new State(o.toString()));
    }

    private static JsonObject playingJson(Map<String, Double> playing) {
        JsonObject p = new JsonObject();
        playing.forEach(p::addProperty);
        return p;
    }

    /** What the editor's pickers offer: spotlight groups, curtains, screens, seasons, points, follow targets. */
    private static JsonObject catalog(MinecraftServer server) {
        JsonObject c = new JsonObject();
        TreeSet<String> groups = new TreeSet<>();
        for (String n : Spotlights.names()) {
            groups.add(n);
            String stem = n.replaceAll("[0-9]+$", "");
            if (!stem.isEmpty() && !stem.equals(n)) groups.add(stem);
        }
        JsonArray g = new JsonArray();
        g.add("tout");
        groups.forEach(g::add);
        c.add("lumiere", g);
        JsonArray cu = new JsonArray();
        cu.add("tout");
        CraftMaster.data().stage().curtains.keySet().forEach(cu::add);
        c.add("rideau", cu);
        JsonArray sc = new JsonArray();
        for (ImageScreenEntity s : MediaServer.screens(server)) if (!s.screenName().isEmpty()) sc.add(s.screenName());
        sc.add("tout");
        c.add("ecran", sc);
        JsonArray se = new JsonArray();
        se.add("tout");
        if (CraftMaster.data() != null) CraftMaster.data().allSeasons().forEach(n -> se.add(String.valueOf(n)));
        c.add("tableaux", se);
        JsonArray pts = new JsonArray();
        pts.add("@centre");
        Scenography.book().plateau.points.keySet().forEach(k -> pts.add("@point:" + k));
        c.add("points", pts);
        JsonArray follow = new JsonArray();
        for (String r : List.of("@scene", "@maitre", "@rang:1", "@rang:2", "@rang:3", "@dernier", "@hasard", "@public", "@moment", "@candidat", "@proche"))
            follow.add(r);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) follow.add(p.getGameProfile().name());
        c.add("suit", follow);
        JsonArray fx = new JsonArray();
        Sequence.EFFECTS.forEach(fx::add);
        c.add("effets", fx);
        return c;
    }

    /** The live stage of a track's target as a keypoint (null: nothing there). */
    static Sequence.Key capture(MinecraftServer server, Sequence.Track tr) {
        Sequence.Key k = new Sequence.Key(0);
        switch (tr.type) {
            case Sequence.CURTAIN -> {
                String name = tr.cible.isBlank() || tr.cible.equals("tout") ? CraftMaster.data().stage().curtains.keySet().stream().findFirst().orElse(null) : tr.cible;
                BlockSpot b = name == null ? null : CraftMaster.data().stage().curtains.get(name);
                if (b == null) return null;
                ServerLevel level = Busts.level(server, b.world());
                BlockPos pos = new BlockPos(b.x(), b.y(), b.z());
                if (level == null || !level.isLoaded(pos)) return null;
                BlockState st = level.getBlockState(pos);
                if (!(st.getBlock() instanceof CurtainBlock)) return null;
                k.ouvert = Curtains.isHeadingOpen(level, pos, st);
                k.fondu = 0;
            }
            case Sequence.PORTRAITS -> {
                for (ServerLevel level : server.getAllLevels())
                    for (PortraitEntity p : level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved())) {
                        if (!tr.cible.isBlank() && !tr.cible.equals("tout") && !String.valueOf(p.season()).equals(tr.cible.strip())) continue;
                        k.hauteur = p.hangSetting() < 0 ? 4f : p.hangSetting();
                        k.fondu = 3;
                        return k;
                    }
                return null;
            }
            case Sequence.SCREEN, Sequence.COMMAND -> {
                return null;
            }
            default -> {
                List<SpotlightBlockEntity> list = Spotlights.select(tr.cible);
                if (list.isEmpty()) return null;
                SpotlightBlockEntity s = list.getFirst();
                k.allume = s.lit();
                k.couleur = String.format("#%06X", s.color());
                k.intensite = s.intensity();
                k.cone = Math.round(s.cone() * 10) / 10f;
                if (!s.follow().isEmpty()) k.suit = s.follow();
                else {
                    k.horizontal = (float) Math.round(s.yaw());
                    k.vertical = (float) Math.round(s.pitch());
                }
                k.effet = s.effect().isEmpty() ? Sequence.NO_EFFECT : s.effect();
                k.fondu = 2;
            }
        }
        return k;
    }

    private static void message(ServerPlayer p, String text) {
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    /** Not used by the editor; kept for the follow describe in tooltips. */
    static String follow(String f) {
        return SpotlightFollow.describe(f);
    }
}
