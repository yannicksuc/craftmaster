package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.camera.Framing;
import fr.lordfinn.craftmaster.camera.Framing.Vec;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Spot;
import fr.lordfinn.craftmaster.task.EnvelopeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The "task location" shot of an intro (docs/design/intro-epreuve.md): the last shot shows where the players will
 * arrive and the task waiting there. Its camera is generated the first time: the round's first TP point, the
 * envelope placed nearest to it ({@link EnvelopeBlockEntity}, within {@link #SEARCH} blocks), and a viewpoint behind
 * the arrival that sees both (free lens position, clear line of sight). It is a bare viewpoint in the round's lieu,
 * named {@link #NAME}, without a tripod (it must not stand in the players' way); once made it is reused as it is,
 * so the organiser can move it ({@code /cam}) and it stays; {@link #regenerate} makes it again.
 */
public final class TaskFraming {

    public static final String NAME = "Lieu de la tâche";
    static final int SEARCH = 16;

    private TaskFraming() {}

    /** The camera found or made, or the reason there is none. */
    public record Result(CameraSpot camera, String problem, boolean envelope) {}

    /** The round's task location camera: the existing one, else a new one. */
    public static Result camera(MinecraftServer server, Manche m) {
        CameraSpot existing = existing(m);
        if (existing != null) return new Result(existing, "", true);
        return regenerate(server, m);
    }

    static CameraSpot existing(Manche m) {
        for (CameraSpot s : Cameras.list().lieu(m.id).all()) if (s.name().equals(NAME)) return s;
        return null;
    }

    /** Places (or moves) the round's task location camera from its TP point and envelope. */
    public static Result regenerate(MinecraftServer server, Manche m) {
        if (m.tp.isEmpty()) return new Result(null, "la manche n'a pas de point de TP (/cm tp)", false);
        Spot arrival = m.tp.getFirst();
        ServerLevel level = Busts.level(server, arrival.world());
        if (level == null) return new Result(null, "monde " + arrival.world() + " introuvable", false);
        Vec eyes = new Vec(arrival.x(), arrival.y() + 1.6, arrival.z());
        BlockPos envelope = nearestEnvelope(level, BlockPos.containing(arrival.x(), arrival.y(), arrival.z()));
        Vec subject;
        Vec env = null;
        if (envelope != null) {
            env = new Vec(envelope.getX() + 0.5, envelope.getY() + 0.3, envelope.getZ() + 0.5);
            // Between the arrival and the envelope, closer to the envelope: the task is what the shot is about
            subject = eyes.add(0, -0.6, 0).lerp(env, 0.6);
        } else {
            // No envelope: what the players will face, two blocks ahead
            double[] f = Framing.facing(arrival.yaw());
            subject = new Vec(arrival.x() + f[0] * 2, arrival.y() + 1, arrival.z() + f[1] * 2);
        }
        Vec lens = null, fallback = null;
        for (Vec c : Framing.candidates(subject, arrival.yaw())) {
            if (!free(level, c) || !sees(level, c, subject)) continue;
            if (fallback == null) fallback = c;
            if ((env == null || sees(level, c, env)) && sees(level, c, eyes)) {
                lens = c;
                break;
            }
        }
        if (lens == null) lens = fallback;
        if (lens == null) return new Result(null, "aucun point de vue dégagé autour du point de TP", envelope != null);
        CameraSpot old = existing(m);
        String group = CameraSpot.group(m.id, "");
        int number = old != null ? old.number() : Cameras.list().nextFree(group);
        CameraSpot spot = new CameraSpot(number, NAME, "", arrival.world(), lens.x(), lens.y(), lens.z(),
                Framing.yaw(lens, subject), Framing.pitch(lens, subject), 0, CameraSpot.BREATHING, m.id, "");
        Cameras.placeViewpoint(server, spot);
        return new Result(spot, envelope == null ? "pas d'enveloppe posée près du point de TP : le plan montre l'arrivée" : "", envelope != null);
    }

    private static BlockPos nearestEnvelope(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-SEARCH, -SEARCH / 2, -SEARCH), center.offset(SEARCH, SEARCH / 2, SEARCH))) {
            if (!level.isLoaded(p)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof EnvelopeBlockEntity)) continue;
            double d = p.distSqr(center);
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** The lens is in the open (its block and the one below have no collision). */
    private static boolean free(ServerLevel level, Vec c) {
        BlockPos p = BlockPos.containing(c.x(), c.y(), c.z());
        return level.isLoaded(p) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** Nothing solid between the lens and the point (the point's own block excepted). */
    private static boolean sees(ServerLevel level, Vec from, Vec to) {
        Vec3 a = new Vec3(from.x(), from.y(), from.z()), b = new Vec3(to.x(), to.y(), to.z());
        BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getBlockPos().equals(BlockPos.containing(b)) || hit.getLocation().distanceTo(b) < 0.6;
    }

    /** For the organiser: "caméra pont:3 (Lieu de la tâche)". */
    static String describe(CameraSpot s) {
        return "caméra " + s.key() + " (" + s.name() + ")";
    }
}
