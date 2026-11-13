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

import java.util.ArrayList;
import java.util.List;

/**
 * The task typed at the bottom of the picture during the last shots of an intro, as in the show's task intros
 * (cue {@code task_type}: title = the task's first sentence, ticks = how long it stays). Broadcast view only: typed
 * letter by letter in the typewriter font, a soft key click per letter, cream on a soft dark shadow, then it fades.
 */
public final class TypedTask {

    private static final FontDescription TYPEWRITER = new FontDescription.Resource(CraftMaster.id("typewriter"));
    private static final int CREAM = 0xF2E6C8;
    /** Seconds per letter, fade out (s). */
    private static final double LETTER = 0.045, FADE = 0.5;

    private static long start;
    private static double length;
    private static String text = "";
    private static int typed;

    private TypedTask() {}

    public static void start(ShowCue cue) {
        text = cue.title() == null ? "" : cue.title().strip();
        length = Math.max(1, cue.ticks() / 20.0);
        typed = 0;
        start = text.isEmpty() ? 0 : System.currentTimeMillis();
    }

    public static void reset() {
        start = 0;
    }

    private static double age() {
        if (start == 0) return -1;
        double a = (System.currentTimeMillis() - start) / 1000.0;
        if (a > length) {
            start = 0;
            return -1;
        }
        return a;
    }

    /** Lines of at most {@code maxWidth} (font units), cut between words. */
    static List<String> wrap(Font font, String s, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(font, next) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static int width(Font font, String s) {
        return font.width(Component.literal(s).withStyle(Style.EMPTY.withFont(TYPEWRITER)));
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!Broadcast.active() || Blackout.active()) return;
        double a = age();
        if (a < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = Math.max(1, Math.min(3, h / 210f));
        int maxWidth = (int) ((w * 0.8f) / scale);
        List<String> lines = wrap(font, text, maxWidth);
        if (lines.size() > 3) lines = lines.subList(0, 3);
        int total = 0;
        for (String l : lines) total += l.length();
        int letters = Math.min(total, (int) (a / LETTER));
        if (letters > typed) {
            int at = 0;
            char last = ' ';
            for (String l : lines) {
                if (letters - 1 < at + l.length()) {
                    last = l.charAt(letters - 1 - at);
                    break;
                }
                at += l.length();
            }
            if (!Character.isWhitespace(last))
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0f, 0.12f));
            typed = letters;
        }
        float alpha = (float) Math.min(1, (length - a) / FADE);
        if (alpha <= 0.02f) return;
        int lineH = 11;
        float blockH = lines.size() * lineH * scale;
        float y0 = h - blockH - h * 0.07f;
        // A soft dark band behind the text, so it reads on any picture
        int bandTop = (int) (y0 - 6 * scale), bandBottom = (int) (y0 + blockH + 4 * scale);
        g.fill(0, bandTop, w, bandBottom, (int) (alpha * 110) << 24);
        Matrix3x2fStack pose = g.pose();
        int left = letters;
        for (int i = 0; i < lines.size(); i++) {
            String full = lines.get(i);
            String shown = full.substring(0, Math.max(0, Math.min(full.length(), left)));
            left -= full.length();
            // Typed from the left of the full line, so the letters do not shift as they come
            float lw = width(font, full) * scale;
            pose.pushMatrix();
            pose.translate(w / 2f - lw / 2, y0 + i * lineH * scale);
            pose.scale(scale, scale);
            Component c = Component.literal(shown).withStyle(Style.EMPTY.withFont(TYPEWRITER));
            g.text(font, c, 1, 1, alpha(0x000000, alpha * 0.6f), false);
            g.text(font, c, 0, 0, alpha(CREAM, alpha), false);
            boolean typing = shown.length() < full.length() && left + full.length() > 0 && shown.length() > 0
                    || (i == lines.size() - 1 && letters >= total && (int) (a * 2.5) % 2 == 0);
            if (typing) {
                int cx = font.width(c);
                g.fill(cx + 1, 9, cx + 6, 10, alpha(CREAM, alpha * 0.8f));
            }
            pose.popMatrix();
            if (left <= 0) break;
        }
    }

    private static int alpha(int rgb, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
