package fr.lordfinn.craftmaster.command;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.data.Stage;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.show.ShowContext;
import fr.lordfinn.craftmaster.show.StageControl;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.action;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.fail;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.list;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.ok;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The show's commands (v3): seasons, games, rounds, cast, live scores, publishing, reveal, timer, stage set-up and
 * undo. They act on the active season and game unless told otherwise, and everything that has an action in the
 * show schema runs through {@link ActionExecutor} (same behaviour and undo as the voice control).
 * Reference: docs/wiki/Commandes.md.
 */
public final class ShowCommands {

    private ShowCommands() {}

    private static final Predicate<CommandSourceStack> ADMIN = Permissions.ADMIN;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, ShowCommands::season, "craftMasterSeason", "cfse");
        CraftMasterCommands.add(d, ShowCommands::partie, "craftMasterPartie", "cfpa");
        CraftMasterCommands.add(d, ShowCommands::manche, "craftMasterManche", "cfma");
        CraftMasterCommands.add(d, ShowCommands::player, "craftMasterPlayer", "cfpl");
        CraftMasterCommands.add(d, ShowCommands::give, "craftMasterGive", "cfg");
        CraftMasterCommands.add(d, ShowCommands::publish, "craftMasterPublish", "cfpu");
        CraftMasterCommands.add(d, ShowCommands::reveal, "craftMasterReveal", "cfrv");
        CraftMasterCommands.add(d, ShowCommands::timer, "craftMasterTimer", "cfti");
        CraftMasterCommands.add(d, ShowCommands::stage, "craftMasterStage", "cfsg");
        CraftMasterCommands.add(d, ShowCommands::undo, "craftMasterUndo", "cfun");
        CraftMasterCommands.add(d, ShowCommands::history, "craftMasterHistory", "cfhi");
        CraftMasterCommands.add(d, ShowCommands::status, "craftMasterStatus", "cfst");
    }

    // ---------------------------------------------------------------- helpers

    private static CraftMasterData data() {
        return CraftMaster.data();
    }

    @SafeVarargs
    private static int run(CommandContext<CommandSourceStack> c, Map<String, Object>... actions) {
        return ActionExecutor.execute(c.getSource(), List.of(actions));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return c.getArgument(name, String.class);
    }

    private static void info(CommandContext<CommandSourceStack> c, Component line) {
        c.getSource().sendSystemMessage(line);
    }

    private static void info(CommandContext<CommandSourceStack> c, String line) {
        info(c, Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    private static void header(CommandContext<CommandSourceStack> c, String title) {
        info(c, Component.literal("— " + title + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** "a, b c" → [a, b, c]: nicknames typed with commas or spaces. */
    private static List<String> words(String s) {
        return Arrays.stream(s.split("[,\\s]+")).filter(w -> !w.isBlank()).toList();
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) fail(c, "Cette commande est réservée aux joueurs.");
        return p;
    }

    /** The block the player looks at (8 blocks), or null with a message. */
    private static BlockPos target(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return null;
        HitResult hit = p.pick(8, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            fail(c, "Aucun bloc ciblé dans la portée (8 blocs).");
            return null;
        }
        return bh.getBlockPos();
    }

    private static BlockSpot spot(CommandContext<CommandSourceStack> c, BlockPos pos) {
        return new BlockSpot(Busts.worldId(c.getSource().getLevel()), pos.getX(), pos.getY(), pos.getZ());
    }

    private static Season activeSeason() {
        return data().activeSeason();
    }

    private static Partie activePartie() {
        Season s = activeSeason();
        return s == null ? null : s.activePartie();
    }

    private static final SuggestionProvider<CommandSourceStack> SEASON_IDS = list(c ->
            data().seasonMap().keySet().stream().map(String::valueOf).toList());
    private static final SuggestionProvider<CommandSourceStack> PARTIES = list(c -> {
        Season s = activeSeason();
        return s == null ? List.of() : s.parties.values().stream().map(p -> p.name).toList();
    });
    private static final SuggestionProvider<CommandSourceStack> MANCHES = list(c -> {
        Partie p = activePartie();
        return p == null ? List.of() : p.manches.stream().map(r -> r.name).toList();
    });
    private static final SuggestionProvider<CommandSourceStack> MANCHES_OR_NEXT = list(c -> {
        List<String> out = new ArrayList<>(List.of("next"));
        Partie p = activePartie();
        if (p != null) p.activeManches().forEach(r -> out.add(r.name));
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> CAST = list(c -> {
        Season s = activeSeason();
        List<String> out = new ArrayList<>(c.getSource().getOnlinePlayerNames());
        if (s != null) for (String n : s.players.keySet()) if (!out.contains(n)) out.add(n);
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> STATUSES = list(c -> Partie.STATUSES);
    private static final SuggestionProvider<CommandSourceStack> SCENES = list(c -> data().stage().scenes.keySet());
    private static final SuggestionProvider<CommandSourceStack> SCENES_OR_NONE = list(c -> {
        List<String> out = new ArrayList<>(data().stage().scenes.keySet());
        out.add("aucun");
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> CURTAINS = list(c -> {
        List<String> out = new ArrayList<>(data().stage().curtains.keySet());
        if (!out.contains(Stage.MAIN_CURTAIN)) out.addFirst(Stage.MAIN_CURTAIN);
        return out;
    });

    // ---------------------------------------------------------------- seasons

    private static LiteralArgumentBuilder<CommandSourceStack> season(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("list").executes(ShowCommands::listSeasons))
                .then(literal("create")
                        .executes(c -> run(c, action("season.create")))
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> run(c, action("season.create", "name", str(c, "name"))))))
                .then(literal("rename").then(argument("season", integer(1)).suggests(SEASON_IDS)
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("season.update", "season", getInteger(c, "season"), "name", str(c, "name")))))))
                .then(literal("archive").then(argument("season", integer(1)).suggests(SEASON_IDS)
                        .then(argument("archived", BoolArgumentType.bool())
                                .executes(c -> run(c, action("season.archive", "season", getInteger(c, "season"), "archived", BoolArgumentType.getBool(c, "archived")))))))
                .then(literal("delete").then(argument("season", integer(1)).suggests(SEASON_IDS)
                        .executes(c -> run(c, action("season.delete", "season", getInteger(c, "season"))))))
                .then(literal("activate").then(argument("season", integer(1)).suggests(SEASON_IDS)
                        .executes(c -> run(c, action("season.activate", "season", getInteger(c, "season"))))));
    }

    private static int listSeasons(CommandContext<CommandSourceStack> c) {
        header(c, "Saisons");
        Season active = activeSeason();
        if (data().seasonMap().isEmpty()) info(c, "Aucune saison : /craftMasterSeason create <nom>");
        for (Season s : data().seasonMap().values()) {
            String flags = (s == active ? " ★ active" : "") + (s.hidden ? " · cachée" : "") + (s.archived ? " · archivée" : "")
                    + (s.published == null ? " · jamais publiée" : "");
            info(c, Component.literal(s.id + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(s.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + s.parties.size() + " partie(s), " + s.players.size() + " joueur(s)" + flags).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    // ---------------------------------------------------------------- games

    private static LiteralArgumentBuilder<CommandSourceStack> partie(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("list").executes(ShowCommands::listParties))
                .then(literal("create")
                        .executes(c -> run(c, action("partie.create")))
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> run(c, action("partie.create", "name", str(c, "name"))))))
                .then(literal("rename").then(argument("partie", word()).suggests(PARTIES)
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("partie.update", "partie", str(c, "partie"), "name", str(c, "name")))))))
                .then(literal("status").then(argument("partie", word()).suggests(PARTIES)
                        .then(argument("status", word()).suggests(STATUSES)
                                .executes(c -> run(c, action("partie.update", "partie", str(c, "partie"), "status", str(c, "status")))))))
                .then(literal("archive").then(argument("partie", word()).suggests(PARTIES)
                        .then(argument("archived", BoolArgumentType.bool())
                                .executes(c -> run(c, action("partie.archive", "partie", str(c, "partie"), "archived", BoolArgumentType.getBool(c, "archived")))))))
                .then(literal("delete").then(argument("partie", word()).suggests(PARTIES)
                        .executes(c -> run(c, action("partie.delete", "partie", str(c, "partie"))))))
                .then(literal("activate").then(argument("partie", word()).suggests(PARTIES)
                        .executes(c -> run(c, action("partie.activate", "partie", str(c, "partie"))))));
    }

    private static int listParties(CommandContext<CommandSourceStack> c) {
        Season s = activeSeason();
        if (s == null) {
            fail(c, "Aucune saison active.");
            return 0;
        }
        header(c, "Parties de " + s.name);
        Partie active = s.activePartie();
        if (s.parties.isEmpty()) info(c, "Aucune partie (« Partie 1 » sera créée avec la première manche).");
        for (Partie p : s.parties.values())
            info(c, Component.literal(p.id + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(p.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + p.status.replace('_', ' ') + ", " + p.activeManches().size() + " manche(s)"
                            + (p == active ? " ★ active" : "") + (p.archived ? " · archivée" : "")).withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    // ---------------------------------------------------------------- rounds

    private static LiteralArgumentBuilder<CommandSourceStack> manche(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("list").executes(ShowCommands::listManches))
                .then(literal("create").then(argument("name", word())
                        .executes(c -> run(c, action("manche.create", "name", str(c, "name"))))
                        .then(argument("timer", integer(0))
                                .executes(c -> run(c, action("manche.create", "name", str(c, "name"), "timer", getInteger(c, "timer")))))))
                .then(literal("rename").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("name", word())
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "name", str(c, "name")))))))
                .then(literal("aliases").then(argument("manche", word()).suggests(MANCHES)
                        .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "aliases", List.of())))
                        .then(argument("aliases", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "aliases", words(str(c, "aliases"))))))))
                .then(literal("stage").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("onStage", BoolArgumentType.bool())
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "onStage", BoolArgumentType.getBool(c, "onStage")))))))
                .then(literal("scene").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("scene", word()).suggests(SCENES_OR_NONE)
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "scene", str(c, "scene")))))))
                .then(literal("timer").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("seconds", integer(0))
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "timer", getInteger(c, "seconds")))))))
                .then(literal("order").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("position", integer(1))
                                .executes(c -> run(c, action("manche.update", "manche", str(c, "manche"), "order", getInteger(c, "position")))))))
                .then(literal("archive").then(argument("manche", word()).suggests(MANCHES)
                        .then(argument("archived", BoolArgumentType.bool())
                                .executes(c -> run(c, action("manche.archive", "manche", str(c, "manche"), "archived", BoolArgumentType.getBool(c, "archived")))))))
                .then(literal("delete").then(argument("manche", word()).suggests(MANCHES)
                        .executes(c -> run(c, action("manche.delete", "manche", str(c, "manche"))))))
                .then(literal("tp")
                        .then(literal("add").then(argument("manche", word()).suggests(MANCHES).executes(ShowCommands::addTp)))
                        .then(literal("clear").then(argument("manche", word()).suggests(MANCHES)
                                .executes(c -> run(c, action("manche.tp.clear", "manche", str(c, "manche")))))))
                .then(literal("start").then(argument("manche", word()).suggests(MANCHES_OR_NEXT)
                        .executes(c -> run(c, action("manche.start", "manche", str(c, "manche"))))))
                .then(literal("next").executes(c -> run(c, action("manche.start", "manche", "next"))))
                .then(literal("end").executes(c -> run(c, action("manche.end"))));
    }

    /** The point where the organiser stands (and looks) becomes a TP point of the round. */
    private static int addTp(CommandContext<CommandSourceStack> c) {
        if (player(c) == null) return 0;
        Vec3 pos = c.getSource().getPosition();
        Vec2 rot = c.getSource().getRotation();
        return run(c, action("manche.tp.add", "manche", str(c, "manche"), "world", Busts.worldId(c.getSource().getLevel()),
                "x", Math.round(pos.x * 10) / 10.0, "y", Math.round(pos.y * 10) / 10.0, "z", Math.round(pos.z * 10) / 10.0,
                "yaw", Math.round(rot.y), "pitch", Math.round(rot.x)));
    }

    private static int listManches(CommandContext<CommandSourceStack> c) {
        Season s = activeSeason();
        Partie p = activePartie();
        if (s == null || p == null) {
            fail(c, "Aucune manche : /craftMasterManche create <nom>");
            return 0;
        }
        header(c, "Manches — " + s.name + (s.parties.size() > 1 ? " · " + p.name : ""));
        Manche current = data().currentManche();
        for (Manche r : p.manches) {
            StringBuilder b = new StringBuilder();
            if (r.onStage) b.append(" · scène");
            if (r.scene != null) b.append(" · décor ").append(r.scene);
            if (r.timer > 0) b.append(" · chrono ").append(r.timer).append(" s");
            if (!r.tp.isEmpty()) b.append(" · ").append(r.tp.size()).append(" TP");
            if (!r.aliases.isEmpty()) b.append(" · surnoms ").append(String.join(", ", r.aliases));
            if (p.scores.containsKey(r.id)) b.append(" · notée");
            String num = r.archived ? "—" : String.valueOf(p.number(r));
            info(c, Component.literal(num + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(r.name).withStyle(r == current ? ChatFormatting.GREEN : r.archived ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE))
                    .append(Component.literal((r == current ? " ▶ en cours" : "") + (r.archived ? " (archivée)" : "") + b).withStyle(ChatFormatting.GRAY)));
        }
        if (p.manches.isEmpty()) info(c, "Aucune manche : /craftMasterManche create <nom>");
        return 1;
    }

    // ---------------------------------------------------------------- cast

    private static LiteralArgumentBuilder<CommandSourceStack> player(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("list").executes(ShowCommands::listPlayers))
                .then(literal("add").then(argument("pseudo", word()).suggests(CAST)
                        .executes(c -> run(c, action("player.add", "pseudo", str(c, "pseudo"))))
                        .then(argument("aliases", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("player.add", "pseudo", str(c, "pseudo"), "aliases", words(str(c, "aliases"))))))))
                .then(literal("aliases").then(argument("pseudo", word()).suggests(CAST)
                        .executes(c -> run(c, action("player.update", "pseudo", str(c, "pseudo"), "aliases", List.of())))
                        .then(argument("aliases", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("player.update", "pseudo", str(c, "pseudo"), "aliases", words(str(c, "aliases"))))))))
                .then(literal("rename").then(argument("pseudo", word()).suggests(CAST)
                        .then(argument("newPseudo", word())
                                .executes(c -> run(c, action("player.update", "pseudo", str(c, "pseudo"), "newPseudo", str(c, "newPseudo")))))))
                .then(literal("remove").then(argument("pseudo", word()).suggests(CAST)
                        .executes(c -> run(c, action("player.remove", "pseudo", str(c, "pseudo"))))));
    }

    private static int listPlayers(CommandContext<CommandSourceStack> c) {
        Season s = activeSeason();
        if (s == null) {
            fail(c, "Aucune saison active.");
            return 0;
        }
        header(c, "Joueurs de " + s.name);
        if (s.players.isEmpty()) info(c, "Aucun joueur : /craftMasterPlayer add <pseudo> [surnoms]");
        for (SeasonPlayer p : s.players.values())
            info(c, Component.literal(p.name).withStyle(p.archived ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE)
                    .append(Component.literal((p.aliases.isEmpty() ? "" : "  (" + String.join(", ", p.aliases) + ")")
                            + "  " + s.total(p.name) + " pts" + (p.archived ? " · archivé" : "")).withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    // ---------------------------------------------------------------- live

    /** Points for the round being played (or the round named): the quickest command of the show. */
    private static LiteralArgumentBuilder<CommandSourceStack> give(String name) {
        return literal(name).requires(ADMIN)
                .then(argument("pseudo", word()).suggests(CAST)
                        .then(argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(c -> run(c, action("score.add", "player", str(c, "pseudo"), "points", getInteger(c, "points"))))
                                .then(argument("manche", word()).suggests(MANCHES)
                                        .executes(c -> run(c, action("score.add", "player", str(c, "pseudo"), "points", getInteger(c, "points"),
                                                "manche", str(c, "manche")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> publish(String name) {
        return literal(name).requires(ADMIN)
                .executes(c -> run(c, action("scores.publish")))
                .then(argument("season", integer(1)).suggests(SEASON_IDS)
                        .executes(c -> run(c, action("scores.publish", "season", getInteger(c, "season")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reveal(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("start")
                        .executes(c -> run(c, action("reveal.start")))
                        .then(argument("season", integer(1)).suggests(SEASON_IDS)
                                .executes(c -> run(c, action("reveal.start", "season", getInteger(c, "season"))))))
                .then(literal("stop").executes(c -> run(c, action("reveal.stop"))))
                .then(literal("timing")
                        .executes(c -> {
                            Stage.RevealTiming t = data().stage().reveal;
                            info(c, "Rideau " + t.curtain() + " s · tambour " + t.drum() + " s · par joueur " + t.step() + " s · podium " + t.podium() + " s");
                            return 1;
                        })
                        .then(argument("curtain", DoubleArgumentType.doubleArg(0, 30))
                                .then(argument("drum", DoubleArgumentType.doubleArg(0, 30))
                                        .then(argument("step", DoubleArgumentType.doubleArg(0, 30))
                                                .then(argument("podium", DoubleArgumentType.doubleArg(0, 30)).executes(c -> {
                                                    Stage.RevealTiming t = new Stage.RevealTiming(DoubleArgumentType.getDouble(c, "curtain"),
                                                            DoubleArgumentType.getDouble(c, "drum"), DoubleArgumentType.getDouble(c, "step"),
                                                            DoubleArgumentType.getDouble(c, "podium"));
                                                    data().change("Durées de la révélation", () -> { data().stage().reveal = t; });
                                                    ok(c, "Révélation : rideau " + t.curtain() + " s, tambour " + t.drum() + " s, "
                                                            + t.step() + " s par joueur, " + t.podium() + " s pour le podium.");
                                                    return 1;
                                                }))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> timer(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("stop").executes(c -> run(c, action("timer.stop"))));
    }

    // ---------------------------------------------------------------- stage set-up

    private static LiteralArgumentBuilder<CommandSourceStack> stage(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("info").executes(ShowCommands::stageInfo))
                .then(literal("curtain")
                        .then(literal("set").then(argument("name", word()).suggests(CURTAINS)
                                .executes(c -> setCurtain(c, target(c)))
                                .then(argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> setCurtain(c, BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
                        .then(literal("remove").then(argument("name", word()).suggests(CURTAINS).executes(c -> {
                            String n = str(c, "name");
                            boolean removed = data().change("Rideau oublié : " + n, () -> data().stage().curtains.remove(n) != null);
                            if (!removed) fail(c, "Aucun rideau « " + n + " ».");
                            else ok(c, "Rideau « " + n + " » oublié (les blocs restent).");
                            return removed ? 1 : 0;
                        })))
                        .then(literal("open")
                                .executes(c -> run(c, action("curtain.open")))
                                .then(argument("name", word()).suggests(CURTAINS).executes(c -> run(c, action("curtain.open", "name", str(c, "name"))))))
                        .then(literal("close")
                                .executes(c -> run(c, action("curtain.close")))
                                .then(argument("name", word()).suggests(CURTAINS).executes(c -> run(c, action("curtain.close", "name", str(c, "name")))))))
                .then(literal("anchor")
                        .executes(c -> setAnchor(c, target(c)))
                        .then(argument("pos", BlockPosArgument.blockPos()).executes(c -> setAnchor(c, BlockPosArgument.getLoadedBlockPos(c, "pos")))))
                .then(literal("scene")
                        .then(literal("save").then(argument("name", word()).suggests(SCENES)
                                .then(argument("from", BlockPosArgument.blockPos()).then(argument("to", BlockPosArgument.blockPos())
                                        .executes(ShowCommands::saveScene)))))
                        .then(literal("place").then(argument("name", word()).suggests(SCENES)
                                .executes(c -> run(c, action("scene.set", "name", str(c, "name"))))))
                        .then(literal("delete").then(argument("name", word()).suggests(SCENES).executes(c -> {
                            String n = str(c, "name");
                            boolean removed = data().change("Décor oublié : " + n, () -> data().stage().scenes.remove(n) != null);
                            if (!removed) fail(c, "Aucun décor « " + n + " ».");
                            else ok(c, "Décor « " + n + " » oublié (le fichier de structure reste).");
                            return removed ? 1 : 0;
                        }))));
    }

    private static int setCurtain(CommandContext<CommandSourceStack> c, BlockPos pos) {
        if (pos == null) return 0;
        if (!(c.getSource().getLevel().getBlockState(pos).getBlock() instanceof fr.lordfinn.craftmaster.block.CurtainBlock)) {
            fail(c, "Pas de rideau en " + pos.toShortString() + ".");
            return 0;
        }
        String n = str(c, "name");
        BlockSpot b = spot(c, pos);
        data().change("Rideau « " + n + " » : " + b.describe(), () -> { data().stage().curtains.put(n, b); });
        ok(c, "Rideau « " + n + " » enregistré en " + b.describe() + ".");
        return 1;
    }

    private static int setAnchor(CommandContext<CommandSourceStack> c, BlockPos pos) {
        if (pos == null) return 0;
        BlockSpot b = spot(c, pos);
        data().change("Ancre de scène : " + b.describe(), () -> { data().stage().anchor = b; });
        ok(c, "Ancre de scène en " + b.describe() + " : les décors sont posés à partir de ce bloc (coin nord-ouest, en bas).");
        return 1;
    }

    private static int saveScene(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String n = str(c, "name");
        BlockPos a = BlockPosArgument.getLoadedBlockPos(c, "from"), b = BlockPosArgument.getLoadedBlockPos(c, "to");
        int max = StageControl.MAX_SCENE_SIZE;
        if (Math.abs(a.getX() - b.getX()) >= max || Math.abs(a.getY() - b.getY()) >= max || Math.abs(a.getZ() - b.getZ()) >= max) {
            fail(c, "Décor trop grand (" + max + " blocs au plus par côté).");
            return 0;
        }
        Vec3i size = StageControl.saveScene(c.getSource().getLevel(), n, a, b);
        data().change("Décor enregistré : " + n, () -> {
            data().stage().scenes.put(n, new Stage.Scene(size.getX(), size.getY(), size.getZ()));
        });
        ok(c, "Décor « " + n + " » enregistré (" + size.getX() + " × " + size.getY() + " × " + size.getZ() + ")."
                + (data().stage().anchor == null ? " Définissez l'ancre : /craftMasterStage anchor" : ""));
        return 1;
    }

    private static int stageInfo(CommandContext<CommandSourceStack> c) {
        Stage st = data().stage();
        header(c, "Plateau");
        info(c, "Rideaux : " + (st.curtains.isEmpty() ? "aucun (/craftMasterStage curtain set stage)" : ""));
        st.curtains.forEach((n, b) -> info(c, "  " + n + " → " + b.describe()));
        info(c, "Ancre : " + (st.anchor == null ? "aucune" : st.anchor.describe()));
        info(c, "Décors : " + (st.scenes.isEmpty() ? "aucun" : ""));
        st.scenes.forEach((n, s) -> info(c, "  " + n + " (" + s.sizeX() + " × " + s.sizeY() + " × " + s.sizeZ() + ")"
                + (n.equals(data().live().scene) ? " ▶ posé" : "")));
        Stage.RevealTiming t = st.reveal;
        info(c, "Révélation : rideau " + t.curtain() + " s, tambour " + t.drum() + " s, " + t.step() + " s par joueur, " + t.podium() + " s podium");
        return 1;
    }

    // ---------------------------------------------------------------- undo and state

    private static LiteralArgumentBuilder<CommandSourceStack> undo(String name) {
        return literal(name).requires(ADMIN)
                .executes(c -> run(c, action("undo", "count", 1)))
                .then(argument("count", integer(1, CraftMasterData.JOURNAL_SIZE))
                        .executes(c -> run(c, action("undo", "count", getInteger(c, "count")))));
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static LiteralArgumentBuilder<CommandSourceStack> history(String name) {
        return literal(name).requires(ADMIN).executes(c -> {
            List<CraftMasterData.Change> h = data().history();
            header(c, "Dernières modifications (annulables)");
            if (h.isEmpty()) info(c, "Rien depuis le démarrage du serveur.");
            for (int i = 0; i < Math.min(10, h.size()); i++)
                info(c, Component.literal((i + 1) + ". " + TIME.format(Instant.ofEpochMilli(h.get(i).at())) + " ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(h.get(i).description()).withStyle(ChatFormatting.WHITE)));
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> status(String name) {
        return literal(name).requires(ADMIN)
                // The voice control's context as JSON (docs/design/emission.md §4), to check what it receives
                .then(literal("context").executes(c -> {
                    String json = new GsonBuilder().serializeNulls().setPrettyPrinting().create()
                            .toJson(ShowContext.build(c.getSource().getServer(), c.getSource().getPlayer()));
                    info(c, Component.literal(json).withStyle(ChatFormatting.GRAY));
                    return 1;
                }))
                .executes(c -> {
            Season s = activeSeason();
            header(c, "Direct");
            info(c, "Saison active : " + (s == null ? "aucune" : s.name + " (n° " + s.id + ")" + (s.hidden ? ", cachée" : "")));
            Partie p = activePartie();
            if (p != null && s.parties.size() > 1) info(c, "Partie active : " + p.name);
            Manche r = data().currentManche();
            Live.Current cur = data().live().current;
            info(c, "Manche en cours : " + (r == null ? "aucune" : data().season(cur.season()).parties.get(cur.partie()).number(r) + " — " + r.name));
            long last = data().live().lastScoreAt;
            info(c, "Dernier score : " + (last == 0 ? "jamais" : TIME.format(Instant.ofEpochMilli(last))));
            if (s != null) {
                int unpublished = 0;
                for (String pl : s.scoredPlayers()) if (s.unpublishedDelta(pl) != 0) unpublished++;
                info(c, "Scores non publiés : " + (unpublished == 0 ? "aucun" : unpublished + " joueur(s) — /craftMasterPublish"));
            }
            Show.status(c.getSource().getServer()).forEach(l -> info(c, l));
            return 1;
        });
    }
}
