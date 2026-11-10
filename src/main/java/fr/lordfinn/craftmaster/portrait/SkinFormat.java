package fr.lordfinn.craftmaster.portrait;

/**
 * Sizes of skin images a portrait accepts: the usual 64×64 (or legacy 64×32) and their HD versions, every texel
 * drawn as k×k pixels: 128×128, 256×256, 512×512, 1024×1024, or any other multiple of 64 up to 1024 (and
 * 128×64, 256×128... legacy). The model's UVs
 * are relative, so an HD skin needs nothing more than its scale for the checks done on pixels.
 */
public final class SkinFormat {

    private SkinFormat() {}

    /** Largest scale accepted: 1024 px wide (bigger images are pointless on a portrait and heavy to keep). */
    public static final int MAX_SCALE = 16;
    /** Largest download (bytes): a 1024×1024 PNG with alpha fits. */
    public static final int MAX_BYTES = 6 * 1024 * 1024;

    /**
     * The scale k of a {@code width}×{@code height} skin (64k wide, 64k or 32k high, k a whole number up to
     * {@link #MAX_SCALE}), or 0 if it is not a skin.
     */
    public static int scale(int width, int height) {
        if (width < 64 || width % 64 != 0) return 0;
        int k = width / 64;
        if (k > MAX_SCALE) return 0;
        return height == width || height * 2 == width ? k : 0;
    }

    /** Legacy layout (no separate left arm and leg): twice as wide as high. */
    public static boolean legacy(int width, int height) {
        return height * 2 == width;
    }

    /**
     * Pixels that are transparent on a slim (3 px arms) skin: the 4th column of the right arm's front and its
     * last row, as vanilla checks at (54, 20) and (55, 31), here the middle of those texels at scale k.
     */
    public static int[][] slimProbes(int k) {
        return new int[][]{{54 * k + k / 2, 20 * k + k / 2}, {55 * k + k / 2, 31 * k + k / 2}};
    }

    /**
     * The copies (vanilla's, for 64×32 skins) giving a legacy skin its left arm and leg, mirrored from the right
     * ones, at scale k: {x, y, dx, dy, width, height} each, for {@code NativeImage.copyRect(..., true, false)}.
     */
    public static int[][] legacyCopies(int k) {
        int[][] base = {
                {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
                {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
                {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
        int[][] out = new int[base.length][];
        for (int i = 0; i < base.length; i++) {
            out[i] = new int[6];
            for (int j = 0; j < 6; j++) out[i][j] = base[i][j] * k;
        }
        return out;
    }
}
