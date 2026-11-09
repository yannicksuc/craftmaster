package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;

import java.util.List;

/**
 * The only place portraits read scores from: the <em>published</em> ranking, like busts and the players' screen
 * (live scores only reach the displays when an organiser publishes them).
 */
public final class PortraitRanking {

    private PortraitRanking() {}

    /** A ranked player and their published total. */
    public record Entry(String player, int points) {}

    /** The season's players, best first. Empty when the data is not loaded. */
    public static List<Entry> ranking(int season) {
        CraftMasterData data = CraftMaster.data();
        if (data == null) return List.of();
        return data.ranking(season, true).stream().map(e -> new Entry(e.player(), e.total())).toList();
    }

    /** A hidden season keeps its portraits anonymous until the reveal. */
    public static boolean hidden(int season) {
        CraftMasterData data = CraftMaster.data();
        return data != null && data.isHidden(season);
    }
}
