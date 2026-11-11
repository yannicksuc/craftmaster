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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * All CraftMaster data, in {@code config/craftmaster/config.yml} (format version 2, see docs/wiki/Donnees.md):
 * seasons → games ({@link Partie}) → rounds ({@link Manche}), each season's cast and published scores, busts,
 * the stage set-up and the live state of the show. A version 1 file (Paper plugin, mod 2.0) is migrated on load
 * after a copy to {@code config.yml.v1}.
 *
 * <p>Every modification goes through {@link #change}: it records the state before the change in the undo journal
 * (the 50 last changes) and saves the file. The model objects returned by the getters are live: modify them only
 * inside {@link #change}.
 */
public final class CraftMasterData {

    public static final int VERSION = 2;
    /** Changes kept in the undo journal. */
    public static final int JOURNAL_SIZE = 50;

    public record BustPos(String world, int x, int y, int z) {}

    public record Entry(String player, int total) {}

    /** One undoable change: when, what (French, shown to the organisers), and the whole data before it. */
    public record Change(long at, String description, Map<String, Object> before) {}

    private final Path file;
    private final Logger log;

    private final TreeMap<Integer, Season> seasons = new TreeMap<>();
    private final TreeMap<Integer, TreeMap<Integer, BustPos>> busts = new TreeMap<>();
    private Stage stage = new Stage();
    private Live live = new Live();

    private final Deque<Change> journal = new ArrayDeque<>();
    /** Undone changes that can be redone, most recent undo first; {@code before} is the state to put back. */
    private final Deque<Change> redoJournal = new ArrayDeque<>();
    /** Nesting depth of {@link #change} calls and the state before the outermost one. */
    private int depth;
    private Map<String, Object> pendingBefore;

    public CraftMasterData(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    // ---------------------------------------------------------------- file

    /**
     * Loads the file; on first start, imports the plugin's plugins/CraftMaster/config.yml if it is there. A version 1
     * file is migrated (the original is kept as config.yml.v1). An unreadable file is kept as config.yml.broken and
     * the mod starts empty.
     */
    public void load(Path legacyPluginFile) {
        Map<String, Object> root;
        try {
            if (!Files.exists(file) && legacyPluginFile != null && Files.exists(legacyPluginFile)) {
                Files.createDirectories(file.getParent());
                Files.copy(legacyPluginFile, file);
                log.info("[CraftMaster] Données du plugin importées depuis {}", legacyPluginFile);
            }
            root = new LinkedHashMap<>();
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
                    if (loaded instanceof Map<?, ?> m) root = Maps.normalize(m);
                }
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
        boolean migrate = Migration.isV1(root);
        if (migrate) {
            Path backup = file.resolveSibling(file.getFileName() + ".v1");
            try {
                if (!Files.exists(backup)) Files.copy(file, backup);
            } catch (IOException e) {
                log.error("[CraftMaster] Impossible de copier {} vers {} : {}", file, backup, e.toString());
            }
            root = Migration.v1ToV2(root, System.currentTimeMillis());
            log.info("[CraftMaster] Fichier converti au format {} (ancien fichier gardé dans {})", VERSION, backup);
        }
        readYaml(root);
        journal.clear();
        redoJournal.clear();
        if (migrate) save();
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
                w.write("# CraftMaster - saisons, parties, manches, scores, busts et plateau\n");
                yaml.dump(toYaml(), w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[CraftMaster] Erreur lors de la sauvegarde de {} : {}", file, e.toString());
        }
    }

    public Path file() { return file; }

    /** The whole data as the YAML tree written to the file. */
    public Map<String, Object> toYaml() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("live", live.toYaml());
        Map<String, Object> s = new LinkedHashMap<>();
        seasons.forEach((id, season) -> s.put(String.valueOf(id), season.toYaml()));
        root.put("seasons", s);
        Map<String, Object> b = new LinkedHashMap<>();
        busts.forEach((season, ranks) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            ranks.forEach((rank, pos) -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("world", pos.world());
                p.put("x", pos.x());
                p.put("y", pos.y());
                p.put("z", pos.z());
                r.put(String.valueOf(rank), p);
            });
            b.put(String.valueOf(season), r);
        });
        root.put("busts", b);
        root.put("stage", stage.toYaml());
        return root;
    }

    private void readYaml(Map<String, Object> root) {
        seasons.clear();
        Maps.map(root, "seasons").forEach((k, v) -> {
            Integer id = Maps.intKey(k);
            if (id != null) seasons.put(id, Season.fromYaml(id, Maps.map(v)));
            else log.warn("[CraftMaster] Saison invalide ignorée : {}", k);
        });
        busts.clear();
        Maps.map(root, "busts").forEach((k, v) -> {
            Integer season = Maps.intKey(k);
            if (season == null) return;
            Maps.map(v).forEach((rk, pv) -> {
                Integer rank = Maps.intKey(rk);
                Map<String, Object> p = Maps.map(pv);
                if (rank == null || p.get("world") == null) {
                    log.warn("[CraftMaster] Bust invalide ignoré : saison {} rang {}", k, rk);
                    return;
                }
                busts.computeIfAbsent(season, x -> new TreeMap<>()).put(rank, new BustPos(String.valueOf(p.get("world")),
                        Maps.toInt(p.get("x")), Maps.toInt(p.get("y")), Maps.toInt(p.get("z"))));
            });
        });
        stage = Stage.fromYaml(Maps.map(root, "stage"));
        live = Live.fromYaml(Maps.map(root, "live"));
    }

    // ---------------------------------------------------------------- changes and undo

    /**
     * The single entry point of every modification: runs it, records the state before it in the undo journal and
     * saves. Nested calls make one change. If the mutation throws, the data is put back as it was. A change that
     * modifies nothing is not recorded.
     */
    public <T> T change(String description, Supplier<T> mutation) {
        begin();
        try {
            T result = mutation.get();
            commit(description);
            return result;
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    public void change(String description, Runnable mutation) {
        change(description, () -> {
            mutation.run();
            return null;
        });
    }

    /** Starts a change made of several steps (see {@link #change}); each begin needs a commit or a rollback. */
    public void begin() {
        if (depth++ == 0) pendingBefore = toYaml();
    }

    /** Ends the change; the outermost commit journals and saves it if something changed. */
    public void commit(String description) {
        if (depth == 0) throw new IllegalStateException("commit without begin");
        if (--depth > 0) return;
        Map<String, Object> before = pendingBefore;
        pendingBefore = null;
        if (before.equals(toYaml())) return;
        journal.push(new Change(System.currentTimeMillis(), description, before));
        while (journal.size() > JOURNAL_SIZE) journal.removeLast();
        // A new change makes the undone ones impossible to redo
        redoJournal.clear();
        save();
    }

    /** Cancels the whole change in progress (all nesting levels): the data is put back as it was before it began. */
    public void rollback() {
        if (depth == 0) return;
        depth = 0;
        readYaml(pendingBefore);
        pendingBefore = null;
    }

    /**
     * Undoes the last {@code count} changes at once.
     *
     * @return the descriptions of the undone changes, most recent first (empty if there was nothing to undo)
     */
    public List<String> undo(int count) {
        if (depth > 0) throw new IllegalStateException("undo during a change");
        List<String> undone = new ArrayList<>();
        Map<String, Object> state = null;
        for (int i = 0; i < count && !journal.isEmpty(); i++) {
            Change c = journal.pop();
            undone.add(c.description());
            // To redo this change: the state just after it (the current one, or the one before the next undone)
            redoJournal.push(new Change(c.at(), c.description(), state == null ? toYaml() : state));
            state = c.before();
        }
        if (state != null) {
            readYaml(state);
            save();
        }
        return undone;
    }

    /**
     * Redoes the last {@code count} undone changes (in the order they were made). Any new change after an undo
     * empties what can be redone.
     *
     * @return the descriptions of the redone changes, oldest first (empty if there was nothing to redo)
     */
    public List<String> redo(int count) {
        if (depth > 0) throw new IllegalStateException("redo during a change");
        List<String> redone = new ArrayList<>();
        Map<String, Object> state = null;
        for (int i = 0; i < count && !redoJournal.isEmpty(); i++) {
            Change r = redoJournal.pop();
            redone.add(r.description());
            journal.push(new Change(r.at(), r.description(), state == null ? toYaml() : state));
            state = r.before();
        }
        while (journal.size() > JOURNAL_SIZE) journal.removeLast();
        if (state != null) {
            readYaml(state);
            save();
        }
        return redone;
    }

    /** Number of undone changes that can be redone. */
    public int redoSize() {
        return redoJournal.size();
    }

    /** The undo journal, most recent change first. */
    public List<Change> history() {
        return List.copyOf(journal);
    }

    // ---------------------------------------------------------------- model

    public Map<Integer, Season> seasonMap() {
        return java.util.Collections.unmodifiableMap(seasons);
    }

    public Season season(int id) {
        return seasons.get(id);
    }

    /** The season, created ("Saison N") if it does not exist yet. Call inside {@link #change}. */
    public Season seasonOrCreate(int id) {
        return seasons.computeIfAbsent(id, k -> new Season(k, "Saison " + k));
    }

    /** Removes a season with its games and scores (busts stay registered). Call inside {@link #change}. */
    public boolean deleteSeason(int id) {
        if (seasons.remove(id) == null) return false;
        if (live.season != null && live.season == id) live.season = null;
        if (live.current != null && live.current.season() == id) live.current = null;
        return true;
    }

    /** Next free season number. */
    public int nextSeasonId() {
        return seasons.isEmpty() ? 1 : seasons.lastKey() + 1;
    }

    /** The active season if it exists, else the latest one not archived, else the latest one, else null. */
    public Season activeSeason() {
        Season s = live.season == null ? null : seasons.get(live.season);
        if (s != null) return s;
        for (Season q : seasons.descendingMap().values()) if (!q.archived) return q;
        return seasons.isEmpty() ? null : seasons.lastEntry().getValue();
    }

    public Stage stage() {
        return stage;
    }

    public Live live() {
        return live;
    }

    /** The round being played, or null (also when it was deleted since). */
    public Manche currentManche() {
        Live.Current c = live.current;
        if (c == null) return null;
        Season s = seasons.get(c.season());
        Partie p = s == null ? null : s.parties.get(c.partie());
        return p == null ? null : p.manche(c.manche());
    }

    // ---------------------------------------------------------------- seasons (plugin-era API)

    /** Season numbers that have data, as text, numerically sorted. */
    public List<String> seasons() {
        return seasons.keySet().stream().map(String::valueOf).toList();
    }

    /** Every season that has data or busts, numerically sorted. */
    public List<Integer> allSeasons() {
        TreeSet<Integer> set = new TreeSet<>(seasons.keySet());
        set.addAll(busts.keySet());
        return new ArrayList<>(set);
    }

    /** A hidden season's ranking is only shown to admins, and its busts stay anonymous until it is revealed. */
    public boolean isHidden(int season) {
        Season s = seasons.get(season);
        return s != null && s.hidden;
    }

    public void setHidden(int season, boolean hidden) {
        change((hidden ? "Saison cachée : " : "Saison révélée : ") + season, () -> { seasonOrCreate(season).hidden = hidden; });
    }

    // ---------------------------------------------------------------- rounds of the active game (plugin-era "tasks")

    /** Names of the rounds still listed in the season's active game. */
    public List<String> tasks(int season) {
        Season s = seasons.get(season);
        Partie p = s == null ? null : s.activePartie();
        return p == null ? new ArrayList<>() : new ArrayList<>(p.activeManches().stream().map(r -> r.name).toList());
    }

    /** A round of the active game by exact name (archived ones included), or null. */
    public Manche manche(int season, String name) {
        Season s = seasons.get(season);
        Partie p = s == null ? null : s.activePartie();
        if (p == null) return null;
        for (Manche r : p.manches) if (r.name.equals(name)) return r;
        return null;
    }

    /** Adds a round to the active game (or lists again an archived one). @return false if it was already listed */
    public boolean addTask(int season, String task) {
        return change("Manche ajoutée : " + task, () -> {
            Partie p = seasonOrCreate(season).activePartieOrCreate();
            Manche r = manche(season, task);
            if (r != null) {
                if (!r.archived) return false;
                r.archived = false;
                return true;
            }
            p.manches.add(new Manche(seasons.get(season).newMancheId(task), task));
            return true;
        });
    }

    /** Archives the round: no longer listed, its points still count (as in the plugin). @return false if not listed */
    public boolean removeTask(int season, String task) {
        return change("Manche retirée : " + task, () -> {
            Manche r = manche(season, task);
            if (r == null || r.archived) return false;
            r.archived = true;
            return true;
        });
    }

    /**
     * Renames the round (its scores follow, they are kept by round id). Renaming onto another round of the game
     * merges the two. @return false if the old round did not exist
     */
    public boolean renameTask(int season, String oldName, String newName) {
        return change("Manche renommée : " + oldName + " → " + newName, () -> {
            Manche r = manche(season, oldName);
            if (r == null) return false;
            Manche target = manche(season, newName);
            if (target != null && target != r) mergeManche(seasons.get(season).activePartie(), r, target);
            else r.name = newName;
            return true;
        });
    }

    /** Moves the scores of {@code from} into {@code into} (added up) and deletes {@code from}. */
    public static void mergeManche(Partie p, Manche from, Manche into) {
        Map<String, Integer> moved = p.scores.remove(from.id);
        if (moved != null) moved.forEach((player, v) -> p.setScore(into.id, player, p.score(into.id, player) + v));
        p.manches.remove(from);
        into.archived = false;
    }

    // ---------------------------------------------------------------- scores (plugin-era API, active game)

    /** Sets the score; a round that does not exist is created archived, a new player joins the cast. */
    public void setScore(int season, String task, String player, int score) {
        change("Score : " + player + " = " + score + " (" + task + ")", () -> {
            Season s = seasonOrCreate(season);
            Partie p = s.activePartieOrCreate();
            Manche r = manche(season, task);
            if (r == null) {
                r = new Manche(s.newMancheId(task), task);
                r.archived = true;
                p.manches.add(r);
            }
            s.players.computeIfAbsent(player, SeasonPlayer::new);
            p.setScore(r.id, player, score);
            live.lastScoreAt = System.currentTimeMillis();
            live.lastScoreManche = r.id;
        });
    }

    /** Adds (or with a negative value, takes away) points to the player's score for the round. @return the new score */
    public int addScore(int season, String task, String player, int points) {
        int score = score(season, task, player) + points;
        setScore(season, task, player, score);
        return score;
    }

    /** Forgets the player's score for the round. @return false if there was no such score */
    public boolean removeScore(int season, String task, String player) {
        return change("Score retiré : " + player + " (" + task + ")", () -> {
            Manche r = manche(season, task);
            return r != null && seasons.get(season).activePartie().removeScore(r.id, player);
        });
    }

    public int score(int season, String task, String player) {
        Manche r = manche(season, task);
        return r == null ? 0 : seasons.get(season).activePartie().score(r.id, player);
    }

    /** Live scores of one player in the season, by round label (all games, archived rounds included). */
    public Map<String, Integer> scores(int season, String player) {
        Season s = seasons.get(season);
        return s == null ? new LinkedHashMap<>() : s.labelledScores().getOrDefault(player, new LinkedHashMap<>());
    }

    /** Players with a live score in the season. */
    public List<String> players(int season) {
        Season s = seasons.get(season);
        return s == null ? new ArrayList<>() : s.scoredPlayers();
    }

    /** Sum of all the player's live scores in the season, every game included. */
    public int total(int season, String player) {
        Season s = seasons.get(season);
        return s == null ? 0 : s.total(player);
    }

    /** Live ranking: players by total, highest first; ties keep the cast order. */
    public List<Entry> ranking(int season) {
        return ranking(season, false);
    }

    public List<Entry> ranking(int season, boolean published) {
        Season s = seasons.get(season);
        return s == null ? new ArrayList<>() : s.ranking(published);
    }

    /** Player at the 1-based rank, or null. */
    public String playerAtRank(int season, int rank, boolean published) {
        List<Entry> r = ranking(season, published);
        return rank < 1 || rank > r.size() ? null : r.get(rank - 1).player();
    }

    public String playerAtRank(int season, int rank) {
        return playerAtRank(season, rank, false);
    }

    /** Published total of a player (0 before the first publish). */
    public int publishedTotal(int season, String player) {
        Season s = seasons.get(season);
        return s == null || s.published == null ? 0 : s.published.total(player);
    }

    /** Copies the season's live scores to the published ones. */
    public void publish(int season) {
        change("Scores publiés (saison " + season + ")", () -> seasonOrCreate(season).publish(System.currentTimeMillis()));
    }

    // ---------------------------------------------------------------- busts

    public void setBust(int season, int rank, BustPos pos) {
        change("Bust : saison " + season + " rang " + rank, () -> {
            busts.computeIfAbsent(season, k -> new TreeMap<>()).put(rank, pos);
        });
    }

    public boolean removeBust(int season, int rank) {
        return change("Bust retiré : saison " + season + " rang " + rank, () -> {
            TreeMap<Integer, BustPos> s = busts.get(season);
            if (s == null || s.remove(rank) == null) return false;
            if (s.isEmpty()) busts.remove(season);
            return true;
        });
    }

    public BustPos bust(int season, int rank) {
        TreeMap<Integer, BustPos> s = busts.get(season);
        return s == null ? null : s.get(rank);
    }

    /** Registered ranks of a season, sorted. */
    public List<Integer> bustRanks(int season) {
        TreeMap<Integer, BustPos> s = busts.get(season);
        return s == null ? new ArrayList<>() : new ArrayList<>(s.keySet());
    }

    public List<Integer> bustSeasons() {
        return new ArrayList<>(busts.keySet());
    }
}
