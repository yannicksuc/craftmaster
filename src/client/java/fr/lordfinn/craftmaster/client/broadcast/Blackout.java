package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.ShowCue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

/**
 * The round's launch after its intro ({@code MancheIntros.launch}), on every game with the mod — players and
 * broadcast alike: the picture fades to black, the round's name is typed letter by letter on the black (typewriter
 * font, a soft key click per letter) with "Manche 2" above it, then the picture opens on the round. The players are
 * teleported by the server while it is black.
 */
public final class Blackout {

    private static final FontDescription TYPEWRITER = new FontDescription.Resource(CraftMaster.id("typewriter"));
    private static final int CREAM = 0xF2E6C8, GOLD = 0xD9A93A;

    private static long start;
    private static int in, hold, out;
    private static String title = "", subtitle = "";
    private static int typed;

    private Blackout() {}

    /** A {@code blackout} cue: title = round name, subtitle = "Manche 2", player = "in,hold,out" (ticks). */
    public static void start(ShowCue cue) {
        String[] t = cue.player().split(",");
        try {
            in = Integer.parseInt(t[0].trim());
            hold = Integer.parseInt(t[1].trim());
            out = Integer.parseInt(t[2].trim());
        } catch (RuntimeException e) {
            in = 12;
            hold = 64;
            out = 26;
        }
        title = cue.title();
        subtitle = cue.subtitle();
        typed = 0;
        start = System.currentTimeMillis();
    }

    public static void reset() {
        start = 0;
    }

    /** Is the picture going to / coming back from black (other HUD elements stay out of it)? */
    public static boolean active() {
        return age() >= 0;
    }

    /** Seconds since the start, or -1 when not running. */
    private static double age() {
        if (start == 0) return -1;
        double a = (System.currentTimeMillis() - start) / 1000.0;
        if (a > (in + hold + out) / 20.0) {
            start = 0;
            return -1;
        }
        return a;
    }

    /** How black the picture is (0..1). */
    static float black(double a) {
        double i = in / 20.0, h = hold / 20.0, o = out / 20.0;
        if (a < i) return (float) smooth(a / i);
        if (a < i + h) return 1;
        return (float) (1 - smooth((a - i - h) / o));
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        double a = age();
        if (a < 0) return;
        float b = black(a);
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, (int) (b * 255) << 24);

        // The text: typed during the black, fades with the reveal
        double i = in / 20.0, holdS = hold / 20.0;
        double typing = a - i - 0.25;
        if (typing < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int letters = Math.min(title.length(), (int) (typing / 0.07));
        if (letters > typed) {
            // One soft click per new letter (not per frame)
            if (!Character.isWhitespace(title.charAt(letters - 1)))
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.9f, 0.18f));
            typed = letters;
        }
        float textAlpha = a < i + holdS ? 1 : b;
        if (textAlpha <= 0.02f) return;

        float scale = Math.max(1, Math.min(4, h / 90f));
        Component line = Component.literal(title.substring(0, letters)).withStyle(Style.EMPTY.withFont(TYPEWRITER));
        float full = font.width(Component.literal(title).withStyle(Style.EMPTY.withFont(TYPEWRITER))) * scale;
        if (full > w - 40) {
            scale *= (w - 40) / full;
            full = font.width(Component.literal(title).withStyle(Style.EMPTY.withFont(TYPEWRITER))) * scale;
        }
        Matrix3x2fStack pose = g.pose();
        // "MANCHE 2" small and gold above, appears with the first letter
        if (!subtitle.isEmpty()) {
            float s = scale * 0.5f;
            Component sub = Component.literal(subtitle.toUpperCase(java.util.Locale.ROOT)).withStyle(Style.EMPTY.withFont(TYPEWRITER));
            float sw = font.width(sub) * s;
            pose.pushMatrix();
            pose.translate(w / 2f - sw / 2, h / 2f - 15 * scale);
            pose.scale(s, s);
            g.text(font, sub, 0, 0, alpha(GOLD, textAlpha), false);
            pose.popMatrix();
        }
        // Typed from the left of the full line, so letters do not shift as they come
        pose.pushMatrix();
        pose.translate(w / 2f - full / 2, h / 2f - 4 * scale);
        pose.scale(scale, scale);
        g.text(font, line, 0, 0, alpha(CREAM, textAlpha), false);
        // Blinking caret while typing
        if (letters < title.length() || (int) (a * 2.5) % 2 == 0) {
            int cx = font.width(line);
            g.fill(cx + 1, 9, cx + 6, 10, alpha(CREAM, textAlpha * 0.8f));
        }
        pose.popMatrix();
    }

    private static int alpha(int rgb, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
