package fr.lordfinn.craftmaster.client.studio;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.studio.SpotlightAim;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A stage spotlight: the yoke turns around the vertical axis, the head tilts in it, both easing smoothly toward the
 * aim (a followed player is tracked here, every frame). When lit: the lens glows, a soft cone of light (additive,
 * fading with distance) goes to the first block on its way, and a pool of light is drawn where it lands.
 */
public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity, SpotlightRenderer.State> {

    public static final Identifier TEXTURE = CraftMaster.id("textures/entity/spotlight.png");
    private static final Geo.Tex TEX = new Geo.Tex(32, 32);
    /** Radius of the beam at the lens (blocks); a narrow beam never gets wider than MAX_RADIUS times its cone. */
    private static final double LENS_RADIUS = 3.2 / 16, MAX_RADIUS = 1.1;
    /** A beam that hits nothing fades out over this length (blocks). */
    private static final double OPEN_LENGTH = 26;
    private static final int SIDES = 20;

    public static class State extends BlockEntityRenderState {
        float yaw, pitch;
        boolean hanging, lit;
        int color;
        /** 0..1 */
        float intensity;
        /** Half-angle, degrees. */
        float cone;
        double length;
        /** Where the beam lands, relative to the block's corner, and the face it lands on (null: nothing hit). */
        Vec3 hit;
        Direction face;
        Vec3 pivot;
    }

    public SpotlightRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpotlightBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, crumbling);
        s.hanging = be.hanging();
        s.lit = be.lit();
        Vec3 pivot = be.pivot();
        s.pivot = pivot.subtract(Vec3.atLowerCornerOf(be.getBlockPos()));

        // Aim: the followed player as seen right now, else the set direction
        float targetYaw = be.yaw(), targetPitch = be.pitch();
        if (!be.target().isEmpty() && be.getLevel() != null) {
            for (Player p : be.getLevel().players()) {
                if (!p.getGameProfile().name().equalsIgnoreCase(be.target())) continue;
                Vec3 at = SpotlightBlockEntity.followPoint(p.getPosition(partialTick), p.getBbHeight()).subtract(pivot);
                if (at.length() < SpotlightBlockEntity.FOLLOW_RANGE) {
                    float[] a = SpotlightAim.angles(at.x, at.y, at.z);
                    targetYaw = a[0];
                    targetPitch = a[1];
                }
                break;
            }
        }
        long now = System.nanoTime();
        // Switched off: the light fades to nothing (at the fade's pace), then the beam is gone
        float targetIntensity = be.lit() ? be.intensity() / (float) SpotlightBlockEntity.MAX_INTENSITY : 0;
        float[] targetColor = rgb(be.color());
        if (!be.renderReady) {
            be.renderYaw = targetYaw;
            be.renderPitch = targetPitch;
            be.renderIntensity = targetIntensity;
            be.renderCone = be.cone();
            be.renderColor = targetColor;
            be.renderReady = true;
        } else {
            double dt = Math.min(0.25, (now - be.renderNanos) / 1e9);
            double fade = be.fade() / 20.0;
            if (fade <= 0) {
                // Fast enough to keep up with a walking player, slow enough to look like a motorised light
                be.renderYaw = SpotlightAim.ease(be.renderYaw, targetYaw, dt, 4.0, 25);
                be.renderPitch = SpotlightAim.ease(be.renderPitch, targetPitch, dt, 4.0, 25);
            } else {
                // A slow move set by the light board: the whole way in about the fade time
                double rate = 3.0 / fade;
                be.renderYaw = SpotlightAim.ease(be.renderYaw, targetYaw, dt, rate, 0);
                be.renderPitch = SpotlightAim.ease(be.renderPitch, targetPitch, dt, rate, 0);
            }
            // Colour, intensity and cone: linear over the fade (a quarter of a second without one)
            double step = dt / Math.max(0.25, fade);
            be.renderIntensity = (float) approach(be.renderIntensity, targetIntensity, step);
            be.renderCone = (float) approach(be.renderCone, be.cone(), step * Math.max(1, Math.abs(be.cone() - be.renderCone)));
            for (int i = 0; i < 3; i++) be.renderColor[i] = (float) approach(be.renderColor[i], targetColor[i], step);
        }
        be.renderNanos = now;
        s.yaw = be.renderYaw;
        s.pitch = be.renderPitch;
        // Light effects (breath, pulse, strobe…) on top, in step on every client (world time)
        double seconds = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0;
        s.intensity = be.renderIntensity * fr.lordfinn.craftmaster.studio.SpotlightEffects.factor(be.effect(), be.effectSpeed(), be.effectPhase(), seconds);
        s.cone = be.renderCone;
        s.color = ((int) (be.renderColor[0] * 255) << 16) | ((int) (be.renderColor[1] * 255) << 8) | (int) (be.renderColor[2] * 255);
        s.lit = s.intensity > 0.005f;

        s.hit = null;
        s.face = null;
        s.length = OPEN_LENGTH;
        if (s.lit && be.getLevel() != null) {
            BlockHitResult hit = SpotlightBlockEntity.trace(be.getLevel(), pivot, s.yaw, s.pitch);
            if (hit.getType() == HitResult.Type.BLOCK) {
                s.length = hit.getLocation().distanceTo(pivot);
                s.hit = hit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                s.face = hit.getDirection();
            }
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        poseStack.pushPose();
        poseStack.translate(s.pivot.x, s.pivot.y, s.pivot.z);
        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.YP.rotationDegrees(-s.yaw));

        // Yoke (pixels): a bar under (or over) the head and two arms holding it at the pivot
        float sign = s.hanging ? -1 : 1;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
            PoseStack.Pose q = ps.last();
            float[][] metal = Geo.all(24, 0, 26, 8);
            float barY0 = sign > 0 ? -6 : 5, barY1 = barY0 + 1;
            Geo.box(c, q, TEX, -5.5f, barY0, -1, 5.5f, barY1, 1, metal, 0xFFFFFFFF, light);
            float armY0 = sign > 0 ? -5 : -1, armY1 = sign > 0 ? 1 : 5;
            Geo.box(c, q, TEX, -5.5f, armY0, -1, -4.5f, armY1, 1, metal, 0xFFFFFFFF, light);
            Geo.box(c, q, TEX, 4.5f, armY0, -1, 5.5f, armY1, 1, metal, 0xFFFFFFFF, light);
            // Swivel between the bar and the block
            float swY0 = sign > 0 ? -7 : 6, swY1 = swY0 + 1;
            Geo.box(c, q, TEX, -1, swY0, -1, 1, swY1, 1, metal, 0xFFFFFFFF, light);
        });

        poseStack./*$ pose_rotate {*/rotate/*$}*/(Axis.XP.rotationDegrees(s.pitch));
        // Head: a can 8 × 8 × 10 px, lens at +z, vents at the back
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, c) -> {
            PoseStack ps = new PoseStack();
            ps.last().set(p);
            ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
            float[] side = {0, 8, 10, 16}, top = {0, 16, 8, 26}, back = {16, 0, 24, 8}, front = {8, 0, 16, 8};
            Geo.box(c, ps.last(), TEX, -4, -4, -5, 4, 4, 5, new float[][]{back, front, side, side, top, top}, 0xFFFFFFFF, light);
            // Rim around the lens (a short hood)
            float[][] hood = Geo.all(24, 0, 26, 8);
            Geo.box(c, ps.last(), TEX, -4.5f, 3.5f, 4, 4.5f, 4.5f, 6.5f, hood, 0xFFFFFFFF, light);
        });
        if (s.lit) {
            // Glowing lens, full bright (dimmed with the intensity)
            int lens = 0xFF000000 | scale(s.color, 0.25f + 0.75f * s.intensity);
            collector.submitCustomGeometry(poseStack, RenderTypes.text(TEXTURE), (p, c) -> {
                float z = 5.02f / 16, r = 3.2f / 16;
                int full = LightCoordsUtil.FULL_BRIGHT;
                float u0 = 0, v0 = 0, u1 = 8 / 32f, v1 = 8 / 32f;
                c.addVertex(p, -r, r, z).setColor(lens).setUv(u0, v0).setLight(full);
                c.addVertex(p, -r, -r, z).setColor(lens).setUv(u0, v1).setLight(full);
                c.addVertex(p, r, -r, z).setColor(lens).setUv(u1, v1).setLight(full);
                c.addVertex(p, r, r, z).setColor(lens).setUv(u1, v0).setLight(full);
            });
            double length = s.length;
            int color = s.color;
            float intensity = s.intensity, cone = s.cone;
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (p, c) -> beam(c, p, length, color, intensity, cone));
        }
        poseStack.popPose();

        if (s.lit && s.hit != null) {
            Vec3 hit = s.hit;
            Direction face = s.face;
            double radius = endRadius(s.length, s.cone);
            int color = s.color;
            // A wide wash spreads the same light over more ground: its pool is paler
            float strength = s.intensity * (float) Math.min(1, 1.6 / Math.max(1, radius));
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (p, c) -> pool(c, p, hit, face, radius * 1.25, color, strength));
        }
    }

    /** The cone, in head space (blocks): two layers, wide and soft, narrow and brighter, fading to nothing at the end. */
    private static void beam(VertexConsumer c, PoseStack.Pose p, double length, int rgb, float intensity, float coneAngle) {
        float z0 = 5.5f / 16, z1 = (float) length;
        float r1 = (float) endRadius(length, coneAngle);
        // The wider the cone, the thinner the haze it lights
        float spread = (float) Math.min(1, 6 / Math.max(6, coneAngle * 1.6));
        int a = Math.round(60 * intensity * spread), b = Math.round(55 * intensity * spread);
        cone(c, p, z0, z1, (float) LENS_RADIUS, r1, Geo.argb(a, rgb), Geo.argb(0, rgb));
        cone(c, p, z0, z1, (float) (LENS_RADIUS * 0.55), r1 * 0.45f, Geo.argb(b, rgb), Geo.argb(0, rgb));
    }

    /** Radius of the beam {@code length} blocks from the lens, for a cone of {@code coneAngle} degrees (half-angle). */
    private static double endRadius(double length, float coneAngle) {
        double max = MAX_RADIUS * Math.max(1, coneAngle / SpotlightBlockEntity.DEFAULT_CONE);
        return Math.min(max, LENS_RADIUS + length * Math.tan(Math.toRadians(coneAngle)));
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private static int scale(int c, float f) {
        return (Math.round(((c >> 16) & 255) * f) << 16) | (Math.round(((c >> 8) & 255) * f) << 8) | Math.round((c & 255) * f);
    }

    /** One step from {@code v} toward {@code to}, never past it. */
    private static double approach(double v, double to, double step) {
        return v < to ? Math.min(to, v + step) : Math.max(to, v - step);
    }

    private static void cone(VertexConsumer c, PoseStack.Pose p, float z0, float z1, float r0, float r1, int c0, int c1) {
        // Fade over the length, but keep a little light where it lands
        int mid = Geo.argb((c0 >>> 24) / 3, c0);
        float zm = z0 + (z1 - z0) * 0.6f, rm = r0 + (r1 - r0) * 0.6f;
        ring(c, p, z0, zm, r0, rm, c0, mid);
        ring(c, p, zm, z1, rm, r1, mid, c1);
    }

    private static void ring(VertexConsumer c, PoseStack.Pose p, float z0, float z1, float r0, float r1, int c0, int c1) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = 2 * Math.PI * i / SIDES, a1 = 2 * Math.PI * (i + 1) / SIDES;
            float x00 = (float) (Math.cos(a0) * r0), y00 = (float) (Math.sin(a0) * r0);
            float x01 = (float) (Math.cos(a1) * r0), y01 = (float) (Math.sin(a1) * r0);
            float x10 = (float) (Math.cos(a0) * r1), y10 = (float) (Math.sin(a0) * r1);
            float x11 = (float) (Math.cos(a1) * r1), y11 = (float) (Math.sin(a1) * r1);
            // Both windings: seen from outside and from inside the beam
            Geo.glow(c, p, x00, y00, z0, c0);
            Geo.glow(c, p, x10, y10, z1, c1);
            Geo.glow(c, p, x11, y11, z1, c1);
            Geo.glow(c, p, x01, y01, z0, c0);
            Geo.glow(c, p, x01, y01, z0, c0);
            Geo.glow(c, p, x11, y11, z1, c1);
            Geo.glow(c, p, x10, y10, z1, c1);
            Geo.glow(c, p, x00, y00, z0, c0);
        }
    }

    /** A soft disc of light on the face the beam lands on (block space), bright in the middle. */
    private static void pool(VertexConsumer c, PoseStack.Pose p, Vec3 hit, Direction face, double radius, int rgb, float strength) {
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 u = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 v = n.cross(u);
        u = v.cross(n);
        Vec3 center = hit.add(n.scale(0.015));
        int inner = Geo.argb(Math.round(120 * strength), rgb), outer = Geo.argb(0, rgb);
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            Vec3 e0 = center.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 e1 = center.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            Geo.glow(c, p, (float) center.x, (float) center.y, (float) center.z, inner);
            Geo.glow(c, p, (float) e0.x, (float) e0.y, (float) e0.z, outer);
            Geo.glow(c, p, (float) e1.x, (float) e1.y, (float) e1.z, outer);
            Geo.glow(c, p, (float) center.x, (float) center.y, (float) center.z, inner);
            Geo.glow(c, p, (float) center.x, (float) center.y, (float) center.z, inner);
            Geo.glow(c, p, (float) e1.x, (float) e1.y, (float) e1.z, outer);
            Geo.glow(c, p, (float) e0.x, (float) e0.y, (float) e0.z, outer);
            Geo.glow(c, p, (float) center.x, (float) center.y, (float) center.z, inner);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
