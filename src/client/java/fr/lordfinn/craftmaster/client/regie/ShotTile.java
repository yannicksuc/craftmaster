package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * A shot in the director's Cameras tab: its preview (the last still the server sent), its number in a corner, its
 * name on a strip at the bottom; the one on air has a red frame and "DIRECT" (G / D: left or right of a split screen
 * of two). A click puts it on air; Ctrl+click puts it side by side with the one on air, or takes it out of the split.
 */
final class ShotTile extends AbstractButton {

    private static final int LIVE = 0xFFE0483C;

    private final String key;
    private final String number;
    private final String badge;
    private final boolean live;
    private final Consumer<Boolean> action;

    /** {@code badge}: "" off air, else what the red badge says; {@code action} gets true on Ctrl+click. */
    ShotTile(int x, int y, int w, int h, String key, int number, String name, String badge, Consumer<Boolean> action) {
        super(x, y, w, h, Component.literal(number + " " + name));
        this.key = key;
        this.number = String.valueOf(number);
        this.badge = badge;
        this.live = !badge.isEmpty();
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.accept(input.hasControlDown());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        Identifier tex = RegieThumbs.get(key);
        if (tex != null) g.blit(tex, x + 1, y + 1, x + w - 1, y + h - 1, 0, 1, 0, 1);
        else {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Theme.VELVET_DARK);
            Component wait = Component.translatable("screen.craftmaster.regie.no_preview");
            g.centeredText(font, wait, x + w / 2, y + h / 2 - 8, Theme.TEXT_DIM);
        }
        // Name on a dark strip, number in a corner badge (red on air)
        String name = getMessage().getString().substring(number.length() + 1);
        g.fill(x + 1, y + h - 11, x + w - 1, y + h - 1, 0xC0100A0C);
        g.text(font, font.plainSubstrByWidth(name, w - 6), x + 3, y + h - 10, Theme.TEXT, false);
        int nw = font.width(number) + 6;
        g.fill(x + 1, y + 1, x + 1 + nw, y + 11, live ? LIVE : 0xC0100A0C);
        g.text(font, number, x + 4, y + 2, live ? 0xFFFFFFFF : Theme.GOLD, false);
        if (live) {
            String d = badge;
            g.fill(x + w - 1 - font.width(d) - 6, y + 1, x + w - 1, y + 11, LIVE);
            g.text(font, d, x + w - 1 - font.width(d) - 3, y + 2, 0xFFFFFFFF, false);
        }
        int frame = live ? LIVE : isHoveredOrFocused() ? Theme.BORDER : Theme.BORDER_DARK;
        g.outline(x, y, w, h, frame);
        if (live) g.outline(x + 1, y + 1, w - 2, h - 2, frame);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
