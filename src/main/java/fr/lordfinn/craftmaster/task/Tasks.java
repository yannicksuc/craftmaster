package fr.lordfinn.craftmaster.task;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * STUB written by chantier H (task props) — replaced at merge by chantier D's real class, which opens the cream
 * paper card on the player's screen. Only the method chantier H calls is here, with the same signature; until
 * then the note is sent as a chat message.
 */
public final class Tasks {

    private Tasks() {}

    /** Shows a note (title + text, several lines) to the player. Stub: chat message. */
    public static void openPaper(ServerPlayer player, String title, String text) {
        player.sendSystemMessage(Component.literal("✉ " + (title == null || title.isBlank() ? "Note" : title)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String line : (text == null ? "" : text).split("\n"))
            player.sendSystemMessage(Component.literal("  " + line).withStyle(ChatFormatting.WHITE));
    }
}
