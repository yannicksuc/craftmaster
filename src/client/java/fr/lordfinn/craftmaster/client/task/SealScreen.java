package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.task.TaskNetwork;
import fr.lordfinn.craftmaster.task.WaxColor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The organisers' wax seal: what goes in the envelope (a round's task, or a free text), the wax colour, the name
 * written on it, how many (or one per contestant, each with their name), announced to all when opened or not.
 * The envelope on the left shows the seal in its colour.
 */
public class SealScreen extends Screen {

    private static final String FREE = "";
    private static final int PER_CAST = -1;

    private final TaskNetwork.OpenEditor data;
    private String content;
    private WaxColor wax;
    private String to = "", freeTitle = "", freeText = "";
    private int count = 1;
    private boolean announce = true;

    private EditBox toBox, titleBox;
    private MultiLineEditBox textBox;
    private int px, py, pw, ph, swatchX, swatchY, envX, envY, envW, envH;

    public SealScreen(TaskNetwork.OpenEditor data) {
        super(Component.translatable("screen.craftmaster.seal"));
        this.data = data;
        TaskNetwork.Round r = round(data.selected());
        this.content = r != null && r.hasTask() ? r.card().mancheId() : firstWithTask();
        this.wax = r != null ? WaxColor.of(r.card().wax()) : WaxColor.RED;
    }

    private String firstWithTask() {
        for (TaskNetwork.Round r : data.rounds()) if (r.hasTask()) return r.card().mancheId();
        return FREE;
    }

    private TaskNetwork.Round round(String id) {
        for (TaskNetwork.Round r : data.rounds()) if (r.card().mancheId().equals(id)) return r;
        return null;
    }

    private Component contentLabel(String id) {
        TaskNetwork.Round r = round(id);
        if (r == null) return Component.translatable("screen.craftmaster.seal.free");
        return Component.literal(r.card().number() + ". " + r.card().mancheName() + (r.card().title().isEmpty() ? "" : " — " + r.card().title()));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 250);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        envW = Math.min(150, pw / 3);
        envH = envW * 80 / 128;
        envX = px + 12;
        envY = py + 40;
        int x = envX + envW + 14, w = px + pw - 10 - x;
        int y = py + 34;

        List<String> contents = new ArrayList<>();
        for (TaskNetwork.Round r : data.rounds()) if (r.hasTask()) contents.add(r.card().mancheId());
        contents.add(FREE);
        if (!contents.contains(content)) content = contents.getFirst();
        addRenderableWidget(CycleButton.<String>builder(this::contentLabel, content).withValues(contents).displayOnlyValue()
                .create(x, y, w, 20, Component.translatable("screen.craftmaster.seal.content"), (b, v) -> {
                    content = v;
                    TaskNetwork.Round r = round(v);
                    if (r != null) wax = WaxColor.of(r.card().wax());
                    rebuildWidgets();
                }));
        y += 26;
        if (content.equals(FREE)) {
            titleBox = addRenderableWidget(new EditBox(font, x, y, w, 18, Component.translatable("screen.craftmaster.task.title")));
            titleBox.setMaxLength(80);
            titleBox.setHint(Component.translatable("screen.craftmaster.task.title_hint").withStyle(ChatFormatting.DARK_GRAY));
            titleBox.setValue(freeTitle);
            titleBox.setResponder(v -> freeTitle = v);
            y += 22;
            textBox = addRenderableWidget(MultiLineEditBox.builder().setX(x).setY(y)
                    .setPlaceholder(Component.translatable("screen.craftmaster.seal.text_hint").withStyle(ChatFormatting.DARK_GRAY))
                    .build(font, w, 56, Component.translatable("screen.craftmaster.task.text")));
            textBox.setCharacterLimit(4000);
            textBox.setValue(freeText);
            textBox.setValueListener(v -> freeText = v);
            y += 62;
        } else {
            titleBox = null;
            textBox = null;
        }
        swatchX = x;
        swatchY = y + 12;
        y += 30;
        toBox = addRenderableWidget(new EditBox(font, x + 50, y, w - 50, 18, Component.translatable("screen.craftmaster.seal.to")));
        toBox.setMaxLength(32);
        toBox.setValue(to);
        toBox.setHint(Component.translatable("screen.craftmaster.seal.to_hint").withStyle(ChatFormatting.DARK_GRAY));
        toBox.setResponder(v -> to = v);
        toBox.visible = count != PER_CAST;
        y += 24;
        List<Integer> counts = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 8));
        if (!data.cast().isEmpty()) counts.add(PER_CAST);
        addRenderableWidget(CycleButton.<Integer>builder(n -> n == PER_CAST
                        ? Component.translatable("screen.craftmaster.seal.per_cast", data.cast().size())
                        : Component.translatable("screen.craftmaster.seal.count", n), count).withValues(counts).displayOnlyValue()
                .create(x, y, w / 2 - 2, 20, Component.empty(), (b, v) -> {
                    count = v;
                    toBox.visible = v != PER_CAST;
                }));
        addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.craftmaster.seal.announce_on"),
                        Component.translatable("screen.craftmaster.seal.announce_off"), announce).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("screen.craftmaster.seal.announce.tip")))
                .create(x + w / 2 + 2, y, w / 2 - 2, 20, Component.empty(), (b, v) -> announce = v));

        int by = py + ph - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.craftmaster.seal.seal"), b -> seal())
                .bounds(px + pw / 2 - 104, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(px + pw / 2 + 4, by, 100, 20).build());
    }

    private void seal() {
        if (!ClientPlayNetworking.canSend(TaskNetwork.Seal.TYPE)) return;
        boolean free = content.equals(FREE);
        if (count == PER_CAST) {
            for (String name : data.cast())
                ClientPlayNetworking.send(new TaskNetwork.Seal(free ? "" : content, freeTitle, freeText, wax.id(), name, 1, announce));
        } else {
            ClientPlayNetworking.send(new TaskNetwork.Seal(free ? "" : content, freeTitle, freeText, wax.id(), to, count, announce));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WaxColor c = TaskStyle.swatchAt(swatchX, swatchY, event.x(), event.y());
        if (c != null) {
            wax = c;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        TaskStyle.panel(g, px, py, pw, ph);
        TaskStyle.title(g, font, Component.translatable("screen.craftmaster.seal").withStyle(ChatFormatting.BOLD), px + pw / 2, py + 8);
        // The envelope, sealed with the chosen colour
        g.fill(envX + 4, envY + 5, envX + envW + 4, envY + envH + 5, 0x50000000);
        g.blit(RenderPipelines.GUI_TEXTURED, PaperScreen.INNER, envX, envY, 0, 0, envW, envH, 128, 80, 128, 80, 0xFFFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, PaperScreen.POCKET, envX, envY, 0, 0, envW, envH, 128, 80, 128, 80, 0xFFFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, PaperScreen.FLAP, envX, envY, 0, 0, envW, envH * 48 / 80, 128, 48, 128, 48, 0xFFFFFFFF);
        PaperView.drawSeal(g, envX + envW / 2, envY + (int) (envH * 48 / 80 * 0.86f), envW * 30 / 128, wax.id(), 255);
        String name = count == PER_CAST ? Component.translatable("screen.craftmaster.seal.each").getString() : to.strip();
        if (!name.isEmpty()) g.centeredText(font, Component.translatable("paper.craftmaster.to", name), envX + envW / 2, envY + envH + 10, TaskStyle.LABEL);
        Component waxLabel = Component.translatable("item.craftmaster.envelope.wax", TaskStyle.waxName(wax));
        int lw = font.width(waxLabel) + 14;
        int lx = envX + (envW - lw) / 2, ly = envY + envH + 22;
        g.fill(lx, ly, lx + 9, ly + 9, 0xFF000000 | wax.rgb);
        g.outline(lx, ly, 9, 9, TaskStyle.GOLD);
        g.text(font, waxLabel, lx + 14, ly + 1, TaskStyle.LABEL, false);

        int x = envX + envW + 14;
        TaskStyle.label(g, font, Component.translatable("screen.craftmaster.seal.content"), x, py + 24);
        TaskStyle.label(g, font, Component.translatable("screen.craftmaster.seal.wax"), x, swatchY - 11);
        TaskStyle.swatches(g, swatchX, swatchY, wax, mouseX, mouseY);
        if (toBox.visible) TaskStyle.label(g, font, Component.translatable("screen.craftmaster.seal.to"), x, toBox.getY() + 5);
        WaxColor hover = TaskStyle.swatchAt(swatchX, swatchY, mouseX, mouseY);
        if (hover != null) g.setTooltipForNextFrame(font, TaskStyle.waxName(hover), mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
