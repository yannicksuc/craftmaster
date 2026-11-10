package fr.lordfinn.craftmaster.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The studio's camera registry (server side), saved in {@code config/craftmaster/cameras.json}. It is the source of
 * truth for the shots: a camera works even when its tripod's chunk is not loaded. Each camera also has a tripod
 * prop ({@link CameraEntity}); a prop whose camera is gone (or replaced by a newer prop) removes itself when its
 * chunk loads. Changes are sent to the clients by {@link Director}.
 */
public final class Cameras {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static CameraList list = new CameraList();
    /** The prop of each camera (entity UUID). */
    private static final Map<Integer, UUID> props = new HashMap<>();
    private static Path file;

    private Cameras() {}

    public static CameraList list() {
        return list;
    }

    public static CameraSpot get(int number) {
        return list.get(number);
    }

    // ---------------------------------------------------------------- file

    public static void load(Path path) {
        file = path;
        list = new CameraList();
        props.clear();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("cameras")) {
                JsonObject o = el.getAsJsonObject();
                CameraSpot s = new CameraSpot(o.get("number").getAsInt(), str(o, "name"), str(o, "role"), str(o, "world"),
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                        o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat(), o.has("fov") ? o.get("fov").getAsFloat() : 0,
                        str(o, "motion"));
                list.put(s);
                if (o.has("prop")) props.put(s.number(), UUID.fromString(o.get("prop").getAsString()));
            }
        } catch (IOException | RuntimeException e) {
            CraftMaster.LOG.error("[Caméras] {} illisible, aucune caméra chargée", path, e);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static void save() {
        if (file == null) return;
        JsonArray arr = new JsonArray();
        for (CameraSpot s : list.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("number", s.number());
            o.addProperty("name", s.name());
            o.addProperty("role", s.role());
            o.addProperty("world", s.world());
            o.addProperty("x", s.x());
            o.addProperty("y", s.y());
            o.addProperty("z", s.z());
            o.addProperty("yaw", s.yaw());
            o.addProperty("pitch", s.pitch());
            o.addProperty("fov", s.fov());
            o.addProperty("motion", s.motion());
            UUID prop = props.get(s.number());
            if (prop != null) o.addProperty("prop", prop.toString());
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("cameras", arr);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftMaster.LOG.error("[Caméras] Impossible d'enregistrer {}", file, e);
        }
    }

    // ---------------------------------------------------------------- changes

    /**
     * Creates or moves camera {@code spot.number()}: the registry entry and its tripod (moved if loaded, else a new
     * one is put there and the old one removes itself when its chunk loads).
     */
    public static void place(MinecraftServer server, CameraSpot spot) {
        list.put(spot);
        ServerLevel level = Busts.level(server, spot.world());
        CameraEntity prop = loadedProp(server, spot.number());
        if (prop != null && prop.level() == level) {
            prop.apply(spot);
        } else {
            if (prop != null) prop.discard();
            props.remove(spot.number());
            if (level != null) {
                CameraEntity e = CameraEntity.create(level, spot);
                // Known before it is added: adding it fires ENTITY_LOAD, which removes unknown tripods
                props.put(spot.number(), e.getUUID());
                level.addFreshEntity(e);
            }
        }
        changed(server);
    }

    /** Changes a camera's settings (name, role, zoom, motion, aim); false if it does not exist. */
    public static boolean update(MinecraftServer server, int number, UnaryOperator<CameraSpot> change) {
        CameraSpot old = list.get(number);
        if (old == null) return false;
        CameraSpot spot = change.apply(old);
        list.put(spot);
        CameraEntity prop = loadedProp(server, number);
        if (prop != null) prop.apply(spot);
        changed(server);
        return true;
    }

    /** Removes a camera and its tripod; false if it does not exist. */
    public static boolean remove(MinecraftServer server, int number) {
        if (list.get(number) == null) return false;
        CameraEntity prop = loadedProp(server, number);
        if (prop != null) prop.discard();
        list.remove(number);
        props.remove(number);
        changed(server);
        return true;
    }

    /** The tripod was knocked down by an organiser: its camera goes with it. */
    static void removedByHand(MinecraftServer server, CameraEntity e) {
        if (e.getUUID().equals(props.get(e.number()))) {
            list.remove(e.number());
            props.remove(e.number());
            changed(server);
        }
    }

    private static void changed(MinecraftServer server) {
        save();
        Director.camerasChanged(server);
    }

    private static CameraEntity loadedProp(MinecraftServer server, int number) {
        UUID id = props.get(number);
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof CameraEntity c && !c.isRemoved()) return c;
        }
        return null;
    }

    /** A tripod whose chunk just loaded: removed if its camera is gone or has another tripod, else put in place. */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof CameraEntity c) || entity.getType() != ModContent.CAMERA) return;
        CameraSpot spot = list.get(c.number());
        if (spot == null || !c.getUUID().equals(props.get(c.number()))) {
            c.discard();
            return;
        }
        if (c.lens().distanceToSqr(spot.x(), spot.y(), spot.z()) > 1e-4 || c.lensYaw() != spot.yaw() || c.lensPitch() != spot.pitch()
                || !c.cameraName().equals(spot.name())) c.apply(spot);
    }
}
