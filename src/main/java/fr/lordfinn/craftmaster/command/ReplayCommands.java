package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.replay.ReplayViewing;
import fr.lordfinn.craftmaster.replay.Replays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

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
 *   <li>{@code /cmreplay disque}, {@code garder <jours>}, {@code max <Go>}, {@code alerte <Go>}, {@code purge <jours>} —
 *   the disk: used and free space, automatic purge, warning, deleting old replays now</li>
 *   <li>{@code /cmreplay voir [n [zone|joueur]]} — the recordings, newest first; watch one (Server Replay's viewer)</li>
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
                .then(literal("format")
                        .then(literal("flashback").executes(c -> format(c, "flashback")))
                        .then(literal("replaymod").executes(c -> format(c, "replaymod")))
                        .then(literal("serveur").executes(c -> format(c, ""))))
                .then(literal("morceaux").then(argument("zone", IntegerArgumentType.integer(10, 300))
                        .then(argument("joueurs", IntegerArgumentType.integer(10, 300)).executes(c -> {
                            Replays.settings().zonePieceSeconds = IntegerArgumentType.getInteger(c, "zone");
                            Replays.settings().playerPieceSeconds = IntegerArgumentType.getInteger(c, "joueurs");
                            Replays.save();
                            return say(c, "Morceaux : zone " + Replays.settings().zonePieceSeconds + " s, joueurs " + Replays.settings().playerPieceSeconds
                                    + " s (au plus ça de perdu après un crash ; plus court = plus lourd).");
                        }))))
                .then(literal("realisation").then(argument("joueur", StringArgumentType.word())
                        .then(argument("action", StringArgumentType.greedyString()).executes(c -> {
                            // A direction order for the replay this player watches (JSON, as the voice control's actions)
                            java.util.Map<String, Object> action;
                            try {
                                action = new com.google.gson.Gson().fromJson(StringArgumentType.getString(c, "action"),
                                        new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>() {}.getType());
                            } catch (RuntimeException e) {
                                return say(c, "Action illisible (JSON attendu, ex. {\"type\":\"camera.cut\",\"camera\":\"2\"}).");
                            }
                            // Not a player selector: someone watching a replay is off the server's player list
                            var p = fr.lordfinn.craftmaster.replay.ReplayViewing.watcher(c.getSource().getServer(), StringArgumentType.getString(c, "joueur"));
                            if (p == null) return say(c, StringArgumentType.getString(c, "joueur") + " ne regarde aucun replay.");
                            return say(c, fr.lordfinn.craftmaster.replay.ReplayViewing.directFor(p, action)
                                    ? "Envoyé au replay de " + p.getGameProfile().name() + "." : p.getGameProfile().name() + " ne regarde aucun replay.");
                        }))))
                .then(literal("disque").executes(c -> {
                    for (String line : fr.lordfinn.craftmaster.replay.ReplayDisk.report())
                        c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))
                .then(literal("garder").then(argument("jours", IntegerArgumentType.integer(0, 3650)).executes(c -> {
                    Replays.settings().keepDays = IntegerArgumentType.getInteger(c, "jours");
                    Replays.save();
                    return say(c, Replays.settings().keepDays == 0 ? "Les replays sont gardés sans limite d'âge."
                            : "Les replays de plus de " + Replays.settings().keepDays + " jour(s) seront supprimés (après chaque enregistrement et au démarrage).");
                })))
                .then(literal("max").then(argument("go", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100_000)).executes(c -> {
                    Replays.settings().maxGb = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "go");
                    Replays.save();
                    return say(c, Replays.settings().maxGb == 0 ? "Pas de plafond pour les replays."
                            : "Les replays ne dépasseront pas " + Replays.settings().maxGb + " Go : les plus anciens partent d'abord.");
                })))
                .then(literal("alerte").then(argument("go", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100_000)).executes(c -> {
                    Replays.settings().minFreeGb = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "go");
                    Replays.save();
                    return say(c, "Alerte quand il reste moins de " + Replays.settings().minFreeGb + " Go libres.");
                })))
                .then(literal("purge").then(argument("jours", IntegerArgumentType.integer(1, 3650)).executes(c -> {
                    int n = fr.lordfinn.craftmaster.replay.ReplayDisk.purge(IntegerArgumentType.getInteger(c, "jours"), 0);
                    return say(c, n + " replay(s) de plus de " + IntegerArgumentType.getInteger(c, "jours") + " jour(s) supprimé(s).");
                })))
                .then(literal("marge").then(argument("chunks", IntegerArgumentType.integer(0, 8)).executes(c -> {
                    Replays.settings().margin = IntegerArgumentType.getInteger(c, "chunks");
                    Replays.save();
                    return say(c, "Marge autour de la zone : " + Replays.settings().margin + " chunk(s).");
                })))
                .then(literal("voir").executes(ReplayCommands::list)
                        .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> watch(c, null))
                                .then(argument("vue", StringArgumentType.greedyString()).executes(c -> watch(c, StringArgumentType.getString(c, "vue"))))))
                .then(literal("repere").then(argument("texte", StringArgumentType.greedyString()).executes(c -> {
                    if (!Replays.recording()) return say(c, "Aucun enregistrement en cours.");
                    Replays.marker(StringArgumentType.getString(c, "texte"), 0xFFFFFF);
                    return say(c, "Repère posé.");
                })));
    }

    private static int watch(CommandContext<CommandSourceStack> c, String view) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String msg = ReplayViewing.watch(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"), view);
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(msg.startsWith("Replay :") ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    /** The recordings of the index, newest first, each with a link per file (zone, players). */
    private static int list(CommandContext<CommandSourceStack> c) {
        List<ReplayViewing.Entry> all = ReplayViewing.entries();
        if (all.isEmpty()) return say(c, Replays.available() ? "Aucun enregistrement dans l'index." : "Server Replay n'est pas installé sur ce serveur.");
        c.getSource().sendSuccess(() -> Component.literal("Replays (clic pour regarder, la barre de lecture en bas ; F8 : la réalisation) :").withStyle(ChatFormatting.GOLD), false);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (int i = 0; i < Math.min(all.size(), 15); i++) {
            ReplayViewing.Entry e = all.get(i);
            int n = i + 1;
            MutableComponent line = Component.literal(n + ". ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(e.label()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" · " + fmt.format(e.started()) + " · " + e.seconds() / 60 + ":" + String.format("%02d", e.seconds() % 60) + " ").withStyle(ChatFormatting.GRAY));
            for (ReplayViewing.View v : e.views()) {
                String cmd = "/craftMasterReplay voir " + n + " " + v.name();
                line.append(Component.literal("[" + v.name() + "] ").withStyle(s -> s.withColor(v.name().equals("zone") ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))));
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int format(CommandContext<CommandSourceStack> c, String f) {
        Replays.settings().format = f;
        Replays.save();
        fr.lordfinn.craftmaster.replay.ServerReplayBridge.setFormat(f);
        return say(c, "Format des prochains enregistrements : " + (f.isEmpty() ? "celui de Server Replay (default_encoding)" : f) + ".");
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
