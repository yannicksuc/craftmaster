package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.studio.ImageRequest;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import fr.lordfinn.craftmaster.studio.StudioNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings of a studio screen (organisers with the image permission, right click): image address, size in blocks,
 * whole image or stretched. The world stays visible on the right.
 */
public class ImageScreenSettings extends Screen {

    private static final int PANEL = 0xD0101018, BORDER = 0xFFFFC23D, LABEL = 0xFFBFBFCF;

    private final ImageScreenEntity screen;
    private EditBox url, widthBox, heightBox;
    private CycleButton<Boolean> fit;
    private Component error;
    private int left, top;

    public ImageScreenSettings(ImageScreenEntity screen) {
        super(Component.translatable("screen.craftmaster.studio_screen"));
        this.screen = screen;
    }

    @Override
    protected void init() {
        left = 10;
        top = Math.max(2, height / 2 - 70);
        int x = left + 82;
        url = box(x, top + 24, 196, screen.url(), "url", ImageRequest.MAX_URL_LENGTH);
        url.moveCursorToStart(false);
        url.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.studio_screen.url_hint")));
        widthBox = box(x, top + 48, 40, String.valueOf(screen.width()), "width", 2);
        heightBox = box(x + 60, top + 48, 40, String.valueOf(screen.height()), "height", 2);
        fit = addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.studio_screen.fit_whole"),
                Component.translatable("screen.craftmaster.studio_screen.fit_stretch"), screen.fit()).displayOnlyValue()
                .create(x, top + 72, 120, 20, Component.translatable("screen.craftmaster.studio_screen.fit")));
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.studio_screen.test_card"), b -> url.setValue(""))
                .bounds(x + 124, top + 72, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.portrait.save"), b -> save())
                .bounds(left + 8, top + 104, 132, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 146, top + 104, 132, 20).build());
        setInitialFocus(url);
    }

    /** The maximum length is set before the value (a long address would be cut otherwise). */
    private EditBox box(int x, int y, int w, String value, String key, int maxLength) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.translatable("screen.craftmaster.studio_screen." + key));
        b.setMaxLength(maxLength);
        b.setValue(value == null ? "" : value);
        return addRenderableWidget(b);
    }

    private void save() {
        String u = url.getValue().trim();
        if (!u.isEmpty() && ImageRequest.urlError(u) != null) {
            error = Component.literal(ImageRequest.urlError(u));
            return;
        }
        int w, h;
        try {
            w = Integer.parseInt(widthBox.getValue().trim());
            h = Integer.parseInt(heightBox.getValue().trim());
        } catch (NumberFormatException e) {
            w = h = -1;
        }
        if (w < 1 || w > ImageRequest.MAX_WIDTH || h < 1 || h > ImageRequest.MAX_HEIGHT) {
            error = Component.translatable("screen.craftmaster.studio_screen.bad_size", ImageRequest.MAX_WIDTH, ImageRequest.MAX_HEIGHT);
            return;
        }
        ClientPlayNetworking.send(new StudioNetwork.SaveScreen(screen.getId(), u, w, h, fit.getValue()));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No blur nor darkening: the screen stays visible behind. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int w = 288, h = 140;
        g.fill(left, top, left + w, top + h, PANEL);
        g.outline(left, top, w, h, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.studio_screen").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + 8, top + 8, 0xFFFFFFFF);
        g.text(font, Component.translatable("screen.craftmaster.studio_screen.url"), left + 8, top + 30, LABEL);
        g.text(font, Component.translatable("screen.craftmaster.studio_screen.size"), left + 8, top + 54, LABEL);
        g.text(font, Component.literal("×"), left + 82 + 47, top + 54, LABEL);
        g.text(font, Component.translatable("screen.craftmaster.studio_screen.fit"), left + 8, top + 78, LABEL);
        if (error != null) g.text(font, error.copy().withStyle(ChatFormatting.RED), left + 8, top + h - 12, 0xFFFF5555);
        else g.text(font, Component.translatable("screen.craftmaster.studio_screen.hint").withStyle(ChatFormatting.DARK_GRAY), left + 8, top + h - 12, LABEL);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
