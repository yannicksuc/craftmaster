package fr.lordfinn.craftmaster.trial;

import java.util.Arrays;

/**
 * The pixels of a drawing board: {@code width × height} cells, each 0 (blank: the board's own surface) or a dye
 * colour 1..16 ({@code DyeColor#getId() + 1}). Synced to the players as a short run-length text
 * ({@link #encode()}: "4096a" is a blank 64×64 board), so a mostly blank drawing costs a few bytes.
 * Pure: drawing lines with a round brush, encoding and decoding are tested.
 */
public final class CanvasPixels {

    public static final int COLORS = 17;
    public static final int MAX_SIDE = 16 * 8;

    private final int width, height;
    private final byte[] cells;

    public CanvasPixels(int width, int height) {
        this.width = Math.max(1, Math.min(MAX_SIDE, width));
        this.height = Math.max(1, Math.min(MAX_SIDE, height));
        this.cells = new byte[this.width * this.height];
    }

    public int width() { return width; }
    public int height() { return height; }

    public int get(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : cells[y * width + x];
    }

    /** @return whether the cell changed */
    public boolean set(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= width || y >= height || color < 0 || color >= COLORS) return false;
        int i = y * width + x;
        if (cells[i] == color) return false;
        cells[i] = (byte) color;
        return true;
    }

    /** A dot of the brush: radius 0 = one pixel, 1 = a 3×3 "plus" disc, 2 = a 5×5 disc. */
    public boolean dot(int cx, int cy, int radius, int color) {
        boolean changed = false;
        int r2 = radius * radius + radius;
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++)
                if (dx * dx + dy * dy <= r2) changed |= set(cx + dx, cy + dy, color);
        return changed;
    }

    /** A straight stroke from one point to the other (Bresenham), both ends included. */
    public boolean line(int x0, int y0, int x1, int y1, int radius, int color) {
        boolean changed = false;
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 4 * MAX_SIDE; guard++) {
            changed |= dot(x0, y0, radius, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
        return changed;
    }

    /** Fills the area of one colour around a point with another (bucket), 4-connected. */
    public boolean fill(int x, int y, int color) {
        int from = get(x, y);
        if (from == color || x < 0 || y < 0 || x >= width || y >= height) return false;
        // Each cell is painted when pushed, so it is pushed at most once
        int[] stack = new int[cells.length];
        int n = 0;
        int start = y * width + x;
        cells[start] = (byte) color;
        stack[n++] = start;
        while (n > 0) {
            int i = stack[--n];
            int cx = i % width, cy = i / width;
            if (cx > 0 && cells[i - 1] == from) { cells[i - 1] = (byte) color; stack[n++] = i - 1; }
            if (cx < width - 1 && cells[i + 1] == from) { cells[i + 1] = (byte) color; stack[n++] = i + 1; }
            if (cy > 0 && cells[i - width] == from) { cells[i - width] = (byte) color; stack[n++] = i - width; }
            if (cy < height - 1 && cells[i + width] == from) { cells[i + width] = (byte) color; stack[n++] = i + width; }
        }
        return true;
    }

    public void clear() {
        Arrays.fill(cells, (byte) 0);
    }

    public boolean isBlank() {
        for (byte b : cells) if (b != 0) return false;
        return true;
    }

    /** Run-length text: for each run, its length (omitted when 1) then the colour as a letter 'a' (blank) to 'q'. */
    public String encode() {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < cells.length) {
            int c = cells[i], run = 1;
            while (i + run < cells.length && cells[i + run] == c) run++;
            if (run > 1) b.append(run);
            b.append((char) ('a' + c));
            i += run;
        }
        return b.toString();
    }

    /** Reads {@link #encode()}'s text into a board of that size (what does not fit is dropped, the rest is blank). */
    public static CanvasPixels decode(int width, int height, String text) {
        CanvasPixels p = new CanvasPixels(width, height);
        if (text == null) return p;
        int i = 0, run = 0;
        for (int k = 0; k < text.length() && i < p.cells.length; k++) {
            char ch = text.charAt(k);
            if (ch >= '0' && ch <= '9') {
                run = Math.min(run * 10 + (ch - '0'), p.cells.length);
                continue;
            }
            int c = ch - 'a';
            if (c < 0 || c >= COLORS) c = 0;
            int n = Math.max(1, run);
            for (int j = 0; j < n && i < p.cells.length; j++) p.cells[i++] = (byte) c;
            run = 0;
        }
        return p;
    }

    /** Copy of the drawing on a board of another size (top-left corner kept). */
    public CanvasPixels resized(int newWidth, int newHeight) {
        CanvasPixels p = new CanvasPixels(newWidth, newHeight);
        for (int y = 0; y < Math.min(height, p.height); y++)
            for (int x = 0; x < Math.min(width, p.width); x++) p.cells[y * p.width + x] = cells[y * width + x];
        return p;
    }
}
