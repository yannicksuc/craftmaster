package fr.lordfinn.craftmaster.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.lordfinn.craftmaster.CraftMaster;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The clips kept by the server: {@code <id>.mp4} files in one folder and an index ({@code index.json}: name, who
 * recorded it, length, size, date, live or replay), newest last. Ids are made from the date ({@code clip-20261113-
 * 021530}, a counter when two arrive in the same second). Plain file work, unit tested.
 */
public final class ClipStore {

    public static final class Clip {
        public String id = "";
        public String name = "";
        public String author = "";
        public int durationMs;
        public long size;
        /** ISO local date-time. */
        public String createdAt = "";
        /** "direct", "replay" or anything the recording game said. */
        public String source = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Path dir;
    private final List<Clip> clips = new ArrayList<>();

    public ClipStore(Path dir) {
        this.dir = dir;
        load();
    }

    public Path dir() { return dir; }

    public synchronized List<Clip> list() {
        return List.copyOf(clips);
    }

    public synchronized Clip get(String id) {
        for (Clip c : clips) if (c.id.equals(id)) return c;
        return null;
    }

    /** The clip at this place in the list, 1 = the oldest; 0 or less counts from the newest (0 = newest). */
    public synchronized Clip byNumber(int n) {
        if (clips.isEmpty()) return null;
        int i = n > 0 ? n - 1 : clips.size() - 1 + n;
        return i >= 0 && i < clips.size() ? clips.get(i) : null;
    }

    public Path file(String id) {
        return dir.resolve(id + ".mp4");
    }

    /** A new id for a clip arriving now. */
    public synchronized String newId(LocalDateTime now) {
        String base = "clip-" + ID_TIME.format(now);
        String id = base;
        for (int i = 2; get(id) != null || Files.exists(file(id)); i++) id = base + "-" + i;
        return id;
    }

    /** Moves a finished upload into the store and indexes it. */
    public synchronized Clip add(Path uploaded, String name, String author, int durationMs, String source, LocalDateTime now) throws IOException {
        Files.createDirectories(dir);
        Clip c = new Clip();
        c.id = newId(now);
        c.name = name == null || name.isBlank() ? defaultName(now) : name.strip();
        c.author = author == null ? "" : author;
        c.durationMs = Math.max(0, durationMs);
        c.source = source == null ? "" : source;
        c.createdAt = now.withNano(0).toString();
        Files.move(uploaded, file(c.id), StandardCopyOption.REPLACE_EXISTING);
        c.size = Files.size(file(c.id));
        clips.add(c);
        save();
        return c;
    }

    public synchronized boolean rename(String id, String name) {
        Clip c = get(id);
        if (c == null || name == null || name.isBlank()) return false;
        c.name = name.strip();
        save();
        return true;
    }

    public synchronized boolean delete(String id) {
        Clip c = get(id);
        if (c == null) return false;
        clips.remove(c);
        try {
            Files.deleteIfExists(file(id));
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Clips] Suppression de {} impossible : {}", id, e.toString());
        }
        save();
        return true;
    }

    static String defaultName(LocalDateTime now) {
        return String.format(Locale.ROOT, "Clip %02d/%02d %02dh%02d", now.getDayOfMonth(), now.getMonthValue(), now.getHour(), now.getMinute());
    }

    private Path index() {
        return dir.resolve("index.json");
    }

    private void load() {
        clips.clear();
        Path f = index();
        if (!Files.isRegularFile(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            List<Clip> read = GSON.fromJson(r, new TypeToken<List<Clip>>() {}.getType());
            if (read != null) for (Clip c : read) if (c != null && ClipLinks.validId(c.id) && Files.isRegularFile(file(c.id))) clips.add(c);
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Clips] Index illisible ({}) : {}", f, e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("index.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(clips, w);
            }
            Files.move(tmp, index(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Clips] Index non enregistré : {}", e.toString());
        }
    }
}
