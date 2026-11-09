package fr.lordfinn.craftmaster.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A game (one evening) of a season: its rounds in order and the live scores, round id → player → points.
 * Most seasons have a single one, "Partie 1", created automatically.
 */
public final class Partie {

    public static final String PREPARATION = "preparation", EN_COURS = "en_cours", TERMINEE = "terminee";
    public static final List<String> STATUSES = List.of(PREPARATION, EN_COURS, TERMINEE);

    public final int id;
    public String name;
    public String status = PREPARATION;
    public boolean archived;
    public final List<Manche> manches = new ArrayList<>();
    /** Round id → player → points (rounds that no longer exist keep their points too). */
    public final Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();

    public Partie(int id, String name) {
        this.id = id;
        this.name = name;
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("status", status);
        Maps.putIf(m, "archived", archived);
        List<Object> list = new ArrayList<>();
        for (Manche r : manches) list.add(r.toYaml());
        m.put("manches", list);
        Map<String, Object> sc = new LinkedHashMap<>();
        scores.forEach((manche, perPlayer) -> sc.put(manche, new LinkedHashMap<>(perPlayer)));
        m.put("scores", sc);
        return m;
    }

    static Partie fromYaml(int id, Map<String, Object> m) {
        Partie p = new Partie(id, Maps.str(m.get("name"), "Partie " + id));
        p.status = normalizeStatus(Maps.str(m.get("status"), PREPARATION));
        if (p.status == null) p.status = PREPARATION;
        p.archived = Maps.bool(m.get("archived"));
        for (Object o : Maps.list(m.get("manches"))) p.manches.add(Manche.fromYaml(Maps.map(o)));
        Maps.map(m, "scores").forEach((manche, perPlayer) -> {
            Map<String, Integer> out = new LinkedHashMap<>();
            Maps.map(perPlayer).forEach((player, v) -> out.put(player, Maps.toInt(v)));
            p.scores.put(manche, out);
        });
        return p;
    }

    /** "terminée", "Terminee", "en cours"... → the stored value, or null if it is not a status. */
    public static String normalizeStatus(String s) {
        String n = slug(s).replace('-', '_');
        return STATUSES.contains(n) ? n : null;
    }

    public Manche manche(String id) {
        for (Manche r : manches) if (r.id.equals(id)) return r;
        return null;
    }

    /** Rounds still listed (not archived), in order. */
    public List<Manche> activeManches() {
        return manches.stream().filter(r -> !r.archived).toList();
    }

    /** 1-based number of the round among the listed ones (0 if archived). */
    public int number(Manche r) {
        return activeManches().indexOf(r) + 1;
    }

    public int score(String mancheId, String player) {
        Map<String, Integer> m = scores.get(mancheId);
        Integer v = m == null ? null : m.get(player);
        return v == null ? 0 : v;
    }

    public boolean hasScore(String mancheId, String player) {
        Map<String, Integer> m = scores.get(mancheId);
        return m != null && m.containsKey(player);
    }

    public void setScore(String mancheId, String player, int value) {
        scores.computeIfAbsent(mancheId, k -> new LinkedHashMap<>()).put(player, value);
    }

    /** @return false if there was no such score */
    public boolean removeScore(String mancheId, String player) {
        Map<String, Integer> m = scores.get(mancheId);
        if (m == null || m.remove(player) == null) return false;
        if (m.isEmpty()) scores.remove(mancheId);
        return true;
    }

    /** "Le Pont !" → "le-pont" (lower case, no accents, dashes): ids of rounds. */
    public static String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "manche" : n;
    }
}
