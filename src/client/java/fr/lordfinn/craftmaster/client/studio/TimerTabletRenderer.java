package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.studio.TabletClock;
import fr.lordfinn.craftmaster.studio.TimerTabletBlock;
import fr.lordfinn.craftmaster.studio.TimerTabletBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

/**
 * The timer tablet: a picture frame (frame colour, passe-partout) around a black screen with seven-segment digits,
 * like the one held up in Taskmaster. Standing on a back strut, on an easel, hung on a wall or lying flat, in any
 * of 16 directions. In pixels (1/16 block): 14 × 10.5 × size, 1.2 deep.
 */
public class TimerTabletRenderer implements BlockEntityRenderer<TimerTabletBlockEntity, TimerTabletRenderer.State> {

    private static final int SCREEN = 0xFF0A0A0A;

    public static class State extends BlockEntityRenderState {
        float yaw;
        TabletClock.Pose pose;
        TabletClock.Settings settings;
        TabletView.View view;
    }

    private final Font font;

    public TimerTabletRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TimerTabletBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.yaw = RotationSegment.convertToDegrees(be.getBlockState().getValue(TimerTabletBlock.ROTATION));
        s.pose = be.getBlockState().getValue(TimerTabletBlock.POSE);
        s.settings = be.settings();
        s.view = TabletView.of(be.settings(), be.clock(), be.overAt(), System.currentTimeMillis());
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TabletClock.Settings st = s.settings;
        float w = 14 * st.size, h = 10.5f * st.size, d = 1.2f;
        int light = s.lightCoords;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.rotate(Axis.YP.rotationDegrees(-s.yaw));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        int wood = 0xFF3A2A1C;
        switch (s.pose) {
            case WALL -> pose.translate(0, 8 - h / 2, -8);
            case FLAT -> {
                // Lying on its back, face up, centred on the block
                pose.translate(0, 0, h / 2);
                pose.rotate(Axis.XP.rotationDegrees(-90));
            }
            case STAND -> {
                // A photo frame on its back strut, leaning back a little
                pose.translate(0, 0, 1.5f);
                pose.rotate(Axis.XP.rotationDegrees(-8));
                float sh = h * 0.7f;
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TimerDisplayRenderer.TEXTURE), (p, c) ->
                        SegmentDraw.box(c, p, -0.6f, 0, -sh * 0.45f, 0.6f, sh, 0, frameColor(st), light));
            }
            case EASEL -> {
                // Three wooden legs, a ledge, the tablet leaning back on them
                float legH = 6 + h * 0.9f;
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TimerDisplayRenderer.TEXTURE), (p, c) -> {
                    SegmentDraw.box(c, p, -w / 2 + 1, 0, 0.5f, -w / 2 + 2, legH, 1.5f, wood, light);
                    SegmentDraw.box(c, p, w / 2 - 2, 0, 0.5f, w / 2 - 1, legH, 1.5f, wood, light);
                    SegmentDraw.box(c, p, -0.5f, 0, -4, 0.5f, legH * 0.85f, -3, wood, light);
                    SegmentDraw.box(c, p, -w / 2, 4.5f, 1.5f, w / 2, 5.5f, 4, wood, light);
                });
                pose.translate(0, 5.5f, 2);
                pose.rotate(Axis.XP.rotationDegrees(-st.tilt));
            }
        }
        drawTablet(s, pose, collector, w, h, d, light);
        pose.popPose();
    }

    private static int frameColor(TabletClock.Settings st) {
        return 0xFF000000 | st.frame.rgb;
    }

    /** The tablet itself: bottom centre at the origin, back at z = 0, face at z = d. */
    private void drawTablet(State s, PoseStack pose, SubmitNodeCollector collector, float w, float h, float d, int light) {
        TabletClock.Settings st = s.settings;
        float b = 1.1f * st.size, m = st.mat == TabletClock.Mat.NONE ? 0 : 1.1f * st.size;
        float sx0 = -w / 2 + b + m, sx1 = w / 2 - b - m, sy0 = b + m, sy1 = h - b - m;
        TabletView.View v = s.view;
        int lit = TabletView.lit(v.rgb(), v.bright(), 0x0A0A0A);
        int ghost = st.ghostSegments ? TabletView.ghost(v.rgb()) : -1;
        boolean label = !v.label().isEmpty();
        float digitsBottom = sy0 + (sy1 - sy0) * (label ? 0.3f : 0.15f);
        float digitsH = (sy1 - sy0) * (label ? 0.55f : 0.7f);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TimerDisplayRenderer.TEXTURE), (p, c) -> {
            SegmentDraw.box(c, p, -w / 2, 0, 0, w / 2, h, d, frameColor(st), light);
            if (m > 0) SegmentDraw.rect(c, p, -w / 2 + b, b, w / 2 - b, h - b, d + 0.01f, 0xFF000000 | st.mat.rgb);
            SegmentDraw.rect(c, p, sx0, sy0, sx1, sy1, d + 0.02f, SCREEN);
            SegmentDraw.text(c, p, v.text(), digitsBottom, (sx1 - sx0) * 0.9f, digitsH, d + 0.03f, lit, ghost);
        });
        if (label) {
            Component text = Component.literal(v.label());
            FormattedCharSequence seq = text.getVisualOrderText();
            int tw = Math.max(1, font.width(seq));
            float scale = Math.min((sy1 - sy0) * 0.16f / font.lineHeight, (sx1 - sx0) * 0.9f / tw);
            pose.pushPose();
            pose.translate(0, sy0 + (sy1 - sy0) * 0.06f + font.lineHeight * scale, d + 0.08f);
            pose.scale(scale, -scale, scale);
            collector.submitText(pose, -tw / 2f, 0, seq, false, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT,
                    TabletView.lit(v.rgb(), 0.85f, 0x0A0A0A), 0, 0);
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
