package fr.lordfinn.craftmaster.broadcast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the director asked of the automatic framing (by voice, the régie or {@code /cam}), live, not saved:
 * <ul>
 *   <li>{@link #size}: wider (+1), tighter (-1) or as it comes (0) — the follow shot's framings and the choice of a
 *       placed camera;</li>
 *   <li>{@link #avoid}: placed cameras never taken by the automatic choices (« évite la caméra 3 »);</li>
 *   <li>{@link #stay}: the player the automatic direction stays on (« reste sur Ensuri »), "" = none.</li>
 * </ul>
 * Sent to every game (the follow shot is framed on the broadcast games) when it changes and at login.
 */
public final class FollowPrefs {

    private FollowPrefs() {}

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CraftMaster.id("follow_prefs"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Payload(new String(buf.readByteArray(16 * 1024), StandardCharsets.UTF_8)));

        @Override
        public Type<Payload> type() { return TYPE; }
    }

    private static int size;
    private static final Set<String> avoid = new LinkedHashSet<>();
    private static String stay = "";

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer())));
    }

    public static int size() { return size; }

    public static boolean avoided(String cameraKey) { return avoid.contains(cameraKey.toLowerCase(Locale.ROOT)); }

    public static List<String> avoided() { return List.copyOf(avoid); }

    public static String stay() { return stay; }

    public static void size(MinecraftServer server, int s) {
        size = Math.max(-1, Math.min(1, s));
        sync(server);
    }

    public static void avoid(MinecraftServer server, String cameraKey, boolean on) {
        if (on) avoid.add(cameraKey.toLowerCase(Locale.ROOT));
        else avoid.remove(cameraKey.toLowerCase(Locale.ROOT));
        sync(server);
    }

    public static void stay(MinecraftServer server, String player) {
        stay = player == null ? "" : player;
        sync(server);
    }

    /** Back to automatic: no size, no player held (the avoided cameras stay avoided until asked). */
    public static void reset(MinecraftServer server) {
        size = 0;
        stay = "";
        sync(server);
    }

    static String json() {
        JsonObject o = new JsonObject();
        o.addProperty("size", size);
        o.addProperty("stay", stay);
        JsonArray a = new JsonArray();
        avoid.forEach(a::add);
        o.add("avoid", a);
        return new Gson().toJson(o);
    }

    private static void sync(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }

    private static void send(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, Payload.TYPE)) ServerPlayNetworking.send(p, new Payload(json()));
    }
}
