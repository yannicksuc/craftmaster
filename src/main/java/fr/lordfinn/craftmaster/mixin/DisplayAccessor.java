package fr.lordfinn.craftmaster.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setBillboardConstraints")
    void craftmaster$setBillboardConstraints(Display.BillboardConstraints billboard);

    @Invoker("setViewRange")
    void craftmaster$setViewRange(float range);

    @Invoker("setTransformation")
    void craftmaster$setTransformation(com.mojang.math.Transformation transformation);
}
