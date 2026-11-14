package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.camera.CameraEntity;
import fr.lordfinn.craftmaster.camera.CameraMount;
import fr.lordfinn.craftmaster.studio.RigLinks;
import fr.lordfinn.craftmaster.studio.RigRodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import fr.lordfinn.craftmaster.client.studio.FurnitureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * The studio camera on its tripod (LordFinn's tripod and rod models), the black camera body
 * with a brass lens, a hood and a pan bar, and the red "on air" tally light that lights up when the camera is live.
 * Its number is painted on both sides. The detail is in camera.png (scripts/gen_assets.py); the tripod and its rod
 * are LordFinn's own models from his server (scripts/ia_sandbox, drawn as item models).
 *
 * <p>Mounted ({@link CameraMount}, decided by the server) it has no legs: a short stem of the rod under the body on a
 * tripod stand or a rod, the body upside-down under a ceiling (a drop pole, the rod's arm with its plate on the
 * ceiling) or a rod, standing on a wall arm (like the studio light's bracket) or on a rod's side arm. Where the rod
 * itself does not reach the camera (a beam under a camera on top, a rod's side) its arm and clamp are drawn here.
 * The lens is always at the entity's {@link CameraEntity#lens()}, where the broadcast shot is taken from.
 * Not drawn at all in broadcast mode (see EntityRenderDispatcherMixin).
 */
public class CameraRenderer extends EntityRenderer<CameraEntity, CameraRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/camera.png");

    public static class State extends EntityRenderState {
        int number;
        /** Lieu / zone written under the number outside the studio ("PONT ENSURI"), "" in the studio. */
        String place = "";
        float yaw, pitch;
        boolean onAir;
        // LordFinn's tripod and rod (the server's ItemsAdder models) under the camera body
        final ItemStackRenderState tripod = new ItemStackRenderState(), rod = new ItemStackRenderState();
        CameraMount mount = CameraMount.FLOOR;
        /** The rod's arm (or the wall bracket / ceiling drop pole) and its clamp, drawn by the camera. */
        final ItemStackRenderState arm = new ItemStackRenderState(), knuckle = new ItemStackRenderState();
        /** On a rod: its own arm does not reach the camera, draw one (with a clamp). */
        boolean rodArm;
    }

    /** Top of the server's tripod model and bottom of the camera body, above the feet (blocks). */
    private static final float TRIPOD_TOP = 1f, BODY_BOTTOM = 1.3f;
    private static final Identifier ARM = CraftMaster.id("furniture/rig_arm"), KNUCKLE = CraftMaster.id("furniture/rig_knuckle");

    private final ModelPart root, head, tally;
    private final ItemModelResolver items;

    public CameraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        root = createLayer().bakeRoot();
        head = root.getChild("head");
        tally = head.getChild("tally");
        // The legs drawn here are replaced by LordFinn's tripod model: only the head (body, lens, tally) stays
        root.getChild("hub").visible = false;
        for (int i = 0; i < 3; i++) root.getChild("leg" + i).visible = false;
        items = ctx.getItemModelResolver();
    }

    /**
     * Model space (pixels, y down, the floor at y = 24, the front toward -z): the lens axis is at y = 0, i.e.
     * {@link CameraEntity#LENS_HEIGHT} above the feet; the head turns around (0, 2, 0).
     */
    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(34, 8).addBox(-2, 2, -2, 4, 2, 4), PartPose.ZERO);
        // Legs: 22 px from the hub's underside, tilted 22° outward, 120° apart
        float tilt = (float) Math.toRadians(22);
        for (int i = 0; i < 3; i++) {
            float turn = (float) Math.toRadians(i * 120 + 60);
            root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(56, 8).addBox(-0.5f, 0, -0.5f, 1, 22, 1),
                    PartPose.offsetAndRotation(0, 3.5f, 0, tilt, turn, 0));
        }
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -5.5f, -5, 6, 7, 11)          // body
                        .texOffs(34, 0).addBox(-2, -4, -8, 4, 4, 3)             // lens
                        .texOffs(48, 0).addBox(-1.5f, -7.5f, 1, 3, 2, 4)        // hood / viewfinder
                        .texOffs(34, 15).addBox(-0.5f, 0, 6, 1, 1, 5),          // pan bar
                PartPose.offset(0, 2, 0));
        head.addOrReplaceChild("tally", CubeListBuilder.create().texOffs(50, 8).addBox(-0.5f, -6.5f, -4, 1, 1, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CameraEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.number = e.number();
        s.yaw = e.lensYaw();
        s.pitch = e.lensPitch();
        BroadcastNetwork.Shot shot = ShowStateClient.get().shot();
        String key = e.cameraKey();
        s.onAir = shot.kind() == BroadcastNetwork.SHOT_CAMERA && shot.camera().equals(key)
                || shot.kind() == BroadcastNetwork.SHOT_MOSAIC && shot.tiles().stream().anyMatch(t -> t.camera().equals(key));
        String group = e.group();
        s.place = fr.lordfinn.craftmaster.camera.CameraSpot.STUDIO.equals(group) ? "" : group.replace(':', ' ').toUpperCase(java.util.Locale.ROOT);
        items.updateForNonLiving(s.tripod, FurnitureRenderer.stack(CraftMaster.id("furniture/tripod")), ItemDisplayContext.NONE, e);
        items.updateForNonLiving(s.rod, FurnitureRenderer.stack(CraftMaster.id("furniture/tripod_rod")), ItemDisplayContext.NONE, e);
        CameraMount mount = e.mount();
        s.mount = mount;
        s.rodArm = false;
        if (mount.onRod()) {
            int[] b = mount.supportBlock(e.getX(), e.getY(), e.getZ());
            BlockState rod = e.level().getBlockState(new BlockPos(b[0], b[1], b[2]));
            // A rod draws its arm toward a linked side, or right through it when it is a straight line
            s.rodArm = !(rod.getBlock() instanceof RigRodBlock) || !RigLinks.has(RigLinks.arms(RigRodBlock.links(rod)), armSide(mount));
        }
        if (!mount.legs() && mount.kind() != CameraMount.Kind.TRIPOD)
            items.updateForNonLiving(s.arm, FurnitureRenderer.stack(ARM), ItemDisplayContext.NONE, e);
        if (s.rodArm) items.updateForNonLiving(s.knuckle, FurnitureRenderer.stack(KNUCKLE), ItemDisplayContext.NONE, e);
    }

    /** The side of the rod's block toward the camera. */
    private static int armSide(CameraMount mount) {
        return switch (mount.kind()) {
            case ROD_TOP -> CameraMount.UP;
            case ROD_UNDER -> CameraMount.DOWN;
            default -> mount.side() ^ 1;
        };
    }

    /**
     * A hidden camera is drawn for no one, except the organisers holding a camera or the travelling marker (to find
     * it again and click it).
     */
    @Override
    //? if >=26.3 {
    public boolean shouldRender(CameraEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, float partialTick) {
        if (e.hiddenProp() && !revealHidden()) return false;
        return super.shouldRender(e, frustum, x, y, z, partialTick);
    }
    //?} else {
    /*public boolean shouldRender(CameraEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (e.hiddenProp() && !revealHidden()) return false;
        return super.shouldRender(e, frustum, x, y, z);
    }
    *///?}

    static boolean revealHidden() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) return false;
        var held = mc.player.getMainHandItem().getItem();
        return held instanceof fr.lordfinn.craftmaster.camera.CameraItem || held instanceof fr.lordfinn.craftmaster.camera.PathMarkerItem;
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        CameraMount mount = s.mount;
        if (mount.legs()) submitTripod(s, poseStack, collector);
        else submitMount(s, poseStack, collector);
        poseStack.pushPose();
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        poseStack.scale(-1, -1, 1);
        // Model space: y down, the lens axis at y = 0
        poseStack.translate(0, -(float) mount.lensDy() - 0.001f, 0);
        // Hung: upside-down around the lens axis (the pan head on top); seen that way, the tilt turns the other way
        if (mount.hung()) poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.ZP.rotationDegrees(180));
        float pitch = mount.hung() ? -s.pitch : s.pitch;
        float xRot = (float) Math.toRadians(pitch);
        boolean onAir = s.onAir;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            // Set here: the geometry is drawn later, after every camera's submit
            head.xRot = xRot;
            tally.visible = false;
            root.render(ps, c, light, OverlayTexture.NO_OVERLAY);
            tally.visible = true;
        });
        // Tally light: full bright red when live, dim otherwise
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            ps.translate(0, 2 / 16f, 0);
            ps./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotation(xRot));
            tally.render(ps, c, onAir ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, onAir ? 0xFFFFFFFF : 0xFF603030);
        });
        submitNumber(s, pitch, mount.hung(), poseStack, collector);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }

    /**
     * Mounted: the stem from the support to the body (the tripod's rod, short), and what holds it: the drop pole
     * under a ceiling, the wall arm, the rod's arm and clamp where the rod does not reach the camera. All around the
     * anchor (the entity's position), turning with nothing but the camera's stem.
     */
    private static void submitMount(State s, PoseStack pose, SubmitNodeCollector collector) {
        CameraMount m = s.mount;
        int light = s.lightCoords;
        float stem = (float) CameraMount.STEM;
        pose.pushPose();
        pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        pose.translate(0, m.hung() ? -stem / 2 : stem / 2, 0);
        pose.scale(1, stem, 1);
        s.rod.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        float sx = m.stepX(), sz = m.stepZ();
        switch (m.kind()) {
            // The drop pole: the rod's arm, from its plate on the ceiling down to the camera
            case CEILING -> arm(s.arm, pose, collector, light, 0, 0, 0, CameraMount.UP);
            // The bracket, like the studio light's: from the middle of the block (tube top at 9 px) to the wall
            case WALL -> arm(s.arm, pose, collector, light, 0, -1 / 16f, 0, m.side());
            case ROD_TOP, ROD_UNDER, ROD_SIDE -> {
                // On a rod's side: the same bracket, its plate against the end of the rod's arm
                if (m.kind() == CameraMount.Kind.ROD_SIDE) arm(s.arm, pose, collector, light, 0, -1 / 16f, 0, m.side());
                if (!s.rodArm) break;
                // The rod's own arm toward the camera, from the rod's centre, and the clamp holding it
                float cx = 0, cy = m.kind() == CameraMount.Kind.ROD_TOP ? -0.5f : 0.5f, cz = 0;
                if (m.kind() == CameraMount.Kind.ROD_SIDE) {
                    cx = sx;
                    cy = -1 / 16f;
                    cz = sz;
                }
                arm(s.arm, pose, collector, light, cx, cy, cz, armSide(m));
                pose.pushPose();
                pose.translate(cx, cy, cz);
                s.knuckle.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            default -> {}
        }
    }

    /** The rod's arm model (centre to a face, plate on that face) drawn around {@code (x, y, z)}, toward {@code side}. */
    private static void arm(ItemStackRenderState arm, PoseStack pose, SubmitNodeCollector collector, int light,
                            float x, float y, float z, int side) {
        pose.pushPose();
        pose.translate(x, y, z);
        if (side == CameraMount.DOWN) pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(180));
        else if (side != CameraMount.UP) {
            // As FurnitureRenderer's bracket: up turned toward the side (south 0°, west 90°, north 180°, east 270°)
            float yRot = switch (side) {
                case 2 -> 180;
                case 4 -> 90;
                case 5 -> 270;
                default -> 0;
            };
            pose./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-yRot));
            pose./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(90));
        }
        arm.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** The tripod (1 block, its model) and its rod up to the camera body, turning with the camera. */
    private static void submitTripod(State s, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        poseStack.translate(0, 0.5, 0);
        s.tripod.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        float h = BODY_BOTTOM - TRIPOD_TOP;
        poseStack.pushPose();
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - s.yaw));
        poseStack.translate(0, TRIPOD_TOP + h / 2, 0);
        poseStack.scale(1, h, 1);
        s.rod.submit(poseStack, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** The camera's number on both sides of the body, like the painted numbers of studio cameras. */
    private void submitNumber(State s, float pitch, boolean hung, PoseStack poseStack, SubmitNodeCollector collector) {
        Font font = getFont();
        Component text = Component.literal(String.valueOf(s.number)).withStyle(ChatFormatting.BOLD);
        float w = font.width(text);
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(0, 2 / 16f, 0);
            poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(pitch));
            // On the side face (x = ±3 px), turned to face outward, centred on the body
            poseStack.translate(side * 3.02f / 16f, -2 / 16f, 0.5f / 16f);
            poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-side * 90));
            float scale = 0.45f / 16f;
            poseStack.scale(scale, scale, scale);
            // Upside-down camera: the number still reads the right way up
            if (hung) poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.ZP.rotationDegrees(180));
            collector.submitText(poseStack, -w / 2, -4, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                    LightCoordsUtil.FULL_BRIGHT, 0xFFFFE39A, 0, 0);
            if (!s.place.isEmpty()) {
                // Outside the studio: the lieu (and zone) in small letters under the number
                Component place = Component.literal(s.place);
                float pw = font.width(place);
                float k = Math.min(0.42f, 17f / Math.max(1, pw));
                poseStack.translate(0, 5.5f, 0);
                poseStack.scale(k, k, k);
                collector.submitText(poseStack, -pw / 2, 0, place.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                        LightCoordsUtil.FULL_BRIGHT, 0xFFE8D9B8, 0, 0);
            }
            poseStack.popPose();
        }
    }
}
