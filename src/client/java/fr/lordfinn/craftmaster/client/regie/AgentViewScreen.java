package fr.lordfinn.craftmaster.client.regie;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;

/**
 * The pictures the régie agent just looked at ({@link AgentVision}), on the organiser's screen: one at a time, as
 * large as the screen allows, ◀ ▶ (or the arrow keys, or the mouse wheel) to go through them, its caption under it.
 */
public class AgentViewScreen extends Screen {

    private final AgentVision.View view;
    private int index;
    private Identifier texture;
    private int texW = 16, texH = 9;

    AgentViewScreen(AgentVision.View view) {
        super(Component.literal("Ce que voit la régie IA"));
        this.view = view;
    }

    @Override
    protected void init() {
        int y = height - 26;
        Button prev = Button.builder(Component.literal("◀"), b -> show(index - 1)).bounds(width / 2 - 70, y, 30, 20).build();
        prev.setTooltip(Tooltip.create(Component.literal("Image précédente (← ou molette)")));
        Button next = Button.builder(Component.literal("▶"), b -> show(index + 1)).bounds(width / 2 + 40, y, 30, 20).build();
        next.setTooltip(Tooltip.create(Component.literal("Image suivante (→ ou molette)")));
        addRenderableWidget(prev);
        addRenderableWidget(next);
        show(index);
    }

    private void show(int i) {
        if (view.images().isEmpty()) return;
        index = Math.floorMod(i, view.images().size());
        release();
        try (InputStream in = Files.newInputStream(view.images().get(index))) {
            NativeImage img = NativeImage.read(in);
            texW = img.getWidth();
            texH = img.getHeight();
            texture = CraftMaster.id("regie_view/" + System.identityHashCode(this) + "_" + index);
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "craftmaster agent view", img));
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Régie IA] Image illisible : {}", view.images().get(index), e);
            texture = null;
        }
    }

    private void release() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
        texture = null;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        show(index + (dy < 0 ? 1 : -1));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            show(index - 1);
            return true;
        }
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            show(index + 1);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xE0100A0C);
        g.centeredText(font, Component.literal(view.title()).withStyle(ChatFormatting.BOLD), width / 2, 6, 0xFFFFE9A8);
        int top = 20, bottom = height - 44;
        int maxW = width - 20, maxH = bottom - top;
        double scale = Math.min(maxW / (double) texW, maxH / (double) texH);
        int w = (int) (texW * scale), h = (int) (texH * scale);
        int x = (width - w) / 2, y = top + (maxH - h) / 2;
        if (texture != null) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
            g.blit(texture, x, y, x + w, y + h, 0, 1, 0, 1);
        }
        String caption = (index + 1) + " / " + view.images().size() + " — " + view.captions().get(index);
        g.centeredText(font, Component.literal(font.plainSubstrByWidth(caption, width - 20)), width / 2, height - 40, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public void removed() {
        release();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
