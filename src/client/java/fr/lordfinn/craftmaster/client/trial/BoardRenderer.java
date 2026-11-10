package fr.lordfinn.craftmaster.client.trial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.trial.BoardEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a drawing board: its painted surface (a dynamic texture, {@link BoardTextures}), a frame matching its kind
 * (gilded for the canvas, wood for the slate, aluminium for the whiteboard) and the text written on it, centred
 * and as large as it fits.
 */
public class BoardRenderer extends EntityRenderer<BoardEntity, BoardRenderer.State> {

    /** Three 16 × 16 frame materials side by side: gold, wood, aluminium (scripts/gen_assets.py). */
    public static final Identifier FRAME = CraftMaster.id("textures/entity/board_frame.png");
    private static final float FRAME_W = 1 / 16f, FRAME_D = 1.5f / 16f;

    public static class State extends EntityRenderState {
        Identifier texture;
        int variant, width, height, ink;
        float yaw;
        List<FormattedCharSequence> lines = List.of();
        int maxLineWidth;
    }

    public BoardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BoardEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.texture = BoardTextures.get(e);
        s.variant = e.variant().ordinal();
        s.width = e.boardWidth();
        s.height = e.boardHeight();
        s.ink = e.variant().ink;
        s.yaw = e.facing().toYRot();
        Font font = getFont();
        List<FormattedCharSequence> lines = new ArrayList<>();
        int max = 0;
        if (!e.text().isBlank()) {
            // Long lines wrap at about 40 characters' width per block of board
            int wrap = Math.max(60, s.width * 64);
            for (String raw : e.text().split("\n", -1)) {
                for (FormattedCharSequence l : font.split(Component.literal(raw), wrap)) {
                    lines.add(l);
                    max = Math.max(max, font.width(l));
                }
                if (lines.size() > 24) break;
            }
        }
        s.lines = lines;
        s.maxLineWidth = max;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float hw = s.width / 2f, hh = s.height / 2f;
        int light = s.lightCoords;
        poseStack.pushPose();
        // Board axes: x to the viewer's right, y up, z out of the wall toward the viewer
        poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(s.texture), (p, c) -> {
            quad(c, p, -hw, -hh, hw, hh, 0.001f, 0, 0, 1, 1, light);
        });
        float u0 = s.variant * 16 / 48f, u1 = u0 + 4 / 48f;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME), (p, c) -> {
            // Back plate, then the four sides of the frame (a little proud of the surface)
            back(c, p, -hw, -hh, hw, hh, (float) -BoardEntity.DEPTH, u0, u1, light);
            box(c, p, -hw - FRAME_W, hh, -0.0625f, hw + FRAME_W, hh + FRAME_W, FRAME_D, u0, u1, light);
            box(c, p, -hw - FRAME_W, -hh - FRAME_W, -0.0625f, hw + FRAME_W, -hh, FRAME_D, u0, u1, light);
            box(c, p, -hw - FRAME_W, -hh, -0.0625f, -hw, hh, FRAME_D, u0, u1, light);
            box(c, p, hw, -hh, -0.0625f, hw + FRAME_W, hh, FRAME_D, u0, u1, light);
        });
        if (!s.lines.isEmpty()) submitText(s, poseStack, collector, hw, hh, light);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }

    private void submitText(State s, PoseStack poseStack, SubmitNodeCollector collector, float hw, float hh, int light) {
        Font font = getFont();
        int lineH = font.lineHeight + 1;
        float margin = 0.15f;
        float scale = Math.min((2 * hw - 2 * margin) / Math.max(1, s.maxLineWidth), (2 * hh - 2 * margin) / (s.lines.size() * lineH));
        scale = Math.min(scale, 1 / 28f);
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.004);
        poseStack.scale(scale, -scale, scale);
        float top = -s.lines.size() * lineH / 2f;
        for (int i = 0; i < s.lines.size(); i++) {
            FormattedCharSequence l = s.lines.get(i);
            collector.submitText(poseStack, -font.width(l) / 2f, top + i * lineH, l, false, Font.DisplayMode.POLYGON_OFFSET,
                    light, s.ink, 0, 0);
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- geometry

    private static void vertex(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        c.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** The front quad, its texture top-left at the board's top-left. */
    private static void quad(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1, int light) {
        vertex(c, p, x0, y1, z, u0, v0, light, 0, 0, 1);
        vertex(c, p, x0, y0, z, u0, v1, light, 0, 0, 1);
        vertex(c, p, x1, y0, z, u1, v1, light, 0, 0, 1);
        vertex(c, p, x1, y1, z, u1, v0, light, 0, 0, 1);
    }

    private static void back(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, float u0, float u1, int light) {
        vertex(c, p, x1, y1, z, u0, 0, light, 0, 0, -1);
        vertex(c, p, x1, y0, z, u0, 1, light, 0, 0, -1);
        vertex(c, p, x0, y0, z, u1, 1, light, 0, 0, -1);
        vertex(c, p, x0, y1, z, u1, 0, light, 0, 0, -1);
    }

    /** A frame bar: every face samples the material strip (u0..u1 × the whole height). */
    private static void box(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float u0, float u1, int light) {
        // front (+z), back (-z)
        vertex(c, p, x0, y1, z1, u0, 0, light, 0, 0, 1); vertex(c, p, x0, y0, z1, u0, 1, light, 0, 0, 1);
        vertex(c, p, x1, y0, z1, u1, 1, light, 0, 0, 1); vertex(c, p, x1, y1, z1, u1, 0, light, 0, 0, 1);
        vertex(c, p, x1, y1, z0, u0, 0, light, 0, 0, -1); vertex(c, p, x1, y0, z0, u0, 1, light, 0, 0, -1);
        vertex(c, p, x0, y0, z0, u1, 1, light, 0, 0, -1); vertex(c, p, x0, y1, z0, u1, 0, light, 0, 0, -1);
        // top (+y), bottom (-y)
        vertex(c, p, x0, y1, z0, u0, 0, light, 0, 1, 0); vertex(c, p, x0, y1, z1, u0, 1, light, 0, 1, 0);
        vertex(c, p, x1, y1, z1, u1, 1, light, 0, 1, 0); vertex(c, p, x1, y1, z0, u1, 0, light, 0, 1, 0);
        vertex(c, p, x0, y0, z1, u0, 0, light, 0, -1, 0); vertex(c, p, x0, y0, z0, u0, 1, light, 0, -1, 0);
        vertex(c, p, x1, y0, z0, u1, 1, light, 0, -1, 0); vertex(c, p, x1, y0, z1, u1, 0, light, 0, -1, 0);
        // left (-x), right (+x)
        vertex(c, p, x0, y1, z0, u0, 0, light, -1, 0, 0); vertex(c, p, x0, y0, z0, u0, 1, light, -1, 0, 0);
        vertex(c, p, x0, y0, z1, u1, 1, light, -1, 0, 0); vertex(c, p, x0, y1, z1, u1, 0, light, -1, 0, 0);
        vertex(c, p, x1, y1, z1, u0, 0, light, 1, 0, 0); vertex(c, p, x1, y0, z1, u0, 1, light, 1, 0, 0);
        vertex(c, p, x1, y0, z0, u1, 1, light, 1, 0, 0); vertex(c, p, x1, y1, z0, u1, 0, light, 1, 0, 0);
    }
}
