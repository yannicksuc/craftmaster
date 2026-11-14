package fr.lordfinn.craftmaster.stage;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.portrait.PortraitCommands;
import fr.lordfinn.craftmaster.portrait.PortraitEntity;
import fr.lordfinn.craftmaster.portrait.Portraits;
import fr.lordfinn.craftmaster.show.StageControl;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import fr.lordfinn.craftmaster.studio.SpotlightFollow;
import fr.lordfinn.craftmaster.studio.Spotlights;
import fr.lordfinn.craftmaster.trial.TrialZones;
import fr.lordfinn.craftmaster.trial.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays timelines ({@link Sequence}) on the server: each keypoint when its time comes (its transition is the
 * spotlights' own fade, the portraits' hoist time…), waves across a group from the audience's left to right,
 * portraits one after the other, loops. Several timelines can play at once (a looping chase under a reveal); playing
 * one already playing starts it again. {@link #seek} puts the stage as it is at a time of a timeline, for the editor.
 */
public final class Sequencer {

    private Sequencer() {}

    private static final class Run {
        final String name;
        Sequence seq;
        long startTick;
        double offset;
        final Set<Sequence.Key> fired = Collections.newSetFromMap(new IdentityHashMap<>());

        Run(String name, Sequence seq) {
            this.name = name;
            this.seq = seq;
        }
    }

    private record Pending(long tick, Runnable action) {}

    private static final Map<String, Run> RUNS = new LinkedHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Sequencer::tick);
    }

    // ---------------------------------------------------------------- playing

    public static boolean exists(String name) {
        return Scenography.book().sequences.containsKey(Scenography.key(name));
    }

    /** Plays the timeline {@code name} from {@code from} seconds (the stage first put as it is at that time). */
    public static String play(MinecraftServer server, String name, double from) {
        String k = Scenography.key(name);
        Sequence seq = Scenography.book().sequences.get(k);
        if (seq == null) return "Séquence « " + k + " » inconnue (" + (Scenography.book().sequences.isEmpty() ? "aucune" : String.join(", ", Scenography.book().sequences.keySet())) + ").";
        from = Math.max(0, Math.min(seq.duree, from));
        Run run = new Run(k, seq);
        run.startTick = server.getTickCount();
        run.offset = from;
        if (from > 0) {
            seek(server, seq, from);
            for (Sequence.Track tr : seq.pistes) for (Sequence.Key key : tr.cles) if (key.t < from) run.fired.add(key);
        }
        RUNS.put(k, run);
        CraftMaster.LOG.info("[Scénographie] Séquence « {} » lancée{}", k, from > 0 ? " à " + from + " s" : "");
        tick(server);
        return "Séquence « " + k + " » lancée" + (from > 0 ? " à " + Sequence.num(from) + " s" : "") + " (" + Sequence.num(seq.duree) + " s"
                + (seq.boucle ? ", en boucle" : "") + ").";
    }

    /** Stops one timeline, or all of them ({@code name} null or empty). The stage stays as it is. */
    public static String stop(String name) {
        if (name == null || name.isBlank()) {
            if (RUNS.isEmpty()) return "Aucune séquence en cours.";
            int n = RUNS.size();
            RUNS.clear();
            PENDING.clear();
            return n + " séquence(s) arrêtée(s).";
        }
        Run r = RUNS.remove(Scenography.key(name));
        return r == null ? "La séquence « " + Scenography.key(name) + " » ne joue pas." : "Séquence « " + r.name + " » arrêtée.";
    }

    /** Playing timelines and where they are (seconds), for the editor's playhead and /sequence statut. */
    public static Map<String, Double> playing(MinecraftServer server) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Run r : RUNS.values()) out.put(r.name, r.offset + (server.getTickCount() - r.startTick) / 20.0);
        return out;
    }

    /** A timeline being edited changed: one playing it goes on with the new version. */
    static void edited(String name, Sequence seq) {
        Run r = RUNS.get(name);
        if (r == null) return;
        r.seq = seq;
        // Keys before the playhead count as played (the new ones too): nothing jumps back
        r.fired.clear();
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (!PENDING.isEmpty()) {
            List<Pending> due = new ArrayList<>();
            PENDING.removeIf(p -> {
                if (p.tick > now) return false;
                due.add(p);
                return true;
            });
            for (Pending p : due) runSafely(p.action);
        }
        if (RUNS.isEmpty()) return;
        for (Run r : List.copyOf(RUNS.values())) {
            double t = r.offset + (now - r.startTick) / 20.0;
            if (r.fired.isEmpty() && r.offset > 0 && t > r.offset) {
                // Edited while playing: everything up to the playhead is already on stage
                for (Sequence.Track tr : r.seq.pistes) for (Sequence.Key k : tr.cles) if (k.t < r.offset) r.fired.add(k);
            }
            for (Sequence.Track tr : r.seq.pistes) {
                if (tr.muette) continue;
                for (Sequence.Key k : tr.cles) {
                    if (k.t > t || r.fired.contains(k)) continue;
                    r.fired.add(k);
                    Sequence.Track track = tr;
                    runSafely(() -> apply(server, track, k, k.fondu));
                }
            }
            if (t >= r.seq.duree) {
                if (r.seq.boucle && r.seq.duree > 0) {
                    r.startTick = now;
                    r.offset = 0;
                    r.fired.clear();
                } else {
                    RUNS.remove(r.name);
                    CraftMaster.LOG.info("[Scénographie] Séquence « {} » terminée", r.name);
                }
            }
        }
    }

    private static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            CraftMaster.LOG.warn("[Scénographie] Keypoint : {}", e.toString());
        }
    }

    private static void later(MinecraftServer server, double seconds, Runnable action) {
        if (seconds <= 0.025) action.run();
        else PENDING.add(new Pending(server.getTickCount() + Math.round(seconds * 20), action));
    }

    // ---------------------------------------------------------------- seeking (the editor's preview)

    /**
     * Puts the stage as it is at {@code at} seconds of {@code seq}, at once: for each track, its keys up to then
     * merged (the latest value of each setting wins). Commands are not run (a seek must not fire fireworks twice).
     */
    public static void seek(MinecraftServer server, Sequence seq, double at) {
        for (Sequence.Track tr : seq.pistes) {
            // Commands and camera cuts are not replayed by a seek (no fireworks twice, no surprise cut on air)
            if (tr.muette || tr.type.equals(Sequence.COMMAND) || tr.type.equals(Sequence.CAMERA)) continue;
            Sequence.Key merged = null;
            for (Sequence.Key k : tr.cles) {
                if (k.t > at) break;
                merged = merged == null ? k.copy() : merge(merged, k);
            }
            if (merged != null) {
                merged.vague = null;
                merged.cascade = null;
                Sequence.Key m = merged;
                runSafely(() -> apply(server, tr, m, 0));
            }
        }
    }

    /** {@code base} with every setting {@code k} gives (a later keypoint over an earlier one). */
    static Sequence.Key merge(Sequence.Key base, Sequence.Key k) {
        Sequence.Key m = base.copy();
        m.t = k.t;
        m.fondu = k.fondu;
        if (k.allume != null) m.allume = k.allume;
        if (k.couleur != null) m.couleur = k.couleur;
        if (k.intensite != null) m.intensite = k.intensite;
        if (k.cone != null) m.cone = k.cone;
        if (k.suit != null) {
            m.suit = k.suit;
            m.vise = null;
            m.horizontal = m.vertical = null;
        }
        if (k.vise != null) {
            m.vise = k.vise;
            m.suit = null;
            m.horizontal = m.vertical = null;
        }
        if (k.horizontal != null && k.vertical != null) {
            m.horizontal = k.horizontal;
            m.vertical = k.vertical;
            m.suit = null;
            m.vise = null;
        }
        if (k.effet != null) m.effet = k.effet;
        if (k.vitesse != null) m.vitesse = k.vitesse;
        if (k.ouvert != null) m.ouvert = k.ouvert;
        if (k.hauteur != null) m.hauteur = k.hauteur;
        if (k.montre != null) m.montre = k.montre;
        m.vague = k.vague;
        m.cascade = k.cascade;
        return m;
    }

    // ---------------------------------------------------------------- one keypoint

    /** Applies one keypoint of a track, its change taking {@code fade} seconds. */
    static void apply(MinecraftServer server, Sequence.Track tr, Sequence.Key k, double fade) {
        switch (tr.type) {
            case Sequence.CURTAIN -> {
                if (k.ouvert == null) return;
                for (String name : curtains(tr.cible)) StageControl.moveCurtain(server, name, k.ouvert);
            }
            case Sequence.PORTRAITS -> {
                if (k.hauteur == null) return;
                List<PortraitEntity> list = portraits(server, tr.cible);
                double step = k.cascade == null ? 0 : Math.max(0, k.cascade);
                for (int i = 0; i < list.size(); i++) {
                    PortraitEntity p = list.get(i);
                    later(server, i * step, () -> {
                        if (!p.isRemoved()) PortraitCommands.hoist(p, k.hauteur, Math.max(0.05, fade));
                    });
                }
            }
            case Sequence.SCREEN -> {
                if (k.montre == null || tr.cible.isBlank()) return;
                String what = k.montre.isBlank() ? "off" : k.montre.strip();
                Cues.run(server, "cmtele " + tr.cible.strip() + " " + what);
            }
            case Sequence.COMMAND -> {
                if (k.commande != null && !k.commande.isBlank()) Cues.run(server, k.commande.strip());
            }
            case Sequence.CAMERA -> {
                if (k.plan == null || k.plan.isBlank()) return;
                java.util.Map<String, Object> act = new java.util.HashMap<>();
                if (k.plan.startsWith("camera:")) {
                    act.put("type", "camera.cut");
                    act.put("camera", k.plan.substring(7));
                } else if (k.plan.startsWith("travelling:")) {
                    act.put("type", "camera.path");
                    act.put("path", k.plan.substring(11));
                } else return;
                if (k.transition != null && !k.transition.isBlank()) act.put("transition", k.transition.strip());
                fr.lordfinn.craftmaster.show.ActionExecutor.execute(server, java.util.List.of(act));
            }
            default -> applyLight(server, tr, k, fade);
        }
    }

    private static void applyLight(MinecraftServer server, Sequence.Track tr, Sequence.Key k, double fade) {
        List<SpotlightBlockEntity> list = new ArrayList<>(Spotlights.select(tr.cible));
        if (list.isEmpty()) return;
        Plan plan = Plan.of(server);
        list.sort(Comparator.comparingDouble(s -> plan.side(Vec3.atCenterOf(s.getBlockPos()))));
        Integer color = k.couleur == null ? null : Spotlights.parseColor(k.couleur);
        String follow = null;
        if (k.suit != null) {
            if (SpotlightFollow.isNobody(k.suit)) follow = "";
            else {
                String rule = SpotlightFollow.parse(k.suit);
                follow = rule != null ? rule : k.suit.strip();
            }
        }
        Vec3 aim = k.vise == null ? null : plan.place(k.vise);
        Spotlights.Settings set = new Spotlights.Settings(k.allume, color, k.intensite, k.cone,
                follow != null ? follow : aim != null || k.horizontal != null ? "" : null, aim,
                k.horizontal != null && k.vertical != null ? k.horizontal : null,
                k.horizontal != null && k.vertical != null ? k.vertical : null);
        double wave = k.vague == null ? 0 : Math.max(0, k.vague);
        int n = list.size();
        for (int i = 0; i < n; i++) {
            SpotlightBlockEntity s = list.get(i);
            float phase = n <= 1 ? 0 : i / (float) n;
            later(server, n <= 1 ? 0 : wave * i / (n - 1), () -> {
                if (s.isRemoved()) return;
                Spotlights.apply(s, set, fade);
                if (k.effet != null || k.vitesse != null) {
                    String effect = k.effet != null ? k.effet : s.effect();
                    float speed = k.vitesse != null ? k.vitesse.floatValue() : k.effet != null ? 1 : s.effectSpeed();
                    s.setEffect(effect, speed, phase);
                }
            });
        }
    }

    private static List<String> curtains(String target) {
        Map<String, ?> all = CraftMaster.data().stage().curtains;
        String t = Scenography.key(target);
        if (t.isEmpty() || t.equals("tout") || t.equals("tous")) return new ArrayList<>(all.keySet());
        List<String> out = new ArrayList<>();
        for (String name : all.keySet()) if (name.equalsIgnoreCase(t) || name.toLowerCase().startsWith(t)) out.add(name);
        return out;
    }

    /** The portraits of a season's gallery ("tout": every portrait), from the audience's left to right. */
    private static List<PortraitEntity> portraits(MinecraftServer server, String target) {
        String t = Scenography.key(target);
        List<PortraitEntity> out = new ArrayList<>();
        Integer season = null;
        try {
            if (!t.isEmpty() && !t.equals("tout")) season = Integer.parseInt(t.replaceAll("^saison\\s*", ""));
        } catch (NumberFormatException ignored) {
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (season != null) out.addAll(Portraits.gallery(level, season));
            else out.addAll(level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(PortraitEntity.class), e -> !e.isRemoved()));
        }
        Plan plan = Plan.of(server);
        out.sort(Comparator.comparingDouble(p -> plan.side(p.position())));
        return out;
    }

    // ---------------------------------------------------------------- the stage's plan

    /**
     * Left and right as the audience sees them, the stage's centre, named points: from the plateau settings and the
     * trial zone "scene".
     */
    public record Plan(Vec3 centre, float front, Look.Plateau plateau) {

        public static Plan of(MinecraftServer server) {
            Look.Plateau p = Scenography.book().plateau;
            Vec3 centre = null;
            Look.Point c = p.points.get("centre");
            if (c != null) centre = new Vec3(c.x, c.y, c.z);
            else {
                Zone z = TrialZones.zones().get(p.scene);
                if (z != null) centre = new Vec3((z.x0() + z.x1() + 1) / 2.0, Math.min(z.y0(), z.y1()), (z.z0() + z.z1() + 1) / 2.0);
            }
            return new Plan(centre == null ? Vec3.ZERO : centre, p.devant == null ? 0 : p.devant, p);
        }

        /** Unit vector to the audience's right. */
        public Vec3 right() {
            double f = Math.toRadians(front);
            return new Vec3(-Math.cos(f), 0, -Math.sin(f));
        }

        /** How far right of the centre a place is, as the audience sees it (negative: left). */
        public double side(Vec3 at) {
            return at.subtract(centre).dot(right());
        }

        /** "@centre", "@point:name", "x y z" → a place, or null. */
        public Vec3 place(String s) {
            if (s == null || s.isBlank()) return null;
            String k = s.strip();
            if (k.equalsIgnoreCase("@centre") || k.equalsIgnoreCase("@center") || k.equalsIgnoreCase("@scene")) return centre.add(0, 1, 0);
            if (k.startsWith("@point:")) {
                Look.Point p = plateau.points.get(Scenography.key(k.substring(7)));
                return p == null ? null : new Vec3(p.x, p.y, p.z);
            }
            String[] c = k.replace(',', ' ').trim().split("\\s+");
            if (c.length != 3) return null;
            try {
                return new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** "x y z" seen in the mirror of the stage's middle line. */
        public String mirrorPlace(String s) {
            Vec3 v = place(s);
            if (v == null || s.startsWith("@")) return s;
            Vec3 r = right();
            double d = v.subtract(centre).dot(r);
            Vec3 m = v.subtract(r.scale(2 * d));
            return Sequence.num(Math.round(m.x * 100) / 100.0).replace(',', '.') + " " + Sequence.num(Math.round(m.y * 100) / 100.0).replace(',', '.')
                    + " " + Sequence.num(Math.round(m.z * 100) / 100.0).replace(',', '.');
        }
    }

    /** A spotlight's world, for messages. */
    static String world(SpotlightBlockEntity s) {
        return Busts.worldId(s.getLevel());
    }
}
