package fr.lordfinn.craftmaster.client.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.fx.FxContent;
import fr.lordfinn.craftmaster.fx.FxNetwork;
import fr.lordfinn.craftmaster.fx.ScreenFx;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client side of the screen effects: server orders, the tooltip of the wearables ("Effet porté : sépia") and the
 * try-out command {@code /essaifx <effet[+effet]|aucun> [secondes]} (this game's own view only, nothing sent).
 */
public final class FxClient {

    private FxClient() {}

    public static void init() {
        FxOverlay.init();
        ClientPlayNetworking.registerGlobalReceiver(FxNetwork.Order.TYPE, (payload, ctx) -> ScreenEffects.receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ScreenEffects::reset));
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            List<String> fx = ScreenFx.parseLenient(FxContent.effectOf(stack));
            if (fx.isEmpty()) return;
            List<Component> names = new ArrayList<>();
            for (String id : fx) names.add(Component.translatable("fx.craftmaster." + id));
            Component joined = names.getFirst();
            for (int i = 1; i < names.size(); i++) joined = Component.empty().append(joined).append(" + ").append(names.get(i));
            lines.add(Component.translatable("item.craftmaster.screen_fx.effect", joined).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("item.craftmaster.screen_fx.hint").withStyle(ChatFormatting.GRAY));
        });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        for (String name : List.of("craftMasterFxTry", "craftmasterfxtry", "essaifx")) d.register(tree(name));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
        return literal(name)
                .executes(c -> {
                    List<String> on = ScreenEffects.visible();
                    c.getSource().sendFeedback(Component.literal("Sur ta vue : " + ScreenFx.describe(on) + " · "
                            + net.minecraft.client.Minecraft.getInstance().getFps() + " images/s").withStyle(ChatFormatting.YELLOW));
                    c.getSource().sendFeedback(Component.literal("Effets : " + String.join(", ", ScreenFx.ids())
                            + " · combinaisons : " + String.join(", ", ScreenFx.PRESETS.keySet())
                            + " · /" + name + " <effet[+effet]|aucun> [secondes]").withStyle(ChatFormatting.GOLD));
                    return 1;
                })
                .then(argument("effect", StringArgumentType.string())
                        .suggests((c, b) -> {
                            String rem = b.getRemaining();
                            int plus = rem.lastIndexOf('+');
                            String head = plus < 0 ? "" : rem.substring(0, plus + 1);
                            String tail = rem.substring(plus + 1).toLowerCase(Locale.ROOT);
                            for (String s : ScreenFx.suggestions()) if (s.startsWith(tail)) b.suggest(head + s);
                            return b.buildFuture();
                        })
                        .executes(c -> tryOut(c, 0))
                        .then(argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(c -> tryOut(c, IntegerArgumentType.getInteger(c, "seconds")))));
    }

    private static int tryOut(CommandContext<FabricClientCommandSource> c, int seconds) {
        String spec = StringArgumentType.getString(c, "effect");
        List<String> fx;
        try {
            fx = ScreenFx.parse(spec);
        } catch (IllegalArgumentException e) {
            c.getSource().sendError(Component.literal("Effet impossible : " + e.getMessage()));
            return 0;
        }
        ScreenEffects.tryOut(ScreenFx.join(fx), seconds);
        c.getSource().sendFeedback(Component.literal(fx.isEmpty() ? "Essai terminé : plus d'effet sur ta vue."
                : "Essai sur ta vue : " + ScreenFx.describe(fx) + (seconds > 0 ? " pendant " + seconds + " s" : "")).withStyle(ChatFormatting.GOLD));
        return 1;
    }
}
