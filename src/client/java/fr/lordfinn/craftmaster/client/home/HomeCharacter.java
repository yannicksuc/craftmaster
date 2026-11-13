package fr.lordfinn.craftmaster.client.home;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.portrait.PortraitSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The master on the left of the title screen: the studio's throne (the item model its block shows, placed the way
 * its invisible floor item frame places it, see {@link fr.lordfinn.craftmaster.client.studio.FurnitureRenderer})
 * with a full size player model wearing his skin sitting in it, thighs on the cushion and back against the
 * backrest, with a slow idle motion (breathing, the head looking around and a little toward the mouse, the hands
 * moving on the armrests). Drawn with the game's own player model and the skin's texture only: nothing of other
 * mods' player rendering (animations, 3D skin layers) is involved.
 * <p>
 * Throne space: blocks, the cushion's centre at the origin, the seated figure facing +z.
 */
public final class HomeCharacter {

    private HomeCharacter() {}

    /** The throne model's height and its cushion's height above the floor (blocks). */
    public static final float THRONE_HEIGHT = 4.6f, SEAT_TOP = 20 / 16f;
    /** The seated figure's visible height (blocks, true size): from under the thighs to the top of the head. */
    public static final float FIGURE_HEIGHT = 22 / 16f;
    /**
     * Where the figure sits on the cushion, toward the backrest (blocks, -z): his back against it. Measured on the
     * throne model (scripts / screenshots), see docs/wiki/Accueil.md.
     */
    private static final float SEAT_BACK = 0.1f;

    private static ModelPart wide, slim, cape;
    private static final ItemStackRenderState THRONE = new ItemStackRenderState();
    private static ItemModel throneModel;

    private static ModelPart model(boolean slimArms) {
        if (wide == null) {
            wide = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
            slim = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot();
            cape = PlayerCapeModel.createCapeLayer().bakeRoot().getChild("body").getChild("cape");
        }
        return slimArms ? slim : wide;
    }

    /** The throne's item model, resolved again only when the models are reloaded (resource packs, F3+T). */
    private static ItemStackRenderState throne() {
        // No item stack: the title screen comes before any world, when items have no components yet. The throne's
        // item model (the one its block shows) is asked for directly.
        Minecraft mc = Minecraft.getInstance();
        ItemModel model = mc.getModelManager().getItemModel(CraftMaster.id("furniture/throne"));
        if (model != throneModel) {
            THRONE.clear();
            model.update(THRONE, ItemStack.EMPTY, mc.getItemModelResolver(), ItemDisplayContext.FIXED, null, null, 0);
            throneModel = model;
        }
        return THRONE;
    }

    /**
     * Submits the throne (if {@code withThrone}) and the seated figure, {@code figureScale} times its true size, in
     * throne space.
     *
     * @param seconds real time clock, for the idle motion
     * @param lookX   -1..1, where the mouse is (the head turns a little toward it)
     */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, PortraitSkins.Skin skin, boolean withThrone,
                              float figureScale, double seconds, double lookX, double lookY) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        if (withThrone) {
            ItemStackRenderState item = throne();
            if (!item.isEmpty()) {
                // As its block's invisible item frame lying on the floor shows it (FurnitureRenderer.frame), the
                // block centred under the cushion
                pose.pushPose();
                pose.translate(0, 0.5f - SEAT_TOP, 0);
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(-90));
                pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180));
                pose.translate(0, 0, 0.5);
                pose.scale(0.5f, 0.5f, 0.5f);
                item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }

        ModelPart root = model(skin.slim());
        pose(root, seconds, lookX, lookY);
        pose.pushPose();
        // Thighs on the cushion (the legs' pivot, 12 px above the feet, sits 2 px above it), back to the backrest
        pose.translate(0, 0, -SEAT_BACK);
        pose.scale(figureScale, figureScale, figureScale);
        pose.translate(0, -10 / 16f, 0);
        // Like LivingEntityRenderer: model space is y down, 1/16 block, the feet 24 px under the origin
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

    /** Sitting with a slow idle motion; never a frown: the head stays level or looks up. */
    private static void pose(ModelPart root, double t, double lookX, double lookY) {
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
        // Hands on the armrests, sliding a little
        rightArm.xRot = (float) Math.toRadians(-36 + 2 * Math.sin(t * 2 * Math.PI / 5.1));
        leftArm.xRot = (float) Math.toRadians(-36 + 2 * Math.sin(t * 2 * Math.PI / 5.7 + 2));
        rightArm.zRot = (float) Math.toRadians(10);
        leftArm.zRot = (float) Math.toRadians(-10);
        // Thighs flat on the cushion, the shins hanging down (the knees bent over the cushion's edge)
        rightLeg.xRot = (float) Math.toRadians(-90);
        leftLeg.xRot = (float) Math.toRadians(-90);
        rightLeg.yRot = (float) Math.toRadians(6);
        leftLeg.yRot = (float) Math.toRadians(-6);
    }
}
