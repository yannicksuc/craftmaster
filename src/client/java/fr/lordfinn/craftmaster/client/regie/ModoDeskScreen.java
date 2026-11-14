package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.client.ui.UiButton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The moderators' director: a grid of buttons like the Stream Deck, one tab per family the moderator has the right to
 * (Caméras, Manche, Points, Journal for the organiser). Every button sends a show action through RunActions; the
 * server checks the right and writes the journal. Points pressed on one player within 0.8 s go as one "+3", like the
 * Stream Deck. TVs and replays get their tabs with their branches.
 */
public class ModoDeskScreen extends Screen {

    private static final int PANEL = 0xE8140E10, BORDER = 0xFFD4A53A, VELVET = 0xFF6B1420, TEXT = 0xFFF1E6C8, DIM = 0xFF9A8A70,
            LIVE = 0xFFE04040, OK = 0xFF7FD67F;

    enum Tab { CAMERAS("cameras"), SCENE("scene"), MANCHE("manche"), POINTS("points"), JOURNAL("journal");
        final String id;
        Tab(String id) { this.id = id; }
    }

    private JsonObject state;
    private long receivedAt;
    /** The state without what moves every second (timer), to rebuild the buttons only when they change. */
    private String layoutKey = "";
    private Tab tab;
    private int left, top, w, h;

    /** Points pressed but not sent yet, per player, and when the last press was. */
    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private final Map<String, Long> pressedAt = new HashMap<>();

    public ModoDeskScreen(JsonObject state) {
        super(Component.translatable("screen.craftmaster.modo_desk"));
        accept(state);
        List<Tab> tabs = tabs();
        tab = tabs.isEmpty() ? Tab.CAMERAS : tabs.getFirst();
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.modo_desk." + key, args);
    }

    private void accept(JsonObject s) {
        state = s;
        receivedAt = System.currentTimeMillis();
    }

    void update(JsonObject s) {
        accept(s);
        JsonObject k = s.deepCopy();
        k.remove("timer");
        k.remove("tick");
        String key = k.toString();
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            if (!tabs().contains(tab) && !tabs().isEmpty()) tab = tabs().getFirst();
            rebuildWidgets();
        }
    }

    private boolean has(String right) {
        if (state.get("admin").getAsBoolean()) return true;
        for (JsonElement e : state.getAsJsonArray("rights")) if (e.getAsString().equals(right)) return true;
        return false;
    }

    private List<Tab> tabs() {
        List<Tab> out = new ArrayList<>();
        if (has("camera")) {
            out.add(Tab.CAMERAS);
            out.add(Tab.SCENE);
        }
        if (has("jeu")) {
            out.add(Tab.MANCHE);
            out.add(Tab.POINTS);
        }
        if (state.get("admin").getAsBoolean()) out.add(Tab.JOURNAL);
        return out;
    }

    private JsonArray array(String key) {
        return state.has(key) && state.get(key).isJsonArray() ? state.getAsJsonArray(key) : new JsonArray();
    }

    // ---------------------------------------------------------------- orders

    private static void run(Object... typeAndFields) {
        JsonObject o = new JsonObject();
        o.addProperty("type", (String) typeAndFields[0]);
        for (int i = 1; i + 1 < typeAndFields.length; i += 2) {
            Object v = typeAndFields[i + 1];
            if (v instanceof Boolean b) o.addProperty((String) typeAndFields[i], b);
            else if (v instanceof Number n) o.addProperty((String) typeAndFields[i], n);
            else o.addProperty((String) typeAndFields[i], String.valueOf(v));
        }
        JsonArray a = new JsonArray();
        a.add(o);
        if (ClientPlayNetworking.canSend(TrialNetwork.RunActions.TYPE)) ClientPlayNetworking.send(new TrialNetwork.RunActions(a.toString()));
    }

    private void points(String player, int delta) {
        pending.merge(player, delta, Integer::sum);
        pressedAt.put(player, System.currentTimeMillis());
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (String p : new ArrayList<>(pending.keySet())) {
            if (now - pressedAt.getOrDefault(p, 0L) < 800) continue;
            int n = pending.remove(p);
            if (n != 0) run("score.add", "player", p, "points", n, "manche", "current");
        }
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(280, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        // Tabs
        int x = left + 6;
        for (Tab tb : tabs()) {
            Component label = t("tab." + tb.id);
            int bw = font.width(label) + 14;
            Button b = Button.builder(tb == tab ? label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : label, btn -> {
                tab = tb;
                rebuildWidgets();
            }).bounds(x, top + 22, bw, 18).build();
            addRenderableWidget(b);
            x += bw + 3;
        }
        int y = top + 46;
        switch (tab) {
            case CAMERAS -> cameras(y);
            case SCENE -> scene(y);
            case MANCHE -> manche(y);
            case POINTS -> pointsTab(y);
            case JOURNAL -> {}
        }
    }

    private Button button(Component label, int x, int y, int bw, Runnable action) {
        Button b = Button.builder(label, btn -> action.run()).bounds(x, y, bw, 20).build();
        addRenderableWidget(b);
        return b;
    }

    /** Where the labels of the Cameras tab go (set by the layout, read when drawing). */
    private int directionLabelY, followLabelY;

    private void cameras(int y) {
        JsonObject shot = state.has("shot") ? state.getAsJsonObject("shot") : new JsonObject();
        String kind = shot.has("kind") ? shot.get("kind").getAsString() : "none";
        String onAir = shot.has("camera") ? shot.get("camera").getAsString() : "";
        String followed = shot.has("player") ? shot.get("player").getAsString() : "";
        // The shots: a preview tile each (5 per row, 16:9), the one on air framed in red
        int cols = 5, gap = 3, tw = (w - 12 - (cols - 1) * gap) / cols, th = tw * 9 / 16, i = 0;
        for (JsonElement e : array("cameras")) {
            JsonObject c = e.getAsJsonObject();
            String key = c.get("key").getAsString();
            String name = c.get("name").getAsString();
            boolean live = kind.equals("camera") && key.equals(onAir);
            ShotTile tile = new ShotTile(left + 6 + (i % cols) * (tw + gap), y + (i / cols) * (th + gap), tw, th, key,
                    c.get("n").getAsInt(), name.isEmpty() ? c.get("role").getAsString() : name, live, () -> run("camera.cut", "camera", key));
            tile.setTooltip(Tooltip.create(Component.literal(c.get("lieu").getAsString())));
            addRenderableWidget(tile);
            if (++i >= 15) break;
        }
        if (i == 0) i = cols;
        y += ((i + cols - 1) / cols) * (th + gap) + 4;
        // How the shots change: a switch and a choice, set apart from the shots
        directionLabelY = y + 6;
        boolean auto = state.has("auto") && state.get("auto").getAsBoolean();
        boolean voice = state.has("voice") && state.get("voice").getAsBoolean();
        String transition = state.has("transition") ? state.get("transition").getAsString() : "coupe";
        int x = left + 66, bw = 62;
        UiButton a = addRenderableWidget(UiButton.of(x, y, bw, 20, t(auto ? "auto_on" : "auto_off"),
                () -> run("camera.auto", "enabled", !auto)).on(() -> auto));
        a.setTooltip(Tooltip.create(t("auto.tip")));
        UiButton v = addRenderableWidget(UiButton.of(x + bw + 3, y, bw, 20, t(voice ? "voice_on" : "voice_off"),
                () -> run("camera.voice", "enabled", !voice)).on(() -> voice));
        v.setTooltip(Tooltip.create(t("voice.tip")));
        int tx = x + 2 * (bw + 3) + 8;
        int tb = (left + w - 6 - tx - 2 * 3) / 3;
        for (String tr : List.of("coupe", "fondu", "enchaine")) {
            UiButton b = addRenderableWidget(UiButton.of(tx, y, tb, 20, t("transition." + tr),
                    () -> run("camera.transition", "transition", tr)).on(() -> tr.equals(transition)));
            b.setTooltip(Tooltip.create(t("transition.tip")));
            tx += tb + 3;
        }
        y += 26;
        followLabelY = y + 6;
        // Follow / POV of a contestant
        JsonArray cast = array("cast");
        int pw = Math.max(60, Math.min(100, (w - 70) / Math.max(1, Math.min(4, cast.size()))));
        int px = left + 66, row = 0;
        for (JsonElement e : cast) {
            String p = e.getAsString();
            if (px + pw > left + w - 6) {
                px = left + 66;
                row++;
                if (row >= 2) break;
            }
            boolean live = (kind.equals("follow") || kind.equals("pov")) && p.equals(followed);
            UiButton b = addRenderableWidget(UiButton.of(px, y + row * 23, pw - 3, 20, Component.literal(p),
                    () -> run("camera.follow", "player", p)).on(() -> live));
            b.setTooltip(Tooltip.create(t("follow.tip")));
            px += pw;
        }
    }

    /** Colours of the quick row: beam colour word (studio/Spotlights) and the swatch drawn on the button. */
    private static final String[][] SWATCHES = {{"chaud", "FFF1D6"}, {"froid", "DDEBFF"}, {"ambre", "FFC46B"}, {"rouge", "FF6060"},
            {"bleu", "7FA0FF"}, {"violet", "C080FF"}, {"vert", "80E080"}, {"rose", "FF9AD0"}};

    private void scene(int y) {
        // Looks: one click plays one (with its own fade)
        int cols = 4, bw = (w - 12 - (cols - 1) * 3) / cols, i = 0;
        for (JsonElement e : array("looks")) {
            String name = e.getAsString();
            Button b = button(Component.literal("◐ " + name), left + 6 + (i % cols) * (bw + 3), y + (i / cols) * 22, bw, () -> run("look.play", "name", name));
            b.setTooltip(Tooltip.create(t("scene.look.tip", name)));
            if (++i >= 8) break;
        }
        y += Math.max(1, (i + cols - 1) / cols) * 22 + 4;
        // Cue lists, then stop and blackout
        i = 0;
        JsonArray cues = array("cues");
        for (JsonElement e : cues) {
            String name = e.getAsString();
            Button b = button(Component.literal("▶ " + name), left + 6 + (i % cols) * (bw + 3), y + (i / cols) * 22, bw, () -> run("cue.play", "name", name));
            b.setTooltip(Tooltip.create(t("scene.cue.tip", name)));
            if (++i >= 6) break;
        }
        int sx = left + 6 + (i % cols) * (bw + 3), sy = y + (i / cols) * 22;
        if (i % cols > cols - 2) {
            sx = left + 6;
            sy += 22;
            i += cols - i % cols;
        }
        button(t("scene.stop").copy().withStyle(ChatFormatting.YELLOW), sx, sy, bw / 2 - 1, () -> run("cue.stop"));
        button(t("scene.noir").copy().withStyle(ChatFormatting.GRAY), sx + bw / 2 + 1, sy, bw / 2 - 1, () -> run("stage.blackout", "fade", 2));
        y = sy + 26;
        // Every spotlight: a colour in one click (1.5 s fade)
        int sw = (w - 12 - 70 - (SWATCHES.length - 1) * 2) / SWATCHES.length;
        int x = left + 6 + 70;
        for (String[] c : SWATCHES) {
            Button b = button(Component.literal("■").withStyle(st -> st.withColor(Integer.parseInt(c[1], 16))), x, y, sw, () -> run("spotlight.set", "on", true, "color", c[0], "fade", 1.5));
            b.setTooltip(Tooltip.create(Component.literal(c[0])));
            x += sw + 2;
        }
        y += 24;
        // Follow-spots: who the beams follow by themselves
        int fw = (w - 12 - 70 - 4 * 3) / 5;
        x = left + 6 + 70;
        for (String[] f : new String[][]{{"@scene", "scene.follow.stage"}, {"@moment", "scene.follow.moment"}, {"@candidat", "scene.follow.contestant"},
                {"@proche", "scene.follow.nearest"}, {"", "scene.follow.none"}}) {
            Button b = button(t(f[1]), x, y, fw, () -> run("spotlight.set", "follow", f[0]));
            b.setTooltip(Tooltip.create(t(f[1] + ".tip")));
            x += fw + 3;
        }
    }

    private void manche(int y) {
        int b3 = (w - 12 - 2 * 3) / 3, b4 = (w - 12 - 3 * 3) / 4;
        button(t("next"), left + 6, y, b3, () -> run("manche.start", "manche", "next"));
        button(t("end"), left + 6 + b3 + 3, y, b3, () -> run("manche.end"));
        button(t("timer_stop"), left + 6 + 2 * (b3 + 3), y, b3, () -> run("timer.stop"));
        y += 26;
        int tx = left + 6;
        for (String o : List.of("start", "pause", "reset", "restart")) {
            Button b = button(t("tablet." + o), tx, y, b4, () -> run("tablet." + o));
            b.setTooltip(Tooltip.create(t("tablet.tip")));
            tx += b4 + 3;
        }
        y += 34;
        int cols = 3, bw = (w - 12 - (cols - 1) * 3) / cols, i = 0;
        for (JsonElement e : array("manches")) {
            JsonObject m = e.getAsJsonObject();
            String id = m.get("id").getAsString();
            boolean cur = m.get("current").getAsBoolean();
            Component text = cur ? Component.literal("● " + m.get("name").getAsString()).withStyle(ChatFormatting.RED) : Component.literal(m.get("name").getAsString());
            Button b = button(text, left + 6 + (i % cols) * (bw + 3), y + (i / cols) * 23, bw, () -> run("manche.start", "manche", id));
            b.setTooltip(Tooltip.create(t("start.tip")));
            if (++i >= 12) break;
        }
    }

    private void pointsTab(int y) {
        int row = 0;
        for (JsonElement e : array("players")) {
            String p = e.getAsJsonObject().get("name").getAsString();
            int ry = y + row * 22;
            if (ry + 20 > top + h - 6) break;
            int bx = left + w - 6 - 4 * 33;
            for (int d : new int[]{-1, 1, 2, 3}) {
                button(Component.literal(d > 0 ? "+" + d : String.valueOf(d)), bx, ry, 30, () -> points(p, d));
                bx += 33;
            }
            row++;
        }
    }

    // ---------------------------------------------------------------- drawing

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + w, top + h, PANEL);
        g.fill(left, top, left + w, top + 18, VELVET);
        g.outline(left, top, w, h, BORDER);
        g.horizontalLine(left + 1, left + w - 2, top + 18, BORDER);
        g.centeredText(font, t("title").copy().withStyle(ChatFormatting.BOLD), width / 2, top + 5, 0xFFFFE9A8);
        if (state.get("paused").getAsBoolean()) g.text(font, t("paused"), left + w - 6 - font.width(t("paused")), top + 5, LIVE, false);
        switch (tab) {
            case MANCHE -> drawManche(g);
            case POINTS -> drawPoints(g);
            case JOURNAL -> drawJournal(g);
            case SCENE -> drawScene(g);
            case CAMERAS -> {
                g.text(font, t("direction"), left + 8, directionLabelY, DIM, false);
                if (!array("cast").isEmpty()) g.text(font, t("follow"), left + 8, followLabelY, DIM, false);
                if (array("cameras").isEmpty()) g.text(font, t("no_cameras"), left + 8, top + 52, DIM, false);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void drawScene(GuiGraphicsExtractor g) {
        String status = state.has("cueStatus") ? state.get("cueStatus").getAsString() : "";
        g.text(font, font.plainSubstrByWidth(status, w - 16), left + 8, top + h - 14, status.startsWith("Aucune") ? DIM : OK, false);
        if (array("looks").isEmpty() && array("cues").isEmpty())
            g.text(font, t("scene.empty"), left + 8, top + h - 26, DIM, false);
        // Labels of the quick rows (positions follow scene(): same arithmetic)
        int cols = 4;
        int looks = Math.min(8, array("looks").size()), cues = Math.min(6, array("cues").size());
        int y = top + 46 + Math.max(1, (looks + cols - 1) / cols) * 22 + 4;
        int i = cues;
        int sy = y + (i / cols) * 22;
        if (i % cols > cols - 2) sy += 22;
        y = sy + 26;
        g.text(font, t("scene.colors"), left + 8, y + 6, DIM, false);
        g.text(font, t("scene.follow"), left + 8, y + 30, DIM, false);
    }

    private void drawManche(GuiGraphicsExtractor g) {
        int y = top + 46 + 26 + 24;
        Component line;
        if (state.has("manche")) {
            JsonObject m = state.getAsJsonObject("manche");
            line = t("current", m.get("number").getAsInt(), m.get("name").getAsString());
        } else line = t("no_round");
        g.text(font, line, left + 8, y, TEXT, false);
        if (state.has("timer") && state.has("tick")) {
            JsonObject tm = state.getAsJsonObject("timer");
            long left20 = tm.get("endTick").getAsLong() - state.get("tick").getAsLong();
            double secs = Math.max(0, left20 / 20.0 - (System.currentTimeMillis() - receivedAt) / 1000.0);
            String text = (int) secs / 60 + ":" + String.format("%02d", (int) secs % 60);
            g.text(font, Component.literal("⏱ " + text), left + w - 8 - font.width("⏱ " + text), y, secs < 10 ? LIVE : OK, false);
        }
    }

    private void drawPoints(GuiGraphicsExtractor g) {
        int y = top + 46, row = 0;
        if (state.has("manche")) g.text(font, t("points_for", state.getAsJsonObject("manche").get("name").getAsString()), left + 8, top + h - 14, DIM, false);
        else g.text(font, t("no_round_points"), left + 8, top + h - 14, LIVE, false);
        for (JsonElement e : array("players")) {
            JsonObject p = e.getAsJsonObject();
            int ry = y + row * 22;
            if (ry + 20 > top + h - 6) break;
            String name = p.get("name").getAsString();
            boolean online = !p.has("online") || p.get("online").getAsBoolean();
            g.text(font, name, left + 8, ry + 6, online ? TEXT : DIM, false);
            String pts = p.get("round").getAsInt() + "  (" + p.get("total").getAsInt() + ")";
            g.text(font, pts, left + 130, ry + 6, TEXT, false);
            Integer wait = pending.get(name);
            if (wait != null && wait != 0) g.text(font, Component.literal((wait > 0 ? "+" : "") + wait).withStyle(ChatFormatting.GOLD), left + 200, ry + 6, 0xFFFFC23D, false);
            row++;
        }
    }

    private void drawJournal(GuiGraphicsExtractor g) {
        int y = top + 48;
        for (JsonElement e : array("journal")) {
            if (y + 10 > top + h - 4) break;
            JsonObject l = e.getAsJsonObject();
            String text = CLOCK.format(Instant.ofEpochMilli(l.get("at").getAsLong())) + "  " + l.get("actor").getAsString()
                    + " (" + l.get("origin").getAsString() + ")  " + l.get("text").getAsString();
            g.text(font, font.plainSubstrByWidth(text, w - 16), left + 8, y, l.get("ok").getAsBoolean() ? TEXT : LIVE, false);
            y += 11;
        }
        if (array("journal").isEmpty()) g.text(font, t("journal_empty"), left + 8, y, DIM, false);
    }

    @Override
    public void onClose() {
        // Points still waiting go now
        for (Map.Entry<String, Integer> e : pending.entrySet())
            if (e.getValue() != 0) run("score.add", "player", e.getKey(), "points", e.getValue(), "manche", "current");
        pending.clear();
        ModoDeskClient.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
