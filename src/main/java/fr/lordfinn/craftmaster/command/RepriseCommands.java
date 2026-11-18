package fr.lordfinn.craftmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.reprise.Reprise;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

/**
 * /cm reprise (/cm reprise): the show frozen after a crash ({@link Reprise}). Without argument or {@code statut}:
 * frozen or not, the clocks, who is missing; {@code reprendre [force]}: the go (refused while a contestant is
 * missing, unless forced); {@code geler}: freezes the show by hand. Organisers only.
 */
public final class RepriseCommands {

    private RepriseCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        CraftMasterCommands.add(d, RepriseCommands::reprise, "craftMasterReprise", "cfrp");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reprise(String name) {
        return literal(name).requires(Permissions.ADMIN)
                .executes(RepriseCommands::status)
                .then(literal("statut").executes(RepriseCommands::status))
                .then(literal("reprendre").executes(c -> resume(c, false))
                        .then(literal("force").executes(c -> resume(c, true))))
                .then(literal("geler").executes(c -> {
                    if (!Reprise.freeze(c.getSource().getServer(), "manuel")) {
                        c.getSource().sendFailure(Component.literal("Le show est déjà gelé."));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Component.literal("⏸ Show gelé : joueurs, entités et chronos arrêtés. "
                            + "/cm reprise reprendre pour repartir.").withStyle(ChatFormatting.GOLD), true);
                    return 1;
                }));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        for (Component line : Reprise.status(c.getSource().getServer())) c.getSource().sendSystemMessage(line);
        return Reprise.frozen() ? 1 : 0;
    }

    private static int resume(CommandContext<CommandSourceStack> c, boolean force) {
        if (!Reprise.frozen()) {
            c.getSource().sendFailure(Component.literal("Le show n'est pas gelé."));
            return 0;
        }
        List<String> missing = Reprise.resume(c.getSource().getServer(), force);
        if (!missing.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Pas de reprise : manque encore " + String.join(", ", missing)
                    + ". /cm reprise reprendre force pour repartir quand même."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("▶ Reprise : chaque chrono repart avec le temps qui lui restait.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
