package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Contestant;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Cues;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowState;
import fr.lordfinn.craftmaster.client.task.CurrentTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The broadcast's TV graphics, drawn over the camera picture in broadcast mode: CraftMaster seal and "EN DIRECT",
 * round banner with its timer, lower third (contestant's name), published score strip, live points, opening title
 * and contestant cards, closing credits roll, letterbox bars, and the transitions (dip to black, cross-fade).
 * <p>
 * Everything is laid out on a 270-unit-high canvas (480 wide in 16:9) scaled to the window, so it looks the same at
 * any resolution and GUI scale; gold, red velvet and cream like the show's poster. Every element slides or fades in
 * and out on real time (smooth at any frame rate).
 */
public final class BroadcastOverlay {

    private BroadcastOverlay() {}

    static final Identifier SEAL = CraftMaster.id("textures/gui/broadcast/seal.png");

    // Poster palette
    static final int GOLD = 0xFFE8B84A, GOLD_LIGHT = 0xFFFFE39A, GOLD_DARK = 0xFF8C5E1A;
    static final int VELVET = 0xFF7C1222, VELVET_LIGHT = 0xFFA51E30, VELVET_DARK = 0xFF3E0610;
    static final int CREAM = 0xFFF6EBCF, INK = 0xFF2B1A10, LIVE_RED = 0xFFE0242E;

    /** Design canvas height. */
    static final float CANVAS_H = 270;
    /** Letterbox bar height (2.39:1 inside 16:9). */
    static final float BAR = CANVAS_H * 0.128f;

    // ---------------------------------------------------------------- cues

    private record Timed(String title, String subtitle, String player, long start, long end) {
        double age() { return (System.currentTimeMillis() - start) / 1000.0; }
        double left() { return (end - System.currentTimeMillis()) / 1000.0; }
        boolean over() { return System.currentTimeMillis() > end; }
    }

    private static Timed lowerThird, titleCard, contestantCard, credits;
    private static final List<Timed> points = new ArrayList<>();
    private static long scoresUntil;
    /** Animated values (0..1) eased toward their target each frame. */
    private static float letterboxK, scoresK, bannerK;
    private static String bannerManche = "";
    private static long lastFrame;

    static void reset() {
        lowerThird = titleCard = contestantCard = credits = null;
        points.clear();
        scoresUntil = 0;
        letterboxK = scoresK = bannerK = 0;
    }

    private static Timed timed(ShowCue c, double defaultSeconds) {
        long now = System.currentTimeMillis();
        double s = c.ticks() > 0 ? c.ticks() / 20.0 : defaultSeconds;
        return new Timed(c.title(), c.subtitle(), c.player(), now, now + (long) (s * 1000));
    }

    static void cue(ShowCue c) {
        switch (c.kind()) {
            case Cues.POINTS -> {
                points.add(timed(c, 3.5));
                while (points.size() > 3) points.removeFirst();
            }
            case Cues.LOWER_THIRD -> lowerThird = timed(c, 7);
            case Cues.SCORES -> scoresUntil = System.currentTimeMillis() + (c.ticks() > 0 ? c.ticks() * 50L : 12000);
            case Cues.TASK -> CurrentTask.showCard(c.ticks() > 0 ? c.ticks() : 240);
            case Cues.TITLE_CARD -> titleCard = timed(c, 5);
            case Cues.CONTESTANT -> {
                contestantCard = timed(c, 4);
                lowerThird = null;
            }
            case Cues.CREDITS -> credits = timed(c, 30);
            case Cues.CLEAR -> {
                lowerThird = titleCard = contestantCard = credits = null;
                points.clear();
                scoresUntil = 0;
            }
            default -> {}
        }
    }

    /** A follow shot on a contestant puts their name on screen (not during the credits, which have their cards). */
    static void onShotChanged(Shot s) {
        if (s.kind() != BroadcastNetwork.SHOT_FOLLOW || !ShowStateClient.get().sequence().isEmpty()) return;
        if (lowerThird != null && !lowerThird.over() && lowerThird.player().equalsIgnoreCase(s.player())) return;
        long now = System.currentTimeMillis();
        lowerThird = new Timed(s.player(), "", s.player(), now, now + 6500);
    }

    // ---------------------------------------------------------------- drawing

    /** HUD element: the whole overlay, only in broadcast mode. */
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!Broadcast.active()) {
            drawPreviewCaption(g, Minecraft.getInstance().font);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        ShowState st = ShowStateClient.get();
        float k = g.guiHeight() / CANVAS_H;
        float w = g.guiWidth() / k, h = CANVAS_H;

        // Split screen: the tiles' pictures over the whole frame (labels below, with the other graphics)
        List<float[]> tiles = DirectorCamera.shown().kind() == BroadcastNetwork.SHOT_MOSAIC ? mosaicLayout(DirectorCamera.shown().tiles().size(), w, h) : List.of();
        if (!tiles.isEmpty()) drawMosaicTiles(g, tiles, k);

        // Old frame dissolving, dip to black: under the graphics, which stay steady through the cut
        Transition.draw(g);
        float black = DirectorCamera.blackAlpha();
        if (black > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (black * 255) << 24));

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        if (!tiles.isEmpty()) {
            pose.scale(k, k);
            drawMosaicLabels(g, font, tiles);
            pose.popMatrix();
            pose.pushMatrix();
        }
        pose.scale(k, k);

        letterboxK = approach(letterboxK, st.letterbox() ? 1 : 0, dt, 0.35);
        float bar = BAR * (float) DirectorCamera.smooth(letterboxK);
        float top = bar, bottom = h - bar;
        if (bar > 0.01f) {
            fill(g, 0, 0, w, bar, 0xFF000000);
            fill(g, 0, h - bar, w, h, 0xFF000000);
        }

        boolean sequence = !st.sequence().isEmpty();
        if (!sequence) {
            drawBug(g, font, w, top, now);
            drawBanner(g, font, st, w, top, dt);
        }
        drawLowerThird(g, font, st, bottom);
        drawScores(g, font, st, w, bottom, dt, now);
        drawPoints(g, font, w, bottom);
        drawContestantCard(g, font, st, bottom);
        drawTitleCard(g, font, w, h);
        drawCredits(g, font, st, w, h);
        pose.popMatrix();
    }

    // ---- split screen

    /**
     * Tile rectangles {x0, y0, x1, y1} in canvas units: two side by side, three or four in a 2 × 2 grid (the third
     * centred on the second row); each keeps the screen's proportions.
     */
    static List<float[]> mosaicLayout(int n, float w, float h) {
        List<float[]> out = new ArrayList<>();
        n = Math.min(n, Mosaic.MAX);
        if (n < 2) return out;
        float margin = 12, gap = 6;
        if (n == 2) {
            float tw = (w - 2 * margin - gap) / 2, th = tw * h / w, y0 = (h - th) / 2;
            out.add(new float[]{margin, y0, margin + tw, y0 + th});
            out.add(new float[]{margin + tw + gap, y0, margin + 2 * tw + gap, y0 + th});
            return out;
        }
        float th = (h - 2 * margin - gap) / 2, tw = th * w / h, x0 = (w - 2 * tw - gap) / 2;
        for (int i = 0; i < n; i++) {
            int row = i / 2, col = i % 2;
            float x = n == 3 && i == 2 ? (w - tw) / 2 : x0 + col * (tw + gap);
            float y = margin + row * (th + gap);
            out.add(new float[]{x, y, x + tw, y + th});
        }
        return out;
    }

    /** Velvet background, then every tile's last picture (placeholder while it has none yet). */
    private static void drawMosaicTiles(GuiGraphicsExtractor g, List<float[]> tiles, float k) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(k, k);
        float w = g.guiWidth() / k;
        gradient(g, 0, 0, w, CANVAS_H, VELVET_DARK, 0xFF1A0306);
        for (float[] r : tiles) fill(g, r[0] - 1.5f, r[1] - 1.5f, r[2] + 1.5f, r[3] + 1.5f, GOLD_DARK);
        pose.popMatrix();
        for (int i = 0; i < tiles.size(); i++) {
            float[] r = tiles.get(i);
            if (Mosaic.ready(i)) Mosaic.draw(g, i, r[0] * k, r[1] * k, r[2] * k, r[3] * k);
            else g.fill(Math.round(r[0] * k), Math.round(r[1] * k), Math.round(r[2] * k), Math.round(r[3] * k), 0xFF120306);
        }
    }

    /** Each tile's name (the zone's players) in a small velvet strip, bottom left, and a thin gold frame. */
    private static void drawMosaicLabels(GuiGraphicsExtractor g, Font font, List<float[]> tiles) {
        List<BroadcastNetwork.Tile> shot = DirectorCamera.shown().tiles();
        for (int i = 0; i < tiles.size() && i < shot.size(); i++) {
            float[] r = tiles.get(i);
            goldFrame(g, r[0], r[1], r[2], r[3], 1);
            String label = shot.get(i).label();
            if (label.isEmpty()) continue;
            Component c = bold(label.toUpperCase(Locale.ROOT));
            float s = 0.75f, tw = width(font, c, s);
            float x0 = r[0] + 4, y1 = r[3] - 4, y0 = y1 - 11;
            velvet(g, x0, y0, x0 + tw + 10, y1, 0.92f, true);
            text(g, font, c, x0 + 5, y0 + 2.2f, s, CREAM, true);
        }
    }

    /** Organiser's own screen during a travelling preview: a small caption at the top. */
    private static void drawPreviewCaption(GuiGraphicsExtractor g, Font font) {
        String caption = DirectorCamera.previewCaption();
        if (caption == null) return;
        Component c = Component.literal(caption);
        int tw = font.width(c);
        int x = (g.guiWidth() - tw) / 2, y = 8;
        g.fill(x - 6, y - 4, x + tw + 6, y + 12, 0xC0200408);
        g.fill(x - 6, y + 12, x + tw + 6, y + 13, GOLD);
        g.text(font, c, x, y, CREAM, true);
    }

    private static float approach(float v, float target, double dt, double tau) {
        return (float) (v + (target - v) * Math.min(1, dt <= 0 ? 1 : (1 - Math.exp(-dt / tau))));
    }

    /** In/out envelope of a timed element: 0 → 1 over {@code in} s, 1 → 0 over the last {@code out} s, eased. */
    private static float envelope(Timed t, double in, double out) {
        if (t == null || t.over()) return 0;
        return (float) Math.min(DirectorCamera.smooth(t.age() / in), DirectorCamera.smooth(t.left() / out));
    }

    // ---- primitives (canvas units, floats rounded to the screen pixel by the pose)

    static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        // Fill takes ints: draw on a 4x finer grid so the canvas scale keeps half units
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(0.25f, 0.25f);
        g.fill(Math.round(x0 * 4), Math.round(y0 * 4), Math.round(x1 * 4), Math.round(y1 * 4), color);
        pose.popMatrix();
    }

    static void gradient(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int top, int bottom) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(0.25f, 0.25f);
        g.fillGradient(Math.round(x0 * 4), Math.round(y0 * 4), Math.round(x1 * 4), Math.round(y1 * 4), top, bottom);
        pose.popMatrix();
    }

    static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        return ((int) (base * Math.max(0, Math.min(1, a))) << 24) | (color & 0xFFFFFF);
    }

    /** Red velvet plate with soft vertical folds and a gold frame. */
    static void velvet(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float a, boolean frame) {
        gradient(g, x0, y0, x1, y1, alpha(VELVET_LIGHT, a * 0.97f), alpha(VELVET_DARK, a * 0.97f));
        // Folds: darker and lighter bands, irregular widths
        float x = x0;
        int i = 0;
        while (x < x1) {
            float band = 5 + (i * 7 % 4);
            fill(g, x, y0, Math.min(x1, x + band * 0.45f), y1, alpha(i % 2 == 0 ? 0x22000000 : 0x14FFD0D0, a));
            x += band;
            i++;
        }
        // Light from the top
        fill(g, x0, y0, x1, y0 + 0.75f, alpha(0x40FFE0C0, a));
        if (frame) goldFrame(g, x0, y0, x1, y1, a);
    }

    /** Two-tone gold line around a box. */
    static void goldFrame(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float a) {
        int outer = alpha(GOLD_DARK, a), inner = alpha(GOLD, a);
        fill(g, x0 - 1, y0 - 1, x1 + 1, y0, outer);
        fill(g, x0 - 1, y1, x1 + 1, y1 + 1, outer);
        fill(g, x0 - 1, y0, x0, y1, outer);
        fill(g, x1, y0, x1 + 1, y1, outer);
        fill(g, x0, y0, x1, y0 + 0.5f, alpha(GOLD_LIGHT, a));
        fill(g, x0, y1 - 0.5f, x1, y1, inner);
        fill(g, x0, y0, x0 + 0.5f, y1, inner);
        fill(g, x1 - 0.5f, y0, x1, y1, inner);
    }

    /** Text at a scale, top-left at (x, y); returns its width in canvas units. */
    static float text(GuiGraphicsExtractor g, Font font, Component c, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, c, 0, 0, color, shadow);
        pose.popMatrix();
        return font.width(c) * scale;
    }

    static float width(Font font, Component c, float scale) {
        return font.width(c) * scale;
    }

    static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    /** A player's face (hat layer included), {@code size} units square. */
    static void face(GuiGraphicsExtractor g, String player, float x, float y, float size) {
        Minecraft mc = Minecraft.getInstance();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 32f, size / 32f);
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player);
        if (info != null) PlayerFaceExtractor.extractRenderState(g, info.getSkin(), 0, 0, 32);
        else PlayerFaceExtractor.extractRenderState(g, profile(player), 0, 0, 32);
        pose.popMatrix();
    }

    private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();

    private static ResolvableProfile profile(String player) {
        return PROFILES.computeIfAbsent(player.toLowerCase(Locale.ROOT), p -> ResolvableProfile.createUnresolved(player));
    }

    static void seal(GuiGraphicsExtractor g, float x, float y, float size, float a) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 32f, size / 32f);
        g.blit(RenderPipelines.GUI_TEXTURED, SEAL, 0, 0, 0, 0, 32, 32, 32, 32, alpha(0xFFFFFFFF, a));
        pose.popMatrix();
    }

    static String clock(double seconds) {
        int s = (int) Math.ceil(seconds);
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ---- top: seal + live bug

    private static void drawBug(GuiGraphicsExtractor g, Font font, float w, float top, long now) {
        // Kept discreet (a small watermark seal, a small live tag): the picture is the show, not the branding
        float y = top + 7;
        seal(g, 9, y - 2, 13, 0.7f);
        // "● EN DIRECT", top right, small; the dot breathes
        Component live = bold("EN DIRECT");
        float tw = width(font, live, 0.5f);
        float x1 = w - 9, x0 = x1 - tw - 11;
        fill(g, x0, y - 1, x1, y + 6.5f, 0x90200408);
        float pulse = (float) (0.55 + 0.45 * Math.sin(now / 1000.0 * Math.PI * 1.2));
        fill(g, x0 + 2.5f, y + 1.5f, x0 + 5.5f, y + 4.5f, alpha(LIVE_RED, 0.4f + 0.6f * pulse));
        text(g, font, live, x0 + 8, y + 1, 0.5f, alpha(CREAM, 0.9f), false);
    }

    // ---- top centre: round banner and timer

    private static void drawBanner(GuiGraphicsExtractor g, Font font, ShowState st, float w, float top, double dt) {
        boolean has = !st.mancheId().isEmpty();
        if (has && !st.mancheId().equals(bannerManche)) {
            // New round: the banner leaves and comes back with the new name
            if (bannerK > 0.5f && !bannerManche.isEmpty()) bannerK = 0;
            bannerManche = st.mancheId();
        }
        bannerK = approach(bannerK, has ? 1 : 0, dt, 0.18);
        if (bannerK < 0.01f || bannerManche.isEmpty()) return;
        float e = (float) DirectorCamera.smooth(bannerK);
        Component label = bold(("Manche " + st.mancheNumber()).toUpperCase(Locale.ROOT));
        String nameText = st.mancheName();
        CurrentTask.Info task = CurrentTask.get().orElse(null);
        if (task != null && task.revealed() && st.mancheId().equals(task.mancheId()) && !task.title().isBlank())
            nameText = task.title();
        Component name = bold(nameText);
        double secs = ShowStateClient.timerSeconds();
        boolean timer = secs >= 0;
        Component clock = bold(timer ? clock(secs) : "");
        float lw = width(font, label, 0.62f) + 12;
        float nw = Math.max(70, width(font, name, 0.9f) + 16);
        float cw = timer ? width(font, Component.literal("00:00").withStyle(ChatFormatting.BOLD), 0.95f) + 14 : 0;
        float total = lw + nw + cw, hgt = 17;
        float x0 = (w - total) / 2, y0 = top + 8 - (1 - e) * 34;
        // Label: gold plate, ink text
        gradient(g, x0, y0, x0 + lw, y0 + hgt, alpha(GOLD_LIGHT, e), alpha(GOLD, e));
        text(g, font, label, x0 + 6, y0 + 6, 0.62f, alpha(INK, e), false);
        // Name: velvet
        velvet(g, x0 + lw, y0, x0 + lw + nw, y0 + hgt, e, false);
        text(g, font, name, x0 + lw + (nw - width(font, name, 0.9f)) / 2, y0 + 4.6f, 0.9f, alpha(CREAM, e), true);
        if (timer) {
            boolean last = secs <= 10.5;
            float pulse = last ? (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2)) : 0;
            float cx = x0 + lw + nw;
            fill(g, cx, y0, cx + cw, y0 + hgt, alpha(last ? mix(0xFF2A0508, 0xFF6A0A14, pulse) : 0xFF1C0E08, e * 0.95f));
            int color = last ? 0xFFFF5A5A : GOLD_LIGHT;
            text(g, font, clock, cx + (cw - width(font, clock, 0.95f)) / 2, y0 + 4.4f, 0.95f, alpha(color, e), true);
            // Time left as a gold line under the banner
            float p = (float) ShowStateClient.timerProgress();
            fill(g, x0, y0 + hgt, x0 + total, y0 + hgt + 1.5f, alpha(0x80000000, e));
            fill(g, x0, y0 + hgt, x0 + total * p, y0 + hgt + 1.5f, alpha(last ? LIVE_RED : GOLD, e));
        }
        goldFrame(g, x0, y0, x0 + total, y0 + hgt, e);
    }

    static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << sh;
        }
        return r;
    }

    // ---- bottom left: lower third

    /** "2e · 14 pts" for a ranked contestant, "Candidat" otherwise; "Maître du jeu" for the host. */
    private static String subtitleFor(ShowState st, String player) {
        if (player.equalsIgnoreCase(st.master())) return "Maître du jeu";
        for (Contestant c : st.contestants()) {
            if (!c.name().equalsIgnoreCase(player)) continue;
            if (c.rank() > 0 && !st.seasonHidden()) return (c.rank() == 1 ? "1er" : c.rank() + "e") + " · " + c.points() + " pts";
            return "Candidat";
        }
        return "";
    }

    private static void drawLowerThird(GuiGraphicsExtractor g, Font font, ShowState st, float bottom) {
        Timed t = lowerThird;
        if (t == null) return;
        if (t.over()) {
            lowerThird = null;
            return;
        }
        float in = (float) DirectorCamera.smooth(t.age() / 0.45), out = (float) DirectorCamera.smooth(t.left() / 0.35);
        float e = Math.min(in, out);
        boolean hasPlayer = !t.player().isEmpty();
        String sub = !t.subtitle().isEmpty() ? t.subtitle() : hasPlayer ? subtitleFor(st, t.player()) : "";
        Component name = bold(t.title());
        Component subC = Component.literal(sub);
        float faceS = hasPlayer ? 26 : 0;
        float nameW = width(font, name, 1.35f), subW = width(font, subC, 0.75f);
        float plateW = Math.max(nameW, subW) + 18 + (hasPlayer ? faceS + 8 : 0);
        float x0 = 22, y1 = bottom - 34, y0 = y1 - 32;
        // Wipe from the left: the plate grows, then the text appears
        float shown = plateW * e;
        fill(g, x0 - 4, y0, x0 - 1, y1, alpha(GOLD, in));
        if (shown > 1) {
            velvet(g, x0, y0, x0 + shown, y1 - 9, 1, false);
            gradient(g, x0, y1 - 9, x0 + shown, y1, GOLD_LIGHT, GOLD);
            fill(g, x0, y0, x0 + shown, y0 + 0.75f, GOLD_LIGHT);
        }
        float textA = (float) DirectorCamera.smooth((e - 0.6) / 0.4);
        if (textA <= 0.02f) return;
        float tx = x0 + 8;
        if (hasPlayer) {
            fill(g, tx - 1, y0 + 2, tx + faceS + 1, y0 + 4 + faceS, alpha(GOLD_DARK, textA));
            face(g, t.player(), tx, y0 + 3, faceS);
            tx += faceS + 8;
        }
        text(g, font, name, tx, y0 + 5, 1.35f, alpha(CREAM, textA), true);
        if (!sub.isEmpty()) text(g, font, subC, tx, y1 - 7.2f, 0.75f, alpha(INK, textA), false);
    }

    // ---- bottom: published scores

    private static void drawScores(GuiGraphicsExtractor g, Font font, ShowState st, float w, float bottom, double dt, long now) {
        List<Contestant> ranked = new ArrayList<>();
        for (Contestant c : st.contestants()) if (c.rank() > 0) ranked.add(c);
        ranked.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
        boolean want = !st.seasonHidden() && !ranked.isEmpty() && st.sequence().isEmpty() && switch (st.scores()) {
            case BroadcastNetwork.SCORES_ON -> true;
            case BroadcastNetwork.SCORES_OFF -> false;
            default -> now < scoresUntil;
        };
        scoresK = approach(scoresK, want ? 1 : 0, dt, 0.2);
        if (scoresK < 0.01f) return;
        float e = (float) DirectorCamera.smooth(scoresK);
        float gap = 4, hgt = 18;
        int n = Math.min(ranked.size(), 6);
        // Wide enough for the longest name and score, all chips the same width, the strip within the screen
        float longest = 0;
        for (int i = 0; i < n; i++)
            longest = Math.max(longest, width(font, Component.literal(ranked.get(i).name()), 0.7f)
                    + width(font, bold(String.valueOf(ranked.get(i).points())), 0.85f));
        float chipW = Math.min(Math.max(78, longest + 40), (w - 40 - (n - 1) * gap) / Math.max(1, n));
        float total = n * chipW + (n - 1) * gap;
        float x = (w - total) / 2, y = bottom - 26 + (1 - e) * 40;
        for (int i = 0; i < n; i++) {
            Contestant c = ranked.get(i);
            float cx = x + i * (chipW + gap);
            velvet(g, cx, y, cx + chipW, y + hgt, 1, true);
            int badge = switch (c.rank()) {
                case 1 -> 0xFFFFC23D;
                case 2 -> 0xFFD5DCE4;
                case 3 -> 0xFFD98A4E;
                default -> CREAM;
            };
            fill(g, cx, y, cx + 12, y + hgt, badge);
            Component rank = bold(String.valueOf(c.rank()));
            text(g, font, rank, cx + 6 - width(font, rank, 0.8f) / 2, y + 6, 0.8f, INK, false);
            face(g, c.name(), cx + 15, y + 3, 12);
            Component pts = bold(String.valueOf(c.points()));
            float pw = width(font, pts, 0.85f);
            text(g, font, pts, cx + chipW - 4 - pw, y + 5.5f, 0.85f, GOLD_LIGHT, true);
            String nm = c.name();
            float maxName = chipW - 32 - pw - 4;
            // A crowded strip first writes the name smaller, and only cuts it when it still does not fit
            float ns = 0.7f;
            while (ns > 0.5f && width(font, Component.literal(nm), ns) > maxName) ns -= 0.05f;
            while (nm.length() > 3 && width(font, Component.literal(nm), ns) > maxName) nm = nm.substring(0, nm.length() - 1);
            if (!nm.equals(c.name())) nm = nm.substring(0, Math.max(1, nm.length() - 1)) + "…";
            text(g, font, Component.literal(nm), cx + 30, y + 6.2f + (0.7f - ns) * font.lineHeight / 2, ns, CREAM, true);
        }
    }

    // ---- live points ("Ensuri +3")

    private static void drawPoints(GuiGraphicsExtractor g, Font font, float w, float bottom) {
        for (Iterator<Timed> it = points.iterator(); it.hasNext(); ) if (it.next().over()) it.remove();
        float y = bottom - 52;
        for (int i = points.size() - 1; i >= 0; i--) {
            Timed p = points.get(i);
            float e = envelope(p, 0.25, 0.4);
            // A little pop when it arrives
            double age = p.age();
            float pop = age < 0.35 ? (float) (1 + 0.18 * Math.sin(age / 0.35 * Math.PI)) : 1;
            boolean plus = p.title().startsWith("+");
            Component delta = bold(p.title());
            Component who = bold(p.player());
            float s = 1.0f;
            float dw = width(font, delta, 1.3f * s), ww = width(font, who, 0.85f * s);
            float bw = ww + dw + 20, bh = 18;
            float x1 = w - 18 + (1 - e) * 60, x0 = x1 - bw;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x1 - bw / 2, y + bh / 2);
            pose.scale(pop, pop);
            pose.translate(-(x1 - bw / 2), -(y + bh / 2));
            velvet(g, x0, y, x1, y + bh, e, true);
            text(g, font, who, x0 + 7, y + 6, 0.85f, alpha(CREAM, e), true);
            text(g, font, delta, x1 - 7 - dw, y + 3.6f, 1.3f, alpha(plus ? 0xFF7CFF8A : 0xFFFF6464, e), true);
            pose.popMatrix();
            y -= bh + 5;
        }
    }

    // ---- opening credits

    private static void drawContestantCard(GuiGraphicsExtractor g, Font font, ShowState st, float bottom) {
        Timed t = contestantCard;
        if (t == null) return;
        if (t.over()) {
            contestantCard = null;
            return;
        }
        float e = envelope(t, 0.5, 0.45);
        float slide = (1 - e) * -180;
        float x0 = 26 + slide, y1 = bottom - 26, y0 = y1 - 62;
        Component name = bold(t.player());
        float nameW = width(font, name, 2.4f);
        float plateW = Math.max(150, nameW + 80);
        velvet(g, x0, y0, x0 + plateW, y1, e, true);
        // Big face in a gold frame
        fill(g, x0 + 7, y0 + 7, x0 + 57, y0 + 57, alpha(GOLD, e));
        fill(g, x0 + 8, y0 + 8, x0 + 56, y0 + 56, alpha(GOLD_DARK, e));
        if (e > 0.3f) face(g, t.player(), x0 + 9, y0 + 9, 46);
        text(g, font, Component.literal(("Candidat " + t.title()).toUpperCase(Locale.ROOT)), x0 + 66, y0 + 12, 0.7f, alpha(GOLD_LIGHT, e), true);
        text(g, font, name, x0 + 66, y0 + 24, 2.4f, alpha(CREAM, e), true);
        String pts = subtitleFor(st, t.player());
        if (!pts.isEmpty() && !pts.equals("Candidat"))
            text(g, font, Component.literal(pts), x0 + 66, y0 + 48, 0.75f, alpha(GOLD, e), true);
    }

    private static void drawTitleCard(GuiGraphicsExtractor g, Font font, float w, float h) {
        Timed t = titleCard;
        if (t == null) return;
        if (t.over()) {
            titleCard = null;
            return;
        }
        float e = envelope(t, 0.8, 0.6);
        float grow = 0.92f + 0.08f * (float) DirectorCamera.smooth(t.age() / 1.2) + 0.02f * (float) (t.age() / 8);
        // Darker middle band behind the title
        gradient(g, 0, h / 2 - 60, w, h / 2, alpha(0x00000000, e), alpha(0x90000000, e));
        gradient(g, 0, h / 2, w, h / 2 + 60, alpha(0x90000000, e), alpha(0x00000000, e));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2, h / 2);
        pose.scale(grow, grow);
        boolean brand = t.title().equalsIgnoreCase("CraftMaster");
        Component title = bold(brand ? "CRAFTMASTER" : t.title());
        float scale = brand ? 4.2f : 2.6f;
        float tw = width(font, title, scale);
        if (tw > w - 40) {
            scale *= (w - 40) / tw;
            tw = width(font, title, scale);
        }
        if (brand) seal(g, -24, -66, 48, e);
        float ty = brand ? -12 : -14;
        // Gold title with a darker gold shadow under it
        text(g, font, title, -tw / 2 + scale * 0.6f, ty + scale * 0.6f, scale, alpha(GOLD_DARK, e), false);
        text(g, font, title, -tw / 2, ty, scale, alpha(GOLD_LIGHT, e), false);
        float lineW = Math.min(w * 0.6f, tw + 40) * (float) DirectorCamera.smooth(t.age() / 1.0);
        fill(g, -lineW / 2, ty + scale * 9 + 4, lineW / 2, ty + scale * 9 + 5, alpha(GOLD, e));
        if (!t.subtitle().isEmpty()) {
            Component sub = Component.literal(t.subtitle().toUpperCase(Locale.ROOT));
            float sw = width(font, sub, 1.1f);
            float sa = e * (float) DirectorCamera.smooth((t.age() - 0.6) / 0.6);
            text(g, font, sub, -sw / 2, ty + scale * 9 + 10, 1.1f, alpha(CREAM, sa), true);
        }
        pose.popMatrix();
    }

    // ---- closing credits

    private record Line(Component text, float scale, int color, float space) {}

    private static List<Line> creditLines(ShowState st) {
        List<Line> l = new ArrayList<>();
        l.add(new Line(bold("CRAFTMASTER"), 3.2f, GOLD_LIGHT, 34));
        if (!st.seasonName().isEmpty()) l.add(new Line(Component.literal(st.seasonName().toUpperCase(Locale.ROOT)), 1.2f, CREAM, 14));
        if (!st.partieName().isEmpty()) l.add(new Line(Component.literal(st.partieName()), 1f, GOLD, 30));
        l.add(new Line(Component.literal("PRÉSENTÉ PAR"), 0.8f, GOLD, 12));
        l.add(new Line(bold(st.master()), 1.5f, CREAM, 30));
        if (!st.contestants().isEmpty()) {
            l.add(new Line(Component.literal("AVEC"), 0.8f, GOLD, 12));
            for (Contestant c : st.contestants()) l.add(new Line(bold(c.name()), 1.3f, CREAM, 15));
            l.add(new Line(Component.empty(), 1, 0, 16));
        }
        List<Contestant> ranked = new ArrayList<>();
        for (Contestant c : st.contestants()) if (c.rank() > 0) ranked.add(c);
        ranked.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
        if (!st.seasonHidden() && !ranked.isEmpty()) {
            l.add(new Line(Component.literal("CLASSEMENT"), 0.8f, GOLD, 13));
            for (Contestant c : ranked) {
                int color = switch (c.rank()) {
                    case 1 -> 0xFFFFC23D;
                    case 2 -> 0xFFD5DCE4;
                    case 3 -> 0xFFD98A4E;
                    default -> CREAM;
                };
                String rank = c.rank() == 1 ? "1er" : c.rank() + "e";
                l.add(new Line(Component.literal(rank + "  ").withStyle(ChatFormatting.BOLD)
                        .append(Component.literal(c.name() + "  —  " + c.points() + " pts").withStyle(s -> s.withBold(false))),
                        1.15f, color, 15));
            }
            l.add(new Line(Component.empty(), 1, 0, 16));
        }
        l.add(new Line(Component.literal("RÉALISATION"), 0.8f, GOLD, 12));
        l.add(new Line(Component.literal("La régie CraftMaster"), 1.1f, CREAM, 30));
        l.add(new Line(bold("Merci d'avoir regardé !"), 1.6f, GOLD_LIGHT, 20));
        return l;
    }

    private static void drawCredits(GuiGraphicsExtractor g, Font font, ShowState st, float w, float h) {
        Timed t = credits;
        if (t == null) return;
        if (t.over()) {
            credits = null;
            return;
        }
        float e = envelope(t, 1.2, 1.5);
        // The picture darkens on the sides of the roll
        gradient(g, 0, 0, w, h, alpha(0x70000000, e), alpha(0x90000000, e));
        List<Line> lines = creditLines(st);
        float total = 0;
        for (Line l : lines) total += l.space();
        double duration = (t.end() - t.start()) / 1000.0;
        // From just under the screen to the last line resting in the middle, then held
        double p = Math.min(1, t.age() / Math.max(1, duration - 4));
        float y = (float) (h + 10 - (h / 2 + 10 + total - 20) * p);
        for (Line l : lines) {
            float tw = width(font, l.text(), l.scale());
            if (y > -30 && y < h + 10) {
                // Fade near the top and bottom edges
                float edge = Math.min(1, Math.min((y + 10) / 40, (h - y) / 40));
                text(g, font, l.text(), (w - tw) / 2, y, l.scale(), alpha(l.color(), e * Math.max(0, edge)), true);
            }
            y += l.space();
        }
    }
}
