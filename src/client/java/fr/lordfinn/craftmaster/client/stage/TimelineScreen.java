package fr.lordfinn.craftmaster.client.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.client.compat.ClientCompat;
import fr.lordfinn.craftmaster.stage.Sequence;
import fr.lordfinn.craftmaster.stage.SequenceTools;
import fr.lordfinn.craftmaster.stage.Sequences;
import fr.lordfinn.craftmaster.stage.TimelineNetwork;
import fr.lordfinn.craftmaster.studio.Spotlights;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The stage timeline editor, docked at the bottom of the screen so the stage stays in view above it while it plays
 * and while it is edited. Tracks on the left (what each one drives), the timeline in the middle (seconds, keypoints,
 * the playhead: moving it shows the stage as it is then), the selected keypoint's settings on the right. Every
 * change is saved on the server by itself.
 * <p>Mouse: double-click a lane = a keypoint there (continuing the track's state); click = select (Shift: add);
 * drag = move (snapped to a quarter second, Ctrl: free); click or drag on the ruler = playhead; wheel = scroll,
 * Ctrl+wheel = zoom; on a setting, wheel or drag = change it, right-click = leave it unchanged ("—").
 * Keys: Space play / stop, Delete, Ctrl+C / V / D, Ctrl+Z / Y, ← → move the selection, Home = start.
 */
public class TimelineScreen extends Screen {

    // ---------------------------------------------------------------- look
    // The UI kit's colours (client/ui/Theme), plus the timeline's own grid
    private static final int BG = Theme.PANEL, BG_LANE = 0x40FFFFFF, BG_LANE_SEL = Theme.SELECTED, BORDER = Theme.BORDER, TEXT = Theme.TEXT,
            DIM = Theme.TEXT_DIM, GOLD = Theme.GOLD, RED = Theme.ERROR, GREEN = Theme.OK, BTN = Theme.BUTTON, BTN_HOVER = Theme.BUTTON_HOVER,
            BTN_ON = Theme.BUTTON_ON, GRID = 0x22FFFFFF, GRID_STRONG = 0x44FFFFFF;
    private static final Map<String, Integer> TYPE_COLOR = Map.of(Sequence.LIGHT, 0xFFFFD86B, Sequence.CURTAIN, 0xFFE05A6A,
            Sequence.PORTRAITS, 0xFFD4A53A, Sequence.SCREEN, 0xFF6BC4FF, Sequence.COMMAND, 0xFFB08CFF, Sequence.CAMERA, 0xFF7FD67F);
    private static final String[] COLORS = {"blanc", "chaud", "froid", "ambre", "or", "rouge", "orange", "jaune", "vert", "cyan", "bleu", "violet", "rose", "magenta"};
    private static final int LANE = 16, RULER = 12, HEADER = 18, FOOTER = 11;

    // ---------------------------------------------------------------- state from the server
    private String name = "";
    private Sequence seq = new Sequence();
    private List<String> names = new ArrayList<>();
    private JsonObject catalog = new JsonObject();
    private Map<String, Double> playing = new LinkedHashMap<>();
    private long playingAt;

    // ---------------------------------------------------------------- editing
    private int track;
    private final List<Sequence.Key> selected = new ArrayList<>();
    private List<Sequence.Key> clipboard = new ArrayList<>();
    private double playhead;
    private double viewStart, pxPerSec = 40;
    private int trackScroll, inspScroll;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long saveAt, seekAt;
    /** Zoom to fit the whole timeline at the next layout (a new timeline was opened). */
    private boolean needsFit = true;
    private boolean deleteArmed;
    private long deleteArmedAt;

    private enum Drag { NONE, PLAYHEAD, KEYS, VALUE }

    /** Where the "+ track" buttons end (the tools start after them). */
    private int addButtonsEnd;

    private Drag drag = Drag.NONE;
    private double dragX;
    private final Map<Sequence.Key, Double> dragFrom = new java.util.IdentityHashMap<>();
    private Field dragField;
    private double dragValue;

    /** The text being typed (a command, a screen's content, the name), or null. */
    private EditBox textBox;
    private Consumer<String> textDone;

    // ---------------------------------------------------------------- layout
    private int top, left, right, bottom, tracksX1, timeX0, timeX1, inspX0, lanesY0, lanesY1;

    /** Buttons drawn this frame (header, track headers, inspector): rectangle, action, tooltip. */
    private record Hit(int x0, int y0, int x1, int y1, Runnable click, Runnable rightClick, Consumer<Double> wheel, Component tip) {}

    private final List<Hit> hits = new ArrayList<>();

    public TimelineScreen(JsonObject state) {
        super(Component.translatable("screen.craftmaster.timeline"));
        open(state);
    }

    static Component t(String key, Object... args) {
        return Component.translatable("screen.craftmaster.timeline." + key, args);
    }

    // ---------------------------------------------------------------- from the server

    void open(JsonObject o) {
        String newName = o.get("name").getAsString();
        boolean same = newName.equals(name);
        name = newName;
        if (o.has("sequence") && o.get("sequence").isJsonObject()) seq = Sequences.parse(o.get("sequence").toString());
        names = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("names")) names.add(e.getAsString());
        catalog = o.getAsJsonObject("catalog");
        acceptPlaying(o.getAsJsonObject("playing"));
        if (!same) {
            track = 0;
            selected.clear();
            playhead = 0;
            viewStart = 0;
            undo.clear();
            redo.clear();
            needsFit = true;
        }
        track = Math.max(0, Math.min(track, seq.pistes.size() - 1));
    }

    void playhead(JsonObject o) {
        acceptPlaying(o.getAsJsonObject("playing"));
    }

    private void acceptPlaying(JsonObject p) {
        playing = new LinkedHashMap<>();
        if (p != null) for (Map.Entry<String, JsonElement> e : p.entrySet()) playing.put(e.getKey(), e.getValue().getAsDouble());
        playingAt = System.currentTimeMillis();
    }

    /** The live stage captured into the selected keypoint (or a new one at the playhead). */
    void captured(JsonObject o) {
        if (!o.has("key") || !o.get("key").isJsonObject()) return;
        int index = o.get("track").getAsInt();
        if (index < 0 || index >= seq.pistes.size()) return;
        Sequence.Key got = Sequences.GSON.fromJson(o.get("key"), Sequence.Key.class);
        Sequence.Track tr = seq.pistes.get(index);
        snapshot();
        Sequence.Key target = selected.size() == 1 && tr.cles.contains(selected.getFirst()) ? selected.getFirst() : null;
        if (target == null) {
            got.t = SequenceTools.snap(o.get("t").getAsDouble());
            tr.cles.add(got);
            tr.sort();
            selected.clear();
            selected.add(got);
        } else {
            got.t = target.t;
            int i = tr.cles.indexOf(target);
            tr.cles.set(i, got);
            selected.clear();
            selected.add(got);
        }
        changed(false);
    }

    private boolean isPlaying() {
        return playing.containsKey(name);
    }

    /** Where the server's playback is now (interpolated between its updates). */
    private double livePlayhead() {
        Double t = playing.get(name);
        if (t == null) return playhead;
        double now = t + (System.currentTimeMillis() - playingAt) / 1000.0;
        return seq.boucle && seq.duree > 0 ? now % seq.duree : Math.min(seq.duree, now);
    }

    // ---------------------------------------------------------------- to the server

    private static void send(JsonObject o) {
        if (ClientPlayNetworking.canSend(TimelineNetwork.Edit.TYPE)) ClientPlayNetworking.send(new TimelineNetwork.Edit(o.toString()));
    }

    private JsonObject order(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("action", action);
        o.addProperty("name", name);
        return o;
    }

    private JsonElement seqJson() {
        return JsonParser.parseString(Sequences.json(seq));
    }

    private void flushSave() {
        if (saveAt == 0) return;
        saveAt = 0;
        JsonObject o = order("save");
        o.add("sequence", seqJson());
        send(o);
    }

    private void seek(double t) {
        playhead = Math.max(0, Math.min(seq.duree, t));
        seekAt = System.currentTimeMillis() + 90;
    }

    private void play() {
        flushSave();
        JsonObject o = order("play");
        o.add("sequence", seqJson());
        o.addProperty("t", playhead >= seq.duree - 0.01 ? 0 : playhead);
        send(o);
    }

    private void stop() {
        playhead = livePlayhead();
        send(order("stop"));
        playing.remove(name);
    }

    // ---------------------------------------------------------------- edits

    /** Before an edit: one undo step. */
    private void snapshot() {
        undo.push(Sequences.json(seq));
        if (undo.size() > 100) undo.removeLast();
        redo.clear();
    }

    /** After an edit: saved soon, and the stage shows it (unless it plays). */
    private void changed(boolean showAtSelection) {
        seq.clean();
        track = Math.max(0, Math.min(track, seq.pistes.size() - 1));
        saveAt = System.currentTimeMillis() + 350;
        if (!isPlaying()) {
            if (showAtSelection && !selected.isEmpty()) {
                Sequence.Key k = selected.getFirst();
                seek(Math.min(seq.duree, k.t + k.fondu));
            } else seek(playhead);
        }
    }

    private void undo(boolean back) {
        Deque<String> from = back ? undo : redo, to = back ? redo : undo;
        if (from.isEmpty()) return;
        to.push(Sequences.json(seq));
        seq = Sequences.parse(from.pop());
        selected.clear();
        changed(false);
    }

    private Sequence.Track currentTrack() {
        return track >= 0 && track < seq.pistes.size() ? seq.pistes.get(track) : null;
    }

    private void addKey(int trackIndex, double t) {
        Sequence.Track tr = seq.pistes.get(trackIndex);
        snapshot();
        // A new keypoint continues the track's state at that time: change only what must change
        Sequence.Key k = null;
        for (Sequence.Key e : tr.cles) if (e.t <= t) k = k == null ? e.copy() : merged(k, e);
        if (k == null) {
            k = new Sequence.Key();
            switch (tr.type) {
                case Sequence.CURTAIN -> k.ouvert = true;
                case Sequence.PORTRAITS -> k.hauteur = 4f;
                case Sequence.SCREEN -> k.montre = "clip dernier";
                case Sequence.COMMAND -> k.commande = "";
                case Sequence.CAMERA -> k.transition = "fondu";
                default -> {
                    k.allume = true;
                    k.intensite = 100;
                    k.couleur = "blanc";
                }
            }
            k.fondu = tr.type.equals(Sequence.LIGHT) ? 1 : tr.type.equals(Sequence.PORTRAITS) ? 3 : 0;
        }
        k.t = SequenceTools.snap(Math.max(0, Math.min(seq.duree, t)));
        k.vague = null;
        k.cascade = null;
        tr.cles.add(k);
        tr.sort();
        track = trackIndex;
        selected.clear();
        selected.add(k);
        changed(true);
        if (tr.type.equals(Sequence.COMMAND)) editText(fieldsFor(tr).stream().filter(f -> f.key.equals("commande")).findFirst().orElse(null), k);
    }

    private static Sequence.Key merged(Sequence.Key a, Sequence.Key b) {
        Sequence.Key m = a.copy();
        if (b.allume != null) m.allume = b.allume;
        if (b.couleur != null) m.couleur = b.couleur;
        if (b.intensite != null) m.intensite = b.intensite;
        if (b.cone != null) m.cone = b.cone;
        if (b.suit != null) m.suit = b.suit;
        if (b.vise != null) m.vise = b.vise;
        if (b.horizontal != null) m.horizontal = b.horizontal;
        if (b.vertical != null) m.vertical = b.vertical;
        if (b.effet != null) m.effet = b.effet;
        if (b.vitesse != null) m.vitesse = b.vitesse;
        if (b.ouvert != null) m.ouvert = b.ouvert;
        if (b.hauteur != null) m.hauteur = b.hauteur;
        if (b.montre != null) m.montre = b.montre;
        m.fondu = b.fondu;
        m.commande = b.commande;
        return m;
    }

    private void deleteSelected() {
        if (selected.isEmpty()) return;
        snapshot();
        for (Sequence.Track tr : seq.pistes) tr.cles.removeIf(k -> selected.stream().anyMatch(s -> s == k));
        selected.clear();
        changed(false);
    }

    private void copy() {
        clipboard = new ArrayList<>();
        for (Sequence.Key k : selected) clipboard.add(k.copy());
    }

    private void paste(double at) {
        Sequence.Track tr = currentTrack();
        if (tr == null || clipboard.isEmpty()) return;
        snapshot();
        List<Sequence.Key> pasted = SequenceTools.pasteAt(clipboard, SequenceTools.snap(at), seq.duree);
        tr.cles.addAll(pasted);
        tr.sort();
        selected.clear();
        selected.addAll(pasted);
        changed(true);
    }

    private void duplicateSelection() {
        if (selected.isEmpty()) return;
        double first = selected.stream().mapToDouble(k -> k.t).min().orElse(0);
        double last = selected.stream().mapToDouble(k -> k.t + Math.max(k.fondu, SequenceTools.SNAP)).max().orElse(0);
        copy();
        paste(Math.min(seq.duree, last + (last - first < SequenceTools.SNAP ? SequenceTools.SNAP : 0)));
    }

    private void addTrack(String type) {
        snapshot();
        JsonArray options = catalog.has(type) ? catalog.getAsJsonArray(type) : new JsonArray();
        String target = type.equals(Sequence.COMMAND) || type.equals(Sequence.CAMERA) ? "" : options.isEmpty() ? "" : options.get(0).getAsString();
        seq.pistes.add(new Sequence.Track(type, target));
        track = seq.pistes.size() - 1;
        int visible = Math.max(1, (lanesY1 - lanesY0) / LANE);
        if (track - trackScroll >= visible) trackScroll = track - visible + 1;
        selected.clear();
        changed(false);
    }

    private void mirrorTrack() {
        Sequence.Track tr = currentTrack();
        if (tr == null) return;
        snapshot();
        Sequence.Track m = SequenceTools.mirror(tr, 0, null);
        if (m.cible.equals(tr.cible)) {
            // No side in the name: mirror the angles in place (same lights, other side of the stage)
            for (Sequence.Key k : tr.cles) if (k.horizontal != null) k.horizontal = SequenceTools.mirrorYaw(k.horizontal, 0);
        } else seq.pistes.add(track + 1, m);
        changed(false);
    }

    private void reverseTrackOrAll() {
        snapshot();
        if (!selected.isEmpty()) {
            double a = selected.stream().mapToDouble(k -> k.t).min().orElse(0);
            double b = selected.stream().mapToDouble(k -> k.t + k.fondu).max().orElse(0);
            for (Sequence.Key k : selected) k.t = Math.max(0, a + b - k.t - k.fondu);
        } else SequenceTools.reverse(seq);
        changed(false);
    }

    private void capture() {
        Sequence.Track tr = currentTrack();
        if (tr == null) return;
        JsonObject o = order("capture");
        o.add("track", JsonParser.parseString(Sequences.GSON.toJson(tr)));
        o.addProperty("index", track);
        o.addProperty("t", playhead);
        send(o);
    }

    private void switchTo(String other) {
        flushSave();
        JsonObject o = order("open");
        o.addProperty("name", other);
        send(o);
    }

    // ---------------------------------------------------------------- text fields

    private void editText(Field f, Sequence.Key k) {
        if (f == null || k == null) return;
        startText(f.textGet.get(), s -> {
            snapshot();
            f.textSet.accept(s);
            changed(true);
        });
    }

    private void startText(String value, Consumer<String> done) {
        closeText(false);
        int w = Math.max(120, right - inspX0 - 8);
        textBox = new EditBox(font, Math.min(inspX0 + 4, width - w - 4), Math.max(4, top - 22), w, 16, Component.empty());
        textBox.setMaxLength(512);
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
    }

    // ---------------------------------------------------------------- the selected keypoint's settings

    private enum Kind { NUMBER, TOGGLE, CYCLE, COLOR, TEXT }

    private static final class Field {
        final String key;
        final Kind kind;
        double min, max, step;
        Supplier<Double> get;
        Consumer<Double> set;
        Supplier<String> textGet;
        Consumer<String> textSet;
        List<String> options = List.of();
        String unit = "";
        boolean nullable = true;

        Field(String key, Kind kind) {
            this.key = key;
            this.kind = kind;
        }
    }

    private Field number(String key, double min, double max, double step, String unit, Supplier<Double> get, Consumer<Double> set) {
        Field f = new Field(key, Kind.NUMBER);
        f.min = min;
        f.max = max;
        f.step = step;
        f.unit = unit;
        f.get = get;
        f.set = set;
        return f;
    }

    private Field cycle(String key, List<String> options, Supplier<String> get, Consumer<String> set) {
        Field f = new Field(key, Kind.CYCLE);
        f.options = options;
        f.textGet = get;
        f.textSet = set;
        return f;
    }

    private Field text(String key, Supplier<String> get, Consumer<String> set) {
        Field f = new Field(key, Kind.TEXT);
        f.textGet = get;
        f.textSet = set;
        return f;
    }

    private List<String> options(String catalogKey, String... first) {
        List<String> l = new ArrayList<>(List.of(first));
        if (catalog.has(catalogKey)) for (JsonElement e : catalog.getAsJsonArray(catalogKey)) if (!l.contains(e.getAsString())) l.add(e.getAsString());
        return l;
    }

    private List<Field> fieldsFor(Sequence.Track tr) {
        List<Field> out = new ArrayList<>();
        Sequence.Key k = selected.size() == 1 ? selected.getFirst() : null;
        if (k == null) return out;
        Field time = number("temps", 0, seq.duree, SequenceTools.SNAP, "s", () -> k.t, v -> k.t = v);
        time.nullable = false;
        out.add(time);
        if (!tr.type.equals(Sequence.COMMAND) && !tr.type.equals(Sequence.SCREEN) && !tr.type.equals(Sequence.CAMERA)) {
            Field fade = number("fondu", 0, 30, SequenceTools.SNAP, "s", () -> k.fondu, v -> k.fondu = v);
            fade.nullable = false;
            out.add(fade);
        }
        switch (tr.type) {
            case Sequence.CURTAIN -> out.add(cycle("ouvert", List.of("—", "ouvert", "fermé"),
                    () -> k.ouvert == null ? "—" : k.ouvert ? "ouvert" : "fermé",
                    s -> k.ouvert = s.equals("—") ? null : s.equals("ouvert")));
            case Sequence.PORTRAITS -> {
                out.add(number("hauteur", 0.5, 40, 0.5, "", () -> k.hauteur == null ? null : (double) k.hauteur, v -> k.hauteur = v == null ? null : v.floatValue()));
                out.add(number("cascade", 0, 5, 0.1, "s", () -> k.cascade, v -> k.cascade = v));
            }
            case Sequence.SCREEN -> {
                out.add(cycle("montre", List.of("clip dernier", "chat", "off"), () -> k.montre == null ? "—" : k.montre, s -> k.montre = s));
                out.add(text("montre_texte", () -> k.montre, s -> k.montre = s));
            }
            case Sequence.COMMAND -> out.add(text("commande", () -> k.commande, s -> k.commande = s));
            case Sequence.CAMERA -> {
                // Shots as "value|label": the cycle shows the label, stores the value
                List<String> values = new ArrayList<>(), labels = new ArrayList<>();
                if (catalog.has("plans")) for (JsonElement e : catalog.getAsJsonArray("plans")) {
                    String[] vl = e.getAsString().split("\\|", 2);
                    values.add(vl[0]);
                    labels.add(vl.length > 1 ? vl[1] : vl[0]);
                }
                Field plan = cycle("plan", labels, () -> {
                    int i = values.indexOf(k.plan == null ? "" : k.plan);
                    return i < 0 ? (k.plan == null ? "—" : k.plan) : labels.get(i);
                }, s -> {
                    int i = labels.indexOf(s);
                    k.plan = i < 0 ? null : values.get(i);
                });
                out.add(plan);
                out.add(cycle("transition", options("transitions", "—"), () -> k.transition == null ? "—" : k.transition,
                        s -> k.transition = s.equals("—") ? null : s));
            }
            default -> {
                out.add(cycle("allume", List.of("—", "allumé", "éteint"), () -> k.allume == null ? "—" : k.allume ? "allumé" : "éteint",
                        s -> k.allume = s.equals("—") ? null : s.equals("allumé")));
                Field color = new Field("couleur", Kind.COLOR);
                color.textGet = () -> k.couleur;
                color.textSet = s -> k.couleur = s;
                out.add(color);
                out.add(number("intensite", 0, 100, 5, "%", () -> k.intensite == null ? null : (double) k.intensite, v -> k.intensite = v == null ? null : (int) Math.round(v)));
                out.add(number("cone", 1, 30, 0.5, "°", () -> k.cone == null ? null : (double) k.cone, v -> k.cone = v == null ? null : v.floatValue()));
                out.add(cycle("suit", options("suit", "—", "personne"), () -> k.suit == null ? "—" : k.suit.isEmpty() ? "personne" : k.suit, s -> {
                    k.suit = s.equals("—") ? null : s.equals("personne") ? "" : s;
                    if (k.suit != null && !k.suit.isEmpty()) {
                        k.vise = null;
                        k.horizontal = k.vertical = null;
                    }
                }));
                out.add(cycle("vise", options("points", "—"), () -> k.vise == null ? "—" : k.vise, s -> {
                    k.vise = s.equals("—") ? null : s;
                    if (k.vise != null) {
                        k.suit = null;
                        k.horizontal = k.vertical = null;
                    }
                }));
                out.add(cycle("effet", options("effets", "—"), () -> k.effet == null ? "—" : k.effet, s -> k.effet = s.equals("—") ? null : s));
                out.add(number("vitesse", 0.25, 4, 0.25, "×", () -> k.vitesse, v -> k.vitesse = v));
                out.add(number("vague", 0, 5, 0.25, "s", () -> k.vague, v -> k.vague = v));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int h = Math.max(120, Math.min(190, (int) (height * 0.46)));
        top = height - h;
        left = 0;
        right = width;
        bottom = height;
        tracksX1 = left + Math.min(118, width / 5);
        inspX0 = right - Math.min(156, width / 4);
        timeX0 = tracksX1 + 2;
        timeX1 = inspX0 - 4;
        lanesY0 = top + HEADER + RULER;
        lanesY1 = bottom - FOOTER - 14;
        if (needsFit && timeX1 - timeX0 > 40) {
            needsFit = false;
            fitZoom();
        }
        if (textBox != null) {
            String v = textBox.getValue();
            Consumer<String> d = textDone;
            textBox = null;
            startText(v, d);
        }
    }

    private void fitZoom() {
        int w = Math.max(100, timeX1 - timeX0);
        if (w <= 100 && width > 0) w = (int) (width * 0.55);
        pxPerSec = Math.max(4, Math.min(400, (w - 8) / Math.max(1, seq.duree)));
        viewStart = 0;
    }

    private double xOf(double t) {
        return timeX0 + (t - viewStart) * pxPerSec;
    }

    private double tOf(double x) {
        return viewStart + (x - timeX0) / pxPerSec;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (saveAt != 0 && now >= saveAt) flushSave();
        if (seekAt != 0 && now >= seekAt) {
            seekAt = 0;
            JsonObject o = order("seek");
            o.addProperty("t", playhead);
            o.add("sequence", seqJson());
            send(o);
        }
        if (deleteArmed && now - deleteArmedAt > 3000) deleteArmed = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (timeX1 - timeX0 < 40) init();
        hits.clear();
        Ui.panel(g, font, left, top, right - left, bottom - top, null);
        drawTracks(g, mouseX, mouseY);
        drawHeader(g, mouseX, mouseY);
        drawTimeline(g, mouseX, mouseY);
        drawInspector(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);
        if (textBox != null) {
            g.fill(textBox.getX() - 3, textBox.getY() - 12, textBox.getX() + textBox.getWidth() + 3, textBox.getY() + 19, BG);
            g.text(font, t("text_hint"), textBox.getX(), textBox.getY() - 10, DIM, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        // Tooltips last, over everything
        if (textBox == null) for (Hit h : hits)
            if (h.tip != null && mouseX >= h.x0 && mouseX < h.x1 && mouseY >= h.y0 && mouseY < h.y1) {
                g.setTooltipForNextFrame(h.tip, mouseX, mouseY);
                break;
            }
        Sequence.Key hover = keyAt(mouseX, mouseY);
        if (hover != null && drag == Drag.NONE && textBox == null) {
            Sequence.Track tr = trackOf(hover);
            g.setTooltipForNextFrame(Component.literal(Sequence.Key.class.cast(hover).describe(tr == null ? null : tr.type) + " · " + secs(hover.t)), mouseX, mouseY);
        }
    }

    private int button(GuiGraphicsExtractor g, int x, int y, Component label, boolean on, int mouseX, int mouseY, Runnable click, Component tip) {
        int w = font.width(label) + 8;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
        Ui.button(g, font, x, y, w, 13, label, on, hover, true);
        hits.add(new Hit(x, y, x + w, y + 13, click, null, null, tip));
        return x + w + 2;
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        int y = top + 3, x = left + 4;
        int i = names.indexOf(name);
        x = button(g, x, y, Component.literal("◀"), false, mx, my, () -> {
            if (!names.isEmpty()) switchTo(names.get((names.indexOf(name) - 1 + names.size()) % names.size()));
        }, t("prev"));
        String title = font.plainSubstrByWidth(name, 110);
        g.text(font, Component.literal(title), x + 2, y + 3, GOLD, false);
        int tw = font.width(title) + 4;
        hits.add(new Hit(x, y, x + tw, y + 13, () -> startText(name, s -> {
            JsonObject o = order("rename");
            o.addProperty("to", s);
            flushSave();
            send(o);
        }), null, null, t("rename")));
        x += tw + 2;
        x = button(g, x, y, Component.literal("▶"), false, mx, my, () -> {
            if (!names.isEmpty()) switchTo(names.get((names.indexOf(name) + 1) % names.size()));
        }, t("next"));
        x = button(g, x, y, Component.literal("+"), false, mx, my, () -> {
            flushSave();
            JsonObject o = order("new");
            o.addProperty("name", "sequence");
            send(o);
        }, t("new"));
        x = button(g, x, y, t("copy_seq"), false, mx, my, () -> {
            flushSave();
            send(order("duplicate"));
        }, t("copy_seq_tip"));
        x = button(g, x, y, deleteArmed ? t("delete_sure") : Component.literal("✕"), deleteArmed, mx, my, () -> {
            if (!deleteArmed) {
                deleteArmed = true;
                deleteArmedAt = System.currentTimeMillis();
            } else {
                deleteArmed = false;
                saveAt = 0;
                send(order("delete"));
            }
        }, t("delete_seq"));
        x += 6;
        // Length: wheel or drag on it
        String len = t("length", secs(seq.duree)).getString();
        int lw = font.width(len) + 8;
        boolean lh = mx >= x && mx < x + lw && my >= y && my < y + 13;
        g.fill(x, y, x + lw, y + 13, lh ? BTN_HOVER : 0x40000000);
        g.text(font, len, x + 4, y + 3, TEXT, false);
        hits.add(new Hit(x, y, x + lw, y + 13, () -> startText(num(seq.duree), s -> {
            try {
                double d = Double.parseDouble(s.replace(',', '.').replace("s", "").strip());
                snapshot();
                seq.duree = Math.max(1, Math.min(3600, d));
                changed(false);
                fitZoom();
            } catch (NumberFormatException ignored) {
            }
        }), null, v -> {
            snapshot();
            seq.duree = Math.max(1, Math.min(3600, seq.duree + v * (hasShift() ? 10 : 1)));
            changed(false);
        }, t("length_tip")));
        x += lw + 2;
        x = button(g, x, y, t("loop"), seq.boucle, mx, my, () -> {
            snapshot();
            seq.boucle = !seq.boucle;
            changed(false);
        }, t("loop_tip"));
        x += 6;
        boolean live = isPlaying();
        x = button(g, x, y, live ? t("stop") : t("play"), live, mx, my, () -> {
            if (isPlaying()) stop();
            else play();
        }, t("play_tip"));
        x = button(g, x, y, Component.literal("⏮"), false, mx, my, () -> seek(0), t("start"));
        if (i < 0 && !names.isEmpty()) g.text(font, t("unsaved"), x, y + 3, DIM, false);
        // Tools: on the bottom row, right-aligned (the header is full on a narrow screen)
        y = lanesY1 + 1;
        List<Object[]> tools = List.of(
                new Object[]{t("capture"), (Runnable) this::capture, t("capture_tip")},
                new Object[]{t("mirror"), (Runnable) this::mirrorTrack, t("mirror_tip")},
                new Object[]{t("reverse"), (Runnable) this::reverseTrackOrAll, t("reverse_tip")},
                new Object[]{t("dup"), (Runnable) this::duplicateSelection, t("dup_tip")});
        int tx = timeX1;
        for (Object[] tool : tools) tx -= font.width((Component) tool[0]) + 10;
        tx = Math.max(tx, addButtonsEnd + 6);
        for (Object[] tool : tools) {
            if (tx + font.width((Component) tool[0]) + 8 > right - 2) break;
            tx = button(g, tx, y, (Component) tool[0], false, mx, my, (Runnable) tool[1], (Component) tool[2]);
        }
    }

    private void drawTracks(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(left, lanesY0 - RULER, tracksX1, lanesY0, 0x30000000);
        g.text(font, t("tracks"), left + 4, lanesY0 - RULER + 2, DIM, false);
        int visible = Math.max(1, (lanesY1 - lanesY0) / LANE);
        trackScroll = Math.max(0, Math.min(trackScroll, Math.max(0, seq.pistes.size() - visible)));
        for (int i = trackScroll; i < seq.pistes.size() && i - trackScroll < visible; i++) {
            Sequence.Track tr = seq.pistes.get(i);
            int y = lanesY0 + (i - trackScroll) * LANE;
            boolean sel = i == track;
            g.fill(left, y, tracksX1, y + LANE - 1, sel ? BG_LANE_SEL : 0x18FFFFFF);
            int color = TYPE_COLOR.getOrDefault(tr.type, TEXT);
            g.fill(left, y, left + 3, y + LANE - 1, color);
            String type = t("type." + tr.type).getString();
            g.text(font, type, left + 6, y + 4, tr.muette ? DIM : color, false);
            int tx = left + 8 + font.width(type);
            // Its target: click (or wheel) goes through what exists
            String target = tr.type.equals(Sequence.COMMAND) || tr.type.equals(Sequence.CAMERA) ? "" : tr.cible.isEmpty() ? "?" : tr.cible;
            int maxW = tracksX1 - tx - 22;
            String shown = font.plainSubstrByWidth(target, Math.max(10, maxW));
            boolean th = mx >= tx && mx < tx + font.width(shown) + 2 && my >= y && my < y + LANE;
            g.text(font, shown, tx, y + 4, tr.muette ? DIM : th ? GOLD : TEXT, false);
            int index = i;
            if (!tr.type.equals(Sequence.COMMAND) && !tr.type.equals(Sequence.CAMERA))
                hits.add(new Hit(tx, y, tx + Math.max(8, font.width(shown) + 2), y + LANE, () -> cycleTarget(index, 1), () -> {
                    Sequence.Track t2 = seq.pistes.get(index);
                    startText(t2.cible, s -> {
                        snapshot();
                        t2.cible = s.strip();
                        changed(false);
                    });
                }, v -> cycleTarget(index, v > 0 ? -1 : 1), t("target_tip")));
            // Mute, delete
            int bx = tracksX1 - 20;
            boolean mh = mx >= bx && mx < bx + 9 && my >= y && my < y + LANE;
            g.text(font, "M", bx + 1, y + 4, tr.muette ? RED : mh ? GOLD : DIM, false);
            hits.add(new Hit(bx, y, bx + 9, y + LANE, () -> {
                snapshot();
                seq.pistes.get(index).muette = !seq.pistes.get(index).muette;
                changed(false);
            }, null, null, t("mute_tip")));
            bx += 10;
            boolean dh = mx >= bx && mx < bx + 9 && my >= y && my < y + LANE;
            g.text(font, "✕", bx + 1, y + 4, dh ? RED : DIM, false);
            hits.add(new Hit(bx, y, bx + 9, y + LANE, () -> {
                snapshot();
                seq.pistes.remove(index);
                selected.clear();
                changed(false);
            }, null, null, t("remove_track_tip")));
            hits.add(new Hit(left, y, tracksX1, y + LANE, () -> {
                track = index;
                selected.clear();
            }, null, null, null));
        }
        // Add a track: one small button per kind
        int y = lanesY1 + 1, x = left + 4;
        g.text(font, "+", x, y + 3, DIM, false);
        x += 8;
        for (String type : Sequence.TYPES) {
            String label = t("type." + type).getString();
            int w = font.width(label) + 4;
            boolean h = mx >= x && mx < x + w && my >= y && my < y + 12;
            Ui.button(g, font, x, y, w, 12, Component.empty(), false, h, true);
            g.text(font, label, x + 2, y + 2, TYPE_COLOR.getOrDefault(type, TEXT), false);
            hits.add(new Hit(x, y, x + w, y + 12, () -> addTrack(type), null, null, t("add_track", label)));
            x += w + 2;
        }
        addButtonsEnd = x;
    }

    private void cycleTarget(int index, int dir) {
        Sequence.Track tr = seq.pistes.get(index);
        List<String> opts = options(tr.type);
        if (opts.isEmpty()) return;
        int i = opts.indexOf(tr.cible);
        snapshot();
        tr.cible = opts.get(((i < 0 ? -1 : i) + dir + opts.size()) % opts.size());
        changed(false);
    }

    private void drawTimeline(GuiGraphicsExtractor g, int mx, int my) {
        int rulerY = lanesY0 - RULER;
        g.fill(timeX0, rulerY, timeX1, lanesY1, 0x22000000);
        // Grid: a line per step, labels where they fit
        double[] steps = {0.25, 0.5, 1, 2, 5, 10, 30, 60};
        double step = 60;
        for (double s : steps) if (s * pxPerSec >= 28) {
            step = s;
            break;
        }
        double sub = step >= 1 && step * pxPerSec >= 60 ? step / 4 : step;
        double t0 = Math.floor(viewStart / sub) * sub;
        for (double tt = t0; tt <= seq.duree + 1e-6; tt += sub) {
            int x = (int) Math.round(xOf(tt));
            if (x < timeX0) continue;
            if (x > timeX1) break;
            boolean major = Math.abs(tt / step - Math.rint(tt / step)) < 1e-6;
            g.fill(x, major ? rulerY + 4 : rulerY + 8, x + 1, lanesY1, major ? GRID_STRONG : GRID);
            if (major && x + 2 + font.width(secs(tt)) <= timeX1) g.text(font, secs(tt), x + 2, rulerY + 2, DIM, false);
        }
        // The end of the timeline
        int endX = (int) Math.round(xOf(seq.duree));
        if (endX >= timeX0 && endX <= timeX1) {
            g.fill(endX, rulerY, endX + 1, lanesY1, BORDER);
            if (endX + 1 < timeX1) g.fill(endX + 1, rulerY, timeX1, lanesY1, 0x50000000);
        }
        // Lanes and their keypoints
        int visible = Math.max(1, (lanesY1 - lanesY0) / LANE);
        for (int i = trackScroll; i < seq.pistes.size() && i - trackScroll < visible; i++) {
            Sequence.Track tr = seq.pistes.get(i);
            int y = lanesY0 + (i - trackScroll) * LANE;
            g.fill(timeX0, y + LANE - 1, timeX1, y + LANE, 0x20FFFFFF);
            if (i == track) g.fill(timeX0, y, timeX1, y + LANE - 1, 0x14FFC23D);
            int typeColor = TYPE_COLOR.getOrDefault(tr.type, TEXT);
            for (Sequence.Key k : tr.cles) {
                int kx = (int) Math.round(xOf(k.t));
                int color = keyColor(tr, k, typeColor);
                if (tr.muette) color = 0xFF6A6050;
                // Its transition: a bar to where the change is complete
                if (k.fondu > 0) {
                    int fx = (int) Math.round(xOf(k.t + k.fondu));
                    int a = Math.max(timeX0, kx), b = Math.min(timeX1, fx);
                    if (b > a) g.fill(a, y + LANE / 2 - 1, b, y + LANE / 2 + 1, (color & 0x00FFFFFF) | 0x90000000);
                }
                // Wave / cascade: a fan of small ticks after the key
                Double spread = k.vague != null ? k.vague : k.cascade;
                if (spread != null && spread > 0) {
                    int sx = (int) Math.round(xOf(k.t + spread));
                    for (int j = 1; j <= 4; j++) {
                        int px = kx + (sx - kx) * j / 4;
                        if (px >= timeX0 && px <= timeX1) g.fill(px, y + 3, px + 1, y + LANE - 4, (color & 0x00FFFFFF) | 0x80000000);
                    }
                }
                if (kx < timeX0 - 4 || kx > timeX1 + 4) continue;
                boolean sel = selected.stream().anyMatch(s -> s == k);
                diamond(g, kx, y + LANE / 2, sel ? 5 : 4, sel ? 0xFFFFFFFF : 0xFF000000);
                diamond(g, kx, y + LANE / 2, sel ? 4 : 3, color);
            }
        }
        // Playhead (the server's while it plays)
        double ph = isPlaying() ? livePlayhead() : playhead;
        int px = (int) Math.round(xOf(ph));
        if (px >= timeX0 && px <= timeX1) {
            int c = isPlaying() ? GREEN : RED;
            g.fill(px, rulerY, px + 1, lanesY1, c);
            g.fill(px - 3, rulerY, px + 4, rulerY + 3, c);
            String label = secs(Math.round(ph * 100) / 100.0);
            g.text(font, label, Math.min(timeX1 - font.width(label), px + 4), lanesY1 - 9, c, false);
        }
        if (seq.pistes.isEmpty()) g.text(font, t("empty"), timeX0 + 6, lanesY0 + 4, DIM, false);
        else if (seq.keyCount() == 0) g.text(font, t("hint_add"), timeX0 + 6, lanesY0 + LANE * Math.min(seq.pistes.size(), visible) + 4, DIM, false);
    }

    private int keyColor(Sequence.Track tr, Sequence.Key k, int typeColor) {
        if (tr.type.equals(Sequence.LIGHT)) {
            if (Boolean.FALSE.equals(k.allume)) return 0xFF505050;
            if (k.couleur != null) {
                Integer c = Spotlights.parseColor(k.couleur);
                if (c != null) return 0xFF000000 | c;
            }
        }
        if (tr.type.equals(Sequence.CURTAIN) && k.ouvert != null) return k.ouvert ? 0xFFE05A6A : 0xFF7A2030;
        return typeColor;
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private void drawInspector(GuiGraphicsExtractor g, int mx, int my) {
        int x0 = inspX0, x1 = right - 4;
        g.fill(x0 - 2, top + HEADER, x0 - 1, bottom - FOOTER, 0x40FFFFFF);
        int y = top + HEADER + 1;
        Sequence.Track tr = currentTrack();
        if (tr == null) {
            g.text(font, t("no_track"), x0 + 2, y + 2, DIM, false);
            return;
        }
        if (selected.size() != 1) {
            g.text(font, selected.isEmpty() ? t("no_key") : t("many_keys", selected.size()), x0 + 2, y + 2, DIM, false);
            g.text(font, t("type." + tr.type), x0 + 2, y + 14, TYPE_COLOR.getOrDefault(tr.type, TEXT), false);
            g.text(font, font.plainSubstrByWidth(tr.cible, x1 - x0 - 4), x0 + 2, y + 25, TEXT, false);
            g.text(font, t("keys_count", tr.cles.size()), x0 + 2, y + 36, DIM, false);
            return;
        }
        Sequence.Key k = selected.getFirst();
        g.text(font, t("key_title", t("type." + tr.type)), x0 + 2, y + 1, TYPE_COLOR.getOrDefault(tr.type, TEXT), false);
        y += 11;
        List<Field> fields = fieldsFor(tr);
        int labelW = 30;
        for (Field f : fields) labelW = Math.max(labelW, font.width(t("field." + f.key)) + 5);
        labelW = Math.min(labelW, (x1 - x0) / 2);
        int room = Math.max(1, (lanesY1 - y) / 11);
        inspScroll = Math.max(0, Math.min(inspScroll, Math.max(0, fields.size() - room)));
        if (inspScroll > 0) g.text(font, "▲", x1 - 6, top + HEADER + 2, DIM, false);
        if (inspScroll + room < fields.size()) g.text(font, "▼", x1 - 6, lanesY1 - 9, DIM, false);
        for (int fi = inspScroll; fi < fields.size(); fi++) {
            Field f = fields.get(fi);
            if (y + 10 > lanesY1) break;
            boolean rowHover = mx >= x0 && mx < x1 && my >= y && my < y + 10;
            if (rowHover) g.fill(x0, y - 1, x1, y + 10, 0x18FFFFFF);
            g.text(font, t("field." + f.key), x0 + 2, y + 1, DIM, false);
            int vx = x0 + labelW;
            switch (f.kind) {
                case NUMBER -> {
                    Double v = f.get.get();
                    String s = v == null ? "—" : num(v) + f.unit;
                    // A bar showing where the value is
                    if (v != null) {
                        int w = x1 - vx;
                        int fillW = (int) Math.round(w * (v - f.min) / Math.max(1e-6, f.max - f.min));
                        g.fill(vx, y + 8, vx + Math.max(0, Math.min(w, fillW)), y + 9, 0x80FFC23D);
                    }
                    g.text(font, s, vx, y + 1, v == null ? DIM : TEXT, false);
                    hits.add(new Hit(vx, y - 1, x1, y + 10, () -> {
                        if (f.get.get() == null) {
                            snapshot();
                            f.set.accept(defaultFor(f));
                            changed(true);
                        } else {
                            dragField = f;
                            dragValue = f.get.get();
                        }
                    }, f.nullable ? () -> {
                        snapshot();
                        f.set.accept(null);
                        changed(true);
                    } : null, d -> {
                        snapshot();
                        Double cur = f.get.get();
                        double base = cur == null ? defaultFor(f) : cur;
                        f.set.accept(clampStep(f, base + d * f.step * (hasShift() ? 5 : 1)));
                        changed(true);
                    }, t("number_tip")));
                }
                case CYCLE -> {
                    String s = f.textGet.get();
                    g.text(font, font.plainSubstrByWidth(s, x1 - vx), vx, y + 1, s.equals("—") ? DIM : TEXT, false);
                    hits.add(new Hit(vx, y - 1, x1, y + 10, () -> cycleField(f, 1), () -> {
                        snapshot();
                        f.textSet.accept("—");
                        changed(true);
                    }, d -> cycleField(f, d > 0 ? -1 : 1), t("cycle_tip")));
                }
                case TEXT -> {
                    String s = f.textGet.get();
                    g.text(font, font.plainSubstrByWidth(s == null || s.isEmpty() ? "…" : s, x1 - vx), vx, y + 1, s == null || s.isEmpty() ? DIM : TEXT, false);
                    hits.add(new Hit(vx, y - 1, x1, y + 10, () -> editText(f, k), null, null, t("text_tip")));
                }
                case COLOR -> {
                    // A row of swatches; the current one framed; right-click: unchanged
                    int sw = Math.max(5, Math.min(8, (x1 - vx) / COLORS.length - 1));
                    String cur = f.textGet.get();
                    for (int i = 0; i < COLORS.length; i++) {
                        int sx = vx + i * (sw + 1);
                        if (sx + sw > x1) break;
                        Integer c = Spotlights.parseColor(COLORS[i]);
                        boolean on = COLORS[i].equalsIgnoreCase(cur == null ? "" : cur);
                        if (on) g.fill(sx - 1, y - 1, sx + sw + 1, y + 9, 0xFFFFFFFF);
                        g.fill(sx, y, sx + sw, y + 8, 0xFF000000 | (c == null ? 0xFFFFFF : c));
                        String name2 = COLORS[i];
                        hits.add(new Hit(sx, y - 1, sx + sw + 1, y + 10, () -> {
                            snapshot();
                            f.textSet.accept(name2);
                            changed(true);
                        }, () -> {
                            snapshot();
                            f.textSet.accept(null);
                            changed(true);
                        }, null, Component.literal(name2)));
                    }
                    if (cur == null) g.text(font, "—", x1 - 6, y + 1, DIM, false);
                }
            }
            y += 11;
        }
    }

    private double defaultFor(Field f) {
        return switch (f.key) {
            case "intensite" -> 100;
            case "cone" -> 3.5;
            case "hauteur" -> 4;
            case "vitesse" -> 1;
            case "vague" -> 1;
            case "cascade" -> 0.3;
            default -> f.min;
        };
    }

    private static double clampStep(Field f, double v) {
        double s = Math.round(v / f.step) * f.step;
        return Math.max(f.min, Math.min(f.max, Math.round(s * 1000) / 1000.0));
    }

    private void cycleField(Field f, int dir) {
        List<String> opts = f.options;
        if (opts.isEmpty()) return;
        int i = opts.indexOf(f.textGet.get());
        snapshot();
        f.textSet.accept(opts.get(((i < 0 ? 0 : i) + dir + opts.size()) % opts.size()));
        changed(true);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        String hint = t("footer").getString();
        g.text(font, font.plainSubstrByWidth(hint, right - left - 8), left + 4, bottom - FOOTER + 1, DIM, false);
    }

    // ---------------------------------------------------------------- input

    private boolean hasShift() {
        return minecraft != null && minecraft.hasShiftDown();
    }

    private Sequence.Track trackOf(Sequence.Key k) {
        for (Sequence.Track tr : seq.pistes) for (Sequence.Key e : tr.cles) if (e == k) return tr;
        return null;
    }

    private int laneAt(double my) {
        if (my < lanesY0 || my >= lanesY1) return -1;
        int i = trackScroll + (int) ((my - lanesY0) / LANE);
        return i < seq.pistes.size() ? i : -1;
    }

    private Sequence.Key keyAt(double mx, double my) {
        if (mx < timeX0 - 4 || mx > timeX1 + 4) return null;
        int i = laneAt(my);
        if (i < 0) return null;
        Sequence.Key best = null;
        double bestD = 5;
        for (Sequence.Key k : seq.pistes.get(i).cles) {
            double d = Math.abs(xOf(k.t) - mx);
            if (d <= bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (textBox != null) {
            if (textBox.isMouseOver(e.x(), e.y())) return super.mouseClicked(e, doubleClick);
            closeText(true);
            return true;
        }
        double mx = e.x(), my = e.y();
        if (my < top) return false;
        boolean rightButton = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        for (Hit h : List.copyOf(hits)) {
            if (mx >= h.x0 && mx < h.x1 && my >= h.y0 && my < h.y1) {
                if (rightButton && h.rightClick != null) {
                    h.rightClick.run();
                    return true;
                }
                if (!rightButton && h.click != null) {
                    h.click.run();
                    if (dragField != null) {
                        drag = Drag.VALUE;
                        dragX = mx;
                        snapshot();
                    }
                    return true;
                }
            }
        }
        // Ruler: the playhead
        if (mx >= timeX0 && mx <= timeX1 && my >= lanesY0 - RULER && my < lanesY0) {
            if (isPlaying()) stop();
            drag = Drag.PLAYHEAD;
            seek(snapTime(tOf(mx), e.hasControlDown()));
            return true;
        }
        int lane = laneAt(my);
        if (lane >= 0 && mx >= timeX0 && mx <= timeX1) {
            track = lane;
            Sequence.Key k = keyAt(mx, my);
            if (rightButton) {
                if (k != null) {
                    selected.clear();
                    selected.add(k);
                    deleteSelected();
                }
                return true;
            }
            if (k == null) {
                if (doubleClick) addKey(lane, tOf(mx));
                else {
                    if (!e.hasShiftDown()) selected.clear();
                    if (!isPlaying()) seek(snapTime(tOf(mx), e.hasControlDown()));
                }
                return true;
            }
            if (e.hasShiftDown()) {
                if (selected.stream().anyMatch(s -> s == k)) selected.removeIf(s -> s == k);
                else selected.add(k);
            } else if (selected.stream().noneMatch(s -> s == k)) {
                selected.clear();
                selected.add(k);
            }
            drag = Drag.KEYS;
            dragX = mx;
            dragFrom.clear();
            for (Sequence.Key s : selected) dragFrom.put(s, s.t);
            snapshot();
            return true;
        }
        return true;
    }

    private double snapTime(double t, boolean free) {
        t = Math.max(0, Math.min(seq.duree, t));
        return free ? Math.round(t * 100) / 100.0 : SequenceTools.snap(t);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        switch (drag) {
            case PLAYHEAD -> seek(snapTime(tOf(e.x()), e.hasControlDown()));
            case KEYS -> {
                double dt = (e.x() - dragX) / pxPerSec;
                for (Map.Entry<Sequence.Key, Double> en : dragFrom.entrySet())
                    en.getKey().t = snapTime(en.getValue() + dt, e.hasControlDown());
            }
            case VALUE -> {
                if (dragField != null) {
                    double w = Math.max(40, right - 4 - inspX0 - 60);
                    double v = dragValue + (e.x() - dragX) / w * (dragField.max - dragField.min);
                    dragField.set.accept(clampStep(dragField, v));
                }
            }
            default -> {
                return super.mouseDragged(e, dx, dy);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        Drag was = drag;
        drag = Drag.NONE;
        if (was == Drag.KEYS) {
            boolean moved = dragFrom.entrySet().stream().anyMatch(en -> en.getKey().t != en.getValue());
            if (moved) {
                for (Sequence.Track tr : seq.pistes) tr.sort();
                changed(true);
            } else if (!undo.isEmpty()) undo.pop();
            dragFrom.clear();
            return true;
        }
        if (was == Drag.VALUE) {
            dragField = null;
            changed(true);
            return true;
        }
        if (was == Drag.PLAYHEAD) return true;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (textBox != null || y < top) return super.mouseScrolled(x, y, scrollX, scrollY);
        for (Hit h : List.copyOf(hits))
            if (h.wheel != null && x >= h.x0 && x < h.x1 && y >= h.y0 && y < h.y1) {
                h.wheel.accept(Math.signum(scrollY));
                return true;
            }
        if (x >= timeX0 && x <= timeX1) {
            if (minecraft.hasControlDown()) {
                double at = tOf(x);
                pxPerSec = Math.max(4, Math.min(400, pxPerSec * (scrollY > 0 ? 1.2 : 1 / 1.2)));
                viewStart = Math.max(0, at - (x - timeX0) / pxPerSec);
            } else if (hasShift() || y < lanesY0) {
                viewStart = Math.max(0, Math.min(Math.max(0, seq.duree - 1), viewStart - scrollY * 40 / pxPerSec));
            } else {
                trackScroll = Math.max(0, trackScroll - (int) Math.signum(scrollY));
            }
            return true;
        }
        if (x < tracksX1) {
            trackScroll = Math.max(0, trackScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (x >= inspX0) {
            inspScroll = Math.max(0, inspScroll - (int) Math.signum(scrollY));
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (textBox != null) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                closeText(true);
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                closeText(false);
                return true;
            }
            return super.keyPressed(e);
        }
        int letter = ClientCompat.shortcutKey(e);
        boolean ctrl = e.hasControlDown(), shift = e.hasShiftDown();
        if (ctrl && letter == 'z') {
            undo(!shift);
            return true;
        }
        if (ctrl && letter == 'y') {
            undo(false);
            return true;
        }
        if (ctrl && letter == 'c') {
            copy();
            return true;
        }
        if (ctrl && letter == 'v') {
            paste(playhead);
            return true;
        }
        if (ctrl && letter == 'd') {
            duplicateSelection();
            return true;
        }
        if (ctrl && letter == 'a') {
            Sequence.Track tr = currentTrack();
            selected.clear();
            if (tr != null) selected.addAll(tr.cles);
            return true;
        }
        switch (key) {
            case InputConstants.KEY_SPACE -> {
                if (isPlaying()) stop();
                else play();
                return true;
            }
            case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> {
                deleteSelected();
                return true;
            }
            case InputConstants.KEY_HOME -> {
                seek(0);
                viewStart = 0;
                return true;
            }
            case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> {
                double d = (key == InputConstants.KEY_LEFT ? -1 : 1) * (shift ? 1 : SequenceTools.SNAP);
                if (selected.isEmpty()) seek(playhead + d);
                else {
                    snapshot();
                    SequenceTools.shift(selected, d, seq.duree);
                    for (Sequence.Track tr : seq.pistes) tr.sort();
                    changed(true);
                }
                return true;
            }
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                track = Math.max(0, Math.min(seq.pistes.size() - 1, track + (key == InputConstants.KEY_UP ? -1 : 1)));
                selected.clear();
                return true;
            }
            default -> {
                return super.keyPressed(e);
            }
        }
    }

    @Override
    public void onClose() {
        closeText(false);
        flushSave();
        send(order("close"));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- text

    static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0).replace('.', ',');
    }

    static String secs(double d) {
        return num(d) + " s";
    }

    /** What a track's target can be: the catalog's names for its kind. */
    private List<String> options(String type) {
        List<String> l = new ArrayList<>();
        if (catalog.has(type)) for (JsonElement e : catalog.getAsJsonArray(type)) l.add(e.getAsString());
        return l;
    }
}
