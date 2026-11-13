package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A round of a game (formerly a "task"). Its {@link #id} never changes, so renaming keeps the scores; the order is
 * its place in {@link Partie#manches}.
 */
public final class Manche {

    public final String id;
    public String name;
    public final List<String> aliases = new ArrayList<>();
    /** Played on the stage: its scenery is placed at the stage anchor when it starts. */
    public boolean onStage;
    /** Name of a saved scenery, or null. */
    public String scene;
    /** Countdown in seconds when it starts, 0 = none. */
    public int timer;
    /**
     * When the timer starts: "lettre" (default: a player of the round first reads its letter; a round without a
     * written task starts it at the launch), "lancement" (with the round) or "bouton" (players frozen until they click « Commencer l'épreuve » in it; the first click starts it).
     * See fr.lordfinn.craftmaster.task.RoundStart.
     */
    public String timerStart = "lettre";
    /** Where the season's players are teleported when it starts (shared out in turn). */
    public final List<Spot> tp = new ArrayList<>();
    /** Its trial zones (TrialZones names, /craftMasterZone): what its replays record, one file each. */
    public final List<String> zones = new ArrayList<>();
    /** Put away: no longer listed nor "next", its points still count. */
    public boolean archived;
    // ---- chantier H: the judge's results sheet of this round (times, counts... → proposed points), undoable
    public fr.lordfinn.craftmaster.trial.ResultSheet results = new fr.lordfinn.craftmaster.trial.ResultSheet();
    /** Text of the task read on the envelope's card (chantier D), or null if none was written. */
    public Task task;
    /** Animated intro of the round on the broadcast (chantier E, TV direction); empty = none. */
    public MancheIntro intro = new MancheIntro();

    public Manche(String id, String name) {
        this.id = id;
        this.name = name;
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        Maps.putIf(m, "aliases", new ArrayList<>(aliases));
        Maps.putIf(m, "onStage", onStage);
        Maps.putIf(m, "scene", scene);
        Maps.putIf(m, "timer", timer);
        if (!"lettre".equals(timerStart)) m.put("timerStart", timerStart);
        List<Object> spots = new ArrayList<>();
        for (Spot s : tp) spots.add(s.toYaml());
        Maps.putIf(m, "tp", spots);
        Maps.putIf(m, "zones", new ArrayList<>(zones));
        Maps.putIf(m, "archived", archived);
        if (!results.isEmpty()) m.put("results", results.toYaml()); // chantier H
        if (task != null) m.put("task", task.toYaml());
        if (!intro.isEmpty()) m.put("intro", intro.toYaml()); // chantier E: intro of the round
        return m;
    }

    static Manche fromYaml(Map<String, Object> m) {
        String name = Maps.str(m.get("name"), Maps.str(m.get("id"), "?"));
        Manche r = new Manche(Maps.str(m.get("id"), Partie.slug(name)), name);
        r.aliases.addAll(Maps.strings(m.get("aliases")));
        r.onStage = Maps.bool(m.get("onStage"));
        r.scene = m.get("scene") == null ? null : String.valueOf(m.get("scene"));
        r.timer = Maps.toInt(m.get("timer"));
        String start = fr.lordfinn.craftmaster.task.RoundStart.parse(Maps.str(m.get("timerStart"), "lettre"));
        r.timerStart = start == null ? "lettre" : start;
        for (Object o : Maps.list(m.get("tp"))) {
            Spot s = Spot.fromYaml(Maps.map(o));
            if (s != null) r.tp.add(s);
        }
        r.zones.addAll(Maps.strings(m.get("zones")));
        r.archived = Maps.bool(m.get("archived"));
        r.results = fr.lordfinn.craftmaster.trial.ResultSheet.fromYaml(m.get("results")); // chantier H
        r.intro = MancheIntro.fromYaml(m.get("intro")); // chantier E: absent = no intro
        // Files written before v4 have no task: the round simply has none
        if (m.get("task") instanceof Map<?, ?>) r.task = Task.fromYaml(Maps.map(m.get("task")));
        return r;
    }

    /** Its id, its name or one of its nicknames, ignoring case. */
    public boolean matches(String s) {
        if (id.equalsIgnoreCase(s) || name.equalsIgnoreCase(s)) return true;
        for (String a : aliases) if (a.equalsIgnoreCase(s)) return true;
        return false;
    }
}
