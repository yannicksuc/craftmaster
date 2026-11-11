package fr.lordfinn.craftmaster.client.studio;

import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.client.ui.UiSounds;
import fr.lordfinn.craftmaster.client.ui.UiTabs;
import fr.lordfinn.craftmaster.studio.SevenSegment;
import fr.lordfinn.craftmaster.studio.StudioNetwork;
import fr.lordfinn.craftmaster.studio.TabletClock;
import fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings of a timer tablet (organisers, right click): a live preview with start / pause / reset, and four tabs —
 * what it counts (mode, duration, format, warning), how it looks (colours, frame, passe-partout, size, text), what
 * drives it (players' click, redstone, rounds, end sound and signal, group) and where it stands (pose, tilt). Every
 * change is sent at once and seen on the tablet in the world.
 */
public class TabletScreen extends Screen {

    private final TimerTabletBlockEntity tablet;
    private TabletClock.Settings s;
    private int tab;
    private int left, top, w, h;

    public TabletScreen(TimerTabletBlockEntity tablet) {
        super(t("title"));
        this.tablet = tablet;
        this.s = tablet.settings().copy();
        // The block's pose is the truth (a tablet placed by a command or a structure)
        this.s.pose = tablet.getBlockState().getValue(fr.lordfinn.craftmaster.studio.TimerTabletBlock.POSE);
    }

    static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.tablet." + key, args);
    }

    private static <E extends Enum<E>> Component value(E e) {
        return Component.translatable("screen.craftmaster.tablet." + e.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT)
                + "." + e.name().toLowerCase(Locale.ROOT));
    }

    private static Component yesNo(boolean b) {
        return Component.translatable(b ? "options.on" : "options.off");
    }

    // ---------------------------------------------------------------- server

    private void send(String control) {
        if (!ClientPlayNetworking.canSend(StudioNetwork.EditTablet.TYPE)) return;
        ClientPlayNetworking.send(new StudioNetwork.EditTablet(tablet.getBlockPos(), control.isEmpty() ? s.toJson() : "", control));
    }

    /** A setting changed: sent at once, the buttons show the new values. */
    private void changed() {
        s = TabletClock.Settings.fromJson(s.toJson());
        send("");
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        w = Math.min(width - 8, 420);
        h = Math.min(height - 8, 236);
        left = (width - w) / 2;
        top = (height - h) / 2;

        // Preview controls (left column)
        int cx = left + 8, by = top + 140;
        addRenderableWidget(UiButton.of(cx, by, 60, 18, t("start"), () -> send("start"))).setTooltip(Tooltip.create(t("start.tip")));
        addRenderableWidget(UiButton.of(cx + 64, by, 60, 18, t("pause"), () -> send("pause")));
        addRenderableWidget(UiButton.of(cx, by + 22, 60, 18, t("reset"), () -> send("reset"))).setTooltip(Tooltip.create(t("reset.tip")));
        addRenderableWidget(UiButton.of(cx + 64, by + 22, 60, 18, t("restart"), () -> send("restart"))).setTooltip(Tooltip.create(t("restart.tip")));

        UiTabs tabs = new UiTabs(font, left + 146, top + 22, 16, List.of(t("tab.mode"), t("tab.look"), t("tab.triggers"), t("tab.pose")),
                () -> tab, i -> {
                    tab = i;
                    UiSounds.page();
                    rebuildWidgets();
                });
        tabs.buttons().forEach(this::addRenderableWidget);

        Rows r = new Rows(left + 146, top + 42, w - 154);
        switch (tab) {
            case 0 -> {
                r.cycle("mode", TabletClock.Mode.values(), () -> s.mode, v -> s.mode = v);
                if (s.mode == TabletClock.Mode.TIMER) r.text("duration", TabletClock.minutes(s.seconds), 10, v -> {
                    int sec = TabletClock.parseDuration(v);
                    if (sec > 0 && sec != s.seconds) {
                        s.seconds = sec;
                        s = TabletClock.Settings.fromJson(s.toJson());
                        send("");
                    }
                });
                r.cycle("format", TabletClock.Format.values(), () -> s.format, v -> s.format = v);
                if (s.mode == TabletClock.Mode.TIMER || s.mode == TabletClock.Mode.SHOW) {
                    r.steps("warn", new int[]{0, 3, 5, 10, 15, 30, 60}, () -> s.warnSeconds, v -> s.warnSeconds = v,
                            v -> v == 0 ? Component.translatable("options.off") : t("seconds", v));
                    r.cycle("warn_color", TabletClock.DigitColor.values(), () -> s.warnColor, v -> s.warnColor = v);
                }
            }
            case 1 -> {
                r.cycle("color", TabletClock.DigitColor.values(), () -> s.color, v -> s.color = v);
                r.toggle("ghost", () -> s.ghostSegments, v -> s.ghostSegments = v);
                r.cycle("frame", TabletClock.FrameColor.values(), () -> s.frame, v -> s.frame = v);
                r.cycle("mat", TabletClock.Mat.values(), () -> s.mat, v -> s.mat = v);
                r.steps("size", new int[]{1, 2, 3, 4}, () -> s.size, v -> s.size = v, v -> Component.literal("× " + v));
                r.text("label", s.label, 40, v -> {
                    if (!v.equals(s.label)) {
                        s.label = v;
                        send("");
                    }
                });
                r.toggle("show_label", () -> s.showLabel, v -> s.showLabel = v);
            }
            case 2 -> {
                r.toggle("click", () -> s.clickToggles, v -> s.clickToggles = v);
                r.cycle("redstone", TabletClock.Redstone.values(), () -> s.redstone, v -> s.redstone = v);
                r.toggle("round_start", () -> s.startOnRound, v -> s.startOnRound = v);
                r.toggle("round_end", () -> s.stopOnRoundEnd, v -> s.stopOnRoundEnd = v);
                r.toggle("with_timer", () -> s.stopWithTimer, v -> s.stopWithTimer = v);
                r.cycle("end_sound", TabletClock.EndSound.values(), () -> s.endSound, v -> s.endSound = v);
                r.toggle("blink", () -> s.blinkWhenDone, v -> s.blinkWhenDone = v);
                r.toggle("signal", () -> s.signalWhenDone, v -> s.signalWhenDone = v);
                r.text("group", s.group, 24, v -> {
                    String g = v.trim().toLowerCase(Locale.ROOT);
                    if (!g.equals(s.group)) {
                        s.group = g;
                        send("");
                    }
                });
            }
            default -> {
                if (s.pose == TabletClock.Pose.WALL) r.note("wall");
                else r.cycle("pose", new TabletClock.Pose[]{TabletClock.Pose.STAND, TabletClock.Pose.EASEL, TabletClock.Pose.FLAT},
                        () -> s.pose, v -> s.pose = v);
                if (s.pose == TabletClock.Pose.EASEL)
                    r.steps("tilt", new int[]{0, 5, 10, 15, 20, 30, 45, 60}, () -> s.tilt, v -> s.tilt = v, v -> Component.literal(v + "°"));
                r.note("pose_hint");
            }
        }
        addRenderableWidget(UiButton.of(left + 8, top + h - 24, 124, 18, Component.translatable("gui.done"), this::onClose));
    }

    /** One setting per row: its name on the left, its value (button or text box) on the right. */
    private final class Rows {
        private final int x, width;
        private int y;

        Rows(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        private int valueX() {
            return x + width - 120;
        }

        private void label(String key) {
            String k = key;
            int ly = y;
            labels.add(g -> g.text(font, t(k), x, ly + 5, Theme.TEXT, false));
        }

        /** Click: next value, Shift-click: previous. */
        <E extends Enum<E>> void cycle(String key, E[] values, Supplier<E> get, Consumer<E> set) {
            label(key);
            UiButton b = new UiButton(valueX(), y, 120, 18, value(get.get()), in -> {
                int i = java.util.Arrays.asList(values).indexOf(get.get());
                int n = values.length;
                set.accept(values[((i + (in.hasShiftDown() ? -1 : 1)) % n + n) % n]);
                changed();
            });
            if (I18nHas.tip(key)) b.setTooltip(Tooltip.create(t(key + ".tip")));
            addRenderableWidget(b);
            y += 19;
        }

        void toggle(String key, Supplier<Boolean> get, Consumer<Boolean> set) {
            label(key);
            UiButton b = new UiButton(valueX(), y, 120, 18, yesNo(get.get()).copy().withStyle(get.get() ? ChatFormatting.GREEN : ChatFormatting.GRAY), in -> {
                set.accept(!get.get());
                changed();
            });
            if (I18nHas.tip(key)) b.setTooltip(Tooltip.create(t(key + ".tip")));
            addRenderableWidget(b);
            y += 19;
        }

        void steps(String key, int[] values, Supplier<Integer> get, Consumer<Integer> set, java.util.function.IntFunction<Component> show) {
            label(key);
            UiButton b = new UiButton(valueX(), y, 120, 18, show.apply(get.get()), in -> {
                int i = 0;
                for (int k = 0; k < values.length; k++) if (values[k] <= get.get()) i = k;
                int n = values.length;
                set.accept(values[((i + (in.hasShiftDown() ? -1 : 1)) % n + n) % n]);
                changed();
            });
            if (I18nHas.tip(key)) b.setTooltip(Tooltip.create(t(key + ".tip")));
            addRenderableWidget(b);
            y += 19;
        }

        void text(String key, String value, int max, Consumer<String> onChange) {
            label(key);
            EditBox e = new EditBox(font, valueX(), y, 120, 18, t(key));
            e.setMaxLength(max);
            e.setValue(value);
            e.setResponder(onChange);
            if (I18nHas.tip(key)) e.setTooltip(Tooltip.create(t(key + ".tip")));
            addRenderableWidget(e);
            y += 19;
        }

        void note(String key) {
            int ly = y;
            labels.add(g -> g.text(font, t(key), x, ly + 5, Theme.TEXT_DIM, false));
            y += 14;
        }
    }

    /** Rows' labels, drawn with the panel (rebuilt with the widgets). */
    private final List<Consumer<GuiGraphicsExtractor>> labels = new java.util.ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        labels.clear();
        super.rebuildWidgets();
    }

    @Override
    protected void clearWidgets() {
        labels.clear();
        super.clearWidgets();
    }

    /** Which settings have a tooltip (".tip" key). */
    private static final class I18nHas {
        static boolean tip(String key) {
            return net.minecraft.locale.Language.getInstance().has("screen.craftmaster.tablet." + key + ".tip");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.panel(g, font, left, top, w, h, t("title").copy().withStyle(ChatFormatting.BOLD));
        preview(g, left + 8, top + 24, 128, 96);
        Component state = tablet.clock().running() ? t("state.running").copy().withStyle(ChatFormatting.GREEN)
                : t("state.stopped").copy().withStyle(ChatFormatting.GRAY);
        g.centeredText(font, state, left + 72, top + 126, Theme.TEXT);
        if (!s.group.isEmpty()) g.centeredText(font, t("in_group", s.group), left + 70, top + 186, Theme.TEXT_DIM);
        for (Consumer<GuiGraphicsExtractor> l : labels) l.accept(g);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    /** The tablet's face as in the world, live: the size-4 layers of the tablet texture, twice as big. */
    private void preview(GuiGraphicsExtractor g, int x, int y, int pw, int ph) {
        long now = System.currentTimeMillis();
        TabletView.View v = TabletView.of(s, tablet.clock(), tablet.overAt(), now);
        int frames = TabletClock.FrameColor.values().length;
        org.joml.Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(pw / 64f, ph / 48f);
        blitLayer(g, frames + 3);
        if (s.mat != TabletClock.Mat.NONE) blitLayer(g, frames + s.mat.ordinal());
        blitLayer(g, s.frame.ordinal());
        pose.popMatrix();
        // The screen inside the frame (4 px) and the passe-partout (4 px), at the preview's scale
        float k = pw / 64f;
        int inset = Math.round((s.mat == TabletClock.Mat.NONE ? 4 : 8) * k);
        int sx0 = x + inset, sy0 = y + inset, sx1 = x + pw - inset, sy1 = y + ph - inset;
        int lit = TabletView.lit(v.rgb(), v.bright(), 0x0A0A0A);
        int ghost = s.ghostSegments ? TabletView.ghost(v.rgb()) : 0;
        boolean label = !v.label().isEmpty();
        int dh = (int) ((sy1 - sy0) * (label ? 0.55f : 0.65f));
        int dTop = sy0 + (int) ((sy1 - sy0) * (label ? 0.12f : 0.17f));
        digits2d(g, v.text(), (sx0 + sx1) / 2, dTop, sx1 - sx0 - 8, dh, lit, ghost);
        if (label) {
            String text = font.plainSubstrByWidth(v.label(), sx1 - sx0 - 6);
            g.centeredText(font, text, (sx0 + sx1) / 2, sy1 - 12, TabletView.lit(v.rgb(), 0.85f, 0x0A0A0A));
        }
    }

    /** One 64 × 48 layer (size 4) of the tablet texture at the origin. */
    private static void blitLayer(GuiGraphicsExtractor g, int row) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TimerTabletRenderer.TEXTURE, 0, 0, 96, row * 48, 64, 48, 160, 864);
    }

    /** Seven-segment text in the GUI (whole pixels), centred on cx, top at y. */
    private static void digits2d(GuiGraphicsExtractor g, String text, int cx, int y, int maxW, int maxH, int lit, int ghost) {
        float dh = maxH, dw = dh * 0.56f;
        float total = SegmentDraw.width(text, dw);
        if (total > maxW) {
            dw *= maxW / total;
            dh *= maxW / total;
        }
        float gap = dw * 0.2f;
        float x = cx - SegmentDraw.width(text, dw) / 2;
        int t = Math.max(1, Math.round(dw * 0.18f));
        int top = y + Math.round((maxH - dh) / 2), bot = Math.round(top + dh), mid = (top + bot) / 2;
        for (char ch : text.toCharArray()) {
            int x0 = Math.round(x), x1 = Math.round(x + dw);
            if (ch == ':') {
                int c = Math.round(x + dw * 0.2f);
                g.fill(c - t / 2, top + (bot - top) / 4, c - t / 2 + t, top + (bot - top) / 4 + t, lit);
                g.fill(c - t / 2, bot - (bot - top) / 4 - t, c - t / 2 + t, bot - (bot - top) / 4, lit);
                x += dw * 0.4f + gap;
                continue;
            }
            if (ch == '.') {
                int c = Math.round(x + dw * 0.15f);
                g.fill(c - t / 2, bot - t, c - t / 2 + t, bot, lit);
                x += dw * 0.3f + gap;
                continue;
            }
            int on = SevenSegment.segments(ch);
            seg(g, on, SevenSegment.A, x0 + t, top, x1 - t, top + t, lit, ghost);
            seg(g, on, SevenSegment.G, x0 + t, mid - t / 2, x1 - t, mid - t / 2 + t, lit, ghost);
            seg(g, on, SevenSegment.D, x0 + t, bot - t, x1 - t, bot, lit, ghost);
            seg(g, on, SevenSegment.F, x0, top + t, x0 + t, mid, lit, ghost);
            seg(g, on, SevenSegment.B, x1 - t, top + t, x1, mid, lit, ghost);
            seg(g, on, SevenSegment.E, x0, mid, x0 + t, bot - t, lit, ghost);
            seg(g, on, SevenSegment.C, x1 - t, mid, x1, bot - t, lit, ghost);
            x += dw + gap;
        }
    }

    private static void seg(GuiGraphicsExtractor g, int on, int bit, int x0, int y0, int x1, int y1, int lit, int ghost) {
        boolean isOn = (on & bit) != 0;
        if (!isOn && ghost == 0) return;
        g.fill(x0, y0, x1, y1, isOn ? lit : ghost);
    }
}
