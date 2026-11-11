package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lordfinn.craftmaster.studio.SevenSegment;
import net.minecraft.util.LightCoordsUtil;

/**
 * Seven-segment text drawn as flat rectangles facing +z, with the white pixels of the studio timer texture
 * ({@link TimerDisplayRenderer#TEXTURE}) tinted: digits, '-', ':' and '.'. Used by the timer tablet.
 */
final class SegmentDraw {

    private SegmentDraw() {}

    private static final Geo.Tex TEX = new Geo.Tex(64, 32);
    private static final float[] WHITE = {42, 2, 46, 6};

    /** Width of {@code text} for digits {@code dw} wide (before scaling). */
    static float width(String text, float dw) {
        float gap = dw * 0.2f, total = 0;
        for (char ch : text.toCharArray()) total += advance(ch, dw) + gap;
        return Math.max(0, total - gap);
    }

    private static float advance(char ch, float dw) {
        return ch == ':' ? dw * 0.4f : ch == '.' ? dw * 0.3f : dw;
    }

    /**
     * {@code text} centred on x = 0, fitted in {@code maxW} × {@code maxH}, bottom at {@code y}, at depth z.
     * {@code unlit} &lt; 0: unlit segments not drawn.
     */
    static void text(VertexConsumer c, PoseStack.Pose p, String text, float y, float maxW, float maxH, float z, int lit, int unlit) {
        float dh = maxH, dw = dh * 0.56f;
        float total = width(text, dw);
        float k = total > maxW ? maxW / total : 1;
        dw *= k;
        dh *= k;
        float gap = dw * 0.2f, x = -width(text, dw) / 2;
        for (char ch : text.toCharArray()) {
            float t = dw * 0.18f;
            if (ch == ':') {
                float cx = x + advance(ch, dw) / 2;
                rect(c, p, cx - t / 2, y + dh * 0.25f, cx + t / 2, y + dh * 0.25f + t, z + 0.02f, lit);
                rect(c, p, cx - t / 2, y + dh * 0.65f, cx + t / 2, y + dh * 0.65f + t, z + 0.02f, lit);
            } else if (ch == '.') {
                float cx = x + advance(ch, dw) / 2;
                rect(c, p, cx - t / 2, y, cx + t / 2, y + t, z + 0.02f, lit);
            } else {
                digit(c, p, x, y, dw, dh, SevenSegment.segments(ch), z, lit, unlit);
            }
            x += advance(ch, dw) + gap;
        }
    }

    private static void digit(VertexConsumer c, PoseStack.Pose p, float x, float y, float w, float h, int on, float z, int lit, int unlit) {
        float t = w * 0.18f, g = t * 0.2f, mid = y + h / 2;
        seg(c, p, on, SevenSegment.A, x + t, y + h - t, x + w - t, y + h, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.G, x + t, mid - t / 2, x + w - t, mid + t / 2, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.D, x + t, y, x + w - t, y + t, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.F, x, mid, x + t, y + h - t, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.B, x + w - t, mid, x + w, y + h - t, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.E, x, y + t, x + t, mid, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.C, x + w - t, y + t, x + w, mid, z, lit, unlit, g, false);
    }

    private static void seg(VertexConsumer c, PoseStack.Pose p, int on, int bit, float x0, float y0, float x1, float y1, float z,
                            int lit, int unlit, float g, boolean horizontal) {
        boolean isOn = (on & bit) != 0;
        if (!isOn && unlit < 0) return;
        if (horizontal) {
            x0 += g;
            x1 -= g;
        } else {
            y0 += g;
            y1 -= g;
        }
        rect(c, p, x0, y0, x1, y1, z + (isOn ? 0.02f : 0), isOn ? lit : unlit);
    }

    // ---------------------------------------------------------------- on the pixel grid

    /** Digit grids (columns × rows of cells), finest first; segments are 1 cell thick. */
    private static final int[][] GRIDS = {{7, 13}, {6, 11}, {5, 9}, {4, 7}, {3, 5}};

    /** A layout of {@code text}: grid, cell size, gap between digits, separators with gaps around them or not. */
    private record Layout(int gw, int gh, int u, int gap, boolean roomy, int cols) {}

    /** Columns of {@code text}: digits {@code gw} wide, {@code gap} between digits, ':' / '.' 1 wide. */
    private static int columns(String text, int gw, int gap, boolean roomy) {
        int cols = 0;
        char prev = 0;
        for (char ch : text.toCharArray()) {
            boolean sep = ch == ':' || ch == '.';
            if (prev != 0) {
                boolean prevSep = prev == ':' || prev == '.';
                cols += sep || prevSep ? (roomy ? gap : 0) : gap;
            }
            cols += sep ? 1 : gw;
            prev = ch;
        }
        return cols;
    }

    /**
     * The best layout of {@code text} in {@code w} × {@code h} px: the tallest digits up to about 60 % of the height
     * (a black margin around them, like a real display), thin segments (1 px cells preferred), spaced digits, the
     * separators in their own spaced column when there is room. Null if nothing fits.
     */
    private static Layout layout(String text, int w, int h) {
        int limit = Math.max(5, Math.round(h * 0.62f));
        Layout best = null;
        for (int[] g : GRIDS)
            for (int u = 1; u <= 2; u++)
                for (boolean roomy : new boolean[]{true, false}) {
                    int gap = g[0] >= 5 ? 2 : 1;
                    int cols = columns(text, g[0], gap, roomy);
                    if (cols * u > w || g[1] * u > Math.min(h, limit)) continue;
                    Layout l = new Layout(g[0], g[1], u, gap, roomy, cols);
                    // Taller wins; same height: thinner cells, then roomy separators
                    if (best == null || g[1] * u > best.gh() * best.u() || g[1] * u == best.gh() * best.u() && u < best.u()) best = l;
                    break;
                }
        return best;
    }

    /** Cells {col, row} lit by a segment (row 0 at the top) on a {@code gw} × {@code gh} grid. */
    private static int[][] segment(int seg, int gw, int gh) {
        int mid = (gh - 1) / 2;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        // 3 × 5: full corners (too small for rounded ones); bigger: rounded corners like a real display
        int a = gw == 3 ? 0 : 1, b = gw == 3 ? gw - 1 : gw - 2;
        switch (seg) {
            case 0 -> { for (int x = a; x <= b; x++) out.add(new int[]{x, 0}); }
            case 3 -> { for (int x = a; x <= b; x++) out.add(new int[]{x, gh - 1}); }
            case 6 -> { for (int x = a; x <= b; x++) out.add(new int[]{x, mid}); }
            case 1, 5 -> {
                int x = seg == 1 ? gw - 1 : 0;
                for (int y = gw == 3 ? 0 : 1; y <= (gw == 3 ? mid : mid - 1); y++) out.add(new int[]{x, y});
            }
            case 2, 4 -> {
                int x = seg == 2 ? gw - 1 : 0;
                for (int y = gw == 3 ? mid : mid + 1; y <= (gw == 3 ? gh - 1 : gh - 2); y++) out.add(new int[]{x, y});
            }
            default -> {}
        }
        return out.toArray(new int[0][]);
    }

    /**
     * {@code text} as seven-segment digits on the face's pixel grid (whole-pixel cells: no mixels next to the
     * frame's texels), centred in {@code x0..x0+w, y0..y0+h} (pixels, y up).
     *
     * @return false if it does not fit at all (the caller draws it freely)
     */
    static boolean pixelText(VertexConsumer c, PoseStack.Pose p, String text, int x0, int y0, int w, int h, float z, int lit, int unlit) {
        Layout l = layout(text, w, h);
        if (l == null) return false;
        int u = l.u();
        int x = x0 + (w - l.cols() * u) / 2, top = y0 + (h - l.gh() * u) / 2 + l.gh() * u;
        char prev = 0;
        for (char ch : text.toCharArray()) {
            boolean sep = ch == ':' || ch == '.';
            if (prev != 0) {
                boolean prevSep = prev == ':' || prev == '.';
                x += (sep || prevSep ? (l.roomy() ? l.gap() : 0) : l.gap()) * u;
            }
            prev = ch;
            if (ch == ':') {
                cell(c, p, x, top, u, 0, Math.round((l.gh() - 1) * 0.3f), z + 0.02f, lit);
                cell(c, p, x, top, u, 0, Math.round((l.gh() - 1) * 0.7f), z + 0.02f, lit);
                x += u;
                continue;
            }
            if (ch == '.') {
                cell(c, p, x, top, u, 0, l.gh() - 1, z + 0.02f, lit);
                x += u;
                continue;
            }
            int on = SevenSegment.segments(ch);
            // Unlit segments first, the lit ones over them
            for (int seg = 0; seg < 7; seg++)
                if ((on & (1 << seg)) == 0 && unlit >= 0) for (int[] k : segment(seg, l.gw(), l.gh())) cell(c, p, x, top, u, k[0], k[1], z, unlit);
            for (int seg = 0; seg < 7; seg++)
                if ((on & (1 << seg)) != 0) for (int[] k : segment(seg, l.gw(), l.gh())) cell(c, p, x, top, u, k[0], k[1], z + 0.02f, lit);
            x += l.gw() * u;
        }
        return true;
    }
    private static void cell(VertexConsumer c, PoseStack.Pose p, int x, int top, int u, int col, int row, float z, int color) {
        rect(c, p, x + col * u, top - (row + 1) * u, x + (col + 1) * u, top - row * u, z, color);
    }

    /** A flat rectangle facing +z (front face only), full bright. */
    static void rect(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, int color) {
        Geo.front(c, p, TEX, x0, y0, x1, y1, z, WHITE[0], WHITE[1], WHITE[2], WHITE[3], color, LightCoordsUtil.FULL_BRIGHT);
    }

    /** A box (all faces) in one colour, lit by {@code light}. */
    static void box(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        Geo.box(c, p, TEX, x0, y0, z0, x1, y1, z1, Geo.all(WHITE[0], WHITE[1], WHITE[2], WHITE[3]), color, light);
    }
}
