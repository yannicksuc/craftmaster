package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.MasterVoice;
import fr.lordfinn.craftmaster.show.Spectators;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /cm spectateurs} (watch a contestant or the broadcast once done, allow or forbid it) and {@code /cm vocal}
 * (the game master's voice modes): {@link Spectators}, {@link MasterVoice}. Watching is for everyone (the server
 * checks who may); the rest is the organisers'. The settings go through the actions (« spectators.set »,
 * « voice.mode »), like the N menu, the Stream Deck and the AI control.
 */
public final class SpectatorCommands {

    private SpectatorCommands() {}

    private static final SuggestionProvider<CommandSourceStack> PLAYING = (c, b) ->
            SharedSuggestionProvider.suggest(Spectators.playingNames(c.getSource().getServer()), b);
    private static final SuggestionProvider<CommandSourceStack> ON_OFF = (c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b);
    private static final SuggestionProvider<CommandSourceStack> MANCHES = (c, b) -> {
        List<String> ids = new ArrayList<>();
        fr.lordfinn.craftmaster.data.Season s = fr.lordfinn.craftmaster.CraftMaster.data() == null ? null : fr.lordfinn.craftmaster.CraftMaster.data().activeSeason();
        if (s != null && s.activePartie() != null) for (fr.lordfinn.craftmaster.data.Manche m : s.activePartie().manches) ids.add(m.id);
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> MODES = (c, b) -> {
        b.suggest("off", Component.literal("Vocal du maître coupé : chacun son vocal habituel"));
        b.suggest("commentaire", Component.literal("Avec les spectateurs (ouvert ou fermé) ; les candidats en jeu ne l'entendent pas"));
        b.suggest("tous", Component.literal("Annonce : tout le monde avec toi (ou candidats / spectateurs)"));
        b.suggest("joueur", Component.literal("Un candidat ou une équipe seul avec toi (celui que tu regardes par défaut)"));
        b.suggest("suivant", Component.literal("Le mode d'après (off → commentaire → tous → joueur)"));
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> TARGETS = (c, b) -> {
        String mode = StringArgumentType.getString(c, "mode");
        List<String> out = new ArrayList<>();
        switch (mode) {
            case "commentaire" -> out.addAll(List.of("ouvert", "ferme", "basculer"));
            case "tous" -> out.addAll(List.of("tous", "candidats", "spectateurs"));
            default -> {
                for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) out.add(p.getGameProfile().name());
                for (String t : c.getSource().getServer().getScoreboard().getTeamNames()) out.add("equipe:" + t);
            }
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, SpectatorCommands::spectators, "craftMasterSpectators", "cfsv");
        CraftMasterCommands.add(d, SpectatorCommands::voice, "craftMasterVocal", "cfvo");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spectators(String name) {
        return literal(name)
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p != null && Spectators.eligible(p)) Spectators.menu(p);
                    c.getSource().sendSystemMessage(Component.literal(Spectators.describe(c.getSource().getServer())).withStyle(ChatFormatting.GRAY));
                    return 1;
                })
                .then(literal("regarder")
                        .then(literal("emission").executes(c -> watch(c, null)))
                        .then(argument("joueur", StringArgumentType.word()).suggests(PLAYING).executes(c -> watch(c, StringArgumentType.getString(c, "joueur")))))
                .then(literal("quitter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!Spectators.stop(p, true)) c.getSource().sendFailure(Component.literal("Tu ne regardes rien."));
                    return 1;
                }))
                .then(literal("liste").requires(Permissions.ADMIN).executes(c -> {
                    c.getSource().sendSystemMessage(Component.literal(Spectators.describe(c.getSource().getServer())).withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
                .then(literal("autoriser").requires(Permissions.ADMIN)
                        .then(argument("etat", StringArgumentType.word()).suggests(ON_OFF)
                                .executes(c -> act(c, "spectators.set", "enabled", on(c)))
                                .then(argument("manche", StringArgumentType.word()).suggests(MANCHES)
                                        .executes(c -> act(c, "spectators.set", "enabled", on(c), "manche", StringArgumentType.getString(c, "manche"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> voice(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(c -> {
                    c.getSource().sendSystemMessage(Component.literal(MasterVoice.status(c.getSource().getServer())).withStyle(ChatFormatting.GOLD));
                    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat"))
                        c.getSource().sendSystemMessage(Component.literal(fr.lordfinn.craftmaster.compat.VoiceGroups.describe(c.getSource().getServer())).withStyle(ChatFormatting.GRAY));
                    return 1;
                })
                .then(literal("mode").then(argument("mode", StringArgumentType.word()).suggests(MODES)
                        .executes(c -> act(c, "voice.mode", "mode", StringArgumentType.getString(c, "mode")))
                        .then(argument("cible", StringArgumentType.greedyString()).suggests(TARGETS)
                                .executes(c -> act(c, "voice.mode", "mode", StringArgumentType.getString(c, "mode"), "target", StringArgumentType.getString(c, "cible"))))))
                // Short forms
                .then(literal("parler").then(argument("joueur", StringArgumentType.greedyString()).suggests(PLAYING)
                        .executes(c -> act(c, "voice.mode", "mode", "joueur", "target", StringArgumentType.getString(c, "joueur")))))
                .then(literal("annonce").executes(c -> act(c, "voice.mode", "mode", "tous"))
                        .then(argument("qui", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("tous", "candidats", "spectateurs"), b))
                                .executes(c -> act(c, "voice.mode", "mode", "tous", "target", StringArgumentType.getString(c, "qui")))))
                .then(literal("commentaire").executes(c -> act(c, "voice.mode", "mode", "commentaire", "target", "basculer")))
                .then(literal("fin").executes(c -> act(c, "voice.mode", "mode", "off")));
    }

    private static boolean on(CommandContext<CommandSourceStack> c) {
        String v = StringArgumentType.getString(c, "etat").toLowerCase(java.util.Locale.ROOT);
        return v.equals("on") || v.equals("oui") || v.equals("true");
    }

    private static int watch(CommandContext<CommandSourceStack> c, String target) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            c.getSource().sendSystemMessage(Component.literal(Spectators.start(p, target)).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("Impossible : " + e.getMessage()));
            return 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            c.getSource().sendFailure(Component.literal("Réservé aux joueurs."));
            return 0;
        }
    }

    private static int act(CommandContext<CommandSourceStack> c, String type, Object... kv) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) a.put((String) kv[i], kv[i + 1]);
        // The one who types is the one who talks
        if (type.equals("voice.mode") && c.getSource().getPlayer() != null) a.put("by", c.getSource().getPlayer().getGameProfile().name());
        return ActionExecutor.execute(c.getSource(), List.of(a));
    }
}
