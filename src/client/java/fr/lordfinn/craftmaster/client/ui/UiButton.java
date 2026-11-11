package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A button in CraftMaster's colours (velvet face, golden edge), with an optional "on" state (a tab, a toggle).
 * The press gets the input, so Shift / Ctrl can be read.
 */
public class UiButton extends AbstractButton {

    private final Consumer<InputWithModifiers> onPress;
    private BooleanSupplier on = () -> false;
    private boolean sound = true;

    public UiButton(int x, int y, int w, int h, Component label, Consumer<InputWithModifiers> onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
    }

    public static UiButton of(int x, int y, int w, int h, Component label, Runnable onPress) {
        return new UiButton(x, y, w, h, label, i -> onPress.run());
    }

    /** Drawn as pressed while this is true (selected tab, toggle on). */
    public UiButton on(BooleanSupplier on) {
        this.on = on;
        return this;
    }

    /** No click sound (the caller plays its own). */
    public UiButton silent() {
        sound = false;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(input);
    }

    @Override
    public void playDownSound(SoundManager manager) {
        if (sound) super.playDownSound(manager);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int face = !active ? Theme.BUTTON_OFF : on.getAsBoolean() ? Theme.BUTTON_ON : isHoveredOrFocused() ? Theme.BUTTON_HOVER : Theme.BUTTON;
        g.fill(x, y, x + w, y + h, Theme.shade(face, -0.5f));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.horizontalLine(x + 1, x + w - 2, y + 1, Theme.shade(face, 0.25f));
        g.horizontalLine(x + 1, x + w - 2, y + h - 2, Theme.shade(face, -0.3f));
        if (active && (isHoveredOrFocused() || on.getAsBoolean())) g.outline(x, y, w, h, Theme.BORDER);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int color = active ? Theme.TEXT : Theme.TEXT_DIM;
        g.centeredText(font, getMessage(), x + w / 2, y + (h - 8) / 2, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
