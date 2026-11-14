package fr.lordfinn.craftmaster.camera;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The entities the travelling steps track: a step's target can be an entity selector, as in commands
 * ({@code @n[type=sheep]}, {@code @p}, {@code @e[tag=star,limit=1]}) or a name. The server resolves each one every half
 * second, from where the travelling starts (so {@code @n} is the nearest one to the camera), and tells the clients
 * which entity it is; they follow it smoothly (DirectorCamera). Coordinates ("x y z") are not resolved here.
 */
public final class TrackTargets {

    private TrackTargets() {}

    /** Selector → entity id (-1: none found), sent whole when it changes. */
    public record Targets(String json) implements CustomPacketPayload {
        public static final Type<Targets> TYPE = new Type<>(CraftMaster.id("track_targets"));
        public static final StreamCodec<FriendlyByteBuf, Targets> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Targets(new String(buf.readByteArray(64 * 1024), StandardCharsets.UTF_8)));

        @Override
        public Type<Targets> type() { return TYPE; }
    }

    private static Map<String, Integer> last = Map.of();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Targets.TYPE, Targets.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) refresh(server, false);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> send(handler.getPlayer(), last)));
    }

    /** Is this target an entity to find (not empty, not coordinates)? */
    public static boolean isEntity(String target) {
        return target != null && !target.isBlank() && !target.strip().matches("-?[0-9.]+ -?[0-9.]+ -?[0-9.]+");
    }

    private static void refresh(MinecraftServer server, boolean force) {
        Map<String, Integer> now = new HashMap<>();
        for (CameraPath p : CameraPaths.all()) {
            if (p.isEmpty()) continue;
            ServerLevel level = Busts.level(server, p.world());
            if (level == null) continue;
            CameraPath.Step first = p.steps().getFirst();
            for (CameraPath.Step s : p.steps()) {
                String target = s.target();
                if (!isEntity(target) || now.containsKey(target.strip())) continue;
                now.put(target.strip(), find(server, level, new Vec3(first.x(), first.y(), first.z()), target.strip()));
            }
        }
        if (!force && now.equals(last)) return;
        last = now;
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) send(pl, now);
    }

    /** The entity a selector (or a name) gives from {@code from}, or -1. */
    static int find(MinecraftServer server, ServerLevel level, Vec3 from, String selector) {
        try {
            var parsed = new EntitySelectorParser(new StringReader(selector), true).parse();
            List<? extends Entity> found = parsed.findEntities(server.createCommandSourceStack().withLevel(level).withPosition(from).withSuppressedOutput());
            return found.isEmpty() ? -1 : found.getFirst().getId();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void send(ServerPlayer player, Map<String, Integer> map) {
        if (!ServerPlayNetworking.canSend(player, Targets.TYPE)) return;
        JsonObject o = new JsonObject();
        map.forEach(o::addProperty);
        ServerPlayNetworking.send(player, new Targets(o.toString()));
    }
}
