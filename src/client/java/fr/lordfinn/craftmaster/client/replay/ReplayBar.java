package fr.lordfinn.craftmaster.client.replay;

import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The playback bar, at the bottom of the screen while a replay plays: the same in every camera mode and every file
 * (zone or a player), so switching never moves it. Line 1: play / pause, time, speed, what is watched and who
 * directs; line 2: the timeline with the recording's markers (coloured ticks) and the playhead. In broadcast mode it
 * fades out after a few seconds without a replay key (the picture stays clean for a capture); H hides it.
 */
final class ReplayBar {

    private ReplayBar() {}

    static final int MARGIN = 8, HEIGHT = 30;
    private static final int BG = 0xB0101018, TRACK = 0xFF3A3A48, FILL = 0xFFF2C14E, TEXT = 0xFFFFFFFF, SOFT = 0xFFB8B8C8;
    private static final long SHOW_MS = 4_000;

    /** The timeline's left, right and middle line (gui pixels), for the panel's clicks. */
    static int trackLeft(int guiWidth) {
        return MARGIN + 6;
    }

    static int trackRight(int guiWidth) {
        return guiWidth - MARGIN - 6;
    }

    static int trackY(int guiHeight) {
        return guiHeight - MARGIN - 7;
    }

    static int top(int guiHeight) {
        return guiHeight - MARGIN - HEIGHT;
    }

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!ReplayClient.active() || ReplayClient.hidden()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean panel = mc.gui.screen() instanceof ReplayScreen;
        float alpha = 1;
        if (Broadcast.active() && !panel) {
            long since = System.currentTimeMillis() - ReplayClient.lastInput;
            if (since > SHOW_MS) alpha = Math.max(0, 1 - (since - SHOW_MS) / 600f);
            if (ReplayClient.paused()) alpha = 1;
        }
        if (alpha <= 0) return;
        draw(g, mc.font, alpha, panel ? hoverMarker(g.guiWidth(), g.guiHeight(), mouseX(mc), mouseY(mc)) : null);
    }

    private static double mouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    private static double mouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private static int a(int color, float alpha) {
        return ((int) (((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0xFFFFFF);
    }

    static void draw(GuiGraphicsExtractor g, Font font, float alpha, Marker hover) {
        ReplayStatus s = ReplayClient.status();
        int w = g.guiWidth(), h = g.guiHeight();
        int x0 = MARGIN, x1 = w - MARGIN, y0 = top(h), y1 = h - MARGIN;
        g.fill(x0, y0, x1, y1, a(BG, alpha));

        long now = ReplayClient.nowMs(), dur = Math.max(1, s.duration());
        String left = (ReplayClient.paused() ? "❚❚ " : "▶ ") + ReplayClient.time(now) + " / " + ReplayClient.time(s.duration())
                + "   ×" + speed(s.speed());
        g.text(font, Component.literal(left).getVisualOrderText(), x0 + 6, y0 + 5, a(TEXT, alpha), true);
        String view = s.view().isEmpty() ? "" : s.view().equals("zone") ? "Zone" : "Vue de " + s.view();
        String right = (view.isEmpty() ? "" : view + " · ") + "Caméra : " + ReplayDirector.mode().label;
        int rw = font.width(right);
        g.text(font, Component.literal(right).getVisualOrderText(), x1 - 6 - rw, y0 + 5, a(SOFT, alpha), true);
        // The title in the middle, if there is room
        int lw = font.width(left);
        String title = s.title();
        int room = x1 - 6 - rw - (x0 + 6 + lw) - 24;
        if (room > 40) {
            String t = font.plainSubstrByWidth(title, room);
            int tx = x0 + 6 + lw + 12 + (room - font.width(t)) / 2;
            g.text(font, Component.literal(t).getVisualOrderText(), tx, y0 + 5, a(SOFT, alpha), true);
        }

        int tl = trackLeft(w), tr = trackRight(w), ty = trackY(h);
        g.fill(tl, ty - 1, tr, ty + 2, a(TRACK, alpha));
        int px = tl + (int) Math.round((tr - tl) * Math.min(1.0, now / (double) dur));
        g.fill(tl, ty - 1, px, ty + 2, a(FILL, alpha));
        for (Marker m : ReplayClient.markers()) {
            int mx = tl + (int) Math.round((tr - tl) * Math.min(1.0, m.ms() / (double) dur));
            int c = 0xFF000000 | (m.color() & 0xFFFFFF);
            g.fill(mx, ty - 4, mx + 1, ty + 5, a(c, alpha));
        }
        g.fill(px - 1, ty - 5, px + 1, ty + 6, a(TEXT, alpha));
        if (hover != null) {
            String label = ReplayClient.time(hover.ms()) + "  " + hover.name();
            int hx = tl + (int) Math.round((tr - tl) * Math.min(1.0, hover.ms() / (double) dur));
            int lw2 = font.width(label);
            int bx = Math.max(x0, Math.min(x1 - lw2 - 8, hx - lw2 / 2 - 4));
            g.fill(bx, y0 - 14, bx + lw2 + 8, y0 - 2, 0xE0101018);
            g.text(font, Component.literal(label).getVisualOrderText(), bx + 4, y0 - 12, 0xFF000000 | (hover.color() & 0xFFFFFF), true);
        }
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
        if (s == null || my < trackY(h) - 7 || my > trackY(h) + 7) return -1;
        int tl = trackLeft(w), tr = trackRight(w);
        if (mx < tl - 4 || mx > tr + 4) return -1;
        return Math.round(Math.max(0, Math.min(1, (mx - tl) / (double) (tr - tl))) * s.duration());
    }
}
