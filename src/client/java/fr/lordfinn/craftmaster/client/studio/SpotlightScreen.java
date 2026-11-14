package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.stage.Sequence;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import fr.lordfinn.craftmaster.studio.SpotlightFollow;
import fr.lordfinn.craftmaster.studio.SpotlightNetwork;
import fr.lordfinn.craftmaster.studio.Spotlights;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A spotlight's menu (organisers: sneak + right-click on it), on the right of the screen so the beam stays in
 * view: its name, on / off, colour, intensity, cone, direction (turn, tilt, the other way, onto what you look at,
 * onto you), who it follows and its effect. Every change is sent at once and shows on the beam; the values shown are
 * the spotlight's own (read from the world every frame).
 */
public class SpotlightScreen extends Screen {

    private static final int TEXT = Theme.TEXT, DIM = Theme.TEXT_DIM, GOLD = Theme.GOLD;
    /** Below the panel's title bar. */
    private static final int TOP = 22;
    private static final String[] COLORS = {"blanc", "chaud", "froid", "ambre", "or", "rouge", "orange", "jaune", "vert", "cyan", "bleu", "violet", "rose", "magenta"};

    private final BlockPos pos;
    private final List<String> follow = new ArrayList<>();
    private EditBox nameBox;
    /** Typing the aim: "horizontal vertical" in degrees (null: not typing). */
    private EditBox angleBox;
    private int x0, y0, w, h, labelW = 50;

    private record Hit(int x0, int y0, int x1, int y1, Runnable click, Consumer<Double> wheel, Component tip) {}

    private final List<Hit> hits = new ArrayList<>();

    /** A slider being dragged: which, and its value range. */
    private String dragging;
    private double dragMin, dragMax;
    private int dragX0, dragX1;
    private long lastSent;

    public SpotlightScreen(JsonObject o) {
        super(Component.translatable("screen.craftmaster.spotlight"));
        pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
        for (JsonElement e : o.getAsJsonArray("follow")) follow.add(e.getAsString());
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.spotlight." + key, args);
    }

    private SpotlightBlockEntity spot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SpotlightBlockEntity s ? s : null;
    }

    private void send(String what, Object value) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("what", what);
        if (value instanceof Boolean b) o.addProperty("value", b);
        else if (value instanceof Number n) o.addProperty("value", n);
        else if (value instanceof JsonObject j) o.add("value", j);
        else o.addProperty("value", String.valueOf(value));
        if (ClientPlayNetworking.canSend(SpotlightNetwork.Edit.TYPE)) ClientPlayNetworking.send(new SpotlightNetwork.Edit(o.toString()));
    }

    @Override
    protected void init() {
        w = Math.min(210, Math.max(176, width / 3));
        labelW = 0;
        for (String k : new String[]{"name", "lit", "color", "intensity", "cone", "direction", "follow", "effect", "speed"})
            labelW = Math.max(labelW, font.width(t(k)));
        labelW += 10;
        h = Math.min(height - 16, 246);
        x0 = width - w - 8;
        y0 = Math.max(8, (height - h) / 2);
        SpotlightBlockEntity s = spot();
        nameBox = new EditBox(font, x0 + 6 + labelW, y0 + TOP, w - 12 - labelW, 14, Component.empty());
        nameBox.setMaxLength(32);
        nameBox.setValue(s == null ? "" : s.name());
        nameBox.setHint(t("name_hint"));
        addRenderableWidget(nameBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        hits.clear();
        SpotlightBlockEntity s = spot();
        if (s == null) {
            onClose();
            return;
        }
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        Ui.panel(g, font, x0, y0, w, h, Component.literal(t("title").getString() + (s.name().isEmpty() ? "" : " « " + s.name() + " »") + "  ·  " + at));
        int lx = x0 + 6, vx = x0 + 6 + labelW, vr = x0 + w - 6;
        g.text(font, t("name"), lx, y0 + TOP + 3, DIM, false);

        int y = y0 + TOP + 20;
        // On / off
        g.text(font, t("lit"), lx, y + 3, DIM, false);
        button(g, vx, y, s.lit() ? t("on") : t("off"), s.lit(), mx, my, () -> send("lit", !s.lit()), null);
        y += 16;
        // Colour swatches
        g.text(font, t("color"), lx, y + 2, DIM, false);
        int sw = Math.max(6, Math.min(9, (vr - vx) / COLORS.length - 1));
        for (int i = 0; i < COLORS.length; i++) {
            int sx = vx + i * (sw + 1);
            if (sx + sw > vr) break;
            Integer c = Spotlights.parseColor(COLORS[i]);
            boolean on = c != null && c == s.color();
            if (on) g.fill(sx - 1, y - 1, sx + sw + 1, y + 11, 0xFFFFFFFF);
            g.fill(sx, y, sx + sw, y + 10, 0xFF000000 | (c == null ? 0xFFFFFF : c));
            String name = COLORS[i];
            hits.add(new Hit(sx, y - 1, sx + sw + 1, y + 11, () -> send("color", name), null, Component.literal(name)));
        }
        y += 15;
        // Intensity, cone: sliders (drag, wheel)
        y = slider(g, y, t("intensity"), "intensity", s.intensity(), 0, 100, 5, "%", mx, my);
        y = slider(g, y, t("cone"), "cone", s.cone(), SpotlightBlockEntity.MIN_CONE, SpotlightBlockEntity.MAX_CONE, 0.5, "°", mx, my);
        // Direction
        g.text(font, t("direction"), lx, y + 3, DIM, false);
        String dir = s.follow().isEmpty() ? Math.round(s.yaw()) + "° / " + Math.round(s.pitch()) + "°" : t("follows_now").getString();
        if (angleBox == null) {
            boolean dh = mx >= vx && mx < vr && my >= y && my < y + 12;
            if (dh) g.fill(vx - 2, y, vr, y + 12, 0x30FFFFFF);
            g.text(font, dir, vx, y + 3, dh ? GOLD : TEXT, false);
            int ay = y;
            hits.add(new Hit(vx - 2, y, vr, y + 12, () -> {
                angleBox = new EditBox(font, vx - 2, ay, vr - vx + 2, 12, Component.empty());
                angleBox.setMaxLength(24);
                angleBox.setValue(Math.round(s.yaw()) + " " + Math.round(s.pitch()));
                addRenderableWidget(angleBox);
                setFocused(angleBox);
                angleBox.setFocused(true);
            }, null, t("angle_tip")));
        }
        y += 13;
        int bx = vx;
        bx = button(g, bx, y, Component.literal("◀"), false, mx, my, () -> send("turn", -15), t("turn_left"));
        bx = button(g, bx, y, Component.literal("▶"), false, mx, my, () -> send("turn", 15), t("turn_right"));
        bx = button(g, bx, y, Component.literal("▲"), false, mx, my, () -> send("tilt", -10), t("tilt_up"));
        bx = button(g, bx, y, Component.literal("▼"), false, mx, my, () -> send("tilt", 10), t("tilt_down"));
        button(g, bx, y, Component.literal("⇄"), false, mx, my, () -> send("flip", true), t("flip"));
        y += 15;
        bx = button(g, vx, y, t("look"), false, mx, my, () -> send("look", true), t("look_tip"));
        if (bx + font.width(t("me")) + 8 > vr) {
            y += 15;
            bx = vx;
        }
        button(g, bx, y, t("me"), false, mx, my, () -> send("me", true), t("me_tip"));
        y += 17;
        // Follow
        g.text(font, t("follow"), lx, y + 3, DIM, false);
        String cur = s.follow();
        String shown = cur.isEmpty() ? t("nobody").getString() : SpotlightFollow.isRule(cur) ? SpotlightFollow.describe(cur) : cur;
        y = cycle(g, y, vx, vr, shown, mx, my, d -> {
            int i = follow.indexOf(cur);
            send("follow", follow.get(((i < 0 ? 0 : i) + (int) Math.round(d) + follow.size()) % follow.size()));
        });
        // Effect and speed
        g.text(font, t("effect"), lx, y + 3, DIM, false);
        String fx = s.effect().isEmpty() ? Sequence.NO_EFFECT : s.effect();
        y = cycle(g, y, vx, vr, fx, mx, my, d -> {
            int i = Sequence.EFFECTS.indexOf(fx);
            send("effect", Sequence.EFFECTS.get(((i < 0 ? 0 : i) + (int) Math.round(d) + Sequence.EFFECTS.size()) % Sequence.EFFECTS.size()));
        });
        if (!s.effect().isEmpty()) y = slider(g, y, t("speed"), "speed", s.effectSpeed(), 0.25, 4, 0.25, "×", mx, my);
        g.text(font, font.plainSubstrByWidth(t("hint").getString(), w - 12), x0 + 6, y0 + h - 11, DIM, false);

        super.extractRenderState(g, mx, my, partial);
        for (Hit hh : hits)
            if (hh.tip != null && mx >= hh.x0 && mx < hh.x1 && my >= hh.y0 && my < hh.y1) {
                g.setTooltipForNextFrame(hh.tip, mx, my);
                break;
            }
    }

    private int button(GuiGraphicsExtractor g, int x, int y, Component label, boolean on, int mx, int my, Runnable click, Component tip) {
        int bw = font.width(label) + 8;
        boolean hover = mx >= x && mx < x + bw && my >= y && my < y + 13;
        Ui.button(g, font, x, y, bw, 13, label, on, hover, true);
        hits.add(new Hit(x, y, x + bw, y + 13, click, null, tip));
        return x + bw + 2;
    }

    private int slider(GuiGraphicsExtractor g, int y, Component label, String key, double value, double min, double max, double step, String unit,
                       int mx, int my) {
        int lx = x0 + 6, vx = x0 + 6 + labelW, vr = x0 + w - 6;
        g.text(font, label, lx, y + 2, DIM, false);
        String v = num(value) + unit;
        int tw = font.width(v);
        int bar0 = vx, bar1 = vr - tw - 4;
        g.fill(bar0, y + 4, bar1, y + 6, 0x60FFFFFF);
        int knob = bar0 + (int) Math.round((bar1 - bar0) * (value - min) / Math.max(1e-6, max - min));
        g.fill(bar0, y + 4, knob, y + 6, GOLD);
        g.fill(knob - 1, y + 1, knob + 2, y + 9, 0xFFFFFFFF);
        g.text(font, v, vr - tw, y + 1, TEXT, false);
        hits.add(new Hit(bar0 - 2, y - 1, bar1 + 3, y + 11, () -> {
            dragging = key;
            dragMin = min;
            dragMax = max;
            dragX0 = bar0;
            dragX1 = bar1;
        }, d -> send(key, Math.max(min, Math.min(max, value + d * step * (minecraft.hasShiftDown() ? 4 : 1)))), null));
        return y + 14;
    }

    private int cycle(GuiGraphicsExtractor g, int y, int vx, int vr, String value, int mx, int my, Consumer<Double> step) {
        boolean hover = mx >= vx && mx < vr && my >= y && my < y + 13;
        Ui.button(g, font, vx, y, vr - vx, 13, Component.empty(), false, hover, true);
        g.text(font, "◀", vx + 3, y + 3, DIM, false);
        g.text(font, "▶", vr - 9, y + 3, DIM, false);
        String shown = font.plainSubstrByWidth(value, vr - vx - 26);
        g.text(font, shown, vx + (vr - vx - font.width(shown)) / 2, y + 3, TEXT, false);
        hits.add(new Hit(vx, y, vx + 12, y + 13, () -> step.accept(-1.0), null, null));
        hits.add(new Hit(vx + 12, y, vr, y + 13, () -> step.accept(1.0), d -> step.accept(-d), null));
        return y + 16;
    }

    private void dragTo(double mx) {
        if (dragging == null) return;
        double f = Math.max(0, Math.min(1, (mx - dragX0) / Math.max(1, dragX1 - dragX0)));
        double v = dragMin + f * (dragMax - dragMin);
        double step = dragging.equals("intensity") ? 5 : dragging.equals("cone") ? 0.5 : 0.25;
        v = Math.round(v / step) * step;
        long now = System.currentTimeMillis();
        if (now - lastSent >= 80) {
            lastSent = now;
            send(dragging, v);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (angleBox != null && !angleBox.isMouseOver(e.x(), e.y())) applyAngle();
        if (super.mouseClicked(e, doubleClick)) return true;
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (Hit hh : List.copyOf(hits))
            if (e.x() >= hh.x0 && e.x() < hh.x1 && e.y() >= hh.y0 && e.y() < hh.y1 && hh.click != null) {
                hh.click.run();
                if (dragging != null) dragTo(e.x());
                return true;
            }
        // A click outside the panel: back to the game
        if (e.x() < x0 || e.x() > x0 + w || e.y() < y0 || e.y() > y0 + h) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            dragTo(e.x());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) {
            lastSent = 0;
            dragTo(e.x());
            dragging = null;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        for (Hit hh : List.copyOf(hits))
            if (hh.wheel != null && x >= hh.x0 && x < hh.x1 && y >= hh.y0 && y < hh.y1) {
                hh.wheel.accept(Math.signum(sy));
                return true;
            }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (angleBox != null && angleBox.isFocused()) {
            if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) {
                applyAngle();
                return true;
            }
            if (e.key() == InputConstants.KEY_ESCAPE) {
                closeAngle();
                return true;
            }
            return super.keyPressed(e);
        }
        if (nameBox.isFocused() && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) {
            send("name", nameBox.getValue().strip());
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(e);
    }

    /** "35 20", "35/20", "35°, -10°" → horizontal, vertical (degrees); sent as the spotlight's exact aim. */
    private void applyAngle() {
        String[] n = angleBox.getValue().replace("°", " ").replace("/", " ").replace(";", " ").replace(",", " ").trim().split("\s+");
        try {
            if (n.length == 2) {
                JsonObject a = new JsonObject();
                a.addProperty("yaw", Float.parseFloat(n[0]));
                a.addProperty("pitch", Math.max(-90, Math.min(90, Float.parseFloat(n[1]))));
                send("angle", a);
            }
        } catch (NumberFormatException ignored) {
        }
        closeAngle();
    }

    private void closeAngle() {
        if (angleBox == null) return;
        removeWidget(angleBox);
        angleBox = null;
        setFocused(null);
    }

    @Override
    public void onClose() {
        SpotlightBlockEntity s = spot();
        // A name typed but not confirmed is kept
        if (nameBox != null && s != null && !nameBox.getValue().strip().equalsIgnoreCase(s.name())) send("name", nameBox.getValue().strip());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0).replace('.', ',');
    }

    /** Opens the menu when the server says so. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpotlightNetwork.Open.TYPE, (p, ctx) ->
                ctx.client().gui.setScreen(new SpotlightScreen(com.google.gson.JsonParser.parseString(p.json()).getAsJsonObject())));
    }

}
