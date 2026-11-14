package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The régie agent's on / off button in Simple Voice Chat's menu (mixin.VoiceChatScreenMixin), drawn like that
 * menu's own toggle buttons: a 20 × 20 button, its 16 × 16 icon from a 32 × 32 sprite (left: on, right: off with
 * Voice Chat's red slash).
 */
public final class AgentToggleButton extends AbstractButton {

    private static final Identifier ICON = CraftMaster.id("icons/regie_agent_button");

    public AgentToggleButton(int x, int y) {
        super(x, y, 20, 20, Component.empty());
        updateTooltip();
    }

    @Override
    public void onPress(InputWithModifiers input) {
        RegieAgent.setEnabled(!RegieAgent.enabled());
        updateTooltip();
    }

    private void updateTooltip() {
        setTooltip(Tooltip.create(Component.translatable(RegieAgent.enabled() ? "tooltip.craftmaster.regie.agent_on" : "tooltip.craftmaster.regie.agent_off")));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(g);
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON, 32, 32, RegieAgent.enabled() ? 0 : 16, 0,
                getX() + 2, getY() + 2, 16, 16);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
