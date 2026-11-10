package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.task.Note;
import fr.lordfinn.craftmaster.task.Notes;
import fr.lordfinn.craftmaster.task.TaskContent;
import fr.lordfinn.craftmaster.task.TaskStore;
import fr.lordfinn.craftmaster.task.Tasks;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Tasks, envelopes and hidden notes (chantier D). {@code /craftMasterTask} (alias {@code /tache}) shows the task
 * of the moment to anyone; its sub-commands are for the organisers and go through {@link ActionExecutor} like the
 * voice control. {@code /craftMasterNote} manages the hidden notes. Reference: docs/wiki/Taches.md.
 */
public final class TaskCommands {

    private TaskCommands() {}

    private static final Predicate<CommandSourceStack> ADMIN = Permissions.ADMIN;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, TaskCommands::task, "craftMasterTask", "tache");
        d.register(task("cftk"));
        CraftMasterCommands.add(d, TaskCommands::note, "craftMasterNote", "cfno");
    }

    private static final SuggestionProvider<CommandSourceStack> MANCHES = list(c -> {
        Season s = CraftMaster.data().activeSeason();
        Partie p = s == null ? null : s.activePartie();
        return p == null ? List.of() : p.activeManches().stream().map(r -> r.name).toList();
    });
    private static final SuggestionProvider<CommandSourceStack> MANCHES_OR_CURRENT = list(c -> {
        List<String> out = new ArrayList<>(List.of("current"));
        Season s = CraftMaster.data().activeSeason();
        Partie p = s == null ? null : s.activePartie();
        if (p != null) p.activeManches().forEach(r -> out.add(r.name));
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> WAXES = list(c -> Arrays.stream(WaxColor.values()).map(w -> w.french).toList());
    private static final SuggestionProvider<CommandSourceStack> NOTES = list(c ->
            Tasks.store() == null ? List.of() : List.copyOf(Tasks.store().notes().keySet()));
    private static final SuggestionProvider<CommandSourceStack> CAST_OR_ALL = list(c -> {
        List<String> out = new ArrayList<>(List.of("tous"));
        Season s = CraftMaster.data().activeSeason();
        if (s != null) s.players.values().stream().filter(p -> !p.archived).forEach(p -> out.add(p.name));
        return out;
    });

    @SafeVarargs
    private static int run(CommandContext<CommandSourceStack> c, Map<String, Object>... actions) {
        return ActionExecutor.execute(c.getSource(), List.of(actions));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return c.getArgument(name, String.class);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) fail(c, "Cette commande est réservée aux joueurs.");
        return p;
    }

    private static void info(CommandContext<CommandSourceStack> c, Component line) {
        c.getSource().sendSystemMessage(line);
    }

    private static void info(CommandContext<CommandSourceStack> c, String line) {
        info(c, Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- /craftMasterTask

    private static LiteralArgumentBuilder<CommandSourceStack> task(String name) {
        return literal(name)
                // Anyone: the task of the moment (secret = "encore secrète")
                .executes(TaskCommands::showCurrent)
                .then(literal("list").requires(ADMIN).executes(TaskCommands::listTasks))
                .then(literal("edit").requires(ADMIN)
                        .executes(c -> editor(c, 0, null))
                        .then(argument("manche", word()).suggests(MANCHES).executes(c -> editor(c, 0, str(c, "manche")))))
                .then(literal("seal").requires(ADMIN).executes(c -> editor(c, 1, null)))
                .then(literal("view").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES).executes(c -> view(c, false))))
                // The letter played on the organiser's screen only (seal, opening): nothing is revealed
                .then(literal("apercu").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES).executes(c -> view(c, true))))
                .then(literal("preview").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES).executes(c -> view(c, true))))
                .then(literal("set").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES)
                        .then(literal("title").then(argument("text", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "title", str(c, "text"))))))
                        .then(literal("text").then(argument("text", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "text", str(c, "text"))))))
                        .then(literal("append").then(argument("text", StringArgumentType.greedyString())
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "text", str(c, "text"), "append", true)))))
                        .then(literal("rules")
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "rules", List.of())))
                                .then(argument("rules", StringArgumentType.greedyString())
                                        .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "rules", str(c, "rules"))))))
                        .then(literal("seconds").then(argument("seconds", integer(0, 36000))
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "seconds", getInteger(c, "seconds"))))))
                        .then(literal("wax").then(argument("wax", word()).suggests(WAXES)
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "wax", str(c, "wax"))))))
                        .then(literal("revealOnStart").then(argument("value", BoolArgumentType.bool())
                                .executes(c -> run(c, action("task.set", "manche", str(c, "manche"), "revealOnStart", BoolArgumentType.getBool(c, "value"))))))))
                .then(literal("clear").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES)
                        .executes(c -> run(c, action("task.clear", "manche", str(c, "manche"))))))
                .then(literal("reveal").requires(ADMIN)
                        .executes(c -> run(c, action("task.reveal")))
                        .then(argument("manche", word()).suggests(MANCHES_OR_CURRENT).executes(c -> run(c, action("task.reveal", "manche", str(c, "manche"))))))
                // The next [secret] part of the text ("révèle la suite")
                .then(literal("suite").requires(ADMIN)
                        .executes(c -> run(c, action("task.reveal_more")))
                        .then(argument("manche", word()).suggests(MANCHES_OR_CURRENT).executes(c -> run(c, action("task.reveal_more", "manche", str(c, "manche"))))))
                .then(literal("hide").requires(ADMIN)
                        .executes(c -> run(c, action("task.hide")))
                        .then(argument("manche", word()).suggests(MANCHES_OR_CURRENT).executes(c -> run(c, action("task.hide", "manche", str(c, "manche"))))))
                .then(literal("show").requires(ADMIN)
                        .executes(c -> run(c, action("task.show")))
                        .then(argument("manche", word()).suggests(MANCHES_OR_CURRENT)
                                .executes(c -> run(c, action("task.show", "manche", str(c, "manche"))))
                                .then(argument("seconds", integer(1, 120))
                                        .executes(c -> run(c, action("task.show", "manche", str(c, "manche"), "seconds", getInteger(c, "seconds")))))))
                .then(literal("envelope").requires(ADMIN).then(argument("manche", word()).suggests(MANCHES_OR_CURRENT)
                        .executes(c -> run(c, action("envelope.give", "manche", str(c, "manche"))))
                        .then(argument("players", StringArgumentType.greedyString()).suggests(CAST_OR_ALL)
                                .executes(c -> run(c, action("envelope.give", "manche", str(c, "manche"),
                                        "players", Arrays.stream(str(c, "players").split("[,\\s]+")).filter(w -> !w.isBlank()).toList()))))))
                .then(literal("close").requires(ADMIN)
                        .executes(c -> close(c, c.getSource().getServer().getPlayerList().getPlayers()))
                        .then(argument("players", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(c -> close(c, net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "players")))))
                .then(literal("resetReveals").requires(ADMIN).executes(TaskCommands::resetReveals))
                .then(literal("tools").requires(ADMIN).executes(TaskCommands::tools));
    }

    private static int showCurrent(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p != null) {
            Tasks.openCurrent(p);
            return 1;
        }
        return listTasks(c);
    }

    private static int editor(CommandContext<CommandSourceStack> c, int mode, String manche) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        String id = null;
        if (manche != null) {
            Tasks.Located l = Tasks.locateActive(manche);
            if (l == null) {
                fail(c, "Manche « " + manche + " » introuvable dans la saison active.");
                return 0;
            }
            id = l.manche().id;
        }
        Tasks.openEditor(p, mode, id);
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> c, boolean preview) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Tasks.Located l = Tasks.locateActive(str(c, "manche"));
        if (l == null || !l.hasTask()) {
            fail(c, l == null ? "Manche « " + str(c, "manche") + " » introuvable." : "« " + l.manche().name + " » n'a pas de tâche écrite.");
            return 0;
        }
        if (preview) {
            Tasks.preview(p, l);
            info(c, "Aperçu de la lettre de « " + l.manche().name + " » (sur ton écran seulement, rien n'est révélé).");
        } else {
            Tasks.openFor(p, l);
        }
        return 1;
    }

    private static int listTasks(CommandContext<CommandSourceStack> c) {
        Season s = CraftMaster.data().activeSeason();
        Partie p = s == null ? null : s.activePartie();
        if (p == null) {
            fail(c, "Aucune manche dans la saison active.");
            return 0;
        }
        info(c, Component.literal("— Tâches · " + s.name + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Manche m : p.activeManches()) {
            Tasks.Located l = new Tasks.Located(s, p, m);
            String state;
            if (!l.hasTask()) state = "pas de tâche";
            else {
                TaskStore.Reveal r = Tasks.store().reveal(s.id, m.id);
                state = (m.task.title.isBlank() ? "(sans titre)" : "« " + m.task.title + " »") + " · cire " + WaxColor.of(m.task.wax).french
                        + (r == null ? " · secrète" : " · révélée à " + TIME.format(Instant.ofEpochMilli(r.at())) + (r.by() == null ? "" : " par " + r.by()));
            }
            info(c, Component.literal(p.number(m) + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(m.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + state).withStyle(l.hasTask() && Tasks.isRevealed(l) ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
        return 1;
    }

    /** Closes the cards being read (everybody looks up). */
    private static int close(CommandContext<CommandSourceStack> c, java.util.Collection<ServerPlayer> players) {
        int n = 0;
        for (ServerPlayer p : players) {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, fr.lordfinn.craftmaster.task.TaskNetwork.CloseCard.TYPE)) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new fr.lordfinn.craftmaster.task.TaskNetwork.CloseCard());
            n++;
        }
        CraftMasterCommands.ok(c, "Cartes fermées chez " + n + " joueur(s).");
        return n;
    }

    private static int resetReveals(CommandContext<CommandSourceStack> c) {
        Season s = CraftMaster.data().activeSeason();
        if (s == null) {
            fail(c, "Aucune saison active.");
            return 0;
        }
        int n = Tasks.store().hideAll(s.id);
        Tasks.sync(c.getSource().getServer());
        CraftMasterCommands.ok(c, n + " tâche(s) de " + s.name + " redevenue(s) secrète(s).");
        return 1;
    }

    private static int tools(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        ItemStack seal = new ItemStack(TaskContent.WAX_SEAL);
        fr.lordfinn.craftmaster.task.TaskComponents.setSealColor(seal, WaxColor.RED);
        p.getInventory().add(seal);
        p.getInventory().add(new ItemStack(TaskContent.UV_LAMP));
        CraftMasterCommands.ok(c, "Sceau à cire et lampe UV donnés.");
        return 1;
    }

    // ---------------------------------------------------------------- /craftMasterNote

    private static LiteralArgumentBuilder<CommandSourceStack> note(String name) {
        return literal(name).requires(ADMIN)
                .then(literal("list").executes(TaskCommands::listNotes))
                .then(literal("create")
                        .executes(c -> noteEditor(c, null))
                        .then(argument("title", StringArgumentType.greedyString()).executes(TaskCommands::createNote)))
                .then(literal("edit").then(argument("note", word()).suggests(NOTES).executes(c -> noteEditor(c, str(c, "note")))))
                .then(literal("info").then(argument("note", word()).suggests(NOTES).executes(TaskCommands::noteInfo)))
                .then(literal("text").then(argument("note", word()).suggests(NOTES)
                        .then(argument("text", StringArgumentType.greedyString()).executes(TaskCommands::noteText))))
                .then(literal("set").then(argument("note", word()).suggests(NOTES)
                        .then(literal("look").then(argument("value", word()).suggests(list(c -> Note.LOOKS)).executes(c -> noteSet(c, "look"))))
                        .then(literal("readers").then(argument("value", word()).suggests(list(c -> Note.READERS)).executes(c -> noteSet(c, "readers"))))
                        .then(literal("players").then(argument("value", StringArgumentType.greedyString()).executes(c -> noteSet(c, "players"))))
                        .then(literal("title").then(argument("value", StringArgumentType.greedyString()).executes(c -> noteSet(c, "title"))))
                        .then(literal("once").then(argument("value", word()).suggests(list(c -> List.of("true", "false"))).executes(c -> noteSet(c, "once"))))
                        .then(literal("ink").then(argument("value", word()).suggests(list(c -> List.of("true", "false"))).executes(c -> noteSet(c, "ink"))))
                        .then(literal("pieces").then(argument("value", word()).suggests(list(c -> List.of("1", "2", "3", "4", "5", "6"))).executes(c -> noteSet(c, "pieces"))))))
                .then(noteOrder("give")).then(noteOrder("bottle")).then(noteOrder("reset")).then(noteOrder("unhide")).then(noteOrder("delete"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> noteOrder(String order) {
        return literal(order).then(argument("note", word()).suggests(NOTES).executes(c -> {
            ServerPlayer p = player(c);
            if (p == null) return 0;
            Note n = Tasks.store().note(str(c, "note"));
            if (n == null) {
                fail(c, "Note « " + str(c, "note") + " » inconnue.");
                return 0;
            }
            Notes.order(p, n.id, order);
            return 1;
        }));
    }

    private static int noteEditor(CommandContext<CommandSourceStack> c, String id) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        if (id != null && Tasks.store().note(id) == null) {
            fail(c, "Note « " + id + " » inconnue.");
            return 0;
        }
        Notes.openEditor(p, id == null ? null : Tasks.store().note(id).id);
        return 1;
    }

    private static int createNote(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        Note n = Tasks.store().createNote(str(c, "title"), System.currentTimeMillis());
        Notes.order(p, n.id, "give");
        CraftMasterCommands.ok(c, "Note « " + n.title + " » créée (id " + n.id + ") : écris son texte avec /craftMasterNote text " + n.id
                + " <texte>, ou /craftMasterNote edit " + n.id + ".");
        return 1;
    }

    private static int noteText(CommandContext<CommandSourceStack> c) {
        Note n = Tasks.store().note(str(c, "note"));
        if (n == null) {
            fail(c, "Note « " + str(c, "note") + " » inconnue.");
            return 0;
        }
        n.text = fr.lordfinn.craftmaster.data.Task.cleanText(str(c, "text"));
        Tasks.store().changed();
        CraftMasterCommands.ok(c, "Texte de la note « " + n.title + " » enregistré (" + n.text.length() + " caractères).");
        return 1;
    }

    /** One setting of a note, as in its editor. */
    private static int noteSet(CommandContext<CommandSourceStack> c, String what) {
        Note n = Tasks.store().note(str(c, "note"));
        if (n == null) {
            fail(c, "Note « " + str(c, "note") + " » inconnue.");
            return 0;
        }
        String v = str(c, "value").strip();
        boolean yes = v.equalsIgnoreCase("true") || v.equalsIgnoreCase("oui") || v.equals("1");
        switch (what) {
            case "look" -> {
                if (!Note.LOOKS.contains(v)) { fail(c, "Aspect : " + String.join(", ", Note.LOOKS) + "."); return 0; }
                n.look = v;
            }
            case "readers" -> {
                if (!Note.READERS.contains(v)) { fail(c, "Lecteurs : all (tous), cast (candidats) ou players (liste)."); return 0; }
                n.readers = v;
            }
            case "players" -> {
                n.players = new ArrayList<>(Arrays.stream(v.split("[,\\s]+")).filter(x -> !x.isBlank()).toList());
                n.readers = Note.PLAYERS;
            }
            case "title" -> n.title = v.length() > 64 ? v.substring(0, 64) : v;
            case "once" -> n.once = yes;
            case "ink" -> n.ink = yes;
            case "pieces" -> {
                try {
                    n.pieces = Math.max(1, Math.min(Note.MAX_PIECES, Integer.parseInt(v)));
                } catch (NumberFormatException e) {
                    fail(c, "Morceaux : de 1 à " + Note.MAX_PIECES + ".");
                    return 0;
                }
            }
            default -> { return 0; }
        }
        Tasks.store().changed();
        CraftMasterCommands.ok(c, "Note « " + n.title + " » : " + what + " = " + v + ".");
        return 1;
    }

    private static int listNotes(CommandContext<CommandSourceStack> c) {
        Map<String, Note> notes = Tasks.store().notes();
        info(c, Component.literal("— Notes cachées —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (notes.isEmpty()) info(c, "Aucune note : /craftMasterNote create <titre>");
        for (Note n : notes.values()) {
            long taken = n.spots.stream().filter(s -> s.takenBy != null).count();
            String flags = (n.once ? " · prise unique" : "") + (n.ink ? " · encre invisible" : "") + (n.pieces > 1 ? " · " + n.pieces + " morceaux" : "")
                    + (n.readers.equals(Note.ALL) ? "" : n.readers.equals(Note.CAST) ? " · candidats" : " · " + String.join(", ", n.players));
            info(c, Component.literal(n.id + " ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("« " + n.title + " »").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + n.spots.size() + " cachette(s)" + (taken > 0 ? " (" + taken + " prise(s))" : "")
                            + ", trouvée " + n.finds.size() + " fois" + flags).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int noteInfo(CommandContext<CommandSourceStack> c) {
        Note n = Tasks.store().note(str(c, "note"));
        if (n == null) {
            fail(c, "Note « " + str(c, "note") + " » inconnue.");
            return 0;
        }
        var v = Notes.view(n);
        info(c, Component.literal("— « " + n.title + " » (" + n.id + ") —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        info(c, n.text.isEmpty() ? "(pas de texte)" : n.text.length() > 120 ? n.text.substring(0, 117) + "…" : n.text);
        info(c, Component.literal("Cachettes :").withStyle(ChatFormatting.YELLOW));
        if (v.spots().isEmpty()) info(c, "  aucune (prends la note en main et clic droit sur un bloc)");
        v.spots().forEach(s -> info(c, Component.literal("  • ").append(s).withStyle(ChatFormatting.GRAY)));
        info(c, Component.literal("Trouvée par :").withStyle(ChatFormatting.YELLOW));
        if (v.finds().isEmpty()) info(c, "  personne pour l'instant");
        v.finds().forEach(f -> info(c, Component.literal("  • ").append(f).withStyle(ChatFormatting.GRAY)));
        return 1;
    }
}
