package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadScoresCommand implements CommandExecutor {
    private final CraftMaster plugin;
    public ReloadScoresCommand(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // On reload la config depuis disque (si besoin) puis on met à jour busts & displays
        plugin.reloadConfig();
        plugin.getTaskManager().save();
        plugin.getScoreManager().save();
        plugin.getBustManager().save();

        plugin.getBustManager().updateAllBusts(plugin.getScoreManager());
        sender.sendMessage("§aScores rechargés et têtes / displays mis à jour.");
        return true;
    }
}
