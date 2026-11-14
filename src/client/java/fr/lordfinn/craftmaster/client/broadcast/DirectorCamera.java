package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.FreeCamera;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Shot;
import fr.lordfinn.craftmaster.camera.CameraMotion;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.fx.ScreenEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the broadcast game looks from, frame by frame: the director's shot (camera with its motion, follow shot,
 * recorded travelling, a player's eyes, the free camera, or the tiles of a split screen in turn) and the transition
 * between two shots. Read by the camera mixin when the broadcast mode is on, or during a travelling preview.
 * <ul>
 *   <li>cut: the new shot right away;</li>
 *   <li>fade: the picture dips to black, the shot changes in the dark, the picture comes back;</li>
 *   <li>cross-fade: the last frame of the old shot is kept and dissolves over the new one ({@link Transition}).</li>
 * </ul>
 * A travelling is sampled at frame rate from the real time since its shot started ({@link CameraPath#sample}): no
 * tick stepping, no jitter.
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
    /** When the pending shot goes on air (System.currentTimeMillis). */
    private static long pendingAt;
    /** Follow shot smoothing. */
    private static Vec3 followPos;
    private static float followYaw, followPitch;
    private static long lastFrame;
    private static String followed = "";
    /** Free camera: the target sent by the server, and the smoothed pose. */
    private static FreeCamera freeTarget;
    private static Vec3 freePos;
    private static float freeYaw, freePitch, freeFov;
    /** Travelling preview on an organiser's own screen: the path, when it started (ms), -1 = none. */
    private static String previewPath;
    private static long previewStart = -1;
    private static long previewMs;
    /** Split screen: frame counter for the tiles in turn. */
    private static long mosaicFrame;

    private DirectorCamera() {}

    static void reset() {
        shown = Shot.NONE;
        seenSerial = -1;
        fadeStart = -1;
        pending = null;
        followPos = null;
        followed = "";
        freeTarget = null;
        freePos = null;
        previewStart = -1;
        Mosaic.rendering = -1;
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

    /** The server moved the free camera. */
    static void freeCamera(FreeCamera f) {
        freeTarget = f;
    }

    /** Plays travelling {@code path} on this game's own screen (not the broadcast) for {@code ticks}. */
    static void preview(String path, int ticks) {
        previewPath = path;
        previewStart = System.currentTimeMillis();
        previewMs = ticks * 50L + 600;
    }

    /** Ends the travelling preview now (Shift or Esc). */
    public static void stopPreview() {
        previewStart = -1;
    }

    /** A travelling preview is playing on this screen. */
    public static boolean previewing() {
        if (previewStart < 0) return false;
        if (System.currentTimeMillis() - previewStart > previewMs) {
            previewStart = -1;
            return false;
        }
        return true;
    }

    /** "Aperçu : pont · 3,2 / 9 s" for the preview's caption, or null. */
    static String previewCaption() {
        if (!previewing()) return null;
        CameraPath p = ShowStateClient.path(previewPath);
        double t = Math.min((System.currentTimeMillis() - previewStart) / 1000.0, p == null ? 0 : p.duration());
        return "Aperçu du trajet « " + previewPath + " » · " + String.format(java.util.Locale.ROOT, "%.1f", t).replace('.', ',')
                + " / " + CameraPath.seconds(p == null ? 0 : Math.round(p.duration() * 10) / 10.0);
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
                    pendingAt = now + (long) (FADE_HALF * 1000);
                }
                case BroadcastNetwork.CROSSFADE -> {
                    Transition.capture();
                    show(target, now);
                }
                case BroadcastNetwork.DOUBLE_EXPOSURE -> {
                    Transition.captureDoubleExposure();
                    show(target, now);
                }
                case BroadcastNetwork.FLASH -> {
                    Transition.flash();
                    show(target, now);
                }
                case BroadcastNetwork.MIRROR -> {
                    // The old picture folds into the kaleidoscope, the shot changes at its peak
                    Transition.mirror();
                    pending = target;
                    pendingAt = now + (long) (Transition.MIRROR * 500);
                }
                default -> show(target, now);
            }
        } else if (pending == null && (!target.sameContent(shown) || !target.effect().equals(shown.effect()))) {
            // Same serial, other content (state lost and resent, camera moved to another lieu): follow it without a
            // transition, keeping the motion's clock when it is still the same kind of shot
            show(target, target.kind() == shown.kind() ? shownSince : now);
        }
        if (pending != null && now >= pendingAt) {
            show(pending, now);
            pending = null;
        }
        if (fadeStart >= 0 && now - fadeStart > 2 * FADE_HALF * 1000) fadeStart = -1;
    }

    private static void show(Shot s, long since) {
        if (s.kind() == BroadcastNetwork.SHOT_FOLLOW && !s.player().equals(followed)) followPos = null;
        followed = s.kind() == BroadcastNetwork.SHOT_FOLLOW ? s.player() : "";
        if (s.kind() != BroadcastNetwork.SHOT_FREE) freePos = null;
        boolean effectChanged = !s.effect().equals(shown.effect());
        shown = s;
        shownSince = since;
        if (s.kind() != BroadcastNetwork.SHOT_MOSAIC) Mosaic.rendering = -1;
        if (effectChanged || s.effect().isEmpty()) applyEffect();
        try {
            // A new shot replays the moves of its effects (focus pull), even when the effect stays the same
            ScreenEffects.restartShotEffects(s.effect());
        } catch (RuntimeException | LinkageError e) {
            // optional, as above
        }
        BroadcastOverlay.onShotChanged(s);
    }

    /** Tells the screen effects (another chantier) which effect the shot on screen asks for. */
    static void applyEffect() {
        try {
            ScreenEffects.setBroadcastEffect(Broadcast.active() && !shown.effect().isEmpty() ? shown.effect() : null);
        } catch (RuntimeException | LinkageError e) {
            // The effects are optional: a broken one never stops the broadcast
        }
    }

    /** The view of the shot on screen, or null to keep the player's own view. */
    public static Pose pose(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1000.0;
        lastFrame = now;
        if (!Broadcast.active()) {
            // Only a travelling preview on an organiser's screen
            if (!previewing()) return null;
            CameraPath p = ShowStateClient.path(previewPath);
            return p == null ? null : path(mc, p, (now - previewStart) / 1000.0);
        }
        // A replay's free camera: the spectator's own eyes, the broadcast look kept (no game interface)
        if (fr.lordfinn.craftmaster.client.replay.ReplayClient.active()
                && fr.lordfinn.craftmaster.client.replay.ReplayDirector.mode() == fr.lordfinn.craftmaster.client.replay.ReplayDirector.Mode.FREE) {
            Mosaic.prepare(false);
            return null;
        }
        update();
        Mosaic.prepare(shown.kind() == BroadcastNetwork.SHOT_MOSAIC);
        Pose result = switch (shown.kind()) {
            case BroadcastNetwork.SHOT_CAMERA -> camera(mc, ShowStateClient.cameras().get(shown.camera()), shotSeconds());
            case BroadcastNetwork.SHOT_FOLLOW -> follow(mc, shown.player(), partialTick, dt);
            case BroadcastNetwork.SHOT_PATH -> {
                CameraPath p = ShowStateClient.path(shown.camera());
                yield p == null ? null : path(mc, p, shotSeconds());
            }
            case BroadcastNetwork.SHOT_POV -> pov(mc, shown.player(), partialTick);
            case BroadcastNetwork.SHOT_FREE -> free(dt);
            case BroadcastNetwork.SHOT_MOSAIC -> mosaic(mc);
            default -> null;
        };
        if (result != null) lastPose = result;
        return result;
    }

    private static Pose lastPose;

    /** The last picture's camera (where a replay's free camera starts from), or null. */
    public static Pose lastPose() {
        return lastPose;
    }

    private static Pose camera(Minecraft mc, CameraSpot spot, double seconds) {
        if (spot == null || !spot.world().equals(ShowStateClient.world(mc))) return null;
        CameraMotion.View v = CameraMotion.view(spot, seconds);
        return new Pose(new Vec3(v.x(), v.y(), v.z()), v.yaw(), v.pitch(), spot.fov());
    }

    private static Pose path(Minecraft mc, CameraPath p, double seconds) {
        if (p.isEmpty() || !p.world().equals(ShowStateClient.world(mc))) return null;
        CameraPath.Pose v = p.sample(seconds, DirectorCamera::playerPosition);
        return new Pose(new Vec3(v.x(), v.y(), v.z()), v.yaw(), v.pitch(), v.fov());
    }

    /** A tracked entity's chest (a travelling step that follows a player, or a selector's entity), or null if not here. */
    public static double[] playerPosition(String target) {
        Minecraft mc = Minecraft.getInstance();
        var e = fr.lordfinn.craftmaster.client.studio.TrackedTargets.entity(target);
        if (e == null) return null;
        Vec3 at = e.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        return new double[]{at.x, at.y + e.getBbHeight() * 0.6, at.z};
    }

    /** The tiles of the split screen in turn, one per frame (each tile is refreshed every n frames). */
    private static Pose mosaic(Minecraft mc) {
        int n = Math.min(shown.tiles().size(), Mosaic.MAX);
        if (n == 0) return null;
        int i = (int) (mosaicFrame++ % n);
        Pose p = camera(mc, ShowStateClient.cameras().get(shown.tiles().get(i).camera()), shotSeconds());
        Mosaic.rendering = p == null ? -1 : i;
        return p;
    }

    private static Player player(Minecraft mc, String name) {
        if (mc.level != null) for (Player p : mc.level.players()) if (p.getGameProfile().name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Through a player's eyes (their own view, smoothed by the game's interpolation). */
    private static Pose pov(Minecraft mc, String name, float partialTick) {
        Player target = player(mc, name);
        if (target == null) {
            CameraSpot wide = ShowStateClient.focused().wide();
            return wide == null ? null : camera(mc, wide, shotSeconds());
        }
        Vec3 eye = target.getEyePosition(partialTick);
        return new Pose(eye, target.getViewYRot(partialTick), target.getViewXRot(partialTick), 0);
    }

    /** The free camera glides to the last pose the server sent. */
    private static Pose free(double dt) {
        FreeCamera f = freeTarget;
        if (f == null) return null;
        Vec3 want = new Vec3(f.x(), f.y(), f.z());
        if (freePos == null || f.smoothing() <= 0) {
            freePos = want;
            freeYaw = f.yaw();
            freePitch = f.pitch();
            freeFov = f.fov();
        } else {
            double k = CameraMotion.smoothing(dt, f.smoothing());
            freePos = freePos.lerp(want, k);
            freeYaw = CameraMotion.lerpAngle(freeYaw, f.yaw(), k);
            freePitch = freePitch + (float) ((f.pitch() - freePitch) * k);
            freeFov = freeFov + (float) ((f.fov() - freeFov) * k);
        }
        return new Pose(freePos, freeYaw, freePitch, freeFov);
    }

    /** Front three-quarter shot on a player, smoothed, pulled in front of walls. Falls back on the wide shot. */
    private static Pose follow(Minecraft mc, String name, float partialTick, double dt) {
        Player target = player(mc, name);
        if (target == null) {
            CameraSpot wide = ShowStateClient.focused().wide();
            if (wide == null) wide = ShowStateClient.cameras().group(CameraSpot.STUDIO).wide();
            return wide == null ? null : camera(mc, wide, shotSeconds());
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
