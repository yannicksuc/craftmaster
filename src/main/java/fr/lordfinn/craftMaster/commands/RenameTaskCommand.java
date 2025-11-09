package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RenameTaskCommand implements CommandExecutor {
    private final CraftMaster plugin;
    public RenameTaskCommand(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /craftMasterRenameTask <SEASON> <OLD_TASK_NAME> <NEW_TASK_NAME>");
            return true;
        }
        try {
            int season = Integer.parseInt(args[0]);
            String oldName = args[1];
            String newName = args[2];
            plugin.getTaskManager().renameTask(season, oldName, newName);
            sender.sendMessage("§aTâche renommée: " + oldName + " -> " + newName + " (saison " + season + ")");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe numéro de saison doit être un entier.");
        }
        return true;
    }
}
