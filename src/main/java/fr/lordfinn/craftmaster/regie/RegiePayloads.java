package fr.lordfinn.craftmaster.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Packets of the voice control. A request id ties each exchange together, so a late answer to an old order is
 * ignored by the client.
 * <pre>
 * key pressed   client → ContextRequest(id)          → server
 *               client ← Context(id, admin, json)     ← server  (fr.lordfinn.craftmaster.show.ShowContext)
 * key released  client → local service (WAV + context) → {transcript, actions, reply}
 *               client → Actions(id, transcript, json) → server  (fr.lordfinn.craftmaster.show.ActionExecutor)
 *               client ← Result(id, ok, lines, code)   ← server
 * any time      client ← Status(admin)                 ← server  (on join and when the admin right changes)
 * </pre>
 * JSON travels as UTF-8 byte arrays (no 32k-character limit of writeUtf).
 */
public final class RegiePayloads {

    private RegiePayloads() {}

    /** Largest JSON accepted from a client (actions); the server's context can be bigger. */
    public static final int MAX_ACTIONS_JSON = 64 * 1024;
    public static final int MAX_CONTEXT_JSON = 900 * 1024;

    static void writeJson(FriendlyByteBuf buf, String json) {
        buf.writeByteArray(json.getBytes(StandardCharsets.UTF_8));
    }

    static String readJson(FriendlyByteBuf buf, int max) {
        return new String(buf.readByteArray(max), StandardCharsets.UTF_8);
    }

    /** Server → client: may this player use the voice control. */
    public record Status(boolean admin) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(CraftMaster.id("regie_status"));
        public static final StreamCodec<FriendlyByteBuf, Status> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeBoolean(p.admin), buf -> new Status(buf.readBoolean()));

        @Override
        public Type<Status> type() { return TYPE; }
    }

    /** Client → server: "send me the show context" (sent as soon as the key goes down, while LordFinn speaks). */
    public record ContextRequest(int id) implements CustomPacketPayload {
        public static final Type<ContextRequest> TYPE = new Type<>(CraftMaster.id("regie_context_request"));
        public static final StreamCodec<FriendlyByteBuf, ContextRequest> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.id), buf -> new ContextRequest(buf.readVarInt()));

        @Override
        public Type<ContextRequest> type() { return TYPE; }
    }

    /** Server → client: the context as JSON ("{}" and admin=false for a non-admin). */
    public record Context(int id, boolean admin, String json) implements CustomPacketPayload {
        public static final Type<Context> TYPE = new Type<>(CraftMaster.id("regie_context"));
        public static final StreamCodec<FriendlyByteBuf, Context> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.id);
                    buf.writeBoolean(p.admin);
                    writeJson(buf, p.json);
                },
                buf -> new Context(buf.readVarInt(), buf.readBoolean(), readJson(buf, MAX_CONTEXT_JSON)));

        @Override
        public Type<Context> type() { return TYPE; }
    }

    /** Client → server: the actions understood by the service (JSON list of the shared action schema). */
    public record Actions(int id, String transcript, String json) implements CustomPacketPayload {
        public static final Type<Actions> TYPE = new Type<>(CraftMaster.id("regie_actions"));
        public static final StreamCodec<FriendlyByteBuf, Actions> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.id);
                    buf.writeUtf(p.transcript, 1024);
                    writeJson(buf, p.json);
                },
                buf -> new Actions(buf.readVarInt(), buf.readUtf(1024), readJson(buf, MAX_ACTIONS_JSON)));

        @Override
        public Type<Actions> type() { return TYPE; }
    }

    /** Server → client: what was done. {@code code}: "" normally, "not_admin" or "bad_actions" when refused. */
    public record Result(int id, boolean ok, List<String> lines, String code) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(CraftMaster.id("regie_result"));
        public static final StreamCodec<FriendlyByteBuf, Result> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.id);
                    buf.writeBoolean(p.ok);
                    buf.writeVarInt(p.lines.size());
                    p.lines.forEach(l -> buf.writeUtf(l, 2048));
                    buf.writeUtf(p.code, 64);
                },
                buf -> {
                    int id = buf.readVarInt();
                    boolean ok = buf.readBoolean();
                    List<String> lines = new ArrayList<>();
                    for (int i = Math.min(buf.readVarInt(), 200); i > 0; i--) lines.add(buf.readUtf(2048));
                    return new Result(id, ok, lines, buf.readUtf(64));
                });

        @Override
        public Type<Result> type() { return TYPE; }
    }
}
