package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A score drawn as a "digit head": a square block like a Minecraft head (darker edge, lit top, shaded bottom) in the
 * score's colour — cold tones for positive scores, warm ones for negative, grey for 0 — with the number on it.
 * Scores are shown from -10 to +10.
 */
public final class DigitHead {

    private DigitHead() {}

    /** Draws the head of {@code score}, {@code size} pixels square, top left at (x, y). */
    public static void draw(GuiGraphicsExtractor g, Font font, int score, int x, int y, int size) {
        int c = Theme.scoreColor(score);
        int px = Math.max(1, size / 16);
        // Edge, face, then the pixel-art light: top and left lit, bottom and right in shadow
        g.fill(x, y, x + size, y + size, Theme.shade(c, -0.55f));
        g.fill(x + px, y + px, x + size - px, y + size - px, c);
        g.fill(x + px, y + px, x + size - px, y + px * 2, Theme.shade(c, 0.35f));
        g.fill(x + px, y + px, x + px * 2, y + size - px, Theme.shade(c, 0.2f));
        g.fill(x + px, y + size - px * 2, x + size - px, y + size - px, Theme.shade(c, -0.25f));
        g.fill(x + size - px * 2, y + px, x + size - px, y + size - px, Theme.shade(c, -0.15f));
        String text = label(score);
        // The number fills about 60 % of the head, whatever its size
        float scale = Math.max(0.5f, size * 0.6f / Math.max(8, font.width(text)));
        scale = Math.min(scale, size * 0.62f / 8f);
        Ui.text(g, font, Component.literal(text).withStyle(ChatFormatting.BOLD), x + size / 2f, y + size / 2f + scale * 0.5f, scale,
                Theme.textOn(c), Theme.textOn(c) == 0xFFFFFFFF);
    }

    /** "+3", "-2", "0". */
    public static String label(int score) {
        return score > 0 ? "+" + score : String.valueOf(score);
    }
}
