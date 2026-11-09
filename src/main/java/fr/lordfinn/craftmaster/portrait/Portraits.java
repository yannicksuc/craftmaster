package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.show.ShowEvents;

import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of the hanging portraits: what each one shows, the rearrangement of a gallery when scores are
 * published and the highlight during the reveal.
 * <p>
 * Entry points for the show flow (chantier A): {@link #onPublished} after "publish the scores",
 * {@link #highlight} at each step of the staged reveal.
 */
public final class Portraits {

    private Portraits() {}

    /** How long a portrait stays lit after {@link #highlight} (ticks). */
    public static final int HIGHLIGHT_TICKS = 100;

    private static Path posesFile;

    public static void register() {
        // Published scores (and each step of the staged reveal) move and light up the galleries
        ShowEvents.register(new ShowEvents.Listener() {
            @Override
            public void onPublished(MinecraftServer server, int season) {
                Portraits.onPublished(server, season);
            }

            @Override
            public void onRevealStep(MinecraftServer server, int season, int rank) {
                Portraits.highlight(server, season, rank);
            }
        });
        PortraitNetwork.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            posesFile = FabricLoader.getInstance().getConfigDir().resolve("craftmaster/poses.json");
            PoseLibraries.setServer(PoseLibrary.load(posesFile, CraftMaster.LOG));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PortraitNetwork.sendPoses(handler.getPlayer()));
        // A portrait coming back with its chunk shows the current ranking (it does not move)
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof PortraitEntity p) refresh(p);
        });
    }

    // ---------------------------------------------------------------- poses

    /** Saves the server's pose library and sends it to everyone. */
    public static void posesChanged(MinecraftServer server) {
        if (posesFile != null) PoseLibraries.server().save(posesFile, CraftMaster.LOG);
        server.getPlayerList().getPlayers().forEach(PortraitNetwork::sendPoses);
    }

    // ---------------------------------------------------------------- what a portrait shows

    private static int indexOf(List<PortraitRanking.Entry> ranking, String player) {
        for (int i = 0; i < ranking.size(); i++)
            if (ranking.get(i).player().equalsIgnoreCase(player)) return i;
        return -1;
    }

    /** Shows the player the portrait follows (its rank or its fixed player), without moving it. */
    public static void refresh(PortraitEntity p) {
        List<PortraitRanking.Entry> ranking = PortraitRanking.ranking(p.season());
        boolean hidden = PortraitRanking.hidden(p.season());
        if (!p.followsRank()) {
            int i = indexOf(ranking, p.fixedPlayer());
            p.show(i < 0 ? p.fixedPlayer() : ranking.get(i).player(), i + 1, i < 0 ? 0 : ranking.get(i).points(), hidden);
        } else {
            int r = p.rank();
            PortraitRanking.Entry e = r >= 1 && r <= ranking.size() ? ranking.get(r - 1) : null;
            p.show(e == null ? null : e.player(), r, e == null ? 0 : e.points(), hidden);
        }
    }

    /** Refreshes every loaded portrait (scores reloaded, season hidden or shown...). */
    /**
     * Updates points and the anonymous flag (hidden season) of every portrait while keeping the player each one
     * shows: moving a player to another column is the job of {@link #onPublished}, which animates it. A portrait that
     * shows nobody yet takes the player of its rank.
     */
    public static void refreshLabels(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels())
            for (PortraitEntity p : level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved())) {
                String shown = p.shownPlayer();
                if (shown.isEmpty()) {
                    refresh(p);
                    continue;
                }
                List<PortraitRanking.Entry> ranking = PortraitRanking.ranking(p.season());
                int i = indexOf(ranking, shown);
                p.show(shown, i < 0 ? p.shownRank() : i + 1, i < 0 ? 0 : ranking.get(i).points(), PortraitRanking.hidden(p.season()));
            }
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels())
            for (PortraitEntity p : level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved())) refresh(p);
    }

    // ---------------------------------------------------------------- galleries

    /** The loaded portraits of a season in a dimension. */
    public static List<PortraitEntity> gallery(ServerLevel level, int season) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved() && e.season() == season));
    }

    /**
     * Sorts a gallery from left to right as seen by the audience (who faces the pictures). The most common
     * facing of the gallery gives the direction of the row.
     */
    static void sortColumns(List<PortraitEntity> gallery) {
        Map<Direction, Integer> count = new HashMap<>();
        for (PortraitEntity p : gallery) count.merge(p.getFacing(), 1, Integer::sum);
        Direction facing = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Direction.SOUTH);
        // The audience looks the other way: its right hand is the facing's counter-clockwise neighbour
        Direction right = facing.getCounterClockWise();
        gallery.sort(Comparator.<PortraitEntity>comparingDouble(p -> {
                    BlockPos c = p.ceiling();
                    return c.getX() * right.getStepX() + c.getZ() * right.getStepZ();
                })
                .thenComparingInt(p -> p.ceiling().getY())
                .thenComparing(p -> p.getUUID().toString()));
    }

    /**
     * Scores of a season were published: each gallery of that season puts its portraits in the order of the
     * ranking, with the animation (step out, slide, step back in), and every portrait shows its player's
     * published points. A hidden season only refreshes (anonymous, nothing moves: the order would give it away).
     */
    public static void onPublished(MinecraftServer server, int season) {
        List<PortraitRanking.Entry> ranking = PortraitRanking.ranking(season);
        boolean hidden = PortraitRanking.hidden(season);
        List<String> names = ranking.stream().map(PortraitRanking.Entry::player).toList();
        for (ServerLevel level : server.getAllLevels()) {
            List<PortraitEntity> gallery = gallery(level, season);
            if (gallery.isEmpty()) continue;
            if (hidden) {
                gallery.forEach(Portraits::refresh);
                continue;
            }
            sortColumns(gallery);
            List<GalleryPlanner.Slot> slots = new ArrayList<>();
            for (int i = 0; i < gallery.size(); i++) {
                PortraitEntity p = gallery.get(i);
                slots.add(new GalleryPlanner.Slot(i, p.followsRank() ? null : p.fixedPlayer(),
                        p.shownPlayer().isEmpty() ? null : p.shownPlayer()));
            }
            List<GalleryPlanner.Placement> plan = GalleryPlanner.plan(slots, names);

            // Columns stay where they are: a portrait moving to column c takes the hook and facing found there
            List<BlockPos> columns = gallery.stream().map(PortraitEntity::ceiling).toList();
            List<Direction> facings = gallery.stream().map(PortraitEntity::getFacing).toList();
            double longest = 0;
            for (GalleryPlanner.Placement pl : plan)
                if (pl.moves()) longest = Math.max(longest, Math.sqrt(columns.get(pl.fromColumn()).distSqr(columns.get(pl.toColumn()))));
            int slide = PortraitAnimation.slideTicks(longest);
            long start = level.getGameTime() + PortraitAnimation.LEAD_TICKS;

            for (int i = 0; i < gallery.size(); i++) {
                PortraitEntity p = gallery.get(i);
                GalleryPlanner.Placement pl = plan.get(i);
                int points = pl.player() == null ? 0 : ranking.get(indexOf(ranking, pl.player())).points();
                p.show(pl.player(), pl.rank(), pl.player() == null || pl.rank() == 0 ? 0 : points, false);
                // A portrait following a rank now follows its player's rank (an empty one waits for its column's)
                p.followRankSilently(pl.rank() > 0 ? pl.rank() : pl.toColumn() + 1);
                if (pl.moves()) {
                    p.moveTo(columns.get(pl.toColumn()), pl.plane(), start, slide);
                    p.setFacing(facings.get(pl.toColumn()));
                }
            }
            CraftMaster.LOG.info("[Portraits] Saison {} ({}) : {}", season, level.dimension().identifier(),
                    plan.stream().map(pl -> (pl.player() == null ? "—" : pl.player()) + " " + pl.fromColumn() + "→" + pl.toColumn()
                            + (pl.plane() != 0 ? " (plan " + pl.plane() + ")" : "")).toList());
        }
    }

    /** Lights up, for a few seconds, the portraits showing that rank of the season (staged reveal). */
    public static void highlight(MinecraftServer server, int season, int rank) {
        for (ServerLevel level : server.getAllLevels())
            for (PortraitEntity p : gallery(level, season))
                if (p.shownRank() == rank) p.highlight(HIGHLIGHT_TICKS);
    }

    /** Season suggested for a new portrait: the latest one. */
    public static int defaultSeason() {
        var data = CraftMaster.data();
        if (data == null || data.allSeasons().isEmpty()) return 1;
        return data.allSeasons().getLast();
    }

    /** Rank suggested for a new portrait of a gallery: the next one after those already hanging. */
    public static int nextRank(ServerLevel level, int season) {
        return gallery(level, season).size() + 1;
    }
}
