package fr.lordfinn.craftmaster.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * The client calls that differ between Minecraft 26.3 (the version the source is written for) and 26.2. The
 * {@code //?} comments are Stonecutter conditions: the 26.2 build uses the commented-out branch. Small renames are
 * Stonecutter swaps instead, see stonecutter.gradle.kts.
 */
public final class ClientCompat {

    private ClientCompat() {}

    /** A colour-only render target the size and format of {@code like}. */
    public static TextureTarget colorTarget(String name, RenderTarget like) {
        //? if >=26.3 {
        return new TextureTarget(name, like.width, like.height, like.getColorTexture().getFormat(), null);
        //?} else {
        /*return new TextureTarget(name, like.width, like.height, false, like.getColorTexture().getFormat());
        *///?}
    }

    /** Copies the colour picture of {@code from} into {@code to} (same size). */
    public static void copyColor(RenderTarget from, RenderTarget to) {
        //? if >=26.3 {
        to.copyColorFrom(from);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(from.getColorTexture(), to.getColorTexture(), 0, 0, 0, 0, 0, to.width, to.height);
        *///?}
    }

    /**
     * A text background rectangle behind world labels. 26.2 has no standalone one: the caller then gives the
     * background colour to every text line instead ({@link #lineBackground}).
     */
    public static void textBackground(SubmitNodeCollector collector, PoseStack poseStack, float x0, float y0, float x1,
                                      float y1, int color, Font.DisplayMode mode, int light) {
        //? if >=26.3
        collector.submitTextBackground(poseStack, x0, y0, x1, y1, color, mode, light);
    }

    /** Background colour to give each text line: 0 when {@link #textBackground} drew it already (26.3). */
    public static int lineBackground(int color) {
        //? if >=26.3 {
        return 0;
        //?} else {
        /*return color;
        *///?}
    }

    /** The key is held now. */
    public static boolean isKeyDown(int key) {
        //? if >=26.3 {
        return InputConstants.isKeyDown(key);
        //?} else {
        /*return InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), key);
        *///?}
    }

    /** The letter the key types on the player's keyboard layout (Ctrl+Z is Ctrl+Z on AZERTY too), else its code. */
    public static int shortcutKey(KeyEvent e) {
        //? if >=26.3 {
        return e.shortcutKey();
        //?} else {
        /*String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(e.key(), e.scancode());
        return name != null && name.length() == 1 ? Character.toLowerCase(name.charAt(0)) : e.key();
        *///?}
    }
}
