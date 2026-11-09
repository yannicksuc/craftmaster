package fr.lordfinn.craftmaster.client.portrait;

import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.Pose.Rotation;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * In-game pose editor (organisers): pick a part of the body (or the whole figure, or the frame), turn and move it
 * with sliders while the portrait in the world shows the result live, then save under a new name, overwrite an
 * existing pose or delete one. Saved poses go to the server's poses.json and to every player.
 */
public class PoseEditorScreen extends Screen {

    private static final int PANEL = 0xD0101018, BORDER = 0xFFFFC23D;
    private static final int W = 232, H = 236;

    /** What the sliders act on. */
    private enum Group {
        FIGURE(Part.FIGURE), HEAD(Part.HEAD), BODY(Part.BODY), RIGHT_ARM(Part.RIGHT_ARM), LEFT_ARM(Part.LEFT_ARM),
        RIGHT_LEG(Part.RIGHT_LEG), LEFT_LEG(Part.LEFT_LEG), FRAME(null);

        final Part part;

        Group(Part part) {
            this.part = part;
        }

        Component label() {
            return Component.translatable("screen.craftmaster.pose." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final PortraitEntity portrait;
    private final PortraitScreen parent;
    private Pose editing;
    private Group group = Group.FIGURE;
    private String name;
    private EditBox nameBox;
    private Button save, delete;
    private int left, top;

    public PoseEditorScreen(PortraitEntity portrait, PortraitScreen parent) {
        super(Component.translatable("screen.craftmaster.pose_editor"));
        this.portrait = portrait;
        this.parent = parent;
        Pose preview = PortraitClient.previewPose(portrait.getId());
        this.editing = preview != null ? preview : portrait.pose();
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

    private void changed(Pose pose) {
        editing = pose;
        PortraitClient.setPreview(portrait.getId(), editing);
    }

    @Override
    protected void init() {
        left = 4;
        top = Math.max(2, (height - H) / 2);
        PortraitClient.setPreview(portrait.getId(), editing);

        // Part buttons, 4 per row; the selected one is greyed out
        Group[] groups = Group.values();
        for (int i = 0; i < groups.length; i++) {
            Group g = groups[i];
            Button b = addRenderableWidget(Button.builder(g.label(), x -> {
                group = g;
                rebuildWidgets();
            }).bounds(left + 1 + (i % 4) * 58, top + 18 + (i / 4) * 22, 56, 20).build());
            b.active = g != group;
        }

        // Sliders of the selected group, two columns
        int cx0 = left + 2, cx1 = left + 117, cy = top + 66, sw = 113;
        if (group == Group.FRAME) {
            addRenderableWidget(new ValueSlider(cx0, cy, sw, "screen.craftmaster.pose.pitch", -60, 60, 1, editing.framePitch(),
                    v -> changed(editing.withFrame(v, editing.frameRoll()))));
            addRenderableWidget(new ValueSlider(cx1, cy, sw, "screen.craftmaster.pose.roll", -60, 60, 1, editing.frameRoll(),
                    v -> changed(editing.withFrame(editing.framePitch(), v))));
        } else {
            Part part = group.part;
            Rotation r = editing.rotation(part);
            addRenderableWidget(new ValueSlider(cx0, cy, sw, "screen.craftmaster.pose.rot_x", -180, 180, 1, r.x(),
                    v -> changed(editing.withRotation(part, new Rotation(v, editing.rotation(part).y(), editing.rotation(part).z())))));
            addRenderableWidget(new ValueSlider(cx1, cy, sw, "screen.craftmaster.pose.rot_y", -180, 180, 1, r.y(),
                    v -> changed(editing.withRotation(part, new Rotation(editing.rotation(part).x(), v, editing.rotation(part).z())))));
            addRenderableWidget(new ValueSlider(cx0, cy + 22, sw, "screen.craftmaster.pose.rot_z", -180, 180, 1, r.z(),
                    v -> changed(editing.withRotation(part, new Rotation(editing.rotation(part).x(), editing.rotation(part).y(), v)))));
            if (part.hideable()) {
                addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.pose.shown"),
                                Component.translatable("screen.craftmaster.pose.hidden"), !editing.isHidden(part)).displayOnlyValue()
                        .create(cx1, cy + 22, sw, 20, Component.empty(), (b, v) -> changed(editing.withHidden(part, !v))));
            } else {
                addRenderableWidget(new ValueSlider(cx1, cy + 22, sw, "screen.craftmaster.pose.scale", 0.5, 2.5, 0.05, editing.scale(),
                        v -> changed(editing.withScale(v))));
                addRenderableWidget(new ValueSlider(cx0, cy + 44, sw, "screen.craftmaster.pose.offset_x", -24, 24, 0.5, editing.offsetX(),
                        v -> changed(editing.withOffset(v, editing.offsetY(), editing.offsetZ()))));
                addRenderableWidget(new ValueSlider(cx1, cy + 44, sw, "screen.craftmaster.pose.offset_y", -48, 24, 0.5, editing.offsetY(),
                        v -> changed(editing.withOffset(editing.offsetX(), v, editing.offsetZ()))));
                // Negative: behind the canvas, for figures coming out of the back of the portrait
            addRenderableWidget(new ValueSlider(cx0, cy + 66, sw, "screen.craftmaster.pose.offset_z", -24, 24, 0.5, editing.offsetZ(),
                        v -> changed(editing.withOffset(editing.offsetX(), editing.offsetY(), v))));
            }
        }

        // Name, save / overwrite, delete
        int y = top + 160;
        nameBox = new EditBox(font, left + 2, y, 128, 20, Component.translatable("screen.craftmaster.pose.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(name);
        nameBox.setResponder(v -> updateButtons());
        addRenderableWidget(nameBox);
        save = addRenderableWidget(Button.builder(Component.empty(), b -> save()).bounds(left + 132, y, 98, 20).build());
        y += 22;
        delete = addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.pose.delete"), b -> delete())
                .bounds(left + 2, y, 74, 20).build());
        List<String> names = new ArrayList<>(PoseLibraries.client().names());
        if (!names.isEmpty()) {
            String current = names.contains(Pose.normalizeName(name)) ? Pose.normalizeName(name) : names.getFirst();
            addRenderableWidget(CycleButton.<String>builder(Component::literal, current).withValues(names)
                    .create(left + 78, y, 152, 20, Component.translatable("screen.craftmaster.pose.load"), (b, v) -> {
                        changed(PoseLibraries.client().get(v));
                        name = v;
                        rebuildWidgets();
                        nameBox.setValue(v);
                    }));
        }
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.pose.use"), b -> use())
                .bounds(left + 2, y, 113, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 117, y, 113, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        String n = Pose.normalizeName(nameBox.getValue());
        boolean exists = PoseLibraries.client().has(n);
        save.setMessage(Component.translatable(exists ? "screen.craftmaster.pose.overwrite" : "screen.craftmaster.pose.save_new"));
        save.active = !n.isEmpty();
        delete.active = exists;
    }

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
                    p.fixedPlayer(), p.displayName(), p.skinUrl(), name, p.hangSetting(), p.window()));
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

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + W, top + H, PANEL);
        g.outline(left, top, W, H, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.pose_editor").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                left + 4, top + 5, 0xFFFFFFFF);
        g.text(font, group.label().copy().withStyle(ChatFormatting.YELLOW), left + 120, top + 5, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    /** A slider between min and max, snapped to step, showing "label: value". */
    private static final class ValueSlider extends AbstractSliderButton {
        private final String key;
        private final double min, max, step;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, String key, double min, double max, double step, double value, DoubleConsumer onChange) {
            super(x, y, w, 20, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            updateMessage();
        }

        private double current() {
            double v = min + (max - min) * value;
            return Math.round(v / step) * step;
        }

        @Override
        protected void updateMessage() {
            double v = current();
            String text = step >= 1 ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0$", "");
            setMessage(Component.translatable(key, text));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
