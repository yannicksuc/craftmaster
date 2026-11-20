package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.trial.Judging;
import fr.lordfinn.craftmaster.trial.item.MeasuringTapeItem;
import fr.lordfinn.craftmaster.trial.kit.Arena;
import fr.lordfinn.craftmaster.trial.kit.Arenas;
import fr.lordfinn.craftmaster.trial.kit.Finish;
import fr.lordfinn.craftmaster.trial.kit.Pickups;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.lordfinn.craftmaster.command.CraftMasterCommands.action;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.fail;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.list;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.ok;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /cm arene} (cfar): the trial sets copied once per contestant ({@link Arenas}) and the installers of
 * the trials built on them. Reference: docs/wiki/Arenes.md.
 */
public final class ArenaCommands {

    private ArenaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, ArenaCommands::arena, "craftMasterArene", "cfar");
    }

    private static final SuggestionProvider<CommandSourceStack> ARENAS = list(c -> Arenas.all().stream().map(a -> a.name).toList());
    private static final SuggestionProvider<CommandSourceStack> MANCHES = list(c -> {
        Season s = CraftMaster.data().activeSeason();
        return s == null || s.activePartie() == null ? List.of() : s.activePartie().activeManches().stream().map(m -> m.name).toList();
    });
    private static final SuggestionProvider<CommandSourceStack> CAST = list(c -> {
        List<String> out = new ArrayList<>(Judging.contestants());
        for (String n : c.getSource().getOnlinePlayerNames()) if (!out.contains(n)) out.add(n);
        out.add("tous");
        return out;
    });
    /** Where /cm arene tp goes: the reference room, then each copy (by its contestant). */
    private static final SuggestionProvider<CommandSourceStack> PLACES = list(c -> {
        List<String> out = new ArrayList<>(List.of("reference"));
        try {
            Arena a = Arenas.get(StringArgumentType.getString(c, "arene"));
            if (a != null) out.addAll(a.copies.keySet());
        } catch (IllegalArgumentException e) {
            // no arena typed yet
        }
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> OPTIONS = list(c -> List.of("chronoLettre", "panneauxSuite", "murs", "inventaireVide", "solFin", "alphabet", "aveugle", "cameraInventaire", "vocalLancement"));
    private static final SuggestionProvider<CommandSourceStack> JURY = list(c -> {
        List<String> out = new ArrayList<>(List.of("tous", "suivant", "precedent"));
        try {
            Arena a = Arenas.get(c.getArgument("arene", String.class));
            if (a != null) out.addAll(fr.lordfinn.craftmaster.trial.kit.DrawingImages.names(a.name));
        } catch (IllegalArgumentException ignored) {
        }
        return out;
    });

    private static String str(CommandContext<CommandSourceStack> c, String n) {
        return c.getArgument(n, String.class);
    }

    private static Arena arena(CommandContext<CommandSourceStack> c) {
        Arena a = Arenas.get(str(c, "arene"));
        if (a == null) fail(c, "Arène inconnue : " + str(c, "arene") + " (/cm arene liste).");
        return a;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) fail(c, "Commande réservée aux joueurs.");
        return p;
    }

    private static void say(CommandContext<CommandSourceStack> c, Component line) {
        c.getSource().sendSystemMessage(line);
    }

    /** "tous" or nothing = the season's contestants; else the names given. */
    private static List<String> players(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("tous") || raw.equalsIgnoreCase("all")) return new ArrayList<>(Judging.contestants());
        return new ArrayList<>(Arrays.asList(raw.trim().split("[ ,]+")));
    }

    /** "riricococo | riri coco co co" → the answers, trimmed (separated by « | »). */
    static List<String> splitAnswers(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\\|")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    private static int showCode(CommandContext<CommandSourceStack> c) {
        Arena a = arena(c);
        if (a == null) return 0;
        Arena.Code code = a.codes.get(str(c, "code"));
        if (code == null) {
            fail(c, "Pas de code « " + str(c, "code") + " » dans « " + a.name + " ». /cm arene code " + a.name + " " + str(c, "code") + " reponses <réponse | autre>");
            return 0;
        }
        say(c, Component.literal("Code « " + str(c, "code") + " » de « " + a.name + " » (espaces, majuscules, accents et ponctuation ignorés)").withStyle(ChatFormatting.GOLD));
        say(c, Component.literal(" Réponses : " + String.join(" | ", code.reponses)).withStyle(ChatFormatting.WHITE));
        if (!code.syllabes.isEmpty()) {
            say(c, Component.literal(" Lue en syllabes, dans l'ordre et en nombre exacts : " + String.join(", ", code.syllabes.keySet())).withStyle(ChatFormatting.WHITE));
            for (Map.Entry<String, List<String>> e : code.syllabes.entrySet())
                if (!e.getValue().isEmpty())
                    say(c, Component.literal("  « " + e.getKey() + " » s'écrit aussi : " + String.join(" | ", e.getValue())).withStyle(ChatFormatting.GRAY));
        }
        for (Map.Entry<String, List<String>> e : code.variantes.entrySet())
            say(c, Component.literal(" « " + e.getKey() + " » s'écrit aussi : " + String.join(" | ", e.getValue())).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** {@code syllabe <s> [orthographes | supprimer]}: the code is then read as syllables of its alphabet. */
    private static int syllable(CommandContext<CommandSourceStack> c, String raw) {
        Arena a = arena(c);
        if (a == null) return 0;
        Arena.Code code = a.codes.computeIfAbsent(str(c, "code"), k -> new Arena.Code());
        List<String> v = splitAnswers(raw);
        if (v.equals(List.of("supprimer"))) code.syllabes.remove(str(c, "syllabe"));
        else code.syllabes.put(str(c, "syllabe"), v);
        Arenas.save();
        return showCode(c);
    }

    private static Manche findManche(String name) {
        Season s = CraftMaster.data().activeSeason();
        if (s == null || s.activePartie() == null) return null;
        for (Manche m : s.activePartie().activeManches())
            if (m.id.equalsIgnoreCase(name) || m.name.equalsIgnoreCase(name) || m.aliases.stream().anyMatch(x -> x.equalsIgnoreCase(name))) return m;
        return null;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> arena(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(ArenaCommands::listArenas)
                .then(literal("liste").executes(ArenaCommands::listArenas))
                .then(literal("creer").then(argument("arene", word()).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    BlockPos[] pts = MeasuringTapeItem.lastPoints(p.getUUID());
                    if (pts == null || pts.length < 2 || pts[0] == null || pts[1] == null) {
                        fail(c, "Pose d'abord deux points au mètre ruban (deux coins opposés de la zone, sol compris).");
                        return 0;
                    }
                    Arena a = Arenas.create(str(c, "arene"), p.level(), pts[0], pts[1]);
                    ok(c, "Arène « " + a.name + " » : " + a.sizeX() + " × " + a.sizeY() + " × " + a.sizeZ() + " blocs. Ensuite : /cm arene spawn, /cm arene copier.");
                    return 1;
                })))
                .then(literal("supprimer").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                    if (!Arenas.remove(str(c, "arene"))) {
                        fail(c, "Arène inconnue.");
                        return 0;
                    }
                    ok(c, "Arène oubliée (ses blocs restent en place).");
                    return 1;
                })))
                .then(literal("spawn").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                    Arena a = arena(c);
                    ServerPlayer p = player(c);
                    if (a == null || p == null) return 0;
                    if (!a.contains(p.blockPosition(), null)) {
                        fail(c, "Place-toi dans la zone de référence de « " + a.name + " ».");
                        return 0;
                    }
                    a.hasSpawn = true;
                    a.sx = p.getX();
                    a.sy = p.getY();
                    a.sz = p.getZ();
                    a.syaw = p.getYRot();
                    Arenas.save();
                    ok(c, "Point de départ de « " + a.name + " » : ici (et au même endroit dans chaque copie). Réapparition comprise.");
                    return 1;
                })))
                .then(literal("copier").then(argument("arene", word()).suggests(ARENAS).executes(c -> copy(c, null))
                        .then(argument("joueurs", StringArgumentType.greedyString()).suggests(CAST).executes(c -> copy(c, str(c, "joueurs"))))))
                .then(literal("reset").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                    Arena a = arena(c);
                    if (a == null) return 0;
                    int n = Arenas.rebuild(c.getSource().getServer(), a);
                    ok(c, n + " copie(s) de « " + a.name + " » remises comme la référence (blocs, coffres, cadres, entités).");
                    return 1;
                })))
                .then(literal("retirer").then(argument("arene", word()).suggests(ARENAS).then(argument("joueur", word()).suggests(CAST).executes(c -> {
                    Arena a = arena(c);
                    if (a == null) return 0;
                    if (!Arenas.unassign(a, str(c, "joueur"))) {
                        fail(c, str(c, "joueur") + " n'a pas de copie.");
                        return 0;
                    }
                    ok(c, "Copie de " + str(c, "joueur") + " retirée de la liste (ses blocs restent).");
                    return 1;
                }))))
                .then(literal("placer").then(argument("arene", word()).suggests(ARENAS).executes(c -> place(c, null))
                        .then(argument("joueurs", StringArgumentType.greedyString()).suggests(CAST).executes(c -> place(c, str(c, "joueurs"))))))
                .then(literal("tp").then(argument("arene", word()).suggests(ARENAS).then(argument("joueur", word()).suggests(PLACES)
                        .executes(c -> tp(c, false)).then(literal("dessus").executes(c -> tp(c, true))))))
                .then(literal("kit").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                    Arena a = arena(c);
                    ServerPlayer p = player(c);
                    if (a == null || p == null) return 0;
                    int n = Arenas.setKit(p, a);
                    ok(c, "Kit de « " + a.name + " » : " + n + " pile(s), celles de ton inventaire. Données par /cm arene placer.");
                    return 1;
                }).then(literal("vide").executes(c -> {
                    Arena a = arena(c);
                    if (a == null) return 0;
                    a.kit.clear();
                    Arenas.save();
                    ok(c, "Kit de « " + a.name + " » vidé.");
                    return 1;
                }))))
                .then(literal("champ").then(argument("arene", word()).suggests(ARENAS).executes(c -> field(c, null))
                        .then(argument("encre", word()).executes(c -> field(c, str(c, "encre"))))))
                .then(literal("manche").then(argument("arene", word()).suggests(ARENAS).then(argument("manche", word()).suggests(MANCHES).executes(c -> {
                    Arena a = arena(c);
                    if (a == null) return 0;
                    Manche m = findManche(str(c, "manche"));
                    if (m == null) {
                        fail(c, "Manche inconnue dans la partie active : " + str(c, "manche"));
                        return 0;
                    }
                    a.manche = m.id;
                    Arenas.save();
                    ok(c, "« " + a.name + " » suit la manche « " + m.name + " ».");
                    return 1;
                }))))
                .then(literal("option").then(argument("arene", word()).suggests(ARENAS)
                        .then(literal("chronoSuite").then(argument("secondes", IntegerArgumentType.integer(0, 7200)).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            a.chronoSuite = IntegerArgumentType.getInteger(c, "secondes");
                            Arenas.save();
                            ok(c, a.chronoSuite == 0 ? "Pas de chrono à la révélation de la suite." : "Chrono de " + a.chronoSuite + " s lancé à la révélation de la suite.");
                            return 1;
                        })))
                        // Game mode of the contestants during the trial
                        .then(literal("mode").then(argument("mode", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("aventure", "survie"), b))
                                .executes(c -> {
                                    Arena a = arena(c);
                                    if (a == null) return 0;
                                    String m = str(c, "mode").toLowerCase();
                                    if (!m.equals("aventure") && !m.equals("survie")) {
                                        fail(c, "Mode : aventure ou survie.");
                                        return 0;
                                    }
                                    a.mode = m.equals("aventure") ? null : m;
                                    Arenas.save();
                                    ok(c, "« " + a.name + " » se joue en " + m + ".");
                                    return 1;
                                })))
                        .then(argument("option", word()).suggests(OPTIONS).then(argument("on", BoolArgumentType.bool()).executes(ArenaCommands::option)))))
                // The steps of /cm epreuve suite, in order (none = one step: revele)
                .then(literal("etapes").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            ok(c, "Suite de « " + a.name + " » : " + String.join(" → ", a.etapes.isEmpty() ? List.of("revele") : a.etapes)
                                    + " (étapes : revele, vocal, vocalfin, chrono:<secondes>, panneaux).");
                            return 1;
                        })
                        .then(argument("etapes", StringArgumentType.greedyString()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                                List.of("revele", "vocal", "vocalfin", "chrono:600", "panneaux", "aucune"), b.createOffset(b.getStart() + b.getRemaining().lastIndexOf(' ') + 1)))
                                .executes(c -> {
                                    Arena a = arena(c);
                                    if (a == null) return 0;
                                    List<String> steps = new java.util.ArrayList<>();
                                    for (String w : str(c, "etapes").trim().toLowerCase().split("[\\s,>→]+")) {
                                        if (w.isEmpty() || w.equals("aucune")) continue;
                                        if (!w.matches("revele|vocal|vocalfin|panneaux|chrono:\\d{1,4}")) {
                                            fail(c, "Étape « " + w + " » inconnue : revele, vocal, vocalfin, chrono:<secondes>, panneaux.");
                                            return 0;
                                        }
                                        steps.add(w);
                                    }
                                    a.etapes = steps;
                                    Arenas.save();
                                    ok(c, "Suite de « " + a.name + " » : " + String.join(" → ", steps.isEmpty() ? List.of("revele") : steps) + ".");
                                    return 1;
                                }))))
                // What « lancer l'épreuve » loads first: the boxes widened by a margin, the starts, other zones
                .then(literal("chargement").then(argument("arene", word()).suggests(ARENAS).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            StringBuilder z = new StringBuilder();
                            for (int[] r : a.chargementZones) z.append(" [").append(r[0]).append(" ").append(r[1]).append(" → ").append(r[2]).append(" ").append(r[3]).append("]");
                            ok(c, "Chargé avant le lancement de « " + a.name + " » : référence et copies + " + a.chargementMarge + " blocs autour, départs"
                                    + (z.isEmpty() ? "" : ", zones" + z) + ".");
                            return 1;
                        })
                        .then(literal("marge").then(argument("blocs", IntegerArgumentType.integer(0, 256)).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            a.chargementMarge = IntegerArgumentType.getInteger(c, "blocs");
                            Arenas.save();
                            ok(c, "« " + a.name + " » : " + a.chargementMarge + " blocs chargés autour de la référence et des copies.");
                            return 1;
                        })))
                        .then(literal("ajouter").then(argument("x0", IntegerArgumentType.integer()).then(argument("z0", IntegerArgumentType.integer())
                                .then(argument("x1", IntegerArgumentType.integer()).then(argument("z1", IntegerArgumentType.integer()).executes(c -> {
                                    Arena a = arena(c);
                                    if (a == null) return 0;
                                    a.chargementZones.add(new int[]{IntegerArgumentType.getInteger(c, "x0"), IntegerArgumentType.getInteger(c, "z0"),
                                            IntegerArgumentType.getInteger(c, "x1"), IntegerArgumentType.getInteger(c, "z1")});
                                    Arenas.save();
                                    ok(c, "« " + a.name + " » : zone ajoutée au chargement (" + a.chargementZones.size() + ").");
                                    return 1;
                                }))))))
                        .then(literal("vider").executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            a.chargementZones.clear();
                            Arenas.save();
                            ok(c, "« " + a.name + " » : plus de zone en plus au chargement.");
                            return 1;
                        }))))
                // Copies = the active season's contestants (new ones built, removed ones' ground put back)
                .then(literal("synchroniser").executes(c -> syncCopies(c, null))
                        .then(argument("arene", word()).suggests(ARENAS).executes(c -> syncCopies(c, str(c, "arene")))))
                .then(literal("panneaux").then(argument("arene", word()).suggests(ARENAS).executes(c -> signs(c, false))
                        .then(literal("verrouiller").executes(c -> signs(c, true)))))
                .then(literal("comparer").then(argument("arene", word()).suggests(ARENAS).executes(c -> compare(c, false))
                        .then(literal("noter").executes(c -> compare(c, true)))))
                .then(literal("jury").then(argument("arene", word()).suggests(ARENAS).executes(c -> jury(c, null, fr.lordfinn.craftmaster.trial.kit.JuryScreen.DEFAULT_SCREEN))
                        .then(argument("quoi", word()).suggests(JURY).executes(c -> jury(c, str(c, "quoi"), fr.lordfinn.craftmaster.trial.kit.JuryScreen.DEFAULT_SCREEN))
                                .then(argument("ecran", word()).executes(c -> jury(c, str(c, "quoi"), str(c, "ecran")))))))
                .then(literal("code").then(argument("arene", word()).suggests(ARENAS).then(argument("code", word()).executes(ArenaCommands::showCode)
                        .then(literal("reponses").then(argument("texte", StringArgumentType.greedyString()).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            Arena.Code code = a.codes.computeIfAbsent(str(c, "code"), k -> new Arena.Code());
                            code.reponses = splitAnswers(str(c, "texte"));
                            Arenas.save();
                            return showCode(c);
                        })))
                        .then(literal("variante").then(argument("mot", word()).then(argument("texte", StringArgumentType.greedyString()).executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            Arena.Code code = a.codes.computeIfAbsent(str(c, "code"), k -> new Arena.Code());
                            List<String> v = splitAnswers(str(c, "texte"));
                            if (v.isEmpty() || v.equals(List.of("aucune"))) code.variantes.remove(str(c, "mot"));
                            else code.variantes.put(str(c, "mot"), v);
                            Arenas.save();
                            return showCode(c);
                        }))))
                        .then(literal("syllabe").then(argument("syllabe", word()).executes(c -> syllable(c, ""))
                                .then(argument("texte", StringArgumentType.greedyString()).executes(c -> syllable(c, str(c, "texte"))))))
                        .then(literal("supprimer").executes(c -> {
                            Arena a = arena(c);
                            if (a == null) return 0;
                            a.codes.remove(str(c, "code"));
                            Arenas.save();
                            ok(c, "Code « " + str(c, "code") + " » retiré de « " + a.name + " ».");
                            return 1;
                        })))))
                .then(literal("demander").then(argument("code", word()).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    if (!fr.lordfinn.craftmaster.trial.kit.Codes.ask(p, str(c, "code"))) {
                        fail(c, "Pas de code « " + str(c, "code") + " » dans l'arène où tu es.");
                        return 0;
                    }
                    return 1;
                })))
                .then(literal("fleurs").then(argument("arene", word()).executes(ArenaCommands::installFlowers)))
                .then(literal("animal").then(argument("arene", word()).executes(ArenaCommands::installAnimal)));
    }

    // ---------------------------------------------------------------- the commands

    private static int listArenas(CommandContext<CommandSourceStack> c) {
        say(c, Component.literal("— Arènes d'épreuve —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (Arenas.all().isEmpty()) say(c, Component.literal(" Aucune. /cm arene creer <nom> avec deux points du mètre ruban.").withStyle(ChatFormatting.GRAY));
        for (Arena a : Arenas.all()) {
            List<String> opts = new ArrayList<>();
            if (a.manche != null) opts.add("manche " + a.manche);
            if (a.chronoLettre) opts.add("chrono à la lettre");
            if (a.chronoSuite > 0) opts.add("chrono " + a.chronoSuite + " s à la suite");
            if (a.panneauxSuite) opts.add("panneaux verrouillés à la suite");
            if (a.field != null) opts.add(a.field.blind ? "champ à dessiner à l'aveugle" : "champ à dessiner");
            if (a.solFin) opts.add(a.alphabet ? "fin au sol, inventaire alphabétique" : "fin au sol");
            if (!a.kit.isEmpty()) opts.add("kit de " + a.kit.size());
            if (a.inventoryCam != null) opts.add(a.inventoryCam ? "caméra d'inventaire" : "sans caméra d'inventaire");
            say(c, Component.literal(" " + a.name).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" " + a.sizeX() + "×" + a.sizeY() + "×" + a.sizeZ() + " — copies : "
                            + (a.copies.isEmpty() ? "aucune" : String.join(", ", a.copies.keySet()))
                            + (opts.isEmpty() ? "" : " — " + String.join(", ", opts))).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int copy(CommandContext<CommandSourceStack> c, String who) {
        Arena a = arena(c);
        if (a == null) return 0;
        List<String> players = players(who);
        if (players.isEmpty()) {
            fail(c, "Aucun candidat dans la saison active : donne les joueurs (/cm arene copier " + a.name + " Ensuri Cyllu).");
            return 0;
        }
        List<String> added = Arenas.assign(a, players);
        int n = Arenas.rebuild(c.getSource().getServer(), a);
        ok(c, n + " copie(s) de « " + a.name + " » construites" + (added.isEmpty() ? "" : " (nouvelles : " + String.join(", ", added) + ")")
                + ". /cm arene placer " + a.name + " pour y envoyer les candidats.");
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> c, String who) {
        Arena a = arena(c);
        if (a == null) return 0;
        List<String> done = Arenas.place(c.getSource().getServer(), a, players(who == null ? String.join(" ", a.copies.keySet()) : who));
        if (done.isEmpty()) {
            fail(c, "Personne de connecté à placer.");
            return 0;
        }
        ok(c, "Placés dans « " + a.name + " » (réapparition là) : " + String.join(", ", done) + ".");
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c, boolean above) {
        Arena a = arena(c);
        ServerPlayer p = player(c);
        if (a == null || p == null) return 0;
        String who = str(c, "joueur");
        boolean reference = who.equalsIgnoreCase("reference") || who.equalsIgnoreCase("référence") || who.equalsIgnoreCase("ref");
        int[] off = reference ? new int[]{0, 0, 0} : null;
        String owner = null;
        if (!reference) for (Map.Entry<String, int[]> e : a.copies.entrySet())
            if (e.getKey().equalsIgnoreCase(who)) {
                off = e.getValue();
                owner = e.getKey();
            }
        if (off == null) {
            fail(c, who + " n'a pas de copie dans « " + a.name + " » (reference pour la salle de référence).");
            return 0;
        }
        ServerLevel level = Arenas.level(c.getSource().getServer(), a);
        String world = level.dimension().identifier().getPath();
        ok(c, (reference ? "Salle de référence de " + a.name : "Copie de " + owner + " dans « " + a.name + " »") + " (monde " + world + ")"
                + (above ? ", vue d'en haut." : (reference && a.manche != null ? " : /cm cam ici <n> y pose une caméra de la manche." : ".")));
        if (above) {
            double x = (a.x0 + a.x1 + 1) / 2.0 + off[0], z = (a.z0 + a.z1 + 1) / 2.0 + off[2];
            double y = a.y1 + off[1] + 2 + Math.max(a.sizeX(), a.sizeZ()) * 0.6;
            p.teleportTo(level, x, y, z, Set.of(), p.getYRot(), 90f, true);
            p.getAbilities().flying = p.getAbilities().mayfly;
            p.onUpdateAbilities();
        } else {
            double[] s = Arenas.spawn(a, reference ? null : off);
            p.teleportTo(level, s[0], s[1], s[2], Set.of(), a.syaw, 0f, true);
        }
        return 1;
    }

    private static int field(CommandContext<CommandSourceStack> c, String ink) {
        Arena a = arena(c);
        ServerPlayer p = player(c);
        if (a == null || p == null) return 0;
        BlockPos[] pts = MeasuringTapeItem.lastPoints(p.getUUID());
        if (pts == null || pts.length < 2 || pts[0] == null || pts[1] == null) {
            fail(c, "Pose deux points au mètre ruban sur deux coins opposés du sol du champ.");
            return 0;
        }
        Arena.Field f = new Arena.Field();
        f.x0 = Math.min(pts[0].getX(), pts[1].getX());
        f.x1 = Math.max(pts[0].getX(), pts[1].getX());
        f.z0 = Math.min(pts[0].getZ(), pts[1].getZ());
        f.z1 = Math.max(pts[0].getZ(), pts[1].getZ());
        f.y = Math.min(pts[0].getY(), pts[1].getY());
        f.paper = BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(new BlockPos(f.x0, f.y, f.z0)).getBlock()).toString();
        if (ink != null) f.ink = ink.contains(":") ? ink : "minecraft:" + ink;
        a.field = f;
        Arenas.save();
        ok(c, "Champ à dessiner de « " + a.name + " » : " + (f.x1 - f.x0 + 1) + " × " + (f.z1 - f.z0 + 1) + ", " + f.paper + " → " + f.ink + ".");
        return 1;
    }

    private static int syncCopies(CommandContext<CommandSourceStack> c, String name) {
        List<Arena> arenas = new java.util.ArrayList<>();
        if (name != null) {
            Arena a = arena(c);
            if (a == null) return 0;
            arenas.add(a);
        } else arenas.addAll(Arenas.all());
        int changed = 0;
        for (Arena a : arenas) {
            if (a.copies.isEmpty()) continue;
            List<String> lines = fr.lordfinn.craftmaster.trial.kit.TrialRun.syncCopies(c.getSource().getServer(), a, true);
            for (String l : lines) ok(c, l);
            if (!lines.isEmpty()) changed++;
        }
        if (changed == 0) ok(c, "Les copies suivent déjà les candidats de la saison (" + String.join(", ", fr.lordfinn.craftmaster.trial.kit.TrialRun.cast()) + ").");
        return 1;
    }

    private static int option(CommandContext<CommandSourceStack> c) {
        Arena a = arena(c);
        if (a == null) return 0;
        boolean on = BoolArgumentType.getBool(c, "on");
        switch (str(c, "option").toLowerCase()) {
            case "chronolettre" -> a.chronoLettre = on;
            case "panneauxsuite" -> a.panneauxSuite = on;
            case "murs" -> a.walls = on;
            case "inventairevide" -> a.clearInventory = on;
            case "solfin" -> a.solFin = on;
            case "aveugle" -> {
                if (a.field == null) {
                    fail(c, "« " + a.name + " » n'a pas de champ à dessiner (/cm arene champ).");
                    return 0;
                }
                a.field.blind = on;
            }
            case "alphabet" -> {
                a.alphabet = on;
                if (on) a.solFin = true;
            }
            case "camerainventaire" -> a.inventoryCam = on;
            case "vocallancement" -> a.vocalLancement = on;
            default -> {
                fail(c, "Option inconnue : chronoLettre, panneauxSuite, murs, inventaireVide, solFin, alphabet, aveugle, cameraInventaire, vocalLancement (ou chronoSuite <secondes>, mode aventure|survie).");
                return 0;
            }
        }
        Arenas.save();
        ok(c, "« " + a.name + " » : " + str(c, "option") + " " + (on ? "oui" : "non") + ".");
        return 1;
    }

    private static int signs(CommandContext<CommandSourceStack> c, boolean lock) {
        Arena a = arena(c);
        if (a == null) return 0;
        Map<String, String> texts = Arenas.signs(c.getSource().getServer(), a, lock);
        Arenas.tellSigns(c.getSource().getServer(), a, texts, lock);
        if (c.getSource().getPlayer() == null || !Permissions.isAdmin(c.getSource().getPlayer()))
            for (Map.Entry<String, String> e : texts.entrySet()) say(c, Component.literal(e.getKey() + " : " + e.getValue()));
        return 1;
    }

    private static int compare(CommandContext<CommandSourceStack> c, boolean record) {
        Arena a = arena(c);
        if (a == null) return 0;
        Map<String, Double> scores = Arenas.compare(c.getSource().getServer(), a);
        if (scores.isEmpty()) {
            fail(c, "Aucune copie à comparer.");
            return 0;
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        say(c, Component.literal("— « " + a.name + " » comparé à la référence —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int rank = 0;
        for (Map.Entry<String, Double> e : sorted) {
            rank++;
            String pct = String.format(java.util.Locale.FRANCE, "%.1f %%", e.getValue());
            say(c, Component.literal(" " + rank + ". ").withStyle(ChatFormatting.GRAY).append(Finish.name(e.getKey()))
                    .append(Component.literal("  " + pct + " identique").withStyle(ChatFormatting.WHITE)));
        }
        if (record) {
            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(action("results.config", "kind", "count", "order", "higher"));
            for (Map.Entry<String, Double> e : sorted) actions.add(action("result.set", "player", e.getKey(), "value", e.getValue()));
            ActionExecutor.execute(c.getSource(), actions);
        } else {
            say(c, Component.literal(" [noter sur la feuille]").withStyle(st -> st.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/cm arene comparer " + a.name + " noter"))));
        }
        return 1;
    }

    /** The jury's screen: all the drawings, or one in big; then the buttons to switch it from the chat. */
    private static int jury(CommandContext<CommandSourceStack> c, String what, String screen) {
        Arena a = arena(c);
        if (a == null) return 0;
        if (what != null) {
            try {
                ok(c, fr.lordfinn.craftmaster.trial.kit.JuryScreen.show(c.getSource().getServer(), a, what, screen));
            } catch (Exception e) {
                fail(c, e.getMessage());
                return 0;
            }
        }
        say(c, Component.literal("Écran du jury : ").withStyle(ChatFormatting.GOLD).append(fr.lordfinn.craftmaster.trial.kit.JuryScreen.buttons(a.name)));
        return 1;
    }

    // ---------------------------------------------------------------- installers

    private static final Item[] FLOWERS = {Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.RED_TULIP,
            Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.TORCHFLOWER};

    /**
     * Fleurs de Jeremy: from where the organiser stands, facing their direction: a grass strip with a dirt path, 26
     * flowers lying on it (numbered 1 to 26, password: a word starting with the matching letter), the vase and the
     * golem at the end. The arena is the whole strip; its spawn is the path's start.
     */
    private static int installFlowers(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ServerLevel level = (ServerLevel) p.level();
        Direction fwd = p.getDirection();
        Direction side = fwd.getClockWise();
        BlockPos start = p.blockPosition().below();
        int length = 26 * 2 + 8, half = 3, height = 5;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), path = Blocks.DIRT_PATH.defaultBlockState();
        for (int i = -1; i <= length; i++)
            for (int s = -half; s <= half; s++) {
                BlockPos floor = start.relative(fwd, i).relative(side, s);
                level.setBlock(floor.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(floor, Math.abs(s) <= 1 && i < length - 1 ? path : grass, Block.UPDATE_CLIENTS);
                for (int h = 1; h <= height; h++) level.setBlock(floor.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        for (int n = 1; n <= 26; n++) {
            BlockPos at = start.relative(fwd, 1 + n * 2).relative(side, n % 2 == 0 ? 1 : -1).above();
            Vec3 v = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            char letter = (char) ('A' + n - 1);
            Pickups.placePickup(level, v, new ItemStack(FLOWERS[(n - 1) % FLOWERS.length]), "initiale:" + letter, "Fleur n°" + n,
                    p.getYRot() + (n * 47) % 360, String.valueOf(n));
        }
        BlockPos pot = start.relative(fwd, length - 3).above();
        level.setBlock(pot, Blocks.DECORATED_POT.defaultBlockState(), Block.UPDATE_CLIENTS);
        Pickups.placeVase(level, new Vec3(pot.getX() + 0.5, pot.getY(), pot.getZ() + 0.5), 26);
        BlockPos golemAt = start.relative(fwd, length - 3).relative(side, 2).above();
        IronGolem golem = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
        if (golem != null) {
            golem.snapTo(golemAt.getX() + 0.5, golemAt.getY(), golemAt.getZ() + 0.5, fwd.getOpposite().toYRot() + 30, 0);
            golem.setNoAi(true);
            golem.setInvulnerable(true);
            golem.setPersistenceRequired();
            golem.setCustomName(Component.literal("Jeremy"));
            level.addFreshEntity(golem);
        }
        BlockPos a = start.relative(fwd, -1).relative(side, -half).below();
        BlockPos b = start.relative(fwd, length).relative(side, half).above(height);
        Arena ar = Arenas.create(str(c, "arene"), level, a, b);
        ar.hasSpawn = true;
        ar.sx = p.getX();
        ar.sy = start.getY() + 1;
        ar.sz = p.getZ();
        ar.syaw = fwd.toYRot();
        ar.chronoLettre = true;
        Arenas.save();
        ok(c, "Chemin de « " + ar.name + " » posé : 26 fleurs, le vase et Jeremy. Ensuite : /cm arene manche " + ar.name
                + " <manche>, /cm arene copier " + ar.name + ", /cm arene placer " + ar.name + ". Panneau d'indice : /cm epreuve panneau <texte> en visant un panneau.");
        return 1;
    }

    /**
     * Animal: a white field (21 × 21) on grass, a sign at its entrance (the animal is written on it), the field
     * registered as the drawing field; the kit is a red and a green banner. Locks the signs and starts a 10 min timer
     * when the task's hidden part is revealed.
     */
    private static int installAnimal(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ServerLevel level = (ServerLevel) p.level();
        Direction fwd = p.getDirection();
        Direction side = fwd.getClockWise();
        BlockPos start = p.blockPosition().below();
        int size = 21, half = size / 2, height = 4;
        for (int i = -2; i <= size + 1; i++)
            for (int s = -half - 1; s <= half + 1; s++) {
                BlockPos floor = start.relative(fwd, i).relative(side, s);
                boolean inside = i >= 1 && i <= size && Math.abs(s) <= half;
                level.setBlock(floor, inside ? Blocks.CONCRETE.white().defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int h = 1; h <= height; h++) level.setBlock(floor.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        BlockPos signAt = start.relative(fwd, -1).relative(side, 2).above();
        level.setBlock(signAt, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,
                net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(fwd.getOpposite().toYRot())), Block.UPDATE_CLIENTS);
        BlockPos a = start.relative(fwd, -2).relative(side, -half - 1).below();
        BlockPos b = start.relative(fwd, size + 1).relative(side, half + 1).above(height);
        Arena ar = Arenas.create(str(c, "arene"), level, a, b);
        Arena.Field f = new Arena.Field();
        BlockPos f0 = start.relative(fwd, 1).relative(side, -half), f1 = start.relative(fwd, size).relative(side, half);
        f.x0 = Math.min(f0.getX(), f1.getX());
        f.x1 = Math.max(f0.getX(), f1.getX());
        f.z0 = Math.min(f0.getZ(), f1.getZ());
        f.z1 = Math.max(f0.getZ(), f1.getZ());
        f.y = start.getY();
        f.blind = true; // LordFinn: nothing shows while they walk, the picture comes at the end
        ar.field = f;
        ar.hasSpawn = true;
        ar.sx = p.getX();
        ar.sy = start.getY() + 1;
        ar.sz = p.getZ();
        ar.syaw = fwd.toYRot();
        ar.panneauxSuite = true;
        ar.chronoSuite = 600;
        ar.kit.clear();
        var ops = c.getSource().getServer().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for (Item banner : List.of(Items.BANNER.red(), Items.BANNER.green()))
            ItemStack.CODEC.encodeStart(ops, new ItemStack(banner)).result().ifPresent(ar.kit::add);
        Arenas.save();
        ok(c, "Champ de « " + ar.name + " » posé (" + size + " × " + size + ", panneau à l'entrée, bannières rouge et verte en kit). Ensuite : /cm arene manche "
                + ar.name + " <manche>, /cm arene copier " + ar.name + ", /cm arene placer " + ar.name + ".");
        return 1;
    }

    /** Writes a sign the organiser looks at (lines separated by "|"), waxed so the contestants cannot change it. */
    static int writeSign(CommandContext<CommandSourceStack> c, String text) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        var hit = p.pick(8, 1f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bh) || !(p.level().getBlockEntity(bh.getBlockPos()) instanceof SignBlockEntity sign)) {
            fail(c, "Regarde un panneau (à moins de 8 blocs).");
            return 0;
        }
        String[] parts = text.split("\\|", -1);
        sign.updateText(t -> {
            for (int i = 0; i < 4; i++) t = t.setMessage(i, Component.literal(i < parts.length ? parts[i].trim() : ""));
            return t;
        }, true);
        sign.setWaxed(true);
        BlockState st = p.level().getBlockState(bh.getBlockPos());
        p.level().sendBlockUpdated(bh.getBlockPos(), st, st, Block.UPDATE_CLIENTS);
        ok(c, "Panneau écrit et verrouillé.");
        return 1;
    }

    static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }
}
