package fr.lordfinn.craftmaster.client.task;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Reading a card: a task, a note, a free text. A sealed envelope first plays its opening, like on TV: the
 * envelope comes in, the wax cracks and falls apart, the flap opens on its red lining, the card slides out and
 * unfolds in front of the reader. Then the card is read (scroll with the wheel); organisers get buttons to reveal,
 * show on screen and edit. A secret task (non-admins) shows the sealed envelope instead.
 * Click, Space or Enter skips the animation; Escape or J closes.
 */
public class PaperScreen extends Screen {

    static final Identifier INNER = CraftMaster.id("textures/gui/task/envelope_inner.png");
    static final Identifier POCKET = CraftMaster.id("textures/gui/task/envelope_pocket.png");
    static final Identifier FLAP = CraftMaster.id("textures/gui/task/envelope_flap.png");
    static final Identifier FLAP_IN = CraftMaster.id("textures/gui/task/envelope_flap_in.png");
    static final Identifier WAX_L = CraftMaster.id("textures/gui/task/wax_half_l.png");
    static final Identifier WAX_R = CraftMaster.id("textures/gui/task/wax_half_r.png");

    // Timeline of the opening, in ticks (1/20 s)
    static final float T_ARRIVE = 12, T_CRACK = 20, T_SPLIT = 25, T_FLAP = 36, T_CARD = 52, T_UNFOLD = 66, T_TEXT = 74;
    /** The quick unfold of a found note. */
    static final float F_UNFOLD = 12;

    private final TaskNetwork.Paper paper;
    private final Screen parent;
    private final boolean envelope, secret, found;
    private PaperView view;
    private long start;
    private float scroll;
    private int cardX, cardY, cardW, cardH;
    private final boolean[] played = new boolean[8];
    private record Crumb(float x, float y, float vx, float vy, int size) {}
    private final List<Crumb> crumbs = new ArrayList<>();

    public PaperScreen(TaskNetwork.Paper paper, Screen parent) {
        super(Component.translatable("screen.craftmaster.paper"));
        this.paper = paper;
        this.parent = parent;
        this.envelope = paper.has(TaskNetwork.ENVELOPE);
        this.secret = paper.has(TaskNetwork.SECRET);
        this.found = paper.has(TaskNetwork.FOUND);
        this.start = System.nanoTime();
    }

    public TaskNetwork.Paper paper() {
        return paper;
    }

    private float ticks(float partial) {
        return (System.nanoTime() - start) / 50_000_000f;
    }

    private boolean animating() {
        float t = ticks(0);
        return envelope ? t < T_TEXT : found && t < F_UNFOLD;
    }

    private void skip() {
        float end = envelope ? T_TEXT : F_UNFOLD;
        start = System.nanoTime() - (long) (end * 50_000_000L);
        for (int i = 0; i < played.length; i++) played[i] = true;
    }

    @Override
    protected void init() {
        boolean small = paper.look().equals("postit");
        cardW = Math.min(small ? 200 : 300, width - 24);
        view = new PaperView(font, paper, small ? 1.4f : 2f);
        int content = view.layout(cardW);
        int maxH = height - (hasButtons() ? 56 : 30);
        cardH = Math.min(content, maxH);
        cardX = (width - cardW) / 2;
        cardY = Math.max(10, (height - cardH - (hasButtons() ? 28 : 0)) / 2);
        scroll = Mth.clamp(scroll, 0, maxScroll());
        if (secret) return;
        List<Button> buttons = new ArrayList<>();
        if (paper.has(TaskNetwork.CAN_REVEAL) && "task".equals(paper.kind()))
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.reveal"), b -> {
                actions(Map.of("type", "task.reveal", "manche", paper.mancheId(), "season", paper.season()));
                onClose();
            }).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.reveal.tip"))).build());
        if (paper.has(TaskNetwork.CAN_EDIT) && "task".equals(paper.kind())) {
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.show"), b -> {
                actions(Map.of("type", "task.show", "manche", paper.mancheId(), "season", paper.season()));
                onClose();
            }).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.show.tip"))).build());
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.edit"), b -> TaskClient.requestEditor(paper.mancheId()))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.edit.tip"))).build());
        }
        if (paper.has(TaskNetwork.CAN_EDIT) && "note".equals(paper.kind()))
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.note.edit"), b -> TaskClient.noteOrder(paper.mancheId(), "edit")).build());
        if (!buttons.isEmpty()) {
            int bw = 104, gap = 6;
            int total = buttons.size() * bw + (buttons.size() - 1) * gap;
            int x = (width - total) / 2, y = Math.min(height - 24, cardY + cardH + 8);
            for (Button b : buttons) {
                b.setRectangle(bw, 20, x, y);
                addRenderableWidget(b);
                x += bw + gap;
            }
        }
    }

    private boolean hasButtons() {
        return !secret && (paper.has(TaskNetwork.CAN_EDIT) || paper.has(TaskNetwork.CAN_REVEAL));
    }

    private void actions(Map<String, Object> action) {
        TaskClient.runActions(List.of(action));
    }

    private int maxScroll() {
        return view == null ? 0 : Math.max(0, view.contentHeight() - cardH);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (float) sy * 18, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (animating()) {
            skip();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (animating() && (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN)) {
            skip();
            return true;
        }
        if (TaskClient.TASK_KEY.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_PAGEDOWN) {
            scroll = Mth.clamp(scroll + (event.key() == InputConstants.KEY_DOWN ? 18 : cardH - 20), 0, maxScroll());
            return true;
        }
        if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_PAGEUP) {
            scroll = Mth.clamp(scroll - (event.key() == InputConstants.KEY_UP ? 18 : cardH - 20), 0, maxScroll());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sound(int index, float at, float t, SoundEvent sound, float pitch) {
        if (played[index] || t < at) return;
        played[index] = true;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.9f));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // A warm spotlight on the reading area, the rest of the room darker
        g.fillGradient(0, 0, width, height / 2, 0x60000000, 0x20100800);
        g.fillGradient(0, height / 2, width, height, 0x20100800, 0x70000000);
        float t = ticks(partial);
        if (secret) drawSecret(g, t);
        else if (envelope && t < T_TEXT) drawOpening(g, t);
        else drawCard(g, t);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    /** Reading: the card, its text fading in after the opening, a scroll bar when it is long. */
    private void drawCard(GuiGraphicsExtractor g, float t) {
        int textAlpha = 255;
        int x = cardX, y = cardY, w = cardW, h = cardH;
        if (envelope) textAlpha = (int) Mth.clamp((t - T_UNFOLD) / (T_TEXT - T_UNFOLD) * 255, 0, 255);
        if (envelope && t < T_TEXT + 8) textAlpha = (int) Mth.clamp((t - T_UNFOLD) / 8f * 255, 0, 255);
        if (found && t < F_UNFOLD) {
            // A found note: rises from the bottom and unfolds
            float p = ease(t / F_UNFOLD);
            int fh = Math.max(4, (int) (h * p));
            y = y + (int) ((1 - p) * (height - y)) / 2;
            sound(0, 0, t, SoundEvents.BOOK_PAGE_TURN, 1.2f);
            view.drawSheet(g, x, y + (h - fh) / 2, w, fh, (int) (255 * Math.min(1, p * 2)));
            return;
        }
        glow(g, x, y, w, h, envelope ? Mth.clamp(1 - (t - T_TEXT) / 30f, 0, 1) : 0);
        view.draw(g, x, y, w, h, (int) scroll, 255, textAlpha);
        if (maxScroll() > 0) {
            int track = h - 8;
            int thumb = Math.max(14, track * h / view.contentHeight());
            int ty = y + 4 + (int) ((track - thumb) * (scroll / maxScroll()));
            g.fill(x + w - 5, y + 4, x + w - 3, y + 4 + track, 0x30000000);
            g.fill(x + w - 5, ty, x + w - 3, ty + thumb, 0xA0B08A3A);
            if (scroll < maxScroll() - 2) g.text(font, Component.literal("▼"), x + w - 14, y + h - 11, 0xC0B08A3A, false);
        }
    }

    /** A golden halo behind the card right after it comes out of the envelope. */
    private void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, float strength) {
        if (strength <= 0) return;
        for (int i = 6; i >= 1; i--) {
            int a = (int) (strength * 22 * (7 - i) / 6f);
            g.fill(x - i * 4, y - i * 4, x + w + i * 4, y + h + i * 4, a << 24 | 0xFFC23D);
        }
    }

    /** Envelope size on screen: 128 x 80 texture drawn at up to 1.5x. */
    private int envW() {
        return Math.min(192, (int) (width * 0.5f)) / 2 * 2;
    }

    /** The sealed envelope of a secret task, gently floating, with its label. */
    private void drawSecret(GuiGraphicsExtractor g, float t) {
        int ew = envW(), eh = ew * 80 / 128;
        int ex = (width - ew) / 2, ey = (height - eh) / 2 - 16 + (int) (Math.sin(t / 12f) * 2);
        drawEnvelope(g, ex, ey, ew, eh, 0, 255, true, 0, false);
        Component title = Component.translatable("paper.craftmaster.secret").withStyle(ChatFormatting.BOLD);
        int tw = font.width(title) + 24;
        int ly = ey + eh + 14;
        g.fill((width - tw) / 2, ly - 5, (width + tw) / 2, ly + 13, 0xE0581018);
        g.outline((width - tw) / 2, ly - 5, tw, 18, 0xFFFFC23D);
        g.centeredText(font, title, width / 2, ly, 0xFFFFE6A0);
        g.centeredText(font, paper.header(), width / 2, ey - 16, 0xFFFFE6A0);
        g.centeredText(font, Component.translatable("paper.craftmaster.secret_hint"), width / 2, ly + 20, 0xFFBFB6A0);
    }

    /** The opening, from the envelope's arrival to the unfolded card. */
    private void drawOpening(GuiGraphicsExtractor g, float t) {
        String wax = paper.wax();
        int ew = envW(), eh = ew * 80 / 128;
        int ex = (width - ew) / 2, ey0 = (height - eh) / 2 + eh / 6;
        // 1. Arrival: from below, with a slight tilt, easing out
        float arrive = ease(Mth.clamp(t / T_ARRIVE, 0, 1));
        int ey = ey0 + (int) ((1 - arrive) * (height - ey0 + 10));
        float tilt = (1 - arrive) * -0.25f;
        sound(0, 0, t, SoundEvents.BOOK_PAGE_TURN, 0.7f);
        // 2. Wax: trembles, cracks, the halves fall
        sound(1, T_CRACK, t, SoundEvents.AXE_WAX_OFF.value(), 0.8f);
        sound(2, T_SPLIT, t, SoundEvents.HONEYCOMB_WAX_ON, 0.6f);
        // 3. Flap opens (0..180 degrees), 4. card slides out, 5. card unfolds toward the reader
        float flap = Mth.clamp((t - T_SPLIT - 1) / (T_FLAP - T_SPLIT - 1), 0, 1);
        sound(3, T_SPLIT + 2, t, SoundEvents.BOOK_PAGE_TURN, 0.9f);
        float out = ease(Mth.clamp((t - T_FLAP) / (T_CARD - T_FLAP), 0, 1));
        sound(4, T_FLAP + 2, t, SoundEvents.BOOK_PUT, 1.1f);
        float unfold = ease(Mth.clamp((t - T_CARD) / (T_UNFOLD - T_CARD), 0, 1));
        sound(5, T_CARD + 1, t, SoundEvents.BOOK_PAGE_TURN, 1.3f);
        // The envelope sinks and fades while the card comes forward
        int envAlpha = (int) (255 * (1 - unfold));
        ey += (int) (unfold * eh * 1.2f);

        g.pose().pushMatrix();
        g.pose().translate(ex + ew / 2f, ey + eh / 2f);
        g.pose().rotate(tilt);
        g.pose().translate(-ew / 2f, -eh / 2f);
        int lx = 0, ly = 0;
        // Card (folded, inside the envelope) then the pocket in front of it
        int cw0 = (int) (ew * 0.86f), ch0 = (int) (eh * 0.78f);
        int cardTopIn = ly + 6, cardTopOut = ly - (int) (ch0 * 0.8f);
        int cy = (int) Mth.lerp(out, cardTopIn, cardTopOut);
        if (envAlpha > 0) {
            drawEnvelopeBack(g, lx, ly, ew, eh, flap, envAlpha);
            if (unfold <= 0) view.drawSheet(g, lx + (ew - cw0) / 2, cy, cw0, ch0, 255);
            drawEnvelopeFront(g, lx, ly, ew, eh, flap, envAlpha, wax, t);
        }
        g.pose().popMatrix();
        // Crumbs of wax flying off when it cracks
        drawCrumbs(g, t, ex + ew / 2, ey + (int) (eh * 48 / 80 * 0.86f), wax);
        if (unfold > 0) {
            // From the folded card above the envelope to the reading card in the middle
            int sx = ex + (ew - cw0) / 2, sy = ey + cy, sw = cw0, sh = ch0;
            int x = (int) Mth.lerp(unfold, sx, cardX), y = (int) Mth.lerp(unfold, sy, cardY);
            int w = (int) Mth.lerp(unfold, sw, cardW), h = (int) Mth.lerp(unfold, sh / 2f, cardH);
            // Folded in half: the lower half opens like a page
            int top = h / 2;
            int bottom = (int) (h / 2f * unfold);
            glow(g, x, y, w, top + bottom, unfold);
            view.drawSheet(g, x, y, w, top + bottom, 255);
            g.fill(x + 2, y + top - 1, x + w - 2, y + top, (int) (60 * (1 - unfold)) << 24);
        }
    }

    private void drawEnvelope(GuiGraphicsExtractor g, int x, int y, int w, int h, float flap, int alpha, boolean sealed, float t, boolean crumbs) {
        drawEnvelopeBack(g, x, y, w, h, flap, alpha);
        drawEnvelopeFront(g, x, y, w, h, flap, alpha, paper.wax(), sealed ? 0 : t);
    }

    /** Inside of the envelope, and the flap when it is open (behind the card). */
    private void drawEnvelopeBack(GuiGraphicsExtractor g, int x, int y, int w, int h, float flap, int alpha) {
        // Soft shadow
        g.fill(x + 4, y + 5, x + w + 4, y + h + 5, (alpha / 4) << 24);
        g.blit(RenderPipelines.GUI_TEXTURED, INNER, x, y, 0, 0, w, h, 128, 80, 128, 80, alpha << 24 | 0xFFFFFF);
        float cos = (float) Math.cos(flap * Math.PI);
        if (cos < 0) drawFlap(g, x, y, w, h, cos, alpha);
    }

    /** Pocket (front folds), the flap while it is closed or opening, the seal. */
    private void drawEnvelopeFront(GuiGraphicsExtractor g, int x, int y, int w, int h, float flap, int alpha, String wax, float t) {
        g.blit(RenderPipelines.GUI_TEXTURED, POCKET, x, y, 0, 0, w, h, 128, 80, 128, 80, alpha << 24 | 0xFFFFFF);
        float cos = (float) Math.cos(flap * Math.PI);
        if (cos >= 0) drawFlap(g, x, y, w, h, cos, alpha);
        // The seal sits on the tip of the closed flap
        int fh = h * 48 / 80;
        int sx = x + w / 2, sy = y + (int) (fh * 0.86f);
        int size = w * 30 / 128;
        if (t < T_SPLIT) {
            int jx = 0, jy = 0;
            if (t > T_ARRIVE + 2) {
                // Trembles harder and harder until it cracks
                float k = Mth.clamp((t - T_ARRIVE - 2) / (T_CRACK - T_ARRIVE - 2), 0, 1);
                jx = (int) (Math.sin(t * 9.1) * 1.6 * k);
                jy = (int) (Math.cos(t * 7.3) * 1.2 * k);
            }
            PaperView.drawSeal(g, sx + jx, sy + jy, size, wax, alpha);
            if (t >= T_CRACK) {
                // The crack runs down the middle
                int c = 0xC0000000 | (darker(WaxColor.of(wax).rgb) & 0xFFFFFF);
                int len = (int) (size * Mth.clamp((t - T_CRACK) / 3f, 0, 1) * 0.8f);
                for (int i = 0; i < len; i += 2)
                    g.fill(sx + jx + ((i / 2) % 2 == 0 ? 0 : 1) - 1, sy + jy - size * 2 / 5 + i, sx + jx + ((i / 2) % 2 == 0 ? 1 : 2), sy + jy - size * 2 / 5 + i + 2, c);
            }
        } else if (t < T_SPLIT + 16) {
            // The two halves fall apart, spinning
            float f = (t - T_SPLIT) / 16f;
            int dy = (int) (f * f * h * 1.4f), dx = (int) (f * size * 0.9f);
            int a = (int) (alpha * Mth.clamp(1.2f - f, 0, 1));
            int rgb = WaxColor.of(wax).rgb;
            g.pose().pushMatrix();
            g.pose().translate(sx - dx, sy + dy);
            g.pose().rotate(-f * 1.2f);
            g.blit(RenderPipelines.GUI_TEXTURED, WAX_L, -size / 2, -size / 2, 0, 0, size, size, 64, 64, 64, 64, a << 24 | rgb);
            g.pose().popMatrix();
            g.pose().pushMatrix();
            g.pose().translate(sx + dx, sy + dy);
            g.pose().rotate(f * 1.4f);
            g.blit(RenderPipelines.GUI_TEXTURED, WAX_R, -size / 2, -size / 2, 0, 0, size, size, 64, 64, 64, 64, a << 24 | rgb);
            g.pose().popMatrix();
        }
    }

    /**
     * The flap folded down ({@code cos} 1), standing up showing its lining ({@code cos} -1), or in between. The
     * GUI culls mirrored quads, so the standing flap uses its own upside-down texture instead of a negative scale.
     */
    private void drawFlap(GuiGraphicsExtractor g, int x, int y, int w, int h, float cos, int alpha) {
        int fh = h * 48 / 80;
        float k = Math.max(0.02f, Math.abs(cos));
        g.pose().pushMatrix();
        g.pose().translate(x, cos >= 0 ? y : y - fh * k);
        g.pose().scale(1, k);
        g.blit(RenderPipelines.GUI_TEXTURED, cos >= 0 ? FLAP : FLAP_IN, 0, 0, 0, 0, w, fh, 128, 48, 128, 48, alpha << 24 | 0xFFFFFF);
        g.pose().popMatrix();
    }

    private void drawCrumbs(GuiGraphicsExtractor g, float t, int cx, int cy, String wax) {
        if (t < T_SPLIT || t > T_SPLIT + 20) return;
        if (crumbs.isEmpty()) {
            Random r = new Random(paper.title().hashCode());
            for (int i = 0; i < 14; i++)
                crumbs.add(new Crumb(0, 0, (r.nextFloat() - 0.5f) * 5f, -r.nextFloat() * 3.5f - 0.5f, 1 + r.nextInt(2)));
        }
        float f = t - T_SPLIT;
        int rgb = WaxColor.of(wax).rgb;
        int a = (int) (255 * Mth.clamp(1 - f / 20f, 0, 1));
        for (Crumb c : crumbs) {
            int x = cx + (int) (c.vx() * f * 2), y = cy + (int) (c.vy() * f * 2 + 0.35f * f * f);
            g.fill(x, y, x + c.size() + 1, y + c.size() + 1, a << 24 | rgb);
        }
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) / 2, gg = (rgb >> 8 & 255) / 2, b = (rgb & 255) / 2;
        return r << 16 | gg << 8 | b;
    }

    static float ease(float x) {
        float c = Mth.clamp(x, 0, 1);
        return 1 - (1 - c) * (1 - c) * (1 - c);
    }
}
