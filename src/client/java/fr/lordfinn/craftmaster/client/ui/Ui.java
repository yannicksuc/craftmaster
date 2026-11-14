package fr.lordfinn.craftmaster.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Drawing helpers of the UI kit: panels, scaled text, flat player faces. */
public final class Ui {

    private Ui() {}

    /** The CraftMaster panel: dark body, golden border, red velvet title bar (none if {@code title} is null). */
    public static void panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component title) {
        g.fill(x, y, x + w, y + h, Theme.PANEL);
        if (title != null) {
            g.fill(x, y, x + w, y + 18, Theme.VELVET);
            g.fill(x, y + 15, x + w, y + 18, Theme.VELVET_DARK);
            g.horizontalLine(x + 1, x + w - 2, y + 18, Theme.BORDER);
            g.centeredText(font, title, x + w / 2, y + 5, Theme.TITLE);
        }
        g.outline(x, y, w, h, Theme.BORDER);
        g.outline(x + 1, y + 1, w - 2, h - 2, Theme.BORDER_DARK);
    }

    /**
     * A button drawn like {@link UiButton} (velvet face, golden edge when hovered or on), for screens that lay out
     * their own controls every frame (timelines, inspectors).
     */
    /** Tooltip width (pixels): a tooltip wraps there, never one long line across the screen. */
    public static final int TOOLTIP_WIDTH = 180;

    /** A tooltip, wrapped (and cut at its line breaks): every menu's tooltips go through here. */
    public static void tooltip(GuiGraphicsExtractor g, Font font, Component text, int mx, int my) {
        g.setTooltipForNextFrame(font, font.split(text, TOOLTIP_WIDTH), mx, my);
    }

    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean on, boolean hover, boolean active) {
        int face = !active ? Theme.BUTTON_OFF : on ? Theme.BUTTON_ON : hover ? Theme.BUTTON_HOVER : Theme.BUTTON;
        g.fill(x, y, x + w, y + h, Theme.shade(face, -0.5f));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.horizontalLine(x + 1, x + w - 2, y + 1, Theme.shade(face, 0.25f));
        g.horizontalLine(x + 1, x + w - 2, y + h - 2, Theme.shade(face, -0.3f));
        if (active && (hover || on)) g.outline(x, y, w, h, Theme.BORDER);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, active ? Theme.TEXT : Theme.TEXT_DIM);
    }

    /** A sheet of cream paper (lists, tables). */
    public static void paper(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Theme.PAPER);
        g.outline(x, y, w, h, Theme.PAPER_EDGE);
    }

    /** Text centred on (cx, cy), {@code scale} times the normal size. */
    public static void text(GuiGraphicsExtractor g, Font font, Component text, float cx, float cy, float scale, int color, boolean shadow) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, -4, color, shadow);
        pose.popMatrix();
    }

    private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();

    /**
     * A player's face drawn flat (2D, hat layer included), {@code size} pixels square. Online players use the skin
     * the game already has; the others are looked up by name (a default skin until it arrives).
     */
    public static void face(GuiGraphicsExtractor g, String player, float x, float y, float size) {
        Minecraft mc = Minecraft.getInstance();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 32f, size / 32f);
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player);
        if (info != null) PlayerFaceExtractor.extractRenderState(g, info.getSkin(), 0, 0, 32);
        else PlayerFaceExtractor.extractRenderState(g, PROFILES.computeIfAbsent(player.toLowerCase(Locale.ROOT),
                p -> ResolvableProfile.createUnresolved(player)), 0, 0, 32);
        pose.popMatrix();
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
