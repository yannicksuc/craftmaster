package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/**
 * Cross-fade: when the shot changes, the last frame on screen is copied, then drawn over the new shot with an
 * alpha going from 1 to 0 (a real dissolve, the old picture frozen like a vision mixer's still store).
 */
public final class Transition {

    /** Render targets are stored bottom-up: their V axis is flipped compared to GUI textures. */
    private static final boolean FLIP = true;

    private static TextureTarget still;
    private static long capturedAt = -1;

    private Transition() {}

    /** Copies the frame on screen now (it is still the last frame of the old shot). */
    static void capture() {
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (still == null || still.width != main.width || still.height != main.height) {
                if (still != null) still.destroyBuffers();
                still = new TextureTarget("craftmaster_crossfade", main.width, main.height, main.getColorTexture().getFormat(), null);
            }
            still.copyColorFrom(main);
            capturedAt = System.currentTimeMillis();
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Réalisation] Fondu enchaîné impossible, coupe sèche", e);
            capturedAt = -1;
        }
    }

    /** Opacity of the old frame now (0 = done). */
    static float alpha() {
        if (capturedAt < 0) return 0;
        double t = (System.currentTimeMillis() - capturedAt) / 1000.0 / DirectorCamera.CROSSFADE;
        if (t >= 1) {
            capturedAt = -1;
            return 0;
        }
        return (float) (1 - DirectorCamera.smooth(t));
    }

    /** Draws the old frame over the whole screen (under the overlay). */
    static void draw(GuiGraphicsExtractor g) {
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
