package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.camera.ShotCoverage;
import fr.lordfinn.craftmaster.camera.ShotCoverage.Body;
import fr.lordfinn.craftmaster.camera.ShotCoverage.Lens;
import fr.lordfinn.craftmaster.camera.ShotCoverage.Seen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The automatic direction's choice of a placed camera, on the server ({@link ShotCoverage} with the world's blocks):
 * a camera that really sees these players, the more of the picture they fill the likelier, never one they are
 * "eating"; or the camera that sees the most players. Cameras avoided by the director ({@link FollowPrefs}) are left
 * out; the wish for a wider / tighter framing weighs in.
 */
public final class CameraChoice {

    private CameraChoice() {}

    /** Field of view of a camera at the player's setting (unknown here). */
    static final double DEFAULT_FOV = 70;
    private static final Random RANDOM = new Random();

    static Body body(ServerPlayer p) {
        return new Body(p.getGameProfile().name(), p.getX(), p.getY(), p.getZ(), p.getBbWidth(), p.getBbHeight());
    }

    /** Every placed camera in these players' world (not avoided), judged for them. */
    static List<Seen> judge(List<ServerPlayer> players) {
        List<Seen> out = new ArrayList<>();
        if (players.isEmpty()) return out;
        ServerLevel level = (ServerLevel) players.getFirst().level();
        String world = level.dimension().identifier().toString();
        List<Body> bodies = new ArrayList<>();
        for (ServerPlayer p : players) if (p.level() == level) bodies.add(body(p));
        ShotCoverage.Sight sight = (a, b) -> level.clip(new ClipContext(new Vec3(a[0], a[1], a[2]), new Vec3(b[0], b[1], b[2]),
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
        for (CameraSpot s : Cameras.list().all()) {
            if (!s.world().equals(world) || FollowPrefs.avoided(s.key())) continue;
            out.add(ShotCoverage.judge(s, Lens.of(s, DEFAULT_FOV), ShotCoverage.ASPECT, bodies, sight, FollowPrefs.size()));
        }
        return out;
    }

    /** A camera that shows all of {@code wanted} well (weighted at random), or null. */
    static CameraSpot showing(List<ServerPlayer> wanted) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : wanted) names.add(p.getGameProfile().name());
        return ShotCoverage.pickFor(judge(wanted), names, RANDOM);
    }

    /** The camera that shows the most of {@code players} (the contestants in sight), or null if it sees none. */
    static CameraSpot most(List<ServerPlayer> players) {
        return ShotCoverage.pickMost(judge(players), RANDOM);
    }
}
