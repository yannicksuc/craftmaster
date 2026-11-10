package fr.lordfinn.craftmaster.client.trial;

import fr.lordfinn.craftmaster.trial.BoardEntity;
import fr.lordfinn.craftmaster.trial.TrialNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Writing on a drawing board: a few lines of text, centred on the board as large as they fit. */
public class BoardTextScreen extends Screen {

    private static final int PANEL = 0xE8140E10, BORDER = 0xFFD4A53A, VELVET = 0xFF6B1420;

    private final BoardEntity board;
    private MultiLineEditBox text;
    private String value;
    private int left, top, w, h;

    public BoardTextScreen(BoardEntity board) {
        super(Component.translatable("screen.craftmaster.board"));
        this.board = board;
        this.value = board.text();
    }

    @Override
    protected void init() {
        w = Math.min(300, width - 20);
        h = 150;
        left = (width - w) / 2;
        top = height - h - 30;
        text = MultiLineEditBox.builder().setX(left + 8).setY(top + 24)
                .setPlaceholder(Component.translatable("screen.craftmaster.board.placeholder"))
                .build(font, w - 16, h - 60, Component.translatable("screen.craftmaster.board"));
        text.setCharacterLimit(BoardEntity.MAX_TEXT);
        text.setValue(value);
        text.setValueListener(v -> value = v);
        addRenderableWidget(text);
        setInitialFocus(text);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            ClientPlayNetworking.send(new TrialNetwork.BoardText(board.getId(), value));
            onClose();
        }).bounds(left + 8, top + h - 28, (w - 20) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + 12 + (w - 20) / 2, top + h - 28, (w - 20) / 2, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The board stays visible above the panel. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + w, top + h, PANEL);
        g.fill(left, top, left + w, top + 18, VELVET);
        g.outline(left, top, w, h, BORDER);
        g.text(font, Component.translatable("screen.craftmaster.board").withStyle(ChatFormatting.BOLD), left + 8, top + 5, 0xFFFFE9A8);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
