package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveTaskTab implements TabCompleter {

    private final CraftMaster plugin;
    public RemoveTaskTab(CraftMaster plugin) { this.plugin = plugin; }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(plugin.getTaskManager().getSeasonsStrings());
        } else if (args.length == 2) {
            try {
                int season = Integer.parseInt(args[0]);
                completions.addAll(plugin.getTaskManager().getTasksStrings(season));
            } catch (NumberFormatException ignored) {}
        }
        return completions;
    }
}
