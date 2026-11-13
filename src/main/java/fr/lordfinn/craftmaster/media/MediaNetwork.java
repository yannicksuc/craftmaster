package fr.lordfinn.craftmaster.media;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Packets of the clips: a game sends the clip it recorded to the server in pieces (organisers), and every game
 * fetches a clip from the server in pieces when a studio screen shows it. No web host, no port to open.
 */
public final class MediaNetwork {

    private MediaNetwork() {}

    /** Piece sizes: serverbound custom packets are limited to 32 KiB, clientbound ones to 1 MiB. */
    public static final int UPLOAD_PIECE = 30_000, DOWNLOAD_PIECE = 256 * 1024;

    /** Client → server: a clip is coming ({@code size} bytes, MP4). */
    public record UploadBegin(int upload, String name, long size, int durationMs, String source) implements CustomPacketPayload {
        public static final Type<UploadBegin> TYPE = new Type<>(CraftMaster.id("clip_upload_begin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadBegin> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.upload);
                    b.writeUtf(p.name, 96);
                    b.writeVarLong(p.size);
                    b.writeVarInt(p.durationMs);
                    b.writeUtf(p.source, 64);
                },
                b -> new UploadBegin(b.readVarInt(), b.readUtf(96), b.readVarLong(), b.readVarInt(), b.readUtf(64)));

        @Override
        public Type<UploadBegin> type() { return TYPE; }
    }

    /** Client → server: one piece of the clip being sent. */
    public record UploadPiece(int upload, long offset, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadPiece> TYPE = new Type<>(CraftMaster.id("clip_upload_piece"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadPiece> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.upload);
                    b.writeVarLong(p.offset);
                    b.writeByteArray(p.data);
                },
                b -> new UploadPiece(b.readVarInt(), b.readVarLong(), b.readByteArray(UPLOAD_PIECE)));

        @Override
        public Type<UploadPiece> type() { return TYPE; }
    }

    /** Client → server: every piece was sent. */
    public record UploadEnd(int upload) implements CustomPacketPayload {
        public static final Type<UploadEnd> TYPE = new Type<>(CraftMaster.id("clip_upload_end"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadEnd> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeVarInt(p.upload), b -> new UploadEnd(b.readVarInt()));

        @Override
        public Type<UploadEnd> type() { return TYPE; }
    }

    /**
     * Server → client: answer about an upload. {@code state}: "ready" (send the pieces), "done" ({@code clip} is its
     * id), "error" ({@code message} says why, in French).
     */
    public record UploadReply(int upload, String state, String clip, String message) implements CustomPacketPayload {
        public static final Type<UploadReply> TYPE = new Type<>(CraftMaster.id("clip_upload_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadReply> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.upload);
                    b.writeUtf(p.state, 16);
                    b.writeUtf(p.clip, 64);
                    b.writeUtf(p.message, 256);
                },
                b -> new UploadReply(b.readVarInt(), b.readUtf(16), b.readUtf(64), b.readUtf(256)));

        @Override
        public Type<UploadReply> type() { return TYPE; }
    }

    /** Client → server: send me this clip. */
    public record Fetch(String clip) implements CustomPacketPayload {
        public static final Type<Fetch> TYPE = new Type<>(CraftMaster.id("clip_fetch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fetch> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeUtf(p.clip, 64), b -> new Fetch(b.readUtf(64)));

        @Override
        public Type<Fetch> type() { return TYPE; }
    }

    /** Server → client: a piece of a clip ({@code total} bytes in all; {@code error} non empty: it cannot be sent). */
    public record Piece(String clip, long total, long offset, byte[] data, String error) implements CustomPacketPayload {
        public static final Type<Piece> TYPE = new Type<>(CraftMaster.id("clip_piece"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Piece> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.clip, 64);
                    b.writeVarLong(p.total);
                    b.writeVarLong(p.offset);
                    b.writeByteArray(p.data);
                    b.writeUtf(p.error, 256);
                },
                b -> new Piece(b.readUtf(64), b.readVarLong(), b.readVarLong(), b.readByteArray(DOWNLOAD_PIECE), b.readUtf(256)));

        @Override
        public Type<Piece> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- chat wall (ChatWall)

    /**
     * Client → server: the broadcast game forwards its Twitch chat (organisers only). {@code kind}: 0 a message
     * ({@code id}, {@code author}, {@code color} 0xRRGGBB or -1, {@code text}), 1 a message deleted ({@code id}),
     * 2 a user timed out or banned ({@code author}), 3 the whole chat cleared.
     */
    public record TwitchChatIn(int kind, String id, String author, int color, String text) implements CustomPacketPayload {
        public static final int MESSAGE = 0, DELETE = 1, BAN = 2, CLEAR = 3;
        public static final Type<TwitchChatIn> TYPE = new Type<>(CraftMaster.id("chat_wall_twitch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwitchChatIn> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.kind);
                    b.writeUtf(p.id, 64);
                    b.writeUtf(p.author, 64);
                    b.writeInt(p.color);
                    b.writeUtf(p.text, 600);
                },
                b -> new TwitchChatIn(b.readVarInt(), b.readUtf(64), b.readUtf(64), b.readInt(), b.readUtf(600)));

        @Override
        public Type<TwitchChatIn> type() { return TYPE; }
    }

    /** Server → client: chat wall messages, oldest first; {@code reset}: they replace everything the game had. */
    public record ChatLines(boolean reset, java.util.List<ChatWall.Line> lines) implements CustomPacketPayload {
        public static final Type<ChatLines> TYPE = new Type<>(CraftMaster.id("chat_wall_lines"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChatLines> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeBoolean(p.reset);
                    b.writeVarInt(p.lines.size());
                    for (ChatWall.Line l : p.lines) {
                        b.writeVarInt(l.source().ordinal());
                        b.writeUtf(l.id(), 64);
                        b.writeUtf(l.author(), 64);
                        b.writeInt(l.color());
                        b.writeUtf(l.text(), 600);
                    }
                },
                b -> {
                    boolean reset = b.readBoolean();
                    int n = Math.min(b.readVarInt(), 4 * ChatWall.HISTORY);
                    java.util.List<ChatWall.Line> lines = new java.util.ArrayList<>(n);
                    ChatWall.Source[] sources = ChatWall.Source.values();
                    for (int i = 0; i < n; i++) {
                        ChatWall.Source s = sources[Math.floorMod(b.readVarInt(), sources.length)];
                        lines.add(new ChatWall.Line(s, b.readUtf(64), b.readUtf(64), b.readInt(), b.readUtf(600)));
                    }
                    return new ChatLines(reset, lines);
                });

        @Override
        public Type<ChatLines> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(UploadBegin.TYPE, UploadBegin.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadPiece.TYPE, UploadPiece.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadEnd.TYPE, UploadEnd.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UploadReply.TYPE, UploadReply.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Fetch.TYPE, Fetch.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Piece.TYPE, Piece.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TwitchChatIn.TYPE, TwitchChatIn.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChatLines.TYPE, ChatLines.CODEC);
    }
}
