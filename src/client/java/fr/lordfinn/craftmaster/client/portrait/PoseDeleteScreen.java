package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.portrait.PortraitNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Deleting a pose of the library, asked first (the portraits wearing it go back to the basic pose). */
public class PoseDeleteScreen extends Screen {

    private final Screen back;
    private final String pose;
    private int x0, y0, w, h;

    public PoseDeleteScreen(Screen back, String pose) {
        super(Component.translatable("screen.craftmaster.pose.delete_confirm.title"));
        this.back = back;
        this.pose = pose;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.pose.delete_confirm." + key, args);
    }

    @Override
    protected void init() {
        w = 240;
        h = 84;
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        int bw = (w - 20) / 2, by = y0 + h - 28;
        addRenderableWidget(UiButton.of(x0 + 6, by, bw, 20, t("confirm"), () -> {
            ClientPlayNetworking.send(new PortraitNetwork.DeletePose(pose));
            onClose();
        }));
        addRenderableWidget(UiButton.of(x0 + 14 + bw, by, bw, 20, Component.translatable("gui.cancel"), this::onClose));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Ui.panel(g, font, x0, y0, w, h, getTitle());
        int worn = PoseSaveScreen.wornBy(pose);
        Component line = worn == 0 ? t("line", pose) : t("line_worn", pose, worn);
        int y = y0 + 24;
        for (var l : font.split(line, w - 16)) {
            g.text(font, l, x0 + 8, y, Theme.TEXT, false);
            y += 10;
        }
        super.extractRenderState(g, mx, my, partial);
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
