package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import fr.lordfinn.craftmaster.portrait.PortraitNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Renames a pose of the library; the portraits wearing it keep it under its new name. */
public class PoseRenameScreen extends Screen {

    private final Screen back;
    private final String original;
    private EditBox nameBox;
    private UiButton confirm;
    private int x0, y0, w, h;

    public PoseRenameScreen(Screen back, String name) {
        super(Component.translatable("screen.craftmaster.pose.rename.title"));
        this.back = back;
        this.original = name;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.pose.rename." + key, args);
    }

    private String typed() {
        return Pose.normalizeName(nameBox.getValue());
    }

    /** Empty, or the name of another pose. */
    private boolean taken() {
        String n = typed();
        return !n.equals(original) && PoseLibraries.client().has(n);
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
        nameBox.setResponder(v -> confirm.active = canRename());
        addRenderableWidget(nameBox);
        int bw = (w - 20) / 2, by = y0 + h - 28;
        confirm = addRenderableWidget(UiButton.of(x0 + 6, by, bw, 20, t("confirm"), this::confirm));
        addRenderableWidget(UiButton.of(x0 + 14 + bw, by, bw, 20, Component.translatable("gui.cancel"), this::onClose));
        setInitialFocus(nameBox);
        confirm.active = canRename();
    }

    private boolean canRename() {
        String n = typed();
        return !n.isEmpty() && !n.equals(original) && !taken();
    }

    private void confirm() {
        if (!canRename()) return;
        ClientPlayNetworking.send(new PortraitNetwork.RenamePose(original, typed()));
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Ui.panel(g, font, x0, y0, w, h, getTitle());
        boolean taken = taken();
        int worn = PoseSaveScreen.wornBy(original);
        Component line = taken ? t("taken", typed()) : worn == 0 ? t("line", original) : t("line_worn", original, worn);
        int y = y0 + 22;
        for (var l : font.split(line, w - 16)) {
            g.text(font, l, x0 + 8, y, taken ? Theme.ERROR : Theme.TEXT, false);
            y += 10;
        }
        super.extractRenderState(g, mx, my, partial);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) {
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
