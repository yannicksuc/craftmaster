package fr.lordfinn.craftMaster.data;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class ScoreManager {
    private final CraftMaster plugin;

    public ScoreManager(CraftMaster plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void setScore(int season, String task, String player, int score) {
        getConfig().set("scores." + season + "." + player + "." + task, score);
        save();
        plugin.getBustManager().updateAllBusts(this);
    }

    public int getScore(int season, String task, String player) {
        return getConfig().getInt("scores." + season + "." + player + "." + task, 0);
    }

    public int getTotalScore(int season, String player) {
        String base = "scores." + season + "." + player;
        if (!getConfig().isConfigurationSection(base)) return 0;
        ConfigurationSection sec = getConfig().getConfigurationSection(base);
        int total = 0;
        for (String key : sec.getKeys(false)) {
            Object v = sec.get(key);
            if (v instanceof Number) total += ((Number) v).intValue();
            else {
                try {
                    total += Integer.parseInt(String.valueOf(v));
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    /**
     * Retourne la liste triée (player -> total) descendant
     */
    public List<Map.Entry<String, Integer>> getRanking(int season) {
        String base = "scores." + season;
        if (!getConfig().isConfigurationSection(base)) return Collections.emptyList();
        Map<String, Integer> totals = new HashMap<>();
        ConfigurationSection sec = getConfig().getConfigurationSection(base);
        for (String player : sec.getKeys(false)) {
            totals.put(player, getTotalScore(season, player));
        }
        return totals.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * retourne le joueur à la position rank (1-based) ou null si introuvable
     */
    public String getPlayerAtRank(int season, int rank) {
        List<Map.Entry<String, Integer>> ranking = getRanking(season);
        if (rank < 1 || rank > ranking.size()) return null;
        return ranking.get(rank - 1).getKey();
    }

    public void save() { plugin.saveConfig(); }
}
