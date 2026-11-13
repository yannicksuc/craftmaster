package fr.lordfinn.craftmaster.show;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.lordfinn.craftmaster.CraftMaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The show's moderators, kept by the server ({@code config/craftmaster/moderateurs.json}), not by the site: the site
 * only shows the list, and every order coming from it is checked again here. A moderator is a Twitch login (the site's
 * sign-in) and/or a Minecraft name (the director in game), with rights ({@link Rights.Right}). {@code paused} stops
 * every moderator at once ({@code /cm modo pause}); the organiser keeps everything.
 */
public final class Moderators {

    private Moderators() {}

    public static final class Entry {
        /** Twitch login, lower case ("" = none). */
        public String twitch = "";
        /** Minecraft name ("" = none). */
        public String minecraft = "";
        public List<String> rights = new ArrayList<>();

        public Set<Rights.Right> rightSet() {
            Set<Rights.Right> out = EnumSet.noneOf(Rights.Right.class);
            for (String r : rights) {
                Rights.Right x = Rights.Right.byId(r);
                // The organiser's right is never given through the list (op / craftmaster:admin only)
                if (x != null && x != Rights.Right.ADMIN) out.add(x);
            }
            return out;
        }

        public String label() {
            if (!minecraft.isEmpty() && !twitch.isEmpty() && !minecraft.equalsIgnoreCase(twitch)) return minecraft + " (" + twitch + ")";
            return minecraft.isEmpty() ? twitch : minecraft;
        }
    }

    static final class File {
        boolean paused = false;
        List<Entry> moderators = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path path;
    private static File file = new File();

    public static synchronized void load(Path p) {
        path = p;
        file = new File();
        if (p == null || !Files.isRegularFile(p)) return;
        try {
            File f = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), File.class);
            if (f != null) file = f;
            if (file.moderators == null) file.moderators = new ArrayList<>();
            file.moderators.removeIf(e -> e == null || (blank(e.twitch) && blank(e.minecraft)));
            for (Entry e : file.moderators) normalize(e);
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.warn("[Modos] {} illisible : {}", p, e.toString());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void normalize(Entry e) {
        e.twitch = e.twitch == null ? "" : e.twitch.trim().toLowerCase(Locale.ROOT);
        e.minecraft = e.minecraft == null ? "" : e.minecraft.trim();
        if (e.rights == null) e.rights = new ArrayList<>();
    }

    private static void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftMaster.LOG.warn("[Modos] écriture de {} impossible : {}", path, e.toString());
        }
    }

    public static synchronized boolean paused() {
        return file.paused;
    }

    public static synchronized void setPaused(boolean paused) {
        file.paused = paused;
        save();
    }

    public static synchronized List<Entry> list() {
        return List.copyOf(file.moderators);
    }

    /** Adds or replaces the moderator with this Twitch login or Minecraft name; returns the entry. */
    public static synchronized Entry put(String twitch, String minecraft, Set<Rights.Right> rights) {
        Entry e = find(twitch, minecraft);
        if (e == null) {
            e = new Entry();
            file.moderators.add(e);
        }
        if (!blank(twitch)) e.twitch = twitch;
        if (!blank(minecraft)) e.minecraft = minecraft;
        e.rights = new ArrayList<>();
        for (Rights.Right r : rights) if (r != Rights.Right.ADMIN) e.rights.add(r.id);
        normalize(e);
        save();
        return e;
    }

    /** Removes the moderator named by Twitch login or Minecraft name; false if none. */
    public static synchronized boolean remove(String name) {
        Entry e = find(name, name);
        if (e == null) return false;
        file.moderators.remove(e);
        save();
        return true;
    }

    public static synchronized Entry byTwitch(String login) {
        if (blank(login)) return null;
        for (Entry e : file.moderators) if (e.twitch.equalsIgnoreCase(login.trim())) return e;
        return null;
    }

    public static synchronized Entry byMinecraft(String name) {
        if (blank(name)) return null;
        for (Entry e : file.moderators) if (e.minecraft.equalsIgnoreCase(name.trim())) return e;
        return null;
    }

    private static Entry find(String twitch, String minecraft) {
        Entry e = byTwitch(twitch);
        return e != null ? e : byMinecraft(minecraft);
    }

    /** For tests. */
    static synchronized void reset() {
        path = null;
        file = new File();
    }
}
