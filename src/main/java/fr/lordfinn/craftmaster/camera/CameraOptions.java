package fr.lordfinn.craftmaster.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-camera options kept apart from {@link CameraSpot} (which many places build): whether its prop is hidden (no
 * one sees it but the organisers holding a camera) and whether its travellings show their ghost (a phantom camera
 * moving along them). Saved in config/craftmaster/cameras-options.json, by camera key ("studio:2").
 */
public final class CameraOptions {

    private CameraOptions() {}

    public static final class Options {
        public boolean cachee;
        /** The travellings' ghost is shown (organisers only). */
        public boolean fantome = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Options> options = new LinkedHashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/cameras-options.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) {
                Map<String, Options> m = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Options>>() {}.getType());
                options = m == null ? new LinkedHashMap<>() : m;
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Caméras] {} illisible", file(), e);
            options = new LinkedHashMap<>();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(options), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Caméras] {} : écriture impossible", file(), e);
        }
    }

    public static Options get(String key) {
        Options o = options.get(key);
        return o == null ? new Options() : o;
    }

    public static boolean hidden(String key) {
        return get(key).cachee;
    }

    public static boolean ghost(String key) {
        return get(key).fantome;
    }

    public static void setHidden(String key, boolean on) {
        options.computeIfAbsent(key, k -> new Options()).cachee = on;
        save();
    }

    public static void setGhost(String key, boolean on) {
        options.computeIfAbsent(key, k -> new Options()).fantome = on;
        save();
    }

    /** A camera changed its key (moved to another lieu): its options follow. */
    public static void moved(String from, String to) {
        Options o = options.remove(from);
        if (o != null) {
            options.put(to, o);
            save();
        }
    }
}
