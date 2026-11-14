package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.camera.CameraList;

import java.util.ArrayList;
import java.util.List;

/**
 * The layouts of a split screen ({@link BroadcastNetwork#SHOT_MOSAIC}), 2 to {@link #MAX} shots, the first tile being
 * the main shot:
 * <ul>
 * <li>{@link #FRAMES} "cadres": framed pictures on the velvet background, side by side (2), 2 × 2 (3–4), 3 × 2 (5–6);</li>
 * <li>{@link #EDGE} "bord à bord": the whole picture cut in parts, edge to edge (2 or 3 columns, then 2 rows), each
 * shot cropped around its centre;</li>
 * <li>{@link #MAIN} "principal": the main shot large at the top, the others smaller in a row below.</li>
 * </ul>
 * The layout travels in the {@code player} field of the mosaic shot (unused there; "" = {@link #FRAMES}, as in the
 * replays recorded before the layouts). Pure arithmetic, shared by the server (names) and the broadcast overlay.
 */
public final class SplitLayouts {

    public static final String FRAMES = "cadres", EDGE = "bord", MAIN = "principal";
    public static final List<String> ALL = List.of(FRAMES, EDGE, MAIN);
    /** At most 6 shots: each tile is drawn every n frames (10 fps at 60 with 6), and smaller tiles stop reading well. */
    public static final int MAX = 6;

    /**
     * A tile that follows a player instead of showing a placed camera: its key is {@code "follow:<player>"} (the
     * broadcast game frames them like a follow shot); every other key is a camera key.
     */
    public static final String FOLLOW = "follow:";

    private SplitLayouts() {}

    /** The tile key of a follow shot on {@code player}. */
    public static String followKey(String player) {
        return FOLLOW + player;
    }

    /** The player a tile key follows, or null for a camera key. */
    public static String followed(String key) {
        return key != null && key.startsWith(FOLLOW) && key.length() > FOLLOW.length() ? key.substring(FOLLOW.length()) : null;
    }

    /** The layout a word means (French and English, with or without accents), or null. */
    public static String parse(String s) {
        if (s == null) return null;
        String f = CameraList.fold(s).replace('-', ' ').replace('_', ' ').trim();
        return switch (f) {
            case "cadres", "cadre", "rectangles", "rectangle", "cote a cote", "frames", "framed", "grille", "grid", "mosaique" -> FRAMES;
            case "bord", "bord a bord", "plein ecran", "coupe", "coupe en deux", "moities", "moitie", "parts", "edge", "full", "split" -> EDGE;
            case "principal", "principale", "grand", "un grand", "incrustation", "main", "pip" -> MAIN;
            default -> null;
        };
    }

    /** The layout of a mosaic shot (its {@code player} field), {@link #FRAMES} when unknown. */
    public static String of(String field) {
        String l = parse(field);
        return l == null ? FRAMES : l;
    }

    /** French name, for the lines and the menus. */
    public static String label(String layout) {
        return switch (of(layout)) {
            case EDGE -> "bord à bord";
            case MAIN -> "un principal";
            default -> "cadres";
        };
    }

    /**
     * Tile rectangles in canvas units, one per shot (in order): {x0, y0, x1, y1, u0, v0, u1, v1}, the u/v part being
     * the part of the shot's picture shown (0–1, top-left origin): all of it, except edge to edge where a cell narrower
     * or wider than the screen shows the middle of the picture.
     */
    public static List<float[]> layout(String layout, int n, float w, float h) {
        n = Math.min(n, MAX);
        List<float[]> out = new ArrayList<>();
        if (n < 2) return out;
        switch (of(layout)) {
            case EDGE -> edge(out, n, w, h);
            case MAIN -> main(out, n, w, h);
            default -> frames(out, n, w, h);
        }
        return out;
    }

    /** Shots per row: 2 → 2, 3 → 2 + 1, 4 → 2 + 2, 5 → 3 + 2, 6 → 3 + 3 (edge to edge: 3 → one row of 3). */
    static int[] rows(int n, boolean edge) {
        return switch (n) {
            case 2 -> new int[]{2};
            case 3 -> edge ? new int[]{3} : new int[]{2, 1};
            case 4 -> new int[]{2, 2};
            case 5 -> new int[]{3, 2};
            default -> new int[]{3, 3};
        };
    }

    private static void frames(List<float[]> out, int n, float w, float h) {
        float margin = 12, gap = 6, a = w / h;
        int[] rows = rows(n, false);
        int cols = 0;
        for (int r : rows) cols = Math.max(cols, r);
        float th = Math.min((h - 2 * margin - (rows.length - 1) * gap) / rows.length, (w - 2 * margin - (cols - 1) * gap) / cols / a);
        float tw = th * a;
        float y = (h - rows.length * th - (rows.length - 1) * gap) / 2;
        for (int k : rows) {
            float x = (w - k * tw - (k - 1) * gap) / 2;
            for (int i = 0; i < k; i++, x += tw + gap) out.add(new float[]{x, y, x + tw, y + th, 0, 0, 1, 1});
            y += th + gap;
        }
    }

    private static void edge(List<float[]> out, int n, float w, float h) {
        float gap = 1.5f, a = w / h;
        int[] rows = rows(n, true);
        float rh = (h - (rows.length - 1) * gap) / rows.length, y = 0;
        for (int k : rows) {
            float cw = (w - (k - 1) * gap) / k, x = 0, ca = cw / rh;
            // The middle of the picture, at the cell's proportions
            float du = ca < a ? ca / a : 1, dv = ca < a ? 1 : a / ca;
            for (int i = 0; i < k; i++, x += cw + gap)
                out.add(new float[]{x, y, x + cw, y + rh, 0.5f - du / 2, 0.5f - dv / 2, 0.5f + du / 2, 0.5f + dv / 2});
            y += rh + gap;
        }
    }

    private static void main(List<float[]> out, int n, float w, float h) {
        float margin = 12, gap = 6, a = w / h, free = h - 2 * margin - gap;
        int small = n - 1;
        float sh = free * 0.28f, sw = sh * a;
        if (small * sw + (small - 1) * gap > w - 2 * margin) {
            sw = (w - 2 * margin - (small - 1) * gap) / small;
            sh = sw / a;
        }
        float mh = Math.min(free - sh, (w - 2 * margin) / a), mw = mh * a;
        float top = (h - mh - gap - sh) / 2;
        float mx = (w - mw) / 2;
        out.add(new float[]{mx, top, mx + mw, top + mh, 0, 0, 1, 1});
        float y = top + mh + gap, x = (w - small * sw - (small - 1) * gap) / 2;
        for (int i = 0; i < small; i++, x += sw + gap) out.add(new float[]{x, y, x + sw, y + sh, 0, 0, 1, 1});
    }
}
