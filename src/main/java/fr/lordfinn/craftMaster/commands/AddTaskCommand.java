package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AddTaskCommand implements CommandExecutor {

    private final CraftMaster plugin;
    public AddTaskCommand(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /craftMasterAddTask <SEASON> <TASK_NAME>");
            return true;
        }
        try {
            int season = Integer.parseInt(args[0]);
            String task = args[1];
            plugin.getTaskManager().addTask(season, task);
            sender.sendMessage("§aTâche '" + task + "' ajoutée à la saison " + season + ".");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe numéro de saison doit être un entier.");
        }
        return true;
    }
}
