package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;

/**
 * Saving a changed pose: asked first, with the name still editable — another name saves a new pose and leaves the
 * old one (and the portraits wearing it) alone. Leaving the editor, a third choice drops the changes.
 */
public class PoseSaveScreen extends Screen {

    private final Screen back;
    private final String original;
    private final Consumer<String> save;
    /** Leaving the editor: drops the changes (null: only saving is asked). */
    private final Runnable discard;
    private EditBox nameBox;
    private UiButton confirm;
    private int x0, y0, w, h;

    /**
     * @param save    called with the name to save under, back on {@code back}
     * @param discard leaving the editor: called to drop the changes (null: no such choice)
     */
    public PoseSaveScreen(Screen back, String name, Consumer<String> save, Runnable discard) {
        super(Component.translatable(discard != null ? "screen.craftmaster.pose.confirm.title_leave" : "screen.craftmaster.pose.confirm.title_new"));
        this.back = back;
        this.original = name;
        this.save = save;
        this.discard = discard;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.pose.confirm." + key, args);
    }

    /** How many portraits around (loaded on this game) wear the pose {@code name}. */
    static int wornBy(String name) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0;
        int n = 0;
        for (var e : level.entitiesForRendering()) if (e instanceof PortraitEntity p && p.poseName().equals(name)) n++;
        return n;
    }

    private String typed() {
        return Pose.normalizeName(nameBox.getValue());
    }

    @Override
    protected void init() {
        w = 240;
        h = 104;
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        String value = nameBox == null ? original : nameBox.getValue();
        nameBox = new EditBox(font, x0 + 8, y0 + 50, w - 16, 18, Component.translatable("screen.craftmaster.pose.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(value);
        nameBox.setResponder(v -> updateConfirm());
        addRenderableWidget(nameBox);
        int by = y0 + h - 28;
        if (discard == null) {
            int bw = (w - 20) / 2;
            confirm = addRenderableWidget(UiButton.of(x0 + 6, by, bw, 20, Component.empty(), this::confirm));
            addRenderableWidget(UiButton.of(x0 + 14 + bw, by, bw, 20, Component.translatable("gui.cancel"), this::onClose));
        } else {
            // Save / don't save / back to the editor
            int bw = (w - 24) / 3;
            confirm = addRenderableWidget(UiButton.of(x0 + 6, by, bw, 20, Component.empty(), this::confirm));
            addRenderableWidget(UiButton.of(x0 + 12 + bw, by, bw, 20, t("discard"), () -> {
                minecraft.gui.setScreen(back);
                discard.run();
            }));
            addRenderableWidget(UiButton.of(x0 + 18 + 2 * bw, by, bw, 20, t("back"), this::onClose));
        }
        setInitialFocus(nameBox);
        updateConfirm();
    }

    private void updateConfirm() {
        String n = typed();
        confirm.active = !n.isEmpty();
        confirm.setMessage(PoseLibraries.client().has(n) ? t("overwrite") : t("save_as"));
    }

    private void confirm() {
        String n = typed();
        if (n.isEmpty()) return;
        minecraft.gui.setScreen(back);
        save.accept(n);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        String n = typed();
        boolean exists = PoseLibraries.client().has(n);
        Ui.panel(g, font, x0, y0, w, h, discard != null ? getTitle() : exists ? t("title") : t("title_new"));
        Component line;
        if (!exists) line = t("new_name", n, original);
        else {
            int worn = wornBy(n);
            line = worn == 0 ? t("exists", n) : t("exists_worn", n, worn);
        }
        int y = y0 + 22;
        for (var l : font.split(line, w - 16)) {
            g.text(font, l, x0 + 8, y, exists ? Theme.GOLD : Theme.TEXT, false);
            y += 10;
        }
        super.extractRenderState(g, mx, my, partial);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if ((e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) && confirm.active) {
            confirm();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
