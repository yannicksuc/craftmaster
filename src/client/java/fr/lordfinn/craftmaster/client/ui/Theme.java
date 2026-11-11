package fr.lordfinn.craftmaster.client.ui;

/**
 * CraftMaster's screen colours (the golden frames and red velvet of the poster, cream paper), shared by every
 * screen built on the UI kit. ARGB.
 */
public final class Theme {

    private Theme() {}

    public static final int PANEL = 0xE8140E10, BORDER = 0xFFD4A53A, BORDER_DARK = 0xFF7A5A1E, VELVET = 0xFF6B1420,
            VELVET_DARK = 0xFF3E0A12, PAPER = 0xFFF1E6C8, PAPER_EDGE = 0xFF8A6A3A, INK = 0xFF2A1A10, INK_SOFT = 0xFF7A5A30,
            TITLE = 0xFFFFE9A8, TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFFB8A890, GOLD = 0xFFFFC23D, SILVER = 0xFFD5DCE4,
            BRONZE = 0xFFD98A4E, OK = 0xFF4CC266, ERROR = 0xFFE0483C, HOVER = 0x30FFFFFF, SELECTED = 0x50FFC23D;

    /** Button faces: normal, hovered, pressed / active, disabled. */
    public static final int BUTTON = 0xFF4A1018, BUTTON_HOVER = 0xFF7A1C28, BUTTON_ON = 0xFF9A6A10, BUTTON_OFF = 0xFF2A2020;

    /** Positive scores: cold tones, from pale cyan (+1) to deep blue-violet (+10). */
    private static final int[] COLD = {0xFF7FD8E0, 0xFF5FC6DD, 0xFF45B0DA, 0xFF3497D4, 0xFF2C7FCB, 0xFF2A68BE, 0xFF2D52AE,
            0xFF33409C, 0xFF3A3189, 0xFF402476};
    /** Negative scores: warm tones, from yellow (-1) to dark red (-10). */
    private static final int[] WARM = {0xFFF2D25A, 0xFFF1BC48, 0xFFEFA43C, 0xFFEA8B34, 0xFFE2722E, 0xFFD85A2A, 0xFFC94426,
            0xFFB53222, 0xFF9C241E, 0xFF80191A};
    public static final int ZERO = 0xFF9A9088;

    /** The face colour of a score's digit head: cold for positive, warm for negative, grey for 0. */
    public static int scoreColor(int score) {
        if (score == 0) return ZERO;
        int i = Math.min(10, Math.abs(score)) - 1;
        return score > 0 ? COLD[i] : WARM[i];
    }

    /** {@code argb} made lighter ({@code f} > 0) or darker ({@code f} < 0), alpha kept. */
    public static int shade(int argb, float f) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        if (f >= 0) {
            r += (int) ((255 - r) * f);
            g += (int) ((255 - g) * f);
            b += (int) ((255 - b) * f);
        } else {
            r = (int) (r * (1 + f));
            g = (int) (g * (1 + f));
            b = (int) (b * (1 + f));
        }
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Black or white text, whichever reads best on that colour. */
    public static int textOn(int argb) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        return 0.299 * r + 0.587 * g + 0.114 * b > 150 ? 0xFF1A1410 : 0xFFFFFFFF;
    }
}
