package fr.lordfinn.craftmaster.toy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /craftMasterToys} (cfty), organisers: what the players did with the toys (found, caught, thrown, golf,
 * balloons, eggs) with buttons to write it down, the hidden toys left (list, glow, collect), the reset, and the
 * fragility of the egg in hand.
 */
public final class ToyCommands {

    private ToyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        Set<String> names = new LinkedHashSet<>(List.of("craftMasterToys", "craftmastertoys", "cfty"));
        for (String n : names) d.register(tree(n));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(ToyCommands::status)
                .then(literal("status").executes(ToyCommands::status))
                .then(literal("reset").executes(c -> {
                    Toys.TALLY.reset();
                    c.getSource().sendSystemMessage(Component.literal("Compteurs des objets remis à zéro (les objets cachés restent en place).").withStyle(ChatFormatting.GOLD));
                    return 1;
                }))
                .then(literal("list").executes(ToyCommands::list))
                .then(literal("glow").executes(c -> glow(c, 10))
                        .then(argument("seconds", IntegerArgumentType.integer(1, 600)).executes(c -> glow(c, IntegerArgumentType.getInteger(c, "seconds")))))
                .then(literal("collect").executes(ToyCommands::collect))
                // How much goes to the chat: tout, important (records, objects in a target), rien
                .then(literal("annonces").executes(c -> {
                    c.getSource().sendSystemMessage(Component.literal("Annonces des objets : " + Toys.announce.word
                            + " (tout | important | rien)").withStyle(ChatFormatting.GOLD));
                    return 1;
                }).then(argument("level", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("tout", "important", "rien"), b))
                        .executes(c -> {
                            Toys.Announce a = Toys.Announce.parse(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "level"));
                            if (a == null) {
                                c.getSource().sendFailure(Component.literal("Annonces : tout, important ou rien."));
                                return 0;
                            }
                            Toys.announce = a;
                            c.getSource().sendSystemMessage(Component.literal(switch (a) {
                                case ALL -> "Chaque lancer et chaque prise s'écrit dans le chat.";
                                case IMPORTANT -> "Dans le chat : les records et les objets dans une cible ; les autres lancers au-dessus de la barre.";
                                case NONE -> "Plus rien dans le chat : tout est dans /cfty status et le journal des épreuves.";
                            }).withStyle(ChatFormatting.GOLD));
                            return 1;
                        })))
                .then(literal("egg").then(argument("blocks", DoubleArgumentType.doubleArg(0, 64)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack stack = p.getMainHandItem();
                    if (!stack.is(Toys.TOY_ITEMS.get(ToyKind.EGG))) {
                        c.getSource().sendFailure(Component.literal("Tiens un œuf fragile en main."));
                        return 0;
                    }
                    double b = DoubleArgumentType.getDouble(c, "blocks");
                    Toys.setFragility(stack, b);
                    c.getSource().sendSystemMessage(Component.literal(b <= 0 ? "◇ Cet œuf casse au moindre choc."
                            : "◇ Cet œuf casse au-delà d'une chute de " + ToyPhysics.blocks(b) + " blocs.").withStyle(ChatFormatting.YELLOW));
                    return 1;
                })));
    }

    /** Every toy put down by an organiser and not found yet, in every level. */
    public static List<ToyEntity> hidden(MinecraftServer server) {
        List<ToyEntity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels())
            for (Entity e : level.getAllEntities()) if (e instanceof ToyEntity t && t.hidden() && !t.isRemoved()) out.add(t);
        return out;
    }

    public static int hiddenLeft(MinecraftServer server) {
        return hidden(server).size();
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        s.sendSystemMessage(Component.literal("— Objets d'épreuve —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int left = hiddenLeft(s.getServer());
        s.sendSystemMessage(Component.literal(" Cachés et pas encore trouvés : " + left).withStyle(ChatFormatting.GRAY)
                .append(button(" [où ?]", "/craftMasterToys list", "Liste avec téléportation"))
                .append(button(" [faire briller]", "/craftMasterToys glow", "10 secondes, visibles à travers les murs")));
        if (Toys.TALLY.isEmpty()) {
            s.sendSystemMessage(Component.literal(" Rien encore depuis la dernière remise à zéro.").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (String p : Toys.TALLY.players()) {
            MutableComponent head = Component.literal(" " + p).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            int found = Toys.TALLY.foundTotal(p);
            if (found > 0) head.append(Toys.noteButton(p, found, ResultSheet.Kind.COUNT, found + " trouvé(s)"));
            int caught = Toys.TALLY.caughtTotal(p);
            if (caught > 0) head.append(Toys.noteButton(p, caught, ResultSheet.Kind.COUNT, caught + " dans la cible"));
            if (Toys.TALLY.bestCatch(p) > 0)
                head.append(Toys.noteButton(p, Toys.TALLY.bestCatch(p), ResultSheet.Kind.DISTANCE, ToyPhysics.blocks(Toys.TALLY.bestCatch(p)) + " blocs (cible)"));
            if (Toys.TALLY.bestThrow(p) > 0)
                head.append(Toys.noteButton(p, Toys.TALLY.bestThrow(p), ResultSheet.Kind.DISTANCE, ToyPhysics.blocksText(Toys.TALLY.bestThrow(p))));
            List<Integer> holes = Toys.TALLY.holes(p);
            if (!holes.isEmpty()) {
                int total = holes.stream().mapToInt(Integer::intValue).sum();
                head.append(Toys.noteButton(p, total, ResultSheet.Kind.COUNT, total + " coups"));
            }
            s.sendSystemMessage(head);
            for (String line : Toys.TALLY.describe(p)) s.sendSystemMessage(Component.literal("   " + line).withStyle(ChatFormatting.GRAY));
        }
        s.sendSystemMessage(Component.literal(" ").append(button("[remettre à zéro]", "/craftMasterToys reset", "Oublie qui a trouvé / lancé quoi")).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        List<ToyEntity> toys = hidden(s.getServer());
        s.sendSystemMessage(Component.literal("— " + toys.size() + " objet(s) caché(s) —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ToyEntity t : toys) {
            String where = String.format(Locale.ROOT, "%.1f %.1f %.1f", t.getX(), t.getY(), t.getZ());
            String tp = "/execute in " + t.level().dimension().identifier() + " run tp @s " + where;
            s.sendSystemMessage(Component.literal(" " + Toys.frName(t.stack()) + " ").withStyle(ChatFormatting.WHITE)
                    .append(button("[" + where + "]", tp, "Y aller")));
        }
        return toys.size();
    }

    private static int glow(CommandContext<CommandSourceStack> c, int seconds) {
        List<ToyEntity> toys = hidden(c.getSource().getServer());
        for (ToyEntity t : toys) t.glowFor(seconds * 20);
        c.getSource().sendSystemMessage(Component.literal("✨ " + toys.size() + " objet(s) caché(s) brillent " + seconds + " s.").withStyle(ChatFormatting.GOLD));
        return toys.size();
    }

    private static int collect(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<ToyEntity> toys = hidden(c.getSource().getServer());
        for (ToyEntity t : toys) {
            ItemStack s = t.stack().copy();
            if (!p.getInventory().add(s)) p.spawnAtLocation((ServerLevel) p.level(), s);
            t.discard();
        }
        c.getSource().sendSystemMessage(Component.literal("▣ " + toys.size() + " objet(s) caché(s) ramassé(s).").withStyle(ChatFormatting.GOLD));
        return toys.size();
    }

    private static MutableComponent button(String text, String cmd, String hover) {
        return Component.literal(text).withStyle(st -> st.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
}
