package fr.lordfinn.craftMaster.commands;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AddBustTab implements TabCompleter {

    private final CraftMaster plugin;

    public AddBustTab(CraftMaster plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Premier argument : SEASON → on propose les saisons existantes dans config
            completions.addAll(plugin.getTaskManager().getSeasonsStrings());
        } else if (args.length == 2) {
            // Deuxième argument : RANK → proposer des nombres (1 à 10 par exemple)
            for (int i = 1; i <= 10; i++) {
                completions.add(String.valueOf(i));
            }
        }

        return completions;
    }
}
