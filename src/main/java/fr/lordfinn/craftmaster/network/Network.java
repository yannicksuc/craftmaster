package fr.lordfinn.craftmaster.network;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Season;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The ranking screen's data: asked by the client (key K), sent by the server (and re-sent when scores change).
 * Players get the published scores, organisers the live ones with the published totals beside.
 */
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

    /**
     * One line of the ranking: total and the score of each round of {@link Ranking#tasks}. {@code publishedTotal}
     * is the published total (equal to {@code total} in a players' ranking).
     */
    public record Row(String player, int total, int publishedTotal, List<Integer> perTask) {}

    /**
     * Server → client: a season's ranking. {@code open} asks the client to open the screen; otherwise it only
     * refreshes a screen already showing that season. {@code seasons} lists the seasons this player may see;
     * {@code hidden}: players cannot see this season yet (admins still get its rows, players get none).
     * {@code live}: the rows are the live scores (organisers), else the published ones. {@code name}: the season's
     * name when it is not the default "Saison N", else empty.
     */
    public record Ranking(boolean open, int season, String name, boolean hidden, boolean live, List<Integer> seasons,
                          List<String> tasks, List<Row> rows) implements CustomPacketPayload {
        public static final Type<Ranking> TYPE = new Type<>(CraftMaster.id("ranking"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ranking> CODEC = CustomPacketPayload.codec(Ranking::write, Ranking::read);

        /** Shown while the server answers a season change. */
        public static Ranking empty(int season, List<Integer> seasons) {
            return new Ranking(false, season, "", false, false, seasons, List.of(), List.of());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeVarInt(season);
            buf.writeUtf(name);
            buf.writeBoolean(hidden);
            buf.writeBoolean(live);
            buf.writeVarInt(seasons.size());
            seasons.forEach(buf::writeVarInt);
            buf.writeVarInt(tasks.size());
            tasks.forEach(buf::writeUtf);
            buf.writeVarInt(rows.size());
            for (Row r : rows) {
                buf.writeUtf(r.player());
                buf.writeVarInt(r.total());
                buf.writeVarInt(r.publishedTotal());
                r.perTask().forEach(buf::writeVarInt);
            }
        }

        private static Ranking read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int season = buf.readVarInt();
            String name = buf.readUtf();
            boolean hidden = buf.readBoolean();
            boolean live = buf.readBoolean();
            List<Integer> seasons = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) seasons.add(buf.readVarInt());
            List<String> tasks = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) tasks.add(buf.readUtf());
            List<Row> rows = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                String player = buf.readUtf();
                int total = buf.readVarInt();
                int published = buf.readVarInt();
                List<Integer> per = new ArrayList<>();
                for (int t = 0; t < tasks.size(); t++) per.add(buf.readVarInt());
                rows.add(new Row(player, total, published, per));
            }
            return new Ranking(open, season, name, hidden, live, seasons, tasks, rows);
        }

        @Override
        public Type<Ranking> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RequestRanking.TYPE, RequestRanking.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Ranking.TYPE, Ranking.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestRanking.TYPE, (payload, ctx) -> sendRanking(ctx.player(), payload.season(), true));
    }

    /** Seasons the player may look at: all of them for admins, the ones not hidden for the others. */
    public static List<Integer> visibleSeasons(boolean admin) {
        CraftMasterData data = CraftMaster.data();
        return data.allSeasons().stream().filter(s -> admin || !data.isHidden(s)).toList();
    }

    /** Latest season the player may see, or -1 if there is none. */
    public static int latestSeason(boolean admin) {
        List<Integer> seasons = visibleSeasons(admin);
        return seasons.isEmpty() ? -1 : seasons.getLast();
    }

    /**
     * The ranking of a season: live scores for organisers, published scores for players. Columns are the rounds
     * still listed; points of other rounds only count in the total.
     */
    public static Ranking build(int season, boolean admin, boolean open) {
        CraftMasterData data = CraftMaster.data();
        Season s = data.season(season);
        String name = s == null || s.name.equals("Saison " + season) ? "" : s.name;
        if (s == null) return new Ranking(open, season, name, false, admin, visibleSeasons(admin), List.of(), List.of());
        List<String> tasks = s.activeLabels();
        Map<String, Map<String, Integer>> live = s.labelledScores();
        Map<String, Map<String, Integer>> published = s.published == null ? Map.of() : s.published.scores();
        Map<String, Map<String, Integer>> shown = admin ? live : published;
        List<Row> rows = new ArrayList<>();
        for (CraftMasterData.Entry e : s.ranking(!admin)) {
            Map<String, Integer> scores = shown.getOrDefault(e.player(), Map.of());
            rows.add(new Row(e.player(), e.total(), data.publishedTotal(season, e.player()),
                    tasks.stream().map(t -> scores.getOrDefault(t, 0)).toList()));
        }
        return new Ranking(open, season, name, s.hidden, admin, visibleSeasons(admin), tasks, rows);
    }

    /**
     * Opens (or refreshes) the player's ranking screen. A hidden season, or no season at all, gives a short
     * message instead.
     *
     * @param season the season, or -1 for the latest one the player may see
     * @return false if the player's game does not have the mod
     */
    public static boolean sendRanking(ServerPlayer player, int season, boolean open) {
        if (CraftMaster.data() == null || !ServerPlayNetworking.canSend(player, Ranking.TYPE)) return false;
        boolean admin = Permissions.isAdmin(player);
        if (season < 0) season = latestSeason(admin);
        if (season < 0 || (!admin && CraftMaster.data().isHidden(season))) {
            player.sendOverlayMessage(Component.translatable("message.craftmaster.ranking_unavailable").withStyle(ChatFormatting.GRAY));
            return true;
        }
        ServerPlayNetworking.send(player, build(season, admin, open));
        return true;
    }

    /** Refreshes the ranking screens open on that season (each player gets what they are allowed to see). */
    public static void broadcast(MinecraftServer server, int season) {
        if (CraftMaster.data() == null) return;
        Ranking forAdmins = null, forPlayers = null;
        boolean hidden = CraftMaster.data().isHidden(season);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(p, Ranking.TYPE)) continue;
            if (Permissions.isAdmin(p)) {
                if (forAdmins == null) forAdmins = build(season, true, false);
                ServerPlayNetworking.send(p, forAdmins);
            } else {
                // A screen left open on a season that just got hidden is emptied
                if (forPlayers == null) forPlayers = hidden
                        ? new Ranking(false, season, "", true, false, visibleSeasons(false), List.of(), List.of())
                        : build(season, false, false);
                ServerPlayNetworking.send(p, forPlayers);
            }
        }
    }
}
