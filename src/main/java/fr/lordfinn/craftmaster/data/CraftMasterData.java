package fr.lordfinn.craftmaster.data;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All CraftMaster data, in one YAML file with the exact layout of the old Paper plugin's config.yml:
 * <pre>
 * seasons: { "1": { tasks: [taskA, taskB], hidden: true } }
 * scores:  { "1": { LordFinn: { taskA: 3, taskB: 5 } } }
 * busts:   { "1": { "1": { world: minecraft:overworld, x: 10, y: 64, z: -3 } } }
 * </pre>
 * The plugin kept two copies of this file in memory that overwrote each other (tasks added in game were lost on
 * restart); here there is a single copy, saved after every change.
 */
public final class CraftMasterData {

    public record BustPos(String world, int x, int y, int z) {}

    public record Entry(String player, int total) {}

    private final Path file;
    private final Logger log;
    private Map<String, Object> root = new LinkedHashMap<>();

    public CraftMasterData(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    // ---------------------------------------------------------------- file

    /** Loads the file; on first start, imports the plugin's plugins/CraftMaster/config.yml if it is there. */
    public void load(Path legacyPluginFile) {
        try {
            if (!Files.exists(file) && legacyPluginFile != null && Files.exists(legacyPluginFile)) {
                Files.createDirectories(file.getParent());
                Files.copy(legacyPluginFile, file);
                log.info("[CraftMaster] Données du plugin importées depuis {}", legacyPluginFile);
            }
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
                    root = loaded instanceof Map<?, ?> m ? normalize(m) : new LinkedHashMap<>();
                }
            } else {
                root = new LinkedHashMap<>();
            }
        } catch (IOException | RuntimeException e) {
            // Keep the broken file aside: the next save would otherwise overwrite it with an empty state
            Path backup = file.resolveSibling(file.getFileName() + ".broken");
            try {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing more we can do, the error below says what happened
            }
            log.error("[CraftMaster] Impossible de lire {} : {} (copie gardée dans {})", file, e.toString(), backup);
            root = new LinkedHashMap<>();
        }
        for (String k : List.of("seasons", "scores", "busts")) root.computeIfAbsent(k, x -> new LinkedHashMap<>());
    }

    public void save() {
        DumperOptions opt = new DumperOptions();
        opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opt.setIndent(2);
        opt.setAllowUnicode(true);
        Yaml yaml = new Yaml(new Representer(opt), opt);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write("# CraftMaster - saisons, scores et busts\n");
                yaml.dump(root, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[CraftMaster] Erreur lors de la sauvegarde de {} : {}", file, e.toString());
        }
    }

    public Path file() { return file; }

    // ---------------------------------------------------------------- tasks

    public List<String> seasons() {
        List<String> out = new ArrayList<>(section(root, "seasons", false).keySet());
        out.sort(Comparator.comparingInt(CraftMasterData::sortKey));
        return out;
    }

    /** Every season that has tasks, scores or busts, numerically sorted. */
    public List<Integer> allSeasons() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        for (String k : List.of("seasons", "scores", "busts"))
            for (String s : section(root, k, false).keySet()) {
                try { set.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        return new ArrayList<>(set);
    }

    public List<String> tasks(int season) {
        Object v = section(section(root, "seasons", false), String.valueOf(season), false).get("tasks");
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
        return out;
    }

    private void setTasks(int season, List<String> tasks) {
        section(section(root, "seasons", true), String.valueOf(season), true).put("tasks", new ArrayList<>(tasks));
    }

    /** @return false if the task already existed */
    public boolean addTask(int season, String task) {
        List<String> tasks = tasks(season);
        if (tasks.contains(task)) return false;
        tasks.add(task);
        setTasks(season, tasks);
        save();
        return true;
    }

    /** Scores already given for that task are kept (as in the plugin). @return false if the task did not exist */
    public boolean removeTask(int season, String task) {
        List<String> tasks = tasks(season);
        if (!tasks.remove(task)) return false;
        setTasks(season, tasks);
        save();
        return true;
    }

    /** Renames the task and moves the players' scores to the new name. @return false if the old task did not exist */
    public boolean renameTask(int season, String oldName, String newName) {
        List<String> tasks = tasks(season);
        int i = tasks.indexOf(oldName);
        if (i < 0) return false;
        if (tasks.contains(newName)) tasks.remove(i);
        else tasks.set(i, newName);
        setTasks(season, tasks);
        for (Object perPlayer : section(section(root, "scores", false), String.valueOf(season), false).values()) {
            if (perPlayer instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Map<String, Object> scores = (Map<String, Object>) m;
                if (scores.containsKey(oldName)) {
                    Object v = scores.remove(oldName);
                    scores.merge(newName, v, (a, b) -> toInt(a) + toInt(b));
                }
            }
        }
        save();
        return true;
    }

    /** A hidden season's ranking is only shown to admins, and its busts stay anonymous until it is revealed. */
    public boolean isHidden(int season) {
        return Boolean.TRUE.equals(section(section(root, "seasons", false), String.valueOf(season), false).get("hidden"));
    }

    public void setHidden(int season, boolean hidden) {
        Map<String, Object> s = section(section(root, "seasons", true), String.valueOf(season), true);
        if (hidden) s.put("hidden", true);
        else s.remove("hidden");
        save();
    }

    // ---------------------------------------------------------------- scores

    public void setScore(int season, String task, String player, int score) {
        section(section(section(root, "scores", true), String.valueOf(season), true), player, true).put(task, score);
        save();
    }

    /** Adds (or with a negative value, takes away) points to the player's score for the task. @return the new score */
    public int addScore(int season, String task, String player, int points) {
        int score = score(season, task, player) + points;
        setScore(season, task, player, score);
        return score;
    }

    /**
     * Forgets the player's score for the task; a player left without any score leaves the season's ranking.
     * @return false if there was no such score
     */
    public boolean removeScore(int season, String task, String player) {
        Map<String, Object> perSeason = section(section(root, "scores", false), String.valueOf(season), false);
        Map<String, Object> perPlayer = section(perSeason, player, false);
        if (!perPlayer.containsKey(task)) return false;
        perPlayer.remove(task);
        if (perPlayer.isEmpty()) perSeason.remove(player);
        save();
        return true;
    }

    public int score(int season, String task, String player) {
        return toInt(section(section(section(root, "scores", false), String.valueOf(season), false), player, false).get(task));
    }

    /** Scores of one player in a season, task by task (tasks no longer listed included). */
    public Map<String, Integer> scores(int season, String player) {
        Map<String, Integer> out = new LinkedHashMap<>();
        section(section(section(root, "scores", false), String.valueOf(season), false), player, false)
                .forEach((k, v) -> out.put(k, toInt(v)));
        return out;
    }

    public List<String> players(int season) {
        return new ArrayList<>(section(section(root, "scores", false), String.valueOf(season), false).keySet());
    }

    /** Sum of all the player's scores in the season (same rule as the plugin). */
    public int total(int season, String player) {
        int t = 0;
        for (Object v : section(section(section(root, "scores", false), String.valueOf(season), false), player, false).values())
            t += toInt(v);
        return t;
    }

    /** Players by total, highest first; ties keep the order players were first scored. */
    public List<Entry> ranking(int season) {
        List<Entry> out = new ArrayList<>();
        for (String p : players(season)) out.add(new Entry(p, total(season, p)));
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return out;
    }

    /** Player at the 1-based rank, or null. */
    public String playerAtRank(int season, int rank) {
        List<Entry> r = ranking(season);
        return rank < 1 || rank > r.size() ? null : r.get(rank - 1).player();
    }

    // ---------------------------------------------------------------- busts

    public void setBust(int season, int rank, BustPos pos) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("world", pos.world());
        b.put("x", pos.x());
        b.put("y", pos.y());
        b.put("z", pos.z());
        section(section(root, "busts", true), String.valueOf(season), true).put(String.valueOf(rank), b);
        save();
    }

    public boolean removeBust(int season, int rank) {
        Map<String, Object> s = section(section(root, "busts", false), String.valueOf(season), false);
        if (s.remove(String.valueOf(rank)) == null) return false;
        if (s.isEmpty()) section(root, "busts", false).remove(String.valueOf(season));
        save();
        return true;
    }

    public BustPos bust(int season, int rank) {
        Map<String, Object> b = section(section(section(root, "busts", false), String.valueOf(season), false), String.valueOf(rank), false);
        if (!(b.get("world") instanceof Object w)) return null;
        return new BustPos(String.valueOf(w), toInt(b.get("x")), toInt(b.get("y")), toInt(b.get("z")));
    }

    /** Registered ranks of a season, sorted. */
    public List<Integer> bustRanks(int season) {
        List<Integer> out = new ArrayList<>();
        for (String k : section(section(root, "busts", false), String.valueOf(season), false).keySet()) {
            try { out.add(Integer.parseInt(k)); } catch (NumberFormatException e) {
                log.warn("[CraftMaster] Rang invalide dans les busts : {}", k);
            }
        }
        Collections.sort(out);
        return out;
    }

    public List<Integer> bustSeasons() {
        List<Integer> out = new ArrayList<>();
        for (String k : section(root, "busts", false).keySet()) {
            try { out.add(Integer.parseInt(k)); } catch (NumberFormatException ignored) {}
        }
        Collections.sort(out);
        return out;
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key, boolean create) {
        Object v = parent.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (!create) return Collections.emptyMap();
        Map<String, Object> m = new LinkedHashMap<>();
        parent.put(key, m);
        return m;
    }

    /** YAML may load "1:" as an Integer key; the plugin (and this mod) always use string keys. */
    private static Map<String, Object> normalize(Map<?, ?> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(String.valueOf(k), v instanceof Map<?, ?> m ? normalize(m) : v));
        return out;
    }

    static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static int sortKey(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }
}
