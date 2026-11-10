package fr.lordfinn.craftmaster.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happens to the tasks and notes during the show, in {@code config/craftmaster/tasks.json}: which tasks were
 * revealed (when, by whom) and the hidden notes with their hiding places and finds.
 *
 * <p>Kept apart from {@code config.yml} on purpose: a reveal or a find is something that happened in the world
 * (like a teleport or a title), so "annule" undoes the organiser's last change, not "Ensuri opened her envelope".
 * The task texts themselves are in the data (Manche#task) and are undone like the rest.
 */
public final class TaskStore {

    /** A task was revealed: when, and the player who opened the envelope (or the organiser who forced it). */
    public record Reveal(long at, String by) {}

    private static final class State {
        Map<String, Reveal> revealed = new LinkedHashMap<>();
        String lastRevealed;
        Map<String, Note> notes = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Logger log;
    private State state = new State();

    public TaskStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Key of a round's reveal: rounds ids are unique within a season. */
    public static String key(int season, String mancheId) {
        return season + ":" + mancheId;
    }

    // ---------------------------------------------------------------- file

    public void load() {
        state = new State();
        if (file == null || !Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State s = GSON.fromJson(r, State.class);
            if (s != null) state = s;
        } catch (IOException | JsonParseException e) {
            Path backup = file.resolveSibling(file.getFileName() + ".broken");
            try {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // the message below says what happened
            }
            if (log != null) log.error("[CraftMaster] Impossible de lire {} : {} (copie gardée dans {})", file, e.toString(), backup);
            state = new State();
        }
        if (state.revealed == null) state.revealed = new LinkedHashMap<>();
        if (state.notes == null) state.notes = new LinkedHashMap<>();
        state.notes.values().removeIf(n -> n == null);
        state.notes.forEach((id, n) -> {
            n.id = id;
            n.normalize();
        });
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(state, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (log != null) log.error("[CraftMaster] Erreur lors de la sauvegarde de {} : {}", file, e.toString());
        }
    }

    // ---------------------------------------------------------------- reveals

    public boolean isRevealed(int season, String mancheId) {
        return state.revealed.containsKey(key(season, mancheId));
    }

    public Reveal reveal(int season, String mancheId) {
        return state.revealed.get(key(season, mancheId));
    }

    /** @return false if it was already revealed (the first reveal is kept) */
    public boolean setRevealed(int season, String mancheId, long at, String by) {
        String k = key(season, mancheId);
        state.lastRevealed = k;
        if (state.revealed.containsKey(k)) {
            save();
            return false;
        }
        state.revealed.put(k, new Reveal(at, by));
        save();
        return true;
    }

    /** @return false if it was not revealed */
    public boolean hide(int season, String mancheId) {
        String k = key(season, mancheId);
        boolean was = state.revealed.remove(k) != null;
        if (k.equals(state.lastRevealed)) state.lastRevealed = null;
        if (was) save();
        return was;
    }

    /** Forgets every reveal of a season (a new rehearsal). @return how many were forgotten */
    public int hideAll(int season) {
        String prefix = season + ":";
        int before = state.revealed.size();
        state.revealed.keySet().removeIf(k -> k.startsWith(prefix));
        if (state.lastRevealed != null && state.lastRevealed.startsWith(prefix)) state.lastRevealed = null;
        int n = before - state.revealed.size();
        if (n > 0) save();
        return n;
    }

    /** {season, mancheId} of the task revealed last, or null. */
    public String[] lastRevealed() {
        if (state.lastRevealed == null) return null;
        int i = state.lastRevealed.indexOf(':');
        return i < 0 ? null : new String[]{state.lastRevealed.substring(0, i), state.lastRevealed.substring(i + 1)};
    }

    // ---------------------------------------------------------------- notes

    public Map<String, Note> notes() {
        return java.util.Collections.unmodifiableMap(state.notes);
    }

    public Note note(String id) {
        if (id == null) return null;
        Note n = state.notes.get(id);
        if (n != null) return n;
        for (Note x : state.notes.values()) if (x.id.equalsIgnoreCase(id) || x.title.equalsIgnoreCase(id)) return x;
        return null;
    }

    /** A new note with a free id made from its title. */
    public Note createNote(String title, long now) {
        String base = Note.slug(title);
        String id = base;
        for (int i = 2; state.notes.containsKey(id); i++) id = base + "-" + i;
        Note n = new Note(id, title == null ? "" : title.strip());
        n.createdAt = now;
        state.notes.put(id, n);
        save();
        return n;
    }

    public boolean deleteNote(String id) {
        boolean removed = state.notes.remove(id) != null;
        if (removed) save();
        return removed;
    }

    /** The note's hiding places at a block, all notes together. */
    public List<Map.Entry<Note, Note.Spot>> spotsAt(String world, int x, int y, int z) {
        List<Map.Entry<Note, Note.Spot>> out = new ArrayList<>();
        for (Note n : state.notes.values())
            for (Note.Spot s : n.spots) if (s.isAt(world, x, y, z)) out.add(Map.entry(n, s));
        return out;
    }

    public List<Map.Entry<Note, Note.Spot>> spotsOn(String entityUuid) {
        List<Map.Entry<Note, Note.Spot>> out = new ArrayList<>();
        for (Note n : state.notes.values())
            for (Note.Spot s : n.spots) if (s.isEntity(entityUuid)) out.add(Map.entry(n, s));
        return out;
    }

    /**
     * Logs a find (once per player and piece). @return true if it is this player's first find of that piece
     */
    public boolean recordFind(Note n, String player, int piece, String method, long at) {
        if (n.foundBy(player, piece)) return false;
        n.finds.add(new Note.Find(player, at, piece, method));
        save();
        return true;
    }

    /** Forgets the finds and puts the taken notes back in their hiding places. */
    public void resetNote(Note n) {
        n.finds.clear();
        for (Note.Spot s : n.spots) s.takenBy = null;
        save();
    }

    /** Saves after the caller changed a note in place. */
    public void changed() {
        save();
    }
}
