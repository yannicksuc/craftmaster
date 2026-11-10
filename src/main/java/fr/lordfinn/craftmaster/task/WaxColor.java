package fr.lordfinn.craftmaster.task;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Colours of the wax seals: the CM gold and red of the poster first, then the usual dye colours. Also the
 * {@code wax} state of the envelope block (one texture per colour, see scripts/gen_assets.py).
 */
public enum WaxColor implements StringRepresentable {
    RED(0xB3202A, "rouge"), GOLD(0xD9A426, "or"), BLACK(0x26222A, "noir"), WHITE(0xE8E2D4, "blanc"),
    BLUE(0x2F4FA8, "bleu"), GREEN(0x3C7A2E, "vert"), PURPLE(0x6E3394, "violet"), PINK(0xD9668C, "rose"),
    ORANGE(0xD9701E, "orange"), CYAN(0x1F8C8C, "cyan"), BROWN(0x6E4424, "marron"), GRAY(0x6E6E74, "gris");

    /** Colour of the wax (RGB). */
    public final int rgb;
    public final String french;

    WaxColor(int rgb, String french) {
        this.rgb = rgb;
        this.french = french;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /** "rouge", "Red", "or", "gold"... → the colour, or null. */
    public static WaxColor parse(String s) {
        if (s == null) return null;
        String k = s.strip().toLowerCase(Locale.ROOT);
        for (WaxColor c : values()) if (c.id().equals(k) || c.french.equals(k)) return c;
        if (k.equals("doré") || k.equals("dorée") || k.equals("jaune")) return GOLD;
        if (k.equals("grise")) return GRAY;
        if (k.equals("verte")) return GREEN;
        if (k.equals("noire")) return BLACK;
        if (k.equals("blanche")) return WHITE;
        if (k.equals("bleue")) return BLUE;
        if (k.equals("violette")) return PURPLE;
        return null;
    }

    public static WaxColor of(String s) {
        WaxColor c = parse(s);
        return c == null ? RED : c;
    }
}
