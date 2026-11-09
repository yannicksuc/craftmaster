package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.network.Network;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The plugin's commands, same names, aliases and arguments. Brigadier is case-sensitive while Bukkit was not, so
 * every command is also registered in lower case (/craftmasteraddtask still works).
 * Admin commands need the craftmaster:admin permission (LuckPerms...) or operator level 2.
 */
public final class CraftMasterCommands {

    private CraftMasterCommands() {}

    private static final Predicate<CommandSourceStack> ADMIN =
            PermissionPredicates.require(CraftMaster.id("admin"), PermissionLevel.GAMEMASTERS);

    private interface Tree {
        LiteralArgumentBuilder<CommandSourceStack> build(String name);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        add(d, CraftMasterCommands::addTask, "craftMasterAddTask", "cfat");
        add(d, CraftMasterCommands::removeTask, "craftMasterRemoveTask", "cfrt");
        add(d, CraftMasterCommands::renameTask, "craftMasterRenameTask", "cfrn");
        add(d, CraftMasterCommands::setScore, "craftMasterSetScore", "cfss");
        add(d, CraftMasterCommands::reloadScores, "craftMasterReloadScores", "cfrs");
        add(d, CraftMasterCommands::addBust, "craftMasterAddBust", "cfab");
        add(d, CraftMasterCommands::removeBust, "craftMasterRemoveBust", "cfdb");
        add(d, CraftMasterCommands::ranking, "craftMasterRanking", "cfrk");
        add(d, CraftMasterCommands::curtain, "craftMasterCurtain", "cfcu");
    }

    private static void add(CommandDispatcher<CommandSourceStack> d, Tree tree, String name, String alias) {
        Set<String> names = new LinkedHashSet<>(List.of(name, name.toLowerCase(Locale.ROOT), alias));
        for (String n : names) d.register(tree.build(n));
    }

    // ---------------------------------------------------------------- tasks

    private static LiteralArgumentBuilder<CommandSourceStack> addTask(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).executes(c -> {
                            int season = getInteger(c, "season");
                            String task = c.getArgument("task", String.class);
                            if (data().addTask(season, task)) ok(c, "Tâche '" + task + "' ajoutée à la saison " + season + ".");
                            else fail(c, "La tâche '" + task + "' existe déjà dans la saison " + season + ".");
                            Network.broadcast(c.getSource().getServer(), season);
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeTask(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).suggests(TASKS).executes(c -> {
                            int season = getInteger(c, "season");
                            String task = c.getArgument("task", String.class);
                            if (data().removeTask(season, task)) ok(c, "Tâche '" + task + "' supprimée de la saison " + season + ".");
                            else fail(c, "Aucune tâche '" + task + "' dans la saison " + season + ".");
                            Network.broadcast(c.getSource().getServer(), season);
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> renameTask(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("old", word()).suggests(TASKS)
                                .then(argument("new", word()).executes(c -> {
                                    int season = getInteger(c, "season");
                                    String oldName = c.getArgument("old", String.class);
                                    String newName = c.getArgument("new", String.class);
                                    if (data().renameTask(season, oldName, newName))
                                        ok(c, "Tâche renommée: " + oldName + " -> " + newName + " (saison " + season + ")");
                                    else fail(c, "Aucune tâche '" + oldName + "' dans la saison " + season + ".");
                                    Network.broadcast(c.getSource().getServer(), season);
                                    return 1;
                                }))));
    }

    // ---------------------------------------------------------------- scores

    private static LiteralArgumentBuilder<CommandSourceStack> setScore(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).suggests(TASKS)
                                .then(argument("pseudo", word()).suggests(PLAYERS)
                                        .then(argument("score", integer()).executes(c -> {
                                            int season = getInteger(c, "season");
                                            String task = c.getArgument("task", String.class);
                                            String pseudo = c.getArgument("pseudo", String.class);
                                            int score = getInteger(c, "score");
                                            data().setScore(season, task, pseudo, score);
                                            Busts.updateAll(c.getSource().getServer());
                                            Network.broadcast(c.getSource().getServer(), season);
                                            ok(c, "Score défini: " + pseudo + " -> " + score + " points (saison " + season + ", tache " + task + ")");
                                            if (!data().tasks(season).contains(task))
                                                c.getSource().sendSystemMessage(Component.literal("Attention : '" + task
                                                        + "' n'est pas une tâche de la saison " + season + " (le score compte quand même).").withStyle(ChatFormatting.YELLOW));
                                            return 1;
                                        })))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadScores(String name) {
        return literal(name).requires(ADMIN).executes(c -> {
            data().load(null);
            Busts.updateAll(c.getSource().getServer());
            for (int s : data().allSeasons()) Network.broadcast(c.getSource().getServer(), s);
            ok(c, "Scores rechargés et têtes / displays mis à jour.");
            return 1;
        });
    }

    // ---------------------------------------------------------------- busts

    private static LiteralArgumentBuilder<CommandSourceStack> addBust(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("rank", integer(1)).suggests(RANKS).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            if (p == null) {
                                fail(c, "Cette commande est réservée aux joueurs.");
                                return 0;
                            }
                            int season = getInteger(c, "season");
                            int rank = getInteger(c, "rank");
                            HitResult hit = p.pick(8, 1f, false);
                            if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
                                fail(c, "Aucun bloc ciblé dans la portée (8 blocs).");
                                return 0;
                            }
                            BlockPos pos = bh.getBlockPos();
                            BlockState state = p.level().getBlockState(pos);
                            if (!Busts.isHead(state)) {
                                fail(c, "Veuillez regarder un Player Head pour enregistrer un bust.");
                                return 0;
                            }
                            CraftMasterData.BustPos old = data().bust(season, rank);
                            if (old != null) Busts.removeLabel(c.getSource().getServer(), old, season, rank);
                            data().setBust(season, rank, new CraftMasterData.BustPos(Busts.worldId(p.level()), pos.getX(), pos.getY(), pos.getZ()));
                            Busts.update(c.getSource().getServer(), season, rank);
                            ok(c, "Bust enregistré: saison " + season + " rang " + rank + " -> emplacement: "
                                    + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " (monde: " + Busts.worldId(p.level()) + ")");
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeBust(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(BUST_SEASONS)
                        .then(argument("rank", integer(1)).suggests(BUST_RANKS).executes(c -> {
                            int season = getInteger(c, "season");
                            int rank = getInteger(c, "rank");
                            CraftMasterData.BustPos old = data().bust(season, rank);
                            if (old == null || !data().removeBust(season, rank)) {
                                fail(c, "Aucun bust pour la saison " + season + " rang " + rank + ".");
                                return 0;
                            }
                            Busts.removeLabel(c.getSource().getServer(), old, season, rank);
                            ok(c, "Bust supprimé: saison " + season + " rang " + rank + " (la tête reste en place).");
                            return 1;
                        })));
    }

    // ---------------------------------------------------------------- ranking

    /** Opens the ranking screen for players who have the mod, prints it in the chat otherwise (and for the console). */
    private static LiteralArgumentBuilder<CommandSourceStack> ranking(String name) {
        return literal(name)
                .executes(c -> showRanking(c, Network.latestSeason()))
                .then(argument("season", integer()).suggests(ALL_SEASONS).executes(c -> showRanking(c, getInteger(c, "season"))))
                .then(literal("chat").then(argument("season", integer()).suggests(ALL_SEASONS)
                        .executes(c -> printRanking(c, getInteger(c, "season")))));
    }

    private static int showRanking(CommandContext<CommandSourceStack> c, int season) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p != null && Network.sendRanking(p, season, true)) return 1;
        return printRanking(c, season);
    }

    private static int printRanking(CommandContext<CommandSourceStack> c, int season) {
        List<CraftMasterData.Entry> r = data().ranking(season);
        c.getSource().sendSystemMessage(Component.literal("— Classement saison " + season + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (r.isEmpty()) c.getSource().sendSystemMessage(Component.literal("Aucun score pour l'instant.").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < r.size(); i++) {
            CraftMasterData.Entry e = r.get(i);
            MutableComponent line = Component.literal("#" + (i + 1) + " ").withStyle(i == 0 ? ChatFormatting.GOLD : i == 1 ? ChatFormatting.GRAY : i == 2 ? ChatFormatting.RED : ChatFormatting.WHITE)
                    .append(Component.literal(e.player()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + e.total() + " pts").withStyle(ChatFormatting.YELLOW));
            c.getSource().sendSystemMessage(line);
        }
        return r.size();
    }

    // ---------------------------------------------------------------- curtains

    private static LiteralArgumentBuilder<CommandSourceStack> curtain(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal(name).requires(ADMIN);
        for (String action : List.of("open", "close", "toggle")) {
            root.then(literal(action).then(argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                Boolean open = Curtains.animate(c.getSource().getLevel(), pos,
                        action.equals("toggle") ? null : action.equals("open"));
                if (open == null) {
                    fail(c, "Pas de rideau en " + pos.toShortString() + ".");
                    return 0;
                }
                ok(c, open ? "Ouverture du rideau." : "Fermeture du rideau.");
                return 1;
            })));
        }
        return root;
    }

    // ---------------------------------------------------------------- helpers

    private static CraftMasterData data() {
        return CraftMaster.data();
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        Supplier<Component> s = () -> Component.literal(msg).withStyle(ChatFormatting.GREEN);
        c.getSource().sendSuccess(s, true);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
    }

    private static SuggestionProvider<CommandSourceStack> list(java.util.function.Function<CommandContext<CommandSourceStack>, Collection<String>> values) {
        return (c, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT).replace("\"", "");
            for (String v : values.apply(c))
                if (v.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(WordArgument.escape(v));
            return b.buildFuture();
        };
    }

    private static int seasonArg(CommandContext<CommandSourceStack> c) {
        try { return IntegerArgumentType.getInteger(c, "season"); } catch (IllegalArgumentException e) { return 0; }
    }

    private static final SuggestionProvider<CommandSourceStack> SEASONS = list(c -> data().seasons());
    private static final SuggestionProvider<CommandSourceStack> ALL_SEASONS = list(c -> data().allSeasons().stream().map(String::valueOf).toList());
    private static final SuggestionProvider<CommandSourceStack> BUST_SEASONS = list(c -> data().bustSeasons().stream().map(String::valueOf).toList());
    private static final SuggestionProvider<CommandSourceStack> TASKS = list(c -> data().tasks(seasonArg(c)));
    private static final SuggestionProvider<CommandSourceStack> BUST_RANKS = list(c -> data().bustRanks(seasonArg(c)).stream().map(String::valueOf).toList());
    private static final SuggestionProvider<CommandSourceStack> RANKS = list(c -> List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = list(c -> {
        Set<String> names = new LinkedHashSet<>(c.getSource().getOnlinePlayerNames());
        names.addAll(data().players(seasonArg(c)));
        return names;
    });
}
