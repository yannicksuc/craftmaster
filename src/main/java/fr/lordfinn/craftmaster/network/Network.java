package fr.lordfinn.craftmaster.network;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The ranking screen's data: asked by the client (key K), sent by the server (and re-sent when scores change). */
public final class Network {

    private Network() {}

    /** Client → server: "send me season N" (-1 = the latest season). */
    public record RequestRanking(int season) implements CustomPacketPayload {
        public static final Type<RequestRanking> TYPE = new Type<>(CraftMaster.id("request_ranking"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestRanking> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeVarInt(p.season), buf -> new RequestRanking(buf.readVarInt()));

        @Override
        public Type<RequestRanking> type() { return TYPE; }
    }

    /** One line of the ranking: total and the score of each task of {@link Ranking#tasks}. */
    public record Row(String player, int total, List<Integer> perTask) {}

    /**
     * Server → client: a season's ranking. {@code open} asks the client to open the screen; otherwise it only
     * refreshes a screen already showing that season.
     */
    public record Ranking(boolean open, int season, List<Integer> seasons, List<String> tasks, List<Row> rows) implements CustomPacketPayload {
        public static final Type<Ranking> TYPE = new Type<>(CraftMaster.id("ranking"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ranking> CODEC = CustomPacketPayload.codec(Ranking::write, Ranking::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeVarInt(season);
            buf.writeVarInt(seasons.size());
            seasons.forEach(buf::writeVarInt);
            buf.writeVarInt(tasks.size());
            tasks.forEach(buf::writeUtf);
            buf.writeVarInt(rows.size());
            for (Row r : rows) {
                buf.writeUtf(r.player());
                buf.writeVarInt(r.total());
                r.perTask().forEach(buf::writeVarInt);
            }
        }

        private static Ranking read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int season = buf.readVarInt();
            List<Integer> seasons = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) seasons.add(buf.readVarInt());
            List<String> tasks = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) tasks.add(buf.readUtf());
            List<Row> rows = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                String player = buf.readUtf();
                int total = buf.readVarInt();
                List<Integer> per = new ArrayList<>();
                for (int t = 0; t < tasks.size(); t++) per.add(buf.readVarInt());
                rows.add(new Row(player, total, per));
            }
            return new Ranking(open, season, seasons, tasks, rows);
        }

        @Override
        public Type<Ranking> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RequestRanking.TYPE, RequestRanking.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Ranking.TYPE, Ranking.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestRanking.TYPE, (payload, ctx) -> sendRanking(ctx.player(), payload.season(), true));
    }

    public static int latestSeason() {
        List<Integer> all = CraftMaster.data().allSeasons();
        return all.isEmpty() ? 1 : all.getLast();
    }

    public static Ranking build(int season, boolean open) {
        CraftMasterData data = CraftMaster.data();
        List<String> tasks = data.tasks(season);
        List<Row> rows = new ArrayList<>();
        for (CraftMasterData.Entry e : data.ranking(season)) {
            Map<String, Integer> scores = data.scores(season, e.player());
            rows.add(new Row(e.player(), e.total(), tasks.stream().map(t -> scores.getOrDefault(t, 0)).toList()));
        }
        return new Ranking(open, season, data.allSeasons(), tasks, rows);
    }

    /** @return false if the player's game does not have the mod */
    public static boolean sendRanking(ServerPlayer player, int season, boolean open) {
        if (CraftMaster.data() == null || !ServerPlayNetworking.canSend(player, Ranking.TYPE)) return false;
        ServerPlayNetworking.send(player, build(season < 0 ? latestSeason() : season, open));
        return true;
    }

    /** Refreshes the ranking screens open on that season. */
    public static void broadcast(MinecraftServer server, int season) {
        if (CraftMaster.data() == null) return;
        Ranking r = build(season, false);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (ServerPlayNetworking.canSend(p, Ranking.TYPE)) ServerPlayNetworking.send(p, r);
    }
}
