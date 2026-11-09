package fr.lordfinn.craftmaster.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Text displays have no public setters: the bust labels go through these. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void craftmaster$setText(Component text);

    @Invoker("setBackgroundColor")
    void craftmaster$setBackgroundColor(int argb);
}
