package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A small rounded tag with a number or a word ("+2", "12 pts", "non publié"), centred on (cx, y). */
public final class ScoreBadge {

    private ScoreBadge() {}

    /** A badge in the colour of the score (cold / warm). */
    public static void score(GuiGraphicsExtractor g, Font font, int value, int cx, int y) {
        draw(g, font, DigitHead.label(value), cx, y, Theme.scoreColor(value));
    }

    public static void draw(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        int w = font.width(text) + 6, x = cx - w / 2;
        g.fill(x + 1, y, x + w - 1, y + 11, color);
        g.fill(x, y + 1, x + w, y + 10, color);
        g.horizontalLine(x + 1, x + w - 2, y + 10, Theme.shade(color, -0.4f));
        g.text(font, text, x + 3, y + 2, Theme.textOn(color), false);
    }
}
