package fr.lordfinn.craftmaster.trial.kit;

import fr.lordfinn.craftmaster.trial.TextMatch;
import fr.lordfinn.craftmaster.trial.TrialLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * A code typed by a contestant in their copy of a set (La pomme: the « ? : » sign). A click on the sign runs
 * {@code /cm arene demander <code>} as the player; the game's dialog window asks for the answer, compared with the
 * set's accepted answers by {@link TextMatch}. A right answer tags the player {@code cm_code_<code>} (the room's
 * functions take it from there); a wrong one asks again. Answers and variants: {@code /cm arene code}.
 */
public final class Codes {

    private Codes() {}

    public static final String TAG = "cm_code_";

    /** @return false if the player is in no set having that code */
    public static boolean ask(ServerPlayer p, String code) {
        return ask(p, code, null);
    }

    private static boolean ask(ServerPlayer p, String code, String error) {
        Arenas.Owner o = Arenas.ownerAt(p.level(), p.blockPosition());
        if (o == null || o.arena().codes.get(code) == null) return false;
        List<Component> lines = error == null ? List.of() : List.of(Component.literal(error).withStyle(ChatFormatting.RED));
        Dialogs.ask(p, Component.literal("? :"), lines, Component.literal("Ta réponse"), 64, (player, typed) -> answer(player, o.arena(), code, typed));
        return true;
    }

    private static void answer(ServerPlayer p, Arena a, String code, String typed) {
        Arena.Code c = a.codes.get(code);
        if (c == null || typed.isBlank()) return;
        String name = Arenas.playerName(p);
        boolean right = c.syllabes.isEmpty() ? TextMatch.matches(typed, c.reponses, c.variantes) : TextMatch.matchesSyllables(typed, c.reponses, c.syllabes);
        if (!right) {
            TrialLog.add(p.level().getServer(), name + " — code « " + code + " » : « " + typed + " » refusé");
            ask(p, code, "✗ « " + typed + " » : ce n'est pas ça");
            return;
        }
        p.addTag(TAG + code);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1f, 1.4f);
        Finish.tellOrganisers(p.level().getServer(), p, Component.literal("🔑 ").withStyle(Finish.colour(name)).append(Finish.name(name))
                        .append(Component.literal(" : code « " + code + " » trouvé avec « " + typed + " »").withStyle(Finish.colour(name))),
                name + " — code « " + code + " » trouvé avec « " + typed + " »");
    }
}
