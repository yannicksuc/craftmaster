package fr.lordfinn.craftmaster.garage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Garage doors: the motor block, the named doors (config/craftmaster/garages.json, for the show's actions and the
 * voice control) and {@code /craftMasterGarage} ({@code /cfga}).
 */
public final class GarageDoors {

    private GarageDoors() {}

    public static GarageMotorBlock MOTOR;
    public static BlockEntityType<GarageMotorBlockEntity> MOTOR_BE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Door name → its motor. */
    private static final Map<String, BlockSpot> NAMES = new LinkedHashMap<>();

    public static void register() {
        MOTOR = ModContent.block("garage_door_motor", GarageMotorBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL));
        MOTOR_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CraftMaster.id("garage_door_motor"),
                new BlockEntityType<>(GarageMotorBlockEntity::new, Set.of(MOTOR)));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> registerCommands(dispatcher));
    }

    // ---------------------------------------------------------------- named doors

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/garages.json");
    }

    public static Map<String, BlockSpot> names() {
        return NAMES;
    }

    static void load() {
        NAMES.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String name : o.keySet()) {
                JsonObject s = o.getAsJsonObject(name);
                NAMES.put(name, new BlockSpot(s.get("world").getAsString(), s.get("x").getAsInt(), s.get("y").getAsInt(), s.get("z").getAsInt()));
            }
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Garage] {} illisible", f, e);
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        NAMES.forEach((name, s) -> {
            JsonObject j = new JsonObject();
            j.addProperty("world", s.world());
            j.addProperty("x", s.x());
            j.addProperty("y", s.y());
            j.addProperty("z", s.z());
            o.add(name, j);
        });
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Garage] impossible d'écrire {}", file(), e);
        }
    }

    /** The registered name matching {@code name} (any case), or null. */
    public static String resolve(String name) {
        if (name == null) return NAMES.size() == 1 ? NAMES.keySet().iterator().next() : null;
        for (String n : NAMES.keySet()) if (n.equalsIgnoreCase(name)) return n;
        return null;
    }

    /**
     * Opens, closes or switches a named door (the show's garage.* actions).
     *
     * @param open true, false, or null to switch
     * @return what happened (French), starting with "⚠" if it did not work
     */
    public static String move(MinecraftServer server, String name, Boolean open) {
        String n = resolve(name);
        if (n == null) return "⚠ Porte de garage « " + (name == null ? "" : name) + " » inconnue (portes : "
                + (NAMES.isEmpty() ? "aucune" : String.join(", ", NAMES.keySet())) + ").";
        BlockSpot b = NAMES.get(n);
        ServerLevel level = Busts.level(server, b.world());
        if (level == null) return "⚠ Monde introuvable pour la porte « " + n + " » : " + b.world();
        BlockPos pos = new BlockPos(b.x(), b.y(), b.z());
        if (!level.isLoaded(pos)) return "⚠ La porte « " + n + " » n'est pas chargée (personne à côté).";
        if (!(level.getBlockEntity(pos) instanceof GarageMotorBlockEntity be)) return "⚠ Plus de moteur de porte en " + b.describe() + " (porte « " + n + " »).";
        Boolean to = be.move(open);
        if (to == null) return "⚠ Pas de porte sous le moteur « " + n + " ».";
        return (to ? "Ouverture" : "Fermeture") + " de la porte « " + n + " ».";
    }

    // ---------------------------------------------------------------- commands

    private static final SuggestionProvider<CommandSourceStack> DOORS =
            (c, b) -> SharedSuggestionProvider.suggest(NAMES.keySet(), b);

    private static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("craftMasterGarage", "craftmastergarage", "cfga")) d.register(tree(n));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String root) {
        LiteralArgumentBuilder<CommandSourceStack> t = literal(root).requires(Permissions.ADMIN);
        LiteralArgumentBuilder<CommandSourceStack> named = literal("porte");
        var nameArg = argument("nom", word()).suggests(DOORS);
        for (String action : List.of("open", "close", "toggle")) {
            Boolean target = action.equals("toggle") ? null : action.equals("open");
            t.then(literal(action).then(argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                GarageMotorBlockEntity be = motor(c);
                if (be == null) return 0;
                Boolean to = be.move(target);
                if (to == null) return fail(c, "Pas de porte sous le moteur (posez-le juste au-dessus du panneau, ou donnez une zone).");
                return ok(c, to ? "Ouverture de la porte." : "Fermeture de la porte.");
            })));
            nameArg.then(literal(action).executes(c -> {
                String r = move(c.getSource().getServer(), c.getArgument("nom", String.class), target);
                return r.startsWith("⚠") ? fail(c, r.substring(2)) : ok(c, r);
            }));
        }
        t.then(named.then(nameArg));

        // Naming a door for the show's actions and the voice control
        t.then(literal("nom").then(argument("pos", BlockPosArgument.blockPos()).then(argument("nom", word()).executes(c -> {
            GarageMotorBlockEntity be = motor(c);
            if (be == null) return 0;
            String name = c.getArgument("nom", String.class);
            String old = resolve(name);
            if (old != null) NAMES.remove(old);
            NAMES.values().removeIf(s -> s.x() == be.getBlockPos().getX() && s.y() == be.getBlockPos().getY() && s.z() == be.getBlockPos().getZ()
                    && s.world().equals(Busts.worldId(c.getSource().getLevel())));
            BlockPos p = be.getBlockPos();
            NAMES.put(name, new BlockSpot(Busts.worldId(c.getSource().getLevel()), p.getX(), p.getY(), p.getZ()));
            be.setName(name);
            save();
            return ok(c, "Porte « " + name + " » enregistrée (" + p.toShortString() + ").");
        }))));
        t.then(literal("oublier").then(argument("nom", word()).suggests(DOORS).executes(c -> {
            String n = resolve(c.getArgument("nom", String.class));
            if (n == null) return fail(c, "Porte inconnue.");
            NAMES.remove(n);
            save();
            return ok(c, "Porte « " + n + " » oubliée (le moteur reste en place).");
        })));
        t.then(literal("liste").executes(c -> {
            if (NAMES.isEmpty()) return ok(c, "Aucune porte nommée. /craftMasterGarage nom <x y z> <nom>");
            NAMES.forEach((n, s) -> c.getSource().sendSuccess(() -> Component.literal("• " + n + " : " + s.describe()), false));
            return NAMES.size();
        }));

        // Mixed-block doors: the panel as a box, relative to the motor
        t.then(literal("zone").then(argument("pos", BlockPosArgument.blockPos())
                .then(literal("auto").executes(c -> {
                    GarageMotorBlockEntity be = motor(c);
                    if (be == null) return 0;
                    be.setBox(null);
                    c.getSource().sendSuccess(() -> GarageMotorBlock.describe(be.findPanel()), false);
                    return 1;
                }))
                .then(argument("de", BlockPosArgument.blockPos()).then(argument("a", BlockPosArgument.blockPos()).executes(c -> {
                    GarageMotorBlockEntity be = motor(c);
                    if (be == null) return 0;
                    if (be.isOpen() || be.isMoving()) return fail(c, "Fermez d'abord la porte.");
                    BlockPos m = be.getBlockPos();
                    BlockPos a = BlockPosArgument.getBlockPos(c, "de").subtract(m), b = BlockPosArgument.getBlockPos(c, "a").subtract(m);
                    if (Math.max(Math.abs(a.getX()), Math.abs(b.getX())) > GaragePanel.MAX_REACH
                            || Math.max(Math.abs(a.getY()), Math.abs(b.getY())) > GaragePanel.MAX_REACH
                            || Math.max(Math.abs(a.getZ()), Math.abs(b.getZ())) > GaragePanel.MAX_REACH)
                        return fail(c, "Zone trop loin du moteur (" + GaragePanel.MAX_REACH + " blocs au plus).");
                    be.setBox(new int[]{a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()});
                    c.getSource().sendSuccess(() -> GarageMotorBlock.describe(be.findPanel()), false);
                    return 1;
                })))));

        // Speed: seconds per block of height
        t.then(literal("vitesse").then(argument("pos", BlockPosArgument.blockPos())
                .then(argument("secondes", DoubleArgumentType.doubleArg(GarageMotion.MIN_TICKS_PER_BLOCK / 20.0, GarageMotion.MAX_TICKS_PER_BLOCK / 20.0))
                        .executes(c -> {
                            GarageMotorBlockEntity be = motor(c);
                            if (be == null) return 0;
                            be.setTicksPerBlock((int) Math.round(DoubleArgumentType.getDouble(c, "secondes") * 20));
                            return ok(c, String.format(Locale.FRANCE, "Vitesse : %.2f s par bloc de hauteur.", be.ticksPerBlock() / 20.0));
                        }))));

        t.then(literal("info").then(argument("pos", BlockPosArgument.blockPos()).executes(c -> {
            GarageMotorBlockEntity be = motor(c);
            if (be == null) return 0;
            GaragePanel p = be.isOpen() || be.isMoving() ? be.panel() : be.findPanel();
            c.getSource().sendSuccess(() -> GarageMotorBlock.describe(p), false);
            String state = be.isMoving() ? (be.isOpen() ? "s'ouvre" : "se ferme") : be.isOpen() ? "ouverte" : "fermée";
            int[] box = be.box();
            c.getSource().sendSuccess(() -> Component.literal("Porte " + state
                    + (be.name().isEmpty() ? "" : " · nom « " + be.name() + " »")
                    + String.format(Locale.FRANCE, " · %.2f s par bloc", be.ticksPerBlock() / 20.0)
                    + (box == null ? " · panneau trouvé tout seul" : " · zone " + box[0] + " " + box[1] + " " + box[2] + " → " + box[3] + " " + box[4] + " " + box[5]))
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        })));
        return t;
    }

    private static GarageMotorBlockEntity motor(CommandContext<CommandSourceStack> c) {
        try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
            if (c.getSource().getLevel().getBlockEntity(pos) instanceof GarageMotorBlockEntity be) return be;
            fail(c, "Pas de moteur de porte de garage en " + pos.toShortString() + ".");
        } catch (Exception e) {
            fail(c, "Position non chargée.");
        }
        return null;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
