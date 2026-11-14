package fr.lordfinn.craftmaster.client.studio;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.camera.CameraNetwork;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A studio camera's menu (camera drivers: right-click on it), on the right of the screen: name, role, zoom, motion,
 * visible or hidden, aim (typed, or where you look), and the two big actions: take control (look through it and
 * frame it with the mouse) and put it on air. Built on the UI kit; every change is sent at once and the server
 * sends the camera back.
 */
public class CameraScreen extends Screen {

    private static final int TEXT = Theme.TEXT, DIM = Theme.TEXT_DIM, GOLD = Theme.GOLD;
    private static final int TOP = 22;
    private static final List<String> ROLES = List.of("", CameraSpot.WIDE, CameraSpot.THRONE, CameraSpot.PODIUM);

    private JsonObject cam;
    private EditBox nameBox, angleBox;
    private int x0, y0, w, h, labelW = 50;

    private record Hit(int x0, int y0, int x1, int y1, Runnable click, Runnable right, Consumer<Double> wheel, Component tip) {}

    private final List<Hit> hits = new ArrayList<>();
    private boolean draggingZoom;
    private int zoomX0, zoomX1;
    private long lastSent;

    public CameraScreen(JsonObject cam) {
        super(Component.translatable("screen.craftmaster.camera"));
        this.cam = cam;
    }

    /** The server sent the camera again (after a change): values refresh, a name being typed is kept. */
    void update(JsonObject o) {
        int before = cam.has("paths") ? cam.getAsJsonArray("paths").size() : 0, after = o.has("paths") ? o.getAsJsonArray("paths").size() : 0;
        cam = o;
        if (before != after) rebuildWidgets();
        if (nameBox != null && !nameBox.isFocused()) nameBox.setValue(o.get("name").getAsString());
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.camera." + key, args);
    }

    private String key() {
        return cam.get("key").getAsString();
    }

    private void send(String what, Object value) {
        JsonObject o = new JsonObject();
        o.addProperty("key", key());
        o.addProperty("what", what);
        if (value instanceof Boolean b) o.addProperty("value", b);
        else if (value instanceof Number n) o.addProperty("value", n);
        else if (value instanceof JsonObject j) o.add("value", j);
        else o.addProperty("value", String.valueOf(value));
        if (ClientPlayNetworking.canSend(CameraNetwork.Edit.TYPE)) ClientPlayNetworking.send(new CameraNetwork.Edit(o.toString()));
    }

    @Override
    protected void init() {
        w = Math.min(220, Math.max(184, width / 3));
        h = Math.min(height - 12, 182 + 26 + 15 * Math.min(5, cam.has("paths") ? cam.getAsJsonArray("paths").size() : 0) + 26);
        x0 = width - w - 8;
        y0 = Math.max(8, (height - h) / 2);
        labelW = 0;
        for (String k : new String[]{"name", "role", "zoom", "motion", "visibility", "direction"}) labelW = Math.max(labelW, font.width(t(k)));
        labelW += 10;
        nameBox = new EditBox(font, x0 + 6 + labelW, y0 + TOP, w - 12 - labelW, 14, Component.empty());
        nameBox.setMaxLength(40);
        nameBox.setValue(cam.get("name").getAsString());
        addRenderableWidget(nameBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        hits.clear();
        Ui.panel(g, font, x0, y0, w, h, t("title", cam.get("number").getAsInt(), cam.get("place").getAsString()));
        int lx = x0 + 6, vx = x0 + 6 + labelW, vr = x0 + w - 6;
        g.text(font, t("name"), lx, y0 + TOP + 3, DIM, false);
        int y = y0 + TOP + 20;

        // Role
        g.text(font, t("role"), lx, y + 3, DIM, false);
        String role = cam.get("role").getAsString();
        y = cycle(g, y, vx, vr, role.isEmpty() ? t("role_none").getString() : t("role." + role).getString(), mx, my, d -> {
            int i = ROLES.indexOf(role);
            send("role", ROLES.get(((i < 0 ? 0 : i) + (int) Math.round(d) + ROLES.size()) % ROLES.size()));
        }, t("role_tip"));
        // Zoom (field of view): drag / wheel, right-click = the viewer's own
        float fov = cam.get("fov").getAsFloat();
        g.text(font, t("zoom"), lx, y + 2, DIM, false);
        String zv = fov <= 0 ? t("zoom_auto").getString() : Math.round(fov) + "°";
        int tw = font.width(zv);
        zoomX0 = vx;
        zoomX1 = vr - tw - 4;
        float shown = fov <= 0 ? 70 : fov;
        g.fill(zoomX0, y + 4, zoomX1, y + 6, 0x60FFFFFF);
        int knob = zoomX0 + Math.round((zoomX1 - zoomX0) * (shown - CameraSpot.MIN_FOV) / (CameraSpot.MAX_FOV - CameraSpot.MIN_FOV));
        g.fill(zoomX0, y + 4, knob, y + 6, fov <= 0 ? DIM : GOLD);
        g.fill(knob - 1, y + 1, knob + 2, y + 9, 0xFFFFFFFF);
        g.text(font, zv, vr - tw, y + 1, fov <= 0 ? DIM : TEXT, false);
        hits.add(new Hit(zoomX0 - 2, y - 1, zoomX1 + 3, y + 11, () -> draggingZoom = true, () -> send("fov", 0),
                d -> send("fov", Math.max(CameraSpot.MIN_FOV, Math.min(CameraSpot.MAX_FOV, shown - d * 5))), t("zoom_tip")));
        y += 14;
        // Motion
        g.text(font, t("motion"), lx, y + 3, DIM, false);
        String motion = cam.get("motion").getAsString();
        y = cycle(g, y, vx, vr, t("motion." + motion).getString(), mx, my, d -> {
            int i = CameraSpot.MOTIONS.indexOf(motion);
            send("motion", CameraSpot.MOTIONS.get(((i < 0 ? 0 : i) + (int) Math.round(d) + CameraSpot.MOTIONS.size()) % CameraSpot.MOTIONS.size()));
        }, t("motion_tip"));
        // Visible / hidden
        g.text(font, t("visibility"), lx, y + 3, DIM, false);
        boolean hidden = cam.get("hidden").getAsBoolean();
        button(g, vx, y, hidden ? t("hidden") : t("visible"), hidden, mx, my, () -> send("hidden", !hidden), t("hidden_tip"));
        y += 16;
        // Aim: typed (click), where I look
        g.text(font, t("direction"), lx, y + 3, DIM, false);
        String dir = Math.round(cam.get("yaw").getAsFloat()) + "° / " + Math.round(cam.get("pitch").getAsFloat()) + "°";
        if (angleBox == null) {
            boolean dh = mx >= vx && mx < vr && my >= y && my < y + 12;
            if (dh) g.fill(vx - 2, y, vr, y + 12, Theme.HOVER);
            g.text(font, dir, vx, y + 3, dh ? GOLD : TEXT, false);
            int ay = y;
            hits.add(new Hit(vx - 2, y, vr, y + 12, () -> {
                angleBox = new EditBox(font, vx - 2, ay, vr - vx + 2, 12, Component.empty());
                angleBox.setMaxLength(24);
                angleBox.setValue(Math.round(cam.get("yaw").getAsFloat()) + " " + Math.round(cam.get("pitch").getAsFloat()));
                addRenderableWidget(angleBox);
                setFocused(angleBox);
                angleBox.setFocused(true);
            }, null, null, t("angle_tip")));
        }
        y += 18;
        // The two big actions
        int half = (w - 16) / 2;
        bigButton(g, x0 + 6, y, half, t("control"), mx, my, this::control, t("control_tip"));
        bigButton(g, x0 + 10 + half, y, half, t("cut"), mx, my, () -> send("cut", true), t("cut_tip"));
        y += 24;
        // Its travellings, each in its colour (the colour of its ghost and points in the world): click one to edit
        // it in the world (Axiom's gizmos), its pencil for the editor; + for a new one
        g.text(font, t("travellings"), lx, y, GOLD, false);
        y += 11;
        com.google.gson.JsonArray paths = cam.has("paths") ? cam.getAsJsonArray("paths") : new com.google.gson.JsonArray();
        String edited = TravellingEdit.edited(key());
        for (int i = 0; i < paths.size() && i < 5; i++) {
            JsonObject p = paths.get(i).getAsJsonObject();
            String name = p.get("name").getAsString();
            int n = p.get("steps").getAsInt();
            String line = name + "  ·  " + (n <= 1 ? t("fixed_shot").getString() : t("path_line", n, fr.lordfinn.craftmaster.camera.CameraPath.seconds(Math.round(p.get("duration").getAsDouble() * 10) / 10.0)).getString());
            boolean on = name.equals(edited);
            int pen = vr - 16;
            boolean rh = mx >= lx && mx < pen - 2 && my >= y && my < y + 13;
            Ui.button(g, font, lx, y, pen - 2 - lx, 13, Component.empty(), on, rh, true);
            g.fill(lx + 3, y + 3, lx + 10, y + 10, TravellingEdit.colour(i));
            g.text(font, font.plainSubstrByWidth(line, pen - 2 - lx - 18), lx + 14, y + 3, TEXT, false);
            hits.add(new Hit(lx, y, pen - 2, y + 13, () -> TravellingEdit.setEdited(key(), name), null, null, t(on ? "edited_path" : "pick_path")));
            boolean ph = mx >= pen && mx < vr && my >= y && my < y + 13;
            Ui.button(g, font, pen, y, vr - pen, 13, Component.literal("✎"), false, ph, true);
            hits.add(new Hit(pen, y, vr, y + 13, () -> {
                TravellingEdit.setEdited(key(), name);
                travelling("open", name);
            }, null, null, t("edit_path")));
            y += 15;
        }
        button(g, lx, y, t("new_path"), false, mx, my, () -> travelling("create", null), t("new_path_tip"));
        g.text(font, font.plainSubstrByWidth(t("hint").getString(), w - 12), x0 + 6, y0 + h - 11, DIM, false);

        super.extractRenderState(g, mx, my, partial);
        for (Hit hh : hits)
            if (hh.tip != null && mx >= hh.x0 && mx < hh.x1 && my >= hh.y0 && my < hh.y1) {
                g.setTooltipForNextFrame(hh.tip, mx, my);
                break;
            }
    }

    /** Opens the travelling editor (an existing travelling, or a new one starting where the camera stands). */
    private void travelling(String action, String path) {
        JsonObject o = new JsonObject();
        o.addProperty("camera", key());
        if (path != null) o.addProperty("path", path);
        o.addProperty("action", action);
        if (ClientPlayNetworking.canSend(fr.lordfinn.craftmaster.camera.TravellingNetwork.Edit.TYPE))
            ClientPlayNetworking.send(new fr.lordfinn.craftmaster.camera.TravellingNetwork.Edit(o.toString()));
    }

    private void control() {
        var lens = cam.getAsJsonArray("lens");
        ControlView.startCamera(key(), cam.get("name").getAsString(),
                new Vec3(lens.get(0).getAsDouble(), lens.get(1).getAsDouble(), lens.get(2).getAsDouble()),
                cam.get("yaw").getAsFloat(), cam.get("pitch").getAsFloat(), cam.get("fov").getAsFloat());
    }

    private int button(GuiGraphicsExtractor g, int x, int y, Component label, boolean on, int mx, int my, Runnable click, Component tip) {
        int bw = font.width(label) + 10;
        boolean hover = mx >= x && mx < x + bw && my >= y && my < y + 14;
        Ui.button(g, font, x, y, bw, 14, label, on, hover, true);
        hits.add(new Hit(x, y, x + bw, y + 14, click, null, null, tip));
        return x + bw + 2;
    }

    private void bigButton(GuiGraphicsExtractor g, int x, int y, int bw, Component label, int mx, int my, Runnable click, Component tip) {
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

    private void dragZoom(double mx) {
        double f = Math.max(0, Math.min(1, (mx - zoomX0) / Math.max(1, zoomX1 - zoomX0)));
        double v = Math.round(CameraSpot.MIN_FOV + f * (CameraSpot.MAX_FOV - CameraSpot.MIN_FOV));
        long now = System.currentTimeMillis();
        if (now - lastSent >= 100) {
            lastSent = now;
            send("fov", v);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (angleBox != null && !angleBox.isMouseOver(e.x(), e.y())) applyAngle();
        if (super.mouseClicked(e, doubleClick)) return true;
        boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        for (Hit hh : List.copyOf(hits))
            if (e.x() >= hh.x0 && e.x() < hh.x1 && e.y() >= hh.y0 && e.y() < hh.y1) {
                if (right && hh.right != null) hh.right.run();
                else if (!right && hh.click != null) hh.click.run();
                if (draggingZoom) dragZoom(e.x());
                return true;
            }
        if (e.x() < x0 || e.x() > x0 + w || e.y() < y0 || e.y() > y0 + h) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingZoom) {
            dragZoom(e.x());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (draggingZoom) {
            lastSent = 0;
            dragZoom(e.x());
            draggingZoom = false;
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
        boolean enter = e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER;
        if (angleBox != null && angleBox.isFocused()) {
            if (enter) {
                applyAngle();
                return true;
            }
            if (e.key() == InputConstants.KEY_ESCAPE) {
                closeAngle();
                return true;
            }
            return super.keyPressed(e);
        }
        if (nameBox.isFocused() && enter) {
            send("name", nameBox.getValue().strip());
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(e);
    }

    private void applyAngle() {
        String[] n = angleBox.getValue().replace("°", " ").replace("/", " ").replace(";", " ").replace(",", " ").trim().split("\\s+");
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
        if (nameBox != null && !nameBox.getValue().strip().equals(cam.get("name").getAsString())) send("name", nameBox.getValue().strip());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Opens the menu when the server sends a camera, or refreshes it. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(CameraNetwork.Open.TYPE, (p, ctx) -> {
            JsonObject o = com.google.gson.JsonParser.parseString(p.json()).getAsJsonObject();
            // Taking control: the refresh after the new aim must not pull the player out of the game view
            if (ControlView.active()) return;
            if (ctx.client().gui.screen() instanceof CameraScreen s && s.key().equals(o.get("key").getAsString())) s.update(o);
            else ctx.client().gui.setScreen(new CameraScreen(o));
        });
    }
}
