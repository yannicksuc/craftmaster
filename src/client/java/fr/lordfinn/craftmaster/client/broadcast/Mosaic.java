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
 * Split screen ({@code SHOT_MOSAIC}): the game renders the world once per frame, so the tiles take turns — frame
 * k shows tile k mod n from its camera, and right after the world is drawn the picture is copied into that tile's
 * texture (GameRendererMixin). The overlay then draws every tile's last picture in a grid: each tile is refreshed
 * every n frames (15 fps per tile at 60 fps with 4 tiles), plenty for rooms where people build. Smart culling is
 * off meanwhile, so the chunks seen by one tile are not culled because of another tile's viewpoint.
 */
public final class Mosaic {

    public static final int MAX = 4;

    /** Tile drawn by the world render of this frame, -1 = none (not a split screen). */
    static int rendering = -1;
    private static final TextureTarget[] TILES = new TextureTarget[MAX];
    private static final long[] filledAt = new long[MAX];
    private static boolean cullWasSmart = true, cullChanged;

    private Mosaic() {}

    /** Before the frame: the tile textures exist at the screen's size; smart culling follows the split screen. */
    static void prepare(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (active && !cullChanged) {
            cullWasSmart = mc.smartCull;
            mc.smartCull = false;
            cullChanged = true;
        } else if (!active && cullChanged) {
            mc.smartCull = cullWasSmart;
            cullChanged = false;
        }
        if (!active) return;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        for (int i = 0; i < MAX; i++) {
            TextureTarget t = TILES[i];
            if (t != null && t.width == main.width && t.height == main.height) continue;
            if (t != null) t.destroyBuffers();
            TILES[i] = new TextureTarget("craftmaster_mosaic_" + i, main.width, main.height, main.getColorTexture().getFormat(), null);
            filledAt[i] = 0;
        }
    }

    /** Right after the world was drawn (before the interface): keeps the picture of the tile being rendered. */
    public static void afterLevel() {
        int i = rendering;
        if (i < 0 || i >= MAX || TILES[i] == null) return;
        try {
            TILES[i].copyColorFrom(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            filledAt[i] = System.currentTimeMillis();
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Réalisation] Écran partagé : copie impossible", e);
            rendering = -1;
        }
    }

    /** Tile {@code i} has a picture already. */
    static boolean ready(int i) {
        return i >= 0 && i < MAX && TILES[i] != null && filledAt[i] > 0;
    }

    /** Draws tile {@code i}'s last picture in the rectangle (GUI coordinates of the current pose). */
    static void draw(GuiGraphicsExtractor g, int i, float x0, float y0, float x1, float y1) {
        if (!ready(i)) return;
        TextureSetup tex = TextureSetup.singleTexture(TILES[i].getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        // Render targets are stored bottom-up: V flipped (as in Transition). Opaque: the world picture's alpha is not
        // 1 everywhere (fogged horizon), blending it would let the velvet background show through
        ((GuiGraphicsExtractorAccessor) g).craftmaster$guiRenderState().addGuiElement(new BlitRenderState(
                RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, tex, new Matrix3x2f(g.pose()), Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1),
                0, 1, 1, 0, 0xFFFFFFFF, null));
    }
}
