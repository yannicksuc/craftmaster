package fr.lordfinn.craftmaster.portrait;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;

import java.util.List;

/**
 * The only place portraits read scores from. Portraits show the <em>published</em> ranking; until the show's
 * data model (chantier A) adds published scores, the live ranking is used. After merging A, point
 * {@link #ranking} at the published scores (see docs/design/emission.md, section 3).
 */
public final class PortraitRanking {

    private PortraitRanking() {}

    /** A ranked player and their published total. */
    public record Entry(String player, int points) {}

    /** The season's players, best first. Empty when the data is not loaded. */
    public static List<Entry> ranking(int season) {
        CraftMasterData data = CraftMaster.data();
        if (data == null) return List.of();
        // TODO(chantier A): published scores instead of the live ones
        return data.ranking(season).stream().map(e -> new Entry(e.player(), e.total())).toList();
    }

    /** A hidden season keeps its portraits anonymous until the reveal. */
    public static boolean hidden(int season) {
        CraftMasterData data = CraftMaster.data();
        return data != null && data.isHidden(season);
    }
}
