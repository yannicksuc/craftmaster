package fr.lordfinn.craftmaster.client.score;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.ui.DigitHead;
import fr.lordfinn.craftmaster.client.ui.ScoreBadge;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The referee's score entry (organisers: key U, /craftMasterScores, StreamDeck /menu). Design:
 * docs/design/saisie-ux.md (phase 1).
 *
 * <p><b>One round</b>: the places are <b>steps</b> with their base painted on them ("1st · 5"); each player is a
 * <b>card</b> standing on a step, his bonus a <b>token</b> under the card that follows him; under each step, the
 * score (step + token). A round never scored opens with the players on the <b>bench</b>: click them in the order
 * they finished. Drag a card to insert it elsewhere (a gold marker shows where, the others make room), onto the
 * bench to take it out of the ranking. Wheel / ▲▼ / right click: ±1 for that player only. <b>All rounds</b>: rounds
 * × players, left click +1, right click -1; a round's number opens it.
 *
 * <p>Everything is a draft (hatched yellow) until « Valider la manche » (Enter): one list of score.set, one undo
 * step. Ctrl+Z / Ctrl+Y undo / redo gestures of the draft; « ↶ Annuler : … » says what it will undo on the server.
 * The draft survives closing the screen (Escape) until it is validated, forgotten or the game disconnects.
 */
public class ScoreEntryScreen extends Screen {

    private enum View { BOARD, ROUND }

    private static final int FACE = 28, MAX_COL = 52, MIN_COL = 40, CELL = 20, ROW = 22;

    // ---------------------------------------------------------------- drafts (kept when the screen closes)

    private static final class Drafts {
        int season = -1;
        final Map<String, ScoreBoard> boards = new HashMap<>();
        /** Rounds in the order of the gestures (to undo the last one from the board view). */
        final Deque<String> gestures = new ArrayDeque<>();
        final Map<String, String> names = new HashMap<>();
    }

    private static Drafts drafts = new Drafts();
    private static JsonObject last;

    /** Forget every draft (disconnect). */
    public static void clearDrafts() {
        drafts = new Drafts();
        last = null;
    }

    /** The name of a round with an unsent draft, or null (the HUD reminds the organiser). */
    public static String draftRoundName() {
        for (Map.Entry<String, ScoreBoard> e : drafts.boards.entrySet())
            if (e.getValue().dirty()) return drafts.names.getOrDefault(e.getKey(), e.getKey());
        return null;
    }

    /** The last board received (undo / redo counts for the StreamDeck), or null. */
    public static JsonObject lastBoard() {
        return last;
    }

    // ---------------------------------------------------------------- state

    private JsonObject board;
    private final List<String> players = new ArrayList<>();
    private final List<JsonObject> manches = new ArrayList<>();
    private View view = View.ROUND;
    private int round, scroll;
    private boolean sent;
    private int sentCount;
    private Component status;
    private long statusAt;
    private String selected;
    // Drag in progress: the card held, from the bench or a step, where it was pressed
    private String dragged;
    private boolean fromBench, dragging;
    private double pressX, pressY, mouseX, mouseY;
    /** Animated x of each card (centre), easing towards its slot. */
    private final Map<String, Float> cardX = new HashMap<>();
    private long lastFrame;
    private int left, top, w, h;

    public ScoreEntryScreen(String json) {
        super(t("title"));
        accept(json);
        String current = board.has("current") && !board.get("current").isJsonNull() ? board.get("current").getAsString() : null;
        for (int i = 0; i < manches.size(); i++) if (manches.get(i).get("id").getAsString().equals(current)) round = i;
        // No round being played: the board of all rounds first
        if (manches.isEmpty() || current == null) view = View.BOARD;
    }

    /** Right edge of the "Publish" button (the hint starts after it). */
    private int publishRight;

    static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.scores." + key, args);
    }

    public int season() {
        return board.get("season").getAsInt();
    }

    private void accept(String json) {
        board = JsonParser.parseString(json).getAsJsonObject();
        last = board;
        if (drafts.season != season()) {
            drafts = new Drafts();
            drafts.season = season();
        }
        players.clear();
        for (JsonElement e : board.getAsJsonArray("players")) if (players.size() < ScoreEntryBoard.MAX_PLAYERS) players.add(e.getAsString());
        manches.clear();
        for (JsonElement e : board.getAsJsonArray("manches")) {
            JsonObject m = e.getAsJsonObject();
            manches.add(m);
            drafts.names.put(m.get("id").getAsString(), m.get("name").getAsString());
        }
        round = Math.max(0, Math.min(round, manches.size() - 1));
    }

    /** A fresh board from the server, shown in place. Rounds with gestures keep their draft unless just validated. */
    public void update(String json) {
        int before = season();
        accept(json);
        if (sent) {
            drafts.boards.clear();
            drafts.gestures.clear();
            setStatus(t("sent", sentCount).copy().withStyle(ChatFormatting.GREEN));
            sent = false;
            selected = null;
        } else if (season() == before) {
            // Rounds nobody touched follow the server (another organiser, the voice, the StreamDeck)
            drafts.boards.entrySet().removeIf(e -> !e.getValue().canUndo() || manche(e.getKey()) == null);
        }
        rebuildWidgets();
    }

    private void setStatus(Component c) {
        status = c;
        statusAt = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- model

    private JsonObject manche(String id) {
        for (JsonObject m : manches) if (m.get("id").getAsString().equals(id)) return m;
        return null;
    }

    private String shownRound() {
        return !manches.isEmpty() ? manches.get(round).get("id").getAsString() : null;
    }

    private Map<String, Integer> serverScores(JsonObject m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("scores").entrySet()) out.put(e.getKey(), e.getValue().getAsInt());
        return out;
    }

    /** The draft of a round (made from the server's scores the first time); free steps take the round's scale. */
    private ScoreBoard boardOf(String id) {
        return drafts.boards.computeIfAbsent(id, k -> {
            JsonObject m = manche(k);
            JsonArray a = m.getAsJsonArray("scale");
            int[] scale = new int[a.size()];
            for (int i = 0; i < a.size(); i++) scale[i] = a.get(i).getAsInt();
            return ScoreBoard.of(players, serverScores(m), scale);
        });
    }

    private ScoreBoard roundBoard() {
        return manches.isEmpty() ? null : boardOf(shownRound());
    }

    private void gesture(String id) {
        drafts.gestures.remove(id);
        drafts.gestures.push(id);
        rebuildWidgets();
    }

    private Map<String, Map<String, Integer>> pending() {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (JsonObject m : manches) {
            String id = m.get("id").getAsString();
            ScoreBoard b = drafts.boards.get(id);
            if (b == null) continue;
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

    /** « Valider la manche »: every draft as one list of score.set (one undo step). */
    private void validate() {
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
        sentCount = actions.size();
        dragged = null;
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

    /** StreamDeck and keys: next / previous season (refused while a draft is pending, with the reason). */
    public void seasonStep(int delta) {
        String draft = draftRoundName();
        if (draft != null) {
            UiSounds.error();
            setStatus(t("draft_blocks", draft).copy().withStyle(ChatFormatting.GOLD));
            return;
        }
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

    private void changeRound(int delta) {
        int j = Math.max(0, Math.min(manches.size() - 1, round + delta));
        if (j == round) return;
        round = j;
        selected = null;
        UiSounds.page();
        rebuildWidgets();
    }

    private void showRound(int index) {
        view = View.ROUND;
        round = index;
        selected = null;
        UiSounds.page();
        rebuildWidgets();
    }

    private void showBoard() {
        view = View.BOARD;
        selected = null;
        UiSounds.page();
        rebuildWidgets();
    }

    private void undoGesture() {
        String id = view == View.ROUND ? shownRound() : drafts.gestures.peek();
        ScoreBoard b = id == null ? null : drafts.boards.get(id);
        if (b != null && b.undo()) {
            UiSounds.drop();
            if (!b.canUndo()) drafts.gestures.remove(id);
        } else UiSounds.error();
        rebuildWidgets();
    }

    private void redoGesture() {
        String id = view == View.ROUND ? shownRound() : drafts.gestures.peek();
        ScoreBoard b = id == null ? null : drafts.boards.get(id);
        if (b != null && b.redo()) UiSounds.pick();
        else UiSounds.error();
        rebuildWidgets();
    }

    /** Forget the draft of every round of this season. */
    private void forgetDraft() {
        drafts.boards.clear();
        drafts.gestures.clear();
        selected = null;
        UiSounds.drop();
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- layout

    private int col() {
        ScoreBoard b = roundBoard();
        int n = b == null ? 1 : Math.max(1, b.steps());
        return Math.max(MIN_COL, Math.min(MAX_COL, (w - 12) / n));
    }

    private int x0() {
        ScoreBoard b = roundBoard();
        int n = b == null ? 0 : b.steps();
        return left + (w - n * col()) / 2;
    }

    private int benchTop() {
        return top + 56;
    }

    private int cardTop() {
        return top + 84;
    }

    private int stepTop() {
        return cardTop() + 50;
    }

    @Override
    protected void init() {
        w = Math.min(width - 8, 420);
        h = Math.min(height - 8, 236);
        left = (width - w) / 2;
        top = (height - h) / 2;

        addRenderableWidget(UiButton.of(left + 6, top + 22, 16, 16, Component.literal("◀"), () -> seasonStep(-1)))
                .setTooltip(Tooltip.create(t("season_prev")));
        addRenderableWidget(UiButton.of(left + 128, top + 22, 16, 16, Component.literal("▶"), () -> seasonStep(1)))
                .setTooltip(Tooltip.create(t("season_next")));
        UiTabs tabs = new UiTabs(font, left, top + 22, 16, List.of(t("tab.board"), t("tab.round")),
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
            addRenderableWidget(UiButton.of(left + 6, top + 40, 16, 16, Component.literal("◀"), () -> changeRound(-1)))
                    .setTooltip(Tooltip.create(t("round_prev")));
            addRenderableWidget(UiButton.of(left + w - 22, top + 40, 16, 16, Component.literal("▶"), () -> changeRound(1)))
                    .setTooltip(Tooltip.create(t("round_next")));
            ScoreBoard b = roundBoard();
            if (!b.bench().isEmpty() && b.size() > 0)
                addRenderableWidget(UiButton.of(left + w - 136, benchTop() + 4, 130, 16, t("tied_last"), () -> {
                    b.benchTiedLast();
                    UiSounds.drop();
                    gesture(shownRound());
                })).setTooltip(Tooltip.create(t("tied_last.tip")));
        }

        // Row A: draft and validation
        int ay = top + h - 42;
        int count = pendingCount();
        UiButton validate = addRenderableWidget(new UiButton(left + w - 136, ay, 130, 18,
                t("validate").copy().withStyle(ChatFormatting.BOLD), in -> validate()).silent());
        validate.setTooltip(Tooltip.create(t("validate.tip")));
        validate.active = count > 0;
        UiButton forget = addRenderableWidget(UiButton.of(left + w - 156, ay, 18, 18, Component.literal("✕"), this::forgetDraft));
        forget.setTooltip(Tooltip.create(t("forget.tip")));
        forget.active = count > 0 || !drafts.gestures.isEmpty();
        // Publish: what players see (portraits, busts, labels, Tab, ranking) catches up with what is validated
        int unpublished = board.has("unpublished") ? board.get("unpublished").getAsInt() : 0;
        Component publishLabel = unpublished > 0 ? t("publish.count", unpublished) : t("publish.none");
        int pw = font.width(publishLabel) + 14;
        publishRight = left + 6 + pw;
        UiButton publish = addRenderableWidget(UiButton.of(left + 6, ay, pw, 18, publishLabel, () -> {
                    JsonArray actions = new JsonArray();
                    JsonObject a = new JsonObject();
                    a.addProperty("type", "scores.publish");
                    a.addProperty("season", season());
                    actions.add(a);
                    runActions(actions);
                }));
        publish.active = count == 0 && unpublished > 0;
        publish.setTooltip(Tooltip.create(count > 0 ? t("publish.draft") : t("publish.tip")));

        // Row B: gestures, server undo / redo (labelled), close
        int by = top + h - 22;
        UiButton gu = addRenderableWidget(UiButton.of(left + 6, by, 18, 18, Component.literal("◁"), this::undoGesture));
        gu.setTooltip(Tooltip.create(t("gesture_undo.tip")));
        UiButton gr = addRenderableWidget(UiButton.of(left + 26, by, 18, 18, Component.literal("▷"), this::redoGesture));
        gr.setTooltip(Tooltip.create(t("gesture_redo.tip")));
        int undos = board.get("undo").getAsInt();
        String undoText = board.has("undoText") ? board.get("undoText").getAsString() : "";
        int uw = w - 6 - 50 - 22 - 52 - 4;
        Component undoLabel = undos > 0 ? t("undo_send", font.plainSubstrByWidth(undoText, uw - 70)) : t("undo_send.none");
        UiButton su = addRenderableWidget(UiButton.of(left + 50, by, uw, 18, undoLabel, () -> simple("undo")));
        su.active = undos > 0;
        if (undos > 0) {
            long ago = board.has("undoAt") ? (System.currentTimeMillis() - board.get("undoAt").getAsLong()) / 60000 : 0;
            su.setTooltip(Tooltip.create(t("undo_send.tip", undoText, ago)));
        }
        UiButton sr = addRenderableWidget(UiButton.of(left + 50 + uw + 2, by, 20, 18, Component.literal("▷"), () -> simple("redo")));
        sr.active = board.get("redo").getAsInt() > 0;
        if (board.has("redoText")) sr.setTooltip(Tooltip.create(t("redo_send.tip", board.get("redoText").getAsString())));
        addRenderableWidget(UiButton.of(left + w - 56, by, 50, 18, Component.translatable("gui.done"), this::onClose));
    }

    // ---------------------------------------------------------------- round view: what is where

    /**
     * Who stands on each step right now: the board, or while dragging the board with the dragged card taken out and
     * put back at the insertion point (so the others make room). Null = free step.
     */
    private List<String> slots() {
        ScoreBoard b = roundBoard();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < b.size(); i++) list.add(b.player(i));
        if (dragging && dragged != null && inSteps(mouseY) && tieTarget(mouseX, mouseY) == null) {
            list.remove(dragged);
            list.add(insertAt(mouseX, list.size()), dragged);
        } else if (dragging && dragged != null && tieTarget(mouseX, mouseY) == null) {
            list.remove(dragged);
        }
        List<String> out = new ArrayList<>(list);
        while (out.size() < b.steps()) out.add(null);
        return out;
    }

    /** Insertion index under the mouse, among {@code count} other cards (never past the steps). */
    private int insertAt(double mx, int count) {
        ScoreBoard b = roundBoard();
        int i = (int) Math.floor((mx - x0()) / col());
        return Math.max(0, Math.min(Math.min(count, b.steps() - 1), i));
    }

    private boolean inSteps(double my) {
        return my >= cardTop() - 8 && my <= stepTop() + 34;
    }

    private boolean inBench(double my) {
        return my >= benchTop() && my < cardTop() - 8;
    }

    private int slotCenter(int i) {
        return x0() + i * col() + col() / 2;
    }

    /** The placed card under the mouse (not the dragged one), or null. */
    private String cardAt(double mx, double my) {
        ScoreBoard b = roundBoard();
        if (b == null || my < cardTop() - 2 || my > cardTop() + FACE + 22) return null;
        for (int i = 0; i < b.size(); i++) {
            int cx = slotCenter(i);
            if (mx >= cx - FACE / 2 - 2 && mx <= cx + FACE / 2 + 2) return b.player(i);
        }
        return null;
    }

    /** While dragging: the card whose centre is under the mouse (dropping there = ex aequo), or null. */
    private String tieTarget(double mx, double my) {
        String c = cardAt(mx, my);
        if (c == null || c.equals(dragged)) return null;
        int cx = slotCenter(roundBoard().indexOf(c));
        return Math.abs(mx - cx) <= FACE / 2f - 5 ? c : null;
    }

    /** A bench face under the mouse, or null. */
    private String benchAt(double mx, double my) {
        ScoreBoard b = roundBoard();
        if (b == null || my < benchTop() + 2 || my > benchTop() + 22) return null;
        List<String> bench = b.bench();
        for (int i = 0; i < bench.size(); i++) {
            int x = left + 44 + i * 22;
            if (mx >= x && mx < x + 18) return bench.get(i);
        }
        return null;
    }

    /** ▲ (+1) or ▼ (-1) next to a hovered card: +1, -1 or 0. */
    private int arrowAt(String player, double mx, double my) {
        int i = roundBoard().indexOf(player);
        if (i < 0) return 0;
        int ax = slotCenter(i) + FACE / 2 + 2;
        if (mx < ax || mx >= ax + 8) return 0;
        if (my >= cardTop() && my < cardTop() + 12) return 1;
        if (my >= cardTop() + 16 && my < cardTop() + 28) return -1;
        return 0;
    }

    private void bump(String player, int delta) {
        ScoreBoard b = roundBoard();
        int i = b.indexOf(player);
        if (i < 0) return;
        int before = b.score(i);
        int s = b.bump(i, delta);
        if (s == before) UiSounds.error();
        else UiSounds.score(s, delta > 0);
        selected = player;
        gesture(shownRound());
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        double mx = e.x(), my = e.y();
        if (view == View.BOARD) return clickBoard(e, mx, my);
        ScoreBoard b = roundBoard();
        if (b == null) return false;
        String card = cardAt(mx, my);
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (card != null) {
                bump(card, -1);
                return true;
            }
            return false;
        }
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (card != null) {
            int arrow = arrowAt(card, mx, my);
            if (arrow != 0 || e.hasShiftDown()) {
                bump(card, arrow != 0 ? arrow : 1);
                return true;
            }
        }
        String benched = benchAt(mx, my);
        if (card != null || benched != null) {
            dragged = card != null ? card : benched;
            fromBench = card == null;
            dragging = false;
            pressX = mx;
            pressY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        mouseX = e.x();
        mouseY = e.y();
        if (dragged != null && !dragging && Math.hypot(e.x() - pressX, e.y() - pressY) > 3) {
            dragging = true;
            UiSounds.pick();
        }
        if (dragged != null) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragged == null) return super.mouseReleased(e);
        ScoreBoard b = roundBoard();
        String p = dragged;
        dragged = null;
        if (!dragging) {
            // A plain click: a bench face goes to the next free step, a card is selected (keyboard)
            if (fromBench) {
                if (b.fromBench(p)) {
                    UiSounds.score(b.score(b.indexOf(p)), true);
                    gesture(shownRound());
                }
            } else {
                selected = p.equals(selected) ? null : p;
                UiSounds.click();
            }
            return true;
        }
        dragging = false;
        double mx = e.x(), my = e.y();
        String tie = tieTarget(mx, my);
        if (tie != null) {
            if (b.tieWith(p, tie)) {
                UiSounds.confirm();
                setStatus(t("tied", p, tie).copy().withStyle(ChatFormatting.GOLD));
                gesture(shownRound());
            } else UiSounds.error();
        } else if (inSteps(my)) {
            int others = b.size() - (fromBench ? 0 : 1);
            int at = insertAt(mx, others);
            boolean done = fromBench ? b.fromBench(p, at) : moveIfNeeded(b, p, at);
            if (done) gesture(shownRound());
            UiSounds.drop();
        } else if (inBench(my) && !fromBench) {
            b.toBench(b.indexOf(p));
            if (p.equals(selected)) selected = null;
            UiSounds.drop();
            gesture(shownRound());
        } else {
            UiSounds.error();
        }
        return true;
    }

    private static boolean moveIfNeeded(ScoreBoard b, String p, int at) {
        int from = b.indexOf(p);
        if (from == at) return false;
        b.moveTo(from, at);
        return true;
    }

    private int gridTop() {
        return top + 82;
    }

    private int gridLeft() {
        return left + 8 + 150;
    }

    private int visibleRows() {
        return Math.max(1, (top + h - 50 - gridTop()) / ROW);
    }

    private boolean clickBoard(MouseButtonEvent e, double mx, double my) {
        for (int r = 0; r < visibleRows() && r + scroll < manches.size(); r++) {
            int y = gridTop() + r * ROW;
            int index = r + scroll;
            if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && Ui.inside(mx, my, left + 10, y + 1, 20, 20)) {
                showRound(index);
                return true;
            }
            for (int c = 0; c < players.size(); c++) {
                int x = gridLeft() + c * (CELL + 8);
                if (!Ui.inside(mx, my, x, y + 1, CELL, CELL)) continue;
                String id = manches.get(index).get("id").getAsString();
                int delta = e.button() == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;
                Integer s = boardOf(id).bumpPlayer(players.get(c), delta);
                if (s == null) UiSounds.error();
                else UiSounds.score(s, delta > 0);
                gesture(id);
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
        String card = roundBoard() == null ? null : cardAt(x, y);
        if (card != null && dy != 0) {
            bump(card, dy > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    // ---------------------------------------------------------------- keyboard

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown(), shift = event.hasShiftDown();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            validate();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_Z) {
            undoGesture();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_Y) {
            redoGesture();
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            if (view == View.ROUND) changeRound(key == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        ScoreBoard b = view == View.ROUND ? roundBoard() : null;
        if (b != null) {
            int i = selected == null ? -1 : b.indexOf(selected);
            if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
                int d = key == InputConstants.KEY_LEFT ? -1 : 1;
                if (b.size() == 0) return true;
                if (i < 0) {
                    selected = b.player(d > 0 ? 0 : b.size() - 1);
                } else if (shift) {
                    int j = Math.max(0, Math.min(b.size() - 1, i + d));
                    if (j != i) {
                        b.moveTo(i, j);
                        UiSounds.drop();
                        gesture(shownRound());
                    }
                } else {
                    selected = b.player(Math.max(0, Math.min(b.size() - 1, i + d)));
                    UiSounds.click();
                }
                return true;
            }
            if ((key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) && i >= 0) {
                bump(selected, key == InputConstants.KEY_UP ? 1 : -1);
                return true;
            }
            if (key == InputConstants.KEY_SPACE && !b.bench().isEmpty()) {
                String p = b.bench().getFirst();
                if (b.fromBench(p)) {
                    UiSounds.score(b.score(b.indexOf(p)), true);
                    gesture(shownRound());
                }
                return true;
            }
            if ((key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) && i >= 0) {
                b.toBench(i);
                selected = null;
                UiSounds.drop();
                gesture(shownRound());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        // The draft stays (Drafts): reopening the screen finds it as it was
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
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        if (!dragging) {
            mouseX = mx;
            mouseY = my;
        }
        Ui.panel(g, font, left, top, w, h, t("title").copy().withStyle(ChatFormatting.BOLD));
        g.centeredText(font, Component.literal(board.get("name").getAsString()), left + 75, top + 26, Theme.TEXT);
        int hidden = board.getAsJsonArray("players").size() - players.size();
        if (hidden > 0) {
            int room = left + w - 6 - 200 - (left + 150);
            g.text(font, font.plainSubstrByWidth(t("too_many", hidden).getString(), room), left + 150, top + 26, Theme.TEXT_DIM, false);
        }
        Component hint;
        if (players.isEmpty()) {
            g.centeredText(font, t("no_players"), width / 2, top + 100, Theme.TEXT_DIM);
            hint = Component.empty();
        } else if (manches.isEmpty()) {
            g.centeredText(font, t("no_rounds"), width / 2, top + 100, Theme.TEXT_DIM);
            hint = Component.empty();
        } else if (view == View.ROUND) {
            hint = drawRound(g, mx, my);
        } else {
            hint = drawBoard(g, mx, my);
        }
        // Row A: hint (or the last status for a few seconds), draft counter
        int ay = top + h - 42;
        boolean showStatus = status != null && System.currentTimeMillis() - statusAt < 6000;
        int count = pendingCount();
        Component d = t("draft", count);
        int dx = left + w - 162 - font.width(d);
        if (count > 0) g.text(font, d, dx, ay + 5, Theme.GOLD, false);
        String line = (showStatus ? status : hint).getString();
        int hx = Math.max(left + 8, publishRight + 6);
        int room = (count > 0 ? dx - 8 : left + w - 162) - hx;
        if (font.width(line) > room) line = font.plainSubstrByWidth(line, Math.max(0, room - font.width("…"))) + "…";
        if (room > 20) g.text(font, line, hx, ay + 5, showStatus ? 0xFFFFD86A : Theme.TEXT_DIM, false);
        super.extractRenderState(g, mx, my, partial);
        // The dragged card above everything, slightly bigger
        if (dragging && dragged != null) {
            int s = FACE + 4;
            g.fill(mx - s / 2 - 3, my - s / 2 - 1, mx + s / 2 + 1, my + s / 2 + 3, 0x60000000);
            g.fill(mx - s / 2 - 2, my - s / 2 - 2, mx + s / 2 + 2, my + s / 2 + 2, Theme.GOLD);
            Ui.face(g, dragged, mx - s / 2f, my - s / 2f, s);
        }
    }

    /** Yellow hatching: what is in the draft (not sent yet). */
    private static void hatch(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0x30FFC23D);
        for (int y = y0; y < y1; y++)
            for (int x = x0 + ((y - y0) % 4); x < x1; x += 4) g.fill(x, y, x + 1, y + 1, 0x70FFC23D);
    }

    private static int medal(int place) {
        return place == 1 ? Theme.GOLD : place == 2 ? Theme.SILVER : place == 3 ? Theme.BRONZE : 0xFF8A8478;
    }

    private Component place(int place) {
        return place <= 3 ? t("place." + place) : t("place.n", place);
    }

    private Component drawRound(GuiGraphicsExtractor g, int mx, int my) {
        JsonObject m = manches.get(round);
        ScoreBoard b = roundBoard();
        Component title = t("round", m.get("number").getAsInt(), m.get("name").getAsString());
        if (m.get("current").getAsBoolean()) title = title.copy().append(t("current").copy().withStyle(ChatFormatting.GREEN));
        g.centeredText(font, title, width / 2, top + 44, Theme.TEXT);

        // Bench
        List<String> bench = b.bench();
        boolean benchZone = !bench.isEmpty() || dragging && !fromBench;
        if (benchZone) {
            int bt = benchTop();
            boolean target = dragging && !fromBench && inBench(my);
            g.fill(left + 6, bt, left + w - 6, bt + 24, target ? 0x40FFC23D : 0x30000000);
            g.outline(left + 6, bt, w - 12, 24, target ? Theme.GOLD : 0x40FFFFFF);
            g.text(font, t("bench"), left + 10, bt + 8, Theme.TEXT_DIM, false);
            for (int i = 0; i < bench.size(); i++) {
                String p = bench.get(i);
                if (dragging && p.equals(dragged)) continue;
                int x = left + 44 + i * 22;
                boolean hover = Ui.inside(mx, my, x, bt + 2, 18, 20);
                if (hover) g.fill(x - 1, bt + 3, x + 19, bt + 23, Theme.GOLD);
                Ui.face(g, p, x, bt + 4, 18);
                if (hover) fr.lordfinn.craftmaster.client.ui.Ui.tooltip(g, font, t("bench.tip", p), mx, my);
            }
            if (bench.isEmpty()) g.centeredText(font, t("to_bench"), width / 2, bt + 8, Theme.TEXT_DIM);
        }

        // Steps, cards on them, scores under them
        List<String> slots = slots();
        Map<String, Integer> changes = b.changes();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 1000f * 14);
        lastFrame = now;
        int col = col(), st = stepTop(), ct = cardTop();
        for (int i = 0; i < slots.size(); i++) {
            int sx = x0() + i * col;
            int c = medal(i + 1);
            // The step: a block in the place's colour, its base painted on it
            g.fill(sx + 3, st, sx + col - 3, st + 14, Theme.shade(c, -0.35f));
            g.fill(sx + 4, st + 1, sx + col - 4, st + 13, c);
            g.horizontalLine(sx + 4, sx + col - 5, st + 1, Theme.shade(c, 0.35f));
            String p = slots.get(i);
            boolean tiedHere = p != null && !dragging && b.isTied(p);
            int shared = tiedHere ? b.place(b.indexOf(p)) : i;
            Component label = tiedHere ? Component.literal("= ").append(place(shared + 1))
                    : place(i + 1).copy().append(" · " + b.stepBase(i));
            g.centeredText(font, label, sx + col / 2, st + 3, Theme.textOn(c));
            if (p == null) {
                g.centeredText(font, "?", sx + col / 2, ct + 10, 0x50FFFFFF);
                continue;
            }
            int score = b.stepBase(shared) + b.modifier(p);
            DigitHead.draw(g, font, score, sx + col / 2 - 8, st + 16, 16);
        }
        // Insertion marker
        String tieAim = dragging ? tieTarget(mx, my) : null;
        if (tieAim != null) {
            int tx = slotCenter(b.indexOf(tieAim));
            g.outline(tx - FACE / 2 - 4, ct - 4, FACE + 8, FACE + 8, Theme.GOLD);
            g.outline(tx - FACE / 2 - 3, ct - 3, FACE + 6, FACE + 6, Theme.GOLD);
            ScoreBadge.draw(g, font, "=", tx, ct - 12, Theme.GOLD);
        } else if (dragging && inSteps(my)) {
            int at = insertAt(mx, b.size() - (fromBench ? 0 : 1));
            int x = x0() + at * col;
            g.fill(x - 1, ct - 4, x + 1, st + 32, Theme.GOLD);
        }
        Component hint = !bench.isEmpty() && b.size() == 0 ? t("hint.bench") : dragging ? t("hint.drag") : t("hint.round");
        for (int i = 0; i < slots.size(); i++) {
            String p = slots.get(i);
            if (p == null) continue;
            float target = slotCenter(i);
            float x = cardX.getOrDefault(p, target);
            x += (target - x) * dt;
            cardX.put(p, x);
            int cx = Math.round(x);
            boolean hover = !dragging && p.equals(cardAt(mx, my));
            if (changes.containsKey(p)) hatch(g, cx - col / 2 + 2, ct - 3, cx + col / 2 - 2, st - 2);
            // The card: face in a golden frame, name, token under it
            g.fill(cx - FACE / 2 - 2, ct - 2, cx + FACE / 2 + 2, ct + FACE + 2, p.equals(selected) ? 0xFFFFFFFF : Theme.BORDER);
            g.fill(cx - FACE / 2 - 1, ct - 1, cx + FACE / 2 + 1, ct + FACE + 1, Theme.BORDER_DARK);
            Ui.face(g, p, cx - FACE / 2f, ct, FACE);
            String name = font.width(p) > col - 2 ? font.plainSubstrByWidth(p, col - 6) + "…" : p;
            g.centeredText(font, name, cx, ct + FACE + 4, Theme.TEXT);
            int mod = b.modifier(p);
            if (mod != 0) ScoreBadge.score(g, font, mod, cx, ct + FACE + 14);
            if (hover) {
                // ▲ ▼ on the card's side: ±1 for this player
                int ax = cx + FACE / 2 + 2;
                g.fill(ax, ct, ax + 8, ct + 12, arrowAt(p, mx, my) == 1 ? 0xFF7FD8E0 : 0xA0000000);
                g.centeredText(font, "▲", ax + 4, ct + 2, 0xFFFFFFFF);
                g.fill(ax, ct + 16, ax + 8, ct + 28, arrowAt(p, mx, my) == -1 ? 0xFFF1BC48 : 0xA0000000);
                g.centeredText(font, "▼", ax + 4, ct + 18, 0xFFFFFFFF);
                int step = b.indexOf(p);
                Component tip = t("card.tip", p, b.stepBase(step), place(step + 1), DigitHead.label(mod), b.score(step));
                fr.lordfinn.craftmaster.client.ui.Ui.tooltip(g, font, tip, mx, my);
            }
        }
        return hint;
    }

    private Component drawBoard(GuiGraphicsExtractor g, int mx, int my) {
        int px0 = left + 6, px1 = left + w - 6, py0 = gridTop() - 22, py1 = top + h - 50;
        Ui.paper(g, px0, py0, px1 - px0, py1 - py0);
        g.text(font, t("col.round"), left + 36, py0 + 7, Theme.INK_SOFT, false);
        for (int c = 0; c < players.size(); c++) {
            int x = gridLeft() + c * (CELL + 8);
            Ui.face(g, players.get(c), x + 2, py0 + 3, 16);
            if (Ui.inside(mx, my, x, py0 + 3, CELL, 16)) fr.lordfinn.craftmaster.client.ui.Ui.tooltip(g, font, Component.literal(players.get(c)), mx, my);
        }
        int rows = visibleRows();
        for (int r = 0; r < rows && r + scroll < manches.size(); r++) {
            int y = gridTop() + r * ROW;
            JsonObject m = manches.get(r + scroll);
            String id = m.get("id").getAsString();
            if (m.get("current").getAsBoolean()) g.fill(px0 + 1, y, px1 - 1, y + ROW, 0x2A1E7A2E);
            else if (r % 2 == 1) g.fill(px0 + 1, y, px1 - 1, y + ROW, 0x14000000);
            boolean hover = Ui.inside(mx, my, left + 10, y + 1, 20, 20);
            g.fill(left + 10, y + 1, left + 30, y + 21, hover ? Theme.shade(Theme.GOLD, 0.3f) : Theme.GOLD);
            g.outline(left + 10, y + 1, 20, 20, Theme.BORDER_DARK);
            g.centeredText(font, String.valueOf(m.get("number").getAsInt()), left + 20, y + 7, Theme.INK);
            if (hover) fr.lordfinn.craftmaster.client.ui.Ui.tooltip(g, font, t("open_round"), mx, my);
            g.text(font, font.plainSubstrByWidth(m.get("name").getAsString(), gridLeft() - left - 44), left + 36, y + 7, Theme.INK, false);
            ScoreBoard b = drafts.boards.get(id);
            Map<String, Integer> server = serverScores(m);
            Map<String, Integer> changes = b == null ? Map.of() : b.changes();
            for (int c = 0; c < players.size(); c++) {
                String p = players.get(c);
                int x = gridLeft() + c * (CELL + 8);
                Integer score = b != null && b.indexOf(p) >= 0 ? Integer.valueOf(b.score(b.indexOf(p))) : server.get(p);
                if (changes.containsKey(p)) hatch(g, x - 2, y, x + CELL + 2, y + ROW);
                if (score == null) g.centeredText(font, "·", x + CELL / 2, y + 7, Theme.INK_SOFT);
                else DigitHead.draw(g, font, score, x, y + 1, CELL);
                if (Ui.inside(mx, my, x, y + 1, CELL, CELL)) g.outline(x - 1, y, CELL + 2, CELL + 2, 0xFF000000);
            }
        }
        if (manches.size() > rows) g.text(font, (scroll + 1) + "–" + Math.min(manches.size(), scroll + rows) + " / " + manches.size(),
                px1 - 40, py0 + 7, Theme.INK_SOFT, false);
        return t("hint.board");
    }
}
