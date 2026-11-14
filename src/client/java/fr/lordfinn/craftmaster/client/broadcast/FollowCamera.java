package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.camera.CameraMotion;
import fr.lordfinn.craftmaster.camera.FollowFraming;
import fr.lordfinn.craftmaster.camera.FollowFraming.Params;
import fr.lordfinn.craftmaster.camera.FollowFraming.Plan;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.ShotCoverage;
import fr.lordfinn.craftmaster.camera.TargetTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The follow shot (/cam follow, the automatic director, replays): framings that change by themselves
 * ({@link FollowFraming}: three-quarters, profile, low and high angle, wide, close-up, over the shoulder…), held
 * 7 to 12 s with a slow orbit or push in, then a glide round the player to the next one. The blocks decide:
 * <ul>
 *   <li>a framing is chosen only if the camera has room there (rays from the head to the four corners of the lens);</li>
 *   <li>every frame the camera is pulled in front of whatever is between it and the head, at once (never inside a
 *       block, never behind a wall), and only moves back out smoothly;</li>
 *   <li>meanwhile it steps round the player (side, lower, higher) towards the nearest place with room, smoothly, and
 *       back to the framing once it is clear;</li>
 *   <li>a framing squeezed too long (the player walked into a corridor, under a low ceiling) is changed early;</li>
 *   <li>a glide whose path would go through a wall becomes a cross-fade.</li>
 * </ul>
 * Now and then (when a placed camera sees the player well: {@link ShotCoverage}) it cuts to that camera for a few
 * seconds, the likelier the more of its picture the player fills, never one they are on, never one the director
 * avoids; back to the framings when the player leaves its picture or after {@link #SPOT_HOLD} s.
 * The director's wish (wider / tighter, {@code FollowPrefs} on the server) weighs on the framings and their distance.
 * <p>
 * On the broadcast game, the mouse turns the camera round the player and the wheel moves it closer or further: the
 * operator keeps it as long as they touch it; {@link #RESUME} seconds after the last touch, the automatic framing
 * comes back with a slow glide.
 */
public final class FollowCamera {

    /** Seconds without touching the mouse or the wheel before the automatic framing comes back. */
    static final double RESUME = 8;
    /** Degrees per mouse unit (the game's own look speed) and distance factor per wheel notch. */
    static final double TURN = 0.15, ZOOM = 0.85;
    /** Room kept between the lens and a block (blocks). */
    static final double MARGIN = 0.3;

    private String player = "";
    private Random random = new Random();
    private Plan plan, previous;
    /** Own clock (seconds), and when the framing started being held and for how long. */
    private double clock, planStart, holdFor;
    /** Glide from {@code from}: start and length (0 = none). */
    private Params from;
    private double glideStart, glideLength;
    /** Smoothed feet and facing of the player. */
    private Vec3 feet;
    private float facing;
    /** Distance actually used (pulled in by the blocks), and how long the framing has been squeezed. */
    private double shown = -1, squeezed;
    /** A cross-fade to another framing: the next frame takes its distance at once. */
    private boolean snap;
    /** When a squeezed framing was last changed (or kept, no better one being free): not again before {@link #RETRY}. */
    private double lastSqueeze = -100;
    /**
     * The player's feet as the camera follows them ({@link TargetTracker}, shared with the follow-spots): smoothed
     * without lagging behind a walking player; their walking speed and direction.
     */
    private final TargetTracker body = new TargetTracker(0.2, 0.25, 0, 0.6);
    private double speed;
    /** Seconds of motion the framing looks ahead of a walking player, and the cap (blocks). */
    static final double LOOK_AHEAD = 0.3, LOOK_AHEAD_MAX = 1.0;
    /** The third the player is put on (signed, smoothed): room left in front of them when they walk across the picture. */
    private double thirdsNow = Double.NaN;
    /** Side step round the player when a block is in the way (degrees of azimuth, elevation), smoothed. */
    private double dodgeYaw, dodgeElevation;
    /** Side steps tried when squeezed (azimuth, elevation, in degrees from the current one). */
    private static final double[][] DODGES = {{15, 0}, {-15, 0}, {30, 0}, {-30, 0}, {50, 0}, {-50, 0}, {0, -12}, {0, 12}, {25, -10}, {-25, -10}};
    static final double DODGE_MAX_YAW = 75, DODGE_MIN_ELEVATION = -25, DODGE_MAX_ELEVATION = 20;
    /** Seconds a framing must be squeezed before it changes, and between two such changes. */
    static final double SQUEEZE = 1.0, RETRY = 3;
    /** Closer than this to the head (blocks) for {@link #TOO_CLOSE_FOR} s: another framing at once (a low door). */
    static final double TOO_CLOSE = 0.9, TOO_CLOSE_FOR = 0.25;
    private double tooClose;
    /** Chance, at a change of framing, to cut to a placed camera that sees the player; how long it is held (s). */
    static final double SPOT_CHANCE = 0.35, SPOT_HOLD = 7;
    /** The placed camera on screen (null = the framings), whether the last change came from one, how long lost. */
    private CameraSpot spot;
    private boolean afterSpot;
    private double spotLost;
    /** The director's wish: +1 wider, -1 tighter, 0 as it comes; the cameras to avoid; distance factor (smoothed). */
    private static int prefSize;
    private static java.util.Set<String> prefAvoid = java.util.Set.of();
    private double sizeFactor = 1;
    /** Hand-held: world direction (yaw of the camera round the player), elevation, distance, last touch (ms). */
    private boolean manual;
    private double handYaw, handElevation, handDistance, handThirds, handFocus;
    private long lastTouch;
    /** The parameters of the last frame (where a glide or the hand-held camera starts from). */
    private Params current;


    /** The follow shot on screen, and the follow tiles of a split screen (one per player, by lower-case name). */
    private static FollowCamera main = new FollowCamera("", 0, false);
    private static final java.util.Map<String, FollowCamera> TILES = new java.util.HashMap<>();
    /** A split screen's tile: no hand-held camera, no cut to a placed camera, no cross-fade (it would fill the screen). */
    private final boolean tile;
    private long lastNanos;

    private FollowCamera(String name, int serial, boolean tile) {
        this.tile = tile;
        init(name, serial);
    }

    /** A new follow shot: starts again from the opening framing. */
    static void reset(String name, int serial) {
        main = new FollowCamera(name, serial, false);
    }

    /** The follow tile of {@code target} in a split screen, this frame (each tile keeps its own framings). */
    static DirectorCamera.Pose tile(Minecraft mc, Player target, float partialTick, double shotSeconds) {
        String name = target.getGameProfile().name();
        FollowCamera c = TILES.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> new FollowCamera(name, k.hashCode(), true));
        long now = System.nanoTime();
        double dt = c.lastNanos == 0 ? 0 : Math.min(0.25, (now - c.lastNanos) / 1e9);
        c.lastNanos = now;
        return c.frame(mc, target, partialTick, dt, shotSeconds);
    }

    /** The split screen is over: its follow tiles start afresh next time. */
    static void clearTiles() {
        TILES.clear();
    }

    private void init(String name, int serial) {
        player = name;
        random = new Random(name.toLowerCase(java.util.Locale.ROOT).hashCode() * 31L + serial);
        plan = null;
        previous = null;
        feet = null;
        shown = -1;
        squeezed = 0;
        dodgeYaw = 0;
        dodgeElevation = 0;
        lastSqueeze = -100;
        tooClose = 0;
        spot = null;
        afterSpot = false;
        speed = 0;
        manual = false;
        glideLength = 0;
        current = null;
    }

    /** The director's wish, sent by the server: {"size": -1|0|1, "avoid": [camera keys], "stay": player}. */
    public static void prefs(String json) {
        try {
            var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            prefSize = o.has("size") ? o.get("size").getAsInt() : 0;
            java.util.Set<String> avoid = new java.util.HashSet<>();
            if (o.has("avoid")) o.getAsJsonArray("avoid").forEach(e -> avoid.add(e.getAsString().toLowerCase(java.util.Locale.ROOT)));
            prefAvoid = avoid;
        } catch (RuntimeException e) {
            // a malformed message changes nothing
        }
    }

    /** The mouse and the wheel move the follow shot's camera now (broadcast game, follow shot on screen, no menu). */
    public static boolean handHeld() {
        Minecraft mc = Minecraft.getInstance();
        return Broadcast.active() && mc.gui.screen() == null && main.current != null
                && DirectorCamera.shown().kind() == BroadcastNetwork.SHOT_FOLLOW
                && !(fr.lordfinn.craftmaster.client.replay.ReplayClient.active()
                && fr.lordfinn.craftmaster.client.replay.ReplayDirector.mode() == fr.lordfinn.craftmaster.client.replay.ReplayDirector.Mode.FREE);
    }

    /** Mouse movement (as given to the player's turn): turns the camera round the player. True if taken. */
    public static boolean turn(double dx, double dy) {
        if (!handHeld()) return false;
        if (dx == 0 && dy == 0) return true;
        main.grab();
        main.handYaw += dx * TURN;
        main.handElevation = Math.max(FollowFraming.MIN_ELEVATION, Math.min(FollowFraming.MAX_ELEVATION, main.handElevation + dy * TURN));
        return true;
    }

    /** Wheel: closer (up) or further (down). True if taken. */
    public static boolean zoom(double amount) {
        if (!handHeld()) return false;
        main.grab();
        main.handDistance = Math.max(FollowFraming.MIN_DISTANCE, Math.min(FollowFraming.MAX_DISTANCE, main.handDistance * Math.pow(ZOOM, amount)));
        return true;
    }

    /** The operator takes the camera where it is now (or keeps it). */
    private void grab() {
        lastTouch = System.currentTimeMillis();
        if (manual) return;
        manual = true;
        if (spot != null) {
            // From a placed camera: the hand-held camera starts from the framing
            spot = null;
            snap = true;
        }
        handYaw = facing + current.azimuth() + dodgeYaw;
        handElevation = Math.max(FollowFraming.MIN_ELEVATION, Math.min(FollowFraming.MAX_ELEVATION, current.elevation() + dodgeElevation));
        dodgeYaw = 0;
        dodgeElevation = 0;
        handDistance = Math.max(FollowFraming.MIN_DISTANCE, shown > 0 ? shown : current.distance());
        handThirds = current.thirds();
        handFocus = current.focus();
        log("à la main");
    }

    /** The camera on {@code target} for this frame. */
    static DirectorCamera.Pose pose(Minecraft mc, Player target, float partialTick, double dt, double shotSeconds) {
        return main.frame(mc, target, partialTick, dt, shotSeconds);
    }

    private DirectorCamera.Pose frame(Minecraft mc, Player target, float partialTick, double dt, double shotSeconds) {
        clock += dt;
        float eyeHeight = target.getEyeHeight();
        Vec3 realFeet = target.getPosition(partialTick);
        Vec3 realEye = realFeet.add(0, eyeHeight, 0);
        float bodyYaw = target instanceof LivingEntity le ? le.yBodyRot : target.getYRot();
        body.update(realFeet.x, realFeet.y, realFeet.z, dt);
        if (feet == null) facing = bodyYaw;
        else facing = CameraMotion.lerpAngle(facing, bodyYaw, CameraMotion.smoothing(dt, 1.6));
        feet = new Vec3(body.x(), body.y(), body.z());
        // The followed head must never be behind a wall (a door just walked through)
        if (blocked(mc, target, realEye, feet.add(0, eyeHeight, 0))) {
            body.snap(realFeet.x, realFeet.y, realFeet.z);
            feet = realFeet;
        }
        speed = body.speed();
        if (plan == null) {
            plan = FollowFraming.PLANS.getFirst();
            if (room(mc, target, eyeHeight, plan, 0) < FollowFraming.ROOM_NEEDED) plan = FollowFraming.choose(null, null, p -> room(mc, target, eyeHeight, p, 0), this::weight, random);
            planStart = clock;
            holdFor = FollowFraming.hold(random);
            log(plan.name());
        }
        if (manual && System.currentTimeMillis() - lastTouch > RESUME * 1000) {
            manual = false;
            next(mc, target, eyeHeight, 3.2, "reprise auto : ");
        }
        if (spot != null && !manual) {
            DirectorCamera.Pose sp = spotPose(mc, target, realFeet, dt);
            if (sp != null) return sp;
        }
        sizeFactor += ((prefSize > 0 ? 1.35 : prefSize < 0 ? 0.75 : 1) - sizeFactor) * CameraMotion.smoothing(dt, 1.2);
        Params base = manual ? new Params(handYaw - facing, handElevation, handDistance, handThirds, handFocus) : auto();
        if (!manual && sizeFactor != 1) base = new Params(base.azimuth(), base.elevation(),
                Math.max(FollowFraming.MIN_DISTANCE, base.distance() * sizeFactor), base.thirds(), base.focus());
        current = base;
        Vec3 pivot = feet.add(0, eyeHeight * base.focus(), 0);
        if (!manual) dodge(mc, target, pivot, base, dt);
        Params p = manual ? base : dodged(base, dodgeYaw, dodgeElevation);

        double[] d = FollowFraming.direction(p, facing);
        double free = free(mc, target, pivot, d, p.distance());
        double want = Math.min(p.distance(), free);
        if (shown < 0 || free < shown || snap) shown = free;   // in front of the block at once (or a cut)
        else shown += (want - shown) * CameraMotion.smoothing(dt, want < shown ? 0.25 : 0.6);
        snap = false;

        if (!manual) {
            boolean tight = shown < 0.6 * p.distance() || shown < 1.1;
            squeezed = tight && glideLength == 0 ? squeezed + dt : 0;
            tooClose = shown < TOO_CLOSE ? tooClose + dt : 0;
            if (tooClose > TOO_CLOSE_FOR && clock - lastSqueeze > 0.8) {
                // In the player's head (a low door): another framing without waiting, even in the middle of a glide
                lastSqueeze = clock;
                tooClose = 0;
                next(mc, target, eyeHeight, 1.2, "trop près → ");
            } else if (squeezed > SQUEEZE && clock - planStart > 2 && clock - lastSqueeze > RETRY) {
                // Squeezed (corridor, low ceiling): another framing, without waiting for the end of this one
                lastSqueeze = clock;
                next(mc, target, eyeHeight, 1.6, "trop serré → ");
            } else if (glideLength == 0 && clock - planStart > holdFor) next(mc, target, eyeHeight, FollowFraming.GLIDE, "");
        }

        Vec3 cam = pivot.add(d[0] * shown, d[1] * shown, d[2] * shown);
        // The player on a third of the picture: aim a little to the side of the head; walking across the picture,
        // on the third that leaves room in front of them, and the aim a little ahead of where they go
        double hx = -d[0], hz = -d[2], h = Math.sqrt(hx * hx + hz * hz);
        double rx = h < 1e-4 ? 0 : -hz / h, rz = h < 1e-4 ? 0 : hx / h;
        double across = body.vx() * rx + body.vz() * rz;
        double wantThirds = Math.abs(across) > 1.2 ? Math.copySign(Math.max(Math.abs(p.thirds()), 0.4), across) : p.thirds();
        thirdsNow = Double.isNaN(thirdsNow) || manual ? wantThirds : thirdsNow + (wantThirds - thirdsNow) * CameraMotion.smoothing(dt, 0.9);
        double ax = body.vx() * LOOK_AHEAD, az = body.vz() * LOOK_AHEAD, al = Math.hypot(ax, az);
        if (al > LOOK_AHEAD_MAX) {
            ax *= LOOK_AHEAD_MAX / al;
            az *= LOOK_AHEAD_MAX / al;
        }
        Vec3 aim = pivot.add(rx * thirdsNow + ax, 0, rz * thirdsNow + az);
        float[] look = CameraMotion.lookAt(cam.x, cam.y, cam.z, aim.x, aim.y, aim.z);
        float breathYaw = (float) (0.4 * Math.sin(shotSeconds * 2 * Math.PI / 9.7));
        float breathPitch = (float) (0.25 * Math.sin(shotSeconds * 2 * Math.PI / 12.1 + 1));
        return new DirectorCamera.Pose(cam, look[0] + breathYaw, look[1] + breathPitch, 0);
    }

    private static Params dodged(Params p, double yaw, double elevation) {
        return new Params(p.azimuth() + yaw, Math.max(FollowFraming.MIN_ELEVATION, Math.min(FollowFraming.MAX_ELEVATION, p.elevation() + elevation)),
                p.distance(), p.thirds(), p.focus());
    }

    /**
     * A block in the way of the framing: steps round the player (to the side, lower or higher) towards the nearest
     * place with room, smoothly, like a cameraman stepping aside; back to the framing itself once it is clear again.
     */
    private void dodge(Minecraft mc, Player target, Vec3 pivot, Params base, double dt) {
        double now = free(mc, target, pivot, FollowFraming.direction(dodged(base, dodgeYaw, dodgeElevation), facing), base.distance());
        double plain = dodgeYaw == 0 && dodgeElevation == 0 ? now : free(mc, target, pivot, FollowFraming.direction(base, facing), base.distance());
        double wantYaw = dodgeYaw, wantElevation = dodgeElevation, tau;
        if (plain >= 0.95 * base.distance()) {
            // The framing itself is clear: back to it, slowly
            wantYaw = 0;
            wantElevation = 0;
            tau = 1.2;
        } else if (now < 0.9 * base.distance()) {
            // Squeezed where it is: the side step with the most room (the smallest one at equal room)
            double best = now;
            for (double[] o : DODGES) {
                double y = Math.max(-DODGE_MAX_YAW, Math.min(DODGE_MAX_YAW, dodgeYaw + o[0]));
                double e = Math.max(DODGE_MIN_ELEVATION, Math.min(DODGE_MAX_ELEVATION, dodgeElevation + o[1]));
                double f = free(mc, target, pivot, FollowFraming.direction(dodged(base, y, e), facing), base.distance());
                if (f > best + 0.15) {
                    best = f;
                    wantYaw = y;
                    wantElevation = e;
                }
            }
            tau = 0.35;
        } else return;
        double k = CameraMotion.smoothing(dt, tau);
        dodgeYaw += (wantYaw - dodgeYaw) * k;
        dodgeElevation += (wantElevation - dodgeElevation) * k;
        if (Math.abs(dodgeYaw) < 0.05 && Math.abs(dodgeElevation) < 0.05 && wantYaw == 0 && wantElevation == 0) {
            dodgeYaw = 0;
            dodgeElevation = 0;
        }
    }

    /** The automatic framing now: the held framing with its slow moves, eased in from the last one while gliding. */
    private Params auto() {
        Params held = FollowFraming.params(plan, clock - planStart);
        if (glideLength <= 0) return held;
        double t = (clock - glideStart) / glideLength;
        if (t >= 1) {
            glideLength = 0;
            return held;
        }
        return from.lerp(held, FollowFraming.ease(t));
    }

    /** The placed camera on screen, or null when it no longer shows the player well (then back to the framings). */
    private DirectorCamera.Pose spotPose(Minecraft mc, Player target, Vec3 realFeet, double dt) {
        CameraSpot s = ShowStateClient.cameras().get(spot.key());
        double seconds = clock - planStart;
        boolean ok = s != null && !prefAvoid.contains(s.key().toLowerCase(java.util.Locale.ROOT)) && sees(mc, target, realFeet, s, seconds);
        spotLost = ok ? 0 : spotLost + dt;
        if (s == null || spotLost > 0.6 || seconds > holdFor) {
            log("caméra " + spot.label() + " → cadrages");
            spot = null;
            afterSpot = true;
            Plan chosen = FollowFraming.choose(null, previous, p -> room(mc, target, target.getEyeHeight(), p, 0), this::weight, random);
            if (chosen != null) {
                previous = plan;
                plan = chosen;
            }
            planStart = clock;
            holdFor = FollowFraming.hold(random);
            glideLength = 0;
            dodgeYaw = 0;
            dodgeElevation = 0;
            snap = true;
            log(plan.name());
            return null;
        }
        CameraMotion.View v = CameraMotion.view(s, seconds);
        return new DirectorCamera.Pose(new Vec3(v.x(), v.y(), v.z()), v.yaw(), v.pitch(), s.fov());
    }

    /** Does placed camera {@code s} (at {@code seconds} of its motion) show the player well? */
    private boolean sees(Minecraft mc, Player target, Vec3 feet, CameraSpot s, double seconds) {
        if (!s.world().equals(ShowStateClient.world(mc))) return false;
        CameraMotion.View v = CameraMotion.view(s, seconds);
        ShotCoverage.Lens lens = new ShotCoverage.Lens(v.x(), v.y(), v.z(), v.yaw(), v.pitch(), s.fov() > 0 ? s.fov() : mc.options.fov().get());
        ShotCoverage.Body body = new ShotCoverage.Body(player, feet.x, feet.y, feet.z, target.getBbWidth(), target.getBbHeight());
        return ShotCoverage.judge(s, lens, aspect(mc), java.util.List.of(body), sight(mc, target), prefSize).weight() > 0;
    }

    /** A placed camera that shows the player well, the likelier the more of its picture they fill; null if none. */
    private CameraSpot pickSpot(Minecraft mc, Player target) {
        Vec3 feet = target.position();
        ShotCoverage.Body body = new ShotCoverage.Body(player, feet.x, feet.y, feet.z, target.getBbWidth(), target.getBbHeight());
        java.util.List<ShotCoverage.Seen> seen = new java.util.ArrayList<>();
        for (CameraSpot s : ShowStateClient.cameras().all()) {
            if (!s.world().equals(ShowStateClient.world(mc)) || prefAvoid.contains(s.key().toLowerCase(java.util.Locale.ROOT))) continue;
            ShotCoverage.Lens lens = ShotCoverage.Lens.of(s, mc.options.fov().get());
            seen.add(ShotCoverage.judge(s, lens, aspect(mc), java.util.List.of(body), sight(mc, target), prefSize));
        }
        return ShotCoverage.pickFor(seen, java.util.List.of(player), random);
    }

    private static double aspect(Minecraft mc) {
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        return h <= 0 ? ShotCoverage.ASPECT : (double) w / h;
    }

    private static ShotCoverage.Sight sight(Minecraft mc, Player target) {
        return (a, b) -> !blocked(mc, target, new Vec3(a[0], a[1], a[2]), new Vec3(b[0], b[1], b[2]));
    }

    /** Goes to another framing: a glide round the player if the way is clear, else a cross-fade. */
    private void next(Minecraft mc, Player target, float eyeHeight, double glide, String why) {
        // Sometimes a placed camera that sees the player well (not twice in a row, not when squeezed or resuming)
        if (!tile && why.isEmpty() && !afterSpot && random.nextDouble() < SPOT_CHANCE) {
            CameraSpot s = pickSpot(mc, target);
            if (s != null) {
                spot = s;
                spotLost = 0;
                planStart = clock;
                holdFor = SPOT_HOLD * (0.8 + 0.4 * random.nextDouble());
                log("caméra " + s.label());
                return;
            }
        }
        afterSpot = false;
        Plan chosen = FollowFraming.choose(manual ? null : plan, previous, p -> room(mc, target, eyeHeight, p, holdFor), this::weight, random);
        if (chosen == null) return;
        if (why.startsWith("trop") && FollowFraming.free(chosen, room(mc, target, eyeHeight, chosen, 0)) < shown * 1.25 + (why.startsWith("trop près") ? 0.3 : 0.4)) {
            // Squeezed, but nothing has clearly more room here (a door, a narrow passage): keep this framing
            squeezed = 0;
            return;
        }
        Params start = current == null ? FollowFraming.params(chosen, 0)
                : new Params(current.azimuth() + (manual ? 0 : dodgeYaw), current.elevation() + (manual ? 0 : dodgeElevation),
                shown > 0 ? shown : current.distance(), current.thirds(), current.focus());
        dodgeYaw = 0;
        dodgeElevation = 0;
        previous = plan;
        plan = chosen;
        planStart = clock;
        holdFor = FollowFraming.hold(random);
        squeezed = 0;
        boolean clear = true;
        Params end = FollowFraming.params(chosen, 0);
        for (int i = 1; i <= 6 && clear; i++) {
            Params q = start.lerp(end, i / 6.0);
            clear = free(mc, target, feet.add(0, eyeHeight * q.focus(), 0), FollowFraming.direction(q, facing), q.distance()) >= 0.7 * q.distance();
        }
        if (clear) {
            from = start;
            glideStart = clock;
            glideLength = glide;
            log(why + chosen.name() + " (glisse)");
        } else {
            if (!tile) Transition.capture();
            glideLength = 0;
            snap = true;
            log(why + chosen.name() + " (enchaîné : le chemin traverse un mur)");
        }
    }

    /**
     * How much a framing is wanted now: walking, the ones that see the player's face coming (less often from
     * behind); the director's wish, the wider or the tighter ones.
     */
    private double weight(Plan p) {
        double w = 1;
        if (speed >= 1.5) {
            double a = Math.abs(((p.azimuth() % 360) + 540) % 360 - 180);
            w = a <= 60 ? 3 : a >= 120 ? 0.3 : 1;
        }
        if (prefSize > 0 && p.distance() >= 3.8) w *= 3;
        if (prefSize < 0 && p.distance() <= 3.4) w *= 3;
        return w;
    }

    /** Share of framing {@code p}'s distance left free by the blocks, the worst of its start, middle and end. */
    private double room(Minecraft mc, Player target, float eyeHeight, Plan p, double hold) {
        double worst = 1;
        for (double s : new double[]{0, hold / 2, hold}) {
            Params q = FollowFraming.params(p, s);
            double f = free(mc, target, feet.add(0, eyeHeight * q.focus(), 0), FollowFraming.direction(q, facing), q.distance());
            worst = Math.min(worst, f / q.distance());
            if (hold == 0) break;
        }
        return worst;
    }

    /**
     * How far the lens can go from {@code pivot} along {@code d} (at most {@code distance}) without a block
     * between: rays from the head to the centre and the four corners of the lens, and from the chest to the lens (a
     * wall corner hiding the body), minus a margin.
     */
    static double free(Minecraft mc, Player target, Vec3 pivot, double[] d, double distance) {
        Vec3 dir = new Vec3(d[0], d[1], d[2]);
        // Two directions across the view: sideways (horizontal) and "up" of the lens
        Vec3 side = Math.abs(d[1]) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(-d[2], 0, d[0]).normalize();
        Vec3 up = side.cross(dir).normalize();
        double reach = distance + MARGIN;
        Vec3 end = pivot.add(dir.scale(reach));
        double best = reach;
        double[][] corners = {{0, 0}, {0.22, 0.16}, {-0.22, 0.16}, {0.22, -0.16}, {-0.22, -0.16}};
        for (double[] c : corners) {
            Vec3 to = end.add(side.scale(c[0])).add(up.scale(c[1]));
            HitResult hit = mc.level.clip(new ClipContext(pivot, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.MISS) continue;
            // Distance along the view axis
            best = Math.min(best, hit.getLocation().subtract(pivot).dot(dir));
        }
        // The body must be seen too: from the chest to the lens
        Vec3 chest = pivot.add(0, -0.7, 0);
        Vec3 lens = pivot.add(dir.scale(Math.max(0, Math.min(distance, best - MARGIN))));
        HitResult body = mc.level.clip(new ClipContext(chest, lens, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
        if (body.getType() != HitResult.Type.MISS) {
            // Pull in until the chest sees the lens: where the blocking point projects on the view axis
            best = Math.min(best, body.getLocation().subtract(pivot).dot(dir));
        }
        return Math.max(0, Math.min(distance, best - MARGIN));
    }

    private static boolean blocked(Minecraft mc, Player target, Vec3 a, Vec3 b) {
        if (a.distanceToSqr(b) < 1e-4) return false;
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target)).getType() != HitResult.Type.MISS;
    }

    private void log(String what) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) CraftMaster.LOG.info("[Suivi] {} : {}", player, what);
    }
}
