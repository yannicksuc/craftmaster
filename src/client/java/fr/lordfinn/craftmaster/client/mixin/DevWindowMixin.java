package fr.lordfinn.craftmaster.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Dev client started in the background (scripts/dev-client.ps1, {@code -PbackgroundClient}): on Minecraft 26.2 the
 * window (GLFW) opens without taking the focus, so whoever uses the PC keeps typing where they were. 26.3 windows are
 * SDL3, which reads the same wish from the environment (build.gradle.kts): nothing to do there. Without the property:
 * vanilla.
 */
@Mixin(Window.class)
public abstract class DevWindowMixin {

    //? if <26.3 {
    /*@org.spongepowered.asm.mixin.Unique
    private static final boolean BACKGROUND_CLIENT = Boolean.getBoolean("craftmaster.dev.backgroundClient");

    @org.spongepowered.asm.mixin.injection.Inject(method = "createGlfwWindow", require = 0, at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void craftmaster$openWithoutFocus(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Long> cir) {
        if (BACKGROUND_CLIENT) {
            org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_FOCUSED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
            org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_FOCUS_ON_SHOW, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        }
    }
    *///?}
}
