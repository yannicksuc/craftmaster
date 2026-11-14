package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * A shot in the director's Cameras tab: its preview (the last still the server sent), its number in a corner, its
 * name on a strip at the bottom; the ones on air have a red frame and "DIRECT". A click puts it on air alone; Ctrl+click
 * adds it to the split screen, or takes it out. In a split screen, a star on the name strip: filled on the main shot,
 * hollow on the others (a click on it makes that one the main shot).
 */
final class ShotTile extends AbstractButton {

    private static final int LIVE = 0xFFE0483C;

    private final String key;
    private final String number;
    /** The star of a tile: none (not in a split screen), hollow (in it), filled (its main shot). */
    static final int NO_STAR = 0, STAR = 1, MAIN = 2;
    private static final int STAR_W = 10;

    private final boolean live;
    private final int star;
    private final Consumer<Boolean> action;
    private final Runnable makeMain;
    private boolean onStar;

    /** {@code action} gets true on Ctrl+click; {@code makeMain}: click on a hollow star. */
    ShotTile(int x, int y, int w, int h, String key, int number, String name, boolean live, int star, Consumer<Boolean> action, Runnable makeMain) {
        super(x, y, w, h, Component.literal(number + " " + name));
        this.key = key;
        this.number = String.valueOf(number);
        this.live = live;
        this.star = star;
        this.action = action;
        this.makeMain = makeMain;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onStar = star == STAR && event.x() >= getX() + getWidth() - 1 - STAR_W && event.y() >= getY() + getHeight() - 11;
        super.onClick(event, doubleClick);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (onStar) {
            onStar = false;
            makeMain.run();
        } else action.accept(input.hasControlDown());
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
        int sw = star == NO_STAR ? 0 : STAR_W;
        g.text(font, font.plainSubstrByWidth(name, w - 6 - sw), x + 3, y + h - 10, Theme.TEXT, false);
        if (star != NO_STAR) g.text(font, star == MAIN ? "★" : "☆", x + w - 1 - STAR_W + 1, y + h - 10, star == MAIN ? Theme.GOLD : Theme.TEXT_DIM, false);
        int nw = font.width(number) + 6;
        g.fill(x + 1, y + 1, x + 1 + nw, y + 11, live ? LIVE : 0xC0100A0C);
        g.text(font, number, x + 4, y + 2, live ? 0xFFFFFFFF : Theme.GOLD, false);
        if (live) {
            String d = "● DIRECT";
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
