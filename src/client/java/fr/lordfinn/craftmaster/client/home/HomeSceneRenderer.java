package fr.lordfinn.craftmaster.client.home;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Draws a {@link HomeSceneState} like the GUI draws an entity in a screen (an offscreen picture blitted in place),
 * but through a real perspective camera instead of the GUI's flat projection: the picture covers the screen and
 * its projection is set again just before the geometry is drawn. Registered with Fabric's picture in picture
 * registry, which gives every layer of a frame its own renderer (and so its own picture).
 */
public class HomeSceneRenderer extends PictureInPictureRenderer<HomeSceneState> {

    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("CraftMaster title screen");

    @Override
    public Class<HomeSceneState> getRenderStateClass() {
        return HomeSceneState.class;
    }

    @Override
    protected void renderToTexture(HomeSceneState state, PoseStack ignored, SubmitNodeCollector collector) {
        int w = Math.max(1, state.x1() - state.x0()), h = Math.max(1, state.y1() - state.y0());
        // Only the aspect matters: the picture is the screen's size at the GUI scale
        projection.setupPerspective(0.1f, 1000f, state.fov(), w, h);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
        RenderSystem.getModelViewStack().identity();
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        CameraRenderState camera = new CameraRenderState();
        for (HomeSceneState.Piece piece : state.pieces()) {
            // Drawn after the screen was built: a failure here sends the game back to the vanilla title screen
            try {
                piece.submit(new PoseStack(), collector, camera);
            } catch (RuntimeException e) {
                HomeScreens.failed(e);
                return;
            }
        }
    }

    @Override
    protected String getTextureLabel() {
        return "craftmaster home";
    }

    @Override
    public void close() {
        super.close();
        projectionBuffer.close();
    }
}
