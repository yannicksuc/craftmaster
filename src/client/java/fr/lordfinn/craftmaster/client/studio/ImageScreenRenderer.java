package fr.lordfinn.craftmaster.client.studio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.client.compat.ClientCompat;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.ImageScreenEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Draws a studio screen: a thin black bezel standing out of the wall, and the picture on it, lit by itself (full
 * brightness, no shading, like a real screen). No image, loading or failed: the CraftMaster test card, with a short
 * message for the last two.
 */
public class ImageScreenRenderer extends EntityRenderer<ImageScreenEntity, ImageScreenRenderer.State> {

    public static final Identifier BEZEL = CraftMaster.id("textures/entity/studio_screen.png");
    public static final Identifier TEST_CARD = CraftMaster.id("textures/entity/studio_test_card.png");
    private static final Geo.Tex BEZEL_TEX = new Geo.Tex(32, 32);
    /** Bezel width around the picture (blocks). */
    private static final float BORDER = 1 / 16f;

    public static class State extends EntityRenderState {
        float yaw;
        int width, height;
        boolean fit;
        Identifier picture;
        int pictureW, pictureH;
        Component message;
    }

    public ImageScreenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ImageScreenEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.yaw = e.getFacing().toYRot();
        s.width = e.width();
        s.height = e.height();
        s.fit = e.fit();
        s.picture = TEST_CARD;
        s.pictureW = 16;
        s.pictureH = 9;
        s.message = null;
        if (!e.url().isEmpty()) {
            ScreenImages.Entry img = ScreenImages.get(e.url());
            switch (img.status()) {
                case READY -> {
                    s.picture = img.texture();
                    s.pictureW = img.width();
                    s.pictureH = img.height();
                }
                case LOADING -> s.message = Component.translatable("screen.craftmaster.studio_screen.loading").withStyle(ChatFormatting.WHITE);
                case FAILED -> s.message = Component.translatable("screen.craftmaster.studio_screen.failed", img.error()).withStyle(ChatFormatting.YELLOW);
            }
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float hw = s.width / 2f, hh = s.height / 2f, hd = (float) ImageScreenEntity.DEPTH / 2f;
        int light = s.lightCoords;
        poseStack.pushPose();
        // Local axes: x to the viewer's right, y up, +z out of the wall toward the audience
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));

        // Bezel: glossy black front frame, dark sides, plain back
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(BEZEL), (p, c) -> {
            float[] front = {0, 0, 16, 16}, side = {16, 0, 18, 16}, back = {16, 16, 32, 32};
            Geo.box(c, p, BEZEL_TEX, -hw, -hh, -hd, hw, hh, hd,
                    new float[][]{back, front, side, side, side, side}, 0xFFFFFFFF, light);
        });

        // The picture: inside the bezel, whole with bars ("fit") or stretched
        float iw = s.width - 2 * BORDER, ih = s.height - 2 * BORDER;
        float pw = iw, ph = ih;
        if (s.fit && s.pictureW > 0 && s.pictureH > 0) {
            float ratio = s.pictureW / (float) s.pictureH;
            if (ratio > iw / ih) ph = iw / ratio;
            else pw = ih * ratio;
        }
        float x0 = -pw / 2, x1 = pw / 2, y0 = -ph / 2, y1 = ph / 2, z = hd + 0.003f;
        collector.submitCustomGeometry(poseStack, RenderTypes.text(s.picture), (p, c) -> {
            int full = LightCoordsUtil.FULL_BRIGHT;
            c.addVertex(p, x0, y1, z).setColor(0xFFFFFFFF).setUv(0, 0).setLight(full);
            c.addVertex(p, x0, y0, z).setColor(0xFFFFFFFF).setUv(0, 1).setLight(full);
            c.addVertex(p, x1, y0, z).setColor(0xFFFFFFFF).setUv(1, 1).setLight(full);
            c.addVertex(p, x1, y1, z).setColor(0xFFFFFFFF).setUv(1, 0).setLight(full);
        });

        if (s.message != null) {
            Font font = getFont();
            poseStack.pushPose();
            float scale = Math.min(s.width, s.height) / 120f;
            poseStack.translate(0, -hh + BORDER + 0.06f + 10 * scale, hd + 0.006f);
            poseStack.scale(scale, -scale, scale);
            int w = font.width(s.message);
            ClientCompat.textBackground(collector, poseStack, -w / 2f - 3, -2, w / 2f + 3, font.lineHeight + 1, 0xB0000000,
                    Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT);
            collector.submitText(poseStack, -w / 2f, 0, s.message.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                    LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, ClientCompat.lineBackground(0xB0000000), 0);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
