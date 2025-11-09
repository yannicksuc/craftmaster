package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;

public class ImageCommand implements CommandExecutor {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private final CraftMaster plugin;

    public ImageCommand(CraftMaster plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seul un joueur peut utiliser cette commande !");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage : /image <URL>");
            return true;
        }

        String url = args[0];
        String name = "image_" + counter.incrementAndGet();

        // La commande à exécuter comme le joueur
        String cmd = String.format("imageframe create %s %s 5 3 combined", name, url);

        // Exécuter la commande comme si le joueur l'avait tapée
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(cmd));

        player.sendMessage(ChatColor.GREEN + "✅ ImageFrame créé : " + name);
        return true;
    }
}
