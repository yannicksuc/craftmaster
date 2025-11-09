package fr.lordfinn.craftMaster;

import fr.lordfinn.craftMaster.commands.*;
import fr.lordfinn.craftMaster.data.BustManager;
import fr.lordfinn.craftMaster.data.ScoreManager;
import fr.lordfinn.craftMaster.data.TaskManager;
import fr.lordfinn.craftMaster.placeholders.CFPlaceholders;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftMaster extends JavaPlugin {

    private static CraftMaster instance;
    private TaskManager taskManager;
    private ScoreManager scoreManager;
    private BustManager bustManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.taskManager = new TaskManager(this);
        this.scoreManager = new ScoreManager(this);
        this.bustManager = new BustManager(this);

        getCommand("craftMasterAddTask").setExecutor(new AddTaskCommand(this));
        getCommand("craftMasterAddTask").setTabCompleter(new AddTaskTab(this));

        getCommand("craftMasterRemoveTask").setExecutor(new RemoveTaskCommand(this));
        getCommand("craftMasterRemoveTask").setTabCompleter(new RemoveTaskTab(this));

        getCommand("craftMasterRenameTask").setExecutor(new RenameTaskCommand(this));
        getCommand("craftMasterRenameTask").setTabCompleter(new RenameTaskTab(this));

        getCommand("craftMasterSetScore").setExecutor(new SetScoreCommand(this));
        getCommand("craftMasterSetScore").setTabCompleter(new SetScoreTab(this));

        getCommand("craftMasterReloadScores").setExecutor(new ReloadScoresCommand(this));
        getCommand("craftMasterReloadScores").setTabCompleter(new ReloadScoresTab());

        getCommand("craftMasterAddBust").setExecutor(new AddBustCommand(this));
        getCommand("craftMasterAddBust").setTabCompleter(new AddBustTab(this));

        getCommand("image").setExecutor(new ImageCommand(this));

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CFPlaceholders(this).register();
            getLogger().info("PlaceholderAPI detected — placeholders enregistrés.");
        } else {
            getLogger().warning("PlaceholderAPI non trouvé — placeholders non enregistrés.");
        }

        getLogger().info("CraftMaster activé.");
    }

    @Override
    public void onDisable() {
        taskManager.save();
        scoreManager.save();
        bustManager.save();
        getLogger().info("CraftMaster désactivé — données sauvegardées.");
    }

    public static CraftMaster getInstance() {
        return instance;
    }

    public TaskManager getTaskManager() { return taskManager; }
    public ScoreManager getScoreManager() { return scoreManager; }
    public BustManager getBustManager() { return bustManager; }
}
