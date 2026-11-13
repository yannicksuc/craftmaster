package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionJournal;
import fr.lordfinn.craftmaster.show.Moderators;
import fr.lordfinn.craftmaster.show.Rights;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The show's moderators (docs/wiki/RegieModos.md), organisers only:
 * <pre>
 * /craftMasterModo liste                                   (/modo, /cfmo)
 * /craftMasterModo ajouter &lt;twitch&gt; [minecraft|-] [droits]   droits: camera,tv,replay,jeu (default camera,tv,replay)
 * /craftMasterModo retirer &lt;nom&gt;
 * /craftMasterModo pause · reprendre                       stops / lets back every moderator at once
 * /craftMasterModo journal [n]                             who did what (the last n lists, 10 by default)
 * </pre>
 */
public final class ModoCommands {

    private ModoCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("craftMasterModo", "craftmastermodo", "modo", "cfmo")) d.register(tree(n));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(ModoCommands::list)
                .then(literal("liste").executes(ModoCommands::list))
                .then(literal("ajouter").then(argument("twitch", StringArgumentType.word())
                        .executes(c -> add(c, "-", ""))
                        .then(argument("minecraft", StringArgumentType.word())
                                .executes(c -> add(c, StringArgumentType.getString(c, "minecraft"), ""))
                                .then(argument("droits", StringArgumentType.greedyString())
                                        .suggests((c, b) -> {
                                            for (String s : List.of("camera,tv,replay", "camera,tv,replay,jeu", "camera", "jeu")) b.suggest(s);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> add(c, StringArgumentType.getString(c, "minecraft"), StringArgumentType.getString(c, "droits")))))))
                .then(literal("retirer").then(argument("nom", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (Moderators.Entry e : Moderators.list()) b.suggest(e.twitch.isEmpty() ? e.minecraft : e.twitch);
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "nom");
                            boolean done = Moderators.remove(n);
                            return say(c, done ? "Modo retiré : " + n : "Aucun modo « " + n + " ».", done);
                        })))
                .then(literal("pause").executes(c -> {
                    Moderators.setPaused(true);
                    return say(c, "Régies des modos coupées (tu gardes tout). /modo reprendre pour les rendre.", true);
                }))
                .then(literal("reprendre").executes(c -> {
                    Moderators.setPaused(false);
                    return say(c, "Régies des modos rendues.", true);
                }))
                .then(literal("site")
                        .executes(c -> say(c, fr.lordfinn.craftmaster.regie.SiteLink.describe(), true))
                        .then(literal("etat").executes(c -> say(c, fr.lordfinn.craftmaster.regie.SiteLink.describe(), true)))
                        .then(literal("on").executes(c -> {
                            fr.lordfinn.craftmaster.regie.SiteLink.setEnabled(true);
                            return say(c, "Lien avec le site activé.", true);
                        }))
                        .then(literal("off").executes(c -> {
                            fr.lordfinn.craftmaster.regie.SiteLink.setEnabled(false);
                            return say(c, "Lien avec le site coupé : la page des modos ne peut plus rien faire.", true);
                        }))
                        .then(literal("lier")
                                .executes(c -> pair(c, ""))
                                .then(argument("adresse", StringArgumentType.greedyString()).executes(c -> pair(c, StringArgumentType.getString(c, "adresse"))))))
                .then(literal("journal")
                        .executes(c -> journal(c, 10))
                        .then(argument("n", IntegerArgumentType.integer(1, 100)).executes(c -> journal(c, IntegerArgumentType.getInteger(c, "n")))));
    }

    private static int add(CommandContext<CommandSourceStack> c, String minecraft, String rightsText) {
        String twitch = StringArgumentType.getString(c, "twitch");
        if (twitch.equals("-")) twitch = "";
        if (minecraft.equals("-")) minecraft = "";
        if (twitch.isEmpty() && minecraft.isEmpty()) return say(c, "Donne au moins un pseudo Twitch ou Minecraft.", false);
        Set<Rights.Right> rights = EnumSet.noneOf(Rights.Right.class);
        if (rightsText.isBlank()) rights.addAll(Rights.MODO);
        else for (String part : rightsText.split("[,\\s]+")) {
            if (part.isBlank()) continue;
            Rights.Right r = Rights.Right.byId(part);
            if (r == null || r == Rights.Right.ADMIN)
                return say(c, "Droit inconnu « " + part + " » (camera, tv, replay, jeu).", false);
            rights.add(r);
        }
        Moderators.Entry e = Moderators.put(twitch, minecraft, rights);
        return say(c, "Modo " + e.label() + " : " + String.join(", ", e.rights) + ".", true);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<Moderators.Entry> all = Moderators.list();
        if (all.isEmpty()) return say(c, "Aucun modo. /modo ajouter <twitch> [minecraft|-] [camera,tv,replay,jeu]", true);
        say(c, all.size() + " modo(s)" + (Moderators.paused() ? " — EN PAUSE" : "") + " :", true);
        for (Moderators.Entry e : all)
            c.getSource().sendSuccess(() -> Component.literal(" · " + (e.twitch.isEmpty() ? "-" : e.twitch) + " / "
                    + (e.minecraft.isEmpty() ? "-" : e.minecraft) + " : " + String.join(", ", e.rights)).withStyle(ChatFormatting.GRAY), false);
        return all.size();
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static int journal(CommandContext<CommandSourceStack> c, int n) {
        List<ActionJournal.Line> lines = ActionJournal.last(n);
        if (lines.isEmpty()) return say(c, "Journal vide.", true);
        for (int i = lines.size() - 1; i >= 0; i--) {
            ActionJournal.Line l = lines.get(i);
            String text = CLOCK.format(Instant.ofEpochMilli(l.at())) + " " + l.actor() + " (" + l.origin() + ") " + String.join(" ; ", l.lines());
            c.getSource().sendSuccess(() -> Component.literal(text).withStyle(l.ok() ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        return lines.size();
    }

    /** New secret, shown once, click to copy: it goes in the site's .env (CRAFTMASTER_LINK_SECRET). */
    private static int pair(CommandContext<CommandSourceStack> c, String url) {
        String secret = fr.lordfinn.craftmaster.regie.SiteLink.pair(url);
        c.getSource().sendSuccess(() -> Component.literal("Nouveau secret du lien (clique pour copier, à mettre dans le .env du site : CRAFTMASTER_LINK_SECRET) :")
                .withStyle(ChatFormatting.GREEN), false);
        c.getSource().sendSuccess(() -> Component.literal(secret).withStyle(s -> s.withColor(ChatFormatting.GOLD)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(secret))), false);
        c.getSource().sendSuccess(() -> Component.literal(fr.lordfinn.craftmaster.regie.SiteLink.describe()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int say(CommandContext<CommandSourceStack> c, String text, boolean ok) {
        if (ok) c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN), false);
        else c.getSource().sendFailure(Component.literal(text));
        return ok ? 1 : 0;
    }
}
