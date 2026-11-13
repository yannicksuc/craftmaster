package fr.lordfinn.craftmaster.wardrobe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The colour tags of a piece, read from its pixels (its item icon) or its dye: a casque jaune is "Jaune" whatever
 * its name says. Pure (ARGB values in, French colour names of {@link OutfitTags} out), tested by OutfitColorsTest.
 */
public final class OutfitColors {

    private OutfitColors() {}

    /** The colour names, as the "Couleur" tags of {@link OutfitTags}. */
    public static final String RED = "Rouge", ORANGE = "Orange", YELLOW = "Jaune", GREEN = "Vert", CYAN = "Cyan", BLUE = "Bleu",
            PURPLE = "Violet", PINK = "Rose", BROWN = "Marron", BLACK = "Noir", WHITE = "Blanc", GRAY = "Gris";

    /** A colour counts when it covers at least this share of a piece (shading and small details do not). */
    static final double MIN_SHARE = 0.12;
    /** At most this many colours per piece, the biggest. */
    static final int MAX_PER_PIECE = 3;

    /** The colour name of one pixel (RGB). */
    public static String classify(int rgb) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max, s = max == 0 ? 0 : (max - min) / max;
        float h;
        if (max == min) h = 0;
        else if (max == r) h = 60 * (((g - b) / (max - min)) % 6);
        else if (max == g) h = 60 * ((b - r) / (max - min) + 2);
        else h = 60 * ((r - g) / (max - min) + 4);
        if (h < 0) h += 360;

        if (v < 0.17f) return BLACK;
        if (s < 0.16f) return v > 0.82f ? WHITE : v < 0.3f ? BLACK : GRAY;
        // Warm hues: dark or dull = brown
        if (h >= 12 && h < 48 && (v < 0.62f || s < 0.42f)) return BROWN;
        if (h < 12 || h >= 345) return v > 0.75f && s < 0.45f ? PINK : RED;
        if (h < 40) return ORANGE;
        if (h < 68) return v < 0.45f ? BROWN : YELLOW;
        if (h < 165) return GREEN;
        if (h < 195) return CYAN;
        if (h < 258) return BLUE;
        if (h < 295) return PURPLE;
        return PINK;
    }

    /**
     * The colours of a piece's image ({@code argb} row by row, {@code w} wide): its inner pixels (not its outline,
     * the pixels touching transparency), the colours covering at least {@link #MIN_SHARE} of them, biggest first.
     */
    public static List<String> ofImage(int[] argb, int w, int h) {
        Map<String, Integer> count = new LinkedHashMap<>();
        int total = 0;
        for (int pass = 0; pass < 2 && total == 0; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = argb[y * w + x];
                    if ((p >>> 24) < 128) continue;
                    // First pass: inner pixels only (the dark outline of an icon is not its colour)
                    if (pass == 0 && edge(argb, w, h, x, y)) continue;
                    count.merge(classify(p & 0xFFFFFF), 1, Integer::sum);
                    total++;
                }
            }
        }
        List<String> out = new ArrayList<>();
        if (total == 0) return out;
        int t = total;
        count.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_SHARE * t)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(MAX_PER_PIECE)
                .forEach(e -> out.add(e.getKey()));
        return out;
    }

    private static boolean edge(int[] argb, int w, int h, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (nx < 0 || ny < 0 || nx >= w || ny >= h || (argb[ny * w + nx] >>> 24) < 128) return true;
        }
        return false;
    }
}
