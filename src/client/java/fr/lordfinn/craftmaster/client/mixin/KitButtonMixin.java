package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * The UI kit everywhere: in CraftMaster's own screens, the vanilla buttons (Button, CycleButton...) are drawn like
 * {@link UiButton} (velvet face, golden edge), so every menu of the mod looks the same. The letter, the sheet of paper
 * and the wax seal keep their paper look.
 */
@Mixin(AbstractButton.class)
public abstract class KitButtonMixin {

    private static final Set<String> PAPER = Set.of("LetterScreen", "PaperScreen", "SealScreen");

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void craftmaster$kit(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        AbstractButton self = (AbstractButton) (Object) this;
        if (self instanceof UiButton) return;
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return;
        Class<?> c = screen.getClass();
        if (!c.getName().startsWith("fr.lordfinn.craftmaster.") || PAPER.contains(c.getSimpleName())) return;
        Ui.button(g, Minecraft.getInstance().font, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.getMessage(),
                false, self.isHoveredOrFocused(), self.active);
        ci.cancel();
    }
}
