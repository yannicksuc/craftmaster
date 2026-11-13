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
 * bottom in the show's colours, the time on its left and the length on its right, the recording's key moments as small
 * icons in the colour of who they are about ({@link ReplayMarkers}). It shows for a few seconds after an order (key, voice, Stream Deck), while paused and while the mouse is on
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
        if (alpha > 0) draw(g, mc.font, alpha, mouse ? hoverGroup(g.guiWidth(), g.guiHeight(), ReplayScreen.mouseX, ReplayScreen.mouseY) : null);
        long c = now - captionAt;
        if (!caption.isEmpty() && c < CAPTION_MS) drawCaption(g, mc.font, c < CAPTION_MS - 400 ? 1 : (CAPTION_MS - c) / 400f);
    }

    private static int c(int rgb, float alpha) {
        return ((int) (255 * Math.max(0, Math.min(1, alpha))) << 24) | rgb;
    }

    private static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, Component.literal(s).getVisualOrderText(), x, y, color, true);
    }

    static void draw(GuiGraphicsExtractor g, Font font, float alpha, ReplayMarkers.Group hover) {
        ReplayStatus s = ReplayClient.status();
        int w = g.guiWidth(), h = g.guiHeight();
        int tl = trackLeft(w), tr = trackRight(w), ty = trackY(h);
        long now = ReplayClient.nowMs(), dur = Math.max(1, s.duration());

        // A soft shade under it, so it reads on a bright sky without a box
        for (int i = 0; i < 8; i++) g.fill(0, h - 38 + i * 5, w, h - 33 + i * 5, c(0x000000, alpha * 0.05f * (i + 1)));

        g.fill(tl, ty, tr, ty + 2, c(TRACK, alpha * 0.7f));
        int px = tl + (int) Math.round((tr - tl) * Math.min(1.0, now / (double) dur));
        g.fill(tl, ty, px, ty + 2, c(GOLD, alpha));
        // The key moments: an icon each in the colour of who it is about; too close together, the first one stands
        // for them all with a small dot above it
        for (ReplayMarkers.Group gr : ReplayMarkers.groups(tl, tr, dur)) {
            boolean on = hover != null && hover.x() == gr.x();
            ReplayMarkers.draw(g, gr.first(), gr.x(), ty - 1, alpha * (on ? 1 : 0.9f), on);
            if (gr.markers().size() > 1) g.fill(gr.x(), ty - (on ? 15 : 9), gr.x() + 1, ty - (on ? 14 : 8), c(CREAM, alpha * 0.8f));
        }
        g.fill(px - 1, ty - 2, px + 2, ty + 4, c(CREAM, alpha));

        String left = (ReplayClient.paused() ? "❚❚  " : "") + ReplayClient.time(now);
        text(g, font, left, tl, ty - 18, c(CREAM, alpha));
        String right = ReplayClient.time(s.duration());
        text(g, font, right, tr - font.width(right), ty - 18, c(CREAM, alpha * 0.6f));

        if (hover != null) {
            // The bubble: the moments of the group (at most 5), each line with its time and a stripe of its colour
            java.util.List<Marker> list = hover.markers();
            int shown = Math.min(5, list.size());
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (int i = 0; i < shown; i++) lines.add(ReplayClient.time(list.get(i).ms()) + "  " + list.get(i).name());
            if (list.size() > shown) lines.add("… et " + (list.size() - shown) + " autre(s)");
            int lw = 0;
            for (String l : lines) lw = Math.max(lw, font.width(l));
            int lh = 11, bh = lines.size() * lh + 4, bottom = ty - 21;
            int bx = Math.max(4, Math.min(w - lw - 16, hover.x() - lw / 2 - 6));
            g.fill(bx, bottom - bh, bx + lw + 12, bottom, c(INK, 0.85f));
            for (int i = 0; i < lines.size(); i++) {
                int y = bottom - bh + 3 + i * lh;
                if (i < shown) g.fill(bx + 3, y, bx + 5, y + 8, c(list.get(i).color() & 0xFFFFFF, 1));
                text(g, font, lines.get(i), bx + 8, y, c(i < shown ? CREAM : TRACK, 1));
            }
        }
        // A filter on: said discreetly on the right, above the length
        if (ReplayMarkers.filtered()) {
            String f = ReplayMarkers.caption();
            text(g, font, f, tr - font.width(f), ty - 29, c(TRACK, alpha * 0.9f));
        }
    }

    private static void drawCaption(GuiGraphicsExtractor g, Font font, float alpha) {
        int w = g.guiWidth(), h = g.guiHeight();
        int tw = font.width(caption);
        int x = (w - tw) / 2, y = trackY(h) - 36;
        g.fill(x - 6, y - 3, x + tw + 6, y + 10, c(INK, alpha * 0.8f));
        g.fill(x - 6, y + 10, x + tw + 6, y + 11, c(GOLD, alpha));
        text(g, font, caption, x, y, c(CREAM, alpha));
    }

    static String speed(float x) {
        return x == Math.round(x) ? String.valueOf(Math.round(x)) : String.valueOf(x).replace('.', ',');
    }

    /** The key moments under the mouse on the timeline (the icons stand above it), or null. */
    static ReplayMarkers.Group hoverGroup(int w, int h, double mx, double my) {
        ReplayStatus s = ReplayClient.status();
        int ty = trackY(h);
        if (s == null || my < ty - 12 || my > ty + 5) return null;
        ReplayMarkers.Group best = null;
        double bestD = 4;
        for (ReplayMarkers.Group gr : ReplayMarkers.groups(trackLeft(w), trackRight(w), s.duration())) {
            if (Math.abs(gr.x() - mx) < bestD) {
                bestD = Math.abs(gr.x() - mx);
                best = gr;
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
