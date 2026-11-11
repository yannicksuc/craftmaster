package fr.lordfinn.craftmaster.network;

import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.ScoreEntryBoard;
import fr.lordfinn.craftmaster.data.Season;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The referee's score entry screen (organisers): the client asks for a season's board, the server answers and
 * pushes it again after every change while the screen is open, so the screen updates in place. Scores are written
 * with the usual actions ({@code score.set} through {@link fr.lordfinn.craftmaster.trial.TrialNetwork.RunActions}),
 * so they are undoable like everything else.
 */
public final class ScoreEntryNetwork {

    private ScoreEntryNetwork() {}

    private static final int MAX_JSON = 1 << 20;
    /** Seasons to ask for: the active one, or "the screen was closed". */
    public static final int ACTIVE = -1, CLOSED = -2;

    /** Client → server: "send me this season's board" ({@link #ACTIVE}), or {@link #CLOSED}. */
    public record RequestBoard(int season, boolean open) implements CustomPacketPayload {
        public static final Type<RequestBoard> TYPE = new Type<>(CraftMaster.id("score_board_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestBoard> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.season + 2);
                    buf.writeBoolean(p.open);
                }, buf -> new RequestBoard(buf.readVarInt() - 2, buf.readBoolean()));

        @Override
        public Type<RequestBoard> type() { return TYPE; }
    }

    /** Server → client: a board ({@link ScoreEntryBoard}); {@code open} opens the screen, else only refreshes it. */
    public record Board(boolean open, String json) implements CustomPacketPayload {
        public static final Type<Board> TYPE = new Type<>(CraftMaster.id("score_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeBoolean(p.open);
                    buf.writeUtf(p.json, MAX_JSON);
                }, buf -> new Board(buf.readBoolean(), buf.readUtf(MAX_JSON)));

        @Override
        public Type<Board> type() { return TYPE; }
    }

    /** Organisers whose screen is open → the season it shows. */
    private static final Map<UUID, Integer> VIEWERS = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RequestBoard.TYPE, RequestBoard.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Board.TYPE, Board.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestBoard.TYPE, (p, ctx) -> {
            if (p.season() == CLOSED) VIEWERS.remove(ctx.player().getUUID());
            else send(ctx.player(), p.season(), p.open());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.player.getUUID()));
    }

    /** Opens (or refreshes) an organiser's score entry screen on a season ({@link #ACTIVE} = the active one). */
    public static void send(ServerPlayer player, int season, boolean open) {
        CraftMasterData data = CraftMaster.data();
        if (data == null || !Permissions.isAdmin(player)) return;
        if (!ServerPlayNetworking.canSend(player, Board.TYPE)) {
            if (open) player.sendSystemMessage(Component.translatable("message.craftmaster.no_client_mod").withStyle(ChatFormatting.RED));
            return;
        }
        if (season == ACTIVE) {
            Season s = data.activeSeason();
            season = s == null ? -1 : s.id;
        }
        JsonObject board = season < 0 ? null : ScoreEntryBoard.build(data, season);
        if (board == null) {
            if (open) player.sendSystemMessage(Component.literal("Aucune saison : créez-en une d'abord.").withStyle(ChatFormatting.RED));
            return;
        }
        VIEWERS.put(player.getUUID(), season);
        ServerPlayNetworking.send(player, new Board(open, board.toString()));
    }

    /** After any change: every open screen gets its season again. */
    public static void refresh(MinecraftServer server) {
        if (CraftMaster.data() == null || VIEWERS.isEmpty()) return;
        Map<Integer, String> built = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Integer season = VIEWERS.get(p.getUUID());
            if (season == null || !Permissions.isAdmin(p) || !ServerPlayNetworking.canSend(p, Board.TYPE)) continue;
            String json = built.computeIfAbsent(season, s -> {
                JsonObject b = ScoreEntryBoard.build(CraftMaster.data(), s);
                return b == null ? null : b.toString();
            });
            if (json != null) ServerPlayNetworking.send(p, new Board(false, json));
        }
    }
}
