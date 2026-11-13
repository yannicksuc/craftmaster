package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import /*$ filter_mode_class {*/com.mojang.renderpearl.api.textures.FilterMode/*$}*/;
import fr.lordfinn.craftmaster.client.compat.ClientCompat;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/**
 * Transitions drawn over the picture:
 * <ul>
 *   <li>cross-fade: when the shot changes, the last frame on screen is copied, then drawn over the new shot with an
 *       alpha going from 1 to 0 (a real dissolve, the old picture frozen like a vision mixer's still store);</li>
 *   <li>double exposure ("surimpression"): the same copied frame, held longer: the new shot fades in over it up to
 *       half and both stay superimposed, then the old one goes ({@link #DOUBLE_EXPOSURE} seconds);</li>
 *   <li>white flash: the cut happens under a white frame that fades out ({@link #FLASH} seconds);</li>
 *   <li>kaleidoscope ("miroir"): the post effect {@code craftmaster:fx/miroir_transition} folds the live picture
 *       into spinning mirrored segments ({@link #mirrorAmount}); the shot changes at its peak, halfway through
 *       {@link #MIRROR} seconds (DirectorCamera), and the new picture unfolds.</li>
 * </ul>
 */
public final class Transition {

    /** Render targets are stored bottom-up: their V axis is flipped compared to GUI textures. */
    private static final boolean FLIP = true;

    /** Lengths, in seconds. */
    public static final double DOUBLE_EXPOSURE = 1.6, FLASH = 0.55, MIRROR = 1.0;

    private static TextureTarget still;
    private static long capturedAt = -1;
    private static double stillLength = DirectorCamera.CROSSFADE;
    private static boolean doubleExposure;
    private static long flashAt = -1, mirrorAt = -1;

    private Transition() {}

    /** Copies the frame on screen now (it is still the last frame of the old shot), for a cross-fade. */
    static void capture() {
        capture(false);
    }

    /** Copies the frame on screen now, held for a long double exposure. */
    static void captureDoubleExposure() {
        capture(true);
    }

    /** White flash, starting now (the cut happens under it). */
    static void flash() {
        flashAt = System.currentTimeMillis();
    }

    /** Kaleidoscope, starting now; the shot must change {@link #MIRROR} / 2 seconds later. */
    static void mirror() {
        mirrorAt = System.currentTimeMillis();
    }

    private static double mirrorT() {
        if (mirrorAt < 0) return -1;
        double t = (System.currentTimeMillis() - mirrorAt) / 1000.0 / MIRROR;
        if (t >= 1) {
            mirrorAt = -1;
            return -1;
        }
        return t;
    }

    /** How much the kaleidoscope folds the picture now: 0 → 1 at the cut (halfway) → 0. */
    public static float mirrorAmount() {
        double t = mirrorT();
        if (t < 0) return 0;
        return (float) DirectorCamera.smooth(Math.min(t, 1 - t) * 2);
    }

    /** Progress of the kaleidoscope, 0..1 (its spin). */
    public static float mirrorProgress() {
        return (float) Math.max(0, mirrorT());
    }

    private static void capture(boolean longHold) {
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (still == null || still.width != main.width || still.height != main.height) {
                if (still != null) still.destroyBuffers();
                still = ClientCompat.colorTarget("craftmaster_crossfade", main);
            }
            ClientCompat.copyColor(main, still);
            capturedAt = System.currentTimeMillis();
            doubleExposure = longHold;
            stillLength = longHold ? DOUBLE_EXPOSURE : DirectorCamera.CROSSFADE;
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Réalisation] Fondu enchaîné impossible, coupe sèche", e);
            capturedAt = -1;
        }
    }

    /** Opacity of the old frame now (0 = done). */
    static float alpha() {
        if (capturedAt < 0) return 0;
        double t = (System.currentTimeMillis() - capturedAt) / 1000.0 / stillLength;
        if (t >= 1) {
            capturedAt = -1;
            return 0;
        }
        if (!doubleExposure) return (float) (1 - DirectorCamera.smooth(t));
        return doubleExposureAlpha(t * DOUBLE_EXPOSURE);
    }

    /**
     * Opacity of the held old frame during a double exposure, {@code s} seconds in: the new picture comes up to
     * half over 0.4 s, both stay superimposed (the old one slowly thinning), then the old one leaves over the last 0.5 s.
     */
    static float doubleExposureAlpha(double s) {
        double out = DOUBLE_EXPOSURE - 0.5;
        if (s < 0.4) return (float) (1 - 0.45 * DirectorCamera.smooth(s / 0.4));
        if (s < out) return (float) (0.55 - 0.1 * (s - 0.4) / (out - 0.4));
        return (float) (0.45 * (1 - DirectorCamera.smooth((s - out) / 0.5)));
    }

    /** Opacity of the white flash now. */
    static float flashAlpha() {
        if (flashAt < 0) return 0;
        double s = (System.currentTimeMillis() - flashAt) / 1000.0;
        if (s >= FLASH) {
            flashAt = -1;
            return 0;
        }
        // Full white for two or three frames (the cut is under it), then it burns out
        return s < 0.06 ? 1f : (float) (1 - DirectorCamera.smooth((s - 0.06) / (FLASH - 0.06)));
    }

    /** Draws the old frame and the flash over the whole screen (under the overlay). */
    static void draw(GuiGraphicsExtractor g) {
        drawStill(g);
        float f = flashAlpha();
        if (f > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (f * 255) << 24) | 0xFFFFFF);
    }

    private static void drawStill(GuiGraphicsExtractor g) {
        float a = alpha();
        if (a <= 0 || still == null) return;
        int color = ((int) (a * 255) << 24) | 0xFFFFFF;
        TextureSetup tex = TextureSetup.singleTexture(still.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        // The copied frame is upside down for the GUI's texture coordinates (render target origin at the bottom)
        ((GuiGraphicsExtractorAccessor) g).craftmaster$guiRenderState().addGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED, tex, new Matrix3x2f(g.pose()), 0, 0, g.guiWidth(), g.guiHeight(),
                0, 1, FLIP ? 1 : 0, FLIP ? 0 : 1, color, null));
    }
}
