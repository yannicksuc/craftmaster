package fr.lordfinn.craftmaster.client.broadcast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.camera.CameraEntity;
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

/**
 * The studio camera on its tripod: three splayed wooden legs with brass feet, a brass head, the black camera body
 * with a brass lens, a hood and a pan bar, and the red "on air" tally light that lights up when the camera is live.
 * Its number is painted on both sides. Nine cubes, the detail is in camera.png (scripts/gen_assets.py).
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
    }

    private final ModelPart root, head, tally;

    public CameraRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        root = createLayer().bakeRoot();
        head = root.getChild("head");
        tally = head.getChild("tally");
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
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(180 - s.yaw));
        poseStack.scale(-1, -1, 1);
        poseStack.translate(0, -1.501, 0);
        head.xRot = (float) Math.toRadians(s.pitch);
        boolean onAir = s.onAir;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            tally.visible = false;
            root.render(ps, c, light, OverlayTexture.NO_OVERLAY);
            tally.visible = true;
        });
        // Tally light: full bright red when live, dim otherwise
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            ps.translate(0, 2 / 16f, 0);
            ps.rotate(Axis.XP.rotation(head.xRot));
            tally.render(ps, c, onAir ? LightCoordsUtil.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, onAir ? 0xFFFFFFFF : 0xFF603030);
        });
        submitNumber(s, poseStack, collector);
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }

    /** The camera's number on both sides of the body, like the painted numbers of studio cameras. */
    private void submitNumber(State s, PoseStack poseStack, SubmitNodeCollector collector) {
        Font font = getFont();
        Component text = Component.literal(String.valueOf(s.number)).withStyle(ChatFormatting.BOLD);
        float w = font.width(text);
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(0, 2 / 16f, 0);
            poseStack.rotate(Axis.XP.rotationDegrees(s.pitch));
            // On the side face (x = ±3 px), turned to face outward, centred on the body
            poseStack.translate(side * 3.02f / 16f, -2 / 16f, 0.5f / 16f);
            poseStack.rotate(Axis.YP.rotationDegrees(-side * 90));
            float scale = 0.45f / 16f;
            poseStack.scale(scale, scale, scale);
            collector.submitText(poseStack, -w / 2, -4, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                    LightCoordsUtil.FULL_BRIGHT, 0xFFFFE39A, 0, 0);
            if (!s.place.isEmpty()) {
                // Outside the studio: the lieu (and zone) in small letters under the number
                Component place = Component.literal(s.place);
                float pw = font.width(place);
                float k = Math.min(0.42f, 20f / Math.max(1, pw));
                poseStack.translate(0, 5.5f, 0);
                poseStack.scale(k, k, k);
                collector.submitText(poseStack, -pw / 2, 0, place.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET,
                        LightCoordsUtil.FULL_BRIGHT, 0xFFE8D9B8, 0, 0);
            }
            poseStack.popPose();
        }
    }
}
