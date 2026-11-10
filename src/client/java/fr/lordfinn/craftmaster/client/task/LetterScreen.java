package fr.lordfinn.craftmaster.client.task;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import fr.lordfinn.craftmaster.client.DevInput;
import fr.lordfinn.craftmaster.client.broadcast.Broadcast;
import fr.lordfinn.craftmaster.task.LetterMotion;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The task letter (a task or a free text): a cream sheet folded in three, sealed with wax across the edge of its
 * top panel. The reader grabs the seal and pulls it off (it follows the mouse, resists, trembles and pops with a
 * crack and bits of wax; a click or a held Space / Enter pulls it by itself), then the top panel flips up and the
 * bottom one down (then the extra panels of a long text, one after the other) while the view zooms out to show the
 * whole width and the first panels. Drawn in letter pixels at a whole number of screen pixels each, nearest
 * filtering, like a vanilla book: crisp pixel art and text.
 *
 * <p>Re-read (no seal): the letter unfolds by itself. A secret task: the sealed letter, its seal will not give.
 * Long letters scroll (wheel, drag, arrows, Page Up / Down, Home / End); the broadcast game scrolls by itself.
 * Click, Space or Enter skips the unfolding; Escape or J closes.
 */
public class LetterScreen extends Screen {

    private final Screen parent;
    private TaskNetwork.Paper paper;
    private final boolean secret, sealed, preview;
    private LetterView view;
    private final LetterMotion.Seal seal = new LetterMotion.Seal();
    /** Animation clock (seconds, slowed by the dev "slowmo") and the time the seal popped (NaN: not yet). */
    private float clock, popAt = Float.NaN, lastInput;
    private long lastFrame = System.nanoTime();
    private boolean draggingPaper, sentBroken;
    private float scroll, scrollTarget, autoPause;
    private int autoDir = 1;
    private final boolean[] played = new boolean[16];
    private final List<Button> buttons = new ArrayList<>();
    private record Bit(float x, float y, float vx, float vy, int size, int shade) {}
    private final List<Bit> bits = new ArrayList<>();
    /** Current view: screen pixels per letter pixel, origin (screen pixels), GUI scale. */
    private float scale = 1, originX, originY;
    private int gui = 1;

    public LetterScreen(TaskNetwork.Paper paper, Screen parent) {
        super(Component.translatable("screen.craftmaster.paper"));
        this.paper = paper;
        this.parent = parent;
        this.secret = paper.has(TaskNetwork.SECRET);
        this.sealed = secret || paper.has(TaskNetwork.ENVELOPE);
        this.preview = paper.has(TaskNetwork.PREVIEW);
        seal.lock(secret);
        if (!sealed) popAt = 0;   // re-read: unfolds by itself
    }

    public TaskNetwork.Paper paper() {
        return paper;
    }

    /** A [secret] part was revealed while the letter is open: the new text, in place. */
    public void update(TaskNetwork.PaperUpdate u) {
        if (!"task".equals(paper.kind()) || u.season() != paper.season() || !u.mancheId().equals(paper.mancheId())) return;
        paper = new TaskNetwork.Paper(paper.kind(), paper.header(), paper.title(), u.text(), u.rules(), paper.seconds(), paper.wax(), paper.look(),
                paper.flags(), paper.pieces(), paper.mancheId(), paper.season(), paper.from());
        view.layout(paper, vars());
    }

    private Map<String, String> vars() {
        Map<String, String> v = new HashMap<>();
        String reader = !paper.from().isEmpty() ? paper.from() : minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().name() : "";
        v.put("player", reader);
        v.put("team", reader);
        v.put("time", paper.seconds() > 0 ? PaperView.clock(paper.seconds()) : "");
        return v;
    }

    @Override
    protected void init() {
        if (view == null) view = new LetterView(font, paper);
        view.layout(paper, vars());
        buttons.forEach(this::removeWidget);
        buttons.clear();
        if (secret) return;
        if (!preview && paper.has(TaskNetwork.CAN_REVEAL) && "task".equals(paper.kind()))
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.reveal"), b -> {
                TaskClient.runActions(List.of(Map.of("type", "task.reveal", "manche", paper.mancheId(), "season", paper.season())));
                onClose();
            }).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.reveal.tip"))).build());
        if (paper.has(TaskNetwork.CAN_EDIT) && "task".equals(paper.kind())) {
            if (!preview) buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.show"), b -> {
                TaskClient.runActions(List.of(Map.of("type", "task.show", "manche", paper.mancheId(), "season", paper.season())));
                onClose();
            }).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.show.tip"))).build());
            buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.edit"), b -> TaskClient.requestEditor(paper.mancheId()))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.edit.tip"))).build());
        }
        int bw = 104, gap = 6;
        int total = buttons.size() * bw + (buttons.size() - 1) * gap;
        int x = (width - total) / 2;
        for (Button b : buttons) {
            b.setRectangle(bw, 20, x, height - 26);
            b.visible = false;
            addRenderableWidget(b);
            x += bw + gap;
        }
    }

    // ---------------------------------------------------------------- state

    private boolean popped() {
        return !Float.isNaN(popAt);
    }

    /** Seconds since the seal popped (0 before). */
    private float since() {
        return popped() ? clock - popAt : 0;
    }

    private boolean unfolding() {
        return popped() && since() < LetterMotion.end(view.panels());
    }

    private boolean open() {
        return popped() && !unfolding();
    }

    private void skip() {
        popAt = clock - LetterMotion.end(view.panels()) - 1;
        for (int i = 0; i < played.length; i++) played[i] = true;
    }

    private void popNow(float dirX, float dirY) {
        popAt = clock;
        play(SoundEvents.AXE_WAX_OFF.value(), 1.35f, 1f);
        play(SoundEvents.HONEYCOMB_WAX_ON, 0.75f, 0.8f);
        Random r = new Random(paper.title().hashCode() * 31L + paper.mancheId().hashCode());
        float sx = LetterMotion.SEAL_X + seal.offsetX(), sy = LetterMotion.SEAL_Y + seal.offsetY();
        for (int i = 0; i < 18; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            float rad = 6 + r.nextFloat() * 14;
            float sp = 40 + r.nextFloat() * 120;
            bits.add(new Bit(sx + (float) Math.cos(a) * rad, sy + (float) Math.sin(a) * rad,
                    (float) Math.cos(a) * sp + dirX * 0.3f, (float) Math.sin(a) * sp - 60 + dirY * 0.3f, 1 + r.nextInt(3), r.nextInt(3)));
        }
        // The reveal and the announce wait for this (an organiser's preview reveals nothing)
        if (paper.has(TaskNetwork.ENVELOPE) && !preview && !sentBroken && ClientPlayNetworking.canSend(TaskNetwork.SealBroken.TYPE)) {
            ClientPlayNetworking.send(new TaskNetwork.SealBroken());
            sentBroken = true;
        }
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void once(int i, boolean when, SoundEvent sound, float pitch) {
        if (played[i] || !when) return;
        played[i] = true;
        play(sound, pitch, 0.9f);
    }

    // ---------------------------------------------------------------- view

    private void updateView(float zoom) {
        Window w = minecraft.getWindow();
        gui = Math.max(1, w.getGuiScale());
        // Closed: room for the hint under the letter; open: for the organisers' buttons
        int sw = w.getWidth(), shOpen = w.getHeight() - (buttons.isEmpty() ? 0 : 30 * gui), shClosed = w.getHeight() - 44 * gui;
        int p0 = LetterMotion.closedScale(sw, shClosed), p1 = LetterMotion.openScale(sw, shOpen, view.panels());
        scale = Mth.lerp(zoom, p0, p1);
        float cy0 = LetterMotion.TOP + LetterMotion.PANEL / 2f + 4;
        float viewH = shOpen / (float) p1;
        int h = view.height();
        float cy1 = h + 16 <= viewH ? h / 2f : viewH / 2f - 10;
        float cy = Mth.lerp(zoom, cy0, cy1) + scroll * zoom;
        originX = sw / 2f - LetterMotion.WIDTH / 2f * scale;
        originY = Mth.lerp(zoom, shClosed / 2f, shOpen / 2f) - cy * scale;
        if (zoom <= 0 || zoom >= 1) {
            originX = Math.round(originX);
            originY = Math.round(originY);
        }
    }

    private float maxScroll() {
        Window w = minecraft.getWindow();
        int sh = w.getHeight() - (buttons.isEmpty() ? 0 : 30 * gui);
        int p1 = LetterMotion.openScale(w.getWidth(), sh, view.panels());
        return LetterMotion.maxScroll(view.height(), sh / (float) p1);
    }

    private float letterX(double guiX) {
        return (float) ((guiX * gui - originX) / scale);
    }

    private float letterY(double guiY) {
        return (float) ((guiY * gui - originY) / scale);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        lastInput = clock;
        if (open() && super.mouseClicked(e, doubleClick)) return true;
        if (!popped()) {
            float x = letterX(e.x()) - LetterMotion.SEAL_X, y = letterY(e.y()) - LetterMotion.SEAL_Y;
            if (LetterMotion.Seal.hit(x, y)) {
                seal.grab(x, y);
                play(SoundEvents.HONEYCOMB_WAX_ON, 1.6f, 0.35f);
            }
            return true;
        }
        if (unfolding()) {
            skip();
            return true;
        }
        draggingPaper = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        lastInput = clock;
        if (seal.grabbed()) {
            if (seal.drag(letterX(e.x()) - LetterMotion.SEAL_X, letterY(e.y()) - LetterMotion.SEAL_Y))
                popNow(seal.offsetX() * 20, seal.offsetY() * 20);
            return true;
        }
        if (draggingPaper && open()) {
            scrollTarget = Mth.clamp(scrollTarget - (float) (dy * gui / scale), 0, maxScroll());
            scroll = scrollTarget;   // the paper follows the hand exactly
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        seal.release();
        draggingPaper = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        lastInput = clock;
        if (open()) scrollTarget = Mth.clamp(scrollTarget - (float) sy * 24, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        lastInput = clock;
        int k = e.key();
        if (unfolding() && (k == InputConstants.KEY_SPACE || k == InputConstants.KEY_RETURN)) {
            skip();
            return true;
        }
        if (TaskClient.TASK_KEY.matches(e)) {
            onClose();
            return true;
        }
        if (open()) {
            float page = LetterMotion.PANEL;
            Float to = switch (k) {
                case InputConstants.KEY_DOWN -> scrollTarget + 24;
                case InputConstants.KEY_UP -> scrollTarget - 24;
                case InputConstants.KEY_PAGEDOWN -> scrollTarget + page;
                case InputConstants.KEY_PAGEUP -> scrollTarget - page;
                case InputConstants.KEY_HOME -> 0f;
                case InputConstants.KEY_END -> maxScroll();
                default -> null;
            };
            if (to != null) {
                scrollTarget = Mth.clamp(to, 0, maxScroll());
                return true;
            }
        }
        return super.keyPressed(e);
    }

    private boolean pullKeyHeld() {
        return InputConstants.isKeyDown(InputConstants.KEY_SPACE) || InputConstants.isKeyDown(InputConstants.KEY_RETURN)
                || DevInput.held(InputConstants.KEY_SPACE) || DevInput.held(InputConstants.KEY_RETURN);
    }

    @Override
    public void onClose() {
        // Closed before tearing it: the server still reveals it (after its delay); nothing more to say here
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- frame

    private void tick(float dt) {
        clock += dt;
        if (!popped()) {
            if (seal.update(dt, pullKeyHeld())) popNow(0, -60);
        } else {
            float t = since();
            once(0, t >= 0.05f && sealed, SoundEvents.BOOK_PAGE_TURN, 0.85f);
            once(1, t >= 0.55f, SoundEvents.BOOK_PAGE_TURN, 1.05f);
            for (int p = 3; p < Math.min(view.panels(), played.length - 2); p++)
                once(p, t >= LetterMotion.end(p), SoundEvents.BOOK_PAGE_TURN, 1.1f + 0.05f * p);
        }
        // Scrolling: eased toward its target; the broadcast scrolls slowly by itself when nobody touches it
        float max = maxScroll();
        if (open() && max > 0 && Broadcast.active() && clock - lastInput > 3) {
            if (autoPause > 0) autoPause -= dt;
            else {
                scrollTarget = Mth.clamp(scrollTarget + autoDir * 9 * dt, 0, max);
                if (scrollTarget >= max || scrollTarget <= 0) {
                    autoDir = -autoDir;
                    autoPause = 4;
                }
            }
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, max);
        if (!draggingPaper) scroll += (scrollTarget - scroll) * (1 - (float) Math.exp(-dt * 12));
        if (Math.abs(scroll - scrollTarget) < 0.05f) scroll = scrollTarget;
        // Whole screen pixels, so the text never sits between two pixels
        scroll = Math.round(scroll * scale) / Math.max(1f, scale);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // The room dims around the letter (no blur: the paper stays sharp)
        g.fillGradient(0, 0, width, height / 2, 0xA0100808, 0x80100808);
        g.fillGradient(0, height / 2, width, height, 0x80100808, 0xB0100808);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1e9f) * DevInput.timeScale();
        lastFrame = now;
        tick(dt);
        float t = since();
        float zoom = popped() ? LetterMotion.zoom(t) : 0;
        updateView(zoom);
        int rgb = WaxColor.of(paper.wax()).rgb;

        g.pose().pushMatrix();
        g.pose().translate(originX / gui, originY / gui);
        g.pose().scale(scale / gui, scale / gui);
        drawLetter(g, t, rgb);
        g.pose().popMatrix();

        drawOverlay(g, mouseX, mouseY);
        for (Button b : buttons) b.visible = open();
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private float openOf(int panel, float t) {
        if (!popped()) return panel == 1 ? 1 : panel == 0 ? seal.tension() * 0.05f : 0;
        return LetterMotion.open(panel, t);
    }

    private void drawLetter(GuiGraphicsExtractor g, float t, int rgb) {
        int n = view.panels();
        LetterMotion.Projection[] proj = new LetterMotion.Projection[n];
        boolean[] drawn = new boolean[n];
        for (int k = 0; k < n; k++) {
            proj[k] = LetterMotion.project(k, openOf(k, t));
            // An extra panel stays folded inside the one before until that one is flat
            drawn[k] = k < 3 || openOf(k - 1, t) >= 1;
        }
        // Shadow under the sheet
        for (int k = 0; k < n; k++)
            if (drawn[k]) fillF(g, 3, proj[k].y0() + 3, LetterMotion.WIDTH + 3, proj[k].y1() + 3, 0x50000000);
        // The middle panel, the bottom chain, the top panel last (outermost when folded)
        for (int k = 1; k < n; k++) if (drawn[k]) drawPanel(g, k, proj[k], t, n, rgb);
        drawCreases(g, t, n);
        drawPanel(g, 0, proj[0], t, n, rgb);
        // The seal on the edge of the top panel, pulled; once popped: flying off, bits of wax
        if (!popped() && sealed) {
            float k = seal.tension();
            float jx = (float) Math.sin(clock * 53) * 0.9f * k, jy = (float) Math.cos(clock * 41) * 0.7f * k;
            float cx = LetterMotion.SEAL_X + seal.offsetX() + jx, cy = proj[0].y1() + seal.offsetY() + jy;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            float lift = 1 + 0.06f * k;    // lifted toward the reader as it gives way
            g.pose().scale(lift, lift);
            fillF(g, -20, 3, 22, 23, (int) (0x40 * (1 - k * 0.5f)) << 24);
            LetterView.seal(g, LetterView.SEAL, 0, 0, rgb, 255);
            g.pose().popMatrix();
        } else if (popped() && sealed && t < 1.0f) {
            float[] f = seal.flight(t);
            if (f[3] > 0) {
                g.pose().pushMatrix();
                g.pose().translate(LetterMotion.SEAL_X + f[0], LetterMotion.SEAL_Y + f[1]);
                g.pose().rotate(f[2]);
                g.pose().scale(1.08f, 1.08f);
                LetterView.seal(g, LetterView.SEAL, 0, 0, rgb, (int) (255 * f[3]));
                g.pose().popMatrix();
            }
        }
        if (popped() && sealed && t < 1.2f) {
            int a = (int) (255 * LetterMotion.clamp(1 - (t - 0.6f) / 0.6f));
            for (Bit b : bits) {
                int x = Math.round(b.x() + b.vx() * t), y = Math.round(b.y() + b.vy() * t + 0.5f * 700 * t * t);
                int c = b.shade() == 0 ? darker(rgb, 0.55f) : b.shade() == 1 ? darker(rgb, 0.8f) : rgb;
                g.fill(x, y, x + b.size(), y + b.size(), a << 24 | c);
            }
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, int k, LetterMotion.Projection p, float t, int n, int rgb) {
        float h = LetterMotion.panelHeight(k);
        float drawnH = p.y1() - p.y0();
        if (drawnH < 0.05f) return;
        int top = LetterMotion.panelTop(k);
        g.pose().pushMatrix();
        g.pose().translate(0, p.y0());
        g.pose().scale(1, drawnH / h);
        // A folded extra panel lies on this one's front: we see its outside
        boolean covered = p.front() && k >= 2 && k + 1 < n && openOf(k + 1, t) <= 0;
        if (p.front() && !covered) {
            g.pose().translate(0, -top);
            g.enableScissor(0, top, LetterMotion.WIDTH, top + (int) h);
            LetterView.paper(g, LetterView.PAPER, top, top, top + (int) h, 0xFFFFFFFF);
            edges(g, top, (int) h);
            view.drawContent(g, top, top + (int) h);
            // What is left of the wax, at the top edge of the open sheet
            if (k == 0 && sealed) LetterView.seal(g, LetterView.RESIDUE, LetterMotion.SEAL_X, 0, rgb, 255);
            g.disableScissor();
        } else {
            LetterView.paper(g, LetterView.BACK, 0, top, top + (int) h, 0xFFFFFFFF);
            edges(g, 0, (int) h);
            if (k == 0) {
                view.drawOutside(g);
                // The torn seal leaves its mark across the edge
                if (popped() && sealed) LetterView.seal(g, LetterView.RESIDUE, LetterMotion.SEAL_X, (int) h, rgb, 255);
            }
            if (k == 2 && sealed && popped() && !p.front()) {
                // The lower half of the seal's mark stays on the bottom panel's outside
                g.enableScissor(0, LetterMotion.SEAL_Y - LetterMotion.TOP, LetterMotion.WIDTH, (int) h);
                LetterView.seal(g, LetterView.RESIDUE, LetterMotion.SEAL_X, LetterMotion.SEAL_Y - LetterMotion.TOP, rgb, 255);
                g.disableScissor();
            }
        }
        g.pose().popMatrix();
        // Turning away from the light while it moves
        if (p.light() < 0.999f) fillF(g, 0, p.y0(), LetterMotion.WIDTH, p.y1(), (int) ((1 - p.light()) * (p.front() ? 110 : 80)) << 24);
    }

    /** Edges of a sheet: a slightly darker outline, so the cream reads against anything. */
    private static void edges(GuiGraphicsExtractor g, int y, int h) {
        g.fill(0, y, 1, y + h, 0x30302010);
        g.fill(LetterMotion.WIDTH - 1, y, LetterMotion.WIDTH, y + h, 0x40302010);
    }

    /** The folds of the flat sheet: a dark crease, its lit lip, a soft stepped shade on both sides. */
    private void drawCreases(GuiGraphicsExtractor g, float t, int n) {
        for (int k = 1; k < n; k++) {
            int below = k + 1;
            boolean topFold = k == 1;
            float a = topFold ? openOf(0, t) : below < n ? openOf(below, t) : 0;
            if (!topFold && below >= n) continue;
            if (a < 0.98f || openOf(k, t) < 0.98f) continue;
            int y = topFold ? LetterMotion.TOP : LetterMotion.panelTop(below);
            g.fill(1, y - 3, LetterMotion.WIDTH - 1, y - 2, 0x0A000000);
            g.fill(1, y - 2, LetterMotion.WIDTH - 1, y - 1, 0x12000000);
            g.fill(1, y - 1, LetterMotion.WIDTH - 1, y, 0x22000000);
            g.fill(1, y, LetterMotion.WIDTH - 1, y + 1, 0x3A40301A);
            g.fill(1, y + 1, LetterMotion.WIDTH - 1, y + 2, 0x50FFFFFF);
            g.fill(1, y + 2, LetterMotion.WIDTH - 1, y + 4, 0x0C000000);
        }
    }

    /** A rectangle with fractional top / bottom (letter pixels). */
    private static void fillF(GuiGraphicsExtractor g, int x0, float y0, int x1, float y1, int argb) {
        if (y1 - y0 <= 0.01f) return;
        g.pose().pushMatrix();
        g.pose().translate(0, y0);
        g.pose().scale(1, y1 - y0);
        g.fill(x0, 0, x1, 1, argb);
        g.pose().popMatrix();
    }

    private static int darker(int rgb, float k) {
        return (int) ((rgb >> 16 & 255) * k) << 16 | (int) ((rgb >> 8 & 255) * k) << 8 | (int) ((rgb & 255) * k);
    }

    /** GUI text around the letter: what to do, the scroll hint, the preview ribbon. */
    private void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        float bottomGui = (originY + (LetterMotion.TOP + LetterMotion.PANEL) * scale) / gui;
        if (secret) {
            Component title = Component.translatable("paper.craftmaster.secret").withStyle(ChatFormatting.BOLD);
            int tw = font.width(title) + 24, ly = Math.min(height - 34, (int) bottomGui + 14);
            g.fill((width - tw) / 2, ly - 5, (width + tw) / 2, ly + 13, 0xE0581018);
            g.outline((width - tw) / 2, ly - 5, tw, 18, 0xFFFFC23D);
            g.centeredText(font, title, width / 2, ly, 0xFFFFE6A0);
            g.centeredText(font, Component.translatable("paper.craftmaster.secret_hint"), width / 2, ly + 20, 0xFFBFB6A0);
        } else if (!popped()) {
            int ly = Math.min(height - 22, (int) bottomGui + 12);
            float pulse = 0.65f + 0.35f * (float) Math.sin(clock * 3);
            g.centeredText(font, Component.translatable("paper.craftmaster.tear"), width / 2, ly, (int) (255 * pulse) << 24 | 0xFFE6A0);
            g.centeredText(font, Component.translatable("paper.craftmaster.tear_hint"), width / 2, ly + 12, 0xC0BFB6A0);
        }
        if (open() && scroll < maxScroll() - 1) {
            // The paper goes on below: a small bobbing arrow
            int y = height - (buttons.isEmpty() ? 14 : 40) + (int) (Math.sin(clock * 4) * 2);
            g.centeredText(font, Component.literal("▼"), width / 2, y, 0xD0FFE6A0);
        }
        if (preview) {
            Component c = Component.translatable("paper.craftmaster.preview_ribbon");
            int w = font.width(c) + 16;
            g.fill((width - w) / 2, 4, (width + w) / 2, 18, 0xE0303A58);
            g.centeredText(font, c, width / 2, 7, 0xFFE8ECFF);
        }
    }
}
