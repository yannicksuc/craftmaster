package fr.lordfinn.craftmaster.client.replay;

import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The replay's only thing on screen, kept sober (the picture is captured for the viewers): a thin timeline at the
 * bottom in the show's colours, the time on its left and the length on its right, the recording's markers as small
 * ticks. It shows for a few seconds after an order (key, voice, Stream Deck), while paused and while the mouse is on
 * it (M); otherwise the picture is clean. A one-line caption above it says what an order just changed ("×2",
 * "Caméra 2", "Vue d'Ensuri"), then fades. H hides it for good.
 */
final class ReplayBar {

    private ReplayBar() {}

    /** Space at the sides and under the timeline (gui pixels). */
    static final int SIDE = 24, BOTTOM = 12;
    private static final int CREAM = 0xF6EBCF, GOLD = 0xF2C14E, INK = 0x2B1A10, TRACK = 0x8A7F6A;
    /** Shown this long after an order, then fades out over FADE_MS. */
    private static final long SHOW_MS = 3_000, FADE_MS = 500, CAPTION_MS = 1_800;

    private static String caption = "";
    private static long captionAt;

    /** A short caption for what an order just changed. */
    static void caption(String text) {
        caption = text;
        captionAt = System.currentTimeMillis();
        ReplayClient.lastInput = captionAt;
    }

    static int trackLeft(int w) {
        return SIDE;
    }

    static int trackRight(int w) {
        return w - SIDE;
    }

    static int trackY(int h) {
        return h - BOTTOM;
    }

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!ReplayClient.active() || ReplayClient.hidden()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean mouse = mc.gui.screen() instanceof ReplayScreen;
        long now = System.currentTimeMillis();
        float alpha = 1;
        if (!mouse && !ReplayClient.paused()) {
            long since = now - ReplayClient.lastInput;
            if (since > SHOW_MS) alpha = Math.max(0, 1 - (since - SHOW_MS) / (float) FADE_MS);
        }
        if (alpha > 0) draw(g, mc.font, alpha, mouse ? hoverMarker(g.guiWidth(), g.guiHeight(), mouseX(mc), mouseY(mc)) : null);
        long c = now - captionAt;
        if (!caption.isEmpty() && c < CAPTION_MS) drawCaption(g, mc.font, c < CAPTION_MS - 400 ? 1 : (CAPTION_MS - c) / 400f);
    }

    private static double mouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    private static double mouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private static int c(int rgb, float alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | rgb;
    }

    private static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, Component.literal(s).getVisualOrderText(), x, y, color, true);
    }

    static void draw(GuiGraphicsExtractor g, Font font, float alpha, Marker hover) {
        ReplayStatus s = ReplayClient.status();
        int w = g.guiWidth(), h = g.guiHeight();
        int tl = trackLeft(w), tr = trackRight(w), ty = trackY(h);
        long now = ReplayClient.nowMs(), dur = Math.max(1, s.duration());

        // A soft shade under it, so it reads on a bright sky without a box
        for (int i = 0; i < 6; i++) g.fill(0, h - 30 + i * 5, w, h - 25 + i * 5, c(0x000000, alpha * 0.06f * (i + 1)));

        g.fill(tl, ty, tr, ty + 2, c(TRACK, alpha * 0.7f));
        int px = tl + (int) Math.round((tr - tl) * Math.min(1.0, now / (double) dur));
        g.fill(tl, ty, px, ty + 2, c(GOLD, alpha));
        for (Marker m : ReplayClient.markers()) {
            int mx = tl + (int) Math.round((tr - tl) * Math.min(1.0, m.ms() / (double) dur));
            g.fill(mx, ty - 3, mx + 1, ty, c(m.color() & 0xFFFFFF, alpha * 0.9f));
        }
        g.fill(px - 1, ty - 2, px + 2, ty + 4, c(CREAM, alpha));

        String left = (ReplayClient.paused() ? "❚❚  " : "") + ReplayClient.time(now);
        text(g, font, left, tl, ty - 12, c(CREAM, alpha));
        String right = ReplayClient.time(s.duration());
        text(g, font, right, tr - font.width(right), ty - 12, c(CREAM, alpha * 0.6f));

        if (hover != null) {
            String label = ReplayClient.time(hover.ms()) + "  " + hover.name();
            int hx = tl + (int) Math.round((tr - tl) * Math.min(1.0, hover.ms() / (double) dur));
            int lw = font.width(label);
            int bx = Math.max(4, Math.min(w - lw - 12, hx - lw / 2 - 4));
            g.fill(bx, ty - 30, bx + lw + 8, ty - 17, c(INK, 0.85f));
            g.fill(bx, ty - 17, bx + lw + 8, ty - 16, c(hover.color() & 0xFFFFFF, 1));
            text(g, font, label, bx + 4, ty - 27, c(CREAM, 1));
        }
    }

    private static void drawCaption(GuiGraphicsExtractor g, Font font, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        int tw = font.width(caption);
        int x = (w - tw) / 2, y = trackY(h) - 30;
        g.fill(x - 6, y - 3, x + tw + 6, y + 10, c(INK, alpha * 0.8f));
        g.fill(x - 6, y + 10, x + tw + 6, y + 11, c(GOLD, alpha));
        text(g, font, caption, x, y, c(CREAM, alpha));
    }

    static String speed(float x) {
        return x == Math.round(x) ? String.valueOf(Math.round(x)) : String.valueOf(x).replace('.', ',');
    }

    /** The marker under the mouse on the timeline, or null. */
    static Marker hoverMarker(int w, int h, double mx, double my) {
        ReplayStatus s = ReplayClient.status();
        if (s == null || Math.abs(my - trackY(h)) > 6) return null;
        int tl = trackLeft(w), tr = trackRight(w);
        long dur = Math.max(1, s.duration());
        Marker best = null;
        double bestD = 4;
        for (Marker m : ReplayClient.markers()) {
            double x = tl + (tr - tl) * Math.min(1.0, m.ms() / (double) dur);
            if (Math.abs(x - mx) < bestD) {
                bestD = Math.abs(x - mx);
                best = m;
            }
        }
        return best;
    }

    /** The file time under the mouse on the timeline, or -1. */
    static long timeAt(int w, int h, double mx, double my) {
        ReplayStatus s = ReplayClient.status();
        if (s == null || my < trackY(h) - 8 || my > trackY(h) + 8) return -1;
        int tl = trackLeft(w), tr = trackRight(w);
        if (mx < tl - 4 || mx > tr + 4) return -1;
        return Math.round(Math.max(0, Math.min(1, (mx - tl) / (double) (tr - tl))) * s.duration());
    }
}
