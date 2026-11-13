package fr.lordfinn.craftmaster.score;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Season;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.Map;

/**
 * Each player's season total beside their name in the player list (Tab): the published totals of the active
 * season, the same for everyone (no mod needed). Nothing when there is no active season or it is hidden.
 * Refreshed whenever the rankings or the active season change.
 */
public final class TabScore {

    private TabScore() {}

    public static final String OBJECTIVE = "craftmaster_score";

    public static void refresh(MinecraftServer server) {
        CraftMasterData data = CraftMaster.data();
        if (server == null || data == null) return;
        ServerScoreboard board = server.getScoreboard();
        Season s = data.activeSeason();
        Objective obj = board.getObjective(OBJECTIVE);
        if (s == null || s.hidden) {
            if (obj != null) board.removeObjective(obj);
            return;
        }
        if (obj == null) obj = board.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal("Score"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        if (board.getDisplayObjective(DisplaySlot.LIST) != obj) board.setDisplayObjective(DisplaySlot.LIST, obj);
        Map<String, Integer> totals = new HashMap<>();
        for (CraftMasterData.Entry e : s.ranking(true)) totals.put(e.player(), data.publishedTotal(s.id, e.player()));
        for (PlayerScoreEntry entry : board.listPlayerScores(obj))
            if (!totals.containsKey(entry.owner())) board.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), obj);
        for (Map.Entry<String, Integer> t : totals.entrySet()) {
            var score = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(t.getKey()), obj);
            if (score.get() != t.getValue()) score.set(t.getValue());
        }
    }
}
