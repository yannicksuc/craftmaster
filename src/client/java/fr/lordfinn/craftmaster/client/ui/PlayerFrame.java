package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A player's frame: his flat face in a golden frame, his score as a digit head just below, his name under it.
 * Used by the score entry screen and the HUD ({@code PlayerFrameHud}); the caller gives the current score, so the
 * frame is always up to date with whatever data feeds it.
 */
public final class PlayerFrame {

    private PlayerFrame() {}

    /** Height of a frame of face size {@code size} (face, score, name). */
    public static int height(int size, boolean name) {
        return size + 4 + scoreSize(size) + 2 + (name ? 11 : 0);
    }

    public static int scoreSize(int size) {
        return Math.max(12, size * 2 / 3);
    }

    /**
     * Draws the frame, top left at (x, y), {@code size + 4} wide.
     *
     * @param score the score shown under the face, or null for none (an empty slot)
     * @param highlight a golden glow (the player being moved, the local player)
     */
    public static void draw(GuiGraphicsExtractor g, Font font, String player, Integer score, int x, int y, int size, boolean name, boolean highlight) {
        int w = size + 4;
        if (highlight) g.fill(x - 2, y - 2, x + w + 2, y + size + 6, 0x80FFC23D);
        g.fill(x, y, x + w, y + size + 4, Theme.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + size + 3, Theme.BORDER_DARK);
        Ui.face(g, player, x + 2, y + 2, size);
        int s = scoreSize(size);
        int sy = y + size + 6;
        if (score != null) DigitHead.draw(g, font, score, x + (w - s) / 2, sy, s);
        if (name) {
            String n = font.width(player) > w + 16 ? font.plainSubstrByWidth(player, w + 10) + "…" : player;
            g.centeredText(font, n, x + w / 2, sy + s + 3, Theme.TEXT);
        }
    }
}
