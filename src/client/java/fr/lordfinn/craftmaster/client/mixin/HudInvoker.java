package fr.lordfinn.craftmaster.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Screen effects: vanilla's own full-screen camera overlay (the one drawn when wearing a carved pumpkin), reused
 * as is for the "citrouille" effect (client.fx.FxOverlay).
 */
@Mixin(Hud.class)
public interface HudInvoker {

    @Invoker("extractTextureOverlay")
    void craftmaster$extractTextureOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha);
}
