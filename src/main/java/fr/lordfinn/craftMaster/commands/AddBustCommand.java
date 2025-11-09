package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddBustCommand implements CommandExecutor {
    private final CraftMaster plugin;

    public AddBustCommand(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length < 2) {
            p.sendMessage("§cUsage: /craftMasterAddBust <SEASON> <RANK>");
            return true;
        }

        try {
            int season = Integer.parseInt(args[0]);
            int rank = Integer.parseInt(args[1]);

            // Récupérer le bloc exact que le joueur regarde
            Block target = p.getTargetBlockExact(8);
            if (target == null) {
                p.sendMessage("§cAucun bloc ciblé dans la portée (8 blocs).");
                return true;
            }

            // Vérifie que c'est bien un skull
            if (target.getType() != Material.PLAYER_HEAD && target.getType() != Material.PLAYER_WALL_HEAD) {
                p.sendMessage("§cVeuillez regarder un Player Head pour enregistrer un bust.");
                return true;
            }

            Location loc = target.getLocation();
            plugin.getBustManager().registerBust(season, rank, loc);
            p.sendMessage("§aBust enregistré: saison " + season + " rang " + rank + " -> " +
                    "emplacement: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " (monde: " + loc.getWorld().getName() + ")");
        } catch (NumberFormatException e) {
            p.sendMessage("§cLes arguments SEASON et RANK doivent être des entiers.");
        }

        return true;
    }
}
