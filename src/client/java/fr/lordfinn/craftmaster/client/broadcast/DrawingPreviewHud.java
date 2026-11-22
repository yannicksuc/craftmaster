package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.client.menu.MenuClient;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.trial.kit.DrawingImages;
import fr.lordfinn.craftmaster.trial.kit.DrawingPreview;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The live picture of the path of the contestant being watched, in a corner, on the jury's paper. The server sends it
 * only to those who may see it ({@link DrawingPreview}); here it is only drawn: the same simplified line as the final
 * picture, framed on the drawing, the current position marked while they draw.
 */
public final class DrawingPreviewHud {

    private DrawingPreviewHud() {}

    public static final KeyMapping KEY = new KeyMapping("key.craftmaster.drawing_preview", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/,
            InputConstants.UNKNOWN.getValue(), CraftMasterClient.CATEGORY);
    private static final int INK = 0xFF1E1814, DOT = 0xFFC8281E, TITLE = 18, PAD = 5;

    private static boolean on = true, live, dirty, feed;
    private static String corner = "bd", player = "";
    private static int size = 132;
    private static final List<float[]> POINTS = new ArrayList<>();
    /** The line as rows of pixels: x, y, width. */
    private static int[] runs = new int[0];
    private static int dotX = -1, dotY = -1, built = -1;

    public static void init() {
        KeyMappingHelper.registerKeyMapping(KEY);
        HudElementRegistry.addLast(CraftMaster.id("drawing_preview"), DrawingPreviewHud::extract);
        ClientPlayNetworking.registerGlobalReceiver(DrawingPreview.Trace.TYPE, (t, ctx) -> receive(t));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY.consumeClick()) if (mc.player != null) MenuClient.command("apercu basculer");
            boolean now = mc.player != null && ShowFeedClient.active();
            if (now != feed && ClientPlayNetworking.canSend(DrawingPreview.Feed.TYPE)) {
                feed = now;
                ClientPlayNetworking.send(new DrawingPreview.Feed(now));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
            POINTS.clear();
            player = "";
            feed = false;
        }));
    }

    static void prefs(String state, String where, String pixels) {
        on = !"off".equals(state);
        if (where != null && !where.isEmpty()) corner = where;
        try {
            size = Integer.parseInt(pixels);
        } catch (NumberFormatException ignored) {
        }
        dirty = true;
    }

    private static void receive(DrawingPreview.Trace t) {
        if (t.reset()) POINTS.clear();
        player = t.player();
        live = t.live();
        for (int i = 0; i + 1 < t.xz().length; i += 2) POINTS.add(new float[]{t.xz()[i], t.xz()[i + 1]});
        dirty = true;
    }

    /** The line drawn again for this size. */
    private static void build(int w, int h) {
        dirty = false;
        built = w;
        dotX = dotY = -1;
        List<List<float[]>> strokes = new ArrayList<>();
        List<float[]> cur = new ArrayList<>();
        float[] last = null;
        for (float[] p : POINTS) {
            if (Float.isNaN(p[0])) {
                if (!cur.isEmpty()) strokes.add(DrawingImages.smooth(DrawingImages.simplify(cur, 0.2f)));
                cur = new ArrayList<>();
            } else {
                cur.add(p);
                last = p;
            }
        }
        if (!cur.isEmpty()) strokes.add(DrawingImages.smooth(DrawingImages.simplify(cur, 0.2f)));
        float x0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (List<float[]> st : strokes)
            for (float[] p : st) {
                x0 = Math.min(x0, p[0]);
                x1 = Math.max(x1, p[0]);
                z0 = Math.min(z0, p[1]);
                z1 = Math.max(z1, p[1]);
            }
        if (x0 > x1) {
            runs = new int[0];
            return;
        }
        float span = Math.max(Math.max(x1 - x0, z1 - z0), 4f), scale = (Math.min(w, h) - 3) / span;
        float ox = (w - (x1 - x0) * scale) / 2 - x0 * scale, oz = (h - (z1 - z0) * scale) / 2 - z0 * scale;
        boolean[] grid = new boolean[w * h];
        for (List<float[]> st : strokes)
            for (int i = 0; i < st.size(); i++) {
                float[] a = st.get(Math.max(0, i - 1)), b = st.get(i);
                float ax = a[0] * scale + ox, az = a[1] * scale + oz, bx = b[0] * scale + ox, bz = b[1] * scale + oz;
                int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(bz - az))));
                for (int k = 0; k <= steps; k++) {
                    int x = Math.round(ax + (bx - ax) * k / steps), y = Math.round(az + (bz - az) * k / steps);
                    if (x >= 0 && x < w && y >= 0 && y < h) grid[x + y * w] = true;
                }
            }
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (!grid[x + y * w]) continue;
                int e = x;
                while (e + 1 < w && grid[e + 1 + y * w]) e++;
                out.add(new int[]{x, y, e - x + 1});
                x = e;
            }
        runs = new int[out.size() * 3];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, runs, i * 3, 3);
        if (last != null) {
            dotX = Math.round(last[0] * scale + ox);
            dotY = Math.round(last[1] * scale + oz);
        }
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || player.isEmpty() || POINTS.isEmpty()) return;
        int w = size, h = size + TITLE, m = 6;
        int x = corner.endsWith("g") ? m : g.guiWidth() - w - m, y = corner.startsWith("h") ? m : g.guiHeight() - h - m;
        Ui.panel(g, mc.font, x, y, w, h, Component.literal(player));
        int px = x + PAD, py = y + TITLE + PAD - 1, pw = w - 2 * PAD, ph = h - TITLE - 2 * PAD + 1;
        Ui.paper(g, px, py, pw, ph);
        if (dirty || built != pw) build(pw, ph);
        for (int i = 0; i + 2 < runs.length; i += 3) g.fill(px + runs[i], py + runs[i + 1], px + runs[i] + runs[i + 2], py + runs[i + 1] + 1, INK);
        if (live && dotX >= 0) g.fill(px + dotX - 1, py + dotY - 1, px + dotX + 2, py + dotY + 2, DOT);
    }
}
