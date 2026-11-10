package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.data.Task;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The organisers' task editor: the rounds of the active game on the left (● has a task, ✔ revealed, ▶ being
 * played), the selected round's task on the right — title, text (paragraphs), rules (one per line), duration
 * shown, wax colour, revealed when the round starts or only by the envelopes. "Enregistrer" sends a
 * {@code task.set} action (undoable, same as the voice and the commands); switching round saves too.
 * "Aperçu" plays the envelope opening with the text as typed.
 */
public class TaskEditorScreen extends Screen {

    /** What is being typed for a round. */
    private static final class Draft {
        String title, text, rules, seconds;
        WaxColor wax;
        boolean revealOnStart, dirty;
    }

    private final List<TaskNetwork.Round> rounds;
    private final Map<String, Draft> drafts = new HashMap<>();
    private String selected;
    private int listScroll;

    private EditBox title, seconds;
    private MultiLineEditBox text, rules;
    private CycleButton<Boolean> onStart;
    private int px, py, pw, ph, rx, rw, swatchX, swatchY;
    private boolean loading;

    public TaskEditorScreen(TaskNetwork.OpenEditor open) {
        super(Component.translatable("screen.craftmaster.task_editor"));
        this.rounds = open.rounds();
        this.selected = open.selected();
        if (round(selected) == null && !rounds.isEmpty()) selected = rounds.getFirst().card().mancheId();
    }

    private TaskNetwork.Round round(String id) {
        for (TaskNetwork.Round r : rounds) if (r.card().mancheId().equals(id)) return r;
        return null;
    }

    private Draft draft(String id) {
        return drafts.computeIfAbsent(id, k -> {
            TaskNetwork.Round r = round(k);
            Draft d = new Draft();
            TaskNetwork.Card c = r == null ? TaskNetwork.Card.NONE : r.card();
            d.title = r != null && r.hasTask() ? c.title() : "";
            d.text = r != null && r.hasTask() ? c.text() : "";
            d.rules = String.join("\n", c.rules());
            // The card shows the round's timer when no own duration was set: the field stays empty then
            d.seconds = r != null && r.ownSeconds() > 0 ? String.valueOf(r.ownSeconds()) : "";
            d.wax = WaxColor.of(c.wax());
            d.revealOnStart = r == null || r.revealOnStart();
            return d;
        });
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int listW = Math.min(130, pw / 3);
        rx = px + listW + 16;
        rw = px + pw - 10 - rx;

        // Rounds
        int y = py + 28;
        int rowH = 18, visible = Math.max(1, (ph - 28 - 34) / (rowH + 2));
        listScroll = Math.max(0, Math.min(listScroll, rounds.size() - visible));
        for (int i = listScroll; i < Math.min(rounds.size(), listScroll + visible); i++) {
            TaskNetwork.Round r = rounds.get(i);
            String id = r.card().mancheId();
            String mark = (r.current() ? "▶ " : "") + (r.hasTask() || draft(id).dirty ? r.card().revealed() ? "✔ " : "● " : "○ ");
            Component label = Component.literal(mark + r.card().number() + ". " + r.card().mancheName());
            Button b = Button.builder(label, btn -> select(id)).bounds(px + 8, y, listW, rowH)
                    .tooltip(Tooltip.create(Component.translatable(r.hasTask() ? r.card().revealed() ? "screen.craftmaster.task.state_revealed"
                            : "screen.craftmaster.task.state_secret" : "screen.craftmaster.task.state_none"))).build();
            b.active = !id.equals(selected);
            addRenderableWidget(b);
            y += rowH + 2;
        }
        if (rounds.isEmpty()) return;

        // The task of the selected round
        Draft d = draft(selected);
        loading = true;
        int fy = py + 50;
        title = addRenderableWidget(new EditBox(font, rx, fy, rw, 18, Component.translatable("screen.craftmaster.task.title")));
        title.setMaxLength(80);
        title.setHint(Component.translatable("screen.craftmaster.task.title_hint").withStyle(ChatFormatting.DARK_GRAY));
        title.setValue(d.title);
        title.setResponder(v -> changed(dd -> dd.title = v));
        fy += 18 + 14;
        int bottom = py + ph - 32;
        int rulesH = ph < 260 ? 28 : 40, optionsH = 40;
        int textH = Math.max(30, bottom - fy - rulesH - 22 - optionsH - 6);
        text = addRenderableWidget(MultiLineEditBox.builder().setX(rx).setY(fy)
                .setPlaceholder(Component.translatable("screen.craftmaster.task.text_hint").withStyle(ChatFormatting.DARK_GRAY))
                .build(font, rw, textH, Component.translatable("screen.craftmaster.task.text")));
        text.setCharacterLimit(4000);
        text.setValue(d.text);
        text.setValueListener(v -> changed(dd -> dd.text = v));
        // Room for the character counter drawn under the text
        fy += textH + 22;
        rules = addRenderableWidget(MultiLineEditBox.builder().setX(rx).setY(fy).setShowDecorations(false)
                .setPlaceholder(Component.translatable("screen.craftmaster.task.rules_hint").withStyle(ChatFormatting.DARK_GRAY))
                .build(font, rw, rulesH, Component.translatable("screen.craftmaster.task.rules")));
        rules.setCharacterLimit(1500);
        rules.setValue(d.rules);
        rules.setValueListener(v -> changed(dd -> dd.rules = v));
        fy += rulesH + 6;
        // Options row: duration, wax, reveal on start
        seconds = addRenderableWidget(new EditBox(font, rx + 44, fy, 40, 18, Component.translatable("screen.craftmaster.task.seconds")));
        seconds.setMaxLength(6);
        seconds.setValue(d.seconds);
        TaskNetwork.Round sel = round(selected);
        int timer = sel.ownSeconds() > 0 ? 0 : sel.card().seconds();
        seconds.setHint(Component.literal(timer > 0 ? String.valueOf(timer) : "—").withStyle(ChatFormatting.DARK_GRAY));
        seconds.setTooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.seconds.tip")));
        seconds.setResponder(v -> changed(dd -> dd.seconds = v));
        // Second row: the wax colours (the reveal switch shares the first row with the duration)
        swatchX = rx + 44;
        swatchY = fy + 26;
        int cycleX = rx + 92;
        onStart = addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.task.on_start"),
                        Component.translatable("screen.craftmaster.task.on_envelope"), d.revealOnStart).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(v ? "screen.craftmaster.task.on_start.tip" : "screen.craftmaster.task.on_envelope.tip")))
                .create(cycleX, fy, Math.max(40, rx + rw - cycleX), 18, Component.empty(), (b, v) -> changed(dd -> dd.revealOnStart = v)));
        loading = false;

        // Buttons
        int by = py + ph - 26;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.save"), b -> save(selected)).build());
        buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.preview"), b -> preview())
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.preview.tip"))).build());
        buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.reveal_short"), b -> act("task.reveal"))
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.reveal.tip"))).build());
        buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.show_short"), b -> act("task.show"))
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.show.tip"))).build());
        buttons.add(Button.builder(Component.translatable("screen.craftmaster.task.envelopes"), b -> act("envelope.give"))
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.task.envelopes.tip"))).build());
        buttons.add(Button.builder(Component.translatable("gui.done"), b -> onClose()).build());
        int bw = (pw - 16 - (buttons.size() - 1) * 4) / buttons.size();
        int bx = px + 8;
        for (Button b : buttons) {
            b.setRectangle(bw, 20, bx, by);
            addRenderableWidget(b);
            bx += bw + 4;
        }
    }

    private void changed(java.util.function.Consumer<Draft> f) {
        if (loading || selected == null) return;
        Draft d = draft(selected);
        f.accept(d);
        d.dirty = true;
    }

    private void select(String id) {
        if (selected != null && draft(selected).dirty) save(selected);
        selected = id;
        rebuildWidgets();
    }

    /** The task.set action of a round's draft. */
    private Map<String, Object> setAction(String id) {
        Draft d = draft(id);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", "task.set");
        a.put("manche", id);
        TaskNetwork.Round r = round(id);
        if (r != null) a.put("season", r.card().season());
        a.put("title", d.title.strip());
        a.put("text", d.text);
        a.put("rules", Task.parseRules(d.rules));
        int s = parseSeconds(d.seconds);
        a.put("seconds", Math.max(0, s));
        a.put("wax", d.wax.id());
        a.put("revealOnStart", d.revealOnStart);
        return a;
    }

    /** "90", "1:30", "" → seconds (0 = the round's timer); -1 if unreadable. */
    static int parseSeconds(String s) {
        String v = s == null ? "" : s.strip();
        if (v.isEmpty()) return 0;
        try {
            if (v.contains(":")) {
                String[] p = v.split(":");
                return Integer.parseInt(p[0].strip()) * 60 + Integer.parseInt(p[1].strip());
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void save(String id) {
        TaskClient.runActions(List.of(setAction(id)));
        draft(id).dirty = false;
    }

    /** Saves first if needed, then the action on this round. */
    private void act(String type) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (draft(selected).dirty) {
            list.add(setAction(selected));
            draft(selected).dirty = false;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        a.put("manche", selected);
        TaskNetwork.Round r = round(selected);
        if (r != null) a.put("season", r.card().season());
        list.add(a);
        TaskClient.runActions(list);
    }

    private void preview() {
        Draft d = draft(selected);
        TaskNetwork.Round r = round(selected);
        int s = parseSeconds(d.seconds);
        Component header = Component.translatable("paper.craftmaster.round", r.card().number(), r.card().mancheName());
        TaskNetwork.Paper p = new TaskNetwork.Paper("task", header, d.title.strip(), Task.cleanText(d.text), Task.parseRules(d.rules),
                s > 0 ? s : r.card().seconds(), d.wax.id(), "paper", TaskNetwork.ENVELOPE, List.of(), selected, r.card().season(), "");
        minecraft.gui.setScreen(new PaperScreen(p, this));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WaxColor c = TaskStyle.swatchAt(swatchX, swatchY, event.x(), event.y());
        if (c != null && selected != null) {
            changed(dd -> dd.wax = c);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < rx - 8) {
            int before = listScroll;
            listScroll = Math.max(0, listScroll - (int) Math.signum(sy));
            if (listScroll != before) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        if (selected != null && draft(selected).dirty) save(selected);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        TaskStyle.panel(g, px, py, pw, ph);
        TaskStyle.title(g, font, Component.translatable("screen.craftmaster.task_editor").withStyle(ChatFormatting.BOLD), px + pw / 2, py + 8);
        if (rounds.isEmpty()) {
            g.centeredText(font, Component.translatable("screen.craftmaster.task.no_rounds"), px + pw / 2, py + ph / 2, TaskStyle.HINT);
        } else {
            TaskNetwork.Round r = round(selected);
            Draft d = draft(selected);
            String head = r.card().number() + ". " + r.card().mancheName() + (d.dirty ? "  •" : "");
            g.text(font, Component.literal(head).withStyle(ChatFormatting.BOLD), rx, py + 24, TaskStyle.GOLD, false);
            if (d.dirty) g.text(font, Component.translatable("screen.craftmaster.task.unsaved"), rx + font.width(head) + 14, py + 24, TaskStyle.HINT, false);
            if (text != null) {
                TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.title"), rx, title.getY() - 11);
                TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.text"), rx, text.getY() - 11);
                TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.rules"), rx, rules.getY() - 11);
                TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.seconds_label"), rx, seconds.getY() + 5);
                TaskStyle.label(g, font, Component.translatable("screen.craftmaster.seal.wax"), rx, swatchY + 2);
                TaskStyle.swatches(g, swatchX, swatchY, d.wax, mouseX, mouseY);
                WaxColor hover = TaskStyle.swatchAt(swatchX, swatchY, mouseX, mouseY);
                if (hover != null) g.setTooltipForNextFrame(font, TaskStyle.waxName(hover), mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
