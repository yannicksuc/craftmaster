package fr.lordfinn.craftmaster.client.home;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.client.portrait.PortraitSkins;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * The master on the left of the title screen: a full size player model wearing his skin, sitting (on the throne
 * of the background picture) or standing, with a slow idle motion (breathing, the head looking around and a little
 * toward the mouse, the hands moving). Drawn with the game's own player model and the skin's texture only: nothing
 * of other mods' player rendering (animations, 3D skin layers) is involved.
 * <p>
 * Character space: blocks, the feet on the origin, facing +z.
 */
public final class HomeCharacter {

    private HomeCharacter() {}

    /** Visible height (blocks) and the height of its middle: sitting, the legs stick out forward at hip height. */
    public static float height(boolean seated) {
        return seated ? 22 / 16f : 32 / 16f;
    }

    public static float middle(boolean seated) {
        return seated ? 21 / 16f : 1f;
    }

    private static ModelPart wide, slim, cape;

    private static ModelPart model(boolean slimArms) {
        if (wide == null) {
            wide = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
            slim = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot();
            cape = PlayerCapeModel.createCapeLayer().bakeRoot().getChild("body").getChild("cape");
        }
        return slimArms ? slim : wide;
    }

    /**
     * Submits the figure in character space.
     *
     * @param seconds real time clock, for the idle motion
     * @param lookX   -1..1, where the mouse is (the head turns a little toward it)
     */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, PortraitSkins.Skin skin, boolean seated,
                              double seconds, double lookX, double lookY) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        ModelPart root = model(skin.slim());
        pose(root, seated, seconds, lookX, lookY);
        // Like LivingEntityRenderer: model space is y down, 1/16 block, the feet 24 px under the origin
        pose.pushPose();
        pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(skin.texture()), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            root.render(ps, c, light, OverlayTexture.NO_OVERLAY);
        });
        if (skin.cape() != null) {
            ModelPart body = root.getChild("body");
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(skin.cape()), (p, c) -> {
                PoseStack ps = new PoseStack();
                ps.last().set(p);
                body.translateAndRotate(ps);
                cape.render(ps, c, light, OverlayTexture.NO_OVERLAY);
            });
        }
        pose.popPose();
    }

    /** The pose with a slow idle motion; never a frown: the head stays level or looks up. */
    private static void pose(ModelPart root, boolean seated, double t, double lookX, double lookY) {
        root.getAllParts().forEach(ModelPart::resetPose);
        ModelPart head = root.getChild("head"), body = root.getChild("body");
        ModelPart rightArm = root.getChild("right_arm"), leftArm = root.getChild("left_arm");
        ModelPart rightLeg = root.getChild("right_leg"), leftLeg = root.getChild("left_leg");
        float breathe = (float) Math.sin(t * 2 * Math.PI / 4.2);
        head.yRot = (float) Math.toRadians(9 * Math.sin(t * 2 * Math.PI / 9.5) + 10 * lookX);
        head.xRot = (float) Math.toRadians(-2 - 2.5 * Math.sin(t * 2 * Math.PI / 6.3 + 1) + 5 * lookY);
        head.y += 0.25f * breathe;
        body.y += 0.15f * breathe;
        rightArm.y += 0.15f * breathe;
        leftArm.y += 0.15f * breathe;
        if (seated) {
            // Hands on the armrests, sliding a little; vanilla's riding legs
            rightArm.xRot = (float) Math.toRadians(-36 + 2 * Math.sin(t * 2 * Math.PI / 5.1));
            leftArm.xRot = (float) Math.toRadians(-36 + 2 * Math.sin(t * 2 * Math.PI / 5.7 + 2));
            rightArm.zRot = (float) Math.toRadians(8);
            leftArm.zRot = (float) Math.toRadians(-8);
            rightLeg.xRot = -1.4137167f;
            rightLeg.yRot = (float) (Math.PI / 10);
            rightLeg.zRot = 0.07853982f;
            leftLeg.xRot = -1.4137167f;
            leftLeg.yRot = (float) (-Math.PI / 10);
            leftLeg.zRot = -0.07853982f;
        } else {
            // Standing at ease, the arms swinging very slightly
            rightArm.xRot = (float) Math.toRadians(3 * Math.sin(t * 2 * Math.PI / 5.1));
            leftArm.xRot = (float) Math.toRadians(-3 * Math.sin(t * 2 * Math.PI / 5.1));
            rightArm.zRot = (float) Math.toRadians(4 + 1.5 * breathe);
            leftArm.zRot = (float) Math.toRadians(-4 - 1.5 * breathe);
            rightLeg.zRot = (float) Math.toRadians(2);
            leftLeg.zRot = (float) Math.toRadians(-2);
        }
    }
}
