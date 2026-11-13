package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.client.compat.ClientCompat;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.portrait.Pose;
import fr.lordfinn.craftmaster.portrait.Pose.Part;
import fr.lordfinn.craftmaster.portrait.PortraitAnimation;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.PortraitGeometry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a hanging portrait: a brass hook under the ceiling, the string, the golden frame and the player model
 * posed in it, plus a label (rank, name, published points) under it. Everything moves from the synced data:
 * the slow sway is computed here from the game time (each portrait has its own period and phase), and so is the
 * column change of a rearrangement ({@link PortraitAnimation}).
 * <p>
 * The figure's parts are drawn one by one with the transforms of {@link PortraitGeometry#partMatrix}, the same
 * ones the hang point is computed from: the string always ends right above the centre of gravity.
 */
public class PortraitRenderer extends EntityRenderer<PortraitEntity, PortraitRenderer.State> {

    public static final Identifier FRAME_TEXTURE = CraftMaster.id("textures/entity/portrait_frame.png");
    /** Painted velvet backdrop seen through the opening with the window look. */
    public static final Identifier BACKDROP_TEXTURE = CraftMaster.id("textures/entity/portrait_backdrop.png");
    private static final int TEX_W = 128, TEX_H = 64;

    /** Sway: a few degrees, one period of 8 to 12 seconds. */
    private static final float SWAY_DEGREES = 2.5f;

    public static class State extends EntityRenderState {
        Pose pose = Pose.NEUTRAL;
        PortraitSkins.Skin skin;
        /** From the entity's drawn position to where the hook is right now (moves during a rearrangement). */
        Vec3 hookOffset = Vec3.ZERO;
        float yaw;
        float sway;
        /** Light where the runner hangs (the air under the ceiling block), not the frame's: lit like the ceiling. */
        int hookLight;
        double hangLength;
        boolean window;
        int windowDepth, windowSize;
        boolean highlighted;
        float highlightPulse;
        List<Component> label = List.of();

        // Pose editor preview (drawn in a screen, see PoseEditorScreen): portrait space only, nothing cut
        boolean preview;
        /** Parts drawn tinted: the one being edited and the one under the mouse (null = none; frame: see below). */
        Part selected, hovered;
        boolean frameSelected, frameHovered;
        /** Portrait space → preview view space (the editor's turntable). */
        Matrix4f view = new Matrix4f();

        // Title screen (client.home): the painting alone (frame, canvas, cut figure) at the pose stack's origin,
        // portrait space in blocks, no hook, string or label; tinted (multiply) to fade it into the background
        boolean menu;
        int tint = 0xFFFFFFFF;
    }

    /** A render state for the pose editor's preview of {@code pose} with {@code skin}. */
    static State previewState(Pose pose, PortraitSkins.Skin skin) {
        State s = new State();
        s.entityType = fr.lordfinn.craftmaster.registry.ModContent.PORTRAIT;
        s.preview = true;
        s.pose = pose;
        s.skin = skin;
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        return s;
    }

    /**
     * A render state for the title screen: the painting of {@code pose} worn by {@code skin}, drawn exactly like a
     * hanging portrait (same frame, canvas cut and figure) but without its hook, string and label, centred on the
     * pose stack's origin (portrait space, in blocks) and multiplied by {@code tint}.
     */
    public static State menuState(Pose pose, PortraitSkins.Skin skin, int tint) {
        State s = new State();
        s.entityType = fr.lordfinn.craftmaster.registry.ModContent.PORTRAIT;
        s.menu = true;
        s.pose = pose;
        s.skin = skin;
        s.tint = tint;
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        return s;
    }

    /** Tints of the pose editor's preview: the part being edited, the part under the mouse. */
    private static final int SELECTED_TINT = 0xFFFFE48C, HOVER_TINT = 0xFFC4E2FF;

    private final Map<Part, ModelPart> wide = new EnumMap<>(Part.class);
    private final Map<Part, ModelPart> slim = new EnumMap<>(Part.class);
    private final ModelPart mouldings, canvas;
    /** The vanilla cape, worn on the body when the shown player has one. */
    private final ModelPart cape;

    public PortraitRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        collectParts(babyPlayer(false), wide);
        collectParts(babyPlayer(true), slim);
        mouldings = buildFrame(0, 4);
        canvas = buildFrame(4, 5);
        cape = PlayerCapeModel.createCapeLayer().bakeRoot().getChild("body").getChild("cape");
        // Hangs a little away from the back, like vanilla's still cape
        cape.xRot = (float) Math.toRadians(6);
    }

    /** The player model with vanilla baby proportions (the baby zombie's transformer). */
    private static ModelPart babyPlayer(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
                .apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot();
    }

    /**
     * The player model's six parts, their own pose cleared: they are placed by PortraitGeometry only, whose
     * pivots and scales must be those vanilla's baby transformer gave them (checked here, logged if not).
     */
    private static void collectParts(ModelPart root, Map<Part, ModelPart> out) {
        out.put(Part.HEAD, root.getChild("head"));
        out.put(Part.BODY, root.getChild("body"));
        out.put(Part.RIGHT_ARM, root.getChild("right_arm"));
        out.put(Part.LEFT_ARM, root.getChild("left_arm"));
        out.put(Part.RIGHT_LEG, root.getChild("right_leg"));
        out.put(Part.LEFT_LEG, root.getChild("left_leg"));
        for (Map.Entry<Part, ModelPart> e : out.entrySet()) {
            PartPose baked = e.getValue().getInitialPose();
            org.joml.Vector3d pivot = PortraitGeometry.pivot(e.getKey());
            double scale = PortraitGeometry.partScale(e.getKey());
            if (Math.abs(baked.x() - pivot.x) > 1e-3 || Math.abs(baked.y() - pivot.y) > 1e-3 || Math.abs(baked.z() - pivot.z) > 1e-3
                    || Math.abs(baked.xScale() - scale) > 1e-3)
                CraftMaster.LOG.warn("[Portraits] Proportions bébé différentes de vanilla pour {} : {} au lieu de {} ×{}", e.getKey(), baked, pivot, scale);
            ModelPart p = e.getValue();
            p.setInitialPose(PartPose.ZERO);
            p.resetPose();
            p.getAllParts().forEach(c -> c.visible = true);
        }
    }

    /**
     * The frame's cuboids, from {@link PortraitGeometry#FRAME_BOXES} turned into model space (y down, front
     * at -z) so the box UVs of portrait_frame.png (made by scripts/gen_assets.py) land the right way up.
     */
    private static ModelPart buildFrame(int from, int to) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int[][] uv = {{0, 0}, {0, 5}, {0, 10}, {10, 10}, {20, 10}};
        for (int i = from; i < to; i++) {
            PortraitGeometry.Box b = PortraitGeometry.FRAME_BOXES.get(i);
            root.addOrReplaceChild("piece" + i, CubeListBuilder.create().texOffs(uv[i][0], uv[i][1])
                            .addBox((float) b.x0(), (float) -b.y1(), (float) -b.z1(),
                                    (float) (b.x1() - b.x0()), (float) (b.y1() - b.y0()), (float) (b.z1() - b.z0())),
                    PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, TEX_W, TEX_H).bakeRoot();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // ---------------------------------------------------------------- extract

    /** Where the hook is now: its column, or between two columns and out of the row during a rearrangement. */
    static Vec3 currentHook(PortraitEntity e, float partialTick) {
        BlockPos to = e.animTo();
        if (to.equals(BlockPos.ZERO)) return e.position();
        Vec3 target = PortraitEntity.hook(to);
        double t = PortraitClock.ticks(e.level().getGameTime()) - e.animStart();
        int slide = e.animSlide();
        if (e.animPlane() == 0 && e.animFrom().equals(to) || t > PortraitAnimation.totalTicks(slide)) return target;
        double[] p = PortraitAnimation.progress(t, slide);
        Vec3 from = PortraitEntity.hook(e.animFrom());
        Direction f = e.getFacing();
        double out = e.animPlane() * PortraitAnimation.PLANE_GAP * p[1];
        return from.lerp(target, p[0]).add(f.getStepX() * out, 0, f.getStepZ() * out);
    }

    @Override
    public void extractRenderState(PortraitEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        Pose preview = PortraitClient.previewPose(e.getId());
        s.pose = preview != null ? preview : e.pose();
        s.skin = e.anonymous() ? PortraitSkins.defaultSkin() : PortraitSkins.resolve(e.shownPlayer(), e.skinUrl());
        s.hookOffset = currentHook(e, partialTick).subtract(s.x, s.y, s.z);
        net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(currentHook(e, partialTick).add(0, -0.1, 0));
        int hb = e.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, under);
        int hs = e.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, under);
        s.hookLight = LightCoordsUtil.pack(hb, hs);
        s.yaw = e.yaw();
        s.hangLength = e.hangLengthAt(PortraitClock.ticks(e.level().getGameTime()));
        s.window = e.window();
        s.windowDepth = e.windowDepth();
        s.windowSize = e.windowSize();

        // Smooth clock (real time, every frame): no stepping at 20 Hz, no jump when the server sends the time
        double clock = PortraitClock.ticks(e.level().getGameTime());
        double seconds = clock / 20.0;
        long seed = e.getUUID().getMostSignificantBits();
        double period = 10 + 2 * PortraitEntity.variation(seed);
        double phase = Math.PI * (1 + PortraitEntity.variation(seed ^ 0x5DEECE66DL));
        s.sway = (float) (SWAY_DEGREES * Math.sin(2 * Math.PI * seconds / period + phase));

        long now = e.level().getGameTime();
        s.highlighted = now <= e.highlightUntil();
        s.highlightPulse = (float) (0.5 + 0.5 * Math.sin(clock * 0.25));
        if (s.highlighted) s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        s.label = label(e);
    }

    /** "#1 LordFinn" / "16 pts", like the busts' labels; "#1 ?" while the season is hidden. */
    private static List<Component> label(PortraitEntity e) {
        int rank = e.shownRank();
        String name = !e.displayName().isEmpty() ? e.displayName() : e.shownPlayer();
        MutableComponent first = rank > 0
                ? Component.literal("#" + rank + " ").withStyle(Busts.rankColor(rank), ChatFormatting.BOLD)
                : Component.empty();
        if (e.anonymous()) return List.of(first.append(Component.literal("?").withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (name.isEmpty()) return List.of(first.append(Component.literal("—").withStyle(ChatFormatting.DARK_GRAY)));
        first.append(Component.literal(name).withStyle(ChatFormatting.WHITE));
        if (rank <= 0) return List.of(first);
        return List.of(first, Component.literal(e.points() + " pts").withStyle(ChatFormatting.YELLOW));
    }

    /** During a rearrangement the portrait leaves its box: cull on the whole path instead. */
    @Override
    //? if >=26.3 {
    protected AABB getBoundingBoxForCulling(PortraitEntity e, float partialTick) {
    //?} else {
    /*protected AABB getBoundingBoxForCulling(PortraitEntity e) {
    *///?}
        AABB box = e.getBoundingBox();
        double t = e.level().getGameTime() - e.animStart();
        if (t < -PortraitAnimation.LEAD_TICKS * 4 || t > PortraitAnimation.totalTicks(e.animSlide()) + 2) return box;
        Vec3 back = PortraitEntity.hook(e.animFrom()).subtract(PortraitEntity.hook(e.animTo()));
        return box.minmax(box.move(back)).inflate(Math.abs(e.animPlane()) * PortraitAnimation.PLANE_GAP + 0.5);
    }

    // ---------------------------------------------------------------- submit

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.preview) {
            submitPreview(s, poseStack, collector);
            return;
        }
        if (s.menu) {
            poseStack.pushPose();
            poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
            submitPainting(s, s.pose, false, poseStack, collector, s.lightCoords, s.tint);
            poseStack.popPose();
            return;
        }
        Pose pose = s.pose;
        boolean window = s.window;
        Vector3d origin = PortraitGeometry.portraitOrigin(pose, window, s.hangLength);
        int light = s.lightCoords;

        poseStack.pushPose();
        poseStack.translate(s.hookOffset.x, s.hookOffset.y, s.hookOffset.z);
        // Portrait axes: x to the viewer's right, y up, z toward the viewer; the sway turns around the string
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw + s.sway));

        // Hook and string, in blocks: one straight vertical line from the runner down to the knot, which
        // portraitOrigin puts right under the hook (and the centre of gravity right under the knot)
        float knotY = (float) -s.hangLength;
        // The runner takes the light under the ceiling; the string the brighter of that and the frame's
        int hookLight = s.highlighted ? light : s.hookLight;
        int stringLight = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), LightCoordsUtil.block(hookLight)),
                Math.max(LightCoordsUtil.sky(light), LightCoordsUtil.sky(hookLight)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME_TEXTURE), (p, c) -> {
            hook(c, p, hookLight);
            string(c, p, knotY, stringLight);
        });

        poseStack.translate(origin.x, origin.y, origin.z);
        poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f frameToCamera = submitPainting(s, pose, window, poseStack, collector, light, 0xFFFFFFFF);

        // The label is a plaque on the front: nothing of it shows from behind
        if (PortraitClipper.cameraInFront(frameToCamera, (float) (PortraitGeometry.DEPTH / 2 + 0.6))) submitLabel(s, pose, poseStack, collector);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }

    /**
     * The painting, portrait space in pixels at the pose stack's origin: the frame (and the highlight, the window's
     * room), then the figure cut by the canvas or seen through the window. Shared by the hanging portraits and the
     * title screen. Returns frame space → camera space (for the label).
     */
    private Matrix4f submitPainting(State s, Pose pose, boolean window, PoseStack poseStack, SubmitNodeCollector collector,
                                    int light, int tint) {
        // Frame: portrait space -> frame space (tilt), then model space (half turn around X), parts in px/16
        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f(PortraitGeometry.frameMatrix(pose)));
        // Frame space -> camera space, for the cut and the window (see PortraitClipper)
        Matrix4f frameToCamera = new Matrix4f(poseStack.last().pose());
        if (s.highlighted) {
            float pulse = s.highlightPulse;
            float haloZ = (float) -PortraitGeometry.HD - 0.2f;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(FRAME_TEXTURE), (p, c) -> halo(c, p, pulse, haloZ));
        }
        float canvasFront = (float) PortraitGeometry.CANVAS_FRONT;
        boolean seeInside = window && PortraitClipper.cameraInFront(frameToCamera, canvasFront);
        if (window) {
            // The canvas reduced to a thin plate at the very back of the frame: white seen from behind
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME_TEXTURE), (p, c) -> backPlate(c, p, light));
        }
        if (seeInside) {
            // The room behind the opening (velvet back and walls), flattened onto the canvas plane like the figure:
            // only ever seen through the opening. Deep enough for the figure in it.
            float depth = (float) Math.max(s.windowDepth, -(PortraitGeometry.figureBackZ(pose, true) - canvasFront) + 1);
            float scale = s.windowSize / 100f;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(BACKDROP_TEXTURE), (p, c) -> {
                PortraitClipper clip = PortraitClipper.portal(c, frameToCamera, canvasFront - 0.02f,
                        (float) PortraitGeometry.OPEN_HW, (float) PortraitGeometry.OPEN_HH);
                roomBack(clip, p, depth, scale, light);
                roomWalls(clip, p, depth, scale, light);
                clip.flush();
            });
        }
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotation((float) Math.PI));
        poseStack.scale(16, 16, 16);
        submitPart(mouldings, poseStack, collector, RenderTypes.entityCutout(FRAME_TEXTURE), light, tint, null);
        if (!window) submitPart(canvas, poseStack, collector, RenderTypes.entityCutout(FRAME_TEXTURE), light, tint, null);
        poseStack.popPose();

        // Figure (baby proportions, see PortraitGeometry): cut by the canvas, or seen through the window
        PortraitSkins.Skin skin = s.skin != null ? s.skin : PortraitSkins.defaultSkin();
        Map<Part, ModelPart> parts = skin.slim() ? slim : wide;
        // Cutout (alpha-tested, both faces): no sorting artifacts on the hat / jacket layers seen from any side
        RenderType figure = RenderTypes.entityCutout(skin.texture());
        float cutZ = (float) PortraitGeometry.cutPlane(pose);
        boolean keepFront = !PortraitGeometry.behindCanvas(pose);
        java.util.function.Function<VertexConsumer, PortraitClipper> clipper = window
                ? c -> PortraitClipper.portal(c, frameToCamera, canvasFront - 0.02f, (float) PortraitGeometry.OPEN_HW, (float) PortraitGeometry.OPEN_HH)
                : c -> PortraitClipper.cut(c, frameToCamera, cutZ, keepFront, (float) PortraitGeometry.HW, (float) PortraitGeometry.HH);
        if (!window || seeInside) {
            for (Part part : PortraitGeometry.BODY_PARTS) {
                if (pose.isHidden(part)) continue;
                poseStack.pushPose();
                poseStack.mulPose(new Matrix4f(PortraitGeometry.partMatrix(pose, part, window)));
                poseStack.scale(16, 16, 16);
                submitPart(parts.get(part), poseStack, collector, figure, light, tint, clipper);
                poseStack.popPose();
            }
            if (skin.cape() != null && !pose.isHidden(Part.BODY)) {
                poseStack.pushPose();
                poseStack.mulPose(new Matrix4f(PortraitGeometry.partMatrix(pose, Part.BODY, window)));
                poseStack.scale(16, 16, 16);
                submitPart(cape, poseStack, collector, RenderTypes.entityCutout(skin.cape()), light, tint, clipper);
                poseStack.popPose();
            }
        }
        return frameToCamera;
    }

    /**
     * The pose editor's preview: the frame and the whole figure in portrait space (nothing cut, so a part hidden
     * in the painting can still be seen and grabbed), turned by the editor's view, the edited part tinted.
     */
    private void submitPreview(State s, PoseStack poseStack, SubmitNodeCollector collector) {
        Pose pose = s.pose;
        int light = LightCoordsUtil.FULL_BRIGHT;
        poseStack.pushPose();
        poseStack.mulPose(s.view);
        poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);

        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f(PortraitGeometry.frameMatrix(pose)));
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotation((float) Math.PI));
        poseStack.scale(16, 16, 16);
        int frameTint = s.frameSelected ? SELECTED_TINT : s.frameHovered ? HOVER_TINT : 0xFFFFFFFF;
        submitTinted(mouldings, poseStack, collector, RenderTypes.entityCutout(FRAME_TEXTURE), light, frameTint);
        submitTinted(canvas, poseStack, collector, RenderTypes.entityCutout(FRAME_TEXTURE), light, frameTint);
        poseStack.popPose();

        PortraitSkins.Skin skin = s.skin != null ? s.skin : PortraitSkins.defaultSkin();
        Map<Part, ModelPart> parts = skin.slim() ? slim : wide;
        RenderType figure = RenderTypes.entityCutout(skin.texture());
        for (Part part : PortraitGeometry.BODY_PARTS) {
            if (pose.isHidden(part)) continue;
            poseStack.pushPose();
            poseStack.mulPose(new Matrix4f(PortraitGeometry.partMatrix(pose, part)));
            poseStack.scale(16, 16, 16);
            int tint = part == s.selected ? SELECTED_TINT : part == s.hovered ? HOVER_TINT : 0xFFFFFFFF;
            submitTinted(parts.get(part), poseStack, collector, figure, light, tint);
            if (part == Part.BODY && skin.cape() != null)
                submitTinted(cape, poseStack, collector, RenderTypes.entityCutout(skin.cape()), light, tint);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void submitTinted(ModelPart part, PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType type,
                                     int light, int tint) {
        collector.submitCustomGeometry(poseStack, type, (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            part.render(ps, c, light, OverlayTexture.NO_OVERLAY, tint);
        });
    }

    /** Model parts are drawn when the frame is rendered; nothing shared changes in between (their pose is fixed). */
    private static void submitPart(ModelPart part, PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType type, int light,
                                   int tint, java.util.function.Function<VertexConsumer, PortraitClipper> clipper) {
        collector.submitCustomGeometry(poseStack, type, (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            if (clipper == null) {
                part.render(ps, c, light, OverlayTexture.NO_OVERLAY, tint);
            } else {
                PortraitClipper clip = clipper.apply(c);
                part.render(ps, clip, light, OverlayTexture.NO_OVERLAY, tint);
                clip.flush();
            }
        });
    }

    /** Label under the lowest point of the portrait, in front of the frame, facing the audience. */
    private void submitLabel(State s, Pose pose, PoseStack poseStack, SubmitNodeCollector collector) {
        if (s.label.isEmpty()) return;
        double[] b = PortraitGeometry.bounds(pose, s.window);
        Font font = getFont();
        poseStack.pushPose();
        poseStack.translate(0, b[1] - 1.5, PortraitGeometry.DEPTH / 2 + 0.6);
        float scale = 0.32f;
        poseStack.scale(scale, -scale, scale);
        int lineH = font.lineHeight + 1;
        int light = LightCoordsUtil.FULL_BRIGHT;
        int width = 0;
        for (Component line : s.label) width = Math.max(width, font.width(line));
        ClientCompat.textBackground(collector, poseStack, -width / 2f - 2, -1, width / 2f + 2, s.label.size() * lineH, 0x80000000,
                Font.DisplayMode.NORMAL, light);
        for (int i = 0; i < s.label.size(); i++) {
            Component line = s.label.get(i);
            collector.submitText(poseStack, -font.width(line) / 2f, i * lineH, line.getVisualOrderText(), false,
                    Font.DisplayMode.POLYGON_OFFSET, light, 0xFFFFFFFF, ClientCompat.lineBackground(0x80000000), 0);
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- custom geometry (blocks / pixels)

    private static void vertex(VertexConsumer c, PoseStack.Pose p, float x, float y, float z, float u, float v, int color,
                               int light, float nx, float ny, float nz) {
        c.addVertex(p, x, y, z).setColor(color).setUv(u / TEX_W, v / TEX_H).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }

    /** Axis-aligned box, every face sampling the same texture square (u0, v0)–(u1, v1). */
    private static void box(VertexConsumer c, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            float u0, float v0, float u1, float v1, int light) {
        int w = 0xFFFFFFFF;
        // -y, +y
        vertex(c, p, x0, y0, z0, u0, v0, w, light, 0, -1, 0); vertex(c, p, x1, y0, z0, u1, v0, w, light, 0, -1, 0);
        vertex(c, p, x1, y0, z1, u1, v1, w, light, 0, -1, 0); vertex(c, p, x0, y0, z1, u0, v1, w, light, 0, -1, 0);
        vertex(c, p, x0, y1, z1, u0, v0, w, light, 0, 1, 0); vertex(c, p, x1, y1, z1, u1, v0, w, light, 0, 1, 0);
        vertex(c, p, x1, y1, z0, u1, v1, w, light, 0, 1, 0); vertex(c, p, x0, y1, z0, u0, v1, w, light, 0, 1, 0);
        // -z, +z
        vertex(c, p, x0, y1, z0, u0, v0, w, light, 0, 0, -1); vertex(c, p, x1, y1, z0, u1, v0, w, light, 0, 0, -1);
        vertex(c, p, x1, y0, z0, u1, v1, w, light, 0, 0, -1); vertex(c, p, x0, y0, z0, u0, v1, w, light, 0, 0, -1);
        vertex(c, p, x1, y1, z1, u0, v0, w, light, 0, 0, 1); vertex(c, p, x0, y1, z1, u1, v0, w, light, 0, 0, 1);
        vertex(c, p, x0, y0, z1, u1, v1, w, light, 0, 0, 1); vertex(c, p, x1, y0, z1, u0, v1, w, light, 0, 0, 1);
        // -x, +x
        vertex(c, p, x0, y1, z1, u0, v0, w, light, -1, 0, 0); vertex(c, p, x0, y1, z0, u1, v0, w, light, -1, 0, 0);
        vertex(c, p, x0, y0, z0, u1, v1, w, light, -1, 0, 0); vertex(c, p, x0, y0, z1, u0, v1, w, light, -1, 0, 0);
        vertex(c, p, x1, y1, z0, u0, v0, w, light, 1, 0, 0); vertex(c, p, x1, y1, z1, u1, v0, w, light, 1, 0, 0);
        vertex(c, p, x1, y0, z1, u1, v1, w, light, 1, 0, 0); vertex(c, p, x1, y0, z0, u0, v1, w, light, 1, 0, 0);
    }

    /**
     * The brass runner on the rail, 4 × 2 × 4 px, under the ceiling: one texel per pixel on every face (the sides
     * take a 4 × 2 strip of the brass, not the whole 4 × 4 squeezed).
     */
    private static void hook(VertexConsumer c, PoseStack.Pose p, int light) {
        float h = 2 / 16f, x0 = -h, y0 = -h, z0 = -h, x1 = h, y1 = 0, z1 = h;
        int w = 0xFFFFFFFF;
        float u0 = 112, u1 = 116, v0 = 0, v1 = 4, vs = 2;
        // -y (seen from below), +y
        vertex(c, p, x0, y0, z0, u0, v0, w, light, 0, -1, 0); vertex(c, p, x1, y0, z0, u1, v0, w, light, 0, -1, 0);
        vertex(c, p, x1, y0, z1, u1, v1, w, light, 0, -1, 0); vertex(c, p, x0, y0, z1, u0, v1, w, light, 0, -1, 0);
        vertex(c, p, x0, y1, z1, u0, v0, w, light, 0, 1, 0); vertex(c, p, x1, y1, z1, u1, v0, w, light, 0, 1, 0);
        vertex(c, p, x1, y1, z0, u1, v1, w, light, 0, 1, 0); vertex(c, p, x0, y1, z0, u0, v1, w, light, 0, 1, 0);
        // the four sides: 4 px wide, 2 px tall
        vertex(c, p, x0, y1, z0, u0, v0, w, light, 0, 0, -1); vertex(c, p, x1, y1, z0, u1, v0, w, light, 0, 0, -1);
        vertex(c, p, x1, y0, z0, u1, vs, w, light, 0, 0, -1); vertex(c, p, x0, y0, z0, u0, vs, w, light, 0, 0, -1);
        vertex(c, p, x1, y1, z1, u0, v0, w, light, 0, 0, 1); vertex(c, p, x0, y1, z1, u1, v0, w, light, 0, 0, 1);
        vertex(c, p, x0, y0, z1, u1, vs, w, light, 0, 0, 1); vertex(c, p, x1, y0, z1, u0, vs, w, light, 0, 0, 1);
        vertex(c, p, x0, y1, z1, u0, v0, w, light, -1, 0, 0); vertex(c, p, x0, y1, z0, u1, v0, w, light, -1, 0, 0);
        vertex(c, p, x0, y0, z0, u1, vs, w, light, -1, 0, 0); vertex(c, p, x0, y0, z1, u0, vs, w, light, -1, 0, 0);
        vertex(c, p, x1, y1, z0, u0, v0, w, light, 1, 0, 0); vertex(c, p, x1, y1, z1, u1, v0, w, light, 1, 0, 0);
        vertex(c, p, x1, y0, z1, u1, vs, w, light, 1, 0, 0); vertex(c, p, x1, y0, z0, u0, vs, w, light, 1, 0, 0);
    }

    /** Two crossed strips, half a pixel wide, straight down from the runner to the knot (x = z = 0). */
    private static void string(VertexConsumer c, PoseStack.Pose p, float knotY, int light) {
        float top = -2 / 16f, w = 0.25f / 16f;
        int col = 0xFFFFFFFF;
        float u0 = 120, u1 = 121, v0 = 0, v1 = 16;
        for (int side = 0; side < 2; side++) {
            float dx = side == 0 ? w : 0, dz = side == 0 ? 0 : w;
            float nx = dz / w, nz = dx / w;
            // Both faces, so the string shows from every side
            vertex(c, p, -dx, top, -dz, u0, v0, col, light, nx, 0, nz);
            vertex(c, p, dx, top, dz, u1, v0, col, light, nx, 0, nz);
            vertex(c, p, dx, knotY, dz, u1, v1, col, light, nx, 0, nz);
            vertex(c, p, -dx, knotY, -dz, u0, v1, col, light, nx, 0, nz);
            vertex(c, p, -dx, knotY, -dz, u0, v1, col, light, -nx, 0, -nz);
            vertex(c, p, dx, knotY, dz, u1, v1, col, light, -nx, 0, -nz);
            vertex(c, p, dx, top, dz, u1, v0, col, light, -nx, 0, -nz);
            vertex(c, p, -dx, top, -dz, u0, v0, col, light, -nx, 0, -nz);
        }
    }

    /** Window look: the thin plate closing the frame at the back (white behind, velvet in front). */
    private static void backPlate(VertexConsumer c, PoseStack.Pose p, int light) {
        float hw = (float) PortraitGeometry.OPEN_HW, hh = (float) PortraitGeometry.OPEN_HH;
        float back = (float) PortraitGeometry.CANVAS_BACK, front = back + 0.1f;
        int w = 0xFFFFFFFF;
        quad(c, p, hw, hh, back, -hw, hh, back, -hw, -hh, back, hw, -hh, back, 64, 0, 68, 4, w, light, 0, 0, -1);
        quad(c, p, -hw, hh, front, hw, hh, front, hw, -hh, front, -hw, -hh, front, 21, 11, 35, 31, w, light, 0, 0, 1);
    }

    /**
     * Window look: the room behind the opening (pixels, frame space): {@code scale} times the opening (so moving
     * around reveals more of it), {@code depth} px deep. Back, sides, floor and ceiling all in the same velvet, at
     * one texel per frame pixel like the game; the walls continue the back's texture at its edges.
     */
    private static void roomBack(VertexConsumer c, PoseStack.Pose p, float depth, float scale, int light) {
        float hw = (float) PortraitGeometry.OPEN_HW * scale, hh = (float) PortraitGeometry.OPEN_HH * scale;
        float z1 = (float) PortraitGeometry.CANVAS_FRONT - depth;
        tiled(c, p, -hw, hh, z1, 1, 0, 0, 2 * hw, BACK_U - hw, 0, -1, 0, 2 * hh, BACK_V - hh, 0xFFFFFFFF, light, 0, 0, 1);
    }

    private static void roomWalls(VertexConsumer c, PoseStack.Pose p, float depth, float scale, int light) {
        float hw = (float) PortraitGeometry.OPEN_HW * scale, hh = (float) PortraitGeometry.OPEN_HH * scale;
        float z0 = (float) PortraitGeometry.CANVAS_FRONT - 0.05f, z1 = (float) PortraitGeometry.CANVAS_FRONT - depth;
        int side = 0xFFC8B4B4, top = 0xFF8C7878, bottom = 0xFFE0CCCC;
        float dz = z0 - z1;
        // Sides: same rows as the back, the columns going on past its edge toward the viewer
        tiled(c, p, -hw, hh, z0, 0, 0, -1, dz, BACK_U - hw - dz, 0, -1, 0, 2 * hh, BACK_V - hh, side, light, 1, 0, 0);
        tiled(c, p, hw, hh, z1, 0, 0, 1, dz, BACK_U + hw, 0, -1, 0, 2 * hh, BACK_V - hh, side, light, -1, 0, 0);
        // Ceiling and floor: same columns as the back, the rows going on past its top / bottom edge
        tiled(c, p, -hw, hh, z0, 1, 0, 0, 2 * hw, BACK_U - hw, 0, 0, -1, dz, BACK_V - hh - dz, top, light, 0, -1, 0);
        tiled(c, p, -hw, -hh, z1, 1, 0, 0, 2 * hw, BACK_U - hw, 0, 0, 1, dz, BACK_V + hh, bottom, light, 0, 1, 0);
    }

    /** The velvet texture's size (texels) and the texel at the middle of the back. */
    private static final float BACKDROP_SIZE = 64, BACK_U = 32, BACK_V = 22;

    /**
     * A wall from corner (ox, oy, oz) along unit directions u (lenU px) and v (lenV px) at one texel per pixel, the
     * corner showing texel (tu, tv) of the velvet; the texture repeats (pieces cut at its edges, never stretched).
     */
    private static void tiled(VertexConsumer c, PoseStack.Pose p, float ox, float oy, float oz,
                              float ux, float uy, float uz, float lenU, float tu, float vx, float vy, float vz, float lenV, float tv,
                              int color, int light, float nx, float ny, float nz) {
        // The vertex helper divides u by TEX_W and v by TEX_H (the frame texture's size)
        float su = TEX_W / BACKDROP_SIZE, sv = TEX_H / BACKDROP_SIZE;
        for (float a = 0; a < lenU - 1e-4f; ) {
            float ta = Math.floorMod((int) Math.floor(tu + a + 1e-4f), (int) BACKDROP_SIZE) + (tu + a - (float) Math.floor(tu + a + 1e-4f));
            float a1 = Math.min(lenU, a + (BACKDROP_SIZE - ta));
            for (float b = 0; b < lenV - 1e-4f; ) {
                float tb = Math.floorMod((int) Math.floor(tv + b + 1e-4f), (int) BACKDROP_SIZE) + (tv + b - (float) Math.floor(tv + b + 1e-4f));
                float b1 = Math.min(lenV, b + (BACKDROP_SIZE - tb));
                float x0 = ox + ux * a + vx * b, y0 = oy + uy * a + vy * b, z0 = oz + uz * a + vz * b;
                float x1 = ox + ux * a1 + vx * b, y1 = oy + uy * a1 + vy * b, z1 = oz + uz * a1 + vz * b;
                float x2 = ox + ux * a1 + vx * b1, y2 = oy + uy * a1 + vy * b1, z2 = oz + uz * a1 + vz * b1;
                float x3 = ox + ux * a + vx * b1, y3 = oy + uy * a + vy * b1, z3 = oz + uz * a + vz * b1;
                quad(c, p, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                        ta * su, tb * sv, (ta + a1 - a) * su, (tb + b1 - b) * sv, color, light, nx, ny, nz);
                b = b1;
            }
            a = a1;
        }
    }

    private static void quad(VertexConsumer c, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int color, int light, float nx, float ny, float nz) {
        vertex(c, p, ax, ay, az, u0, v0, color, light, nx, ny, nz);
        vertex(c, p, bx, by, bz, u1, v0, color, light, nx, ny, nz);
        vertex(c, p, cx, cy, cz, u1, v1, color, light, nx, ny, nz);
        vertex(c, p, dx, dy, dz, u0, v1, color, light, nx, ny, nz);
    }

    /** Golden glow behind the frame while it is highlighted (pixels, frame space). */
    private static void halo(VertexConsumer c, PoseStack.Pose p, float pulse, float z) {
        float hw = (float) PortraitGeometry.FRAME_W / 2 + 7, hh = (float) PortraitGeometry.FRAME_H / 2 + 7;
        int alpha = (int) (110 + 120 * pulse);
        int color = (alpha << 24) | 0xFFD040;
        int light = LightCoordsUtil.FULL_BRIGHT;
        float u0 = 96, v0 = 32, u1 = 128, v1 = 64;
        vertex(c, p, -hw, hh, z, u0, v0, color, light, 0, 0, 1);
        vertex(c, p, -hw, -hh, z, u0, v1, color, light, 0, 0, 1);
        vertex(c, p, hw, -hh, z, u1, v1, color, light, 0, 0, 1);
        vertex(c, p, hw, hh, z, u1, v0, color, light, 0, 0, 1);
    }
}
