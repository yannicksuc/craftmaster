package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.task.LetterMotion;
import fr.lordfinn.craftmaster.task.TaskMarkup;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The printed side of the task letter: its text laid out in letter pixels (see {@link LetterMotion}) with the
 * typewriter font (craftmaster:typewriter, 1 font pixel = 1 letter pixel) and the task markup ({@link TaskMarkup}):
 * a red heading, centred lines, separators, a handwritten note in blue ink, the final rule, red stamps, hidden
 * parts as black bars, the signature at the bottom right. Also the pixel-art pieces (paper, seal, residue).
 */
final class LetterView {

    static final FontDescription TYPEWRITER = new FontDescription.Resource(CraftMaster.id("typewriter"));
    static final Identifier PAPER = CraftMaster.id("textures/gui/task/letter_paper.png");
    static final Identifier BACK = CraftMaster.id("textures/gui/task/letter_back.png");
    static final Identifier SEAL = CraftMaster.id("textures/gui/task/letter_seal.png");
    static final Identifier RESIDUE = CraftMaster.id("textures/gui/task/letter_residue.png");
    static final int SEAL_SIZE = 48;

    static final int INK = 0xFF3A3530, RED = 0xFF9E2A2B, STAMP = 0xFFB02A2A, GREY = 0xFF8A8272, BLUE = 0xFF2A4A9A,
            BAR = 0xFF1E1A18, READABLE_SECRET = 0xFF8A4AA8;
    static final int MARGIN = 22, LINE = 13, NOTE_LINE = 11;

    /** A laid-out piece: text at (x, y), a separator, a stamp's box. */
    record Item(int kind, FormattedCharSequence text, int x, int y, int w, int color) {}

    static final int TEXT = 0, SEPARATOR = 1, STAMP_BOX = 2;

    private final Font font;
    private TaskNetwork.Paper paper;
    private final List<Item> items = new ArrayList<>();
    private int bottom, panels = 3;
    private FormattedCharSequence signature, outsideHeader, outsideTo;

    LetterView(Font font, TaskNetwork.Paper paper) {
        this.font = font;
        this.paper = paper;
    }

    TaskNetwork.Paper paper() {
        return paper;
    }

    int panels() {
        return panels;
    }

    int height() {
        return LetterMotion.height(panels);
    }

    static Style type() {
        return Style.EMPTY.withFont(TYPEWRITER);
    }

    // ---------------------------------------------------------------- layout

    void layout(TaskNetwork.Paper p, Map<String, String> vars) {
        paper = p;
        items.clear();
        int inner = LetterMotion.WIDTH - 2 * MARGIN;
        // Outside of the folded letter: the round and the addressee, like an envelope
        String header = p.header().getString().toUpperCase(Locale.ROOT);
        outsideHeader = header.isEmpty() ? null : Component.literal(header).withStyle(type().withColor(GREY)).getVisualOrderText();
        outsideTo = p.from().isEmpty() ? null
                : Component.translatable("paper.craftmaster.to", p.from()).withStyle(type().withColor(INK)).getVisualOrderText();

        List<Item> body = new ArrayList<>();
        int y = 0;
        TaskMarkup.Doc doc = TaskMarkup.parse(p.text(), vars);
        boolean hasHeading = doc.blocks().stream().anyMatch(b -> b.kind() == TaskMarkup.Kind.HEADING);
        if (!p.title().isBlank()) {
            y = centred(body, Component.literal(p.title().toUpperCase(Locale.ROOT)).withStyle(type().withColor(RED)), y, inner, LINE);
            y += hasHeading ? 2 : 7;
        }
        for (TaskMarkup.Block b : doc.blocks()) {
            switch (b.kind()) {
                case GAP -> y += 7;
                case HEADING -> {
                    y = centred(body, spans(b.spans(), RED, true, false), y, inner, LINE);
                    y += 6;
                }
                case LINE -> y = centred(body, spans(b.spans(), INK, false, false), y, inner, LINE);
                case SEPARATOR -> {
                    body.add(new Item(SEPARATOR, null, LetterMotion.WIDTH / 2, y + 4, 0, GREY));
                    y += 12;
                }
                case NOTE -> {
                    y += 3;
                    MutableComponent c = spans(b.spans(), BLUE, false, true);
                    for (FormattedCharSequence l : font.split(c, inner - 16)) {
                        body.add(new Item(TEXT, l, MARGIN + 10, y, font.width(l), BLUE));
                        y += NOTE_LINE;
                    }
                    y += 3;
                }
                case RULE -> {
                    y += 5;
                    y = centred(body, spans(b.spans(), INK, false, false).withStyle(s -> s.withBold(true)), y, inner, LINE);
                    y += 2;
                }
                case STAMP -> {
                    y += 3;
                    MutableComponent c = spans(b.spans(), STAMP, true, false).withStyle(s -> s.withBold(true));
                    FormattedCharSequence l = font.split(c, inner - 16).getFirst();
                    int w = font.width(l);
                    int x = (LetterMotion.WIDTH - w) / 2;
                    body.add(new Item(STAMP_BOX, null, x - 6, y - 4, w + 12, STAMP));
                    body.add(new Item(TEXT, l, x, y, w, STAMP));
                    y += 19;
                }
            }
        }
        // The round's rules (the editor's list) and the duration
        if (!p.rules().isEmpty()) {
            y += 6;
            for (String r : p.rules()) {
                TaskMarkup.Doc rd = TaskMarkup.parse(r, vars);
                if (rd.blocks().isEmpty()) continue;
                MutableComponent c = Component.literal("• ").withStyle(type().withColor(INK)).append(spans(rd.blocks().getFirst().spans(), INK, false, false));
                y = centred(body, c, y, inner, LINE);
            }
        }
        if (p.seconds() > 0) {
            y += 6;
            y = centred(body, Component.translatable("paper.craftmaster.letter_duration", PaperView.clock(p.seconds())).withStyle(type().withColor(GREY)), y, inner, LINE);
        }
        // In the middle panel if it fits (like the real letters), else from its top, flowing into the next panels
        int room = LetterMotion.PANEL - 20;
        int top = y <= room ? LetterMotion.TOP + (LetterMotion.PANEL - y) / 2 + 2 : LetterMotion.TOP + 12;
        for (Item it : body) items.add(new Item(it.kind(), it.text(), it.x(), it.y() + top, it.w(), it.color()));
        bottom = top + y;
        // Signature at the bottom right of the last panel
        String sig = doc.signature() != null ? doc.signature() : Component.translatable("paper.craftmaster.signature").getString();
        signature = sig.isBlank() ? null : Component.literal(sig.toUpperCase(Locale.ROOT)).withStyle(type().withColor(INK)).getVisualOrderText();
        panels = LetterMotion.panels(bottom + 26);
    }

    /** Lines of a component, wrapped and centred. @return the y after them */
    private int centred(List<Item> out, Component c, int y, int inner, int lineH) {
        for (FormattedCharSequence l : font.split(c, inner)) {
            int w = font.width(l);
            out.add(new Item(TEXT, l, (LetterMotion.WIDTH - w) / 2, y, w, INK));
            y += lineH;
        }
        return y;
    }

    /** The spans of a block as one component: typewriter (or a hand-written default font for a note). */
    private static MutableComponent spans(List<TaskMarkup.Span> spans, int ink, boolean caps, boolean hand) {
        MutableComponent out = Component.empty();
        for (TaskMarkup.Span s : spans) {
            Style st = hand ? Style.EMPTY.withItalic(true) : type();
            int color = s.color().isEmpty() ? ink : 0xFF000000 | TaskMarkup.inkColor(s.color());
            st = st.withColor(color & 0xFFFFFF)
                    .withBold((s.style() & TaskMarkup.BOLD) != 0 ? true : null)
                    .withUnderlined((s.style() & TaskMarkup.UNDERLINE) != 0 ? true : null)
                    .withStrikethrough((s.style() & TaskMarkup.STRIKE) != 0 ? true : null);
            String text = caps ? s.text().toUpperCase(Locale.ROOT) : s.text();
            switch (s.secret()) {
                // Redacted: a black bar about as long as the hidden words (the text itself never came here)
                case TaskMarkup.HIDDEN -> out.append(Component.literal("█".repeat(Math.max(1, (text.length() * 7 + 8) / 9)))
                        .withStyle(Style.EMPTY.withColor(BAR & 0xFFFFFF)));
                // An organiser reads it, marked as still hidden
                case TaskMarkup.HIDDEN_READABLE -> out.append(Component.literal(text).withStyle(st.withColor(READABLE_SECRET & 0xFFFFFF).withUnderlined(true)));
                default -> out.append(Component.literal(text).withStyle(st));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- drawing (letter pixels)

    /** Paper rows [from, to) of a texture tiling vertically, drawn from local y {@code y}. */
    static void paper(GuiGraphicsExtractor g, Identifier tex, int y, int from, int to, int argb) {
        int r = from;
        while (r < to) {
            int v = Math.floorMod(r, 256);
            int n = Math.min(to - r, 256 - v);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, y + (r - from), 0, v, LetterMotion.WIDTH, n, 256, 256, argb);
            r += n;
        }
    }

    /** The printed content between letter rows [top, bottom) (the caller clips and transforms). */
    void drawContent(GuiGraphicsExtractor g, int top, int bottomY) {
        for (Item it : items) {
            if (it.y() > bottomY + 4 || it.y() + LINE < top - 4) continue;
            switch (it.kind()) {
                case SEPARATOR -> {
                    int cx = it.x(), cy = it.y();
                    g.fill(cx - 22, cy, cx - 6, cy + 1, it.color());
                    g.fill(cx + 6, cy, cx + 22, cy + 1, it.color());
                    g.fill(cx - 1, cy - 2, cx + 1, cy + 3, it.color());
                    g.fill(cx - 2, cy - 1, cx + 2, cy + 2, it.color());
                }
                case STAMP_BOX -> dashedBox(g, it.x(), it.y(), it.w(), 16, it.color());
                default -> g.text(font, it.text(), it.x(), it.y(), it.color(), false);
            }
        }
        if (signature != null) {
            int sy = height() - 20;
            if (sy >= top - 4 && sy <= bottomY) {
                int w = font.width(signature);
                g.text(font, signature, LetterMotion.WIDTH - MARGIN - w, sy, INK, false);
                g.fill(LetterMotion.WIDTH - MARGIN - w, sy + 10, LetterMotion.WIDTH - MARGIN, sy + 11, 0x60000000 | (INK & 0xFFFFFF));
            }
        }
    }

    /** The outside of the top panel, closed: round and addressee written on it. {@code h}: its height drawn. */
    void drawOutside(GuiGraphicsExtractor g) {
        if (outsideHeader != null) g.text(font, outsideHeader, (LetterMotion.WIDTH - font.width(outsideHeader)) / 2, 12, GREY, false);
        if (outsideTo != null) g.text(font, outsideTo, (LetterMotion.WIDTH - font.width(outsideTo)) / 2, 27, INK, false);
    }

    /** A rubber stamp's frame: red dashes, 2 px thick, the ink a little uneven. */
    private static void dashedBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int faded = 0xB0000000 | (color & 0xFFFFFF);
        for (int i = 0; i < w; i += 6) {
            int e = Math.min(w, i + 4);
            g.fill(x + i, y, x + e, y + 2, (i / 6) % 3 == 1 ? faded : color);
            g.fill(x + i, y + h - 2, x + e, y + h, (i / 6) % 4 == 2 ? faded : color);
        }
        for (int j = 0; j < h; j += 6) {
            int e = Math.min(h, j + 4);
            g.fill(x, y + j, x + 2, y + e, color);
            g.fill(x + w - 2, y + j, x + w, y + e, (j / 6) % 2 == 1 ? faded : color);
        }
    }

    /** The wax seal (tinted grey texture), centred on (cx, cy). */
    static void seal(GuiGraphicsExtractor g, Identifier tex, int cx, int cy, int rgb, int alpha) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - SEAL_SIZE / 2, cy - SEAL_SIZE / 2, 0, 0, SEAL_SIZE, SEAL_SIZE, SEAL_SIZE, SEAL_SIZE,
                alpha << 24 | rgb);
    }
}
