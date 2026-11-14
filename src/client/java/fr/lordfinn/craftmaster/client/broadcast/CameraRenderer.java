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
    /** All white: the travelling points take their colour from the tint. */
    private static final Identifier WHITE = CraftMaster.id("textures/entity/white.png");

    public static class State extends EntityRenderState {
        /** A hidden camera: only its travellings are drawn. */
        boolean bodyHidden;
        /** Travelling ghosts: x, y, z from the anchor, yaw, pitch, tint (ARGB bits). */
        final java.util.List<float[]> ghosts = new java.util.ArrayList<>();
        /** Travelling steps: x, y, z from the anchor, yaw, pitch, tint (ARGB bits), size; a point, or a ghost camera. */
        final java.util.List<float[]> dots = new java.util.ArrayList<>(), stepGhosts = new java.util.ArrayList<>();
        /** The paths themselves, dotted: x, y, z from the anchor, tint (ARGB bits), size. */
        final java.util.List<float[]> trail = new java.util.ArrayList<>();
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

    private final ModelPart root, head, tally, dot;
    private final ItemModelResolver items;

    public CameraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        root = createLayer().bakeRoot();
        head = root.getChild("head");
        tally = head.getChild("tally");
        dot = root.getChild("dot");
        dot.visible = false;
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
        // A centred 1 px cube: the travelling points (drawn on their own, white texture, tinted)
        root.addOrReplaceChild("dot", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, -0.5f, -0.5f, 1, 1, 1), PartPose.ZERO);
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
        s.bodyHidden = e.hiddenProp() && !revealHidden();
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
        // Its travellings' ghosts: a see-through copy moving along each one, in a loop (organisers, ghost option on)
        s.ghosts.clear();
        if (e.showsGhost() && !Broadcast.active() && !DirectorCamera.previewing() && fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) {
            double now = (e.level().getGameTime() + partialTick) / 20.0;
            for (fr.lordfinn.craftmaster.camera.CameraPath p : ShowStateClient.allPaths()) {
                if (!p.camera().equals(key) || p.isEmpty()) continue;
                double total = p.duration();
                double t = total <= 0 ? 0 : now % (total + 1.0);
                fr.lordfinn.craftmaster.camera.CameraPath.Pose v = p.sample(Math.min(t, total), DirectorCamera::playerPosition);
                double dx = v.x() - e.getX(), dy = v.y() - e.getY(), dz = v.z() - e.getZ();
                // A fixed shot from where the camera already stands needs no ghost
                if (p.steps().size() == 1 && dx * dx + (dy - e.mount().lensDy()) * (dy - e.mount().lensDy()) + dz * dz < 0.04) continue;
                int colour = fr.lordfinn.craftmaster.client.studio.TravellingEdit.colourOf(p);
                boolean edited = p.name().equals(fr.lordfinn.craftmaster.client.studio.TravellingEdit.edited(key));
                s.ghosts.add(new float[]{(float) dx, (float) dy, (float) dz, v.yaw(), v.pitch(), tint(colour, edited ? 0x90 : 0x38)});
            }
        }
        // The steps of its travellings shown in the world: a point, or a ghost camera when hovered / selected / new
        // The edited travelling (TravellingEdit) in its colour, bright; the camera's others paler and smaller
        s.dots.clear();
        s.stepGhosts.clear();
        s.trail.clear();
        java.util.Set<String> shown = fr.lordfinn.craftmaster.client.studio.TravellingMarkers.shownPaths(net.minecraft.client.Minecraft.getInstance());
        String edited = fr.lordfinn.craftmaster.client.studio.TravellingEdit.edited(key);
        boolean gizmos = fr.lordfinn.craftmaster.client.studio.TravellingEdit.gizmosActive();
        if (!shown.isEmpty()) for (fr.lordfinn.craftmaster.camera.CameraPath p : ShowStateClient.allPaths()) {
            if (!p.camera().equals(key) || !shown.contains(p.name())) continue;
            boolean main = p.name().equals(edited);
            int colour = fr.lordfinn.craftmaster.client.studio.TravellingEdit.colourOf(p);
            for (int i = 0; i < p.steps().size(); i++) {
                // The step looked through: not drawn in front of the lens
                if (fr.lordfinn.craftmaster.client.studio.ControlView.controllingStep(p.name(), i)) continue;
                fr.lordfinn.craftmaster.camera.CameraPath.Pose st = fr.lordfinn.craftmaster.client.studio.TravellingEdit.pose(p, i);
                boolean selected = fr.lordfinn.craftmaster.client.studio.TravellingEdit.isSelected(p.name(), i);
                // A step tracking an entity or looking at a place: its ghost camera aims there
                float[] aim = p.aimFromStep(i, DirectorCamera::playerPosition);
                float ayaw = aim != null ? aim[0] : st.yaw(), apitch = aim != null ? aim[1] : st.pitch();
                float[] at = {(float) (st.x() - e.getX()), (float) (st.y() - e.getY()), (float) (st.z() - e.getZ()), ayaw, apitch,
                        tint(selected ? brighter(colour) : colour, selected ? 0xD0 : main ? 0xB0 : 0x30), main ? 3.6f : 1.8f};
                if (selected || fr.lordfinn.craftmaster.client.studio.TravellingMarkers.showsGhost(p.name(), i)) s.stepGhosts.add(at);
                // Several travellings' steps at the same place: their colours in turn
                else if (!fr.lordfinn.craftmaster.client.studio.TravellingEdit.hiddenByOverlap(p.name(), i)) s.dots.add(at);
            }
            trail(e, p, colour, main, s.trail);
        }
        // The dotted path going on past both ends of the edited travelling (Axiom on), drawn like the path itself
        if (gizmos) for (var h : fr.lordfinn.craftmaster.client.studio.TravellingEdit.handles()) {
            if (!h.camera().equals(key) || h.lit()) continue;
            // A camera without a travelling: the colour its first one will have
            int colour = h.path() == null ? fr.lordfinn.craftmaster.client.studio.TravellingEdit.colour(0)
                    : fr.lordfinn.craftmaster.client.studio.TravellingEdit.colourOf(h.path());
            s.trail.add(new float[]{(float) (h.x() - e.getX()), (float) (h.y() - e.getY()), (float) (h.z() - e.getZ()), tint(colour, 0xC0), 1.8f});
        }
        // The ghost of a step about to be added, white
        var preview = fr.lordfinn.craftmaster.client.studio.TravellingEdit.preview();
        if (preview != null) {
            if (preview.camera().equals(key)) {
                var v = preview.pose();
                s.stepGhosts.add(new float[]{(float) (v.x() - e.getX()), (float) (v.y() - e.getY()), (float) (v.z() - e.getZ()), v.yaw(), v.pitch(),
                        tint(0xFFFFFFFF, 0xC0), 2.2f});
            }
        }
    }

    /** The path between the steps, dotted (the poses being dragged included). */
    private static void trail(CameraEntity e, fr.lordfinn.craftmaster.camera.CameraPath p, int colour, boolean main, java.util.List<float[]> out) {
        if (p.steps().size() < 2) return;
        for (float[] pt : fr.lordfinn.craftmaster.client.studio.TravellingEdit.trail(fr.lordfinn.craftmaster.client.studio.TravellingEdit.asDrawn(p)))
            out.add(new float[]{(float) (pt[0] - e.getX()), (float) (pt[1] - e.getY()), (float) (pt[2] - e.getZ()), tint(colour, main ? 0xC0 : 0x28), main ? 1.8f : 0.9f});
    }

    private static float tint(int rgb, int alpha) {
        return Float.intBitsToFloat((alpha << 24) | (rgb & 0xFFFFFF));
    }

    private static int brighter(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return 0xFF000000 | ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
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
        if (fr.lordfinn.craftmaster.client.studio.ControlView.controlling(e.cameraKey())) return false;
        // Hidden: its body is not drawn, but its ghost and travelling points still are (organisers)
        if (e.hiddenProp() && !revealHidden() && !(e.showsGhost() && fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin())) return false;
        return super.shouldRender(e, frustum, x, y, z, partialTick);
    }
    //?} else {
    /*public boolean shouldRender(CameraEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (fr.lordfinn.craftmaster.client.studio.ControlView.controlling(e.cameraKey())) return false;
        // Hidden: its body is not drawn, but its ghost and travelling points still are (organisers)
        if (e.hiddenProp() && !revealHidden() && !(e.showsGhost() && fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin())) return false;
        return super.shouldRender(e, frustum, x, y, z);
    }
    *///?}

    /** With its ghost shown, drawn even off screen (the ghost may be in view while the camera is not). */
    @Override
    protected boolean affectedByCulling(CameraEntity e) {
        return !(e.showsGhost() && fr.lordfinn.craftmaster.client.regie.VoiceRegie.isAdmin()) && super.affectedByCulling(e);
    }

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
        if (s.bodyHidden) {
            submitTravelling(s, poseStack, collector);
            return;
        }
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
        submitTravelling(s, poseStack, collector);
        super.submit(s, poseStack, collector, camera);
    }

    /** Its travellings in the world: the moving ghosts, the steps (points, or ghost cameras). */
    private void submitTravelling(State s, PoseStack poseStack, SubmitNodeCollector collector) {
        for (float[] gh : s.ghosts) submitGhost(gh, Float.floatToRawIntBits(gh[5]), poseStack, collector);
        for (float[] gh : s.stepGhosts) submitGhost(gh, Float.floatToRawIntBits(gh[5]), poseStack, collector);
        for (float[] d : s.dots) submitDot(d[0], d[1], d[2], Float.floatToRawIntBits(d[5]), d[6], poseStack, collector);
        for (float[] d : s.trail) submitDot(d[0], d[1], d[2], Float.floatToRawIntBits(d[3]), d[4], poseStack, collector);
    }

    /** A travelling's ghost: the camera's head, see-through, at the lens place {gh[0..2]} (from the anchor), turned to its aim. */
    private void submitGhost(float[] gh, int tint, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(gh[0], gh[1], gh[2]);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(180 - gh[3]));
        poseStack.scale(-1, -1, 1);
        float xRot = (float) Math.toRadians(gh[4]);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            head.xRot = xRot;
            tally.visible = false;
            head.render(ps, c, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
            tally.visible = true;
        });
        poseStack.popPose();
    }

    /** A small glowing point (the tally light's cube, {@code size} times): a travelling's step, or its path. */
    private void submitDot(float x, float y, float z, int tint, float size, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(size, size, size);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WHITE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            dot.visible = true;
            dot.render(ps, c, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
            dot.visible = false;
        });
        poseStack.popPose();
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
