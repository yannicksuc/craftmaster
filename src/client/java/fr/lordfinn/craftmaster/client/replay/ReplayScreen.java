package fr.lordfinn.craftmaster.client.replay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The mouse on the replay's timeline (key M or {@code /cmbarre}), nothing else on screen: a click seeks, a marker
 * under the mouse shows its name, the wheel steps 5 s. The rest is driven by voice, keys and Stream Deck. M or Esc
 * gives the mouse back to the game.
 */
final class ReplayScreen extends Screen {

    ReplayScreen() {
        super(Component.literal("Replay"));
    }

    @Override
    public void tick() {
        if (!ReplayClient.active()) onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        long t = ReplayBar.timeAt(width, height, e.x(), e.y());
        if (t < 0) return super.mouseClicked(e, doubleClick);
        ReplayClient.seek(t);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        ReplayClient.step(scrollY > 0 ? 5_000 : -5_000);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ReplayClient.PANEL_KEY.matches(event)) {
            onClose();
            return true;
        }
        if (ReplayClient.PLAY_KEY.matches(event)) {
            ReplayClient.togglePause();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // Closed by the user (Esc, M): not reopened; replaced by a loading screen (a seek back, a file switch): it is
        ReplayClient.panelWanted = false;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}
}
