package fr.lordfinn.craftMaster.data;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;
import java.util.logging.Level;

public class BustManager {

    private final CraftMaster plugin;
    private final FileConfiguration cfg;

    public BustManager(CraftMaster plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void registerBust(int season, int rank, Location loc) {
        String base = "busts." + season + "." + rank;
        cfg.set(base + ".world", loc.getWorld().getName());
        cfg.set(base + ".x", loc.getBlockX());
        cfg.set(base + ".y", loc.getBlockY());
        cfg.set(base + ".z", loc.getBlockZ());
        save();
        plugin.getLogger().info("[BustManager] Bust enregistré pour saison " + season + " rang " + rank + " en " +
                loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (" + loc.getWorld().getName() + ")");
    }

    public Location getBustLocation(int season, int rank) {
        String base = "busts." + season + "." + rank;
        if (!cfg.contains(base + ".world")) return null;
        World w = Bukkit.getWorld(cfg.getString(base + ".world"));
        if (w == null) return null;
        double x = cfg.getInt(base + ".x");
        double y = cfg.getInt(base + ".y");
        double z = cfg.getInt(base + ".z");
        return new Location(w, x, y, z);
    }

    public void updateBustsForSeason(int season, ScoreManager scoreManager) {
        String prefix = "busts." + season;
        if (!cfg.isConfigurationSection(prefix)) return;

        for (String rankKey : cfg.getConfigurationSection(prefix).getKeys(false)) {
            int rank;
            try {
                rank = Integer.parseInt(rankKey);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[BustManager] rank invalide dans config: " + rankKey);
                continue;
            }

            Location loc = getBustLocation(season, rank);
            if (loc == null) {
                plugin.getLogger().warning("[BustManager] Aucun emplacement trouvé pour saison " + season + " rang " + rank);
                continue;
            }

            String playerName = scoreManager.getPlayerAtRank(season, rank);

            Block b = loc.getBlock();
            try {
                if (b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD) {
                    Skull skull = (Skull) b.getState();
                    if (playerName != null) {
                        setHeadOwnerAsync(skull, playerName);
                        plugin.getLogger().info("[BustManager] Skull mis à jour pour rang " + rank + " -> " + playerName);
                    } else {
                        skull.setOwningPlayer(null);
                        skull.update(true);
                        plugin.getLogger().info("[BustManager] Skull reset pour rang " + rank);
                    }
                } else {
                    plugin.getLogger().info("[BustManager] Pas un skull, recherche TextDisplay à proximité pour rang " + rank);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[BustManager] Erreur lors de la mise à jour d'un bust: " + e.getMessage(), e);
            }
        }
    }

    private void setHeadOwnerAsync(Skull skull, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();

                // Récupération du GameProfile avec skin depuis Mojang
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(uuid, playerName);
                profile.complete(true); // force la récupération des textures si nécessaire

                // Application de la texture sur le main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    skull.setPlayerProfile(profile);
                    skull.update(true);
                    plugin.getLogger().info("[BustManager] Tête mise à jour pour " + playerName + " à " +
                            skull.getLocation().getBlockX() + "," + skull.getLocation().getBlockY() + "," + skull.getLocation().getBlockZ());
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[BustManager] Impossible de charger la texture de " + playerName + " : " + e.getMessage());
            }
        });
    }
    public void updateAllBusts(ScoreManager scoreManager) {
        if (!cfg.isConfigurationSection("busts")) return;
        for (String seasonKey : cfg.getConfigurationSection("busts").getKeys(false)) {
            try {
                int season = Integer.parseInt(seasonKey);
                updateBustsForSeason(season, scoreManager);
            } catch (NumberFormatException ignored) {}
        }
    }

    public void save() { plugin.saveConfig(); }
}
