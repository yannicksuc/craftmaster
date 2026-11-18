package fr.lordfinn.craftmaster.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The item a vanilla item display shows (flowers lying on a path, the vase's bouquet). */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
    @Invoker("setItemStack")
    void craftmaster$setItemStack(ItemStack stack);

    @Invoker("getItemStack")
    ItemStack craftmaster$getItemStack();

    @Invoker("setItemTransform")
    void craftmaster$setItemTransform(ItemDisplayContext context);
}
