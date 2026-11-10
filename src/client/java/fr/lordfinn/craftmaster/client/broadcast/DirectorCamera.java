package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.camera.CameraMotion;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the broadcast game looks from, frame by frame: the director's shot (camera with its motion, or a follow
 * shot) and the transition between two shots. Read by the camera mixin when the broadcast mode is on.
 * <ul>
 *   <li>cut: the new shot right away;</li>
 *   <li>fade: the picture dips to black, the shot changes in the dark, the picture comes back;</li>
 *   <li>cross-fade: the last frame of the old shot is kept and dissolves over the new one ({@link Transition}).</li>
 * </ul>
 */
public final class DirectorCamera {

    /** A view with its field of view (0 = the player's setting). */
    public record Pose(Vec3 position, float yaw, float pitch, float fov) {}

    /** Half of a dip to black, and the whole cross-fade (seconds). */
    static final double FADE_HALF = 0.35, CROSSFADE = 0.9;

    /** The shot shown now (may lag behind the director's during a dip to black). */
    private static Shot shown = Shot.NONE;
    private static int seenSerial = -1;
    private static long shownSince;
    /** Dip to black in progress: when it started, and the shot waiting for the dark. */
    private static long fadeStart = -1;
    private static Shot pending;
    /** Follow shot smoothing. */
    private static Vec3 followPos;
    private static float followYaw, followPitch;
    private static long lastFrame;
    private static String followed = "";

    private DirectorCamera() {}

    static void reset() {
        shown = Shot.NONE;
        seenSerial = -1;
        fadeStart = -1;
        pending = null;
        followPos = null;
        followed = "";
    }

    /** The shot on screen now. */
    public static Shot shown() {
        return shown;
    }

    /** Real seconds since the shot on screen started. */
    public static double shotSeconds() {
        return (System.currentTimeMillis() - shownSince) / 1000.0;
    }

    /** Black over the picture during a dip to black, 0..1. */
    public static float blackAlpha() {
        if (fadeStart < 0) return 0;
        double t = (System.currentTimeMillis() - fadeStart) / 1000.0;
        if (t < FADE_HALF) return (float) smooth(t / FADE_HALF);
        if (t < 2 * FADE_HALF) return (float) smooth(2 - t / FADE_HALF);
        return 0;
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Called every frame before the view is computed: notices a new shot and runs the transition. */
    private static void update() {
        Shot target = ShowStateClient.get().shot();
        long now = System.currentTimeMillis();
        if (target.serial() != seenSerial) {
            boolean first = seenSerial < 0;
            seenSerial = target.serial();
            int transition = first ? BroadcastNetwork.CUT : target.transition();
            switch (transition) {
                case BroadcastNetwork.FADE -> {
                    fadeStart = now;
                    pending = target;
                }
                case BroadcastNetwork.CROSSFADE -> {
                    Transition.capture();
                    show(target, now);
                }
                default -> show(target, now);
            }
        } else if (target.kind() != shown.kind() || target.camera() != shown.camera() || !target.player().equals(shown.player())) {
            // Same serial, other content (state lost and resent): follow it without a transition
            if (pending == null) show(target, now);
        }
        if (pending != null && now - fadeStart >= FADE_HALF * 1000) {
            show(pending, now);
            pending = null;
        }
        if (fadeStart >= 0 && now - fadeStart > 2 * FADE_HALF * 1000) fadeStart = -1;
    }

    private static void show(Shot s, long now) {
        if (s.kind() == BroadcastNetwork.SHOT_FOLLOW && !s.player().equals(followed)) followPos = null;
        followed = s.kind() == BroadcastNetwork.SHOT_FOLLOW ? s.player() : "";
        shown = s;
        shownSince = now;
        BroadcastOverlay.onShotChanged(s);
    }

    /** The view of the shot on screen, or null to keep the player's own view. */
    public static Pose pose(float partialTick) {
        update();
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1000.0;
        lastFrame = now;
        return switch (shown.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> camera(mc, ShowStateClient.cameras().get(shown.camera()));
            case BroadcastNetwork.SHOT_FOLLOW -> follow(mc, shown.player(), partialTick, dt);
            default -> null;
        };
    }

    private static Pose camera(Minecraft mc, CameraSpot spot) {
        if (spot == null || !spot.world().equals(ShowStateClient.world(mc))) return null;
        CameraMotion.View v = CameraMotion.view(spot, shotSeconds());
        return new Pose(new Vec3(v.x(), v.y(), v.z()), v.yaw(), v.pitch(), spot.fov());
    }

    /** Front three-quarter shot on a player, smoothed, pulled in front of walls. Falls back on the wide shot. */
    private static Pose follow(Minecraft mc, String name, float partialTick, double dt) {
        Player target = null;
        if (mc.level != null) for (Player p : mc.level.players()) if (p.getGameProfile().name().equalsIgnoreCase(name)) target = p;
        if (target == null) {
            CameraSpot wide = ShowStateClient.cameras().wide();
            return wide == null ? null : camera(mc, wide);
        }
        Vec3 feet = target.getPosition(partialTick);
        float bodyYaw = target instanceof LivingEntity le ? le.yBodyRot : target.getYRot();
        double[] want = CameraMotion.followPosition(feet.x, feet.y, feet.z, bodyYaw, 3.3, 1.1, 1.85);
        Vec3 eye = feet.add(0, target.getEyeHeight() - 0.1, 0);
        Vec3 desired = new Vec3(want[0], want[1], want[2]);
        // Keep the camera on this side of the walls
        HitResult hit = mc.level.clip(new ClipContext(eye, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = hit.getLocation().subtract(eye);
            double len = back.length();
            desired = len < 0.6 ? eye.add(back.scale(0.5)) : eye.add(back.scale((len - 0.3) / len));
        }
        if (followPos == null) {
            followPos = desired;
            float[] look = CameraMotion.lookAt(desired.x, desired.y, desired.z, eye.x, eye.y, eye.z);
            followYaw = look[0];
            followPitch = look[1];
        } else {
            // Position lags a bit (operator walking), the framing follows faster
            followPos = followPos.lerp(desired, CameraMotion.smoothing(dt, 0.55));
            float[] look = CameraMotion.lookAt(followPos.x, followPos.y, followPos.z, eye.x, eye.y, eye.z);
            double k = CameraMotion.smoothing(dt, 0.18);
            followYaw = CameraMotion.lerpAngle(followYaw, look[0], k);
            followPitch = followPitch + (float) ((look[1] - followPitch) * k);
        }
        double s = shotSeconds();
        float breathYaw = (float) (0.4 * Math.sin(s * 2 * Math.PI / 9.7));
        float breathPitch = (float) (0.25 * Math.sin(s * 2 * Math.PI / 12.1 + 1));
        return new Pose(followPos, followYaw + breathYaw, followPitch + breathPitch, 0);
    }
}
