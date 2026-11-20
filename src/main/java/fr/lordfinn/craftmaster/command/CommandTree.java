package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.network.MenuNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The one command of the mod: {@code /craftmaster} (alias {@code /cm}), in French, one group per thing of the show
 * (docs/wiki/Commandes.md). It is put together at the end of the registration from the trees the other classes
 * registered under their old names: each old tree is copied under its group with its words translated to French
 * ({@link #WORDS}), then the old root is taken out of the dispatcher, so the Tab key only proposes the new tree.
 * <p>
 * The old names still work through {@link #translate}: the console, RCON, the cue lists already saved, command
 * blocks and the AI control are translated silently, a player typing one gets the new form in grey.
 */
public final class CommandTree {

    private CommandTree() {}

    public static final String ROOT = "craftmaster", ALIAS = "cm";

    /** English words of the old trees and their French form (a sibling with the French name already: merged). */
    static final Map<String, String> WORDS = new LinkedHashMap<>();

    static {
        String[] pairs = {
                "start", "lancer", "list", "liste", "create", "creer", "rename", "renommer", "delete", "supprimer",
                "remove", "retirer", "add", "ajouter", "activate", "activer", "archive", "archiver", "aliases", "surnoms",
                "order", "ordre", "timer", "chrono", "end", "fin", "next", "suivante", "prev", "precedente",
                "status", "statut", "context", "contexte", "open", "ouvrir", "close", "fermer", "toggle", "basculer",
                "set", "regler", "clear", "effacer", "show", "montrer", "edit", "editer", "enable", "activer",
                "players", "joueurs", "curtain", "rideau", "scene", "decor", "stage", "scene", "anchor", "ancre",
                "place", "poser", "save", "enregistrer", "timing", "durees", "append", "ajouter", "rules", "regles",
                "seconds", "secondes", "title", "titre", "text", "texte", "wax", "cire", "revealOnStart", "revelee",
                "preview", "apercu", "view", "lire", "seal", "sceau", "hide", "cacher", "reveal", "reveler",
                "envelope", "enveloppe", "tools", "outils", "resetReveals", "cachertout", "readers", "lecteurs",
                "once", "unique", "ink", "encre", "pieces", "morceaux", "give", "donner", "bottle", "bouteille",
                "unhide", "decacher", "apply", "appliquer", "kind", "mesure", "scale", "bareme", "lock", "verrouiller",
                "size", "taille", "log", "journal", "prop", "objet", "rearm", "rearmer", "here", "ici", "aim", "viser",
                "goto", "aller", "name", "nom", "motion", "mouvement", "cut", "couper", "wide", "large",
                "follow", "suivre", "mosaic", "mosaique", "all", "tout", "lowerthird", "synthe", "player", "joueur",
                "task", "tache", "letterbox", "bandes", "spotlight", "vedette", "broadcast", "diffusion",
                "highlight", "illuminer", "hoist", "hisser", "poseEditor", "editeurposes", "season", "saison",
                "rank", "rang", "window", "fenetre", "windowDepth", "profondeurfenetre", "windowSize", "taillefenetre",
                "height", "hauteur", "publish", "publier", "fit", "ajuster", "loop", "boucle", "restart", "relancer",
                "collect", "ramasser", "egg", "oeuf", "glow", "briller", "play", "lancer", "charger", "lancer"};
        for (int i = 0; i < pairs.length; i += 2) WORDS.put(pairs[i], pairs[i + 1]);
    }

    /** Old root (lower case) → its place in the new tree ("" = the root itself). */
    static final Map<String, String> LEGACY = new LinkedHashMap<>();
    /** Old roots whose words differ from {@link #WORDS} (old root → word → new word). */
    static final Map<String, Map<String, String>> LEGACY_WORDS = new LinkedHashMap<>();
    /** Old roots gone without a place in the tree: what to use instead. */
    static final Map<String, String> GONE = new LinkedHashMap<>();

    private static void legacy(String path, String... roots) {
        for (String r : roots) LEGACY.put(r.toLowerCase(Locale.ROOT), path);
    }

    static {
        legacy("manche", "craftMasterManche", "cfma");
        legacy("manche chrono", "craftMasterTimer", "cfti");
        legacy("saison", "craftMasterSeason", "cfse");
        legacy("partie", "craftMasterPartie", "cfpa");
        legacy("joueur", "craftMasterPlayer", "cfpl");
        legacy("points", "craftMasterGive", "cfg");
        legacy("scores fixer", "craftMasterSetScore", "cfss");
        legacy("scores ajouter", "craftMasterAddScore", "cfas");
        legacy("scores effacer", "craftMasterRemoveScore", "cfds");
        legacy("scores recharger", "craftMasterReloadScores", "cfrs");
        legacy("scores saisie", "craftMasterScores", "cfsc");
        legacy("scores publier", "craftMasterPublish", "cfpu");
        legacy("classement", "craftMasterRanking", "cfrk");
        legacy("classement cacher", "craftMasterHideRanking", "cfhr");
        legacy("revelation", "craftMasterReveal", "cfrv");
        legacy("reprise", "craftMasterReprise", "cfrp");
        legacy("annuler", "craftMasterUndo", "cfun");
        legacy("refaire", "craftMasterRedo", "cfrf");
        legacy("historique", "craftMasterHistory", "cfhi");
        legacy("statut", "craftMasterStatus", "cfst");
        legacy("tache", "craftMasterTask", "tache", "cftk");
        legacy("note", "craftMasterNote", "cfno");
        legacy("epreuve", "craftMasterTrial", "cftr");
        legacy("epreuve feuille", "craftMasterResult", "cfre");
        legacy("epreuve chrono", "craftMasterChrono", "cfch");
        legacy("epreuve buzzer", "craftMasterBuzzer", "cfbz");
        legacy("epreuve zone", "craftMasterZone", "cfzo");
        legacy("epreuve tableau", "craftMasterBoard", "cfbd");
        legacy("epreuve jouets", "craftMasterToys", "cfty");
        legacy("arene", "craftMasterArene", "cfar");
        legacy("plateau", "craftMasterStage", "cfsg", "plateau");
        legacy("plateau rideau bloc", "craftMasterCurtain", "cfcu");
        legacy("plateau garage", "craftMasterGarage", "cfga");
        legacy("plateau pupitre", "craftMasterDesk", "cfdk");
        legacy("plateau gong", "craftMasterGong", "cfgo");
        legacy("plateau tablette", "craftMasterTablet", "cftb");
        legacy("plateau portrait", "craftMasterPortrait", "cfpt");
        legacy("plateau buste ajouter", "craftMasterAddBust", "cfab");
        legacy("plateau buste retirer", "craftMasterRemoveBust", "cfdb");
        legacy("ambiance", "ambiance", "cfam", "craftMasterLook");
        legacy("conduite", "conduite", "cfco", "craftMasterCue");
        legacy("sequence", "sequence", "cfsq", "craftMasterSequence");
        legacy("projecteur", "craftMasterSpotlight", "cfsp");
        legacy("camera", "craftMasterCamera", "cam");
        legacy("tv", "craftMasterTv", "tv");
        legacy("fx", "craftMasterFx", "fx");
        legacy("replay", "craftMasterReplay", "cmreplay");
        legacy("media image", "craftMasterImage", "image", "cfim");
        legacy("media tele", "cmtele", "craftMasterTele");
        legacy("media clip", "cmclip", "craftMasterClip");
        legacy("media chaine", "cmchaine", "craftMasterChaine");
        legacy("modo", "craftMasterModo", "modo", "cfmo");
        legacy("ia", "regieia");
        legacy("worldedit", "craftMasterWorldEdit", "cfwe");
        legacy("nommer", "craftMasterName", "cfnm");
        legacy("go", "craftMasterGo");
        legacy("spectateurs", "craftMasterSpectators", "cfsv");
        legacy("vocal", "craftMasterVocal", "cfvo");
        legacy("chronos", "craftMasterChronos", "cfcb");
        // The old "/plateau" (stage points) is in the stage group now
        LEGACY_WORDS.put("plateau", Map.of("retirer", "retirerpoint"));
        for (String r : List.of("craftMasterAddTask", "cfat")) GONE.put(r.toLowerCase(Locale.ROOT), "/cm manche creer <nom>");
        for (String r : List.of("craftMasterRemoveTask", "cfrt")) GONE.put(r.toLowerCase(Locale.ROOT), "/cm manche archiver <manche> true");
        for (String r : List.of("craftMasterRenameTask", "cfrn")) GONE.put(r.toLowerCase(Locale.ROOT), "/cm manche renommer <manche> <nom>");
    }

    /** An old tree's English word in French ({@code name} → {@code nom}), or the word itself. */
    public static String french(String word) {
        return WORDS.getOrDefault(word, word);
    }

    /** Where an old root ({@code cam}, {@code cfsp}…) stands in the new tree ("camera", "projecteur"…), or null. */
    public static String legacyPath(String oldRoot) {
        return oldRoot == null ? null : LEGACY.get(oldRoot.toLowerCase(Locale.ROOT));
    }

    private static LiteralCommandNode<CommandSourceStack> root;

    /** The new tree, once registered (null before). */
    public static LiteralCommandNode<CommandSourceStack> root() {
        return root;
    }

    // ---------------------------------------------------------------- building

    /** What a group is for, shown by {@code /cm aide} (and its order). */
    static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("menu", "le menu CraftMaster (touche N)");
        GROUPS.put("aide", "cette aide");
        GROUPS.put("statut", "l'état du direct");
        GROUPS.put("historique", "les dernières modifications");
        GROUPS.put("annuler", "annule la dernière modification");
        GROUPS.put("refaire", "refait ce qui vient d'être annulé");
        GROUPS.put("saison", "les saisons");
        GROUPS.put("partie", "les parties d'une saison");
        GROUPS.put("manche", "les manches : lancer, chrono, TP, décor");
        GROUPS.put("joueur", "les candidats de la saison");
        GROUPS.put("points", "donner des points dans la manche en cours");
        GROUPS.put("scores", "saisie, corrections et publication des scores");
        GROUPS.put("classement", "le classement");
        GROUPS.put("revelation", "la révélation mise en scène");
        GROUPS.put("tache", "les lettres de tâche et les enveloppes");
        GROUPS.put("note", "les notes cachées");
        GROUPS.put("epreuve", "feuille, chronos, buzzers, zones, objets d'épreuve");
        GROUPS.put("arene", "les arènes copiées pour chaque candidat");
        GROUPS.put("plateau", "rideaux, décors, garages, pupitres, gong, tablettes, tableaux, busts");
        GROUPS.put("ambiance", "les ambiances lumière");
        GROUPS.put("conduite", "les conduites");
        GROUPS.put("sequence", "les séquences (timeline)");
        GROUPS.put("projecteur", "les projecteurs");
        GROUPS.put("camera", "les caméras et la réalisation (alias cam)");
        GROUPS.put("tv", "l'habillage : synthés, scores, intro, diffusion, caméra d'inventaire");
        GROUPS.put("fx", "les effets d'écran");
        GROUPS.put("replay", "les replays");
        GROUPS.put("media", "images, télés, clips et chaînes Twitch");
        GROUPS.put("modo", "la régie des modos");
        GROUPS.put("ia", "les boutons de la régie IA");
        GROUPS.put("debug", "le mode debug (pour toi seul)");
        GROUPS.put("reprise", "la reprise après un crash");
        GROUPS.put("worldedit", "WorldEdit sans mises à jour");
        GROUPS.put("nommer", "nommer l'objet tenu");
        GROUPS.put("go", "le bouton « Commencer l'épreuve » de la lettre");
    }

    /**
     * Puts the tree together from the old ones registered in {@code d}, then takes the old ones out. Called once per
     * dispatcher, after every other registration ({@link CraftMaster}: late phase).
     */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        Map<String, CommandNode<CommandSourceStack>> old = new LinkedHashMap<>();
        for (CommandNode<CommandSourceStack> n : d.getRoot().getChildren()) old.put(n.getName().toLowerCase(Locale.ROOT), n);

        LiteralCommandNode<CommandSourceStack> cm = LiteralArgumentBuilder.<CommandSourceStack>literal(ROOT)
                .executes(CommandTree::home).build();

        // The new parts first: menu, help, debug
        cm.addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("menu").executes(c -> menu(c.getSource(), ""))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("page", WordArgument.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(MENU_PAGES, b))
                        .executes(c -> menu(c.getSource(), c.getArgument("page", String.class)))).build());
        cm.addChild(LiteralArgumentBuilder.<CommandSourceStack>literal("aide").executes(c -> help(c.getSource(), null))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("groupe", WordArgument.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                cm.getChildren().stream().filter(n -> n.canUse(c.getSource())).map(CommandNode::getName).toList(), b))
                        .executes(c -> help(c.getSource(), c.getArgument("groupe", String.class)))).build());
        cm.addChild(fr.lordfinn.craftmaster.trial.Debug.command());

        graft(cm, "statut", old.get("craftmasterstatus"), null);
        graft(cm, "historique", old.get("craftmasterhistory"), null);
        graft(cm, "annuler", old.get("craftmasterundo"), null);
        graft(cm, "refaire", old.get("craftmasterredo"), null);

        graft(cm, "saison", old.get("craftmasterseason"), null);
        graft(cm, "partie", old.get("craftmasterpartie"), null);
        LiteralCommandNode<CommandSourceStack> manche = graft(cm, "manche", old.get("craftmastermanche"), null);
        if (manche != null) {
            graftChildren(child(manche, "chrono", Permissions.ADMIN), old.get("craftmastertimer"), null);
            manche.addChild(fr.lordfinn.craftmaster.trial.kit.InventoryCam.mancheCommand());
            manche.addChild(fr.lordfinn.craftmaster.trial.kit.TrialRun.mancheEnvelopeCommand());
        }
        graft(cm, "joueur", old.get("craftmasterplayer"), null);

        graft(cm, "points", old.get("craftmastergive"), null);
        LiteralCommandNode<CommandSourceStack> scores = group(cm, "scores");
        graft(scores, "saisie", old.get("craftmasterscores"), null);
        graft(scores, "publier", old.get("craftmasterpublish"), null);
        graft(scores, "ajouter", old.get("craftmasteraddscore"), null);
        graft(scores, "fixer", old.get("craftmastersetscore"), null);
        graft(scores, "effacer", old.get("craftmasterremovescore"), null);
        graft(scores, "recharger", old.get("craftmasterreloadscores"), null);
        LiteralCommandNode<CommandSourceStack> ranking = graft(cm, "classement", old.get("craftmasterranking"), null);
        if (ranking != null) graft(ranking, "cacher", old.get("craftmasterhideranking"), null);
        graft(cm, "revelation", old.get("craftmasterreveal"), null);

        graft(cm, "tache", old.get("craftmastertask"), null);
        graft(cm, "note", old.get("craftmasternote"), Map.of("look", "aspect"));

        LiteralCommandNode<CommandSourceStack> trial = graft(cm, "epreuve", old.get("craftmastertrial"), null);
        if (trial != null) {
            graft(trial, "feuille", old.get("craftmasterresult"), null);
            graft(trial, "chrono", old.get("craftmasterchrono"), null);
            graft(trial, "buzzer", old.get("craftmasterbuzzer"), null);
            graft(trial, "zone", old.get("craftmasterzone"), null);
            graft(trial, "tableau", old.get("craftmasterboard"), null);
            graft(trial, "jouets", old.get("craftmastertoys"), null);
            // The trial live in one action each: lancer <manche>, suite, terminer
            for (var n : fr.lordfinn.craftmaster.trial.kit.TrialRun.trialCommands()) trial.addChild(n);
        }
        graft(cm, "arene", old.get("craftmasterarene"), null);
        // Watching a trial once done, the game master's voice
        graft(cm, "spectateurs", old.get("craftmasterspectators"), null);
        graft(cm, "vocal", old.get("craftmastervocal"), null);
        graft(cm, "chronos", old.get("craftmasterchronos"), null);

        LiteralCommandNode<CommandSourceStack> stage = graft(cm, "plateau", old.get("craftmasterstage"), null);
        if (stage != null) {
            // The old /plateau (the audience's side, points for the spotlights)
            CommandNode<CommandSourceStack> points = old.get("plateau");
            if (points != null) for (CommandNode<CommandSourceStack> ch : points.getChildren())
                stage.addChild(clone(ch, ch.getName().equals("retirer") ? "retirerpoint" : ch.getName(), null, false));
            CommandNode<CommandSourceStack> curtain = stage.getChild("rideau");
            if (curtain != null) graft(curtain, "bloc", old.get("craftmastercurtain"), null);
            graft(stage, "garage", old.get("craftmastergarage"), null);
            graft(stage, "pupitre", old.get("craftmasterdesk"), null);
            graft(stage, "gong", old.get("craftmastergong"), null);
            graft(stage, "tablette", old.get("craftmastertablet"), null);
            graft(stage, "portrait", old.get("craftmasterportrait"), null);
            LiteralCommandNode<CommandSourceStack> bust = group(stage, "buste");
            graft(bust, "ajouter", old.get("craftmasteraddbust"), null);
            graft(bust, "retirer", old.get("craftmasterremovebust"), null);
        }

        graft(cm, "ambiance", old.get("ambiance"), null);
        graft(cm, "conduite", old.get("conduite"), null);
        graft(cm, "sequence", old.get("sequence"), Map.of("scene", "scene"));
        graft(cm, "projecteur", old.get("craftmasterspotlight"), null);

        LiteralCommandNode<CommandSourceStack> camera = graft(cm, "camera", old.get("craftmastercamera"), null);
        LiteralCommandNode<CommandSourceStack> tv = graft(cm, "tv", old.get("craftmastertv"), null);
        if (tv != null) tv.addChild(fr.lordfinn.craftmaster.trial.kit.InventoryCam.command());
        graft(cm, "fx", old.get("craftmasterfx"), null);
        graft(cm, "replay", old.get("craftmasterreplay"), null);
        LiteralCommandNode<CommandSourceStack> media = group(cm, "media");
        graft(media, "image", old.get("craftmasterimage"), null);
        graft(media, "tele", old.get("cmtele"), null);
        graft(media, "clip", old.get("cmclip"), null);
        graft(media, "chaine", old.get("cmchaine"), null);

        graft(cm, "modo", old.get("craftmastermodo"), null);
        graft(cm, "ia", old.get("regieia"), null);

        graft(cm, "reprise", old.get("craftmasterreprise"), null);
        graft(cm, "worldedit", old.get("craftmasterworldedit"), null);
        graft(cm, "nommer", old.get("craftmastername"), null);
        graft(cm, "go", old.get("craftmastergo"), null);

        // Sub-alias: /cm cam = /cm camera
        if (camera != null) cm.addChild(alias(camera, "cam"));

        // Out with the old names (their trees live on in the new one)
        List<String> gone = new ArrayList<>();
        for (String name : old.keySet()) if (LEGACY.containsKey(name) || GONE.containsKey(name)) gone.add(old.get(name).getName());
        for (String extra : List.of("craftmasteraddtask", "craftmasterremovetask", "craftmasterrenametask", "cfat", "cfrt", "cfrn"))
            if (old.containsKey(extra)) gone.add(old.get(extra).getName());
        for (String name : gone) removeChild(d.getRoot(), name);

        d.getRoot().addChild(cm);
        d.getRoot().addChild(alias(cm, ALIAS));
        root = cm;
        CraftMaster.LOG.info("[Commandes] /{} (/{}) : {} groupes ; {} anciennes racines retirées (traduites si on les tape)",
                ROOT, ALIAS, cm.getChildren().size(), gone.size());
    }

    /** The old tree {@code from} copied under {@code parent} as {@code name}, its words in French. Null if absent. */
    static LiteralCommandNode<CommandSourceStack> graft(CommandNode<CommandSourceStack> parent, String name,
                                                       CommandNode<CommandSourceStack> from, Map<String, String> words) {
        if (from == null) {
            CraftMaster.LOG.warn("[Commandes] rien à mettre sous « {} » (ancienne commande absente)", name);
            return null;
        }
        LiteralCommandNode<CommandSourceStack> copy = (LiteralCommandNode<CommandSourceStack>) clone(from, name, words, true);
        parent.addChild(copy);
        return (LiteralCommandNode<CommandSourceStack>) parent.getChild(name);
    }

    /** The children of {@code from} added to {@code parent} (merged with its own). */
    static void graftChildren(CommandNode<CommandSourceStack> parent, CommandNode<CommandSourceStack> from, Map<String, String> words) {
        if (from == null) return;
        if (from.getCommand() != null && parent.getCommand() == null) CraftMaster.LOG.debug("[Commandes] {} sans action propre", parent.getName());
        for (CommandNode<CommandSourceStack> ch : from.getChildren()) parent.addChild(clone(ch, null, words, false));
    }

    /** A group of its own (no old tree): usable by whoever can use one of its children. */
    static LiteralCommandNode<CommandSourceStack> group(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack>[] self = new CommandNode[1];
        Predicate<CommandSourceStack> any = s -> self[0] != null && self[0].getChildren().stream().anyMatch(n -> n.canUse(s));
        LiteralCommandNode<CommandSourceStack> n = LiteralArgumentBuilder.<CommandSourceStack>literal(name).requires(any)
                .executes(c -> help(c.getSource(), name)).build();
        self[0] = n;
        parent.addChild(n);
        return n;
    }

    /** The child {@code name} of {@code parent}, made if missing. */
    static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name, Predicate<CommandSourceStack> requires) {
        CommandNode<CommandSourceStack> n = parent.getChild(name);
        if (n != null) return n;
        n = LiteralArgumentBuilder.<CommandSourceStack>literal(name).requires(requires).build();
        parent.addChild(n);
        return n;
    }

    /**
     * A deep copy of {@code node}, named {@code name} (null: its own name, translated if a literal), its literal
     * children translated through {@link #WORDS} and {@code words}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CommandNode<CommandSourceStack> clone(CommandNode<CommandSourceStack> node, String name, Map<String, String> words, boolean keepName) {
        ArgumentBuilder<CommandSourceStack, ?> b;
        if (node instanceof LiteralCommandNode<CommandSourceStack> lit) {
            String n = name != null ? name : keepName ? lit.getLiteral() : word(lit.getLiteral(), words);
            b = LiteralArgumentBuilder.<CommandSourceStack>literal(n);
        } else if (node instanceof ArgumentCommandNode arg) {
            RequiredArgumentBuilder r = RequiredArgumentBuilder.argument(arg.getName(), arg.getType());
            if (arg.getCustomSuggestions() != null) r.suggests(arg.getCustomSuggestions());
            b = r;
        } else {
            throw new IllegalArgumentException("node " + node);
        }
        b.requires(node.getRequirement());
        if (node.getCommand() != null) b.executes(node.getCommand());
        if (node.getRedirect() != null) b.forward(node.getRedirect(), node.getRedirectModifier(), node.isFork());
        CommandNode<CommandSourceStack> built = b.build();
        for (CommandNode<CommandSourceStack> ch : node.getChildren()) built.addChild(clone(ch, null, words, false));
        return built;
    }

    static String word(String w, Map<String, String> words) {
        if (words != null && words.containsKey(w)) return words.get(w);
        return WORDS.getOrDefault(w, w);
    }

    /** Another name for {@code node}: same rights, action and children (shared, not copied). */
    static LiteralCommandNode<CommandSourceStack> alias(CommandNode<CommandSourceStack> node, String name) {
        LiteralArgumentBuilder<CommandSourceStack> b = LiteralArgumentBuilder.<CommandSourceStack>literal(name).requires(node.getRequirement());
        if (node.getCommand() != null) b.executes(node.getCommand());
        LiteralCommandNode<CommandSourceStack> n = b.build();
        for (CommandNode<CommandSourceStack> ch : node.getChildren()) n.addChild(ch);
        return n;
    }

    @SuppressWarnings("unchecked")
    private static void removeChild(CommandNode<CommandSourceStack> parent, String name) {
        try {
            for (String field : List.of("children", "literals", "arguments")) {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<String, ?>) f.get(parent)).remove(name);
            }
        } catch (ReflectiveOperationException e) {
            CraftMaster.LOG.warn("[Commandes] impossible de retirer /{} : {}", name, e.toString());
        }
    }

    // ---------------------------------------------------------------- the root, the menu, the help

    private static int home(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        if (s.getPlayer() != null && MenuNetwork.canOpen(s.getPlayer())) return menu(s, "");
        return help(s, null);
    }

    /** The menu's pages (MainMenuScreen). */
    static final List<String> MENU_PAGES = List.of("manches", "epreuves", "ambiances", "camera", "joueurs", "debug");

    private static int menu(CommandSourceStack s, String page) {
        if (s.getPlayer() == null) {
            s.sendFailure(Component.literal("Le menu s'ouvre dans le jeu (touche N). Ici : /cm aide."));
            return 0;
        }
        if (!MenuNetwork.canOpen(s.getPlayer())) {
            s.sendFailure(Component.literal("Ton jeu n'a pas le mod CraftMaster : /cm aide liste les commandes."));
            return 0;
        }
        if (!page.isEmpty() && !MENU_PAGES.contains(page)) {
            s.sendFailure(Component.literal("Page « " + page + " » inconnue : " + String.join(", ", MENU_PAGES) + "."));
            return 0;
        }
        MenuNetwork.open(s.getPlayer(), page);
        return 1;
    }

    /** The groups (clickable), or the commands of one group. */
    static int help(CommandSourceStack s, String group) {
        if (root == null) return 0;
        if (group == null) {
            s.sendSuccess(() -> Component.literal("CraftMaster — /cm <groupe> … (Tab propose la suite)").withStyle(ChatFormatting.GOLD), false);
            for (Map.Entry<String, String> e : GROUPS.entrySet()) {
                CommandNode<CommandSourceStack> n = root.getChild(e.getKey());
                if (n == null || !n.canUse(s)) continue;
                s.sendSuccess(() -> Component.literal(" " + e.getKey()).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new ClickEvent.SuggestCommand("/cm " + e.getKey() + " "))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/cm aide " + e.getKey() + " : ses commandes"))))
                        .append(Component.literal("  " + e.getValue()).withStyle(ChatFormatting.GRAY)), false);
            }
            return 1;
        }
        CommandNode<CommandSourceStack> n = root.getChild(group.toLowerCase(Locale.ROOT));
        if (n == null || !n.canUse(s)) {
            s.sendFailure(Component.literal("Groupe « " + group + " » inconnu : /cm aide pour la liste."));
            return 0;
        }
        String head = "/cm " + n.getName();
        s.sendSuccess(() -> Component.literal(head + (GROUPS.containsKey(n.getName()) ? " — " + GROUPS.get(n.getName()) : ""))
                .withStyle(ChatFormatting.GOLD), false);
        Map<CommandNode<CommandSourceStack>, String> usage = s.getServer().getCommands().getDispatcher().getSmartUsage(n, s);
        if (usage.isEmpty()) s.sendSuccess(() -> Component.literal(" " + head).withStyle(ChatFormatting.YELLOW), false);
        for (String u : usage.values()) {
            String line = head + " " + u;
            String first = line.replaceAll("[ ]?[\\[<(].*$", "");
            MutableComponent m = Component.literal(" " + line).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent.SuggestCommand(first + " "))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Clic : écrire " + first))));
            s.sendSuccess(() -> m, false);
        }
        return 1;
    }

    // ---------------------------------------------------------------- old names

    /**
     * The new form of {@code line} if it starts with an old name (with or without its slash), else null. The words
     * after it are translated where the new tree has the French word ({@link #WORDS}), the arguments are kept.
     */
    public static String translate(String line) {
        if (root == null || line == null) return null;
        String body = line.startsWith("/") ? line.substring(1) : line;
        List<String> tokens = tokens(body);
        if (tokens.isEmpty()) return null;
        String first = tokens.getFirst().toLowerCase(Locale.ROOT);
        String path = LEGACY.get(first);
        if (path == null) return null;
        CommandNode<CommandSourceStack> node = root;
        StringBuilder out = new StringBuilder(ALIAS);
        if (!path.isEmpty()) for (String p : path.split(" ")) {
            CommandNode<CommandSourceStack> next = node.getChild(p);
            if (next == null) return null;
            node = next;
            out.append(' ').append(p);
        }
        Map<String, String> extra = LEGACY_WORDS.getOrDefault(first, Map.of());
        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String w = t;
            CommandNode<CommandSourceStack> next = node == null ? null : literalChild(node, t);
            if (next == null && node != null) {
                String fr = extra.containsKey(t) ? extra.get(t) : WORDS.get(t);
                if (fr != null && literalChild(node, fr) != null) {
                    next = literalChild(node, fr);
                    w = fr;
                }
            }
            if (next == null && node != null) {
                // An argument: follow it when there is only one way
                CommandNode<CommandSourceStack> arg = null;
                int args = 0;
                for (CommandNode<CommandSourceStack> ch : node.getChildren())
                    if (ch instanceof ArgumentCommandNode) {
                        arg = ch;
                        args++;
                    }
                next = args == 1 ? arg : node;
            }
            node = next;
            out.append(' ').append(w);
        }
        return out.toString();
    }

    private static CommandNode<CommandSourceStack> literalChild(CommandNode<CommandSourceStack> node, String name) {
        CommandNode<CommandSourceStack> ch = node.getChild(name);
        return ch instanceof LiteralCommandNode ? ch : null;
    }

    /** What to use instead of an old command that has no place in the new tree, or null. */
    public static String gone(String line) {
        if (line == null) return null;
        String body = line.startsWith("/") ? line.substring(1) : line;
        int sp = body.indexOf(' ');
        return GONE.get((sp < 0 ? body : body.substring(0, sp)).toLowerCase(Locale.ROOT));
    }

    /** Words of a command line, quoted parts kept whole (with their quotes). */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') quoted = !quoted;
            if (ch == ' ' && !quoted) {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** The groups' names (menus, tests). */
    public static Collection<String> groups() {
        return GROUPS.keySet();
    }

    static int ok(CommandSourceStack s, String message) {
        s.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GOLD), true);
        return Command.SINGLE_SUCCESS;
    }

    static int fail(CommandSourceStack s, String message) {
        s.sendFailure(Component.literal(message));
        return 0;
    }
}
