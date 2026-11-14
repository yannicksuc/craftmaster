package fr.lordfinn.craftmaster.client.mixin;

import fr.lordfinn.craftmaster.client.home.HomeScreens;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every screen the game opens goes through here: Minecraft's title screen becomes CraftMaster's (client.home). */
@Mixin(Gui.class)
public class GuiSetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen craftmaster$homeScreen(Screen screen) {
        // Esc during a travelling preview: it ends the preview, no pause menu
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen && fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.previewing()) {
            fr.lordfinn.craftmaster.client.broadcast.DirectorCamera.stopPreview();
            return null;
        }
        return HomeScreens.replace(screen);
    }
}
