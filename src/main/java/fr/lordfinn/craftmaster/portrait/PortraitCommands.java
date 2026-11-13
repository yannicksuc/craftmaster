package fr.lordfinn.craftmaster.portrait;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.command.WordArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /craftMasterPortrait (alias /cfpt): placing and setting up hanging portraits without the screens (handy for
 * tests and for the console), plus the pose list, a manual publish and highlight. Reference: docs/wiki/Tableaux.md.
 * Without a target, a command acts on the portrait the player is looking at.
 */
public final class PortraitCommands {

    private PortraitCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("craftMasterPortrait", "craftmasterportrait", "cfpt")) d.register(tree(name));
    }

    private static final SuggestionProvider<CommandSourceStack> POSES = (c, b) ->
            SharedSuggestionProvider.suggest(PoseLibraries.server().names().stream().map(WordArgument::escape), b);
    private static final SuggestionProvider<CommandSourceStack> FACINGS = (c, b) ->
            SharedSuggestionProvider.suggest(List.of("north", "south", "east", "west"), b);

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name).requires(Permissions.ADMIN);

        root.then(literal("place").then(argument("season", integer()).then(argument("rank", integer(1))
                .executes(c -> placeLooking(c))
                .then(argument("ceiling", BlockPosArgument.blockPos()).then(argument("facing", StringArgumentType.word()).suggests(FACINGS)
                        .executes(PortraitCommands::placeAt))))));

        LiteralArgumentBuilder<CommandSourceStack> set = literal("set");
        RequiredArgumentBuilder<CommandSourceStack, ?> targets = argument("targets", EntityArgument.entities());
        addSettings(set, false);
        addSettings(targets, true);
        root.then(set.then(targets));

        root.then(literal("edit").executes(c -> open(c, false, false)).then(argument("targets", EntityArgument.entities()).executes(c -> open(c, true, false))));
        root.then(literal("poseEditor").executes(c -> open(c, false, true)).then(argument("targets", EntityArgument.entities()).executes(c -> open(c, true, true))));
        root.then(literal("remove").executes(c -> remove(c, false)).then(argument("targets", EntityArgument.entities()).executes(c -> remove(c, true))));
        root.then(literal("poses").executes(PortraitCommands::listPoses));
        root.then(literal("publish").then(argument("season", integer()).executes(c -> {
            Portraits.onPublished(c.getSource().getServer(), getInteger(c, "season"));
            ok(c, "Tableaux de la saison " + getInteger(c, "season") + " réarrangés.");
            return 1;
        })));
        // Raise / lower a whole gallery (stage scenery): /cfpt hoist <season> <blocks|auto> [seconds]
        root.then(literal("hoist").then(argument("season", integer()).then(argument("length", StringArgumentType.word()).suggests(LENGTHS)
                .executes(c -> hoistGallery(c, 0))
                .then(argument("seconds", DoubleArgumentType.doubleArg(0.5, 120))
                        .executes(c -> hoistGallery(c, DoubleArgumentType.getDouble(c, "seconds")))))));
        root.then(literal("highlight").then(argument("season", integer()).then(argument("rank", integer(1)).executes(c -> {
            Portraits.highlight(c.getSource().getServer(), getInteger(c, "season"), getInteger(c, "rank"));
            ok(c, "Tableau du rang " + getInteger(c, "rank") + " illuminé.");
            return 1;
        }))));
        return root;
    }

    private static final SuggestionProvider<CommandSourceStack> LENGTHS =
            (c, b) -> SharedSuggestionProvider.suggest(List.of("auto", "0.5", "1", "2", "4", "8"), b);

    /** "auto" gives -1, else a length in blocks; NaN (with a message) when invalid. */
    private static float length(CommandContext<CommandSourceStack> c, String v) {
        if (v.equalsIgnoreCase("auto")) return -1;
        float h;
        try {
            h = Float.parseFloat(v.replace(',', '.'));
        } catch (NumberFormatException e) {
            fail(c, "Longueur de ficelle : « auto » ou un nombre de blocs.");
            return Float.NaN;
        }
        if (!(h >= 0 && h <= PortraitEntity.MAX_HANG)) {
            fail(c, "Longueur de ficelle entre 0 et " + (int) PortraitEntity.MAX_HANG + " blocs.");
            return Float.NaN;
        }
        return h;
    }

    private static String lengthText(float h) {
        return h < 0 ? "la ficelle automatique" : "une ficelle de " + (h == Math.rint(h) ? String.valueOf((int) h) : String.valueOf(h).replace('.', ',')) + " bloc(s)";
    }

    private static String seconds(int ticks) {
        return String.valueOf(Math.round(ticks / 2.0) / 10.0).replace('.', ',') + " s";
    }

    private static int hoistGallery(CommandContext<CommandSourceStack> c, double seconds) {
        float h = length(c, StringArgumentType.getString(c, "length"));
        if (Float.isNaN(h)) return 0;
        int season = getInteger(c, "season"), n = 0, longest = 0;
        for (ServerLevel level : c.getSource().getServer().getAllLevels())
            for (PortraitEntity p : Portraits.gallery(level, season)) {
                longest = Math.max(longest, hoist(p, h, seconds));
                n++;
            }
        if (n == 0) {
            fail(c, "Aucun tableau dans la galerie de la saison " + season + ".");
            return 0;
        }
        ok(c, n + " tableau(x) de la saison " + season + " : en route vers " + lengthText(h) + " (" + seconds(longest) + ").");
        return n;
    }

    private static int hoistSet(CommandContext<CommandSourceStack> c, boolean targeted, double seconds) throws CommandSyntaxException {
        float h = length(c, StringArgumentType.getString(c, "value"));
        if (Float.isNaN(h)) return 0;
        List<PortraitEntity> list = targets(c, targeted);
        int longest = 0;
        for (PortraitEntity p : list) longest = Math.max(longest, hoist(p, h, seconds));
        if (!list.isEmpty()) ok(c, list.size() + " tableau(x) : en route vers " + lengthText(h) + " (" + seconds(longest) + ").");
        return list.size();
    }

    /** Starts a hoist, with the winch sounds at its start and its end. */
    public static int hoist(PortraitEntity p, float blocks, double seconds) {
        int ticks = p.hoist(blocks, (int) Math.round(seconds * 20));
        if (p.level() instanceof ServerLevel level) {
            Vec3 at = p.position();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.6f, 0.8f);
            fr.lordfinn.craftmaster.task.Tasks.later(level.getServer(), ticks, () -> {
                if (!p.isRemoved()) level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
            });
        }
        return ticks;
    }

    /** set [targets] season|rank|player|name|skin|pose|window|windowDepth|windowSize|height|hoist|angle value */
    private static void addSettings(com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> node, boolean targeted) {
        node.then(literal("season").then(argument("value", integer()).executes(c ->
                apply(c, targeted, p -> p.setSeason(getInteger(c, "value")), "saison " + getInteger(c, "value")))));
        node.then(literal("rank").then(argument("value", integer(1)).executes(c ->
                apply(c, targeted, p -> p.setRank(getInteger(c, "value")), "suit le rang " + getInteger(c, "value")))));
        node.then(literal("player").then(argument("value", WordArgument.word()).executes(c -> {
            String player = c.getArgument("value", String.class);
            return apply(c, targeted, p -> p.setFixedPlayer(player), "suit " + player);
        })));
        node.then(literal("name").then(argument("value", StringArgumentType.greedyString()).executes(c -> {
            String text = StringArgumentType.getString(c, "value");
            boolean reset = text.equals("-");
            return apply(c, targeted, p -> p.setDisplayName(reset ? "" : text), reset ? "nom du joueur affiché" : "nom affiché « " + text + " »");
        })));
        node.then(literal("skin").then(argument("value", StringArgumentType.greedyString()).executes(c -> {
            String url = StringArgumentType.getString(c, "value").trim();
            boolean reset = url.equals("-");
            if (!reset && !PortraitNetwork.validSkinUrl(url)) {
                fail(c, "Adresse invalide (http:// ou https:// attendu).");
                return 0;
            }
            return apply(c, targeted, p -> p.setSkinUrl(reset ? "" : url), reset ? "skin du joueur" : "skin " + url);
        })));
        node.then(literal("pose").then(argument("value", StringArgumentType.greedyString()).suggests(POSES).executes(c -> {
            String pose = Pose.normalizeName(StringArgumentType.getString(c, "value").replace("\"", ""));
            if (!PoseLibraries.server().has(pose)) {
                fail(c, "Pose inconnue : " + pose + " (/cfpt poses).");
                return 0;
            }
            return apply(c, targeted, p -> p.setPoseName(pose), "pose " + pose);
        })));
        node.then(literal("window").then(argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
            boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "value");
            return apply(c, targeted, p -> p.setWindow(on), on ? "effet fenêtre (Antichamber) activé" : "effet fenêtre désactivé");
        })));
        node.then(literal("windowDepth").then(argument("value", integer(PortraitEntity.MIN_WINDOW_DEPTH, PortraitEntity.MAX_WINDOW_DEPTH)).executes(c ->
                apply(c, targeted, p -> p.setWindowDepth(getInteger(c, "value")), "profondeur de la fenêtre " + getInteger(c, "value") + " px"))));
        node.then(literal("windowSize").then(argument("value", integer(PortraitEntity.MIN_WINDOW_SIZE, PortraitEntity.MAX_WINDOW_SIZE)).executes(c ->
                apply(c, targeted, p -> p.setWindowSize(getInteger(c, "value")), "taille de la pièce " + getInteger(c, "value") + " %"))));
        node.then(literal("height").then(argument("value", StringArgumentType.word()).suggests(LENGTHS)
                .executes(c -> {
                    float hang = length(c, StringArgumentType.getString(c, "value"));
                    if (Float.isNaN(hang)) return 0;
                    return apply(c, targeted, p -> p.setHang(hang), hang < 0 ? "ficelle automatique" : "ficelle de " + hang + " bloc(s)");
                })));
        // Exact angle: "angle 20" turns the picture 20° from its wall direction; "angle moi" faces whoever types it
        node.then(literal("angle").then(argument("value", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("0", "15", "-15", "30", "-30", "45", "-45", "moi"), b))
                .executes(c -> {
                    String v = StringArgumentType.getString(c, "value");
                    if (v.equalsIgnoreCase("moi") || v.equalsIgnoreCase("me")) {
                        ServerPlayer me = c.getSource().getPlayer();
                        if (me == null) {
                            fail(c, "« angle moi » se tape en jeu.");
                            return 0;
                        }
                        float look = me.getYRot();
                        return apply(c, targeted, p -> p.faceLook(look), "tourné vers toi");
                    }
                    float deg;
                    try {
                        deg = Float.parseFloat(v.replace(',', '.').replace("°", ""));
                    } catch (NumberFormatException e) {
                        fail(c, "Angle : un nombre de degrés (−180 à 180) ou « moi ».");
                        return 0;
                    }
                    return apply(c, targeted, p -> p.setTurn(deg), "tourné de " + Math.round(net.minecraft.util.Mth.wrapDegrees(deg)) + "° par rapport à son mur");
                })));
        // The same, raised or lowered smoothly: hoist <length> [seconds]
        node.then(literal("hoist").then(argument("value", StringArgumentType.word()).suggests(LENGTHS)
                .executes(c -> hoistSet(c, targeted, 0))
                .then(argument("seconds", DoubleArgumentType.doubleArg(0.5, 120))
                        .executes(c -> hoistSet(c, targeted, DoubleArgumentType.getDouble(c, "seconds"))))));
    }

    // ---------------------------------------------------------------- targets

    /** The portrait the player looks at (10 blocks), or the given targets. */
    private static List<PortraitEntity> targets(CommandContext<CommandSourceStack> c, boolean targeted) throws CommandSyntaxException {
        List<PortraitEntity> out = new ArrayList<>();
        if (targeted) {
            Collection<? extends Entity> found = EntityArgument.getEntities(c, "targets");
            for (Entity e : found) if (e instanceof PortraitEntity p) out.add(p);
            if (out.isEmpty()) fail(c, "Aucun tableau parmi les entités visées.");
            return out;
        }
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) {
            fail(c, "Précisez les tableaux visés (ex. @e[type=craftmaster:portrait,sort=nearest,limit=1]).");
            return out;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(10));
        PortraitEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PortraitEntity p : player.level().getEntitiesOfClass(PortraitEntity.class, new AABB(eye, end).inflate(3))) {
            Optional<Vec3> hit = p.getBoundingBox().clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestDist) {
                bestDist = hit.get().distanceToSqr(eye);
                best = p;
            }
        }
        if (best == null) fail(c, "Regardez un tableau (10 blocs max).");
        else out.add(best);
        return out;
    }

    private static int apply(CommandContext<CommandSourceStack> c, boolean targeted, java.util.function.Consumer<PortraitEntity> change, String what) throws CommandSyntaxException {
        List<PortraitEntity> list = targets(c, targeted);
        for (PortraitEntity p : list) {
            change.accept(p);
            Portraits.refresh(p);
        }
        if (!list.isEmpty()) ok(c, list.size() + " tableau(x) : " + what + ".");
        return list.size();
    }

    // ---------------------------------------------------------------- actions

    private static int placeLooking(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) {
            fail(c, "Depuis la console : /cfpt place <saison> <rang> <bloc du plafond> <north|south|east|west>.");
            return 0;
        }
        HitResult hit = player.pick(8, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            fail(c, "Regardez le bloc du plafond (8 blocs max).");
            return 0;
        }
        int n = place(c, player.level(), bh.getBlockPos(), player.getDirection().getOpposite());
        // Facing the one who places it, exactly
        for (PortraitEntity p : player.level().getEntitiesOfClass(PortraitEntity.class, new AABB(bh.getBlockPos()).inflate(0.5, 17, 0.5)))
            if (p.ceiling().equals(bh.getBlockPos()) && p.tickCount == 0) p.faceLook(player.getYRot());
        return n;
    }

    private static int placeAt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Direction facing = Direction.byName(StringArgumentType.getString(c, "facing").toLowerCase(Locale.ROOT));
        if (facing == null || facing.getAxis().isVertical()) {
            fail(c, "Orientation : north, south, east ou west.");
            return 0;
        }
        return place(c, c.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(c, "ceiling"), facing);
    }

    private static int place(CommandContext<CommandSourceStack> c, ServerLevel level, BlockPos ceiling, Direction facing) {
        PortraitEntity p = PortraitEntity.create(level, ceiling, facing, getInteger(c, "season"), getInteger(c, "rank"));
        Portraits.refresh(p);
        level.addFreshEntity(p);
        ok(c, "Tableau accroché sous " + ceiling.toShortString() + " (saison " + p.season() + ", rang " + p.rank() + ").");
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> c, boolean targeted, boolean poseEditor) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) {
            fail(c, "Commande réservée aux joueurs.");
            return 0;
        }
        List<PortraitEntity> list = targets(c, targeted);
        if (list.isEmpty()) return 0;
        return PortraitNetwork.openScreen(player, list.getFirst(), poseEditor) ? 1 : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> c, boolean targeted) throws CommandSyntaxException {
        List<PortraitEntity> list = targets(c, targeted);
        list.forEach(Entity::discard);
        if (!list.isEmpty()) ok(c, list.size() + " tableau(x) retiré(s).");
        return list.size();
    }

    private static int listPoses(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSystemMessage(Component.literal("Poses : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.join(", ", PoseLibraries.server().names())).withStyle(ChatFormatting.WHITE)));
        return PoseLibraries.server().names().size();
    }

    // ---------------------------------------------------------------- helpers

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
    }
}
