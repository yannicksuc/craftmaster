package fr.lordfinn.craftmaster.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.Director;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * The recorded camera moves ("trajets"), saved in {@code config/craftmaster/trajets.json}:
 * {@code {"version": 1, "paths": [{name, lieu, world, steps: [{x, y, z, yaw, pitch, fov?, travel?, hold?, curve?}]}]}}.
 * Missing fields take their default, unknown ones are ignored, a broken path is skipped (the others load). Like the
 * cameras they are world set-up, not show data: outside the undo journal. The clients get them with the show state.
 */
public final class CameraPaths {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TreeMap<String, CameraPath> PATHS = new TreeMap<>();
    private static Path file;

    private CameraPaths() {}

    public static List<CameraPath> all() {
        return new ArrayList<>(PATHS.values());
    }

    public static Map<String, CameraPath> byName() {
        return PATHS;
    }

    /** By name, ignoring case, accents and spaces ("Le pont" → "le-pont"); also "pont" for "le-pont" if unique. */
    public static CameraPath get(String name) {
        if (name == null) return null;
        String n = CameraPath.normalizeName(name);
        CameraPath p = PATHS.get(n);
        if (p != null) return p;
        String bare = n.replaceFirst("^(le|la|les|l|du|de|des|trajet|travelling)-", "");
        p = PATHS.get(bare);
        if (p != null) return p;
        CameraPath found = null;
        for (CameraPath c : PATHS.values()) {
            if (!c.name().contains(bare)) continue;
            if (found != null) return null;
            found = c;
        }
        return found;
    }

    public static void load(Path path) {
        file = path;
        PATHS.clear();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.has("paths") ? root.getAsJsonArray("paths") : new JsonArray();
            for (JsonElement el : arr) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    List<CameraPath.Step> steps = new ArrayList<>();
                    if (o.has("steps")) for (JsonElement se : o.getAsJsonArray("steps")) {
                        JsonObject s = se.getAsJsonObject();
                        steps.add(new CameraPath.Step(num(s, "x", 0), num(s, "y", 0), num(s, "z", 0), (float) num(s, "yaw", 0),
                                (float) num(s, "pitch", 0), (float) num(s, "fov", 0), num(s, "travel", CameraPath.DEFAULT_TRAVEL),
                                num(s, "hold", 0), s.has("curve") ? s.get("curve").getAsString() : null,
                                s.has("cible") ? s.get("cible").getAsString() : ""));
                    }
                    CameraPath p = new CameraPath(o.get("name").getAsString(), o.has("lieu") ? o.get("lieu").getAsString() : null,
                            o.has("world") ? o.get("world").getAsString() : "minecraft:overworld", steps,
                            o.has("camera") ? o.get("camera").getAsString() : "");
                    PATHS.put(p.name(), p);
                } catch (RuntimeException e) {
                    CraftMaster.LOG.warn("[Trajets] Trajet illisible ignoré dans {} : {}", path, el);
                }
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.error("[Trajets] {} illisible, aucun trajet chargé", path, e);
        }
    }

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    private static void save() {
        if (file == null) return;
        JsonArray arr = new JsonArray();
        for (CameraPath p : PATHS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            o.addProperty("lieu", p.lieu());
            o.addProperty("world", p.world());
            if (!p.camera().isEmpty()) o.addProperty("camera", p.camera());
            JsonArray steps = new JsonArray();
            for (CameraPath.Step s : p.steps()) {
                JsonObject so = new JsonObject();
                so.addProperty("x", s.x());
                so.addProperty("y", s.y());
                so.addProperty("z", s.z());
                so.addProperty("yaw", s.yaw());
                so.addProperty("pitch", s.pitch());
                if (s.fov() > 0) so.addProperty("fov", s.fov());
                so.addProperty("travel", s.travel());
                if (s.hold() > 0) so.addProperty("hold", s.hold());
                if (!CameraPath.SMOOTH.equals(s.curve())) so.addProperty("curve", s.curve());
                if (!s.target().isEmpty()) so.addProperty("cible", s.target());
                steps.add(so);
            }
            o.add("steps", steps);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("paths", arr);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftMaster.LOG.error("[Trajets] Impossible d'enregistrer {}", file, e);
        }
    }

    /** Creates or replaces a path. */
    public static void put(MinecraftServer server, CameraPath path) {
        PATHS.put(path.name(), path);
        changed(server);
    }

    /** Changes an existing path; false if there is none of that name. */
    public static boolean update(MinecraftServer server, String name, UnaryOperator<CameraPath> change) {
        CameraPath old = PATHS.get(CameraPath.normalizeName(name));
        if (old == null) return false;
        PATHS.put(old.name(), change.apply(old));
        changed(server);
        return true;
    }

    public static boolean remove(MinecraftServer server, String name) {
        boolean removed = PATHS.remove(CameraPath.normalizeName(name)) != null;
        if (removed) changed(server);
        return removed;
    }

    private static void changed(MinecraftServer server) {
        save();
        if (server != null) Director.pathsChanged(server);
    }
}
