package fr.lordfinn.craftMaster.data;

import fr.lordfinn.craftMaster.CraftMaster;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final CraftMaster plugin;
    private final File configFile;
    private FileConfiguration cfg;

    public TaskManager(CraftMaster plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.cfg = YamlConfiguration.loadConfiguration(configFile);
    }

    public void addTask(int season, String taskName) {
        String path = "seasons." + season + ".tasks";
        List<String> tasks = new ArrayList<>(cfg.getStringList(path));
        if (!tasks.contains(taskName)) {
            tasks.add(taskName);
            cfg.set(path, tasks);
            save();
        }
    }

    public void removeTask(int season, String taskName) {
        String path = "seasons." + season + ".tasks";
        List<String> tasks = new ArrayList<>(cfg.getStringList(path));
        if (tasks.remove(taskName)) {
            cfg.set(path, tasks);
            save();
        }
    }

    public void renameTask(int season, String oldName, String newName) {
        String path = "seasons." + season + ".tasks";
        List<String> tasks = new ArrayList<>(cfg.getStringList(path));
        if (tasks.contains(oldName)) {
            tasks.remove(oldName);
            if (!tasks.contains(newName)) tasks.add(newName);
            cfg.set(path, tasks);
            save();
        }
    }

    public List<String> getSeasonsStrings() {
        if (cfg.getConfigurationSection("seasons") == null)
            return new ArrayList<>();
        return new ArrayList<>(cfg.getConfigurationSection("seasons").getKeys(false));
    }

    public List<String> getTasksStrings(int season) {
        return getTasks(season);
    }

    public List<String> getTasks(int season) {
        return cfg.getStringList("seasons." + season + ".tasks");
    }

    public void save() {
        try {
            cfg.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde du config.yml : " + e.getMessage());
        }
    }

    public void reload() {
        this.cfg = YamlConfiguration.loadConfiguration(configFile);
    }
}
