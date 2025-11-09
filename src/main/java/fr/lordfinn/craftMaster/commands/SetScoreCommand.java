package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SetScoreCommand implements CommandExecutor {
    private final CraftMaster plugin;
    public SetScoreCommand(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /craftMasterSetScore <SEASON> <TASK_NAME> <PSEUDO> <SCORE>");
            return true;
        }
        try {
            int season = Integer.parseInt(args[0]);
            String task = args[1];
            String pseudo = args[2];
            int score = Integer.parseInt(args[3]);
            plugin.getScoreManager().setScore(season, task, pseudo, score);
            sender.sendMessage("§aScore défini: " + pseudo + " -> " + score + " points (saison " + season + ", tache " + task + ")");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe numéro de saison et le score doivent être des entiers.");
        }
        return true;
    }
}
