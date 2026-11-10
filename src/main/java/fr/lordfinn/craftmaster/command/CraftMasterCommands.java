package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.network.Network;
import fr.lordfinn.craftmaster.portrait.Portraits;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * All the commands: the plugin's ones (same names, aliases and arguments) plus the mod's (scores, ranking, busts,
 * curtains; the show's commands are in {@link ShowCommands}). Commands that change seasons, rounds or scores run
 * through {@link ActionExecutor}, like the voice control, so every change is undoable. Brigadier is case-sensitive while Bukkit was not, so every command is also registered in lower case
 * (/craftmasteraddtask still works). Admin commands need {@link Permissions#ADMIN}. Reference: docs/wiki/Commandes.md.
 */
public final class CraftMasterCommands {

    private CraftMasterCommands() {}

    private static final Predicate<CommandSourceStack> ADMIN = Permissions.ADMIN;

    interface Tree {
        LiteralArgumentBuilder<CommandSourceStack> build(String name);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        add(d, CraftMasterCommands::addTask, "craftMasterAddTask", "cfat");
        add(d, CraftMasterCommands::removeTask, "craftMasterRemoveTask", "cfrt");
        add(d, CraftMasterCommands::renameTask, "craftMasterRenameTask", "cfrn");
        add(d, CraftMasterCommands::setScore, "craftMasterSetScore", "cfss");
        add(d, CraftMasterCommands::addScore, "craftMasterAddScore", "cfas");
        add(d, CraftMasterCommands::removeScore, "craftMasterRemoveScore", "cfds");
        add(d, CraftMasterCommands::reloadScores, "craftMasterReloadScores", "cfrs");
        add(d, CraftMasterCommands::addBust, "craftMasterAddBust", "cfab");
        add(d, CraftMasterCommands::removeBust, "craftMasterRemoveBust", "cfdb");
        add(d, CraftMasterCommands::ranking, "craftMasterRanking", "cfrk");
        add(d, CraftMasterCommands::hideRanking, "craftMasterHideRanking", "cfhr");
        add(d, CraftMasterCommands::curtain, "craftMasterCurtain", "cfcu");
        ShowCommands.register(d);
        // Tasks, envelopes, hidden notes (chantier D)
        TaskCommands.register(d);
    }

    static void add(CommandDispatcher<CommandSourceStack> d, Tree tree, String name, String alias) {
        Set<String> names = new LinkedHashSet<>(List.of(name, name.toLowerCase(Locale.ROOT), alias));
        for (String n : names) d.register(tree.build(n));
    }

    // ---------------------------------------------------------------- tasks (= rounds of the active game)

    private static LiteralArgumentBuilder<CommandSourceStack> addTask(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).executes(c -> {
                            int season = getInteger(c, "season");
                            String task = c.getArgument("task", String.class);
                            Manche existing = data().manche(season, task);
                            if (existing != null && !existing.archived) {
                                fail(c, "La tâche '" + task + "' existe déjà dans la saison " + season + ".");
                                return 0;
                            }
                            // A task removed before comes back with its points
                            List<Map<String, Object>> actions = existing != null
                                    ? List.of(action("manche.archive", "season", season, "manche", existing.id, "archived", false))
                                    : List.of(action("manche.create", "season", season, "name", task));
                            return run(c, actions, "Tâche '" + task + "' ajoutée à la saison " + season + ".");
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeTask(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).suggests(TASKS).executes(c -> {
                            int season = getInteger(c, "season");
                            String task = c.getArgument("task", String.class);
                            Manche existing = data().manche(season, task);
                            if (existing == null || existing.archived) {
                                fail(c, "Aucune tâche '" + task + "' dans la saison " + season + ".");
                                return 0;
                            }
                            // Archived: no longer listed, its points still count (as in the plugin)
                            return run(c, List.of(action("manche.archive", "season", season, "manche", existing.id, "archived", true)),
                                    "Tâche '" + task + "' supprimée de la saison " + season + ".");
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
                                    Manche existing = data().manche(season, oldName);
                                    if (existing == null) {
                                        fail(c, "Aucune tâche '" + oldName + "' dans la saison " + season + ".");
                                        return 0;
                                    }
                                    return run(c, List.of(action("manche.update", "season", season, "manche", existing.id, "name", newName, "merge", true)),
                                            "Tâche renommée: " + oldName + " -> " + newName + " (saison " + season + ")");
                                }))));
    }

    // ---------------------------------------------------------------- scores

    /**
     * The actions that make sure the round exists before scoring it: a score on a task that is not listed is
     * accepted (as in the plugin), the round is created archived.
     */
    private static List<Map<String, Object>> withRound(int season, String task, Map<String, Object> scoreAction) {
        if (data().manche(season, task) != null) return List.of(scoreAction);
        return List.of(action("manche.create", "season", season, "name", task),
                action("manche.archive", "season", season, "manche", task, "archived", true), scoreAction);
    }

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
                                            boolean known = data().tasks(season).contains(task);
                                            int r = run(c, withRound(season, task, action("score.set", "season", season, "manche", task, "player", pseudo, "value", score)),
                                                    "Score défini: " + pseudo + " -> " + score + " points (saison " + season + ", tache " + task + ")");
                                            if (r > 0 && !known) warnUnknownTask(c, season, task);
                                            return r;
                                        })))));
    }

    /** Adds points (or takes them away with a negative number) without retyping the whole score. */
    private static LiteralArgumentBuilder<CommandSourceStack> addScore(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).suggests(TASKS)
                                .then(argument("pseudo", word()).suggests(PLAYERS)
                                        .then(argument("points", integer()).executes(c -> {
                                            int season = getInteger(c, "season");
                                            String task = c.getArgument("task", String.class);
                                            String pseudo = c.getArgument("pseudo", String.class);
                                            int points = getInteger(c, "points");
                                            boolean known = data().tasks(season).contains(task);
                                            int score = data().score(season, task, pseudo) + points;
                                            int r = run(c, withRound(season, task, action("score.add", "season", season, "manche", task, "player", pseudo, "points", points)),
                                                    (points >= 0 ? "+" : "") + points + " pour " + pseudo + " -> " + score
                                                            + " points (saison " + season + ", tache " + task + ")");
                                            if (r > 0 && !known) warnUnknownTask(c, season, task);
                                            return r;
                                        })))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeScore(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(SEASONS)
                        .then(argument("task", word()).suggests(TASKS)
                                .then(argument("pseudo", word()).suggests(SCORED_PLAYERS).executes(c -> {
                                    int season = getInteger(c, "season");
                                    String task = c.getArgument("task", String.class);
                                    String pseudo = c.getArgument("pseudo", String.class);
                                    if (data().manche(season, task) == null || data().season(season) == null) {
                                        fail(c, pseudo + " n'a pas de score pour '" + task + "' (saison " + season + ").");
                                        return 0;
                                    }
                                    return run(c, List.of(action("score.remove", "season", season, "manche", task, "player", pseudo)),
                                            "Score retiré: " + pseudo + " (saison " + season + ", tache " + task + ")");
                                }))));
    }

    private static void warnUnknownTask(CommandContext<CommandSourceStack> c, int season, String task) {
        c.getSource().sendSystemMessage(Component.literal("Attention : '" + task + "' n'est pas une tâche de la saison "
                + season + " (le score compte quand même).").withStyle(ChatFormatting.YELLOW));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadScores(String name) {
        return literal(name).requires(ADMIN).executes(c -> {
            data().load(null);
            ActionExecutor.refresh(c.getSource().getServer());
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

    /**
     * Opens the ranking screen for players who have the mod, prints it in the chat otherwise (and for the console).
     * Players who are not admins cannot see hidden seasons.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> ranking(String name) {
        return literal(name)
                .executes(c -> showRanking(c, -1))
                .then(argument("season", integer()).suggests(VISIBLE_SEASONS).executes(c -> showRanking(c, getInteger(c, "season"))))
                .then(literal("chat")
                        .executes(c -> printRanking(c, -1))
                        .then(argument("season", integer()).suggests(VISIBLE_SEASONS).executes(c -> printRanking(c, getInteger(c, "season")))));
    }

    private static int showRanking(CommandContext<CommandSourceStack> c, int season) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p != null && Network.sendRanking(p, season, true)) return 1;
        return printRanking(c, season);
    }

    /** Players get the published ranking; organisers the live one, with the points not published yet. */
    private static int printRanking(CommandContext<CommandSourceStack> c, int season) {
        boolean admin = ADMIN.test(c.getSource());
        if (season < 0) season = Network.latestSeason(admin);
        if (season < 0 || (!admin && data().isHidden(season))) {
            fail(c, "Aucun classement disponible pour l'instant.");
            return 0;
        }
        List<CraftMasterData.Entry> r = data().ranking(season, !admin);
        String title = "— Classement saison " + season + (data().isHidden(season) ? " (caché aux joueurs)" : "")
                + (admin ? " (en direct)" : "") + " —";
        c.getSource().sendSystemMessage(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (r.isEmpty()) c.getSource().sendSystemMessage(Component.literal("Aucun score pour l'instant.").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < r.size(); i++) {
            CraftMasterData.Entry e = r.get(i);
            MutableComponent line = Component.literal("#" + (i + 1) + " ").withStyle(Busts.rankColor(i + 1))
                    .append(Component.literal(e.player()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + e.total() + " pts").withStyle(ChatFormatting.YELLOW));
            int unpublished = admin ? e.total() - data().publishedTotal(season, e.player()) : 0;
            if (unpublished != 0)
                line.append(Component.literal("  (" + (unpublished > 0 ? "+" : "") + unpublished + " non publié)").withStyle(ChatFormatting.GOLD));
            c.getSource().sendSystemMessage(line);
        }
        return r.size();
    }

    /** Hides a season's ranking until the reveal: players see nothing, busts stay anonymous. */
    private static LiteralArgumentBuilder<CommandSourceStack> hideRanking(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("season", integer()).suggests(ALL_SEASONS)
                        .then(argument("hidden", BoolArgumentType.bool()).executes(c -> {
                            int season = getInteger(c, "season");
                            boolean hidden = BoolArgumentType.getBool(c, "hidden");
                            MinecraftServer server = c.getSource().getServer();
                            if (hidden)
                                return run(c, List.of(action("season.update", "season", season, "hidden", true)),
                                        "Classement de la saison " + season + " caché aux joueurs.");
                            // Revealing shows the current scores: they are published at the same time
                            int r = run(c, List.of(action("season.update", "season", season, "hidden", false),
                                    action("scores.publish", "season", season)), "Classement de la saison " + season + " révélé.");
                            // The reveal is a moment of the show: everyone is told
                            if (r > 0) server.getPlayerList().broadcastSystemMessage(Component.literal("Le classement de la saison "
                                    + season + " est révélé ! (touche K)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                            return r;
                        })));
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

    static void ok(CommandContext<CommandSourceStack> c, String msg) {
        Supplier<Component> s = () -> Component.literal(msg).withStyle(ChatFormatting.GREEN);
        c.getSource().sendSuccess(s, true);
    }

    static void fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
    }

    /** An action of the show schema: its type then key, value pairs. */
    static Map<String, Object> action(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i + 1 < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    /**
     * Runs the actions through {@link ActionExecutor} (so they are undoable like the voice control's) and answers
     * with the plugin-era message when they worked, with the reason otherwise.
     */
    private static int run(CommandContext<CommandSourceStack> c, List<Map<String, Object>> actions, String okMessage) {
        ActionExecutor.Result r = ActionExecutor.execute(c.getSource().getServer(), actions);
        if (!r.ok()) {
            r.lines().forEach(l -> fail(c, l));
            return 0;
        }
        ok(c, okMessage);
        return 1;
    }

    static SuggestionProvider<CommandSourceStack> list(java.util.function.Function<CommandContext<CommandSourceStack>, Collection<String>> values) {
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
    private static final SuggestionProvider<CommandSourceStack> VISIBLE_SEASONS = list(c ->
            Network.visibleSeasons(ADMIN.test(c.getSource())).stream().map(String::valueOf).toList());
    private static final SuggestionProvider<CommandSourceStack> SCORED_PLAYERS = list(c -> data().players(seasonArg(c)));
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
