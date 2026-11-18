package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.fx.FxContent;
import fr.lordfinn.craftmaster.fx.ScreenEffectsServer;
import fr.lordfinn.craftmaster.fx.ScreenFx;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.action;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.fail;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.list;
import static fr.lordfinn.craftmaster.command.CraftMasterCommands.ok;
import static fr.lordfinn.craftmaster.command.WordArgument.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Screen effects: {@code /cm fx} (alias {@code /cm fx}). Organisers only (or craftmaster:camera for the
 * broadcast view, like the cameras). Effects go through {@link ActionExecutor} (actions {@code fx.set} /
 * {@code fx.clear}), like the voice control. Reference: docs/wiki/Effets.md.
 * <ul>
 *   <li>{@code /cm fx <diffusion|tous|candidats|joueur> <effet[+effet]|aucun> [secondes]}</li>
 *   <li>{@code /cm fx aucun} — every effect off (broadcast and players)</li>
 *   <li>{@code /cm fx porter <joueurs> <objet> [verrouille]} — puts glasses / a mask on, optionally locked</li>
 *   <li>{@code /cm fx retirer <joueurs>}, {@code /cm fx liste}, {@code /cm fx etat}</li>
 * </ul>
 */
public final class FxCommands {

    private FxCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, FxCommands::tree, "craftMasterFx", "fx");
    }

    private static final SuggestionProvider<CommandSourceStack> TARGETS = list(c -> {
        Set<String> out = new LinkedHashSet<>(List.of("diffusion", "tous", "candidats"));
        out.addAll(c.getSource().getOnlinePlayerNames());
        return out;
    });
    private static final SuggestionProvider<CommandSourceStack> WEARABLES = list(c -> FxContent.ITEMS.keySet());
    private static final SuggestionProvider<CommandSourceStack> LOCK = list(c -> List.of("verrouille", "libre"));

    /** Suggests the effect names, also after a "+" (to build a combination). */
    private static final SuggestionProvider<CommandSourceStack> EFFECTS = (c, b) -> {
        String rem = b.getRemaining();
        int plus = rem.lastIndexOf('+');
        String head = plus < 0 ? "" : rem.substring(0, plus + 1);
        String tail = rem.substring(plus + 1).toLowerCase(Locale.ROOT);
        for (String s : ScreenFx.suggestions()) {
            if (plus >= 0 && s.equals("aucun")) continue;
            if (s.startsWith(tail)) b.suggest(head + s);
        }
        return b.buildFuture();
    };

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return literal(name).requires(Permissions.CAMERA)
                .executes(FxCommands::status)
                .then(literal("etat").executes(FxCommands::status))
                .then(literal("liste").executes(FxCommands::listEffects))
                .then(literal("aucun").executes(c -> run(c, action("fx.clear", "target", "tout"))))
                .then(literal("porter").requires(Permissions.ADMIN)
                        .then(argument("players", EntityArgument.players())
                                .then(argument("item", word()).suggests(WEARABLES)
                                        .executes(c -> wear(c, false))
                                        .then(argument("lock", word()).suggests(LOCK)
                                                .executes(c -> wear(c, isLock(c.getArgument("lock", String.class))))))))
                .then(literal("retirer").requires(Permissions.ADMIN)
                        .then(argument("players", EntityArgument.players()).executes(FxCommands::takeOff)))
                .then(argument("target", word()).suggests(TARGETS)
                        .then(argument("effect", word()).suggests(EFFECTS)
                                .executes(c -> set(c, 0))
                                .then(argument("seconds", integer(1, 3600)).executes(c -> set(c, getInteger(c, "seconds"))))));
    }

    private static boolean isLock(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        return s.startsWith("verrou") || s.equals("lock") || s.equals("oui") || s.equals("true");
    }

    @SafeVarargs
    private static int run(CommandContext<CommandSourceStack> c, Map<String, Object>... actions) {
        return ActionExecutor.execute(c.getSource(), List.of(actions));
    }

    private static int set(CommandContext<CommandSourceStack> c, int seconds) {
        String target = c.getArgument("target", String.class);
        String effect = c.getArgument("effect", String.class);
        // A player (not the broadcast) needs an organiser; craftmaster:camera only drives the broadcast picture
        if (!ScreenEffectsServer.isBroadcast(target) && !Permissions.ADMIN.test(c.getSource())) {
            fail(c, "Mettre un effet sur un joueur est réservé aux organisateurs (la permission caméra suffit pour la diffusion).");
            return 0;
        }
        List<String> parsed;
        boolean delta = ScreenEffectsServer.isDelta(effect);
        try {
            parsed = ScreenFx.parse(delta ? effect.replace('-', '+') : effect);
        } catch (IllegalArgumentException e) {
            fail(c, "Effet impossible : " + e.getMessage());
            return 0;
        }
        if (parsed.isEmpty() && !delta) return run(c, action("fx.clear", "target", target));
        // "+sepia" adds to what is on, "-flou" takes it off; otherwise the effect replaces what is on
        Map<String, Object> a = action("fx.set", "target", target, "effect", delta ? effect : ScreenFx.join(parsed));
        if (seconds > 0) a.put("seconds", seconds);
        return run(c, a);
    }

    private static int wear(CommandContext<CommandSourceStack> c, boolean lock) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        net.minecraft.world.item.ItemStack item = FxContent.wearable(c.getArgument("item", String.class));
        if (item == null) {
            fail(c, "Objet inconnu (objets : " + String.join(", ", FxContent.ITEMS.keySet()) + ").");
            return 0;
        }
        for (ServerPlayer p : players) ok(c, ScreenEffectsServer.wear(p, item, lock));
        return players.size();
    }

    private static int takeOff(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer p : players) ok(c, ScreenEffectsServer.takeOff(p));
        return players.size();
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        List<String> lines = new ArrayList<>();
        String b = ScreenEffectsServer.broadcastEffect();
        lines.add("Diffusion : " + ScreenFx.describe(ScreenFx.parseLenient(b)));
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
            String forced = ScreenEffectsServer.forcedOn(p);
            String worn = FxContent.effectOf(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            if (forced.isEmpty() && worn.isEmpty()) continue;
            lines.add(p.getName().getString() + " : " + (forced.isEmpty() ? "" : "imposé " + ScreenFx.describe(ScreenFx.parseLenient(forced)))
                    + (!forced.isEmpty() && !worn.isEmpty() ? " · " : "") + (worn.isEmpty() ? "" : "porte " + ScreenFx.describe(ScreenFx.parseLenient(worn))));
        }
        lines.add("/cm fx <diffusion|tous|candidats|joueur> <effet[+effet]|aucun> [secondes] · /cm fx liste");
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int listEffects(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("Effets : ");
        for (ScreenFx.Kind k : ScreenFx.ORDER) sb.append(k.id()).append(" (").append(k.fr()).append("), ");
        sb.setLength(sb.length() - 2);
        String effects = sb.toString();
        String presets = "Combinaisons : " + String.join(", ", ScreenFx.PRESETS.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).toList())
                + ". À combiner avec + : fisheye+masque_rond+sepia.";
        c.getSource().sendSuccess(() -> Component.literal(effects).withStyle(ChatFormatting.GOLD), false);
        c.getSource().sendSuccess(() -> Component.literal(presets).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
