package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** A row of tabs (UiButtons, the selected one drawn pressed). The screen adds {@link #buttons()} as widgets. */
public final class UiTabs {

    private final List<UiButton> buttons = new ArrayList<>();

    /**
     * @param selected which tab is selected (read at each frame)
     * @param onSelect called with the index of the clicked tab
     */
    public UiTabs(Font font, int x, int y, int h, List<Component> labels, IntSupplier selected, IntConsumer onSelect) {
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            int w = font.width(labels.get(i)) + 14;
            buttons.add(UiButton.of(cx, y, w, h, labels.get(i), () -> onSelect.accept(index)).on(() -> selected.getAsInt() == index));
            cx += w + 2;
        }
    }

    public List<UiButton> buttons() {
        return buttons;
    }

    /** Total width of the row. */
    public int width() {
        if (buttons.isEmpty()) return 0;
        UiButton last = buttons.getLast();
        return last.getX() + last.getWidth() - buttons.getFirst().getX();
    }
}
