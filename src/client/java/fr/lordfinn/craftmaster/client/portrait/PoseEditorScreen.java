package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
import fr.lordfinn.craftmaster.portrait.PoseEditing;
import fr.lordfinn.craftmaster.portrait.PoseHistory;
import fr.lordfinn.craftmaster.portrait.PoseHitTest;
import fr.lordfinn.craftmaster.portrait.PoseLibraries;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.PortraitNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * In-game pose editor (organisers). On the right, a 3D preview of the portrait: click a part of the body (or the
 * frame) to pick it, drag it to turn it, Ctrl + drag to move the whole figure, right drag (or drag the background)
 * to turn the view, the wheel for fine steps. On the left, the same values as sliders, the parts as buttons, and
 * the library (save, overwrite, delete, start from). Undo / redo, mirror, symmetric copy, reset, hide, all with
 * keyboard shortcuts (shown in the tooltips). The portrait in the world shows the pose live; saved poses go to
 * the server's poses.json and to every player.
 */
public class PoseEditorScreen extends Screen {

    private static final int PANEL = 0xD0101018, BORDER = 0xFFFFC23D, PREVIEW_BG = 0xE0180C10, HINT = 0xFFB8B0A0;
    private static final int W = 232, H = 236;
    /** Toolbar under the preview. */
    private static final int TOOL = 20, TOOLBAR = TOOL + 4;

    /** What the sliders act on. */
    enum Group {
        FIGURE(Part.FIGURE), HEAD(Part.HEAD), BODY(Part.BODY), RIGHT_ARM(Part.RIGHT_ARM), LEFT_ARM(Part.LEFT_ARM),
        RIGHT_LEG(Part.RIGHT_LEG), LEFT_LEG(Part.LEFT_LEG), FRAME(null);

        final Part part;

        Group(Part part) {
            this.part = part;
        }

        Component label() {
            return Component.translatable("screen.craftmaster.pose." + name().toLowerCase(Locale.ROOT));
        }

        static Group of(Part part) {
            for (Group g : values()) if (g.part == part) return g;
            return FRAME;
        }
    }

    private enum Drag { NONE, PART, FRAME, MOVE, ORBIT }

    private final PortraitEntity portrait;
    private final PortraitScreen parent;
    private final PoseHistory history;
    private Pose editing;
    private Group group = Group.FIGURE;
    private String name;
    private EditBox nameBox;
    private Button save, delete, undoButton, redoButton;
    private final List<ValueSlider> sliders = new ArrayList<>();
    private int left, top;

    // Preview: its box, the view (turntable), the axis the wheel and arrows turn, the gesture in progress
    private int px0, py0, pw, ph;
    private double yaw = 25, pitch = 12, zoom = 1;
    private PoseEditing.Axis wheelAxis = PoseEditing.Axis.X;
    private Drag drag = Drag.NONE;
    private PoseHitTest.Hit hover;

    public PoseEditorScreen(PortraitEntity portrait, PortraitScreen parent) {
        super(Component.translatable("screen.craftmaster.pose_editor"));
        this.portrait = portrait;
        this.parent = parent;
        Pose preview = PortraitClient.previewPose(portrait.getId());
        this.editing = preview != null ? preview : portrait.pose();
        this.history = new PoseHistory(editing);
        this.name = parent != null ? parent.portrait().poseName() : portrait.poseName();
        if (preview != null && parent != null) {
            // The settings screen was previewing another pose than the portrait's: edit that one
            for (var e : PoseLibraries.client().all().entrySet()) if (e.getValue().equals(preview)) name = e.getKey();
        }
    }

    void libraryChanged() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (nameBox != null) name = nameBox.getValue();
        super.rebuildWidgets();
    }

    // ---------------------------------------------------------------- changes

    /** Every change goes through the history (undo / redo) and shows at once in the preview and the world. */
    private void changed(Pose pose, String key) {
        if (!history.change(pose, key, System.currentTimeMillis())) return;
        show(history.current());
    }

    private void show(Pose pose) {
        boolean hiddenChanged = group.part != null && pose.isHidden(group.part) != editing.isHidden(group.part);
        editing = pose;
        PortraitClient.setPreview(portrait.getId(), editing);
        for (ValueSlider s : sliders) s.sync();
        updateButtons();
        // The shown / hidden switches follow (undo, mirror...)
        if (hiddenChanged) rebuildWidgets();
    }

    private void undo() {
        show(history.undo());
    }

    private void redo() {
        show(history.redo());
    }

    private void select(Group g) {
        if (g == group) return;
        group = g;
        history.seal();
        rebuildWidgets();
    }

    private void mirror() {
        changed(PoseEditing.mirror(editing), null);
    }

    /** Symmetric copy of the selected limb onto the other side. */
    private void copySide() {
        if (group.part != null && PoseEditing.sided(group.part)) changed(PoseEditing.copyToOtherSide(editing, group.part), null);
    }

    private void resetPart() {
        changed(group == Group.FRAME ? PoseEditing.resetFrame(editing) : PoseEditing.reset(editing, group.part), null);
    }

    private void toggleHidden() {
        if (group.part != null && group.part.hideable()) changed(editing.withHidden(group.part, !editing.isHidden(group.part)), null);
    }

    /** One fine step (wheel, arrow keys) on the wheel's axis of the selected part, or on the frame's tilt. */
    private void step(double degrees) {
        String key = "step:" + group + ":" + wheelAxis;
        if (group == Group.FRAME) {
            boolean pitchAxis = wheelAxis == PoseEditing.Axis.X;
            double p = editing.framePitch() + (pitchAxis ? degrees : 0), r = editing.frameRoll() + (pitchAxis ? 0 : degrees);
            changed(editing.withFrame(Math.max(-60, Math.min(60, p)), Math.max(-60, Math.min(60, r))), key);
        } else {
            changed(PoseEditing.turn(editing, group.part, wheelAxis, degrees), key);
        }
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        left = 4;
        top = Math.max(2, (height - H) / 2);
        px0 = left + W + 6;
        py0 = top;
        pw = Math.max(90, Math.min(240, width - px0 - 4));
        ph = H;
        PortraitClient.setPreview(portrait.getId(), editing);
        sliders.clear();

        // Part buttons, 4 per row, with their number key; the selected one is greyed out
        Group[] groups = Group.values();
        for (int i = 0; i < groups.length; i++) {
            Group g = groups[i];
            Button b = addRenderableWidget(Button.builder(g.label(), x -> select(g))
                    .bounds(left + 1 + (i % 4) * 58, top + 18 + (i / 4) * 22, 56, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.pose.pick_tip", g.label(), String.valueOf(i + 1))))
                    .build());
            b.active = g != group;
        }

        // Sliders of the selected group, two columns
        int cx0 = left + 2, cx1 = left + 117, cy = top + 66, sw = 113;
        if (group == Group.FRAME) {
            // Lambdas, not editing::x: they must read the pose current when they run, not the one at creation
            slider(cx0, cy, sw, "pitch", -60, 60, 1, () -> editing.framePitch(), v -> editing.withFrame(v, editing.frameRoll()));
            slider(cx1, cy, sw, "roll", -60, 60, 1, () -> editing.frameRoll(), v -> editing.withFrame(editing.framePitch(), v));
        } else {
            Part part = group.part;
            slider(cx0, cy, sw, "rot_x", -180, 180, 1, () -> editing.rotation(part).x(), v -> {
                Rotation r = editing.rotation(part);
                return editing.withRotation(part, new Rotation(v, r.y(), r.z()));
            });
            slider(cx1, cy, sw, "rot_y", -180, 180, 1, () -> editing.rotation(part).y(), v -> {
                Rotation r = editing.rotation(part);
                return editing.withRotation(part, new Rotation(r.x(), v, r.z()));
            });
            slider(cx0, cy + 22, sw, "rot_z", -180, 180, 1, () -> editing.rotation(part).z(), v -> {
                Rotation r = editing.rotation(part);
                return editing.withRotation(part, new Rotation(r.x(), r.y(), v));
            });
            if (part.hideable()) {
                addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.pose.shown"),
                                Component.translatable("screen.craftmaster.pose.hidden"), !editing.isHidden(part)).displayOnlyValue()
                        .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.pose.hide_tip")))
                        .create(cx1, cy + 22, sw, 20, Component.empty(), (b, v) -> changed(editing.withHidden(part, !v), null)));
            } else {
                slider(cx1, cy + 22, sw, "scale", 0.5, 2.5, 0.05, () -> editing.scale(), v -> editing.withScale(v));
                slider(cx0, cy + 44, sw, "offset_x", -24, 24, 0.5, () -> editing.offsetX(), v -> editing.withOffset(v, editing.offsetY(), editing.offsetZ()));
                slider(cx1, cy + 44, sw, "offset_y", -48, 24, 0.5, () -> editing.offsetY(), v -> editing.withOffset(editing.offsetX(), v, editing.offsetZ()));
                // Negative: behind the canvas, for figures coming out of the back of the portrait
                slider(cx0, cy + 66, sw, "offset_z", -24, 24, 0.5, () -> editing.offsetZ(), v -> editing.withOffset(editing.offsetX(), editing.offsetY(), v));
            }
        }

        // Name, save / overwrite, delete
        int y = top + 160;
        nameBox = new EditBox(font, left + 2, y, 128, 20, Component.translatable("screen.craftmaster.pose.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(name);
        nameBox.setResponder(v -> updateButtons());
        addRenderableWidget(nameBox);
        save = addRenderableWidget(Button.builder(Component.empty(), b -> save()).bounds(left + 132, y, 98, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.pose.save_tip"))).build());
        y += 22;
        delete = addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.pose.delete"), b -> delete())
                .bounds(left + 2, y, 74, 20).build());
        List<String> names = new ArrayList<>(PoseLibraries.client().names());
        if (!names.isEmpty()) {
            String current = names.contains(Pose.normalizeName(name)) ? Pose.normalizeName(name) : names.getFirst();
            addRenderableWidget(CycleButton.<String>builder(Component::literal, current).withValues(names)
                    .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.pose.load_tip")))
                    .create(left + 78, y, 152, 20, Component.translatable("screen.craftmaster.pose.load"), (b, v) -> {
                        // Loading is a change like any other: undo brings the previous pose back
                        changed(PoseLibraries.client().get(v), null);
                        name = v;
                        rebuildWidgets();
                        nameBox.setValue(v);
                    }));
        }
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.pose.use"), b -> use())
                .bounds(left + 2, y, 113, 20).tooltip(Tooltip.create(Component.translatable("screen.craftmaster.pose.use_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 117, y, 113, 20).build());

        // Toolbar under the preview
        int tx = px0 + 2, ty = py0 + ph - TOOL - 2;
        // Symbols from the font's full-size glyphs (code page 437 / Latin-1), the tooltips say what they do
        undoButton = tool(tx, ty, "←", "undo", this::undo);
        redoButton = tool(tx += TOOL + 1, ty, "→", "redo", this::redo);
        tool(tx += TOOL + 1, ty, "↔", "mirror", this::mirror);
        Button copy = tool(tx += TOOL + 1, ty, "»", "copy_side", this::copySide);
        copy.active = group.part != null && PoseEditing.sided(group.part);
        tool(tx += TOOL + 1, ty, "0", "reset", this::resetPart);
        boolean hidden = group.part != null && editing.isHidden(group.part);
        Button hide = tool(tx += TOOL + 1, ty, hidden ? "○" : "•", "hide", this::toggleHidden);
        hide.active = group.part != null && group.part.hideable();
        tool(tx += TOOL + 1, ty, "⌂", "view", this::resetView);
        updateButtons();
    }

    private void slider(int x, int y, int w, String key, double min, double max, double step, DoubleSupplier get,
                        java.util.function.DoubleFunction<Pose> set) {
        String historyKey = "slider:" + group + ":" + key;
        sliders.add(addRenderableWidget(new ValueSlider(x, y, w, "screen.craftmaster.pose." + key, min, max, step, get,
                v -> changed(set.apply(v), historyKey))));
    }

    private Button tool(int x, int y, String symbol, String key, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(symbol), b -> action.run()).bounds(x, y, TOOL, TOOL)
                .tooltip(Tooltip.create(Component.translatable("screen.craftmaster.pose.tool." + key))).build());
    }

    private void resetView() {
        yaw = 25;
        pitch = 12;
        zoom = 1;
    }

    private void updateButtons() {
        if (nameBox == null || save == null) return;
        String n = Pose.normalizeName(nameBox.getValue());
        boolean exists = PoseLibraries.client().has(n);
        save.setMessage(Component.translatable(exists ? "screen.craftmaster.pose.overwrite" : "screen.craftmaster.pose.save_new"));
        save.active = !n.isEmpty();
        delete.active = exists;
        if (undoButton != null) undoButton.active = history.canUndo();
        if (redoButton != null) redoButton.active = history.canRedo();
    }

    // ---------------------------------------------------------------- preview geometry

    /** The preview's view: portrait pixels → GUI pixels, the frame's centre in the middle of the picture. */
    PoseHitTest.View view() {
        int boxH = ph - TOOLBAR;
        double scale = Math.min(pw / 34.0, boxH / 40.0) * zoom;
        return new PoseHitTest.View(yaw, pitch, scale, px0 + pw / 2.0, py0 + boxH / 2.0);
    }

    private boolean inPreview(double x, double y) {
        return x >= px0 && x < px0 + pw && y >= py0 && y < py0 + ph - TOOLBAR;
    }

    private boolean slimSkin() {
        return skin().slim();
    }

    private PortraitSkins.Skin skin() {
        return portrait.anonymous() ? PortraitSkins.defaultSkin() : PortraitSkins.resolve(portrait.shownPlayer(), portrait.skinUrl());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        if (!inPreview(e.x(), e.y())) return false;
        setFocused(null);
        if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            drag = Drag.ORBIT;
            return true;
        }
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        PoseHitTest.Hit hit = PoseHitTest.pick(editing, view(), e.x(), e.y(), slimSkin());
        history.seal();
        if (e.hasControlDown() && hit != null && !hit.frame()) {
            drag = Drag.MOVE;
        } else if (hit == null) {
            drag = Drag.ORBIT;
        } else {
            select(Group.of(hit.part()));
            drag = hit.frame() ? Drag.FRAME : Drag.PART;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (drag == Drag.NONE) return super.mouseDragged(e, dx, dy);
        switch (drag) {
            case ORBIT -> {
                PoseHitTest.View v = view().turned(dx * 0.8, dy * 0.8);
                yaw = v.yaw();
                pitch = v.pitch();
            }
            case MOVE -> {
                double s = view().scale();
                changed(PoseEditing.move(editing, dx / s, -dy / s), "drag");
            }
            case FRAME -> changed(PoseEditing.dragFrame(editing, dx, dy), "drag");
            case PART -> {
                if (group.part != null) {
                    boolean alternate = e.hasShiftDown();
                    changed(PoseEditing.drag(editing, group.part, dx, dy, alternate), "drag");
                    // The wheel then fine-tunes what the drag moved most
                    PoseEditing.Axis[] axes = PoseEditing.dragAxes(group.part, alternate);
                    wheelAxis = Math.abs(dx) >= Math.abs(dy) ? axes[0] : axes[1];
                }
            }
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        // One drag, one slider move = one undo step
        history.seal();
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!inPreview(x, y) || scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (minecraft.hasControlDown()) {
            zoom = Math.max(0.5, Math.min(3, zoom * (scrollY > 0 ? 1.15 : 1 / 1.15)));
        } else {
            step(Math.signum(scrollY) * (minecraft.hasShiftDown() ? 5 : 1));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (nameBox != null && nameBox.isFocused() || getFocused() instanceof AbstractSliderButton) return super.keyPressed(e);
        // Letters by what the key types (Ctrl+Z is Ctrl+Z on an AZERTY keyboard too), digits and arrows by place
        int letter = e.shortcutKey(), key = e.key();
        boolean ctrl = e.hasControlDown(), shift = e.hasShiftDown();
        if (ctrl && letter == 'z') {
            if (shift) redo();
            else undo();
            return true;
        }
        if (ctrl && letter == 'y') {
            redo();
            return true;
        }
        if (ctrl) return super.keyPressed(e);
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_8) {
            select(Group.values()[key - InputConstants.KEY_1]);
            return true;
        }
        switch (letter) {
            case 'm' -> mirror();
            case 'c' -> copySide();
            case 'r' -> resetPart();
            case 'h' -> toggleHidden();
            case 'x' -> wheelAxis = PoseEditing.Axis.X;
            case 'y' -> wheelAxis = PoseEditing.Axis.Y;
            case 'z' -> wheelAxis = PoseEditing.Axis.Z;
            default -> {
                if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_UP) step(shift ? 5 : 1);
                else if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_DOWN) step(shift ? -5 : -1);
                else if (key == InputConstants.KEY_HOME) resetView();
                else return super.keyPressed(e);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- library

    private void save() {
        String n = Pose.normalizeName(nameBox.getValue());
        if (n.isEmpty()) return;
        name = n;
        ClientPlayNetworking.send(new PortraitNetwork.SavePose(n, editing.toJson().toString()));
    }

    private void delete() {
        String n = Pose.normalizeName(nameBox.getValue());
        if (PoseLibraries.client().has(n)) ClientPlayNetworking.send(new PortraitNetwork.DeletePose(n));
    }

    /** Saves the pose and gives it to the portrait (through its settings screen when opened from there). */
    private void use() {
        save();
        if (name.isEmpty()) return;
        if (parent != null) {
            parent.selectPose(name);
        } else {
            PortraitEntity p = portrait;
            ClientPlayNetworking.send(new PortraitNetwork.Update(p.getId(), p.season(), p.followsRank() ? p.rank() : 0,
                    p.fixedPlayer(), p.displayName(), p.skinUrl(), name, p.hangSetting(), p.window(), p.windowDepth(), p.windowSize()));
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        PortraitClient.setPreview(portrait.getId(), null);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + W, top + H, PANEL);
        g.outline(left, top, W, H, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.pose_editor").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                left + 4, top + 5, 0xFFFFFFFF);
        g.text(font, group.label().copy().withStyle(ChatFormatting.YELLOW), left + 120, top + 5, 0xFFFFFFFF);
        extractPreview(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void extractPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int boxH = ph - TOOLBAR;
        g.fill(px0, py0, px0 + pw, py0 + ph, PREVIEW_BG);
        g.outline(px0, py0, pw, ph, BORDER);
        PoseHitTest.View view = view();
        hover = drag == Drag.NONE && inPreview(mouseX, mouseY) ? PoseHitTest.pick(editing, view, mouseX, mouseY, slimSkin()) : null;

        PortraitRenderer.State s = PortraitRenderer.previewState(editing, skin());
        s.selected = group.part;
        s.frameSelected = group == Group.FRAME;
        if (hover != null) {
            s.hovered = hover.part();
            s.frameHovered = hover.frame();
        }
        s.view = previewMatrix(view);
        // Drawn with the portrait's own renderer (same model, skin, cape and frame), at the view's scale
        float blocks = (float) (view.scale() * 16);
        g.entity(s, blocks, new Vector3f(0, 0, 0), new Quaternionf(), null, px0 + 1, py0 + 1, px0 + pw - 1, py0 + boxH);

        // Header: the part being edited and the axis of the wheel / arrows; footer: how to use the mouse
        Component part = group.label().copy().withStyle(ChatFormatting.YELLOW);
        g.text(font, Component.translatable("screen.craftmaster.pose.preview_head", part, wheelAxis.name()), px0 + 4, py0 + 4, 0xFFFFFFFF);
        Component help = Component.translatable("screen.craftmaster.pose.help_short").withStyle(ChatFormatting.GRAY);
        int hx = px0 + pw - 4 - font.width(help);
        g.text(font, help, hx, py0 + 4, HINT);
        if (mouseX >= hx && mouseX < px0 + pw && mouseY >= py0 + 2 && mouseY < py0 + 14) {
            List<Component> lines = new ArrayList<>();
            for (int i = 1; i <= 9; i++) lines.add(Component.translatable("screen.craftmaster.pose.help." + i));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        } else if (hover != null && drag == Drag.NONE) {
            Component what = hover.frame() ? Group.FRAME.label() : Group.of(hover.part()).label();
            g.setTooltipForNextFrame(Component.translatable("screen.craftmaster.pose.hover", what), mouseX, mouseY);
        }
    }

    /**
     * Portrait space → the picture: the view's turntable, then the GUI's axes (y down in the picture), pixels
     * of portrait space being 1/16 block (the renderer scales them).
     */
    static Matrix4f previewMatrix(PoseHitTest.View view) {
        return new Matrix4f().scale(1, -1, -1).mul(new Matrix4f(view.rotation()));
    }

    /** A slider between min and max, snapped to step, showing "label: value", following the pose when it changes. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final String key;
        private final double min, max, step;
        private final DoubleSupplier get;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, String key, double min, double max, double step, DoubleSupplier get, DoubleConsumer onChange) {
            super(x, y, w, 20, Component.empty(), 0);
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.onChange = onChange;
            sync();
        }

        /** Shows the pose's current value (after a drag in the preview, an undo...). */
        void sync() {
            value = (Math.max(min, Math.min(max, get.getAsDouble())) - min) / (max - min);
            updateMessage();
        }

        private double current() {
            double v = min + (max - min) * value;
            return Math.round(v / step) * step;
        }

        @Override
        protected void updateMessage() {
            if (get == null) return;
            double v = Math.round(get.getAsDouble() / step) * step;
            String text = step >= 1 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0$", "");
            setMessage(Component.translatable(key, text));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
