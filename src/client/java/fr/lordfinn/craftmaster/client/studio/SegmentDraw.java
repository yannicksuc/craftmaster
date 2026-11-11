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

    /** Cells (column, row) of each segment of a 3 × 5 digit, row 0 at the top. */
    private static final int[][][] CELLS = {
            {{0, 0}, {1, 0}, {2, 0}},            // A
            {{2, 0}, {2, 1}, {2, 2}},            // B
            {{2, 2}, {2, 3}, {2, 4}},            // C
            {{0, 4}, {1, 4}, {2, 4}},            // D
            {{0, 2}, {0, 3}, {0, 4}},            // E
            {{0, 0}, {0, 1}, {0, 2}},            // F
            {{0, 2}, {1, 2}, {2, 2}},            // G
    };

    /** Columns of {@code text} on the grid: digits 3 wide, 1 column between two digits, ':' and '.' in that column. */
    static int columns(String text) {
        int cols = 0;
        boolean prevDigit = false;
        for (char ch : text.toCharArray()) {
            if (ch == ':' || ch == '.') {
                cols += 1;
                prevDigit = false;
            } else {
                cols += (prevDigit ? 1 : 0) + 3;
                prevDigit = true;
            }
        }
        return cols;
    }

    /**
     * {@code text} as 3 × 5 cell digits whose cells are whole pixels ({@code u} px, the biggest that fits), centred
     * in the area {@code x0..x0+w, y0..y0+h} (pixels of the face, y up) — no mixels next to the frame's texels.
     *
     * @return false if it does not fit even at 1 px a cell (the caller draws it freely)
     */
    static boolean pixelText(VertexConsumer c, PoseStack.Pose p, String text, int x0, int y0, int w, int h, float z, int lit, int unlit) {
        int cols = columns(text);
        int u = Math.min(w / Math.max(1, cols), h / 5);
        if (u < 1) return false;
        int x = x0 + (w - cols * u) / 2, top = y0 + (h - 5 * u) / 2 + 5 * u;
        boolean prevDigit = false;
        for (char ch : text.toCharArray()) {
            if (ch == ':') {
                cell(c, p, x, top, u, 0, 1, z + 0.02f, lit);
                cell(c, p, x, top, u, 0, 3, z + 0.02f, lit);
                x += u;
                prevDigit = false;
                continue;
            }
            if (ch == '.') {
                cell(c, p, x, top, u, 0, 4, z + 0.02f, lit);
                x += u;
                prevDigit = false;
                continue;
            }
            if (prevDigit) x += u;
            int on = SevenSegment.segments(ch);
            // Unlit segments first, the lit ones over them (shared corner cells end up lit)
            for (int seg = 0; seg < 7; seg++)
                if ((on & (1 << seg)) == 0 && unlit >= 0) for (int[] k : CELLS[seg]) cell(c, p, x, top, u, k[0], k[1], z, unlit);
            for (int seg = 0; seg < 7; seg++)
                if ((on & (1 << seg)) != 0) for (int[] k : CELLS[seg]) cell(c, p, x, top, u, k[0], k[1], z + 0.02f, lit);
            x += 3 * u;
            prevDigit = true;
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
