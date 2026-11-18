package fr.lordfinn.craftmaster.camera;

import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recording travellings in game (shared by {@code /cm cam trajet} and the "Repère de travelling" item): a step is
 * taken at the organiser's eyes, looking where they look. Also the dust line that shows a path in the world.
 */
public final class PathRecording {

    private PathRecording() {}

    /** Paths whose line is drawn, until that server tick. */
    private static final Map<String, Long> SHOWN = new HashMap<>();

    /**
     * Adds a step at the player's eyes to path {@code name} (created in the director's focus lieu if new).
     * {@code travel} &lt; 0: the same travel time as the previous step, or {@link CameraPath#DEFAULT_TRAVEL}.
     * Returns the line for the player.
     */
    public static String addStep(MinecraftServer server, ServerPlayer p, String name, double travel) {
        CameraPath old = CameraPaths.get(name);
        if (old != null && !old.name().equals(CameraPath.normalizeName(name))) old = null;
        String world = Busts.worldId(p.level());
        CameraPath path = old != null ? old : new CameraPath(name, Director.focusLieu(), world, List.of());
        if (!path.isEmpty() && !path.world().equals(world)) return "⚠ Le trajet « " + path.name() + " » est dans une autre dimension.";
        double t = travel >= 0 ? travel : path.steps().size() >= 2 ? path.steps().getLast().travel() : CameraPath.DEFAULT_TRAVEL;
        CameraPath.Step step = new CameraPath.Step(p.getX(), p.getEyeY(), p.getZ(), p.getYRot(), p.getXRot(), 0, t, 0, null);
        CameraPaths.put(server, path.plus(step));
        int n = path.steps().size() + 1;
        show(server, path.name(), 10);
        return n == 1
                ? "Trajet « " + path.name() + " » créé (" + Lieux.lieuName(path.lieu()) + ") : étape 1 à tes yeux. Déplace-toi et ajoute la suivante."
                : "Trajet « " + path.name() + " » : étape " + n + " ajoutée (" + CameraPath.seconds(t) + " depuis l'étape " + (n - 1)
                  + ", total " + CameraPath.seconds(path.plus(step).duration()) + ").";
    }

    /** Removes step {@code index} (0-based; -1 = the last one). Returns the line for the player. */
    public static String removeStep(MinecraftServer server, String name, int index) {
        CameraPath path = CameraPaths.get(name);
        if (path == null) return "⚠ Aucun trajet « " + name + " ».";
        if (path.isEmpty()) return "⚠ Le trajet « " + path.name() + " » n'a aucune étape.";
        int i = index < 0 ? path.steps().size() - 1 : index;
        if (i >= path.steps().size()) return "⚠ Le trajet « " + path.name() + " » n'a que " + path.steps().size() + " étape(s).";
        List<CameraPath.Step> steps = new ArrayList<>(path.steps());
        steps.remove(i);
        CameraPaths.put(server, path.withSteps(steps));
        show(server, path.name(), 8);
        return "Trajet « " + path.name() + " » : étape " + (i + 1) + " retirée (" + steps.size() + " restante(s)).";
    }

    /** Draws the path's line (gold dust, a bigger red dot on each step) for {@code seconds}. */
    public static void show(MinecraftServer server, String name, int seconds) {
        SHOWN.put(CameraPath.normalizeName(name), server.getTickCount() + seconds * 20L);
    }

    /** Stops drawing a path (it goes on air). */
    public static void hide(String name) {
        SHOWN.remove(CameraPath.normalizeName(name));
    }

    public static void tick(MinecraftServer server) {
        if (SHOWN.isEmpty() || server.getTickCount() % 4 != 0) return;
        long now = server.getTickCount();
        SHOWN.entrySet().removeIf(e -> e.getValue() < now || CameraPaths.byName().get(e.getKey()) == null);
        // Only for the organisers' own games: never on a broadcast picture
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (Permissions.canDriveCameras(p) && !Director.broadcasters().contains(p.getUUID())) viewers.add(p);
        if (viewers.isEmpty()) return;
        DustParticleOptions line = new DustParticleOptions(0xE8B84A, 0.7f), step = new DustParticleOptions(0xE0242E, 1.6f);
        for (String n : SHOWN.keySet()) {
            CameraPath p = CameraPaths.byName().get(n);
            ServerLevel level = Busts.level(server, p.world());
            if (level == null || p.isEmpty()) continue;
            for (ServerPlayer v : viewers) {
                if (v.level() != level) continue;
                for (CameraPath.Step s : p.steps()) level.sendParticles(v, step, true, false, s.x(), s.y(), s.z(), 2, 0.04, 0.04, 0.04, 0);
                double d = p.duration();
                int samples = (int) Math.min(240, Math.max(8, d * 8));
                for (int i = 0; i <= samples; i++) {
                    CameraPath.Pose pose = p.sample(d * i / samples);
                    level.sendParticles(v, line, true, false, pose.x(), pose.y(), pose.z(), 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
