package fr.lordfinn.craftmaster.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.task.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /ambiance} (alias {@code /cfam}): stage looks; {@code /conduite} (alias {@code /cfco}): cue lists and what
 * plays them by itself. Names may hold spaces when quoted; lists in the chat are clickable.
 */
public final class StageCommands {

    private StageCommands() {}

    private static final SuggestionProvider<CommandSourceStack> LOOKS = (c, b) -> SharedSuggestionProvider.suggest(
            Scenography.book().looks.keySet().stream().map(StageCommands::quoted).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> CUES = (c, b) -> SharedSuggestionProvider.suggest(
            Scenography.book().cues.keySet().stream().map(StageCommands::quoted).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> CUES_OR_LOOKS = (c, b) -> {
        List<String> l = new ArrayList<>();
        Scenography.book().cues.keySet().forEach(k -> l.add(quoted(k)));
        Scenography.book().looks.keySet().forEach(k -> l.add(quoted(k)));
        l.add("aucune");
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> PARTS = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("tout", "lumieres", "rideaux", "tableaux", "ecrans", "lumieres,rideaux"), b);
    private static final SuggestionProvider<CommandSourceStack> LINE_STARTS = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("attendre 2", "ambiance ", "noir 2", "conduite ", "cfsp tout couleur rouge 2", "cfsp tout suivre @scene",
                    "cfsp groupe face* intensite 60 3", "cfpt hoist 1 6 4", "cmtele tout clip dernier", "cfma next", "tv intro"), b);
    private static final SuggestionProvider<CommandSourceStack> SPOT_TARGETS = (c, b) -> {
        List<String> l = new ArrayList<>(List.of("tout"));
        l.addAll(fr.lordfinn.craftmaster.studio.Spotlights.names());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> LOOK_ORDERS = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("allume", "eteint", "couleur rouge", "intensite 60", "cone 8", "suivre @scene", "vise ", "fondu 2", "rideau ouvert"), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) -> SharedSuggestionProvider.suggest(Cues.EVENTS, b);
    private static final SuggestionProvider<CommandSourceStack> MANCHES = (c, b) -> {
        var data = fr.lordfinn.craftmaster.CraftMaster.data();
        var s = data == null ? null : data.activeSeason();
        List<String> l = new ArrayList<>();
        if (s != null) for (var p : s.parties.values()) for (var m : p.manches) l.add(m.id);
        return SharedSuggestionProvider.suggest(l, b);
    };

    static String quoted(String name) {
        return name.contains(" ") ? "\"" + name + "\"" : name;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("ambiance", "cfam", "craftMasterLook")) d.register(looks(n));
        for (String n : List.of("conduite", "cfco", "craftMasterCue")) d.register(cues(n));
    }

    // ---------------------------------------------------------------- looks

    private static LiteralArgumentBuilder<CommandSourceStack> looks(String root) {
        var save = argument("name", StringArgumentType.string()).suggests(LOOKS)
                .executes(c -> say(c, Scenography.saveLook(c.getSource().getServer(), str(c, "name"), List.of(), null)))
                .then(argument("parts", StringArgumentType.word()).suggests(PARTS)
                        .executes(c -> saveParts(c, null))
                        .then(argument("fade", DoubleArgumentType.doubleArg(0, 60)).executes(c -> saveParts(c, DoubleArgumentType.getDouble(c, "fade")))));
        var play = argument("name", StringArgumentType.string()).suggests(LOOKS)
                .executes(c -> say(c, Scenography.playLook(c.getSource().getServer(), str(c, "name"), null)))
                .then(argument("fade", DoubleArgumentType.doubleArg(0, 60))
                        .executes(c -> say(c, Scenography.playLook(c.getSource().getServer(), str(c, "name"), DoubleArgumentType.getDouble(c, "fade")))));
        return literal(root).requires(Permissions.ADMIN)
                .executes(StageCommands::listLooks)
                .then(literal("enregistrer").then(save))
                .then(literal("save").then(save))
                .then(literal("lancer").then(play))
                .then(literal("charger").then(play))
                .then(literal("play").then(play))
                .then(literal("liste").executes(StageCommands::listLooks))
                // Changes a saved look without playing it: /ambiance modifier tension contre couleur rouge
                .then(literal("modifier").then(argument("name", StringArgumentType.string()).suggests(LOOKS)
                        .then(argument("target", StringArgumentType.string()).suggests(SPOT_TARGETS)
                                .then(argument("order", StringArgumentType.greedyString()).suggests(LOOK_ORDERS)
                                        .executes(c -> say(c, Scenography.editLook(str(c, "name"), str(c, "target"), str(c, "order"))))))))
                .then(literal("supprimer").then(argument("name", StringArgumentType.string()).suggests(LOOKS)
                        .executes(c -> say(c, Scenography.deleteLook(str(c, "name"))))))
                .then(literal("noir").executes(c -> say(c, Scenography.blackout(c.getSource().getServer(), 1)))
                        .then(argument("fade", DoubleArgumentType.doubleArg(0, 60))
                                .executes(c -> say(c, Scenography.blackout(c.getSource().getServer(), DoubleArgumentType.getDouble(c, "fade"))))));
    }

    private static int saveParts(CommandContext<CommandSourceStack> c, Double fade) {
        List<String> parts = new ArrayList<>();
        for (String w : str(c, "parts").split("[,+]")) {
            if (w.equalsIgnoreCase("tout")) {
                parts.clear();
                break;
            }
            String p = Look.part(w);
            if (p == null) {
                c.getSource().sendFailure(Component.literal("Partie inconnue « " + w + " » : lumieres, rideaux, tableaux, ecrans ou tout."));
                return 0;
            }
            parts.add(p);
        }
        return say(c, Scenography.saveLook(c.getSource().getServer(), str(c, "name"), parts, fade));
    }

    private static int listLooks(CommandContext<CommandSourceStack> c) {
        Map<String, Look> looks = Scenography.book().looks;
        c.getSource().sendSuccess(() -> Component.literal("Ambiances (" + looks.size() + ") — clic : la lancer").withStyle(ChatFormatting.GOLD), false);
        if (looks.isEmpty()) c.getSource().sendSuccess(() -> Component.literal(" Règle la scène puis /ambiance enregistrer <nom>.").withStyle(ChatFormatting.GRAY), false);
        looks.forEach((name, look) -> c.getSource().sendSuccess(() -> Component.literal(" ▶ " + name).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand("/ambiance lancer " + quoted(name)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Lancer « " + name + " »"))))
                .append(Component.literal("  " + look.describe()).withStyle(ChatFormatting.GRAY))
                .append(button(" [mettre à jour]", "/ambiance enregistrer " + quoted(name), "Remplacer par la scène actuelle", false)), false));
        return looks.size();
    }

    // ---------------------------------------------------------------- cue lists

    private static LiteralArgumentBuilder<CommandSourceStack> cues(String root) {
        var name = argument("name", StringArgumentType.string()).suggests(CUES);
        return literal(root).requires(Permissions.ADMIN)
                .executes(StageCommands::listCues)
                .then(literal("liste").executes(StageCommands::listCues))
                .then(literal("creer").then(argument("name", StringArgumentType.string()).executes(c -> say(c, Cues.create(str(c, "name"))))))
                .then(literal("ajouter").then(argument("name", StringArgumentType.string()).suggests(CUES)
                        .then(argument("line", StringArgumentType.greedyString()).suggests(LINE_STARTS)
                                .executes(c -> say(c, Cues.add(str(c, "name"), str(c, "line"), 0))))))
                .then(literal("inserer").then(argument("name", StringArgumentType.string()).suggests(CUES)
                        .then(argument("at", IntegerArgumentType.integer(1)).then(argument("line", StringArgumentType.greedyString()).suggests(LINE_STARTS)
                                .executes(c -> say(c, Cues.add(str(c, "name"), str(c, "line"), IntegerArgumentType.getInteger(c, "at"))))))))
                .then(literal("retirer").then(argument("name", StringArgumentType.string()).suggests(CUES)
                        .then(argument("at", IntegerArgumentType.integer(1)).executes(c -> say(c, Cues.remove(str(c, "name"), IntegerArgumentType.getInteger(c, "at")))))))
                .then(literal("voir").then(argument("name", StringArgumentType.string()).suggests(CUES).executes(c -> showCue(c, str(c, "name")))))
                .then(literal("lancer").then(argument("name", StringArgumentType.string()).suggests(CUES_OR_LOOKS)
                        .executes(c -> say(c, Cues.play(c.getSource().getServer(), str(c, "name"))))))
                .then(literal("stop").executes(c -> say(c, Cues.stop())))
                .then(literal("statut").executes(c -> say(c, Cues.status())))
                .then(literal("supprimer").then(argument("name", StringArgumentType.string()).suggests(CUES).executes(c -> say(c, Cues.delete(str(c, "name"))))))
                .then(literal("auto").executes(StageCommands::listAutos)
                        .then(argument("event", StringArgumentType.word()).suggests(EVENTS)
                                .then(argument("cue", StringArgumentType.string()).suggests(CUES_OR_LOOKS)
                                        .executes(c -> say(c, Cues.setAuto(str(c, "event"), str(c, "cue")))))))
                .then(literal("manche").then(argument("manche", StringArgumentType.string()).suggests(MANCHES)
                        .then(argument("cue", StringArgumentType.string()).suggests(CUES_OR_LOOKS).executes(c -> {
                            Tasks.Located l = Tasks.locateActive(str(c, "manche"));
                            if (l == null) {
                                c.getSource().sendFailure(Component.literal("Manche « " + str(c, "manche") + " » introuvable dans la saison active."));
                                return 0;
                            }
                            return say(c, Cues.setRound(l.manche().id, l.manche().name, str(c, "cue")));
                        }))));
    }

    private static int listCues(CommandContext<CommandSourceStack> c) {
        Map<String, List<String>> cues = Scenography.book().cues;
        c.getSource().sendSuccess(() -> Component.literal("Conduites (" + cues.size() + ") — " + Cues.status()).withStyle(ChatFormatting.GOLD), false);
        if (cues.isEmpty()) c.getSource().sendSuccess(() -> Component.literal(" /conduite ajouter <nom> <ligne> crée une conduite et sa première ligne.").withStyle(ChatFormatting.GRAY), false);
        cues.forEach((name, lines) -> c.getSource().sendSuccess(() -> Component.literal(" ▶ " + name).withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand("/conduite lancer " + quoted(name)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Lancer « " + name + " »"))))
                .append(Component.literal("  " + lines.size() + " ligne(s)").withStyle(ChatFormatting.GRAY))
                .append(button(" [voir]", "/conduite voir " + quoted(name), "Ses lignes, à modifier", false)), false));
        c.getSource().sendSuccess(() -> button(" [stop]", "/conduite stop", "Arrêter la conduite en cours", false)
                .append(button("  [automatiques]", "/conduite auto", "Ce qui se lance tout seul", false)), false);
        return cues.size();
    }

    private static int showCue(CommandContext<CommandSourceStack> c, String name) {
        List<String> lines = Cues.lines(name);
        if (lines == null) {
            c.getSource().sendFailure(Component.literal("Conduite « " + Scenography.key(name) + " » inconnue."));
            return 0;
        }
        String q = quoted(Scenography.key(name));
        c.getSource().sendSuccess(() -> Component.literal("Conduite « " + Scenography.key(name) + " »").withStyle(ChatFormatting.GOLD)
                .append(button("  [▶ lancer]", "/conduite lancer " + q, "La jouer maintenant", false)), false);
        for (int i = 0; i < lines.size(); i++) {
            int n = i + 1;
            String line = lines.get(i);
            c.getSource().sendSuccess(() -> Component.literal(String.format(" %2d. ", n)).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(line).withStyle(st -> st.withColor(line.startsWith("attendre") ? ChatFormatting.AQUA : ChatFormatting.WHITE)
                            .withClickEvent(new ClickEvent.SuggestCommand("/conduite inserer " + q + " " + n + " " + line))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Clic : copier pour insérer une ligne modifiée ici")))))
                    .append(button("  [✕]", "/conduite retirer " + q + " " + n, "Retirer cette ligne", false)), false);
        }
        c.getSource().sendSuccess(() -> button(" [+ ajouter une ligne]", "/conduite ajouter " + q + " ", "attendre <s>, ambiance <nom> [s], noir [s], conduite <nom>, ou une commande", true), false);
        return lines.size();
    }

    private static int listAutos(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("Ce qui se lance tout seul :").withStyle(ChatFormatting.GOLD), false);
        for (String e : Cues.EVENTS) {
            String cue = Cues.autos().get(e);
            c.getSource().sendSuccess(() -> Component.literal(" • " + e + " : ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(cue == null ? "rien" : cue).withStyle(cue == null ? ChatFormatting.GRAY : ChatFormatting.WHITE))
                    .append(button("  [changer]", "/conduite auto " + e + " ", "Conduite ou ambiance à lancer à ce moment (aucune : rien)", true)), false);
        }
        Map<String, String> rounds = Scenography.book().rounds;
        if (!rounds.isEmpty()) rounds.forEach((m, cue) -> c.getSource().sendSuccess(() -> Component.literal(" • manche " + m + " : " + cue).withStyle(ChatFormatting.GRAY), false));
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static MutableComponent button(String text, String command, String hover, boolean suggest) {
        return Component.literal(text).withStyle(st -> st.withColor(ChatFormatting.GREEN)
                .withClickEvent(suggest ? new ClickEvent.SuggestCommand(command) : new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int say(CommandContext<CommandSourceStack> c, String message) {
        boolean bad = message.contains("inconnu") || message.startsWith("Aucun") || message.startsWith("Donne") || message.startsWith("Ni ");
        if (bad) c.getSource().sendFailure(Component.literal(message));
        else c.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        return bad ? 0 : 1;
    }
}
