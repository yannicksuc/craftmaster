package fr.lordfinn.craftmaster.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Previews of the shots for the directors (in game and on the site): a still per camera, refreshed every
 * {@link #REFRESH_TICKS} and when a camera moves. The server cannot render: it asks one game to take them — the
 * broadcast game if there is one, else an organiser's — which renders each camera's view for one frame (its own
 * screen keeps its picture) and sends a small PNG back. The server keeps the last one of each camera and hands them
 * to the directors in game and to the site ({@link SiteLink}).
 */
public final class Thumbnails {

    private Thumbnails() {}

    /** Picture size (pixels) and the largest PNG accepted. */
    public static final int WIDTH = 192, HEIGHT = 108, MAX_BYTES = 200_000;
    static final int REFRESH_TICKS = 30 * 20;

    /** Server → the capturing game: take these cameras (keys). */
    public record Request(List<String> keys) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CraftMaster.id("thumb_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(String.join(";", p.keys), 32767),
                buf -> new Request(List.of(buf.readUtf(32767).split(";"))));

        @Override
        public Type<Request> type() { return TYPE; }
    }

    /** One camera's picture: from the capturing game to the server, from the server to the directors' games. */
    public record Image(String key, byte[] png) implements CustomPacketPayload {
        public static final Type<Image> TYPE = new Type<>(CraftMaster.id("thumb_image"));
        public static final StreamCodec<FriendlyByteBuf, Image> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.key);
                    buf.writeByteArray(p.png);
                }, buf -> new Image(buf.readUtf(), buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<Image> type() { return TYPE; }
    }

    private static final Map<String, byte[]> IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> VERSIONS = new ConcurrentHashMap<>();
    private static int version;
    private static UUID capturer;
    private static long lastRequest = Long.MIN_VALUE / 2;
    private static String lastCameras = "";

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Image.TYPE, Image.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Image.TYPE, Image.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Image.TYPE, (p, ctx) -> received(ctx.server(), ctx.player(), p));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            String cams = signature();
            boolean moved = !cams.equals(lastCameras);
            if (moved || server.getTickCount() - lastRequest >= REFRESH_TICKS) {
                lastCameras = cams;
                request(server);
            }
        });
        // A director joining gets the pictures already taken
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (!viewer(handler.getPlayer())) return;
            IMAGES.forEach((k, png) -> send(handler.getPlayer(), new Image(k, png)));
        }));
    }

    /** Where every camera is and looks: a change (a camera placed, moved, turned) asks for new pictures. */
    private static String signature() {
        StringBuilder b = new StringBuilder();
        for (CameraSpot s : Cameras.list().all())
            b.append(s.key()).append(Math.round(s.x() * 4)).append(Math.round(s.y() * 4)).append(Math.round(s.z() * 4))
                    .append(Math.round(s.yaw())).append(Math.round(s.pitch())).append(Math.round(s.fov())).append(';');
        return b.toString();
    }

    private static boolean viewer(ServerPlayer p) {
        return Permissions.canDriveCameras(p) && ServerPlayNetworking.canSend(p, Image.TYPE);
    }

    /** Asks a game to take the pictures of the cameras near it (the ones it has the world for). */
    static void request(MinecraftServer server) {
        lastRequest = server.getTickCount();
        ServerPlayer taker = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (Director.broadcasters().contains(p.getUUID()) && ServerPlayNetworking.canSend(p, Request.TYPE)) taker = p;
        if (taker == null) for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (viewer(p) && ServerPlayNetworking.canSend(p, Request.TYPE)) {
                taker = p;
                break;
            }
        if (taker == null) return;
        capturer = taker.getUUID();
        double reach = server.getPlayerList().getViewDistance() * 16.0;
        String world = taker.level().dimension().identifier().toString();
        List<String> keys = new ArrayList<>();
        for (CameraSpot s : Cameras.list().all()) {
            if (!s.world().equals(world)) continue;
            double dx = s.x() - taker.getX(), dz = s.z() - taker.getZ();
            if (dx * dx + dz * dz <= reach * reach) keys.add(s.key());
        }
        if (!keys.isEmpty()) ServerPlayNetworking.send(taker, new Request(keys));
    }

    private static void received(MinecraftServer server, ServerPlayer from, Image img) {
        if (!from.getUUID().equals(capturer) || img.png().length == 0 || img.png().length > MAX_BYTES) return;
        if (Cameras.list().get(img.key()) == null) return;
        IMAGES.put(img.key(), img.png());
        VERSIONS.put(img.key(), ++version);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (viewer(p)) send(p, img);
    }

    private static void send(ServerPlayer p, Image img) {
        if (ServerPlayNetworking.canSend(p, Image.TYPE)) ServerPlayNetworking.send(p, img);
    }

    /** The pictures for the site: camera key → (version, PNG). */
    static Map<String, byte[]> images() {
        return IMAGES;
    }

    static int versionOf(String key) {
        return VERSIONS.getOrDefault(key, 0);
    }
}
