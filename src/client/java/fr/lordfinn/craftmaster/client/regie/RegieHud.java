package fr.lordfinn.craftmaster.client.regie;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The voice control panel, top-left of the screen: "● Régie à l'écoute" with a level meter while the key is
 * held, "⏳ …" while the order is understood / run, then for a few seconds what was heard, what was done and how
 * to undo it. Only the admin who speaks sees it (it is drawn by their own game).
 */
final class RegieHud implements HudElement {

    private static final int RED = 0xFFFF5555, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55,
            YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00, BG = 0xB0000000, BORDER_OK = 0xFF55FF55, BORDER_KO = 0xFFFF5555;

    private record Line(FormattedCharSequence text, int color) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        VoiceRegie.View v = VoiceRegie.view;
        if (v.phase() == VoiceRegie.Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int maxWidth = Math.max(140, Math.min(260, g.guiWidth() / 2 - 12));
        List<Line> lines = new ArrayList<>();
        int border = 0;
        long now = System.currentTimeMillis();
        String dots = ".".repeat((int) (now / 400 % 3) + 1);

        switch (v.phase()) {
            case LISTENING -> {
                Recorder r = VoiceRegie.recorder();
                double secs = r == null ? 0 : r.seconds();
                // Blinking dot: unmistakable on stream that the mic is open
                int color = (now / 500) % 2 == 0 ? RED : 0xFFAA0000;
                add(lines, font, Component.translatable("hud.craftmaster.regie.listening"), color, maxWidth);
                add(lines, font, Component.literal(String.format("%.1f s", secs)), GRAY, maxWidth);
            }
            case THINKING -> add(lines, font, Component.translatable("hud.craftmaster.regie.thinking", dots), GOLD, maxWidth);
            case EXECUTING -> {
                heard(lines, font, v, maxWidth);
                add(lines, font, Component.translatable("hud.craftmaster.regie.executing", dots), GOLD, maxWidth);
            }
            case SHOWING -> {
                // Green: actions ran fine · red: failure · gold: only an answer, nothing was changed
                border = !v.ok() ? BORDER_KO : v.undoHint() ? BORDER_OK : GOLD;
                heard(lines, font, v, maxWidth);
                if (!v.reply().isBlank()) add(lines, font, Component.literal(v.reply()), v.ok() ? WHITE : RED, maxWidth);
                for (String l : v.lines()) add(lines, font, Component.literal("• " + l), l.startsWith("✗") ? RED : v.ok() ? GREEN : YELLOW, maxWidth);
                if (v.undoHint()) add(lines, font, Component.translatable("hud.craftmaster.regie.undo_hint"), GRAY, maxWidth);
            }
            default -> { }
        }
        if (lines.isEmpty()) return;

        int w = 0;
        for (Line l : lines) w = Math.max(w, font.width(l.text()));
        if (v.phase() == VoiceRegie.Phase.LISTENING) w = Math.max(w, 80);
        int h = lines.size() * (font.lineHeight + 1) + (v.phase() == VoiceRegie.Phase.LISTENING ? 6 : 0);
        // Top-left corner: free in vanilla, away from the titles of the show (centre) and the chat (bottom)
        int x = 8, y = 8;
        g.fill(x - 4, y - 4, x + w + 4, y + h + 3, BG);
        if (border != 0) g.fill(x - 4, y - 4, x - 2, y + h + 3, border);
        int ly = y;
        for (Line l : lines) {
            g.text(font, l.text(), x, ly, l.color(), true);
            ly += font.lineHeight + 1;
        }
        if (v.phase() == VoiceRegie.Phase.LISTENING) {
            Recorder r = VoiceRegie.recorder();
            float level = r == null ? 0 : r.level();
            g.fill(x, ly + 1, x + w, ly + 4, 0xFF333333);
            g.fill(x, ly + 1, x + Math.round(w * Math.min(1f, level * 1.5f)), ly + 4, level > 0.6f ? YELLOW : GREEN);
        }
    }

    private static void heard(List<Line> lines, Font font, VoiceRegie.View v, int maxWidth) {
        if (!v.transcript().isBlank())
            add(lines, font, Component.translatable("hud.craftmaster.regie.heard", v.transcript()), GRAY, maxWidth);
    }

    private static void add(List<Line> out, Font font, FormattedText text, int color, int maxWidth) {
        for (FormattedCharSequence s : font.split(text, maxWidth)) out.add(new Line(s, color));
    }
}
