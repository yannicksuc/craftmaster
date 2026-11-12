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
 * and the text written on it, centred and as large as it fits. The frame is 2 px wide and stands 2 px proud, in
 * Minecraft's own materials at 16 texels per block, the grain along each bar: stripped birch for the canvas,
 * stripped oak for the school slate, iron for the whiteboard; the slate and the whiteboard have a tray at the bottom
 * (chalk, markers).
 */
public class BoardRenderer extends EntityRenderer<BoardEntity, BoardRenderer.State> {

    /** Frame material of each variant (canvas, slate, whiteboard): Minecraft's block textures. */
    private static final Identifier[] FRAMES = {
            Identifier.parse("minecraft:textures/block/stripped_birch_log.png"),
            Identifier.parse("minecraft:textures/block/stripped_oak_log.png"),
            Identifier.parse("minecraft:textures/block/iron_block.png")};
    private static final float PX = 1 / 16f, FRAME_W = 2 * PX, FRAME_D = 2 * PX, BACK = -PX;

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
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(s.texture), (p, c) -> {
            quad(c, p, -hw, -hh, hw, hh, 0.001f, 0, 0, 1, 1, light);
        });
        boolean tray = s.variant != BoardEntity.Variant.CANVAS.ordinal();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(FRAMES[s.variant]), (p, c) -> {
            back(c, p, -hw, -hh, hw, hh, (float) -BoardEntity.DEPTH, light);
            // Top and bottom bars run the whole width (they cover the corners), the sides fit between them
            bar(c, p, -hw - FRAME_W, hh, BACK, hw + FRAME_W, hh + FRAME_W, FRAME_D, true, light);
            bar(c, p, -hw - FRAME_W, -hh - FRAME_W, BACK, hw + FRAME_W, -hh, FRAME_D, true, light);
            bar(c, p, -hw - FRAME_W, -hh, BACK, -hw, hh, FRAME_D, false, light);
            bar(c, p, hw, -hh, BACK, hw + FRAME_W, hh, FRAME_D, false, light);
            // Tray under the board, 1 px below the bottom bar's top and 3 px deep
            if (tray) bar(c, p, -hw + 2 * PX, -hh - FRAME_W - PX, FRAME_D, hw - 2 * PX, -hh - PX, FRAME_D + 3 * PX, true, light);
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

    private static void back(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, int light) {
        vertex(c, p, x1, y1, z, 0, 0, light, 0, 0, -1);
        vertex(c, p, x1, y0, z, 0, (y1 - y0), light, 0, 0, -1);
        vertex(c, p, x0, y0, z, (x1 - x0), (y1 - y0), light, 0, 0, -1);
        vertex(c, p, x0, y1, z, (x1 - x0), 0, light, 0, 0, -1);
    }

    /**
     * A frame bar, cut into pieces of at most one block so the 16 px material never stretches. On every face the
     * texture's v runs along the bar (the wood's grain follows it) and u across, both in blocks (16 texels each).
     */
    private static void bar(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            boolean horizontal, int light) {
        float start = horizontal ? x0 : y0, end = horizontal ? x1 : y1;
        for (float a = start; a < end - 1e-4f; a += 1) {
            float b = Math.min(end, a + 1);
            if (horizontal) piece(c, p, a, y0, z0, b, y1, z1, true, light);
            else piece(c, p, x0, a, z0, x1, b, z1, false, light);
        }
    }

    private static void piece(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                              boolean horizontal, int light) {
        // (u, v) of a corner: v along the bar from the piece's start, u across
        // front (+z) / back (-z): across = y (horizontal bar) or x (vertical bar)
        face(c, p, new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}}, x0, y0, z0, horizontal, 2, light, 0, 0, 1);
        face(c, p, new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}}, x0, y0, z0, horizontal, 2, light, 0, 0, -1);
        face(c, p, new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, x0, y0, z0, horizontal, 1, light, 0, 1, 0);
        face(c, p, new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}}, x0, y0, z0, horizontal, 1, light, 0, -1, 0);
        face(c, p, new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}}, x0, y0, z0, horizontal, 0, light, -1, 0, 0);
        face(c, p, new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}}, x0, y0, z0, horizontal, 0, light, 1, 0, 0);
    }

    /**
     * One face; {@code flat} is the axis the face is perpendicular to (0 x, 1 y, 2 z). The long axis gives v, the
     * remaining one gives u.
     */
    private static void face(VertexConsumer c, PoseStack.Pose p, float[][] corners, float ox, float oy, float oz, boolean horizontal,
                             int flat, int light, float nx, float ny, float nz) {
        int along = horizontal ? 0 : 1;
        // A face across the bar (its end): its two own axes
        int vAxis = flat == along ? (along + 2) % 3 : along;
        int uAxis = 3 - flat - vAxis;
        float[] origin = {ox, oy, oz};
        for (float[] k : corners) {
            float v = k[vAxis] - origin[vAxis];
            float u = k[uAxis] - origin[uAxis];
            vertex(c, p, k[0], k[1], k[2], u, v, light, nx, ny, nz);
        }
    }
}
