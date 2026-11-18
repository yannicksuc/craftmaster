package fr.lordfinn.craftmaster.trial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.Show;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The task zones of the server (config/craftmaster/zones.json): a contestant who leaves one gets a warning (title,
 * sound, then a reminder in the action bar while outside) and the organisers are told; coming back is noted too.
 * Zones are set up by commands ({@code /cm epreuve zone}) and survive restarts.
 */
public final class TrialZones {

    private TrialZones() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Zone> ZONES = new LinkedHashMap<>();
    /** Last known state of each (player, zone): inside or not. Absent = not seen inside yet (no warning). */
    private static final Map<UUID, Map<String, Boolean>> INSIDE = new HashMap<>();
    /** Zones whose outline is drawn, until that server tick. */
    private static final Map<String, Long> SHOWN = new HashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/zones.json");
    }

    public static Map<String, Zone> zones() {
        return ZONES;
    }

    public static void load() {
        ZONES.clear();
        INSIDE.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonArray a = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : a) {
                JsonObject o = e.getAsJsonObject();
                List<String> players = new ArrayList<>();
                if (o.has("players")) o.getAsJsonArray("players").forEach(p -> players.add(p.getAsString()));
                Zone z = new Zone(o.get("name").getAsString(), o.get("world").getAsString(), o.get("x0").getAsInt(), o.get("y0").getAsInt(),
                        o.get("z0").getAsInt(), o.get("x1").getAsInt(), o.get("y1").getAsInt(), o.get("z1").getAsInt(), players,
                        !o.has("enabled") || o.get("enabled").getAsBoolean());
                ZONES.put(z.name(), z);
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Zones] {} illisible", f, e);
        }
    }

    public static void save() {
        JsonArray a = new JsonArray();
        for (Zone z : ZONES.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", z.name());
            o.addProperty("world", z.world());
            o.addProperty("x0", z.x0());
            o.addProperty("y0", z.y0());
            o.addProperty("z0", z.z0());
            o.addProperty("x1", z.x1());
            o.addProperty("y1", z.y1());
            o.addProperty("z1", z.z1());
            JsonArray ps = new JsonArray();
            z.players().forEach(ps::add);
            o.add("players", ps);
            o.addProperty("enabled", z.enabled());
            a.add(o);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Zones] impossible d'écrire {}", file(), e);
        }
    }

    public static void put(Zone z) {
        ZONES.put(z.name(), z);
        INSIDE.values().forEach(m -> m.remove(z.name()));
        save();
    }

    public static boolean remove(String name) {
        boolean removed = ZONES.remove(name) != null;
        INSIDE.values().forEach(m -> m.remove(name));
        SHOWN.remove(name);
        if (removed) save();
        return removed;
    }

    /** Draws the zone's edges with red dust for {@code seconds}. */
    public static void show(MinecraftServer server, String name, int seconds) {
        SHOWN.put(name, server.getTickCount() + seconds * 20L);
    }

    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) return;
        long t = server.getTickCount();
        if (t % 10 == 0) watch(server, t);
        if (t % 5 == 0 && !SHOWN.isEmpty()) {
            SHOWN.entrySet().removeIf(e -> e.getValue() < t || !ZONES.containsKey(e.getKey()));
            for (String n : SHOWN.keySet()) outline(server, ZONES.get(n));
        }
    }

    private static void watch(MinecraftServer server, long tick) {
        List<String> contestants = Judging.contestants();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String name = p.getGameProfile().name();
            String world = p.level().dimension().identifier().toString();
            Map<String, Boolean> state = INSIDE.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
            for (Zone z : ZONES.values()) {
                if (!z.watches(name, contestants) || p.isSpectator()) {
                    state.remove(z.name());
                    continue;
                }
                boolean in = z.contains(world, p.getX(), p.getY(), p.getZ());
                Boolean was = state.get(z.name());
                if (was == null) {
                    // Watched from the first time they are seen inside
                    if (in) state.put(z.name(), true);
                    continue;
                }
                if (was && !in) left(server, p, z);
                else if (!was && in) back(server, p, z);
                else if (!in && tick % 40 == 0)
                    p.sendOverlayMessage(Component.literal("⚠ Reviens dans la zone « " + z.name() + " » !").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                state.put(z.name(), in);
            }
        }
    }

    private static void left(MinecraftServer server, ServerPlayer p, Zone z) {
        String name = p.getGameProfile().name();
        Show.title(p, Component.literal("Hors zone !").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("Reviens dans « " + z.name() + " »").withStyle(ChatFormatting.YELLOW), 2, 30, 10);
        Show.sound(p, SoundEvents.NOTE_BLOCK_BASS, 1f, 0.5f);
        TrialLog.add(server, "Zone « " + z.name() + " » : " + name + " est sorti");
        for (ServerPlayer a : server.getPlayerList().getPlayers())
            if (Permissions.isAdmin(a) && a != p)
                a.sendSystemMessage(Component.literal("⚠ " + name + " est sorti de la zone « " + z.name() + " »").withStyle(ChatFormatting.RED));
    }

    private static void back(MinecraftServer server, ServerPlayer p, Zone z) {
        String name = p.getGameProfile().name();
        p.sendOverlayMessage(Component.literal("✔ De retour dans la zone « " + z.name() + " »").withStyle(ChatFormatting.GREEN));
        TrialLog.add(server, "Zone « " + z.name() + " » : " + name + " est revenu");
        for (ServerPlayer a : server.getPlayerList().getPlayers())
            if (Permissions.isAdmin(a) && a != p)
                a.sendSystemMessage(Component.literal("✔ " + name + " est revenu dans la zone « " + z.name() + " »").withStyle(ChatFormatting.GRAY));
    }

    private static void outline(MinecraftServer server, Zone z) {
        Identifier id = Identifier.tryParse(z.world());
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return;
        DustParticleOptions dust = new DustParticleOptions(0xE03030, 1.4f);
        double x0 = z.x0(), y0 = z.y0(), z0 = z.z0(), x1 = z.x1() + 1, y1 = z.y1() + 1, z1 = z.z1() + 1;
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i], b = corners[(i + 1) % 4];
            for (double y : new double[]{y0, y1}) edge(level, dust, a[0], y, a[1], b[0], y, b[1]);
            edge(level, dust, a[0], y0, a[1], a[0], y1, a[1]);
        }
    }

    private static void edge(ServerLevel level, DustParticleOptions dust, double ax, double ay, double az, double bx, double by, double bz) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int n = (int) Math.min(200, Math.max(1, len * 2));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            level.sendParticles(dust, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, 1, 0, 0, 0, 0);
        }
    }
}
