package fr.lordfinn.craftmaster.client.score;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.client.ui.DigitHead;
import fr.lordfinn.craftmaster.client.ui.PlayerFrame;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import fr.lordfinn.craftmaster.client.ui.UiSounds;
import fr.lordfinn.craftmaster.client.ui.UiTabs;
import fr.lordfinn.craftmaster.data.ScoreBoard;
import fr.lordfinn.craftmaster.data.ScoreEntryBoard;
import fr.lordfinn.craftmaster.network.ScoreEntryNetwork;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The referee's score entry screen (organisers: key U, /craftMasterScores, StreamDeck /menu).
 *
 * <p>Two views. <b>Round</b>: the players of one round side by side, best first, each with +1 above and -1 below
 * his frame (flat face, score as a digit head). Click a face to pick the player up (his face follows the mouse),
 * click another face to swap them; Shift-click sends a player to the top; +1 / -1 only change that player (see
 * {@link ScoreBoard} for the rule). With a player picked up, +1 / -1 on another place moves him there first (the
 * others shift), then applies the bonus. <b>Board</b>: rounds × players (8 at most), left click +1, right click -1
 * on a cell; a round's number opens it in the round view.
 *
 * <p>Nothing is sent before « Mettre à jour »: then every changed score goes as one list of {@code score.set}
 * (one undoable change). The server pushes the board again after every change and the screen updates in place.
 */
public class ScoreEntryScreen extends Screen {

    private enum View { BOARD, ROUND }

    private static final int FACE = 32, COL = 58, MIN_COL = 42, CELL = 20, ROW = 22;

    private JsonObject board;
    private final List<String> players = new ArrayList<>();
    private final List<JsonObject> manches = new ArrayList<>();
    /** Local boards of the rounds being edited (created when a round is shown or edited). */
    private final Map<String, ScoreBoard> boards = new HashMap<>();
    /** Rounds the referee actually changed (a round only looked at is not sent unless shown when updating). */
    private final Set<String> touched = new HashSet<>();
    private View view = View.ROUND;
    private int round;
    /** Index of the player picked up in the round view, or -1. */
    private int held = -1;
    /** Scores were just sent: the next board from the server replaces the local edits. */
    private boolean sent;
    private int scroll;
    private int left, top, w, h;

    public ScoreEntryScreen(String json) {
        super(t("title"));
        accept(json);
        // Open on the round being played, if any
        String current = board.has("current") && !board.get("current").isJsonNull() ? board.get("current").getAsString() : null;
        for (int i = 0; i < manches.size(); i++) if (manches.get(i).get("id").getAsString().equals(current)) round = i;
        // No round being played: the board of all rounds first
        if (manches.isEmpty() || current == null) view = View.BOARD;
    }

    static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.scores." + key, args);
    }

    public int season() {
        return board.get("season").getAsInt();
    }

    private static JsonObject last;

    /** The last board received (undo / redo counts for the StreamDeck), or null. */
    public static JsonObject lastBoard() {
        return last;
    }

    private void accept(String json) {
        board = JsonParser.parseString(json).getAsJsonObject();
        last = board;
        players.clear();
        for (JsonElement e : board.getAsJsonArray("players")) if (players.size() < ScoreEntryBoard.MAX_PLAYERS) players.add(e.getAsString());
        manches.clear();
        for (JsonElement e : board.getAsJsonArray("manches")) manches.add(e.getAsJsonObject());
        round = Math.max(0, Math.min(round, manches.size() - 1));
    }

    /** A fresh board from the server: shown at once, in place; local edits are kept unless they were just sent. */
    public void update(String json) {
        int before = season();
        accept(json);
        if (sent || season() != before) {
            boards.clear();
            touched.clear();
            held = -1;
            sent = false;
        } else {
            // Rounds not edited follow the server; the edited ones keep what the referee is doing
            boards.keySet().removeIf(id -> !touched.contains(id) && !id.equals(shownRound()));
            boards.keySet().removeIf(id -> manches.stream().noneMatch(m -> m.get("id").getAsString().equals(id)));
            if (shownRound() != null && !touched.contains(shownRound())) {
                boards.remove(shownRound());
                held = -1;
            }
        }
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- model

    private JsonObject manche(String id) {
        for (JsonObject m : manches) if (m.get("id").getAsString().equals(id)) return m;
        return null;
    }

    private String shownRound() {
        return view == View.ROUND && !manches.isEmpty() ? manches.get(round).get("id").getAsString() : null;
    }

    private Map<String, Integer> serverScores(JsonObject m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("scores").entrySet()) out.put(e.getKey(), e.getValue().getAsInt());
        return out;
    }

    /**
     * The local board of a round. In the round view an unscored round starts from its scale (the places give the
     * points); edited from the board view it starts from 0.
     */
    private ScoreBoard boardOf(String id, boolean withScale) {
        return boards.computeIfAbsent(id, k -> {
            JsonObject m = manche(k);
            int[] scale = new int[0];
            if (withScale) {
                JsonArray a = m.getAsJsonArray("scale");
                scale = new int[a.size()];
                for (int i = 0; i < a.size(); i++) scale[i] = a.get(i).getAsInt();
            }
            return ScoreBoard.of(players, serverScores(m), scale);
        });
    }

    private ScoreBoard roundBoard() {
        return manches.isEmpty() ? null : boardOf(shownRound(), true);
    }

    /** Boards to send: the edited rounds, and the round on screen. */
    private Map<String, Map<String, Integer>> pending() {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (JsonObject m : manches) {
            String id = m.get("id").getAsString();
            ScoreBoard b = boards.get(id);
            if (b == null || !(touched.contains(id) || id.equals(shownRound()))) continue;
            Map<String, Integer> c = b.changes();
            if (!c.isEmpty()) out.put(id, c);
        }
        return out;
    }

    private int pendingCount() {
        return pending().values().stream().mapToInt(Map::size).sum();
    }

    // ---------------------------------------------------------------- server

    private static void runActions(JsonArray actions) {
        if (actions.isEmpty() || !ClientPlayNetworking.canSend(TrialNetwork.RunActions.TYPE)) return;
        ClientPlayNetworking.send(new TrialNetwork.RunActions(actions.toString()));
    }

    /** « Mettre à jour »: every changed score as one list of score.set (one undoable change). */
    private void sendScores() {
        JsonArray actions = new JsonArray();
        pending().forEach((manche, changes) -> changes.forEach((player, value) -> {
            JsonObject a = new JsonObject();
            a.addProperty("type", "score.set");
            a.addProperty("season", season());
            a.addProperty("manche", manche);
            a.addProperty("player", player);
            a.addProperty("value", value);
            actions.add(a);
        }));
        if (actions.isEmpty()) return;
        sent = true;
        held = -1;
        UiSounds.confirm();
        runActions(actions);
    }

    static void simple(String type) {
        JsonArray actions = new JsonArray();
        JsonObject a = new JsonObject();
        a.addProperty("type", type);
        actions.add(a);
        runActions(actions);
    }

    private void changeSeason(int delta) {
        JsonArray list = board.getAsJsonArray("seasons");
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).getAsInt() == season()) {
                int j = i + delta;
                if (j >= 0 && j < list.size()) {
                    UiSounds.page();
                    ClientPlayNetworking.send(new ScoreEntryNetwork.RequestBoard(list.get(j).getAsInt(), false));
                }
                return;
            }
    }

    /** StreamDeck and keys: next / previous season. */
    public void seasonStep(int delta) {
        if (pendingCount() == 0) changeSeason(delta);
        else UiSounds.error();
    }

    private void changeRound(int delta) {
        int j = Math.max(0, Math.min(manches.size() - 1, round + delta));
        if (j == round) return;
        leaveRound();
        round = j;
        UiSounds.page();
        rebuildWidgets();
    }

    /** A round only looked at (scale proposal never changed) is forgotten when leaving it. */
    private void leaveRound() {
        String id = shownRound();
        if (id != null && !touched.contains(id)) boards.remove(id);
        held = -1;
    }

    private void showRound(int index) {
        leaveRound();
        view = View.ROUND;
        round = index;
        scroll = 0;
        UiSounds.page();
        rebuildWidgets();
    }

    private void showBoard() {
        leaveRound();
        view = View.BOARD;
        UiSounds.page();
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int cols = Math.max(4, players.size());
        w = Math.min(width - 8, Math.max(380, 30 + cols * COL));
        h = Math.min(height - 8, 250);
        left = (width - w) / 2;
        top = (height - h) / 2;

        // Season
        addRenderableWidget(UiButton.of(left + 6, top + 22, 16, 16, Component.literal("◀"), () -> seasonStep(-1)))
                .setTooltip(Tooltip.create(t("season_prev")));
        addRenderableWidget(UiButton.of(left + 128, top + 22, 16, 16, Component.literal("▶"), () -> seasonStep(1)))
                .setTooltip(Tooltip.create(t("season_next")));
        // Views
        UiTabs tabs = new UiTabs(font, left + w - 6 - 150, top + 22, 16, List.of(t("tab.board"), t("tab.round")),
                () -> view == View.BOARD ? 0 : 1, i -> {
                    if (i == 0 && view != View.BOARD) showBoard();
                    else if (i == 1 && view != View.ROUND && !manches.isEmpty()) showRound(round);
                });
        int shift = left + w - 6 - tabs.width() - tabs.buttons().getFirst().getX();
        for (UiButton b : tabs.buttons()) {
            b.setX(b.getX() + shift);
            addRenderableWidget(b);
        }

        if (view == View.ROUND && !manches.isEmpty()) {
            addRenderableWidget(UiButton.of(left + 6, top + 42, 16, 16, Component.literal("◀"), () -> changeRound(-1)))
                    .setTooltip(Tooltip.create(t("round_prev")));
            addRenderableWidget(UiButton.of(left + w - 22, top + 42, 16, 16, Component.literal("▶"), () -> changeRound(1)))
                    .setTooltip(Tooltip.create(t("round_next")));
            int x0 = columnsLeft();
            for (int i = 0; i < roundBoard().size(); i++) {
                int index = i;
                int cx = x0 + i * col() + (col() - 30) / 2;
                addRenderableWidget(new UiButton(cx, plusY(), 30, 14, Component.literal("+1").withStyle(ChatFormatting.AQUA), in -> bump(index, 1)).silent())
                        .setTooltip(Tooltip.create(t("plus.tip")));
                addRenderableWidget(new UiButton(cx, minusY(), 30, 14, Component.literal("-1").withStyle(ChatFormatting.GOLD), in -> bump(index, -1)).silent())
                        .setTooltip(Tooltip.create(t("minus.tip")));
            }
        }

        int by = top + h - 24;
        UiButton update = addRenderableWidget(new UiButton(left + 6, by, 130, 18, t("update").copy().withStyle(ChatFormatting.BOLD), in -> sendScores()).silent());
        update.setTooltip(Tooltip.create(t("update.tip")));
        update.active = pendingCount() > 0;
        UiButton undo = addRenderableWidget(UiButton.of(left + 140, by, 70, 18, t("undo"), () -> simple("undo")));
        undo.active = board.get("undo").getAsInt() > 0;
        undo.setTooltip(Tooltip.create(t("undo.tip")));
        UiButton redo = addRenderableWidget(UiButton.of(left + 214, by, 70, 18, t("redo"), () -> simple("redo")));
        redo.active = board.get("redo").getAsInt() > 0;
        redo.setTooltip(Tooltip.create(t("redo.tip")));
        addRenderableWidget(UiButton.of(left + w - 66, by, 60, 18, Component.translatable("gui.done"), this::onClose));
    }

    /** Column width of the round view: 58, narrower when 8 players must fit in a small window. */
    private int col() {
        int n = roundBoard() == null ? 1 : Math.max(1, roundBoard().size());
        return Math.max(MIN_COL, Math.min(COL, (w - 12) / n));
    }

    private int columnsLeft() {
        int n = roundBoard() == null ? 0 : roundBoard().size();
        return left + (w - n * col()) / 2;
    }

    private int plusY() {
        return top + 76;
    }

    private int frameY() {
        return plusY() + 18;
    }

    private int minusY() {
        return frameY() + PlayerFrame.height(FACE, true) + 2;
    }

    private void bump(int index, int delta) {
        ScoreBoard b = roundBoard();
        if (b == null) return;
        int score;
        if (held >= 0 && held != index) {
            score = b.moveAndBump(held, index, delta);
            UiSounds.drop();
        } else {
            score = b.bump(index, delta);
        }
        held = -1;
        touched.add(shownRound());
        UiSounds.score(score, delta > 0);
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        double mx = e.x(), my = e.y();
        if (view == View.ROUND) return clickRound(e, mx, my);
        return clickBoard(e, mx, my);
    }

    private boolean clickRound(MouseButtonEvent e, double mx, double my) {
        ScoreBoard b = roundBoard();
        if (b == null) return false;
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            // Right click: put back the player being moved
            if (held >= 0) {
                held = -1;
                UiSounds.drop();
                return true;
            }
            return false;
        }
        int x0 = columnsLeft();
        for (int i = 0; i < b.size(); i++) {
            int fx = x0 + i * col() + (col() - FACE - 4) / 2;
            if (!Ui.inside(mx, my, fx - 4, frameY() - 2, FACE + 12, PlayerFrame.height(FACE, false) + 4)) continue;
            if (e.hasShiftDown()) {
                b.toFront(held >= 0 ? held : i);
                held = -1;
                UiSounds.drop();
            } else if (held < 0) {
                held = i;
                UiSounds.pick();
            } else if (held == i) {
                held = -1;
                UiSounds.drop();
            } else {
                b.swap(held, i);
                held = -1;
                UiSounds.drop();
            }
            touched.add(shownRound());
            rebuildWidgets();
            return true;
        }
        return false;
    }

    private int gridTop() {
        return top + 82;
    }

    private int gridLeft() {
        return left + 8 + 150;
    }

    private int visibleRows() {
        return Math.max(1, (top + h - 40 - gridTop()) / ROW);
    }

    private boolean clickBoard(MouseButtonEvent e, double mx, double my) {
        int rows = visibleRows();
        for (int r = 0; r < rows && r + scroll < manches.size(); r++) {
            int y = gridTop() + r * ROW;
            int index = r + scroll;
            // The round's number opens the round view
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && Ui.inside(mx, my, left + 10, y + 1, 20, 20)) {
                showRound(index);
                return true;
            }
            for (int c = 0; c < players.size(); c++) {
                int x = gridLeft() + c * (CELL + 8);
                if (!Ui.inside(mx, my, x, y + 1, CELL, CELL)) continue;
                String id = manches.get(index).get("id").getAsString();
                ScoreBoard b = boardOf(id, false);
                int delta = e.button() == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;
                int score = b.bump(b.indexOf(players.get(c)), delta);
                touched.add(id);
                UiSounds.score(score, delta > 0);
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (view == View.BOARD) {
            scroll = Math.max(0, Math.min(Math.max(0, manches.size() - visibleRows()), scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public void removed() {
        if (ClientPlayNetworking.canSend(ScoreEntryNetwork.RequestBoard.TYPE))
            ClientPlayNetworking.send(new ScoreEntryNetwork.RequestBoard(ScoreEntryNetwork.CLOSED, false));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.panel(g, font, left, top, w, h, t("title").copy().withStyle(ChatFormatting.BOLD));
        String name = board.get("name").getAsString();
        g.centeredText(font, Component.literal(name), left + 75, top + 26, Theme.TEXT);
        if (players.isEmpty()) {
            g.centeredText(font, t("no_players"), width / 2, top + 100, Theme.TEXT_DIM);
        } else if (manches.isEmpty()) {
            g.centeredText(font, t("no_rounds"), width / 2, top + 100, Theme.TEXT_DIM);
        } else if (view == View.ROUND) {
            drawRound(g, mouseX, mouseY);
        } else {
            drawBoard(g, mouseX, mouseY);
        }
        int count = pendingCount();
        if (count > 0) g.centeredText(font, t("pending", count), width / 2, top + h - 36, Theme.GOLD);
        int hidden = board.getAsJsonArray("players").size() - players.size();
        if (hidden > 0) g.text(font, t("too_many", hidden), left + 150, top + 26, Theme.TEXT_DIM, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
        // The player being moved follows the mouse, above everything
        ScoreBoard b = view == View.ROUND ? roundBoard() : null;
        if (b != null && held >= 0 && held < b.size()) {
            g.fill(mouseX - FACE / 2 - 2, mouseY - FACE / 2 - 2, mouseX + FACE / 2 + 2, mouseY + FACE / 2 + 2, Theme.GOLD);
            Ui.face(g, b.player(held), mouseX - FACE / 2f, mouseY - FACE / 2f, FACE);
        }
    }

    private void drawRound(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        JsonObject m = manches.get(round);
        Component title = t("round", m.get("number").getAsInt(), m.get("name").getAsString());
        if (m.get("current").getAsBoolean()) title = title.copy().append(t("current").copy().withStyle(ChatFormatting.GREEN));
        g.centeredText(font, title, width / 2, top + 46, Theme.TEXT);
        ScoreBoard b = roundBoard();
        boolean proposal = m.getAsJsonObject("scores").isEmpty() && !touched.contains(shownRound());
        g.centeredText(font, proposal ? t("scale_hint") : t("hint"), width / 2, top + 60, Theme.TEXT_DIM);
        int x0 = columnsLeft();
        for (int i = 0; i < b.size(); i++) {
            int cx = x0 + i * col();
            String p = b.player(i);
            // Place
            int place = i + 1;
            int color = place == 1 ? Theme.GOLD : place == 2 ? Theme.SILVER : place == 3 ? Theme.BRONZE : Theme.TEXT_DIM;
            g.centeredText(font, "#" + place, cx + col() / 2, plusY() - 10, color);
            int fx = cx + (col() - FACE - 4) / 2;
            if (i == held) {
                // The empty slot of the player being moved
                g.outline(fx, frameY(), FACE + 4, FACE + 4, Theme.GOLD);
                DigitHead.draw(g, font, b.score(i), fx + (FACE + 4 - PlayerFrame.scoreSize(FACE)) / 2, frameY() + FACE + 6, PlayerFrame.scoreSize(FACE));
                continue;
            }
            boolean hover = Ui.inside(mouseX, mouseY, fx - 4, frameY() - 2, FACE + 12, PlayerFrame.height(FACE, false) + 4);
            PlayerFrame.draw(g, font, p, b.score(i), fx, frameY(), FACE, true, hover);
            int mod = b.modifier(p);
            if (mod != 0) {
                String s = DigitHead.label(mod);
                g.text(font, Component.literal(s).withStyle(ChatFormatting.BOLD), fx + FACE + 4 - font.width(s) + 4, frameY() - 4,
                        mod > 0 ? 0xFF7FD8E0 : 0xFFF1BC48, true);
            }
        }
    }

    private void drawBoard(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int px0 = left + 6, px1 = left + w - 6, py0 = gridTop() - 22, py1 = top + h - 40;
        Ui.paper(g, px0, py0, px1 - px0, py1 - py0);
        g.text(font, t("col.round"), left + 36, py0 + 7, Theme.INK_SOFT, false);
        for (int c = 0; c < players.size(); c++) {
            int x = gridLeft() + c * (CELL + 8);
            Ui.face(g, players.get(c), x + 2, py0 + 3, 16);
            if (Ui.inside(mouseX, mouseY, x, py0 + 3, CELL, 16)) g.setTooltipForNextFrame(font, Component.literal(players.get(c)), mouseX, mouseY);
        }
        int rows = visibleRows();
        for (int r = 0; r < rows && r + scroll < manches.size(); r++) {
            int y = gridTop() + r * ROW;
            JsonObject m = manches.get(r + scroll);
            String id = m.get("id").getAsString();
            if (m.get("current").getAsBoolean()) g.fill(px0 + 1, y, px1 - 1, y + ROW, 0x2A1E7A2E);
            else if (r % 2 == 1) g.fill(px0 + 1, y, px1 - 1, y + ROW, 0x14000000);
            // The round's icon: a gold block with its number, opens the round view
            boolean hover = Ui.inside(mouseX, mouseY, left + 10, y + 1, 20, 20);
            g.fill(left + 10, y + 1, left + 30, y + 21, hover ? Theme.shade(Theme.GOLD, 0.3f) : Theme.GOLD);
            g.outline(left + 10, y + 1, 20, 20, Theme.BORDER_DARK);
            g.centeredText(font, String.valueOf(m.get("number").getAsInt()), left + 20, y + 7, Theme.INK);
            if (hover) g.setTooltipForNextFrame(font, t("open_round"), mouseX, mouseY);
            String name = font.plainSubstrByWidth(m.get("name").getAsString(), gridLeft() - left - 44);
            g.text(font, name, left + 36, y + 7, Theme.INK, false);
            ScoreBoard b = boards.get(id);
            Map<String, Integer> server = serverScores(m);
            Map<String, Integer> changes = b == null ? Map.of() : b.changes();
            for (int c = 0; c < players.size(); c++) {
                String p = players.get(c);
                int x = gridLeft() + c * (CELL + 8);
                Integer score = b != null ? Integer.valueOf(b.score(b.indexOf(p))) : server.get(p);
                if (score == null || (b != null && !server.containsKey(p) && !changes.containsKey(p))) {
                    g.centeredText(font, "·", x + CELL / 2, y + 7, Theme.INK_SOFT);
                } else {
                    DigitHead.draw(g, font, score, x, y + 1, CELL);
                    if (changes.containsKey(p)) g.outline(x - 1, y, CELL + 2, CELL + 2, Theme.GOLD);
                }
                if (Ui.inside(mouseX, mouseY, x, y + 1, CELL, CELL)) g.outline(x - 1, y, CELL + 2, CELL + 2, 0xFF000000);
            }
        }
        if (manches.size() > rows) g.text(font, (scroll + 1) + "–" + Math.min(manches.size(), scroll + rows) + " / " + manches.size(),
                px1 - 40, py0 + 7, Theme.INK_SOFT, false);
        g.centeredText(font, t("board_hint"), width / 2, top + 45, Theme.TEXT_DIM);
    }
}
