package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.data.Task;
import fr.lordfinn.craftmaster.task.Note;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The organisers' note editor: text and look, who may read it, taken by the first finder or left in place,
 * invisible ink, torn pieces; on the right, where it is hidden and who found it when. Buttons give the hiding tool,
 * put the note in a bottle, put the taken notes back, remove the hiding places or delete the note.
 */
public class NoteEditorScreen extends Screen {

    private final TaskNetwork.NoteView note;
    private String title, text, look, readers, players;
    private boolean once, ink;
    private int pieces;
    private boolean confirmDelete;

    private EditBox playersBox;
    private int px, py, pw, ph, leftW, listX, listW;
    private double listScroll;

    public NoteEditorScreen(TaskNetwork.NoteView note) {
        super(Component.translatable("screen.craftmaster.note_editor"));
        this.note = note;
        title = note.title();
        text = note.text();
        look = note.look();
        readers = note.readers();
        players = String.join(", ", note.players());
        once = note.once();
        ink = note.ink();
        pieces = note.pieces();
    }

    private boolean isNew() {
        return note.id().isEmpty();
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        leftW = isNew() ? pw - 20 : (pw - 20) * 3 / 5;
        int x = px + 10, w = leftW;
        listX = x + leftW + 10;
        listW = px + pw - 10 - listX;
        int y = py + 34;

        EditBox titleBox = addRenderableWidget(new EditBox(font, x, y, w, 18, Component.translatable("screen.craftmaster.task.title")));
        titleBox.setMaxLength(64);
        titleBox.setValue(title);
        titleBox.setHint(Component.translatable("screen.craftmaster.note.title_hint").withStyle(ChatFormatting.DARK_GRAY));
        titleBox.setResponder(v -> title = v);
        y += 32;
        int bottom = py + ph - 56;
        int textH = Math.max(36, bottom - y - 50);
        MultiLineEditBox textBox = addRenderableWidget(MultiLineEditBox.builder().setX(x).setY(y)
                .setPlaceholder(Component.translatable("screen.craftmaster.note.text_hint").withStyle(ChatFormatting.DARK_GRAY))
                .build(font, w, textH, Component.translatable("screen.craftmaster.task.text")));
        textBox.setCharacterLimit(4000);
        textBox.setValue(text);
        textBox.setValueListener(v -> text = v);
        y += textH + 4;

        // Options: two rows of three
        int cw = (w - 8) / 3;
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("note.craftmaster.look." + v), look)
                .withValues(Note.LOOKS).create(x, y, cw, 20, Component.translatable("screen.craftmaster.note.look"), (b, v) -> look = v));
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable("note.craftmaster.readers." + v), readers)
                .withValues(Note.READERS).displayOnlyValue().withTooltip(v -> Tooltip.create(Component.translatable("note.craftmaster.readers." + v + ".tip")))
                .create(x + cw + 4, y, cw, 20, Component.translatable("screen.craftmaster.note.readers"), (b, v) -> {
                    readers = v;
                    playersBox.visible = v.equals(Note.PLAYERS);
                }));
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)), pieces)
                .withValues(List.of(1, 2, 3, 4, 5, 6)).withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.note.pieces.tip")))
                .create(x + 2 * (cw + 4), y, cw, 20, Component.translatable("screen.craftmaster.note.pieces"), (b, v) -> pieces = v));
        y += 24;
        addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.note.once_on"),
                        Component.translatable("screen.craftmaster.note.once_off"), once).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(v ? "screen.craftmaster.note.once_on.tip" : "screen.craftmaster.note.once_off.tip")))
                .create(x, y, cw, 20, Component.empty(), (b, v) -> once = v));
        addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.note.ink"),
                        Component.translatable("screen.craftmaster.note.ink_off"), ink).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.note.ink.tip")))
                .create(x + cw + 4, y, cw, 20, Component.empty(), (b, v) -> ink = v));
        playersBox = addRenderableWidget(new EditBox(font, x + 2 * (cw + 4), y + 1, cw, 18, Component.translatable("screen.craftmaster.note.players")));
        playersBox.setMaxLength(200);
        playersBox.setValue(players);
        playersBox.setHint(Component.translatable("screen.craftmaster.note.players_hint").withStyle(ChatFormatting.DARK_GRAY));
        playersBox.setResponder(v -> players = v);
        playersBox.visible = readers.equals(Note.PLAYERS);

        // Buttons
        List<Button> row = new ArrayList<>();
        row.add(Button.builder(Component.translatable(isNew() ? "screen.craftmaster.note.create" : "screen.craftmaster.task.save"), b -> save()).build());
        row.add(Button.builder(Component.translatable("screen.craftmaster.task.preview"), b -> preview()).build());
        if (!isNew()) {
            row.add(Button.builder(Component.translatable("screen.craftmaster.note.tool"), b -> order("give"))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.note.tool.tip"))).build());
            row.add(Button.builder(Component.translatable("screen.craftmaster.note.bottle"), b -> order("bottle"))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.note.bottle.tip"))).build());
        }
        row.add(Button.builder(Component.translatable("gui.done"), b -> onClose()).build());
        layoutRow(row, py + ph - 50);
        if (!isNew()) {
            List<Button> row2 = new ArrayList<>();
            row2.add(Button.builder(Component.translatable("screen.craftmaster.note.reset"), b -> order("reset"))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.note.reset.tip"))).build());
            row2.add(Button.builder(Component.translatable("screen.craftmaster.note.unhide"), b -> order("unhide"))
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.note.unhide.tip"))).build());
            row2.add(Button.builder(Component.translatable(confirmDelete ? "screen.craftmaster.note.delete_confirm" : "screen.craftmaster.note.delete"), b -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    rebuildWidgets();
                    return;
                }
                order("delete");
                onClose();
            }).build());
            layoutRow(row2, py + ph - 26);
        }
    }

    private void layoutRow(List<Button> row, int y) {
        int bw = (pw - 20 - (row.size() - 1) * 4) / row.size();
        int bx = px + 10;
        for (Button b : row) {
            b.setRectangle(bw, 20, bx, y);
            addRenderableWidget(b);
            bx += bw + 4;
        }
    }

    private TaskNetwork.NoteView edited() {
        List<String> names = Arrays.stream(players.split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
        return new TaskNetwork.NoteView(note.id(), title, text, look, readers, names, once, ink, pieces, List.of(), List.of());
    }

    private void save() {
        if (ClientPlayNetworking.canSend(TaskNetwork.SaveNote.TYPE)) ClientPlayNetworking.send(new TaskNetwork.SaveNote(edited()));
        onClose();
    }

    private void order(String order) {
        TaskClient.noteOrder(note.id(), order);
    }

    /** The note as a finder reads it (ink lit, all pieces found). */
    private void preview() {
        String t = Task.cleanText(text);
        List<String> parts = pieces > 1 ? Note.split(t, pieces) : List.of();
        int flags = TaskNetwork.FOUND | (ink ? TaskNetwork.INK | TaskNetwork.LIT : 0);
        Component header = Component.translatable("paper.craftmaster.preview");
        minecraft.gui.setScreen(new PaperScreen(new TaskNetwork.Paper("note", header, title.strip(), t, List.of(), 0, "red", look, flags, parts, note.id(), 0, ""), this));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX) {
            listScroll = Math.max(0, listScroll - sy * 10);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        TaskStyle.panel(g, px, py, pw, ph);
        Component head = isNew() ? Component.translatable("screen.craftmaster.note.new")
                : Component.translatable("screen.craftmaster.note_editor").append(" · " + note.id());
        TaskStyle.title(g, font, head.copy().withStyle(ChatFormatting.BOLD), px + pw / 2, py + 8);
        int x = px + 10;
        TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.title"), x, py + 23);
        TaskStyle.label(g, font, Component.translatable("screen.craftmaster.task.text"), x, py + 55);
        if (!isNew()) {
            // Hiding places and finds
            int top = py + 23, bottom = py + ph - 56;
            g.fill(listX - 4, top - 3, px + pw - 8, bottom, 0x40000000);
            g.enableScissor(listX - 4, top - 3, px + pw - 8, bottom);
            int y = top - (int) listScroll;
            y = section(g, Component.translatable("screen.craftmaster.note.spots", note.spots().size()), note.spots(),
                    Component.translatable("screen.craftmaster.note.no_spot"), y);
            y += 6;
            y = section(g, Component.translatable("screen.craftmaster.note.finds", note.finds().size()), note.finds(),
                    Component.translatable("screen.craftmaster.note.no_find"), y);
            int content = y + (int) listScroll - top;
            listScroll = Math.min(listScroll, Math.max(0, content - (bottom - top)));
            g.disableScissor();
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private int section(GuiGraphicsExtractor g, Component title, List<Component> lines, Component empty, int y) {
        g.text(font, title, listX, y, TaskStyle.GOLD, false);
        y += 12;
        if (lines.isEmpty()) {
            g.text(font, empty, listX + 4, y, TaskStyle.HINT, false);
            return y + 10;
        }
        for (Component l : lines) {
            for (FormattedCharSequence s : font.split(Component.literal("• ").append(l), listW - 6)) {
                g.text(font, s, listX + 2, y, TaskStyle.LABEL, false);
                y += 10;
            }
            y += 1;
        }
        return y;
    }
}
