package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.PoseHitTest;
import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The pose library as a gallery: every saved pose drawn in its frame (the portrait's skin and window look), its
 * name under it. A click starts the editor from that pose; the pencil of a card renames it, its cross deletes it
 * (asked first); the wheel scrolls.
 */
public class PoseGalleryScreen extends Screen {

    private static final int CARD_W = 76, CARD_H = 96, GAP = 6, NAME_H = 12, PEN = 12;

    private final Screen back;
    private String current;
    private final PortraitSkins.Skin skin;
    private final int[] look;
    private final Consumer<String> chosen;
    private final List<Map.Entry<String, Pose>> poses = new ArrayList<>();
    private int x0, y0, w, h, cols, scroll;
    /** The library the cards show: a new one (a pose renamed, saved…) refreshes them. */
    private fr.lordfinn.craftmaster.portrait.PoseLibrary shown;

    /**
     * @param look the window look {on (1/0), depth, size} to draw the poses with
     * @param chosen called with the pose's name, back on {@code back}
     */
    public PoseGalleryScreen(Screen back, String current, PortraitSkins.Skin skin, int[] look, Consumer<String> chosen) {
        super(Component.translatable("screen.craftmaster.pose.gallery"));
        this.back = back;
        this.current = Pose.normalizeName(current);
        this.skin = skin;
        this.look = look;
        this.chosen = chosen;
        refresh();
    }

    private void refresh() {
        shown = PoseLibraries.client();
        current = shown.resolve(current);
        poses.clear();
        poses.addAll(shown.all().entrySet());
        poses.sort(Map.Entry.comparingByKey());
    }

    /** Top-left corner of card {@code i}, or null when it is scrolled out. */
    private int[] card(int i) {
        int row = i / cols - scroll;
        if (row < 0 || row >= visibleRows()) return null;
        return new int[]{x0 + 8 + (i % cols) * (CARD_W + GAP), y0 + 22 + row * (CARD_H + GAP)};
    }

    private boolean onPen(int i, double mx, double my) {
        int[] c = card(i);
        return c != null && Ui.inside(mx, my, c[0] + CARD_W - PEN - 2, c[1] + 2, PEN, PEN);
    }

    /** The cross, left of the pencil: delete. */
    private boolean onCross(int i, double mx, double my) {
        int[] c = card(i);
        return c != null && Ui.inside(mx, my, c[0] + CARD_W - 2 * PEN - 4, c[1] + 2, PEN, PEN);
    }

    @Override
    protected void init() {
        cols = Math.max(1, Math.min(6, (width - 40) / (CARD_W + GAP)));
        w = cols * (CARD_W + GAP) - GAP + 16;
        int rows = Math.max(1, (poses.size() + cols - 1) / cols);
        int fit = Math.max(1, (height - 50) / (CARD_H + GAP));
        h = Math.min(rows, fit) * (CARD_H + GAP) - GAP + 30;
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int visibleRows() {
        return Math.max(1, (h - 30 + GAP) / (CARD_H + GAP));
    }

    private int maxScroll() {
        int rows = (poses.size() + cols - 1) / cols;
        return Math.max(0, rows - visibleRows());
    }

    /** The card under the mouse, or -1. */
    private int cardAt(double mx, double my) {
        for (int i = 0; i < poses.size(); i++) {
            int[] c = card(i);
            if (c != null && Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H)) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (shown != PoseLibraries.client()) {
            refresh();
            init();
        }
        Ui.panel(g, font, x0, y0, w, h, getTitle());
        int hovered = cardAt(mx, my);
        Component tip = null;
        String fullName = null;
        for (int i = 0; i < poses.size(); i++) {
            int row = i / cols - scroll;
            if (row < 0 || row >= visibleRows()) continue;
            int cx = x0 + 8 + (i % cols) * (CARD_W + GAP), cy = y0 + 22 + row * (CARD_H + GAP);
            String name = poses.get(i).getKey();
            boolean isCurrent = name.equals(current);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, isCurrent ? Theme.VELVET : Theme.VELVET_DARK);
            if (i == hovered) g.fill(cx, cy, cx + CARD_W, cy + CARD_H, Theme.HOVER);
            g.outline(cx, cy, CARD_W, CARD_H, isCurrent || i == hovered ? Theme.BORDER : Theme.BORDER_DARK);
            preview(g, poses.get(i).getValue(), cx + 1, cy + 1, CARD_W - 2, CARD_H - NAME_H - 2);
            String shown = font.plainSubstrByWidth(name, CARD_W - 6);
            if (!shown.equals(name)) {
                shown = font.plainSubstrByWidth(name, CARD_W - 6 - font.width("…")) + "…";
                if (i == hovered) fullName = name;
            }
            g.centeredText(font, Component.literal(shown), cx + CARD_W / 2, cy + CARD_H - NAME_H + 1, isCurrent ? Theme.GOLD : Theme.TEXT);
            if (i == hovered) {
                // Rename: a pencil in the corner of the card under the mouse
                boolean on = onPen(i, mx, my), off = onCross(i, mx, my);
                Ui.button(g, font, cx + CARD_W - PEN - 2, cy + 2, PEN, PEN, Component.literal("✎"), false, on, true);
                Ui.button(g, font, cx + CARD_W - 2 * PEN - 4, cy + 2, PEN, PEN, Component.literal("✕"), false, off, true);
                if (on) tip = Component.translatable("screen.craftmaster.pose.rename.tip");
                else if (off) tip = Component.translatable("screen.craftmaster.pose.delete");
            }
        }
        if (maxScroll() > 0) {
            // Where the list is: a thin bar on the right
            int trackY = y0 + 22, trackH = h - 30, barH = Math.max(12, trackH * visibleRows() / ((poses.size() + cols - 1) / cols));
            int barY = trackY + (trackH - barH) * scroll / maxScroll();
            g.fill(x0 + w - 5, trackY, x0 + w - 3, trackY + trackH, Theme.BORDER_DARK);
            g.fill(x0 + w - 5, barY, x0 + w - 3, barY + barH, Theme.BORDER);
        }
        super.extractRenderState(g, mx, my, partial);
        if (tip != null) Ui.tooltip(g, font, tip, mx, my);
        else if (fullName != null) Ui.tooltip(g, font, Component.literal(fullName), mx, my);
    }

    /** One pose in its frame, turned a little like the editor's preview, fitted in the box. */
    private void preview(GuiGraphicsExtractor g, Pose pose, int x, int y, int bw, int bh) {
        PortraitRenderer.State s = PortraitRenderer.previewState(pose, skin);
        s.window = look[0] == 1;
        s.windowDepth = look[1];
        s.windowSize = look[2];
        double scale = Math.min(bw / 34.0, bh / 40.0);
        PoseHitTest.View view = new PoseHitTest.View(25, 12, scale, x + bw / 2.0, y + bh / 2.0);
        s.view = PoseEditorScreen.previewMatrix(view);
        g.entity(s, (float) (scale * 16), new Vector3f(0, 0, 0), new Quaternionf(), null, x, y, x + bw, y + bh);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int i = cardAt(e.x(), e.y());
        if (i >= 0 && e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            String name = poses.get(i).getKey();
            if (onPen(i, e.x(), e.y())) {
                minecraft.gui.setScreen(new PoseRenameScreen(this, name));
                return true;
            }
            if (onCross(i, e.x(), e.y())) {
                minecraft.gui.setScreen(new PoseDeleteScreen(this, name));
                return true;
            }
            minecraft.gui.setScreen(back);
            chosen.accept(name);
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
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
