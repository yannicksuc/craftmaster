package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.replay.Replays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Replays (Server Replay, optional): {@code /craftMasterReplay} (alias {@code /cmreplay}). Organisers only.
 * <ul>
 *   <li>{@code /cmreplay} — status: Server Replay found, mode, what is being recorded</li>
 *   <li>{@code /cmreplay mode manche|partie|off} — automatic recording of each round, each game, or never</li>
 *   <li>{@code /cmreplay start} / {@code stop} — by hand (the current round, else the active game)</li>
 *   <li>{@code /cmreplay joueurs on|off}, {@code zone on|off}, {@code marge <chunks>} — what is recorded</li>
 *   <li>{@code /cmreplay repere <texte>} — a marker on the timeline of the files being recorded</li>
 * </ul>
 * Reference: docs/wiki/Replays.md.
 */
public final class ReplayCommands {

    private ReplayCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, ReplayCommands::tree, "craftMasterReplay", "cmreplay");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(ReplayCommands::status)
                .then(literal("status").executes(ReplayCommands::status))
                .then(literal("start").executes(c -> say(c, Replays.startNow(c.getSource().getServer()))))
                .then(literal("stop").executes(c -> say(c, Replays.stopNow(c.getSource().getServer()))))
                .then(literal("mode")
                        .then(literal(Replays.MANCHE).executes(c -> mode(c, Replays.MANCHE)))
                        .then(literal(Replays.PARTIE).executes(c -> mode(c, Replays.PARTIE)))
                        .then(literal(Replays.OFF).executes(c -> mode(c, Replays.OFF))))
                .then(literal("joueurs").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    Replays.settings().players = BoolArgumentType.getBool(c, "on");
                    Replays.save();
                    return say(c, "Un fichier par candidat : " + (Replays.settings().players ? "oui" : "non") + " (dès le prochain enregistrement).");
                })))
                .then(literal("zone").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    Replays.settings().zone = BoolArgumentType.getBool(c, "on");
                    Replays.save();
                    return say(c, "Un fichier pour la zone de l'épreuve : " + (Replays.settings().zone ? "oui" : "non") + " (dès le prochain enregistrement).");
                })))
                .then(literal("marge").then(argument("chunks", IntegerArgumentType.integer(0, 8)).executes(c -> {
                    Replays.settings().margin = IntegerArgumentType.getInteger(c, "chunks");
                    Replays.save();
                    return say(c, "Marge autour de la zone : " + Replays.settings().margin + " chunk(s).");
                })))
                .then(literal("repere").then(argument("texte", StringArgumentType.greedyString()).executes(c -> {
                    if (!Replays.recording()) return say(c, "Aucun enregistrement en cours.");
                    Replays.marker(StringArgumentType.getString(c, "texte"), 0xFFFFFF);
                    return say(c, "Repère posé.");
                })));
    }

    private static int mode(CommandContext<CommandSourceStack> c, String mode) {
        Replays.settings().mode = mode;
        Replays.save();
        return say(c, switch (mode) {
            case Replays.MANCHE -> "Chaque manche sera enregistrée, de son lancement à sa fin.";
            case Replays.PARTIE -> "Chaque partie sera enregistrée, de sa première manche jusqu'à la partie suivante ou terminée.";
            default -> "Plus d'enregistrement automatique (/cmreplay start pour lancer à la main).";
        } + (Replays.available() ? "" : " (Server Replay n'est pas installé : rien ne sera enregistré.)"));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        for (String line : Replays.status())
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
