package fr.lordfinn.craftmaster.media;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.StudioCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Commands of the screens' videos and the clips (reference: docs/wiki/Clips.md), permission craftmaster:image:
 * <ul>
 *   <li>{@code /cmtele liste}, {@code /cmtele nommer <nom>} (the screen you look at), {@code /cmtele <nom> clip
 *   [n|dernier]}, {@code lien <URL>}, {@code direct <joueur>}, {@code image <URL>}, {@code chat [twitch|jeu|tout]}
 *   (the chat wall, {@link ChatWall}), {@code off}.</li>
 *   <li>{@code /cmclip liste}, {@code /cmclip renommer <n> <nom>}, {@code /cmclip supprimer <n>}.</li>
 *   <li>{@code /cmchaine lier <joueur> <chaîne>}, {@code delier <joueur>}, {@code liste}: contestants' Twitch channels.</li>
 * </ul>
 */
public final class MediaCommands {

    private MediaCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SCREENS = (c, b) ->
            SharedSuggestionProvider.suggest(Stream.concat(MediaServer.screenNames(c.getSource().getServer()).stream(), Stream.of("tout")), b);
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (c, b) ->
            SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().name()), b);
    private static final SuggestionProvider<CommandSourceStack> CLIPS = (c, b) -> {
        List<String> s = new ArrayList<>(List.of("dernier"));
        for (int i = 1; i <= MediaServer.clips().list().size(); i++) s.add(String.valueOf(i));
        return SharedSuggestionProvider.suggest(s, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String n : List.of("cmtele", "craftMasterTele", "craftmastertele")) d.register(tele(n));
        for (String n : List.of("cmclip", "craftMasterClip", "craftmasterclip")) d.register(clip(n));
        for (String n : List.of("cmchaine", "craftMasterChaine", "craftmasterchaine")) d.register(chaine(n));
    }

    // ---------------------------------------------------------------- /cmtele

    private static LiteralArgumentBuilder<CommandSourceStack> tele(String name) {
        return literal(name).requires(Permissions.IMAGE)
                .then(literal("liste").executes(MediaCommands::listScreens))
                .then(literal("nommer").then(argument("nom", StringArgumentType.word()).executes(MediaCommands::nameScreen)))
                .then(argument("tele", StringArgumentType.word()).suggests(SCREENS)
                        .then(literal("clip").executes(c -> show(c, MediaServer.Kind.CLIP, "dernier"))
                                .then(argument("quoi", StringArgumentType.word()).suggests(CLIPS)
                                        .executes(c -> show(c, MediaServer.Kind.CLIP, StringArgumentType.getString(c, "quoi")))))
                        .then(literal("lien").then(argument("url", StringArgumentType.greedyString())
                                .executes(c -> show(c, MediaServer.Kind.LINK, StringArgumentType.getString(c, "url")))))
                        .then(literal("image").then(argument("url", StringArgumentType.greedyString())
                                .executes(c -> show(c, MediaServer.Kind.IMAGE, StringArgumentType.getString(c, "url")))))
                        .then(literal("direct").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                                .executes(c -> show(c, MediaServer.Kind.LIVE, StringArgumentType.getString(c, "joueur")))))
                        .then(literal("chat").executes(c -> show(c, MediaServer.Kind.CHAT, "tout"))
                                .then(argument("source", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("twitch", "jeu", "tout"), b))
                                        .executes(c -> show(c, MediaServer.Kind.CHAT, StringArgumentType.getString(c, "source")))))
                        .then(literal("off").executes(c -> show(c, MediaServer.Kind.OFF, ""))));
    }

    /**
     * Like the voice, the Stream Deck and the control room: a tele.show action through the action executor (one
     * path, the same effect). A refusal ("⚠ …") is shown in red.
     */
    private static int show(CommandContext<CommandSourceStack> c, MediaServer.Kind kind, String arg) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "tele.show");
        a.put("tele", StringArgumentType.getString(c, "tele"));
        switch (kind) {
            case CLIP -> a.put("clip", arg);
            case LINK -> a.put("link", arg);
            case LIVE -> a.put("live", arg);
            case IMAGE -> a.put("image", arg);
            case CHAT -> a.put("chat", arg);
            case OFF -> a.put("off", true);
        }
        fr.lordfinn.craftmaster.show.ActionExecutor.Result r = fr.lordfinn.craftmaster.show.ActionExecutor.execute(c.getSource().getServer(), List.of(a));
        boolean ok = r.ok();
        for (String line : r.lines()) {
            if (line.startsWith("⚠")) {
                ok = false;
                fail(c, line.substring(1).strip());
            } else if (r.ok()) ok(c, line);
            else fail(c, line);
        }
        return ok ? 1 : 0;
    }

    private static int listScreens(CommandContext<CommandSourceStack> c) {
        List<ImageScreenEntity> all = MediaServer.screens(c.getSource().getServer());
        if (all.isEmpty()) {
            fail(c, "Aucun écran du studio chargé.");
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Télés du studio :").withStyle(ChatFormatting.GOLD), false);
        for (ImageScreenEntity s : all) {
            String n = s.screenName().isEmpty() ? "(sans nom)" : s.screenName();
            String what = s.url().isEmpty() ? "mire" : s.url();
            var p = s.blockPosition();
            c.getSource().sendSuccess(() -> Component.literal(" • " + n + " ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(what + "  (" + p.getX() + " " + p.getY() + " " + p.getZ() + ")").withStyle(ChatFormatting.GRAY)), false);
        }
        return all.size();
    }

    private static int nameScreen(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        Optional<ImageScreenEntity> s = player == null ? Optional.empty() : StudioCommands.lookedScreen(player);
        if (s.isEmpty()) {
            fail(c, "Regarde un écran du studio pour le nommer.");
            return 0;
        }
        String n = StringArgumentType.getString(c, "nom").toLowerCase(Locale.ROOT);
        s.get().setScreenName(n);
        ok(c, "Cet écran s'appelle « " + n + " » : /cmtele " + n + " clip | lien <URL> | direct <joueur> | chat | off.");
        return 1;
    }

    // ---------------------------------------------------------------- /cmclip

    private static LiteralArgumentBuilder<CommandSourceStack> clip(String name) {
        return literal(name).requires(Permissions.IMAGE)
                .then(literal("liste").executes(MediaCommands::listClips))
                .then(literal("renommer").then(argument("quoi", StringArgumentType.word()).suggests(CLIPS)
                        .then(argument("nom", StringArgumentType.greedyString()).executes(c -> {
                            ClipStore.Clip cl = MediaServer.clip(StringArgumentType.getString(c, "quoi"));
                            if (cl == null || !MediaServer.clips().rename(cl.id, StringArgumentType.getString(c, "nom"))) {
                                fail(c, "Clip introuvable (/cmclip liste).");
                                return 0;
                            }
                            ok(c, "Clip renommé : « " + cl.name + " ».");
                            return 1;
                        }))))
                .then(literal("supprimer").then(argument("quoi", StringArgumentType.word()).suggests(CLIPS).executes(c -> {
                    ClipStore.Clip cl = MediaServer.clip(StringArgumentType.getString(c, "quoi"));
                    if (cl == null || !MediaServer.clips().delete(cl.id)) {
                        fail(c, "Clip introuvable (/cmclip liste).");
                        return 0;
                    }
                    ok(c, "Clip « " + cl.name + " » supprimé du serveur.");
                    return 1;
                })));
    }

    private static int listClips(CommandContext<CommandSourceStack> c) {
        List<ClipStore.Clip> all = MediaServer.clips().list();
        if (all.isEmpty()) {
            fail(c, "Aucun clip sur le serveur : F9 démarre un clip, F10 l'arrête.");
            return 0;
        }
        List<String> screens = MediaServer.screenNames(c.getSource().getServer());
        String first = screens.isEmpty() ? "<tele>" : screens.getFirst();
        c.getSource().sendSuccess(() -> Component.literal("Clips (" + all.size() + ") :").withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < all.size(); i++) {
            ClipStore.Clip cl = all.get(i);
            int n = i + 1;
            String cmd = "/cmtele " + first + " clip " + n;
            MutableComponent line = Component.literal(" " + n + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(cl.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(Locale.ROOT, "  %d s · %.1f Mo · %s%s", cl.durationMs / 1000, cl.size / 1048576.0,
                            cl.author, cl.source.isEmpty() ? "" : " · " + cl.source)).withStyle(ChatFormatting.GRAY))
                    .withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))));
            c.getSource().sendSuccess(() -> line, false);
        }
        return all.size();
    }

    // ---------------------------------------------------------------- /cmchaine

    private static LiteralArgumentBuilder<CommandSourceStack> chaine(String name) {
        return literal(name).requires(Permissions.IMAGE)
                .then(literal("liste").executes(c -> {
                    Map<String, String> all = MediaServer.channels().all();
                    if (all.isEmpty()) {
                        fail(c, "Aucune chaîne liée : /cmchaine lier <joueur> <chaîne>.");
                        return 0;
                    }
                    var online = c.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().name().toLowerCase(Locale.ROOT)).toList();
                    c.getSource().sendSuccess(() -> Component.literal("Chaînes Twitch des candidats :").withStyle(ChatFormatting.GOLD), false);
                    all.forEach((p, ch) -> c.getSource().sendSuccess(() -> Component.literal(" • " + p + " → twitch.tv/" + ch)
                            .withStyle(online.contains(p.toLowerCase(Locale.ROOT)) ? ChatFormatting.GREEN : ChatFormatting.GRAY), false));
                    return all.size();
                }))
                .then(literal("lier").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                        .then(argument("chaine", StringArgumentType.greedyString()).executes(c -> {
                            String p = StringArgumentType.getString(c, "joueur"), ch = StringArgumentType.getString(c, "chaine");
                            if (!MediaServer.channels().link(p, ch)) {
                                fail(c, "Chaîne invalide : son nom Twitch (3 à 25 lettres, chiffres ou _) ou twitch.tv/<nom>.");
                                return 0;
                            }
                            ok(c, p + " → twitch.tv/" + MediaServer.channels().channelOf(p) + " (/cmtele <tele> direct " + p + ").");
                            return 1;
                        }))))
                .then(literal("delier").then(argument("joueur", StringArgumentType.word()).executes(c -> {
                    String p = StringArgumentType.getString(c, "joueur");
                    if (!MediaServer.channels().unlink(p)) {
                        fail(c, p + " n'a pas de chaîne liée.");
                        return 0;
                    }
                    ok(c, p + " n'a plus de chaîne liée.");
                    return 1;
                })));
    }

    private static void ok(CommandContext<CommandSourceStack> c, String m) {
        c.getSource().sendSuccess(() -> Component.literal(m).withStyle(ChatFormatting.GREEN), true);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String m) {
        c.getSource().sendFailure(Component.literal(m));
    }
}
