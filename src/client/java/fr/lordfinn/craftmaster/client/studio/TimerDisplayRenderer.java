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
 * with the round's title on a velvet band and the time in big seven-segment digits. Gold while it runs, red and
 * pulsing for the last ten seconds, "0:00" blinking red when the time is up, the set duration dimmed before the
 * start. Unlit segments stay faintly visible, like a real display.
 */
public class TimerDisplayRenderer implements BlockEntityRenderer<TimerDisplayBlockEntity, TimerDisplayRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/studio_timer.png");
    private static final Geo.Tex TEX = new Geo.Tex(64, 32);
    /** Panel size in panel pixels (scaled to the block entity's size). */
    private static final float PW = 40, PH = 16;

    private static final int GOLD = 0xFFC83C, RED = 0xFF3A30, CREAM = 0xE8D9B0, OFF = 0x2A1C14;

    public static class State extends BlockEntityRenderState {
        float yaw;
        int size;
        StudioTimerClient.View view;
        float pulse;
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
        s.view = StudioTimerClient.view();
        s.pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2));
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-s.yaw));
        // Against the wall (block's back face at z = -0.5), standing on the block's bottom, centred
        float k = s.size / PH;           // blocks per panel pixel
        poseStack.translate(0, -0.5f, -0.5f);
        poseStack.scale(k, k, k);
        int light = LightCoordsUtil.FULL_BRIGHT;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            // Panel body: 1 px thick; front = the panel face, sides = gold
            float[] face = {0, 0, 40, 16}, gold = {48, 0, 64, 16};
            Geo.box(c, p, TEX, -PW / 2, 0, 0, PW / 2, PH, 1, new float[][]{gold, face, gold, gold, gold, gold}, 0xFFFFFFFF, light);
        });

        StudioTimerClient.View v = s.view;
        int color = switch (v.mode()) {
            case RUNNING -> v.seconds() <= 10 ? RED : GOLD;
            case TIME_UP -> RED;
            case READY -> CREAM;
            case IDLE -> GOLD;
        };
        int alpha = switch (v.mode()) {
            case RUNNING -> v.seconds() <= 10 ? (int) (170 + 85 * s.pulse) : 255;
            case TIME_UP -> s.pulse > 0.5 ? 255 : 60;
            case READY -> 200;
            case IDLE -> 130;
        };
        int lit = blend(color, alpha);
        int unlit = 0xFF000000 | OFF;
        // Same opaque pass as the panel: the translucent text pass drew nothing once the other chantiers' renderers
        // were in the game (the digits vanished in the merged build)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> digits(c, p, v.clock(), lit, unlit));

        // Title on the velvet band: "3 · Le Pont" (or the show's name)
        Component title = Component.literal(v.title().isEmpty() ? "CraftMaster" : v.title());
        FormattedCharSequence text = title.getVisualOrderText();
        int w = Math.max(1, font.width(text));
        float scale = Math.min(2.2f / font.lineHeight, 34f / w);
        poseStack.pushPose();
        poseStack.translate(0, 13.6f + font.lineHeight * scale / 2 - 0.2f, 1.25f);
        poseStack.scale(scale, -scale, scale);
        collector.submitText(poseStack, -w / 2f, 0, text, false, Font.DisplayMode.POLYGON_OFFSET, light, 0xFFFFD86A, 0, 0);
        poseStack.popPose();
        poseStack.popPose();
    }

    /** Opaque colour of a lit segment: the colour scaled toward the unlit one by {@code alpha} (0..255). */
    private static int blend(int rgb, int alpha) {
        float t = alpha / 255f;
        int r = (int) (((rgb >> 16) & 255) * t + ((OFF >> 16) & 255) * (1 - t));
        int g = (int) (((rgb >> 8) & 255) * t + ((OFF >> 8) & 255) * (1 - t));
        int b = (int) ((rgb & 255) * t + (OFF & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The clock in the digits area (x -19..19, y 1.5..11.5 in panel pixels), centred, scaled down to fit. */
    private static void digits(VertexConsumer c, PoseStack.Pose p, String clock, int lit, int unlit) {
        float dw = 6, dh = 10, gap = 1.2f, colon = 2.4f;
        float total = 0;
        for (char ch : clock.toCharArray()) total += (ch == ':' ? colon : dw) + gap;
        total -= gap;
        float scale = Math.min(1f, 37f / total);
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
