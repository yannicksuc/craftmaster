package fr.lordfinn.craftmaster.media;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pictures made by the server (the walked drawings, the jury's board) shown on the studio screens, the way
 * CrazyPhone sends its photos: no website, the bytes go through the game's own connection. The server keeps each
 * PNG under its SHA-1 ({@code config/craftmaster/images/<sha1>.png}) and gives it the address
 * {@code cmimg://<sha1>}; a player's game that must show that address asks for it ({@link Request}) and receives it
 * in pieces of at most 512 kB ({@link Chunk}). An address never changes content, so the games keep it in their cache.
 */
public final class ServerImages {

    private ServerImages() {}

    public static final String SCHEME = "cmimg://";
    private static final int CHUNK = 512 * 1024;
    private static final Map<String, byte[]> MEMORY = new ConcurrentHashMap<>();

    public record Request(String id) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CraftMaster.id("server_image_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.id, 64), buf -> new Request(buf.readUtf(64)));

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** A piece of a picture; {@code total} = 0: the server does not have it. */
    public record Chunk(String id, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(CraftMaster.id("server_image_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeUtf(p.id, 64);
                    buf.writeVarInt(p.index);
                    buf.writeVarInt(p.total);
                    buf.writeByteArray(p.data);
                },
                buf -> new Chunk(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK)));

        @Override
        public Type<Chunk> type() {
            return TYPE;
        }
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/images");
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Chunk.TYPE, Chunk.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, ctx) -> {
            String id = payload.id();
            byte[] png = id.matches("[0-9a-f]{40}") ? get(id) : null;
            if (png == null) {
                ctx.responseSender().sendPacket(new Chunk(id, 0, 0, new byte[0]));
                return;
            }
            int total = (png.length + CHUNK - 1) / CHUNK;
            for (int i = 0; i < total; i++) {
                int from = i * CHUNK, to = Math.min(png.length, from + CHUNK);
                ctx.responseSender().sendPacket(new Chunk(id, i, total, java.util.Arrays.copyOfRange(png, from, to)));
            }
        });
    }

    /** Keeps a PNG and returns its address for the studio screens. */
    public static String publish(byte[] png) {
        try {
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(png));
            if (MEMORY.putIfAbsent(id, png) == null) {
                Files.createDirectories(dir());
                Path f = dir().resolve(id + ".png");
                if (!Files.exists(f)) Files.write(f, png);
            }
            return SCHEME + id;
        } catch (Exception e) {
            throw new IllegalStateException("image impossible à garder : " + e.getMessage(), e);
        }
    }

    private static byte[] get(String id) {
        byte[] b = MEMORY.get(id);
        if (b != null) return b;
        try {
            Path f = dir().resolve(id + ".png");
            if (!Files.exists(f)) return null;
            b = Files.readAllBytes(f);
            MEMORY.put(id, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
