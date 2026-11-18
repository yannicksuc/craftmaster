package fr.lordfinn.craftmaster.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where an inventory screen stands : the show's inventory camera. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int craftmaster$leftPos();

    @Accessor("topPos")
    int craftmaster$topPos();
}
