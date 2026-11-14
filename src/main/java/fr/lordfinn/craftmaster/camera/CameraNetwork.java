package fr.lordfinn.craftmaster.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;

/**
 * A studio camera's menu (camera drivers: right-click on its prop). The server sends the camera's settings (and
 * again after each change), the client sends the changes.
 * <pre>
 * client ← Open(json)   {key, number, name, role, fov, motion, hidden, ghost, lens: [x, y, z], yaw, pitch, paths: [...]}
 * client → Edit(json)   {key, what: "name"|"role"|"fov"|"motion"|"hidden"|"ghost"|"cut"|"look"|"angle", value}
 * </pre>
 */
public final class CameraNetwork {

    private CameraNetwork() {}

    private static final int MAX = 64 * 1024;

    public record Open(String json) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(CraftMaster.id("camera_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Open(new String(buf.readByteArray(MAX), StandardCharsets.UTF_8)));

        @Override
        public Type<Open> type() { return TYPE; }
    }

    public record Edit(String json) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(CraftMaster.id("camera_edit"));
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Edit(new String(buf.readByteArray(MAX), StandardCharsets.UTF_8)));

        @Override
        public Type<Edit> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!Permissions.canDriveCameras(player)) return;
            try {
                JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
                String key = o.get("key").getAsString();
                if (edit(player, key, o.get("what").getAsString(), o.get("value"))) open(player, key);
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("[Caméras] Menu : {}", e.toString());
            }
        });
    }

    /** Opens (or refreshes) the menu of camera {@code key}; false without the client mod. */
    public static boolean open(ServerPlayer player, String key) {
        CameraSpot s = Cameras.get(key);
        if (s == null || !ServerPlayNetworking.canSend(player, Open.TYPE)) return false;
        JsonObject o = new JsonObject();
        o.addProperty("key", key);
        o.addProperty("number", s.number());
        o.addProperty("name", s.name());
        o.addProperty("role", s.role());
        o.addProperty("fov", s.fov());
        o.addProperty("motion", s.motion());
        o.addProperty("hidden", CameraOptions.hidden(key));
        o.addProperty("ghost", CameraOptions.ghost(key));
        o.addProperty("place", s.placeName());
        JsonArray lens = new JsonArray();
        lens.add(s.x());
        lens.add(s.y());
        lens.add(s.z());
        o.add("lens", lens);
        o.addProperty("yaw", s.yaw());
        o.addProperty("pitch", s.pitch());
        JsonArray paths = new JsonArray();
        for (CameraPath path : TravellingNetwork.of(key)) {
            JsonObject po = new JsonObject();
            po.addProperty("name", path.name());
            po.addProperty("steps", path.steps().size());
            po.addProperty("duration", path.duration());
            paths.add(po);
        }
        o.add("paths", paths);
        ServerPlayNetworking.send(player, new Open(o.toString()));
        return true;
    }

    /** Applies one change; true when the menu should be refreshed. */
    private static boolean edit(ServerPlayer player, String key, String what, com.google.gson.JsonElement v) {
        MinecraftServer server = player.level().getServer();
        switch (what) {
            case "name" -> Cameras.update(server, key, s -> s.withName(v.getAsString()));
            case "role" -> Cameras.update(server, key, s -> s.withRole(v.getAsString()));
            case "fov" -> Cameras.update(server, key, s -> s.withFov(v.getAsFloat()));
            case "motion" -> Cameras.update(server, key, s -> s.withMotion(v.getAsString()));
            case "hidden" -> {
                CameraOptions.setHidden(key, v.getAsBoolean());
                Cameras.update(server, key, s -> s);
            }
            case "ghost" -> {
                CameraOptions.setGhost(key, v.getAsBoolean());
                Cameras.update(server, key, s -> s);
            }
            case "cut" -> {
                CameraSpot s = Cameras.get(key);
                if (s != null) fr.lordfinn.craftmaster.broadcast.Director.cut(server, s, -1);
                return false;
            }
            // Looks where the player looks, staying in place
            case "look" -> Cameras.update(server, key, s -> s.withRotation(player.getYRot(), player.getXRot()));
            case "angle" -> {
                JsonObject a = v.getAsJsonObject();
                Cameras.update(server, key, s -> s.withRotation(a.get("yaw").getAsFloat(), a.get("pitch").getAsFloat()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
