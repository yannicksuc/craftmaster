package fr.lordfinn.craftmaster.portrait;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The poses portraits can take, kept by the server in config/craftmaster/poses.json and synced to every client.
 * A missing file is created with the presets (inspired by the CraftMaster poster); afterwards the file is the
 * truth: a preset deleted in the editor stays deleted. A portrait whose pose no longer exists falls back to
 * {@link #DEFAULT}, and if that one was deleted too, to the built-in preset of that name.
 */
public final class PoseLibrary {

    public static final String DEFAULT = "classique";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, Pose> poses = new LinkedHashMap<>();

    public PoseLibrary() {}

    public PoseLibrary(Map<String, Pose> poses) {
        poses.forEach(this::put);
    }

    // ---------------------------------------------------------------- presets

    private static Pose pose(double x, double y, double z, double scale, double pitch, double roll, Object... parts) {
        Map<Part, Rotation> rot = new EnumMap<>(Part.class);
        java.util.EnumSet<Part> hidden = java.util.EnumSet.noneOf(Part.class);
        for (int i = 0; i < parts.length; ) {
            Part p = (Part) parts[i++];
            if (i < parts.length && parts[i] instanceof double[] r) {
                rot.put(p, new Rotation(r[0], r[1], r[2]));
                i++;
            } else {
                hidden.add(p);
            }
        }
        return new Pose(rot, hidden, x, y, z, scale, pitch, roll);
    }

    private static double[] r(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    /**
     * The poses shipped with the mod, in the order of the poster, for the baby-proportioned figure (18 px tall)
     * in the 18 × 24 px frame. A part without rotation is hidden.
     */
    public static Map<String, Pose> presets() {
        Map<String, Pose> m = new LinkedHashMap<>();
        // Bust: head and chest fill the frame, the legs are not shown
        m.put("classique", pose(0, -19.6, 1.5, 1.6, 0, 0,
                Part.HEAD, r(-4, 12, 0), Part.RIGHT_ARM, r(0, 0, 4), Part.LEFT_ARM, r(0, 0, -4),
                Part.RIGHT_LEG, Part.LEFT_LEG));
        // One foot already out of the frame, leaning toward the audience, a hand on the frame's side
        m.put("sort du cadre", pose(0, -10, 3, 1, 0, 3,
                Part.BODY, r(14, 0, 0), Part.HEAD, r(-12, 0, 0),
                Part.RIGHT_LEG, r(-38, 0, 0), Part.LEFT_LEG, r(14, 0, 0),
                Part.RIGHT_ARM, r(-55, 0, 12), Part.LEFT_ARM, r(-10, 0, -62)));
        // Sitting on the bottom edge, legs dangling out of the frame
        m.put("assis dans le cadre", pose(0, -16, 2, 1, 0, 0,
                Part.BODY, r(-6, 0, 0), Part.HEAD, r(-4, -14, 0),
                Part.RIGHT_LEG, r(-78, 10, 0), Part.LEFT_LEG, r(-66, -8, 0),
                Part.RIGHT_ARM, r(-24, 0, 10), Part.LEFT_ARM, r(-24, 0, -10)));
        // Standing under the frame and holding it above the head
        m.put("porte le cadre", pose(0, -28, 0, 1, 0, 4,
                Part.HEAD, r(-22, 0, 0),
                Part.RIGHT_ARM, r(-180, 0, -14), Part.LEFT_ARM, r(-180, 0, 14),
                Part.RIGHT_LEG, r(0, 0, 3), Part.LEFT_LEG, r(0, 0, -3)));
        // Leaning out over the bottom edge, one arm reaching toward the audience
        m.put("se penche dehors", pose(0, -10, 2, 1, 0, 0,
                Part.FIGURE, r(0, 16, 0), Part.BODY, r(38, 0, 0), Part.HEAD, r(-34, 0, 0),
                Part.RIGHT_ARM, r(-70, 0, 0), Part.LEFT_ARM, r(-30, 0, -24)));
        // Waving
        m.put("salut", pose(0, -10, 2, 1, 0, 0,
                Part.HEAD, r(0, -10, 6), Part.RIGHT_ARM, r(0, 0, 140), Part.LEFT_ARM, r(0, 0, -6),
                Part.RIGHT_LEG, r(0, 0, 2), Part.LEFT_LEG, r(0, 0, -2)));
        m.put("bras croisés", pose(0, -10, 2, 1, 0, 0,
                Part.HEAD, r(-4, 0, 0), Part.RIGHT_ARM, r(-72, -36, 0), Part.LEFT_ARM, r(-72, 36, 0),
                Part.RIGHT_LEG, r(0, 0, 3), Part.LEFT_LEG, r(0, 0, -3)));
        // Seated in front of the frame as if it were the back of a throne (LordFinn on the poster)
        m.put("trône", pose(0, -13, 3, 1, 0, 0,
                Part.HEAD, r(-6, 0, 0), Part.BODY, r(-4, 0, 0),
                Part.RIGHT_LEG, r(-88, 10, 0), Part.LEFT_LEG, r(-88, -10, 0),
                Part.RIGHT_ARM, r(-38, 0, 10), Part.LEFT_ARM, r(-38, 0, -10)));
        // Coming out of the back of the portrait: seen from behind, the upper body leans out of the flat back
        m.put("sort par derrière", pose(0, -10, -3, 1, 0, 0,
                Part.FIGURE, r(0, 180, 0), Part.BODY, r(28, 0, 0), Part.HEAD, r(-18, 0, 0),
                Part.RIGHT_ARM, r(-75, 0, 10), Part.LEFT_ARM, r(-40, 0, -18)));
        return m;
    }

    public static PoseLibrary withPresets() {
        return new PoseLibrary(presets());
    }

    // ---------------------------------------------------------------- access

    /** The pose of that name, else the default one, else the built-in default. */
    public Pose get(String name) {
        Pose p = poses.get(Pose.normalizeName(name));
        if (p == null) p = poses.get(DEFAULT);
        return p != null ? p : presets().get(DEFAULT);
    }

    public boolean has(String name) {
        return poses.containsKey(Pose.normalizeName(name));
    }

    public Map<String, Pose> all() {
        return Collections.unmodifiableMap(poses);
    }

    public List<String> names() {
        return List.copyOf(poses.keySet());
    }

    /** Adds or replaces a pose. Returns the name it was stored under, or null if the name is empty. */
    public String put(String name, Pose pose) {
        String key = Pose.normalizeName(name);
        if (key.isEmpty() || pose == null) return null;
        poses.put(key, pose);
        return key;
    }

    public boolean remove(String name) {
        return poses.remove(Pose.normalizeName(name)) != null;
    }

    // ---------------------------------------------------------------- JSON and file

    /** {"version": 1, "poses": {"classique": {...}, ...}} */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject list = new JsonObject();
        poses.forEach((name, pose) -> list.add(name, pose.toJson()));
        root.add("poses", list);
        return GSON.toJson(root);
    }

    /** Parses a library; malformed poses are skipped. Throws on a document that is not a JSON object. */
    public static PoseLibrary fromJson(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("poses.json: an object is expected");
        JsonObject o = root.getAsJsonObject();
        JsonObject list = o.has("poses") && o.get("poses").isJsonObject() ? o.getAsJsonObject("poses") : o;
        PoseLibrary lib = new PoseLibrary();
        for (Map.Entry<String, JsonElement> e : list.entrySet()) {
            if (e.getValue().isJsonObject()) lib.put(e.getKey(), Pose.fromJson(e.getValue().getAsJsonObject()));
        }
        return lib;
    }

    /**
     * Reads the file, or creates it with the presets. An unreadable file is set aside (poses.json.broken) and
     * replaced by the presets, so a typo never leaves the show without poses.
     */
    public static PoseLibrary load(Path file, Logger log) {
        if (Files.exists(file)) {
            try {
                return fromJson(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("[Portraits] {} illisible, mis de côté et remplacé par les poses fournies", file, e);
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
        }
        PoseLibrary lib = withPresets();
        lib.save(file, log);
        return lib;
    }

    /** Atomic write (temporary file then move). */
    public void save(Path file, Logger log) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[Portraits] Impossible d'écrire {}", file, e);
        }
    }

    /** Names in the order they are offered in the screens. */
    public Set<String> keySet() {
        return Collections.unmodifiableSet(poses.keySet());
    }
}
