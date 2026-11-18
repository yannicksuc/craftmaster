package fr.lordfinn.craftmaster.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Size and response of a vanilla interaction entity (the trial kit's clickable spots). */
@Mixin(Interaction.class)
public interface InteractionAccessor {
    @Invoker("setWidth")
    void craftmaster$setWidth(float width);

    @Invoker("setHeight")
    void craftmaster$setHeight(float height);

    @Invoker("setResponse")
    void craftmaster$setResponse(boolean response);
}
