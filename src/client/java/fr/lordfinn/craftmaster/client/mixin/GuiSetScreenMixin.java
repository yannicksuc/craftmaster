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
        return HomeScreens.replace(screen);
    }
}
