package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.task.WaxColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The look of the organisers' screens: red velvet panels with a gold border, gold titles, wax colour swatches. */
final class TaskStyle {

    private TaskStyle() {}

    static final int GOLD = 0xFFFFC23D, LABEL = 0xFFE8D8B0, HINT = 0xFF9C8E78, VELVET = 0xF0240A12, VELVET_DARK = 0xF0160609;
    static final int SWATCH = 12, SWATCH_GAP = 3;

    /** Velvet panel: vertical folds, gold double border. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, VELVET, VELVET_DARK);
        for (int fx = x + 6; fx < x + w - 4; fx += 14) g.fill(fx, y + 2, fx + 2, y + h - 2, 0x0EFFFFFF);
        g.outline(x, y, w, h, GOLD);
        g.outline(x + 2, y + 2, w - 4, h - 4, 0x60FFC23D);
    }

    static void title(GuiGraphicsExtractor g, Font font, Component text, int cx, int y) {
        g.centeredText(font, text, cx, y, GOLD);
        int w = font.width(text) / 2 + 10;
        g.fill(cx - w, y + 11, cx + w, y + 12, 0x80FFC23D);
    }

    static void label(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
        g.text(font, text, x, y, LABEL, false);
    }

    /** The 12 wax colours in a row; the chosen one is framed in gold. */
    static void swatches(GuiGraphicsExtractor g, int x, int y, WaxColor chosen, int mouseX, int mouseY) {
        WaxColor[] all = WaxColor.values();
        for (int i = 0; i < all.length; i++) {
            int sx = x + i * (SWATCH + SWATCH_GAP);
            boolean on = all[i] == chosen;
            boolean hover = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= y && mouseY < y + SWATCH;
            g.fill(sx - 1, y - 1, sx + SWATCH + 1, y + SWATCH + 1, on ? GOLD : hover ? 0xFFFFFFFF : 0xFF000000);
            g.fill(sx, y, sx + SWATCH, y + SWATCH, 0xFF000000 | all[i].rgb);
            // A little shine, like a drop of wax
            g.fill(sx + 2, y + 2, sx + 5, y + 4, 0x50FFFFFF);
        }
    }

    static int swatchesWidth() {
        return WaxColor.values().length * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
    }

    /** The colour under the mouse, or null. */
    static WaxColor swatchAt(int x, int y, double mouseX, double mouseY) {
        WaxColor[] all = WaxColor.values();
        for (int i = 0; i < all.length; i++) {
            int sx = x + i * (SWATCH + SWATCH_GAP);
            if (mouseX >= sx && mouseX < sx + SWATCH && mouseY >= y && mouseY < y + SWATCH) return all[i];
        }
        return null;
    }

    static Component waxName(WaxColor c) {
        return Component.translatable("wax.craftmaster." + c.id());
    }
}
