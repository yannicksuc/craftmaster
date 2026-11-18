package fr.lordfinn.craftmaster.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.bust.Busts;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /cm sequence} (alias {@code /cm sequence}): the stage's timelines (edit in game, play, tools, files);
 * {@code /cm plateau}: the stage's plan (where the audience looks from, named points, the master).
 */
public final class TimelineCommands {

    private TimelineCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SEQUENCES = (c, b) -> SharedSuggestionProvider.suggest(
            Sequences.all().keySet().stream().map(StageCommands::quoted).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> POINTS = (c, b) -> SharedSuggestionProvider.suggest(
            Scenography.book().plateau.points.keySet().stream().map(StageCommands::quoted).toList(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("sequence", "cfsq", "craftMasterSequence")) d.register(sequence(n));
        for (String n : List.of("plateau", "cfpl")) d.register(plateau(n));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int say(CommandContext<CommandSourceStack> c, String message) {
        boolean bad = message.contains("inconnue") || message.startsWith("Rien") || message.startsWith("Choisis") || message.startsWith("Pas de")
                || message.startsWith("Donne") || message.contains("illisible");
        c.getSource().sendSuccess(() -> Component.literal(message).withStyle(bad ? ChatFormatting.RED : ChatFormatting.GOLD), false);
        return bad ? 0 : 1;
    }

    // ---------------------------------------------------------------- /cm sequence

    private static LiteralArgumentBuilder<CommandSourceStack> sequence(String root) {
        var name = argument("name", StringArgumentType.string()).suggests(SEQUENCES);
        return literal(root).requires(Permissions.ADMIN)
                .executes(TimelineCommands::list)
                .then(literal("liste").executes(TimelineCommands::list))
                .then(literal("editer").executes(c -> edit(c, null))
                        .then(argument("name", StringArgumentType.string()).suggests(SEQUENCES).executes(c -> edit(c, str(c, "name")))))
                .then(literal("creer").then(argument("name", StringArgumentType.string())
                        .executes(c -> say(c, Sequences.create(str(c, "name"), 10)))
                        .then(argument("seconds", DoubleArgumentType.doubleArg(1, 3600))
                                .executes(c -> say(c, Sequences.create(str(c, "name"), DoubleArgumentType.getDouble(c, "seconds")))))))
                .then(literal("lancer").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .executes(c -> say(c, Sequencer.play(c.getSource().getServer(), str(c, "name"), 0)))
                        .then(argument("from", DoubleArgumentType.doubleArg(0, 3600))
                                .executes(c -> say(c, Sequencer.play(c.getSource().getServer(), str(c, "name"), DoubleArgumentType.getDouble(c, "from")))))))
                .then(literal("stop").executes(c -> say(c, Sequencer.stop(null)))
                        .then(argument("name", StringArgumentType.string()).suggests(SEQUENCES).executes(c -> say(c, Sequencer.stop(str(c, "name"))))))
                .then(literal("statut").executes(TimelineCommands::status))
                .then(literal("supprimer").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .executes(c -> say(c, Sequences.delete(str(c, "name"))))))
                .then(literal("dupliquer").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .then(argument("copy", StringArgumentType.string()).executes(c -> say(c, Sequences.duplicate(str(c, "name"), str(c, "copy")))))))
                .then(literal("renommer").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .then(argument("to", StringArgumentType.string()).executes(c -> say(c, Sequences.rename(str(c, "name"), str(c, "to")))))))
                .then(literal("miroir").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .executes(c -> say(c, Sequences.mirror(c.getSource().getServer(), str(c, "name"))))))
                .then(literal("inverser").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .executes(c -> say(c, Sequences.reverse(str(c, "name"))))))
                .then(literal("etirer").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .then(argument("factor", DoubleArgumentType.doubleArg(0.1, 10))
                                .executes(c -> say(c, Sequences.stretch(str(c, "name"), DoubleArgumentType.getDouble(c, "factor")))))))
                .then(literal("boucle").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .then(argument("on", BoolArgumentType.bool()).executes(c -> say(c, Sequences.loop(str(c, "name"), BoolArgumentType.getBool(c, "on")))))))
                .then(literal("importer").executes(c -> say(c, Sequences.importAll()))
                        .then(argument("name", StringArgumentType.string()).suggests(SEQUENCES).executes(c -> say(c, Sequences.importFile(str(c, "name"))))))
                .then(literal("exporter").then(argument("name", StringArgumentType.string()).suggests(SEQUENCES)
                        .executes(c -> say(c, Sequences.exportFile(str(c, "name"))))));
    }

    private static int edit(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return say(c, "Rien : l'éditeur s'ouvre en jeu.");
        if (name != null && !Sequencer.exists(name)) Sequences.create(name, 10);
        return TimelineNetwork.open(player, name) ? 1 : say(c, "Rien : il faut le mod CraftMaster côté client pour l'éditeur.");
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Map<String, Sequence> all = Sequences.all();
        if (all.isEmpty()) return say(c, "Aucune séquence. Crée-en une : /cm sequence editer <nom>.");
        Map<String, Double> playing = Sequencer.playing(c.getSource().getServer());
        MutableComponent out = Component.literal("Séquences :").withStyle(ChatFormatting.GOLD);
        for (Map.Entry<String, Sequence> e : all.entrySet()) {
            String q = StageCommands.quoted(e.getKey());
            Sequence s = e.getValue();
            out.append(Component.literal("\n ▶ ").withStyle(st -> st.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/cm sequence lancer " + q))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Lancer")))));
            out.append(Component.literal(e.getKey()).withStyle(st -> st.withColor(playing.containsKey(e.getKey()) ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                    .withClickEvent(new ClickEvent.RunCommand("/cm sequence editer " + q))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Ouvrir l'éditeur" + (s.description.isEmpty() ? "" : "\n" + s.description))))));
            out.append(Component.literal("  " + Sequence.num(s.duree) + " s, " + s.pistes.size() + " piste(s), " + s.keyCount() + " keypoint(s)"
                    + (s.boucle ? ", boucle" : "") + (playing.containsKey(e.getKey()) ? " — joue" : "")).withStyle(ChatFormatting.GRAY));
        }
        c.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        Map<String, Double> playing = Sequencer.playing(c.getSource().getServer());
        if (playing.isEmpty()) return say(c, "Aucune séquence en cours.");
        StringBuilder b = new StringBuilder("En cours :");
        playing.forEach((n, t) -> b.append(" « ").append(n).append(" » à ").append(Sequence.num(Math.round(t * 10) / 10.0)).append(" s ;"));
        return say(c, b.substring(0, b.length() - 1) + ".");
    }

    // ---------------------------------------------------------------- /cm plateau

    private static LiteralArgumentBuilder<CommandSourceStack> plateau(String root) {
        return literal(root).requires(Permissions.ADMIN)
                .executes(TimelineCommands::plateauInfo)
                .then(literal("devant").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return say(c, "Rien : place-toi dans le public, regarde la scène, et tape /cm plateau devant.");
                    // Rounded to a quarter turn when close: stages are usually square to the world
                    float yaw = net.minecraft.util.Mth.wrapDegrees(p.getYRot());
                    float snapped = Math.round(yaw / 90f) * 90f;
                    Scenography.book().plateau.devant = Math.abs(yaw - snapped) < 12 ? snapped : yaw;
                    Scenography.save();
                    return say(c, "Le public regarde la scène vers " + Math.round(Scenography.book().plateau.devant) + "° : gauche et droite sont les siennes (miroirs, vagues).");
                }))
                .then(literal("point").then(argument("name", StringArgumentType.string()).suggests(POINTS).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return say(c, "Rien : en jeu, place-toi à l'endroit voulu.");
                    String k = Scenography.key(str(c, "name"));
                    Scenography.book().plateau.points.put(k, new Look.Point(Busts.worldId(p.level()), Math.round(p.getX() * 100) / 100.0,
                            Math.round((p.getY() + 1) * 100) / 100.0, Math.round(p.getZ() * 100) / 100.0));
                    Scenography.save();
                    return say(c, "Point « " + k + " » ici : les projecteurs peuvent le viser (@point:" + k + ").");
                })))
                .then(literal("retirer").then(argument("name", StringArgumentType.string()).suggests(POINTS).executes(c -> {
                    String k = Scenography.key(str(c, "name"));
                    if (Scenography.book().plateau.points.remove(k) == null) return say(c, "Point « " + k + " » inconnu.");
                    Scenography.save();
                    return say(c, "Point « " + k + " » retiré.");
                })))
                .then(literal("maitre").then(argument("player", StringArgumentType.word()).executes(c -> {
                    Scenography.book().plateau.maitre = str(c, "player");
                    Scenography.save();
                    return say(c, "Le CraftMaster (@maitre) est " + str(c, "player") + ".");
                })))
                .then(literal("scene").then(argument("zone", StringArgumentType.word()).executes(c -> {
                    Scenography.book().plateau.scene = fr.lordfinn.craftmaster.trial.Zone.normalizeName(str(c, "zone"));
                    Scenography.save();
                    return say(c, "La scène est la zone « " + Scenography.book().plateau.scene + " ».");
                })))
                .then(literal("public").then(argument("zone", StringArgumentType.word()).executes(c -> {
                    Scenography.book().plateau.publicZone = fr.lordfinn.craftmaster.trial.Zone.normalizeName(str(c, "zone"));
                    Scenography.save();
                    return say(c, "Le public est la zone « " + Scenography.book().plateau.publicZone + " ».");
                })));
    }

    private static int plateauInfo(CommandContext<CommandSourceStack> c) {
        Look.Plateau p = Scenography.book().plateau;
        var zones = fr.lordfinn.craftmaster.trial.TrialZones.zones();
        String msg = "Plateau : scène = zone « " + p.scene + " »" + (zones.containsKey(p.scene) ? "" : " (à créer : /cm epreuve zone creer " + p.scene + ")")
                + ", public = zone « " + p.publicZone + " »" + (zones.containsKey(p.publicZone) ? "" : " (facultative)")
                + ", le public regarde vers " + (p.devant == null ? "? (/cm plateau devant)" : Math.round(p.devant) + "°")
                + ", CraftMaster : " + fr.lordfinn.craftmaster.studio.SpotlightFollow.master()
                + ", points : " + (p.points.isEmpty() ? "aucun (/cm plateau point <nom>)" : String.join(", ", p.points.keySet())) + ".";
        return say(c, msg);
    }
}
