package fr.lordfinn.craftmaster.portrait;

import com.mojang.brigadier.CommandDispatcher;
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
        root.then(literal("highlight").then(argument("season", integer()).then(argument("rank", integer(1)).executes(c -> {
            Portraits.highlight(c.getSource().getServer(), getInteger(c, "season"), getInteger(c, "rank"));
            ok(c, "Tableau du rang " + getInteger(c, "rank") + " illuminé.");
            return 1;
        }))));
        return root;
    }

    /** set [targets] season|rank|player|name|skin|pose|window|height value */
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
        node.then(literal("height").then(argument("value", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("auto", "0.5", "1", "1.5"), b))
                .executes(c -> {
                    String v = StringArgumentType.getString(c, "value");
                    float h;
                    if (v.equalsIgnoreCase("auto")) h = -1;
                    else {
                        try {
                            h = Float.parseFloat(v.replace(',', '.'));
                        } catch (NumberFormatException e) {
                            fail(c, "Longueur de ficelle : « auto » ou un nombre de blocs.");
                            return 0;
                        }
                        if (!(h >= 0 && h <= PortraitEntity.MAX_HANG)) {
                            fail(c, "Longueur de ficelle entre 0 et " + (int) PortraitEntity.MAX_HANG + " blocs.");
                            return 0;
                        }
                    }
                    float hang = h;
                    return apply(c, targeted, p -> p.setHang(hang), hang < 0 ? "ficelle automatique" : "ficelle de " + hang + " bloc(s)");
                })));
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
        return place(c, player.level(), bh.getBlockPos(), player.getDirection().getOpposite());
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
