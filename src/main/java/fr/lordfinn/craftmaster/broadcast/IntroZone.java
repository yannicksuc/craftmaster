package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraPaths;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Lieux;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.MancheIntro;
import fr.lordfinn.craftmaster.trial.kit.Arena;
import fr.lordfinn.craftmaster.trial.kit.TrialRun;

import java.util.List;

/**
 * The zone of a trial's intro: the shots of a round played in arenas stay where the players arrive (the arena's world,
 * its reference and copies widened by {@link Arena#chargementMarge}, its {@link Arena#chargementZones}), the area
 * loaded before the launch. No studio camera, no far trajet: nothing is loaded and unloaded far away while the round
 * starts. A round without arenas has no zone (any shot).
 */
public final class IntroZone {

    private IntroZone() {}

    /** "" when the point is in the round's zone (or the round has no arena); else why not. */
    public static String check(String mancheId, String world, double x, double z) {
        List<Arena> arenas = TrialRun.arenas(mancheId);
        if (arenas.isEmpty()) return "";
        for (Arena a : arenas) {
            if (!a.world.equals(world)) continue;
            int m = Math.max(0, a.chargementMarge);
            if (inside(x, z, a.x0 - m, a.z0 - m, a.x1 + m, a.z1 + m)) return "";
            for (int[] o : a.copies.values())
                if (inside(x, z, a.x0 + o[0] - m, a.z0 + o[2] - m, a.x1 + o[0] + m, a.z1 + o[2] + m)) return "";
            for (int[] r : a.chargementZones)
                if (r.length >= 4 && inside(x, z, Math.min(r[0], r[2]), Math.min(r[1], r[3]), Math.max(r[0], r[2]), Math.max(r[1], r[3]))) return "";
        }
        String names = String.join(", ", arenas.stream().map(a -> a.name).toList());
        boolean sameWorld = arenas.stream().anyMatch(a -> a.world.equals(world));
        return sameWorld ? "loin de l'arène " + names : "hors du monde de l'arène " + names;
    }

    private static boolean inside(double x, double z, int x0, int z0, int x1, int z1) {
        return x >= Math.min(x0, x1) && x <= Math.max(x0, x1) + 1 && z >= Math.min(z0, z1) && z <= Math.max(z0, z1) + 1;
    }

    /** A camera: "" when it films inside the round's zone. */
    public static String check(String mancheId, CameraSpot c) {
        if (c == null) return "";
        if (!TrialRun.arenas(mancheId).isEmpty() && CameraSpot.STUDIO.equals(CameraSpot.lieuOf(c.lieu())))
            return "caméra du studio";
        return check(mancheId, c.world(), c.x(), c.z());
    }

    /** One shot of the round's intro: "" when it stays in the zone (the task location and a follow always do). */
    public static String check(Manche m, MancheIntro.Shot s) {
        if (m == null || TrialRun.arenas(m.id).isEmpty()) return "";
        switch (s.kind()) {
            case MancheIntro.CAMERA -> {
                return check(m.id, Lieux.resolve(null, s.ref(), m.id));
            }
            case MancheIntro.PATH -> {
                CameraPath p = CameraPaths.get(s.ref());
                if (p == null || p.isEmpty()) return "";
                double d = Math.max(0.1, p.duration());
                for (double t = 0; t <= d + 1e-6; t += Math.max(0.5, d / 16)) {
                    CameraPath.Pose pose = p.sample(t);
                    String why = check(m.id, p.world(), pose.x(), pose.z());
                    if (!why.isEmpty()) return "trajet " + why;
                }
                return "";
            }
            default -> {
                return "";
            }
        }
    }
}
