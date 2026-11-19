package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.SevenSegment;
import fr.lordfinn.craftmaster.studio.TimerDisplayBlock;
import fr.lordfinn.craftmaster.studio.TimerDisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The giant timer: a gold-framed black panel (2.5 × 1 times its size, standing on the block and against the wall)
 * with a title on a velvet band and the time in big seven-segment digits — what and how as its settings say
 * ({@link TabletView}, like the tablet). By default the show's timer: gold while it runs, red and pulsing for the
 * last ten seconds, "0:00" blinking red when the time is up, the set duration dimmed before the start. Unlit
 * segments stay faintly visible, like a real display (setting).
 */
public class TimerDisplayRenderer implements BlockEntityRenderer<TimerDisplayBlockEntity, TimerDisplayRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/studio_timer.png");
    private static final Geo.Tex TEX = new Geo.Tex(64, 32);
    /** Panel size in panel pixels (scaled to the block entity's size). */
    private static final float PW = 40, PH = 16;

    /** The title band: centre and usable height / width (panel pixels), height of the font's capitals. */
    private static final float BAND_CENTRE = 13f, BAND_TEXT = 1.5f, BAND_WIDTH = 31f, CAPS = 7f;

    private static final int OFF = TabletView.GIANT_OFF;

    public static class State extends BlockEntityRenderState {
        float yaw;
        int size;
        TabletView.View view;
        String band;
        boolean ghost;
    }

    private final Font font;

    public TimerDisplayRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TimerDisplayBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.yaw = be.getBlockState().getValue(TimerDisplayBlock.FACING).toYRot();
        s.size = be.size();
        s.view = TabletView.of(be.settings(), be.clock(), be.overAt(), be.bellAt(), System.currentTimeMillis());
        s.band = TabletView.giantBand(be.settings());
        s.ghost = be.settings().ghostSegments;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));
        // Against the wall (block's back face at z = -0.5), standing just above the block's bottom (a hair above
        // the floor's top face, which flickered through the panel's bottom), centred
        float k = s.size / PH;           // blocks per panel pixel
        poseStack.translate(0, -0.5f + 0.004f, -0.5f + 0.002f);
        poseStack.scale(k, k, k);
        int light = LightCoordsUtil.FULL_BRIGHT;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            // Panel body: 1 px thick; front = the panel face, sides = gold
            float[] face = {0, 0, 40, 16}, gold = {48, 0, 64, 16};
            Geo.box(c, p, TEX, -PW / 2, 0, 0, PW / 2, PH, 1, new float[][]{gold, face, gold, gold, gold, gold}, 0xFFFFFFFF, light);
        });

        TabletView.View v = s.view;
        int lit = TabletView.lit(v.rgb(), v.bright(), OFF);
        int unlit = s.ghost ? 0xFF000000 | OFF : 0;
        // Same opaque pass as the panel: the translucent text pass drew nothing once the other chantiers' renderers
        // were in the game (the digits vanished in the merged build)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> digits(c, p, v.text(), lit, unlit));

        // Title on the velvet band (panel y 12..14): "3 · Le Pont" (or the show's name). Sized on the capitals
        // (7 of the font's 9 px line), centred on the band, never wider than the band
        if (s.band.isEmpty()) {
            poseStack.popPose();
            return;
        }
        FormattedCharSequence text = Component.literal(s.band).getVisualOrderText();
        int w = Math.max(1, font.width(text));
        float scale = Math.min(BAND_TEXT / CAPS, BAND_WIDTH / w);
        poseStack.pushPose();
        poseStack.translate(0, BAND_CENTRE + CAPS * scale / 2, 1.25f);
        poseStack.scale(scale, -scale, scale);
        collector.submitText(poseStack, -w / 2f, 0, text, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFFFFD86A, 0, 0);
        poseStack.popPose();
        poseStack.popPose();
    }

    /** The clock in the digits area (x -16..16, y 1.5..11.5 in panel pixels), centred, scaled down to fit. */
    private static void digits(VertexConsumer c, PoseStack.Pose p, String clock, int lit, int unlit) {
        float dw = 6, dh = 10, gap = 1.2f, colon = 2.4f, dot = 2f;
        float total = 0;
        for (char ch : clock.toCharArray()) total += (ch == ':' ? colon : ch == '.' ? dot : dw) + gap;
        total -= gap;
        // Inside the gold frame (3 px each side), with a margin
        float scale = Math.min(1f, 32f / total);
        float x = -total * scale / 2, y0 = 1.5f + (10 - dh * scale) / 2;
        // In front of the panel face (z = 1) by enough not to flicker from far away
        float z = 1.25f;
        for (char ch : clock.toCharArray()) {
            if (ch == ':') {
                float cx = x + colon * scale / 2, d = 0.7f * scale;
                rect(c, p, cx - d, y0 + 2.6f * scale, cx + d, y0 + 2.6f * scale + 2 * d, z, lit);
                rect(c, p, cx - d, y0 + 6.2f * scale, cx + d, y0 + 6.2f * scale + 2 * d, z, lit);
                x += (colon + gap) * scale;
                continue;
            }
            if (ch == '.') {
                float cx = x + dot * scale / 2, d = 0.7f * scale;
                rect(c, p, cx - d, y0, cx + d, y0 + 2 * d, z, lit);
                x += (dot + gap) * scale;
                continue;
            }
            digit(c, p, x, y0, dw * scale, dh * scale, SevenSegment.segments(ch), z, lit, unlit);
            x += (dw + gap) * scale;
        }
    }

    /** One digit, bottom-left at (x, y), every segment drawn (lit or dim). */
    private static void digit(VertexConsumer c, PoseStack.Pose p, float x, float y, float w, float h, int on, float z, int lit, int unlit) {
        float t = w * 0.2f, g = t * 0.18f, mid = y + h / 2;
        // a, g, d (horizontal) ; f, b (top) ; e, c (bottom)
        seg(c, p, on, SevenSegment.A, x + t, y + h - t, x + w - t, y + h, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.G, x + t, mid - t / 2, x + w - t, mid + t / 2, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.D, x + t, y, x + w - t, y + t, z, lit, unlit, g, true);
        seg(c, p, on, SevenSegment.F, x, mid, x + t, y + h - t, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.B, x + w - t, mid, x + w, y + h - t, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.E, x, y + t, x + t, mid, z, lit, unlit, g, false);
        seg(c, p, on, SevenSegment.C, x + w - t, y + t, x + w, mid, z, lit, unlit, g, false);
    }

    private static void seg(VertexConsumer c, PoseStack.Pose p, int on, int bit, float x0, float y0, float x1, float y1, float z,
                            int lit, int unlit, float g, boolean horizontal) {
        // A small gap at the ends so neighbouring segments read as separate bars
        if (horizontal) {
            x0 += g;
            x1 -= g;
        } else {
            y0 += g;
            y1 -= g;
        }
        if ((on & bit) == 0 && unlit == 0) return;
        rect(c, p, x0, y0, x1, y1, z + ((on & bit) != 0 ? 0.1f : 0), (on & bit) != 0 ? lit : unlit);
    }

    /** White pixels of the texture, tinted by the vertex colour. */
    private static final float[] WHITE = {42, 2, 46, 6};

    /** A flat rectangle facing +z (only its front face is drawn), sampling the white pixels of the texture. */
    private static void rect(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, int color) {
        Geo.box(c, p, TEX, x0, y0, z - 0.01f, x1, y1, z, new float[][]{null, WHITE, null, null, null, null}, color,
                LightCoordsUtil.FULL_BRIGHT);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /** Culled on the whole panel, not the block. */
    public static AABB panelBox(TimerDisplayBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(be.size() * 1.3);
    }
}
