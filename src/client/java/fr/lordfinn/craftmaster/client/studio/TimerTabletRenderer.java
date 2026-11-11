package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

/**
 * The timer tablet: a picture frame around a passe-partout and dark glass with seven-segment digits, like the one
 * held up in Taskmaster. In pixels (1/16 block), 16 × 12 × size, 1 deep; 1 texel = 1 px everywhere (no mixels):
 * <ul>
 * <li>wooden frames (oak, walnut = dark oak) are real wood: stiles in the game's stripped log (grain along them),
 * rails in its planks, standing half a pixel proud of the passe-partout, the back in planks;</li>
 * <li>the other frames (lacquer, brass, brushed steel) are layers of {@code textures/entity/timer_tablet.png}
 * (scripts/gen_tablet.py), with a back of the same material (screws on metal);</li>
 * <li>the digits are 3 × 5 cells of whole pixels.</li>
 * </ul>
 * Standing on a back strut, on a painter's easel (stripped oak legs behind it, an oak tray it rests on), hung on a
 * wall or lying flat, in any of 16 directions.
 */
public class TimerTabletRenderer implements BlockEntityRenderer<TimerTabletBlockEntity, TimerTabletRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/timer_tablet.png");
    static final Geo.Tex TEX = new Geo.Tex(160, 864);
    private static final Geo.Tex BLOCK = new Geo.Tex(16, 16);
    private static final int ROW = 48;
    private static final int[] SIZE_X = {0, 0, 16, 48, 96};
    private static final int FRAMES = TabletClock.FrameColor.values().length;
    private static final int MATS_ROW = FRAMES, SCREEN_ROW = FRAMES + 3, BACKS_ROW = SCREEN_ROW + 1;

    private static Identifier block(String name) {
        return Identifier.withDefaultNamespace("textures/block/" + name + ".png");
    }

    private static final Identifier OAK_PLANKS = block("oak_planks"), OAK_LOG = block("stripped_oak_log"),
            DARK_PLANKS = block("dark_oak_planks"), DARK_LOG = block("stripped_dark_oak_log");

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
        s.view = TabletView.of(be.settings(), be.clock(), be.overAt(), be.bellAt(), System.currentTimeMillis());
    }

    /** UV rectangle {u0, v0, u1, v1} of a layer row at a size, w × h px from (x, y). */
    static float[] uv(int row, int size, float x, float y, float w, float h) {
        float u = SIZE_X[size] + x, v = row * ROW + y;
        return new float[]{u, v, u + w, v + h};
    }

    private static boolean wood(TabletClock.FrameColor f) {
        return f == TabletClock.FrameColor.OAK || f == TabletClock.FrameColor.WALNUT;
    }

    // ---------------------------------------------------------------- textured boxes, 1 texel = 1 px

    /**
     * A box in a 16 × 16 block texture, every face tiled by 16 px pieces at the texture's own pixel size (the planks
     * of a long rail repeat like blocks, never stretch).
     */
    private static void tiled(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        // north / south: x × y ; west / east: z × y ; up / down: x × z
        pieces(x0, x1, y0, y1, (a0, a1, b0, b1, u0, v0) -> {
            float[] f = {u0, v0, u0 + (a1 - a0), v0 + (b1 - b0)};
            Geo.box(c, p, BLOCK, a0, b0, z0, a1, b1, z1, new float[][]{f, f, null, null, null, null}, 0xFFFFFFFF, light);
        });
        pieces(z0, z1, y0, y1, (a0, a1, b0, b1, u0, v0) -> {
            float[] f = {u0, v0, u0 + (a1 - a0), v0 + (b1 - b0)};
            Geo.box(c, p, BLOCK, x0, b0, a0, x1, b1, a1, new float[][]{null, null, f, f, null, null}, 0xFFFFFFFF, light);
        });
        pieces(x0, x1, z0, z1, (a0, a1, b0, b1, u0, v0) -> {
            float[] f = {u0, v0, u0 + (a1 - a0), v0 + (b1 - b0)};
            Geo.box(c, p, BLOCK, a0, y0, b0, a1, y1, b1, new float[][]{null, null, null, null, f, f}, 0xFFFFFFFF, light);
        });
    }

    private interface Piece {
        void at(float a0, float a1, float b0, float b1, float u0, float v0);
    }

    /** Splits a rectangle at multiples of 16 px; each piece gets the texel offset of its own position. */
    private static void pieces(float a0, float a1, float b0, float b1, Piece out) {
        for (float a = a0; a < a1 - 1e-4f; ) {
            float an = Math.min(a1, (float) (Math.floor(a / 16 + 1e-4) + 1) * 16);
            for (float b = b0; b < b1 - 1e-4f; ) {
                float bn = Math.min(b1, (float) (Math.floor(b / 16 + 1e-4) + 1) * 16);
                float u = a - (float) Math.floor(a / 16) * 16, v = 16 - (bn - (float) Math.floor((bn - 1e-4f) / 16) * 16);
                out.at(a, an, b, bn, u, v);
                b = bn;
            }
            a = an;
        }
    }

    /** A box textured from a 48 px row of the tablet atlas, each face cut to its own size (≤ 48 px). */
    private static void atlasBox(VertexConsumer c, PoseStack.Pose p, int row, int size, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float dx = x1 - x0, dy = Math.min(48, y1 - y0), dz = z1 - z0;
        float[] ns = uv(row, size, 0, 0, Math.min(16 * size, dx), dy), we = uv(row, size, 0, 0, dz, dy), ud = uv(row, size, 0, 0, Math.min(16 * size, dx), dz);
        Geo.box(c, p, TEX, x0, y0, z0, x1, y1, z1, new float[][]{ns, ns, we, we, ud, ud}, 0xFFFFFFFF, light);
    }

    // ---------------------------------------------------------------- poses

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TabletClock.Settings st = s.settings;
        int size = st.size;
        float w = 16 * size, h = 12 * size;
        int light = s.lightCoords;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.rotate(Axis.YP.rotationDegrees(-s.yaw));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        switch (s.pose) {
            case WALL -> {
                pose.translate(0, 8 - h / 2, -8);
                drawTablet(s, pose, collector, w, h, light);
            }
            case FLAT -> {
                pose.translate(0, 0, h / 2);
                pose.rotate(Axis.XP.rotationDegrees(-90));
                drawTablet(s, pose, collector, w, h, light);
            }
            case STAND -> {
                // A photo frame on its back strut, leaning back a little
                pose.translate(0, 0, 2);
                pose.rotate(Axis.XP.rotationDegrees(-10));
                drawStrut(s, pose, collector, h, light);
                drawTablet(s, pose, collector, w, h, light);
            }
            case EASEL -> drawEasel(s, pose, collector, w, h, light);
        }
        pose.popPose();
    }

    /** The strut: from 3/4 of the height on the back down to the floor behind, in the back's material. */
    private static void drawStrut(State s, PoseStack pose, SubmitNodeCollector collector, float h, int light) {
        TabletClock.Settings st = s.settings;
        float top = Math.round(h * 0.75f);
        float len = Math.round(top / (float) Math.cos(Math.toRadians(30)));
        pose.pushPose();
        pose.translate(0, top, -0.5f);
        pose.rotate(Axis.XP.rotationDegrees(-150));
        if (wood(st.frame)) {
            Identifier log = st.frame == TabletClock.FrameColor.OAK ? OAK_LOG : DARK_LOG;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(log), (p, c) -> tiled(c, p, -1.5f, 0, -0.5f, 1.5f, len, 0.5f, light));
        } else {
            int row = BACKS_ROW + st.frame.ordinal();
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, c) ->
                    atlasBox(c, p, row, st.size, -1.5f, 0, -0.5f, 1.5f, len, 0.5f, light));
        }
        pose.popPose();
    }

    /**
     * A painter's easel: two stripped oak legs behind the tablet leaning back by the tilt, an oak tray in front of
     * their foot that the tablet rests on, an oak crossbar above it, a third leg at the back down to the floor.
     */
    private void drawEasel(State s, PoseStack pose, SubmitNodeCollector collector, float w, float h, int light) {
        float tilt = Math.max(5, s.settings.tilt);
        float tray = 4, legLen = tray + h + 3, lx = w / 2 - 2;
        pose.pushPose();
        pose.translate(0, 0, 3);
        pose.rotate(Axis.XP.rotationDegrees(-tilt));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(OAK_LOG), (p, c) -> {
            tiled(c, p, -lx - 2, 0, -2, -lx, legLen, 0, light);
            tiled(c, p, lx, 0, -2, lx + 2, legLen, 0, light);
        });
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(OAK_PLANKS), (p, c) -> {
            // Tray in front of the legs (the tablet stands in it), crossbar joining the legs above the tablet
            tiled(c, p, -w / 2 - 1, tray - 1, 0, w / 2 + 1, tray, 2, light);
            tiled(c, p, -lx - 2, legLen - 3, -2, lx + 2, legLen - 1, -1, light);
        });
        pose.pushPose();
        pose.translate(0, tray, 0);
        drawTablet(s, pose, collector, w, h, light);
        pose.popPose();
        pose.popPose();
        // Back leg: from the top of the front legs down to the floor behind
        double t = Math.toRadians(tilt);
        float jointY = (float) (legLen * Math.cos(t)), jointZ = (float) (3 - legLen * Math.sin(t) - 2);
        float footZ = (float) (jointZ - jointY * Math.tan(t));
        float backLen = (float) (jointY / Math.cos(t));
        pose.pushPose();
        pose.translate(0, 0, footZ);
        pose.rotate(Axis.XP.rotationDegrees(tilt));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(OAK_LOG), (p, c) -> tiled(c, p, -1, 0, -1, 1, backLen, 1, light));
        pose.popPose();
    }

    // ---------------------------------------------------------------- the tablet

    /** Bottom centre at the origin, back at z = 0, glass at z = 1. */
    private void drawTablet(State s, PoseStack pose, SubmitNodeCollector collector, float w, float h, int light) {
        TabletClock.Settings st = s.settings;
        int size = st.size;
        float d = 1;
        int b = size, m = st.mat == TabletClock.Mat.NONE ? 0 : size;
        boolean wood = wood(st.frame);
        int frameRow = st.frame.ordinal();
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            float[] face = uv(SCREEN_ROW, size, 0, 0, w, h), back = uv(BACKS_ROW + frameRow, size, 0, 0, w, h);
            float[] top = uv(frameRow, size, 0, 0, w, 1), bottom = uv(frameRow, size, 0, h - 1, w, 1);
            float[] left = uv(frameRow, size, 0, 0, 1, h), right = uv(frameRow, size, w - 1, 0, 1, h);
            // Wood: its rails cover the sides and the planks the back
            Geo.box(c, p, TEX, -w / 2, 0, 0, w / 2, h, d, wood ? new float[][]{null, face, null, null, null, null}
                    : new float[][]{back, face, left, right, top, bottom}, 0xFFFFFFFF, light);
            if (m > 0) layer(c, p, uv(MATS_ROW + st.mat.ordinal(), size, 0, 0, w, h), w, h, d + 0.02f, light);
            if (!wood) layer(c, p, uv(frameRow, size, 0, 0, w, h), w, h, d + 0.04f, light);
        });
        if (wood) {
            Identifier planks = st.frame == TabletClock.FrameColor.OAK ? OAK_PLANKS : DARK_PLANKS;
            Identifier log = st.frame == TabletClock.FrameColor.OAK ? OAK_LOG : DARK_LOG;
            float zf = d + 0.5f;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(planks), (p, c) -> {
                tiled(c, p, -w / 2, h - b, 0, w / 2, h, zf, light);
                tiled(c, p, -w / 2, 0, 0, w / 2, b, zf, light);
                tiled(c, p, -w / 2, 0, -0.01f, w / 2, h, 0, light);
            });
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(log), (p, c) -> {
                tiled(c, p, -w / 2, b, 0, -w / 2 + b, h - b, zf, light);
                tiled(c, p, w / 2 - b, b, 0, w / 2, h - b, zf, light);
            });
        }
        drawFace(s, pose, collector, w, h, d, b + m);
    }

    /** Digits (on the pixel grid) and the label, inside the glass. */
    private void drawFace(State s, PoseStack pose, SubmitNodeCollector collector, float w, float h, float d, int inset) {
        TabletClock.Settings st = s.settings;
        TabletView.View v = s.view;
        int lit = TabletView.lit(v.rgb(), v.bright(), 0x0A0A0A);
        int ghost = st.ghostSegments ? TabletView.ghost(v.rgb()) : -1;
        boolean label = !v.label().isEmpty();
        int sx0 = Math.round(-w / 2) + inset, sy0 = inset, sw = Math.round(w) - 2 * inset, sh = Math.round(h) - 2 * inset;
        // The label takes the bottom third of the glass
        int labelH = label ? Math.max(2, sh / 3) : 0;
        // A 1 px black margin around the digits, except on the smallest tablet where every column counts
        int side = sw >= 20 ? 1 : 0;
        int dx0 = sx0 + side, dy0 = sy0 + labelH + (label ? 0 : 1), dw = sw - 2 * side, dh = sh - labelH - (label ? 1 : 2);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TimerDisplayRenderer.TEXTURE), (p, c) -> {
            if (!SegmentDraw.pixelText(c, p, v.text(), dx0, dy0, dw, dh, d + 0.06f, lit, ghost))
                SegmentDraw.text(c, p, v.text(), dy0, dw, dh, d + 0.06f, lit, ghost);
        });
        if (label) {
            Component text = Component.literal(v.label());
            FormattedCharSequence seq = text.getVisualOrderText();
            int tw = Math.max(1, font.width(seq));
            float scale = Math.min(labelH * 0.8f / font.lineHeight, sw * 0.9f / tw);
            pose.pushPose();
            pose.translate(0, sy0 + labelH * 0.1f + font.lineHeight * scale, d + 0.1f);
            pose.scale(scale, -scale, scale);
            collector.submitText(pose, -tw / 2f, 0, seq, false, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT,
                    TabletView.lit(v.rgb(), 0.85f, 0x0A0A0A), 0, 0);
            pose.popPose();
        }
    }

    /** A front-facing cut-out layer over the whole face. */
    private static void layer(VertexConsumer c, PoseStack.Pose p, float[] uv, float w, float h, float z, int light) {
        Geo.front(c, p, TEX, -w / 2, 0, w / 2, h, z, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
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
