package fr.lordfinn.craftmaster.client.trial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The judge's results sheet of a round (organisers, key N or /cm epreuve feuille): one line per contestant with
 * their value (typed, said to the voice, or measured: stopwatch, tape, counter, scales), a disqualification
 * toggle, their stopwatch, their place and the points the scale proposes. "Apply the scale" writes those points
 * (score.set) in one undoable change. Everything is an action run by the server, exactly like the voice.
 */
public class ResultsScreen extends Screen {

    private static final int PANEL = 0xE8140E10, BORDER = 0xFFD4A53A, VELVET = 0xFF6B1420, PAPER = 0xFFF1E6C8,
            INK = 0xFF2A1A10, ROW_ALT = 0x14000000, GOLD = 0xFFFFC23D, SILVER = 0xFFD5DCE4, BRONZE = 0xFFD98A4E;
    private static final int ROW = 24;

    private JsonObject sheet;
    private long receivedAt;
    private final Map<String, ItemStack> heads = new HashMap<>();
    /** The value boxes, by player, with the value they were filled with (to send only real changes). */
    private final Map<String, EditBox> values = new LinkedHashMap<>();
    private final Map<String, String> sent = new HashMap<>();
    private final Map<String, Button> watches = new HashMap<>();
    private EditBox scale;
    private int left, top, w, h, listTop;

    public ResultsScreen(String json) {
        super(Component.translatable("screen.craftmaster.results"));
        accept(json);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.results." + key, args);
    }

    private void accept(String json) {
        sheet = JsonParser.parseString(json).getAsJsonObject();
        receivedAt = System.currentTimeMillis();
    }

    public String mancheId() {
        return sheet.getAsJsonObject("manche").get("id").getAsString();
    }

    /** A fresh copy from the server: shown at once, what is being typed is kept. */
    public void update(String json) {
        Map<String, String> typing = new HashMap<>();
        String focused = null;
        for (Map.Entry<String, EditBox> e : values.entrySet()) {
            if (e.getValue().isFocused()) focused = e.getKey();
            if (!e.getValue().getValue().equals(sent.get(e.getKey()))) typing.put(e.getKey(), e.getValue().getValue());
        }
        boolean scaleFocused = scale != null && scale.isFocused();
        String scaleText = scale == null ? null : scale.getValue();
        accept(json);
        rebuildWidgets();
        typing.forEach((p, v) -> {
            if (values.containsKey(p)) values.get(p).setValue(v);
        });
        if (focused != null && values.containsKey(focused)) setFocused(values.get(focused));
        if (scaleFocused) {
            scale.setValue(scaleText);
            setFocused(scale);
        }
    }

    private List<JsonObject> rows() {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : sheet.getAsJsonArray("rows")) out.add(e.getAsJsonObject());
        return out;
    }

    private ResultSheet.Kind kind() {
        ResultSheet.Kind k = ResultSheet.Kind.parse(sheet.get("kind").getAsString());
        return k == null ? ResultSheet.Kind.COUNT : k;
    }

    // ---------------------------------------------------------------- actions

    private void run(Map<String, Object> action) {
        action.putIfAbsent("manche", mancheId());
        JsonArray a = new JsonArray();
        JsonObject o = new JsonObject();
        action.forEach((k, v) -> {
            if (v instanceof Boolean b) o.addProperty(k, b);
            else if (v instanceof Number n) o.addProperty(k, n);
            else o.addProperty(k, String.valueOf(v));
        });
        a.add(o);
        ClientPlayNetworking.send(new TrialNetwork.RunActions(a.toString()));
    }

    private static Map<String, Object> action(String type, Object... fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i + 1 < fields.length; i += 2) m.put((String) fields[i], fields[i + 1]);
        return m;
    }

    /** Sends a value box if it changed: a value, or nothing = the line is cleared. */
    private void commit(String player) {
        EditBox b = values.get(player);
        if (b == null) return;
        String v = b.getValue().trim();
        if (v.equals(sent.get(player))) return;
        sent.put(player, v);
        if (v.isEmpty()) run(action("result.remove", "player", player));
        else run(action("result.set", "player", player, "value", v));
    }

    private void commitAll() {
        for (String p : new ArrayList<>(values.keySet())) commit(p);
    }

    private void changeManche(int delta) {
        JsonArray list = sheet.getAsJsonArray("manches");
        String id = mancheId();
        int i = 0;
        for (int k = 0; k < list.size(); k++) if (list.get(k).getAsJsonObject().get("id").getAsString().equals(id)) i = k;
        int j = Math.max(0, Math.min(list.size() - 1, i + delta));
        if (j == i) return;
        commitAll();
        ClientPlayNetworking.send(new TrialNetwork.RequestSheet(list.get(j).getAsJsonObject().get("id").getAsString()));
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        values.clear();
        sent.clear();
        watches.clear();
        List<JsonObject> rows = rows();
        w = Math.min(440, width - 16);
        listTop = 78;
        h = Math.min(height - 12, listTop + Math.max(4, rows.size()) * ROW + 40);
        left = (width - w) / 2;
        top = (height - h) / 2;

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> changeManche(-1)).tooltip(Tooltip.create(t("prev")))
                .bounds(left + 6, top + 22, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> changeManche(1)).tooltip(Tooltip.create(t("next")))
                .bounds(left + w - 24, top + 22, 18, 18).build());

        // Settings: what is measured, which way wins, the scale
        int y = top + 46;
        List<ResultSheet.Kind> kinds = Arrays.asList(ResultSheet.Kind.values());
        CycleButton<ResultSheet.Kind> kind = CycleButton.<ResultSheet.Kind>builder(k -> Component.translatable("screen.craftmaster.results.kind." + k.id), kind())
                .withValues(kinds).create(left + 8, y, 136, 20, t("kind"), (b, v) -> run(action("results.config", "kind", v.id)));
        kind.setTooltip(Tooltip.create(t("kind.tip")));
        addRenderableWidget(kind);
        boolean lower = sheet.get("lowerWins").getAsBoolean();
        CycleButton<Boolean> order = CycleButton.booleanBuilder(t("lower"), t("higher"), lower).displayOnlyValue()
                .create(left + 148, y, 120, 20, t("order"), (b, v) -> run(action("results.config", "order", v ? "lower" : "higher")));
        order.setTooltip(Tooltip.create(t("order.tip")));
        addRenderableWidget(order);
        scale = new EditBox(font, left + w - 98, y, 90, 20, t("scale"));
        scale.setValue(sheet.get("scale").getAsString().replace(",", "-"));
        scale.setTooltip(Tooltip.create(t("scale.tip")));
        addRenderableWidget(scale);

        // One line per contestant: value, disqualified, stopwatch
        int ry = top + listTop;
        for (JsonObject r : rows) {
            String player = r.get("player").getAsString();
            boolean dq = r.get("dq").getAsBoolean();
            String text = r.has("value") ? editable(r.get("value").getAsDouble()) : "";
            EditBox b = new EditBox(font, left + 150, ry + 2, 80, 20, Component.literal(player));
            b.setMaxLength(16);
            b.setValue(text);
            b.setHint(t("value_hint").copy().withStyle(ChatFormatting.DARK_GRAY));
            b.setTooltip(Tooltip.create(t("value.tip")));
            addRenderableWidget(b);
            values.put(player, b);
            sent.put(player, text);
            addRenderableWidget(Button.builder(t(dq ? "dq_on" : "dq_off").copy().withStyle(dq ? ChatFormatting.RED : ChatFormatting.GRAY),
                            x -> run(action("result.dq", "player", player, "dq", !dq)))
                    .tooltip(Tooltip.create(t("dq.tip"))).bounds(left + 234, ry + 2, 34, 20).build());
            boolean running = r.get("running").getAsLong() >= 0;
            Button watch = addRenderableWidget(Button.builder(Component.literal(running ? "■" : "⏱"),
                            x -> run(action(running ? "stopwatch.stop" : "stopwatch.start", "player", player)))
                    .tooltip(Tooltip.create(t(running ? "watch_stop.tip" : "watch_start.tip"))).bounds(left + 272, ry + 2, 20, 20).build());
            watches.put(player, watch);
            ry += ROW;
        }

        int by = top + h - 28;
        Button apply = addRenderableWidget(Button.builder(t("apply").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), b -> {
            commitAll();
            String sc = scale.getValue().trim();
            if (!sc.isEmpty() && !sc.replace("-", ",").equals(sheet.get("scale").getAsString())) run(action("results.apply", "scale", sc));
            else run(action("results.apply"));
        }).tooltip(Tooltip.create(t("apply.tip"))).bounds(left + 8, by, 170, 20).build());
        apply.active = rows.stream().anyMatch(r -> r.has("points"));
        addRenderableWidget(Button.builder(t("clear"), b -> run(action("results.clear"))).tooltip(Tooltip.create(t("clear.tip")))
                .bounds(left + 182, by, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 88, by, 80, 20).build());
    }

    /** 42.5 → "42,5" ; 62.5 seconds → "1:02,5" (how it is typed back). */
    private String editable(double v) {
        if (kind() == ResultSheet.Kind.TIME && v >= 60) return Stopwatch.format(Math.round(v * 1000));
        return ResultSheet.decimal(v, 2);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            if (scale != null && scale.isFocused()) {
                run(action("results.config", "scale", scale.getValue().trim()));
                return true;
            }
            for (Map.Entry<String, EditBox> e : values.entrySet())
                if (e.getValue().isFocused()) {
                    commit(e.getKey());
                    // Next line, like a spreadsheet
                    List<String> order = new ArrayList<>(values.keySet());
                    int i = order.indexOf(e.getKey());
                    if (i + 1 < order.size()) setFocused(values.get(order.get(i + 1)));
                    return true;
                }
        }
        return super.keyPressed(event);
    }

    @Override
    public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
        // Leaving a value box sends it
        for (Map.Entry<String, EditBox> e : values.entrySet())
            if (e.getValue().isFocused() && e.getValue() != focused) commit(e.getKey());
        super.setFocused(focused);
    }

    @Override
    public void onClose() {
        commitAll();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack head(String player) {
        return heads.computeIfAbsent(player, p -> {
            ItemStack s = new ItemStack(Items.PLAYER_HEAD);
            s.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(p));
            return s;
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + w, top + h, PANEL);
        g.fill(left, top, left + w, top + 18, VELVET);
        g.outline(left, top, w, h, BORDER);
        g.horizontalLine(left + 1, left + w - 2, top + 18, BORDER);
        g.centeredText(font, t("title").copy().withStyle(ChatFormatting.BOLD), width / 2, top + 5, 0xFFFFE9A8);
        JsonObject m = sheet.getAsJsonObject("manche");
        Component manche = t("manche", m.get("number").getAsInt(), m.get("name").getAsString());
        if (m.get("current").getAsBoolean()) manche = manche.copy().append(t("current").copy().withStyle(ChatFormatting.GREEN));
        g.centeredText(font, manche, width / 2, top + 27, 0xFFFFFFFF);

        // The sheet itself: cream paper
        int px0 = left + 6, px1 = left + w - 6, py0 = top + listTop - 12, py1 = top + h - 34;
        g.fill(px0, py0, px1, py1, PAPER);
        g.outline(px0, py0, px1 - px0, py1 - py0, 0xFF8A6A3A);
        g.text(font, t("col.player"), left + 42, py0 + 2, 0xFF7A5A30, false);
        g.text(font, t("col.value"), left + 152, py0 + 2, 0xFF7A5A30, false);
        g.text(font, t("col.place"), left + 300, py0 + 2, 0xFF7A5A30, false);
        g.text(font, t("col.points"), left + w - 70, py0 + 2, 0xFF7A5A30, false);

        long now = System.currentTimeMillis();
        int ry = top + listTop;
        int i = 0;
        for (JsonObject r : rows()) {
            String player = r.get("player").getAsString();
            if (i++ % 2 == 1) g.fill(px0 + 1, ry, px1 - 1, ry + ROW, ROW_ALT);
            boolean online = r.get("online").getAsBoolean();
            g.item(head(player), left + 12, ry + 4);
            g.text(font, player, left + 32, ry + 8, online ? INK : 0xFF8A7A6A, false);
            long running = r.get("running").getAsLong();
            int place = r.get("place").getAsInt();
            boolean dq = r.get("dq").getAsBoolean();
            String placeText = dq ? "DQ" : place == 0 ? "—" : "#" + place;
            int color = dq ? 0xFFC02020 : place == 1 ? 0xFFB8860B : place == 2 ? 0xFF6E7C8A : place == 3 ? 0xFFA0522D : INK;
            g.text(font, placeText, left + 302, ry + 8, color, false);
            // A running stopwatch shows its time live where the value goes
            if (running >= 0) g.text(font, "⏱ " + Stopwatch.format(running + (now - receivedAt)), left + 326, ry + 8, 0xFF1E7A2E, false);
            if (r.has("points")) {
                String pts = "+" + r.get("points").getAsInt();
                g.text(font, Component.literal(pts).withStyle(ChatFormatting.BOLD), left + w - 62, ry + 8, 0xFF9A6A00, false);
            }
            if (r.has("score")) {
                String sc = "(" + r.get("score").getAsInt() + ")";
                g.text(font, sc, left + w - 14 - font.width(sc), ry + 8, 0xFF8A7A6A, false);
            }
            ry += ROW;
        }
        if (rows().isEmpty()) g.centeredText(font, t("empty"), width / 2, top + listTop + 20, 0xFF7A5A30);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
