package fr.lordfinn.craftmaster.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Turns a version 1 file (the Paper plugin's config.yml, or the mod 2.0's, which is the same) into version 2:
 * the tasks of a season become the rounds of its "Partie 1", scores follow (scores of tasks no longer listed become
 * archived rounds, so totals do not change), busts and hidden seasons are kept, and the current scores are
 * published so players, busts and labels show exactly what they showed before.
 */
final class Migration {

    private Migration() {}

    static Map<String, Object> v1ToV2(Map<String, Object> v1, long now) {
        Map<String, Object> seasonsV1 = Maps.map(v1, "seasons");
        Map<String, Object> scoresV1 = Maps.map(v1, "scores");
        TreeSet<Integer> ids = new TreeSet<>();
        for (String k : seasonsV1.keySet()) if (Maps.intKey(k) != null) ids.add(Maps.intKey(k));
        for (String k : scoresV1.keySet()) if (Maps.intKey(k) != null) ids.add(Maps.intKey(k));

        Map<String, Object> seasons = new LinkedHashMap<>();
        for (int id : ids) {
            Map<String, Object> sv1 = Maps.map(seasonsV1, String.valueOf(id));
            Season s = new Season(id, "Saison " + id);
            s.hidden = Maps.bool(sv1.get("hidden"));
            Partie p = s.activePartieOrCreate();
            for (String task : Maps.strings(sv1.get("tasks")))
                if (find(p, task) == null) p.manches.add(new Manche(s.newMancheId(task), task));
            Maps.map(scoresV1, String.valueOf(id)).forEach((player, perTask) -> {
                s.players.computeIfAbsent(player, SeasonPlayer::new);
                Maps.map(perTask).forEach((task, v) -> {
                    Manche r = find(p, task);
                    if (r == null) {
                        // A task removed from the list but still counting in the totals, as in the plugin
                        r = new Manche(s.newMancheId(task), task);
                        r.archived = true;
                        p.manches.add(r);
                    }
                    p.setScore(r.id, player, Maps.toInt(v));
                });
            });
            s.publish(now);
            seasons.put(String.valueOf(id), s.toYaml());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", CraftMasterData.VERSION);
        out.put("seasons", seasons);
        out.put("busts", v1.getOrDefault("busts", new LinkedHashMap<>()));
        if (!ids.isEmpty()) out.put("live", new LinkedHashMap<>(Map.of("season", ids.last())));
        return out;
    }

    private static Manche find(Partie p, String name) {
        for (Manche r : p.manches) if (r.name.equals(name)) return r;
        return null;
    }

    /** True for a file written before version 2 (no "version" key). */
    static boolean isV1(Map<String, Object> root) {
        return Maps.toInt(root.get("version")) < 2 && !root.isEmpty();
    }
}
