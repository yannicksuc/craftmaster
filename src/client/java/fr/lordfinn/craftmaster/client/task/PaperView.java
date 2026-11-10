package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a card of cream paper with its text laid out like a printed page: a small header ("MANCHE 3 · LE PONT"),
 * the title in large ink letters, a gold rule, the paragraphs, the rules as diamond bullets, the duration and the
 * CM wax seal at the bottom. Used by the reading screen, the opening animation and the overlay card (HUD / TV).
 * Looks: cream paper, aged parchment, notebook page (lined, red margin), sticky note; invisible ink under UV light.
 */
public final class PaperView {

    static final Identifier PAPER = CraftMaster.id("textures/gui/task/paper.png");
    static final Identifier EDGES = CraftMaster.id("textures/gui/task/paper_edges.png");
    static final Identifier SEAL = CraftMaster.id("textures/gui/task/wax_seal.png");

    /** Paper colour, ink colour, accent (rules, ornaments) of each look. */
    record Look(int paper, int ink, int accent, boolean lined, boolean burnt) {}

    static Look look(String id) {
        return switch (id) {
            case "parchment" -> new Look(0xFFE6CF9E, 0xFF3D2410, 0xFF8A5A22, false, true);
            case "notebook" -> new Look(0xFFF8F7F0, 0xFF1F2C78, 0xFFB8323A, true, false);
            case "postit" -> new Look(0xFFFFE680, 0xFF2A2620, 0xFFB08A10, false, false);
            default -> new Look(0xFFF4E9CF, 0xFF2B1F14, 0xFFB08A3A, false, false);
        };
    }

    /** A laid-out line: text, x offset inside the card, y, colour, scale; or a drawing (rule, bullet). */
    record Line(FormattedCharSequence text, int x, int y, int color, float scale, int kind) {}

    static final int TEXT = 0, ORNAMENT = 1, BULLET = 2, FAINT = 3;

    private final Font font;
    private final TaskNetwork.Paper paper;
    private final Look look;
    private final boolean uv;
    private final List<Line> lines = new ArrayList<>();
    private int contentHeight;
    private int width;
    private final float titleScale;

    public PaperView(Font font, TaskNetwork.Paper paper, float titleScale) {
        this.font = font;
        this.paper = paper;
        this.look = look(paper.look());
        this.uv = paper.has(TaskNetwork.INK) && paper.has(TaskNetwork.LIT);
        this.titleScale = titleScale;
    }

    public TaskNetwork.Paper paper() {
        return paper;
    }

    int ink() {
        return uv ? 0xFFC9A8FF : look.ink();
    }

    int paperColor() {
        return uv ? 0xFF3A2C55 : look.paper();
    }

    /** Lays the text out for a card {@code w} wide (margins included). @return the height the content needs */
    public int layout(int w) {
        width = w;
        lines.clear();
        int margin = paper.look().equals("notebook") ? 30 : 16;
        int inner = w - margin - 16;
        int y = 14;
        int lh = font.lineHeight + 2;
        // Header: small capitals
        if (!paper.header().isEmpty()) {
            String h = paper.header().toUpperCase(Locale.ROOT);
            for (FormattedCharSequence l : font.split(FormattedText.of(h), inner)) {
                lines.add(new Line(l, centre(l, w, 1f), y, look.accent(), 1f, TEXT));
                y += lh;
            }
            y += 2;
        }
        if (!paper.from().isEmpty() && "task".equals(paper.kind())) {
            Component to = Component.translatable("paper.craftmaster.to", paper.from());
            for (FormattedCharSequence l : font.split(to, inner)) {
                lines.add(new Line(l, centre(l, w, 1f), y, look.accent(), 1f, FAINT));
                y += lh;
            }
        }
        // Title
        String title = paper.title().isEmpty() && "task".equals(paper.kind()) ? "" : paper.title();
        if (!title.isEmpty()) {
            y += 2;
            int tw = (int) (inner / titleScale);
            for (FormattedCharSequence l : font.split(FormattedText.of(title, Style.EMPTY.withBold(true)), tw)) {
                lines.add(new Line(l, centre(l, w, titleScale), y, ink(), titleScale, TEXT));
                y += (int) (font.lineHeight * titleScale) + 2;
            }
            y += 3;
        }
        // Gold rule with a diamond
        lines.add(new Line(null, margin, y, look.accent(), 1f, ORNAMENT));
        y += 10;
        // Body
        boolean invisible = paper.has(TaskNetwork.INK) && !paper.has(TaskNetwork.LIT);
        boolean torn = !paper.pieces().isEmpty();
        if (invisible) {
            // A blank sheet; only a faint hint for the curious
            y += lh * 4;
            Component hint = Component.translatable("paper.craftmaster.blank");
            for (FormattedCharSequence l : font.split(hint, inner)) {
                lines.add(new Line(l, centre(l, w, 1f), y, 0x30000000 | (look.ink() & 0xFFFFFF), 1f, FAINT));
                y += lh;
            }
            y += lh * 3;
        } else if (torn && paper.text().isEmpty()) {
            y = layoutPieces(y, margin, inner, lh);
        } else {
            y = layoutText(paper.text(), y, margin, inner, lh);
        }
        // Rules
        if (!paper.rules().isEmpty()) {
            y += 4;
            Component label = Component.translatable("paper.craftmaster.rules");
            FormattedCharSequence ls = Component.literal(label.getString().toUpperCase(Locale.ROOT)).getVisualOrderText();
            lines.add(new Line(ls, margin, y, look.accent(), 1f, TEXT));
            y += lh + 1;
            for (String r : paper.rules()) {
                boolean first = true;
                for (FormattedCharSequence l : font.split(FormattedText.of(r), inner - 12)) {
                    if (first) lines.add(new Line(null, margin + 2, y, look.accent(), 1f, BULLET));
                    lines.add(new Line(l, margin + 12, y, ink(), 1f, TEXT));
                    y += lh;
                    first = false;
                }
                y += 2;
            }
        }
        // Duration
        if (paper.seconds() > 0) {
            y += 4;
            Component d = Component.translatable("paper.craftmaster.duration", clock(paper.seconds()));
            FormattedCharSequence l = d.getVisualOrderText();
            lines.add(new Line(l, centre(l, w, 1f), y, look.accent(), 1f, TEXT));
            y += lh;
        }
        // Room for the wax seal stamp
        y += "task".equals(paper.kind()) || "text".equals(paper.kind()) ? 34 : 10;
        contentHeight = y;
        return y;
    }

    private int layoutText(String text, int y, int margin, int inner, int lh) {
        String[] paragraphs = text.split("\n\n");
        for (int p = 0; p < paragraphs.length; p++) {
            for (String raw : paragraphs[p].split("\n")) {
                if (raw.isBlank()) {
                    y += lh / 2;
                    continue;
                }
                for (FormattedCharSequence l : font.split(FormattedText.of(raw), inner)) {
                    lines.add(new Line(l, margin, y, ink(), 1f, TEXT));
                    y += lh;
                }
            }
            if (p < paragraphs.length - 1) y += lh / 2 + 2;
        }
        return y;
    }

    /** Torn note: each piece on its own strip, the missing ones as dotted gaps. */
    private int layoutPieces(int y, int margin, int inner, int lh) {
        List<String> pieces = paper.pieces();
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            int top = y;
            y += 5;
            if (piece.isEmpty()) {
                Component missing = Component.translatable("paper.craftmaster.missing_piece", i + 1);
                FormattedCharSequence l = missing.getVisualOrderText();
                lines.add(new Line(l, centre(l, width, 1f), y + 4, 0x80000000 | (look.ink() & 0xFFFFFF), 1f, FAINT));
                y += lh + 10;
            } else {
                y = layoutText(piece, y, margin, inner, lh);
            }
            y += 4;
            // The strip itself is drawn behind (kind = piece index + 10, x = top)
            lines.add(new Line(null, top, y, piece.isEmpty() ? 0 : 1, 1f, 10 + i));
            y += 4;
        }
        return y;
    }

    private int centre(FormattedCharSequence l, int w, float scale) {
        return (int) ((w - font.width(l) * scale) / 2);
    }

    static String clock(int seconds) {
        return seconds >= 3600 ? seconds / 3600 + " h " + String.format(Locale.ROOT, "%02d", seconds % 3600 / 60)
                : seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    public int contentHeight() {
        return contentHeight;
    }

    /** Width given to the last {@link #layout}. */
    public int contentWidth() {
        return width;
    }

    // ---------------------------------------------------------------- drawing

    /** The sheet alone (no text): paper colour, grain, edges, notebook lines, drop shadow. */
    public void drawSheet(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        int a = alpha << 24;
        // Soft shadow under the sheet
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, (alpha / 3) << 24);
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, (alpha / 3) << 24);
        int base = paperColor();
        g.blit(RenderPipelines.GUI_TEXTURED, PAPER, x, y, 0, 0, w, h, 256, 256, 256, 256, a | (base & 0xFFFFFF));
        if (look.burnt() && !uv) g.blit(RenderPipelines.GUI_TEXTURED, EDGES, x, y, 0, 0, w, h, 256, 256, 256, 256, a | 0x5A3410);
        else g.blit(RenderPipelines.GUI_TEXTURED, EDGES, x, y, 0, 0, w, h, 256, 256, 256, 256, (alpha * 2 / 5) << 24 | 0x8A6A3A);
        if (uv) {
            // Under the lamp: violet light, brighter in the middle
            g.fillGradient(x, y, x + w, y + h / 2, (alpha / 4) << 24 | 0x7A4CFF, (alpha / 8) << 24 | 0xB07CFF);
            g.fillGradient(x, y + h / 2, x + w, y + h, (alpha / 8) << 24 | 0xB07CFF, (alpha / 4) << 24 | 0x7A4CFF);
        }
        if (look.lined()) {
            int lh = font.lineHeight + 2;
            for (int ly = y + 14 + lh - 2; ly < y + h - 6; ly += lh) g.fill(x + 4, ly, x + w - 4, ly + 1, (alpha / 4) << 24 | 0x4A78C8);
            g.fill(x + 24, y, x + 25, y + h, (alpha / 2) << 24 | 0xC83C46);
        }
        g.outline(x, y, w, h, (alpha / 3) << 24 | 0x6A5030);
    }

    /**
     * The text, scrolled by {@code scroll} and cut to [clipTop, clipBottom] (screen y); {@code alpha} 0..255
     * fades it in.
     */
    public void drawText(GuiGraphicsExtractor g, int x, int y, int scroll, int alpha, int h) {
        if (alpha < 8) return;
        int fade = Math.max(8, alpha);
        for (Line l : lines) {
            int ly = y + l.y() - scroll;
            if (l.kind() >= 10) {
                drawStrip(g, x, y - scroll + l.x(), y - scroll + l.y(), l.color() == 1, fade);
                continue;
            }
            if (ly < y - 20 || ly > y + h + 4) continue;
            int color = withAlpha(l.color(), fade);
            switch (l.kind()) {
                case ORNAMENT -> drawRule(g, x + l.x(), ly, width - 2 * l.x(), color);
                case BULLET -> {
                    int cx = x + l.x() + 3, cy = ly + 3;
                    g.fill(cx - 1, cy - 2, cx + 1, cy + 2, color);
                    g.fill(cx - 2, cy - 1, cx + 2, cy + 1, color);
                }
                default -> {
                    if (l.scale() != 1f) {
                        g.pose().pushMatrix();
                        g.pose().translate(x + l.x(), ly);
                        g.pose().scale(l.scale(), l.scale());
                        g.text(font, l.text(), 0, 0, color, false);
                        g.pose().popMatrix();
                    } else if (uv && l.kind() == TEXT) {
                        // Glowing ink: a soft halo behind the letters
                        g.text(font, l.text(), x + l.x() + 1, ly, withAlpha(0xFF7A4CFF, fade / 3), false);
                        g.text(font, l.text(), x + l.x(), ly, color, false);
                    } else {
                        g.text(font, l.text(), x + l.x(), ly, color, false);
                    }
                }
            }
        }
    }

    /** A torn strip of paper (behind a piece's text): ragged top and bottom edges, darker if missing. */
    private void drawStrip(GuiGraphicsExtractor g, int x, int top, int bottom, boolean found, int alpha) {
        int col = found ? 0x18000000 : 0x10000000;
        g.fill(x + 6, top + 2, x + width - 6, bottom - 2, withAlpha(col, Math.min(alpha, 255)));
        // Ragged tear lines
        for (int i = 0; x + 6 + i * 3 < x + width - 6; i++) {
            int jitter = Math.floorMod(i * 7 + top * 3, 4);
            int c = withAlpha(0x60000000 | (look.ink() & 0xFFFFFF), alpha);
            g.fill(x + 6 + i * 3, bottom - 1 + (jitter > 1 ? 1 : 0), x + 9 + i * 3, bottom + (jitter > 1 ? 1 : 0), c);
            if (!found) {
                // Dotted outline of the missing piece
                if (i % 2 == 0) g.fill(x + 6 + i * 3, top + 2, x + 8 + i * 3, top + 3, c);
            }
        }
    }

    private static void drawRule(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        int cx = x + w / 2;
        g.fill(x + 10, y + 2, cx - 6, y + 3, color);
        g.fill(cx + 6, y + 2, x + w - 10, y + 3, color);
        // Diamond in the middle
        g.fill(cx - 1, y - 1, cx + 1, y + 6, color);
        g.fill(cx - 2, y, cx + 2, y + 5, color);
        g.fill(cx - 3, y + 1, cx + 3, y + 4, color);
    }

    /** The CM wax seal (tinted with its colour), {@code size} px wide, centred on (cx, cy). */
    public static void drawSeal(GuiGraphicsExtractor g, int cx, int cy, int size, String wax, int alpha) {
        int rgb = WaxColor.of(wax).rgb;
        g.blit(RenderPipelines.GUI_TEXTURED, SEAL, cx - size / 2, cy - size / 2, 0, 0, size, size, 64, 64, 64, 64, alpha << 24 | rgb);
    }

    static int withAlpha(int argb, int alpha) {
        int a = (argb >>> 24) * Mth.clamp(alpha, 0, 255) / 255;
        return a << 24 | (argb & 0xFFFFFF);
    }

    /** Whole card: sheet, text, seal stamp at the bottom. */
    public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int scroll, int alpha, int textAlpha) {
        drawSheet(g, x, y, w, h, alpha);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        drawText(g, x, y, scroll, textAlpha, h);
        if (("task".equals(paper.kind()) || "text".equals(paper.kind())) && !(paper.has(TaskNetwork.INK) && !paper.has(TaskNetwork.LIT)))
            drawSeal(g, x + w / 2, y + contentHeight - 18 - scroll, 26, paper.wax(), textAlpha);
        g.disableScissor();
    }
}
