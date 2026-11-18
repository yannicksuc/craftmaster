package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.TrackTargets;
import fr.lordfinn.craftmaster.camera.TravellingNetwork;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A camera's travellings (its menu → a travelling), on the right of the screen so the stage stays in view: the
 * travelling (switch, rename, delete, a new one), its actions (preview on my screen, on air, the ghost in the world,
 * the marker item), its steps (one frame each: stand there, look, "+ step here"), and the selected step's settings
 * (time to get there, hold, transition, zoom, aim: its own angle, a player it tracks, a place it looks at). A single
 * step is a fixed shot. Built on the UI kit; every change is saved on the server, which sends the travelling back.
 */
public class TravellingScreen extends Screen {

    private static final int TEXT = Theme.TEXT, DIM = Theme.TEXT_DIM, GOLD = Theme.GOLD;
    private static final int TOP = 22, ROW = 12;

    private JsonObject data;
    private int selected;
    /** Scrolled content (pixels) and its height, when the panel is taller than the screen allows. */
    private int scroll, contentHeight;
    private int listScroll;
    private boolean deleteArmed;
    private EditBox textBox;
    private Consumer<String> textDone;
    private int x0, y0, w, h, labelW = 50;

    private record Hit(int x0, int y0, int x1, int y1, Runnable click, Runnable right, Consumer<Double> wheel, Component tip) {}

    private final List<Hit> hits = new ArrayList<>();

    public TravellingScreen(JsonObject data) {
        super(Component.translatable("screen.craftmaster.travelling"));
        this.data = data;
        if (data.has("select")) selected = data.get("select").getAsInt();
    }

    /** The travelling open in the editor right now, or null (its steps are drawn in the world). */
    public static String editedPath() {
        return net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof TravellingScreen s ? s.path() : null;
    }

    /** Is this step the one selected in the open editor (its ghost camera shows, in gold)? */
    public static boolean editing(String path, int index) {
        return net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof TravellingScreen s && path.equals(s.path()) && s.selected == index;
    }

    void update(JsonObject o) {
        boolean samePath = o.has("path") && data.has("path") && o.get("path").getAsString().equals(data.get("path").getAsString());
        int count = o.has("steps") ? o.getAsJsonArray("steps").size() : 0;
        if (!samePath) selected = 0;
        else if (count > steps().size()) selected = count - 1; // a step was just added: select it
        data = o;
        if (o.has("select")) selected = o.get("select").getAsInt();
        selected = Math.max(0, Math.min(selected, count - 1));
        deleteArmed = false;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.travelling." + key, args);
    }

    private String camera() {
        return data.get("camera").getAsString();
    }

    private String path() {
        return data.has("path") ? data.get("path").getAsString() : null;
    }

    private JsonArray steps() {
        return data.has("steps") ? data.getAsJsonArray("steps") : new JsonArray();
    }

    private void send(String action, Object... extra) {
        JsonObject o = new JsonObject();
        o.addProperty("camera", camera());
        if (path() != null) o.addProperty("path", path());
        o.addProperty("action", action);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            String k = (String) extra[i];
            Object v = extra[i + 1];
            if (v instanceof Boolean b) o.addProperty(k, b);
            else if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof JsonObject j) o.add(k, j);
            else o.addProperty(k, String.valueOf(v));
        }
        if (ClientPlayNetworking.canSend(TravellingNetwork.Edit.TYPE)) ClientPlayNetworking.send(new TravellingNetwork.Edit(o.toString()));
    }

    /** The camera's menu again (its travellings are listed there). */
    private void backToCamera() {
        JsonObject o = new JsonObject();
        o.addProperty("key", camera());
        o.addProperty("what", "open");
        o.addProperty("value", true);
        if (ClientPlayNetworking.canSend(fr.lordfinn.craftmaster.camera.CameraNetwork.Edit.TYPE))
            ClientPlayNetworking.send(new fr.lordfinn.craftmaster.camera.CameraNetwork.Edit(o.toString()));
    }

    private void setField(String field, Object value) {
        send("set", "index", selected, "field", field, "value", value);
    }

    @Override
    protected void init() {
        w = Math.min(270, Math.max(210, width * 2 / 5));
        h = Math.min(height - 8, 352);
        x0 = width - w - 6;
        y0 = Math.max(6, (height - h) / 2);
        labelW = 0;
        for (String k : new String[]{"travel", "hold", "curve", "zoom", "aim", "angle"}) labelW = Math.max(labelW, font.width(t(k)));
        labelW += 10;
        if (textBox != null) {
            String v = textBox.getValue();
            Consumer<String> d = textDone;
            textBox = null;
            startText(v, d);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        hits.clear();
        dragHits.clear();
        Ui.panel(g, font, x0, y0, w, h, t("title", data.get("number").getAsInt(), data.get("cameraName").getAsString()));
        int lx = x0 + 6, vr = x0 + w - 6;
        // The content scrolls inside the panel when the screen is too small for it
        g.enableScissor(x0 + 1, y0 + 19, x0 + w - 1, y0 + h - 13);
        int y = y0 + TOP - scroll;
        int contentTop = y;
        JsonArray paths = data.getAsJsonArray("paths");
        if (path() == null) {
            g.text(font, t("none"), lx, y + 2, DIM, false);
            y += 14;
            big(g, lx, y, w - 12, t("create"), mx, my, () -> send("create"), t("create_tip"));
            g.disableScissor();
            super.extractRenderState(g, mx, my, partial);
            tooltips(g, mx, my);
            return;
        }
        // ◀ back to the camera's menu, the travelling's name (click: rename), ✕ delete (twice)
        int x = button(g, lx, y, t("back"), false, mx, my, this::backToCamera, null);
        String nm = font.plainSubstrByWidth(path(), w - 120 - (x - lx));
        boolean nh = mx >= x && mx < x + font.width(nm) + 6 && my >= y && my < y + 14;
        g.text(font, nm, x + 3, y + 3, nh ? Theme.TITLE : GOLD, false);
        hits.add(new Hit(x, y, x + font.width(nm) + 6, y + 14, () -> startText(path(), s -> send("rename", "value", s)), null, null, t("rename")));
        x += font.width(nm) + 8;
        int rx = vr - font.width(deleteArmed ? t("delete_sure") : Component.literal("✕")) - 10;
        button(g, rx, y, deleteArmed ? t("delete_sure") : Component.literal("✕"), deleteArmed, mx, my, () -> {
            if (deleteArmed) send("delete");
            else deleteArmed = true;
        }, t("delete"));
        y += 17;
        // Actions
        boolean ghost = data.get("ghost").getAsBoolean();
        x = lx;
        x = button(g, x, y, t("preview"), false, mx, my, () -> TravellingEdit.preview(camera(), path(), selected), t("preview_tip"));
        button(g, x, y, ghost ? t("ghost_on") : t("ghost_off"), ghost, mx, my, () -> send("ghost", "value", !ghost), t("ghost_tip"));
        y += 18;
        // Steps
        g.text(font, t("steps"), lx, y + 1, DIM, false);
        y += 11;
        JsonArray steps = steps();
        int rows = Math.max(height < 300 ? 2 : 3, Math.min(height < 300 ? 3 : 6, steps.size()));
        int listTop = y, listH = rows * ROW;
        Ui.paper(g, lx, listTop - 1, w - 12, listH + 2);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, steps.size() - rows)));
        for (int i = listScroll; i < steps.size() && i - listScroll < rows; i++) {
            JsonObject s = steps.get(i).getAsJsonObject();
            int ry = listTop + (i - listScroll) * ROW;
            boolean sel = i == selected;
            boolean rh = mx >= lx && mx < vr && my >= ry && my < ry + ROW;
            if (sel) g.fill(lx + 1, ry, vr - 1, ry + ROW, Theme.SELECTED);
            else if (rh) g.fill(lx + 1, ry, vr - 1, ry + ROW, 0x20000000);
            g.text(font, font.plainSubstrByWidth(describe(i, s), w - 20), lx + 4, ry + 2, Theme.INK, false);
            int index = i;
            hits.add(new Hit(lx, ry, vr, ry + ROW, () -> selected = index, null, d -> listScroll = Math.max(0, listScroll - (int) Math.signum(d)), null));
        }
        // Added, moved, turned and removed in the world (Axiom's gizmo): here, only what the world cannot show
        y = listTop + listH + 6;
        // The selected step
        if (selected < steps.size()) {
            JsonObject s = steps.get(selected).getAsJsonObject();
            g.text(font, t("step", selected + 1), lx, y, GOLD, false);
            y += 11;
            int vx = lx + labelW;
            if (selected > 0) y = slider(g, y, t("travel"), s.get("travel").getAsDouble(), 0, 30, 0.5, "s", mx, my, v -> setField("travel", v), t("travel_tip"));
            y = slider(g, y, t("hold"), s.get("hold").getAsDouble(), 0, 30, 0.5, "s", mx, my, v -> setField("hold", v), t("hold_tip"));
            if (selected > 0) {
                g.text(font, t("curve"), lx, y + 3, DIM, false);
                String curve = s.get("curve").getAsString();
                y = cycle(g, y, vx, vr, t("curve." + curve).getString(), mx, my, d -> {
                    int i = CameraPath.CURVES.indexOf(curve);
                    setField("curve", CameraPath.CURVES.get(((i < 0 ? 0 : i) + (int) Math.round(d) + CameraPath.CURVES.size()) % CameraPath.CURVES.size()));
                }, null);
            }
            float fov = s.get("fov").getAsFloat();
            y = zoom(g, y, fov, mx, my);
            // Aim: its own angle, an entity it tracks (a selector, as in commands), the stage's centre or a point
            g.text(font, t("aim"), lx, y + 3, DIM, false);
            String target = s.get("target").getAsString();
            boolean tracks = TrackTargets.isEntity(target);
            List<String[]> aims = aims(tracks ? target : null);
            int cur = tracks ? 1 : 0;
            if (!tracks && !target.isEmpty()) {
                cur = -1;
                for (int i = 2; i < aims.size(); i++) if (aims.get(i)[1].equals(target)) cur = i;
                if (cur < 0) {
                    aims.add(new String[]{target, target});
                    cur = aims.size() - 1;
                }
            }
            int fcur = cur;
            y = cycle(g, y, vx, vr, aims.get(fcur)[0], mx, my, d -> {
                String[] next = aims.get(((fcur + (int) Math.round(d)) % aims.size() + aims.size()) % aims.size());
                setField("target", next[1]);
            }, null);
            if (tracks) {
                // The selector (click: type it, with the chat's autocompletion) and what it finds now
                g.text(font, t("entity"), lx, y + 2, DIM, false);
                boolean eh = textBox == null && mx >= vx && mx < vr && my >= y && my < y + 12;
                if (eh) g.fill(vx - 2, y, vr, y + 12, Theme.HOVER);
                g.text(font, font.plainSubstrByWidth(target, vr - vx - 2), vx, y + 2, eh ? GOLD : TEXT, false);
                int ey = y;
                hits.add(new Hit(vx - 2, y, vr, y + 12, () -> startSelector(target, vx - 2, ey, vr - vx + 2), null, null, t("entity_tip")));
                y += 13;
                net.minecraft.world.entity.Entity found = TrackedTargets.entity(CameraPath.trackKey(target,
                        s.get("x").getAsDouble(), s.get("y").getAsDouble(), s.get("z").getAsDouble()));
                String status = found != null ? "→ " + found.getName().getString() : t("entity_none").getString();
                g.text(font, font.plainSubstrByWidth(status, vr - vx), vx, y, found != null ? DIM : 0xFFE08A6A, false);
                y += 11;
            }
            if (target.isEmpty()) {
                g.text(font, t("angle"), lx, y + 2, DIM, false);
                String dir = Math.round(s.get("yaw").getAsFloat()) + "° / " + Math.round(s.get("pitch").getAsFloat()) + "°";
                boolean dh = mx >= vx && mx < vr && my >= y && my < y + 12;
                if (dh) g.fill(vx - 2, y, vr, y + 12, Theme.HOVER);
                g.text(font, dir, vx, y + 2, dh ? GOLD : TEXT, false);
                hits.add(new Hit(vx - 2, y, vr, y + 12, () -> startText(Math.round(s.get("yaw").getAsFloat()) + " " + Math.round(s.get("pitch").getAsFloat()), v -> {
                    String[] n = v.replace("°", " ").replace("/", " ").replace(",", " ").trim().split("\\s+");
                    try {
                        JsonObject a = new JsonObject();
                        a.addProperty("yaw", Float.parseFloat(n[0]));
                        a.addProperty("pitch", Math.max(-90, Math.min(90, Float.parseFloat(n[1]))));
                        setField("angle", a);
                    } catch (RuntimeException ignored) {
                    }
                }), null, null, t("angle_tip")));
                y += 13;
            }
            // Like a camera: look through the step and frame it
            JsonObject st = s;
            int sel = selected;
            button(g, lx, y, t("control"), false, mx, my, () -> ControlView.startStep(camera(), path(), sel,
                    new net.minecraft.world.phys.Vec3(st.get("x").getAsDouble(), st.get("y").getAsDouble(), st.get("z").getAsDouble()),
                    st.get("yaw").getAsFloat(), st.get("pitch").getAsFloat(), st.get("fov").getAsFloat()), t("control_tip"));
            y += 16;
        }
        g.disableScissor();
        contentHeight = y - contentTop;
        int room = h - 22;
        if (contentHeight > room) {
            // A thin bar on the right: where the view is in the content
            int barH = Math.max(12, room * room / contentHeight), barY = y0 + 19 + (room - barH) * scroll / Math.max(1, contentHeight - room);
            g.fill(x0 + w - 3, barY, x0 + w - 1, barY + barH, Theme.BORDER_DARK);
        }
        if (textBox != null && !suggesting) {
            g.fill(textBox.getX() - 3, textBox.getY() - 12, textBox.getX() + textBox.getWidth() + 3, textBox.getY() + 19, Theme.PANEL);
            g.text(font, t("text_hint"), textBox.getX(), textBox.getY() - 10, DIM, false);
        }
        super.extractRenderState(g, mx, my, partial);
        if (textBox != null && suggesting) drawSuggestions(g, mx, my);
        if (textBox == null) tooltips(g, mx, my);
    }

    /** The aim choices: its own angle, an entity (its selector, "@p" at first), the stage's centre and points. */
    private List<String[]> aims(String selector) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{t("aim_angle").getString(), ""});
        out.add(new String[]{t("aim_entity").getString(), selector != null ? selector : "@p"});
        for (JsonElement e : data.getAsJsonArray("points")) {
            JsonObject p = e.getAsJsonObject();
            if (p.get("at").getAsString().isEmpty()) continue;
            // "@centre": the middle of the stage zone; "@point:x": a point named on the stage plan (/cm plateau point)
            String name = p.get("name").getAsString();
            Component label = name.startsWith("@point:") ? t("aim_point", name.substring(7)) : t("aim_centre");
            out.add(new String[]{label.getString(), p.get("at").getAsString()});
        }
        return out;
    }

    /** A step in the list: when it starts (its time in the travelling), then what is special about it. */
    private String describe(int i, JsonObject s) {
        StringBuilder b = new StringBuilder(clock(start(i)));
        List<String> more = new ArrayList<>();
        if (i > 0) more.add(t("curve." + s.get("curve").getAsString()).getString());
        if (s.get("hold").getAsDouble() > 0) more.add(t("pause", clock(s.get("hold").getAsDouble())).getString());
        if (!more.isEmpty()) b.append(" · ").append(String.join(", ", more));
        String target = s.get("target").getAsString();
        if (!target.isEmpty()) b.append(more.isEmpty() ? " · " : ", ").append(target.matches("-?[0-9.]+ -?[0-9.]+ -?[0-9.]+") ? t("looks").getString() : t("tracks", target).getString());
        if (s.get("fov").getAsFloat() > 0) b.append(", ").append(Math.round(s.get("fov").getAsFloat())).append("°");
        return b.toString();
    }

    /** Seconds from the start when the camera reaches step {@code i}. */
    private double start(int i) {
        JsonArray steps = steps();
        double t = 0;
        for (int k = 1; k <= i && k < steps.size(); k++)
            t += steps.get(k - 1).getAsJsonObject().get("hold").getAsDouble() + steps.get(k).getAsJsonObject().get("travel").getAsDouble();
        return t;
    }

    /** "1m05s", "12s", "3,5s": minutes only past a minute, a tenth only when there is one. */
    static String clock(double seconds) {
        long tenths = Math.round(seconds * 10);
        long whole = tenths / 10, m = whole / 60, sec = whole % 60;
        String tail = tenths % 10 == 0 ? "" : "," + (tenths % 10);
        return m > 0 ? m + "m" + (sec < 10 ? "0" : "") + sec + tail + "s" : sec + tail + "s";
    }


    private void tooltips(GuiGraphicsExtractor g, int mx, int my) {
        for (Hit hh : hits)
            if (hh.tip != null && mx >= hh.x0 && mx < hh.x1 && my >= hh.y0 && my < hh.y1) {
                fr.lordfinn.craftmaster.client.ui.Ui.tooltip(g, font, hh.tip, mx, my);
                break;
            }
    }

    // ---------------------------------------------------------------- controls (UI kit)

    private int button(GuiGraphicsExtractor g, int x, int y, Component label, boolean on, int mx, int my, Runnable click, Component tip) {
        int bw = font.width(label) + 10;
        boolean hover = mx >= x && mx < x + bw && my >= y && my < y + 14;
        Ui.button(g, font, x, y, bw, 14, label, on, hover, true);
        hits.add(new Hit(x, y, x + bw, y + 14, click, null, null, tip));
        return x + bw + 2;
    }

    private void big(GuiGraphicsExtractor g, int x, int y, int bw, Component label, int mx, int my, Runnable click, Component tip) {
        boolean hover = mx >= x && mx < x + bw && my >= y && my < y + 18;
        Ui.button(g, font, x, y, bw, 18, label, false, hover, true);
        hits.add(new Hit(x, y, x + bw, y + 18, click, null, null, tip));
    }

    private int cycle(GuiGraphicsExtractor g, int y, int vx, int vr, String value, int mx, int my, Consumer<Double> step, Component tip) {
        boolean hover = mx >= vx && mx < vr && my >= y && my < y + 13;
        Ui.button(g, font, vx, y, vr - vx, 13, Component.empty(), false, hover, true);
        g.text(font, "◀", vx + 3, y + 3, DIM, false);
        g.text(font, "▶", vr - 9, y + 3, DIM, false);
        String shown = font.plainSubstrByWidth(value, vr - vx - 26);
        g.text(font, shown, vx + (vr - vx - font.width(shown)) / 2, y + 3, TEXT, false);
        hits.add(new Hit(vx, y, vx + 12, y + 13, () -> step.accept(-1.0), null, null, tip));
        hits.add(new Hit(vx + 12, y, vr, y + 13, () -> step.accept(1.0), null, d -> step.accept(-d), tip));
        return y + 16;
    }

    /** A value with a bar: wheel ±step (Shift ×4), click on the bar sets it there. */
    private int slider(GuiGraphicsExtractor g, int y, Component label, double value, double min, double max, double step, String unit,
                       int mx, int my, Consumer<Double> set, Component tip) {
        int lx = x0 + 6, vx = x0 + 6 + labelW, vr = x0 + w - 6;
        g.text(font, label, lx, y + 2, DIM, false);
        String v = CameraPath.seconds(value).replace(" s", "") + unit;
        int tw = font.width(v);
        int b0 = vx, b1 = vr - tw - 4;
        g.fill(b0, y + 4, b1, y + 6, 0x60FFFFFF);
        int knob = b0 + (int) Math.round((b1 - b0) * Math.min(1, (value - min) / (max - min)));
        g.fill(b0, y + 4, knob, y + 6, GOLD);
        g.fill(knob - 1, y + 1, knob + 2, y + 9, 0xFFFFFFFF);
        g.text(font, v, vr - tw, y + 1, TEXT, false);
        Hit bar = new Hit(b0 - 2, y - 1, b1 + 3, y + 11, () -> {
            double f = Math.max(0, Math.min(1, (mouseX() - b0) / (double) Math.max(1, b1 - b0)));
            set.accept(Math.round((min + f * (max - min)) / step) * step);
        }, null, d -> set.accept(Math.max(min, Math.min(max, Math.round((value + d * step * (minecraft.hasShiftDown() ? 4 : 1)) / step) * step))), tip);
        hits.add(bar);
        dragHits.add(bar);
        return y + 14;
    }

    private int zoom(GuiGraphicsExtractor g, int y, float fov, int mx, int my) {
        int lx = x0 + 6, vx = x0 + 6 + labelW, vr = x0 + w - 6;
        g.text(font, t("zoom"), lx, y + 2, DIM, false);
        String zv = fov <= 0 ? t("zoom_auto").getString() : Math.round(fov) + "°";
        int tw = font.width(zv);
        int b0 = vx, b1 = vr - tw - 4;
        float shown = fov <= 0 ? 70 : fov;
        g.fill(b0, y + 4, b1, y + 6, 0x60FFFFFF);
        int knob = b0 + Math.round((b1 - b0) * (shown - CameraSpot.MIN_FOV) / (CameraSpot.MAX_FOV - CameraSpot.MIN_FOV));
        g.fill(b0, y + 4, knob, y + 6, fov <= 0 ? DIM : GOLD);
        g.fill(knob - 1, y + 1, knob + 2, y + 9, 0xFFFFFFFF);
        g.text(font, zv, vr - tw, y + 1, fov <= 0 ? DIM : TEXT, false);
        Hit bar = new Hit(b0 - 2, y - 1, b1 + 3, y + 11, () -> {
            double f = Math.max(0, Math.min(1, (mouseX() - b0) / (double) Math.max(1, b1 - b0)));
            setField("fov", Math.round(CameraSpot.MIN_FOV + f * (CameraSpot.MAX_FOV - CameraSpot.MIN_FOV)));
        }, () -> setField("fov", 0), d -> setField("fov", Math.max(CameraSpot.MIN_FOV, Math.min(CameraSpot.MAX_FOV, shown - d * 5))), t("zoom_tip"));
        hits.add(bar);
        dragHits.add(bar);
        return y + 14;
    }

    private double lastMouseX;
    /** The bars of this frame, and the one held: it follows the mouse until let go (sent at most every 60 ms). */
    private final List<Hit> dragHits = new ArrayList<>();
    private Hit dragging;
    private long dragSent;

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            lastMouseX = e.x();
            if (System.currentTimeMillis() - dragSent > 60) {
                dragSent = System.currentTimeMillis();
                dragging.click().run();
            }
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) {
            lastMouseX = e.x();
            dragging.click().run();
            dragging = null;
            return true;
        }
        return super.mouseReleased(e);
    }

    private double mouseX() {
        return lastMouseX;
    }

    // ---------------------------------------------------------------- text

    /** The selector of a tracked entity, typed in place, with the chat's autocompletion under it. */
    private void startSelector(String value, int x, int y, int width) {
        startText(value, v -> {
            if (!v.isBlank()) setField("target", v.strip());
        });
        textBox.setX(x);
        textBox.setY(y - 2);
        textBox.setWidth(width);
        textBox.setMaxLength(256);
        textBox.setValue(value);
        // All selected: typing replaces it
        textBox.moveCursorToEnd(false);
        textBox.setHighlightPos(0);
        suggesting = true;
    }

    private boolean suggesting;
    private List<com.mojang.brigadier.suggestion.Suggestion> suggestions = List.of();
    private String suggestedFor;
    private int suggestionIndex, suggestionY, suggestionW;

    /** The autocompletion under the selector box: what fits at the cursor. */
    private void drawSuggestions(GuiGraphicsExtractor g, int mx, int my) {
        String text = textBox.getValue();
        int cursor = Math.min(text.length(), textBox.getCursorPosition());
        String key = text + "|" + cursor;
        if (!key.equals(suggestedFor)) {
            suggestedFor = key;
            suggestions = TrackedTargets.suggest(text.substring(0, cursor));
            if (suggestions.size() > 8) suggestions = suggestions.subList(0, 8);
            suggestionIndex = 0;
        }
        if (suggestions.isEmpty()) return;
        int x = textBox.getX(), y = textBox.getY() + 17;
        int width = 0;
        for (var sg : suggestions) width = Math.max(width, font.width(sg.getText()));
        width += 8;
        suggestionY = y;
        suggestionW = width;
        g.fill(x - 1, y - 1, x + width + 1, y + suggestions.size() * 11 + 1, Theme.BORDER_DARK);
        g.fill(x, y, x + width, y + suggestions.size() * 11, Theme.PANEL);
        for (int i = 0; i < suggestions.size(); i++) {
            boolean on = i == suggestionIndex || mx >= x && mx < x + width && my >= y + i * 11 && my < y + i * 11 + 11;
            if (on) g.fill(x, y + i * 11, x + width, y + i * 11 + 11, Theme.HOVER);
            g.text(font, suggestions.get(i).getText(), x + 4, y + i * 11 + 2, on ? GOLD : TEXT, false);
        }
    }

    private void applySuggestion(int i) {
        if (i < 0 || i >= suggestions.size()) return;
        String text = textBox.getValue();
        int cursor = Math.min(text.length(), textBox.getCursorPosition());
        String head = suggestions.get(i).apply(text.substring(0, cursor));
        textBox.setValue(head + text.substring(cursor));
        textBox.setCursorPosition(head.length());
        textBox.setHighlightPos(head.length());
    }

    private void startText(String value, Consumer<String> done) {
        closeText(false);
        suggesting = false;
        textBox = new EditBox(font, x0 + 6, Math.max(4, y0 - 20), w - 12, 16, Component.empty());
        textBox.setMaxLength(64);
        textBox.setValue(value == null ? "" : value);
        textDone = done;
        addRenderableWidget(textBox);
        setFocused(textBox);
        textBox.setFocused(true);
    }

    private void closeText(boolean keep) {
        if (textBox == null) return;
        if (keep && textDone != null) textDone.accept(textBox.getValue());
        removeWidget(textBox);
        textBox = null;
        textDone = null;
        suggesting = false;
        suggestions = List.of();
        suggestedFor = null;
        setFocused(null);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        lastMouseX = e.x();
        if (textBox != null) {
            if (suggesting && !suggestions.isEmpty() && e.x() >= textBox.getX() && e.x() < textBox.getX() + suggestionW
                    && e.y() >= suggestionY && e.y() < suggestionY + suggestions.size() * 11) {
                applySuggestion((int) ((e.y() - suggestionY) / 11));
                setFocused(textBox);
                return true;
            }
            if (textBox.isMouseOver(e.x(), e.y())) return super.mouseClicked(e, doubleClick);
            closeText(true);
            return true;
        }
        if (super.mouseClicked(e, doubleClick)) return true;
        boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (inContent(e.y())) for (Hit hh : List.copyOf(hits))
            if (e.x() >= hh.x0 && e.x() < hh.x1 && e.y() >= hh.y0 && e.y() < hh.y1) {
                if (right && hh.right != null) hh.right.run();
                else if (!right && hh.click != null) {
                    hh.click.run();
                    if (dragHits.contains(hh)) {
                        dragging = hh;
                        dragSent = System.currentTimeMillis();
                    }
                }
                return true;
            }
        if (e.x() < x0 || e.x() > x0 + w || e.y() < y0 || e.y() > y0 + h) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (inContent(y)) for (Hit hh : List.copyOf(hits))
            if (hh.wheel != null && x >= hh.x0 && x < hh.x1 && y >= hh.y0 && y < hh.y1) {
                hh.wheel.accept(Math.signum(sy));
                return true;
            }
        if (x >= x0 && x < x0 + w && y >= y0 && y < y0 + h) {
            scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (h - 22)), scroll - (int) Math.round(sy * 18)));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    /** Inside the scrolled part of the panel (a control hidden above or below cannot be clicked). */
    private boolean inContent(double y) {
        return y >= y0 + 19 && y < y0 + h - 13;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (textBox != null) {
            if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) {
                closeText(true);
                return true;
            }
            if (e.key() == InputConstants.KEY_ESCAPE) {
                closeText(false);
                return true;
            }
            if (suggesting && !suggestions.isEmpty()) {
                if (e.key() == InputConstants.KEY_TAB) {
                    applySuggestion(suggestionIndex);
                    return true;
                }
                if (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN) {
                    suggestionIndex = Math.floorMod(suggestionIndex + (e.key() == InputConstants.KEY_UP ? -1 : 1), suggestions.size());
                    return true;
                }
            }
            return super.keyPressed(e);
        }
        if (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN) {
            selected = Math.max(0, Math.min(steps().size() - 1, selected + (e.key() == InputConstants.KEY_UP ? -1 : 1)));
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Closed: the step selected here stays selected in the world (Axiom's gizmo on it). */
    @Override
    public void removed() {
        super.removed();
        if (path() != null && !steps().isEmpty()) TravellingEdit.select(path(), selected);
    }

    /** Opens the editor when the server sends a travelling, or refreshes it. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(TravellingNetwork.Open.TYPE, (p, ctx) -> {
            JsonObject o = com.google.gson.JsonParser.parseString(p.json()).getAsJsonObject();
            // The travelling open in the editor is the one edited in the world
            if (o.has("path")) TravellingEdit.setEdited(o.get("camera").getAsString(), o.get("path").getAsString());
            if (ctx.client().gui.screen() instanceof TravellingScreen s && s.camera().equals(o.get("camera").getAsString())) s.update(o);
            else ctx.client().gui.setScreen(new TravellingScreen(o));
        });
    }
}
