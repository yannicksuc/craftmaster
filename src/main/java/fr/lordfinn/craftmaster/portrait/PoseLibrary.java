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

    /**
     * Poses given at random to portraits (new ones, and those still in {@link #DEFAULT}, which LordFinn finds too
     * plain): never the basic pose.
     */
    public static final java.util.List<String> VARIED = java.util.List.of("salut", "bras croisés", "se penche dehors", "assis dans le cadre", "trône");

    /** One of {@link #VARIED}, stable for a given seed (a portrait's UUID). */
    public static String varied(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        return VARIED.get((int) Math.floorMod(h, (long) VARIED.size()));
    }
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
     * The poses shipped with the mod, in the spirit of the CraftMaster poster, for the baby-proportioned figure
     * (18 px tall: hips 6 px above the feet, shoulders 11, arms 5 long) in the 18 × 24 px frame (opening ±7 × ±10,
     * bottom bar from y -12 to -10, canvas face at z -0.5). What is behind the canvas is inside the painting (cut).
     * A part without rotation is hidden. Feet standing on the bar sit 0.06 px above it (no z-fighting).
     */
    public static Map<String, Pose> presets() {
        Map<String, Pose> m = new LinkedHashMap<>();
        // Bust: head and chest come out of the painting, hands resting on the bottom bar
        m.put("classique", pose(0, -19.6, 1, 1.6, 0, 0,
                Part.HEAD, r(-4, 12, 0), Part.RIGHT_ARM, r(-28, 0, 6), Part.LEFT_ARM, r(-28, 0, -6),
                Part.RIGHT_LEG, Part.LEFT_LEG));
        // Climbing out: one leg hooked over the bottom bar, the other still in the painting, hands on the sides
        m.put("sort du cadre", pose(0, -15, -0.5, 1, 0, 3,
                Part.BODY, r(22, 0, 0), Part.HEAD, r(-16, 0, 0),
                Part.RIGHT_LEG, r(-82, 0, 4), Part.LEFT_LEG, r(10, 0, 0),
                Part.RIGHT_ARM, r(-30, 0, 62), Part.LEFT_ARM, r(-30, 0, -62)));
        // Sitting on the bottom bar, legs dangling out of the frame, hands on the bar
        m.put("assis dans le cadre", pose(0, -15.5, 0.5, 1, 0, 0,
                Part.HEAD, r(-6, -12, 0),
                Part.RIGHT_LEG, r(-58, 8, 0), Part.LEFT_LEG, r(-50, -6, 0),
                Part.RIGHT_ARM, r(-12, 0, 22), Part.LEFT_ARM, r(-12, 0, -22)));
        // Standing under the frame, holding it above the head by its bottom bar
        m.put("porte le cadre", pose(0, -28, 0, 1, 0, 4,
                Part.HEAD, r(-25, 0, 0),
                Part.RIGHT_ARM, r(-180, 0, -15), Part.LEFT_ARM, r(-180, 0, 15),
                Part.RIGHT_LEG, r(0, 0, 3), Part.LEFT_LEG, r(0, 0, -3)));
        // Leaning out of the painting: the upper body outside, the legs still in the picture, one arm reaching out
        m.put("se penche dehors", pose(0, -9.94, -3, 1, 0, 0,
                Part.FIGURE, r(0, 12, 0), Part.BODY, r(62, 0, 0), Part.HEAD, r(-50, 0, 0),
                Part.RIGHT_ARM, r(-55, 0, 35), Part.LEFT_ARM, r(-40, 0, -30)));
        // Standing on the bottom bar, waving, the other hand holding the side of the frame
        m.put("salut", pose(0, -9.94, 0, 1, 0, 0,
                Part.HEAD, r(0, -10, 6), Part.RIGHT_ARM, r(0, 0, 140), Part.LEFT_ARM, r(-20, 0, -62),
                Part.RIGHT_LEG, r(0, 0, 2), Part.LEFT_LEG, r(0, 0, -2)));
        // Standing on the bottom bar, leaning on the side of the frame, arms crossed
        m.put("bras croisés", pose(-1.5, -9.94, 0.5, 1, 0, 0,
                Part.FIGURE, r(0, 0, 8), Part.HEAD, r(-4, -10, 0),
                Part.RIGHT_ARM, r(-72, -36, 0), Part.LEFT_ARM, r(-72, 36, 0),
                Part.RIGHT_LEG, r(0, 0, -5), Part.LEFT_LEG, r(0, 0, -5)));  // parallel: crossed legs flicker
        // Seated on the bottom bar as on a throne (LordFinn on the poster), the painting as its back
        m.put("trône", pose(0, -15.5, 1.5, 1, 0, 0,
                Part.HEAD, r(-8, 0, 0),
                Part.RIGHT_LEG, r(-88, 12, 0), Part.LEFT_LEG, r(-88, -12, 0),
                Part.RIGHT_ARM, r(-40, 0, 24), Part.LEFT_ARM, r(-40, 0, -24)));
        // Coming out of the flat back of the portrait (seen from behind), the legs still inside the painting
        m.put("sort par derrière", pose(0, -9.94, -1, 1, 0, 0,
                Part.FIGURE, r(0, 180, 0), Part.BODY, r(55, 0, 0), Part.HEAD, r(-40, 0, 0),
                Part.RIGHT_ARM, r(-90, 0, 10), Part.LEFT_ARM, r(-40, 0, -30)));
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
                PoseLibrary lib = fromJson(Files.readString(file, StandardCharsets.UTF_8));
                if (lib.liftFeet()) lib.save(file, log);
                return lib;
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

    /**
     * Shipped poses saved before 13/11/2026 stand exactly on the bottom bar (feet at y -10): lifted to the
     * presets' current height, so the soles no longer flicker against the bar. Returns true if one changed.
     */
    boolean liftFeet() {
        boolean changed = false;
        Map<String, Pose> shipped = presets();
        for (Map.Entry<String, Pose> e : poses.entrySet()) {
            Pose now = shipped.get(e.getKey());
            Pose p = e.getValue();
            if (now != null && p.offsetY() == -10 && now.offsetY() != -10) {
                e.setValue(p.withOffset(p.offsetX(), now.offsetY(), p.offsetZ()));
                changed = true;
            }
            // "Se penche dehors" had its right arm stiff straight forward at shoulder height: never that gesture
            Pose cur = e.getValue();
            Pose.Rotation arm = cur.rotations().get(Part.RIGHT_ARM);
            if (now != null && e.getKey().equals("se penche dehors") && arm != null && arm.x() == -95 && arm.y() == 0 && arm.z() == 0) {
                e.setValue(cur.withRotation(Part.RIGHT_ARM, now.rotations().get(Part.RIGHT_ARM)));
                changed = true;
            }
            // "Bras croisés" had its legs converging (-10 / -3): they went through each other and flickered
            cur = e.getValue();
            Pose.Rotation rl = cur.rotations().get(Part.RIGHT_LEG), ll = cur.rotations().get(Part.LEFT_LEG);
            if (now != null && e.getKey().equals("bras croisés") && rl != null && ll != null && rl.z() == -10 && ll.z() == -3) {
                e.setValue(cur.withRotation(Part.RIGHT_LEG, now.rotations().get(Part.RIGHT_LEG))
                        .withRotation(Part.LEFT_LEG, now.rotations().get(Part.LEFT_LEG)));
                changed = true;
            }
        }
        return changed;
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
