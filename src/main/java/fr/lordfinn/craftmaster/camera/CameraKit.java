package fr.lordfinn.craftmaster.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A camera picked up (Shift + hit): its item keeps its settings and its travellings, saved with where the camera
 * stood. Placed again, if it had travellings, the player is asked: do they follow the camera (moved and turned with
 * it) or stay where they were in the world?
 */
public final class CameraKit {

    private CameraKit() {}

    /** Server → client: ask how to place the travellings of camera {@code key}. */
    public record Ask(String json) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(CraftMaster.id("camera_kit_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.json.getBytes(StandardCharsets.UTF_8)),
                buf -> new Ask(new String(buf.readByteArray(4096), StandardCharsets.UTF_8)));

        @Override
        public Type<Ask> type() { return TYPE; }
    }

    /** A camera placed whose travellings wait for the player's answer. */
    private record Pending(String key, JsonObject kit) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Ask.TYPE, Ask.CODEC);
        // Gone without answering: the travellings follow the camera
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> answer(handler.getPlayer(), true)));
    }

    // ---------------------------------------------------------------- picking up

    /** The item of a camera picked up: its settings and travellings in it. */
    public static ItemStack pickUp(MinecraftServer server, CameraEntity e) {
        ItemStack stack = new ItemStack(ModContent.CAMERA_ITEM);
        String key = e.cameraKey();
        CameraSpot spot = Cameras.get(key);
        if (spot == null) return stack;
        JsonObject o = new JsonObject();
        o.addProperty("name", spot.name() == null ? "" : spot.name());
        o.addProperty("role", spot.role() == null ? "" : spot.role());
        o.addProperty("fov", spot.fov());
        o.addProperty("motion", spot.motion() == null ? "" : spot.motion());
        o.addProperty("hidden", CameraOptions.hidden(key));
        o.addProperty("ghost", CameraOptions.ghost(key));
        o.addProperty("x", spot.x());
        o.addProperty("y", spot.y());
        o.addProperty("z", spot.z());
        o.addProperty("yaw", spot.yaw());
        JsonArray paths = new JsonArray();
        for (CameraPath p : TravellingNetwork.of(key)) paths.add(CameraPaths.toJson(p));
        o.add("paths", paths);
        CompoundTag tag = new CompoundTag();
        tag.putString("kit", o.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        String name = spot.name() == null || spot.name().isBlank() ? "Caméra" : "Caméra « " + spot.name() + " »";
        int n = paths.size();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name + (n == 0 ? "" : n == 1 ? " · 1 travelling" : " · " + n + " travellings"))
                .withStyle(ChatFormatting.GOLD));
        return stack;
    }

    /** What a picked-up camera's item keeps, or null for a new camera. */
    public static JsonObject kit(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String s = data.copyTag().getStringOr("kit", "");
        if (s.isEmpty()) return null;
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- placing again

    /** A new camera from a kit: its settings (name, role, field of view, motion). */
    public static CameraSpot withSettings(CameraSpot spot, JsonObject kit) {
        if (kit == null) return spot;
        CameraSpot s = spot;
        if (kit.has("name") && !kit.get("name").getAsString().isBlank()) s = s.withName(kit.get("name").getAsString());
        if (kit.has("role")) s = s.withRole(kit.get("role").getAsString());
        if (kit.has("fov")) s = s.withFov(kit.get("fov").getAsFloat());
        if (kit.has("motion") && !kit.get("motion").getAsString().isBlank()) s = s.withMotion(kit.get("motion").getAsString());
        return s;
    }

    /** The camera is placed: its options back, and the question about its travellings if it had some. */
    public static void placed(ServerPlayer player, CameraSpot spot, JsonObject kit) {
        if (kit == null) return;
        MinecraftServer server = player.level().getServer();
        CameraOptions.setHidden(spot.key(), kit.has("hidden") && kit.get("hidden").getAsBoolean());
        CameraOptions.setGhost(spot.key(), !kit.has("ghost") || kit.get("ghost").getAsBoolean());
        Cameras.update(server, spot.key(), s -> s);
        int n = kit.has("paths") ? kit.getAsJsonArray("paths").size() : 0;
        if (n == 0) return;
        answer(player, true);
        PENDING.put(player.getUUID(), new Pending(spot.key(), kit));
        if (!ServerPlayNetworking.canSend(player, Ask.TYPE)) {
            answer(player, true);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("camera", spot.key());
        o.addProperty("label", spot.label());
        o.addProperty("count", n);
        ServerPlayNetworking.send(player, new Ask(o.toString()));
    }

    /** The answer: the travellings follow the camera ({@code relative}) or stay where they were. */
    public static void answer(ServerPlayer player, boolean relative) {
        Pending p = PENDING.remove(player.getUUID());
        if (p == null) return;
        MinecraftServer server = player.level().getServer();
        CameraSpot spot = Cameras.get(p.key);
        if (spot == null) return;
        double ox = p.kit.get("x").getAsDouble(), oy = p.kit.get("y").getAsDouble(), oz = p.kit.get("z").getAsDouble();
        float turn = relative ? spot.yaw() - p.kit.get("yaw").getAsFloat() : 0;
        double rad = Math.toRadians(turn), cos = Math.cos(rad), sin = Math.sin(rad);
        int added = 0;
        for (JsonElement el : p.kit.getAsJsonArray("paths")) {
            CameraPath old = CameraPaths.fromJson(el.getAsJsonObject());
            List<CameraPath.Step> steps = new ArrayList<>();
            for (CameraPath.Step s : old.steps()) {
                if (!relative) {
                    steps.add(s);
                    continue;
                }
                double[] at = moved(s.x(), s.y(), s.z(), ox, oy, oz, spot, cos, sin);
                CameraPath.Step m = s.withPlace(at[0], at[1], at[2], s.yaw() + turn, s.pitch());
                // A place it looks at moves with it too
                double[] target = s.targetPlace();
                if (target != null) {
                    double[] t = moved(target[0], target[1], target[2], ox, oy, oz, spot, cos, sin);
                    m = m.withTarget(String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", t[0], t[1], t[2]));
                }
                steps.add(m);
            }
            String base = CameraPath.normalizeName("cam" + spot.number() + (spot.lieu().equals(CameraSpot.STUDIO) ? "" : "-" + spot.lieu()));
            String name = base + "-1";
            for (int i = 2; CameraPaths.get(name) != null; i++) name = base + "-" + i;
            CameraPaths.put(server, new CameraPath(name, spot.lieu(), relative ? spot.world() : old.world(), steps, spot.key()));
            added++;
        }
        player.sendSystemMessage(Component.literal(added + (added == 1 ? " travelling remis" : " travellings remis")
                + (relative ? ", ils suivent la caméra." : " à leur place d'avant.")).withStyle(ChatFormatting.GOLD));
    }

    /** A point of the old camera's surroundings, moved and turned with the camera. */
    private static double[] moved(double x, double y, double z, double ox, double oy, double oz, CameraSpot spot, double cos, double sin) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        return new double[]{spot.x() + dx * cos - dz * sin, spot.y() + dy, spot.z() + dx * sin + dz * cos};
    }
}
