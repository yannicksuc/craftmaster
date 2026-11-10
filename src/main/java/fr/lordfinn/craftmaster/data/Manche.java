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
    /** Where the season's players are teleported when it starts (shared out in turn). */
    public final List<Spot> tp = new ArrayList<>();
    /** Put away: no longer listed nor "next", its points still count. */
    public boolean archived;
    // ---- chantier H: the judge's results sheet of this round (times, counts... → proposed points), undoable
    public fr.lordfinn.craftmaster.trial.ResultSheet results = new fr.lordfinn.craftmaster.trial.ResultSheet();

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
        List<Object> spots = new ArrayList<>();
        for (Spot s : tp) spots.add(s.toYaml());
        Maps.putIf(m, "tp", spots);
        Maps.putIf(m, "archived", archived);
        if (!results.isEmpty()) m.put("results", results.toYaml()); // chantier H
        return m;
    }

    static Manche fromYaml(Map<String, Object> m) {
        String name = Maps.str(m.get("name"), Maps.str(m.get("id"), "?"));
        Manche r = new Manche(Maps.str(m.get("id"), Partie.slug(name)), name);
        r.aliases.addAll(Maps.strings(m.get("aliases")));
        r.onStage = Maps.bool(m.get("onStage"));
        r.scene = m.get("scene") == null ? null : String.valueOf(m.get("scene"));
        r.timer = Maps.toInt(m.get("timer"));
        for (Object o : Maps.list(m.get("tp"))) {
            Spot s = Spot.fromYaml(Maps.map(o));
            if (s != null) r.tp.add(s);
        }
        r.archived = Maps.bool(m.get("archived"));
        r.results = fr.lordfinn.craftmaster.trial.ResultSheet.fromYaml(m.get("results")); // chantier H
        return r;
    }

    /** Its id, its name or one of its nicknames, ignoring case. */
    public boolean matches(String s) {
        if (id.equalsIgnoreCase(s) || name.equalsIgnoreCase(s)) return true;
        for (String a : aliases) if (a.equalsIgnoreCase(s)) return true;
        return false;
    }
}
