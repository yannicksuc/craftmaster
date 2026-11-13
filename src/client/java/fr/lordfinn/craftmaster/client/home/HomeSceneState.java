package fr.lordfinn.craftmaster.client.home;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import java.util.List;

/**
 * One 3D layer of the title screen, drawn by {@link HomeSceneRenderer} into the GUI over the whole screen: what
 * it contains, seen by a perspective camera ({@code fov} degrees vertically, the screen's aspect). The pieces
 * submit their geometry in camera space (blocks, eye at the origin looking down -z, y up).
 */
public record HomeSceneState(float fov, List<Piece> pieces, int x0, int y0, int x1, int y1, ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    /** Something drawn in the layer: submits its geometry with {@code poseStack} at camera space. */
    @FunctionalInterface
    public interface Piece {
        void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);
    }

    public static HomeSceneState fullScreen(float fov, List<Piece> pieces, int width, int height) {
        return new HomeSceneState(fov, pieces, 0, 0, width, height, PictureInPictureRenderState.getBounds(0, 0, width, height, null));
    }

    @Override
    public float scale() {
        return 1;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }
}
