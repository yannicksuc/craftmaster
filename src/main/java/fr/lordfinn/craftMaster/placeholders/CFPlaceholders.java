package fr.lordfinn.craftMaster.placeholders;

import fr.lordfinn.craftMaster.CraftMaster;
import fr.lordfinn.craftMaster.data.ScoreManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CFPlaceholders extends PlaceholderExpansion {

    private final CraftMaster plugin;
    private final ScoreManager scoreManager;

    public CFPlaceholders(CraftMaster plugin) {
        this.plugin = plugin;
        this.scoreManager = plugin.getScoreManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cfscore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LordFinn";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    /**
     * Supported placeholders:
     * - %cfscore_SEASON_TASK_PSEUDO% -> score int of PSEUDO for TASK in SEASON
     * - %cfscore_SEASON_PSEUDO% -> total score of PSEUDO for SEASON
     * - %cfscore_SEASON_PLAYERCLASSEMENT% -> returns the total score of the player ranked at position PLAYERCLASSEMENT (e.g. 1 -> first)
     *
     * NOTE: params is the part after identifier + '_' ; e.g. for %cfscore_2_task_LordFinn% params = "2_task_LordFinn"
     */
    @Override
    public @Nullable String onPlaceholderRequest(Player p, @NotNull String params) {
        if (params == null || params.isEmpty()) return "";
        String[] parts = params.split("_");
        try {
            // At least season present
            int season = Integer.parseInt(parts[0]);

            // case: %cfscore_SEASON_TASK_PSEUDO% -> length >= 3
            if (parts.length >= 3) {
                String task = parts[1];
                // recompose pseudo in case it contained underscores
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < parts.length; i++) {
                    if (i > 2) sb.append("_");
                    sb.append(parts[i]);
                }
                String pseudo = sb.toString();
                int val = scoreManager.getScore(season, task, pseudo);
                return String.valueOf(val);
            }

            // case: %cfscore_SEASON_PSEUDO% -> length == 2
            if (parts.length == 2) {
                String second = parts[1];
                // If second is number -> treat as rank for PLAYERCLASSEMENT (score of player at position)
                try {
                    int rank = Integer.parseInt(second);
                    String player = scoreManager.getPlayerAtRank(season, rank);
                    if (player == null) return "0";
                    int total = scoreManager.getTotalScore(season, player);
                    return String.valueOf(total);
                } catch (NumberFormatException ignored) {
                    // else second is pseudo
                    return String.valueOf(scoreManager.getTotalScore(season, second));
                }
            }

        } catch (Exception e) {
            return "";
        }
        return "";
    }
}
