package fr.lordfinn.craftmaster.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

/**
 * WorldEdit without block updates by default: when a player joins, their WorldEdit session gets
 * {@code //perf neighbors off} and {@code //perf update off} (WorldEdit forgets {@code //perf} at every login, the
 * setting is not saved and has no config option). Big pastes then leave sand, water, redstone and plants as they are.
 * <p>
 * WorldEdit is optional: everything goes through reflection, only when the {@code worldedit} mod is there. The
 * setting is {@code config/craftmaster/worldedit.json} ({@code "perfOff": true}), switched with
 * {@code /craftMasterWorldEdit perf on|off}. A player can still type {@code //perf} for the rest of the session.
 */
public final class WorldEditPerf {

    private WorldEditPerf() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Applied again this many ticks after the login, in case WorldEdit made the session later. */
    private static final int SECOND_PASS_TICKS = 40;

    private static boolean enabled = true;
    private static boolean warned;
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("worldedit");
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(WorldEditPerf::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> registerCommands(dispatcher));
    }

    private static void onJoin(ServerPlayer player) {
        if (!enabled || !available()) return;
        apply(player);
        PENDING.put(player.getUUID(), SECOND_PASS_TICKS);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (e.getValue() > 1) {
                e.setValue(e.getValue() - 1);
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && enabled) apply(p);
        }
    }

    /**
     * Sets the player's WorldEdit session without neighbour or block updates.
     *
     * @return true if it worked
     */
    public static boolean apply(ServerPlayer player) {
        try {
            Object session = session(player);
            Class<?> sideEffect = Class.forName("com.sk89q.worldedit.util.SideEffect");
            Class<?> state = Class.forName("com.sk89q.worldedit.util.SideEffect$State");
            Object off = enumValue(state, "OFF");
            Object set = session.getClass().getMethod("getSideEffectSet").invoke(session);
            Method with = set.getClass().getMethod("with", sideEffect, state);
            set = with.invoke(set, enumValue(sideEffect, "NEIGHBORS"), off);
            set = with.invoke(set, enumValue(sideEffect, "UPDATE"), off);
            session.getClass().getMethod("setSideEffectSet", set.getClass()).invoke(session, set);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                CraftMaster.LOG.warn("[WorldEdit] impossible de couper les mises à jour pour {} : {}", player.getScoreboardName(), e.toString());
            }
            return false;
        }
    }

    /** "neighbors off, update off" as WorldEdit reports it for the player, or null if it cannot be read. */
    public static String describe(ServerPlayer player) {
        try {
            Object session = session(player);
            Object set = session.getClass().getMethod("getSideEffectSet").invoke(session);
            Class<?> sideEffect = Class.forName("com.sk89q.worldedit.util.SideEffect");
            Method get = set.getClass().getMethod("getState", sideEffect);
            return "neighbors " + get.invoke(set, enumValue(sideEffect, "NEIGHBORS")).toString().toLowerCase()
                    + ", update " + get.invoke(set, enumValue(sideEffect, "UPDATE")).toString().toLowerCase();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** WorldEdit.getInstance().getSessionManager().get(the player as a WorldEdit actor). */
    private static Object session(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> we = Class.forName("com.sk89q.worldedit.WorldEdit");
        Object instance = we.getMethod("getInstance").invoke(null);
        Object manager = we.getMethod("getSessionManager").invoke(instance);
        Class<?> adapter = Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
        Object actor;
        try {
            // WorldEdit 7.4 (Minecraft 26.x): FabricAdapter.get().fromNativePlayer(player)
            Object a = adapter.getMethod("get").invoke(null);
            actor = a.getClass().getMethod("fromNativePlayer", ServerPlayer.class).invoke(a, player);
        } catch (NoSuchMethodException e) {
            // Older WorldEdit: the static FabricAdapter.adaptPlayer(player)
            actor = adapter.getMethod("adaptPlayer", ServerPlayer.class).invoke(null, player);
        }
        Class<?> owner = Class.forName("com.sk89q.worldedit.session.SessionOwner");
        return manager.getClass().getMethod("get", owner).invoke(manager, actor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    // ---------------------------------------------------------------- setting

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/worldedit.json");
    }

    static void load() {
        enabled = true;
        Path f = file();
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("perfOff")) enabled = o.get("perfOff").getAsBoolean();
            } else if (available()) {
                save();
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[WorldEdit] {} illisible", f, e);
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("perfOff", enabled);
        o.addProperty("_doc", "true : à la connexion, WorldEdit est réglé comme //perf neighbors off + //perf update off. "
                + "/craftMasterWorldEdit perf on|off");
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[WorldEdit] impossible d'écrire {}", file(), e);
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("craftMasterWorldEdit", "craftmasterworldedit", "cfwe")) d.register(tree(n));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String root) {
        return literal(root).requires(Permissions.ADMIN)
                .executes(c -> {
                    String mine = c.getSource().getPlayer() != null && available() ? describe(c.getSource().getPlayer()) : null;
                    c.getSource().sendSuccess(() -> Component.literal((available() ? "WorldEdit présent" : "WorldEdit absent")
                            + " · sans mises à jour à la connexion : " + (enabled ? "oui" : "non")
                            + (mine != null ? " · votre session : " + mine : "")).withStyle(ChatFormatting.GOLD), false);
                    return 1;
                })
                .then(literal("perf")
                        // "perf off" = like //perf ... off: no updates at login (the default)
                        .then(literal("off").executes(c -> set(c.getSource(), true)))
                        .then(literal("on").executes(c -> set(c.getSource(), false))));
    }

    private static int set(CommandSourceStack source, boolean perfOff) {
        enabled = perfOff;
        save();
        int n = 0;
        if (perfOff && available())
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) if (apply(p)) n++;
        int done = n;
        source.sendSuccess(() -> Component.literal(perfOff
                ? "WorldEdit sans mises à jour (voisins, update) à chaque connexion" + (available() ? " ; appliqué à " + done + " joueur(s) en ligne." : " (WorldEdit absent).")
                : "WorldEdit garde ses réglages par défaut à la connexion (les sessions en cours ne changent pas, //perf pour elles).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
