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

import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork.Tile;
import fr.lordfinn.craftmaster.camera.Lieux;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** How many of {@code players} the best camera shows (0: none sees anyone). */
    static int mostCount(List<ServerPlayer> players) {
        int most = 0;
        for (Seen s : judge(players)) most = Math.max(most, s.players().size());
        return most;
    }

    /**
     * A split screen of the round's zones (the contestants in different rooms or maps): one tile per zone where
     * someone is, its camera that sees them best, else following its player; {@code main}'s zone first. Null when
     * the contestants are not in two zones or more, or when the tiles are too far apart for the broadcast game to see
     * them all at once (different dimensions, or further than its view distance from their middle).
     */
    static List<Tile> zoneTiles(MinecraftServer server, List<ServerPlayer> players, String lieu, String main) {
        Map<String, List<ServerPlayer>> zones = new LinkedHashMap<>();
        for (ServerPlayer p : players) {
            String z = Lieux.zoneOf(server, p.getGameProfile().name(), lieu);
            if (z == null) continue;
            List<ServerPlayer> in = zones.computeIfAbsent(z, k -> new ArrayList<>());
            if (main != null && p.getGameProfile().name().equalsIgnoreCase(main)) in.addFirst(p);
            else in.add(p);
        }
        if (zones.size() < 2) return null;
        List<List<ServerPlayer>> order = new ArrayList<>(zones.values());
        order.sort((a, b) -> Boolean.compare(isMain(b.getFirst(), main), isMain(a.getFirst(), main)));
        List<Tile> tiles = new ArrayList<>();
        List<double[]> at = new ArrayList<>();
        String world = null;
        for (List<ServerPlayer> in : order) {
            if (tiles.size() == fr.lordfinn.craftmaster.broadcast.SplitLayouts.MAX) break;
            ServerPlayer rep = in.getFirst();
            String w = rep.level().dimension().identifier().toString();
            if (world == null) world = w;
            else if (!world.equals(w)) return null;
            CameraSpot cam = showing(in);
            if (cam == null) cam = showing(List.of(rep));
            if (cam != null) {
                tiles.add(new Tile(cam.key(), ""));
                at.add(new double[]{cam.x(), cam.z()});
            } else {
                tiles.add(new Tile(fr.lordfinn.craftmaster.broadcast.SplitLayouts.followKey(rep.getGameProfile().name()), ""));
            }
            at.add(new double[]{rep.getX(), rep.getZ()});
        }
        // The broadcast game stands at the middle of the tiles: everything must be within its view distance
        double mx = 0, mz = 0;
        for (double[] p : at) {
            mx += p[0] / at.size();
            mz += p[1] / at.size();
        }
        double reach = (server.getPlayerList().getViewDistance() - 1) * 16.0;
        for (double[] p : at) if (Math.hypot(p[0] - mx, p[1] - mz) > reach) return null;
        return tiles;
    }

    private static boolean isMain(ServerPlayer p, String main) {
        return main != null && p.getGameProfile().name().equalsIgnoreCase(main);
    }

    /** A layout fitting {@code n} tiles, at random: two side by side or cut in half; more in a grid or one large. */
    static String layoutFor(int n) {
        List<String> fit = n <= 2 ? List.of(fr.lordfinn.craftmaster.broadcast.SplitLayouts.EDGE, fr.lordfinn.craftmaster.broadcast.SplitLayouts.FRAMES)
                : List.of(fr.lordfinn.craftmaster.broadcast.SplitLayouts.FRAMES, fr.lordfinn.craftmaster.broadcast.SplitLayouts.MAIN);
        return fit.get(RANDOM.nextInt(fit.size()));
    }

    /** True with probability {@code p} (« on va voir » sometimes shows the others small too). */
    static boolean chance(double p) {
        return RANDOM.nextDouble() < p;
    }
}
