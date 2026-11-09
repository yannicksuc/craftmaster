package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A season: its cast, its games ({@link Partie}) and the published scores. The total of a player is the sum of
 * their scores in every game, archived ones included.
 */
public final class Season {

    /** Scores as they were at the last "publish": what players, busts and labels show. */
    public record Published(long at, Map<String, Map<String, Integer>> scores) {

        public int total(String player) {
            int t = 0;
            for (int v : scores.getOrDefault(player, Map.of()).values()) t += v;
            return t;
        }
    }

    public final int id;
    public String name;
    /** Ranking shown to admins only, busts anonymous, until the reveal. */
    public boolean hidden;
    /** Put away: read-only, still listed in the rankings. */
    public boolean archived;
    /** Exact player name → cast member, in the order they joined (ties in the ranking keep this order). */
    public final Map<String, SeasonPlayer> players = new LinkedHashMap<>();
    public final TreeMap<Integer, Partie> parties = new TreeMap<>();
    /** Game the commands act on when they do not name one. */
    public Integer activePartie;
    /** Null until the first publish. */
    public Published published;

    public Season(int id, String name) {
        this.id = id;
        this.name = name;
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        Maps.putIf(m, "hidden", hidden);
        Maps.putIf(m, "archived", archived);
        if (activePartie != null) m.put("activePartie", activePartie);
        Map<String, Object> ps = new LinkedHashMap<>();
        players.forEach((n, p) -> ps.put(n, p.toYaml()));
        m.put("players", ps);
        Map<String, Object> parts = new LinkedHashMap<>();
        parties.forEach((id, p) -> parts.put(String.valueOf(id), p.toYaml()));
        m.put("parties", parts);
        if (published != null) {
            Map<String, Object> pub = new LinkedHashMap<>();
            pub.put("at", published.at());
            Map<String, Object> sc = new LinkedHashMap<>();
            published.scores().forEach((player, per) -> sc.put(player, new LinkedHashMap<>(per)));
            pub.put("scores", sc);
            m.put("published", pub);
        }
        return m;
    }

    static Season fromYaml(int id, Map<String, Object> m) {
        Season s = new Season(id, Maps.str(m.get("name"), "Saison " + id));
        s.hidden = Maps.bool(m.get("hidden"));
        s.archived = Maps.bool(m.get("archived"));
        Maps.map(m, "players").forEach((n, p) -> s.players.put(n, SeasonPlayer.fromYaml(n, Maps.map(p))));
        Maps.map(m, "parties").forEach((k, p) -> {
            Integer pid = Maps.intKey(k);
            if (pid != null) s.parties.put(pid, Partie.fromYaml(pid, Maps.map(p)));
        });
        if (m.get("activePartie") != null) s.activePartie = Maps.toInt(m.get("activePartie"));
        if (m.get("published") instanceof Map<?, ?> pub) {
            Map<String, Map<String, Integer>> sc = new LinkedHashMap<>();
            Maps.map(Maps.map(pub), "scores").forEach((player, per) -> {
                Map<String, Integer> out = new LinkedHashMap<>();
                Maps.map(per).forEach((label, v) -> out.put(label, Maps.toInt(v)));
                sc.put(player, out);
            });
            s.published = new Published(Maps.toLong(Maps.map(pub).get("at")), sc);
        }
        return s;
    }

    // ---------------------------------------------------------------- games and rounds

    /** The active game if it still exists, else the last one not archived, else null. */
    public Partie activePartie() {
        Partie p = activePartie == null ? null : parties.get(activePartie);
        if (p != null) return p;
        for (Partie q : parties.descendingMap().values()) if (!q.archived) return q;
        return null;
    }

    /** The active game, created as "Partie 1" when the season has none: nobody has to think about games. */
    public Partie activePartieOrCreate() {
        Partie p = activePartie();
        if (p != null) return p;
        int id = parties.isEmpty() ? 1 : parties.lastKey() + 1;
        p = new Partie(id, "Partie " + id);
        parties.put(id, p);
        activePartie = id;
        return p;
    }

    /** A new round id, unique in the whole season. */
    public String newMancheId(String name) {
        String base = Partie.slug(name), id = base;
        for (int i = 2; mancheById(id) != null; i++) id = base + "-" + i;
        return id;
    }

    public Manche mancheById(String id) {
        for (Partie p : parties.values()) {
            Manche r = p.manche(id);
            if (r != null) return r;
        }
        return null;
    }

    // ---------------------------------------------------------------- cast

    public SeasonPlayer player(String nameOrAlias) {
        SeasonPlayer exact = players.get(nameOrAlias);
        if (exact != null) return exact;
        for (SeasonPlayer p : players.values()) if (p.name.equalsIgnoreCase(nameOrAlias)) return p;
        for (SeasonPlayer p : players.values()) if (p.matches(nameOrAlias)) return p;
        return null;
    }

    // ---------------------------------------------------------------- live scores

    /** Players with at least one live score, cast order first. */
    public List<String> scoredPlayers() {
        java.util.LinkedHashSet<String> scored = new java.util.LinkedHashSet<>();
        for (Partie p : parties.values()) for (Map<String, Integer> per : p.scores.values()) scored.addAll(per.keySet());
        List<String> out = new ArrayList<>();
        for (String n : players.keySet()) if (scored.remove(n)) out.add(n);
        out.addAll(scored);
        return out;
    }

    public int total(String player) {
        int t = 0;
        for (Partie p : parties.values()) for (Map<String, Integer> per : p.scores.values()) t += per.getOrDefault(player, 0);
        return t;
    }

    /** Live ranking: highest total first, ties in cast order. */
    public List<CraftMasterData.Entry> liveRanking() {
        List<CraftMasterData.Entry> out = new ArrayList<>();
        for (String p : scoredPlayers()) out.add(new CraftMasterData.Entry(p, total(p)));
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return out;
    }

    /** Published ranking (empty before the first publish). */
    public List<CraftMasterData.Entry> publishedRanking() {
        List<CraftMasterData.Entry> out = new ArrayList<>();
        if (published == null) return out;
        for (String p : published.scores().keySet()) out.add(new CraftMasterData.Entry(p, published.total(p)));
        out.sort((a, b) -> Integer.compare(b.total(), a.total()));
        return out;
    }

    public List<CraftMasterData.Entry> ranking(boolean publishedScores) {
        return publishedScores ? publishedRanking() : liveRanking();
    }

    /**
     * Each player's live scores labelled by round name (prefixed by the game when the season has several),
     * rounds in order, archived and unknown rounds last. Used for publishing and for the ranking screen.
     */
    public Map<String, Map<String, Integer>> labelledScores() {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (String player : scoredPlayers()) out.put(player, new LinkedHashMap<>());
        boolean several = parties.size() > 1;
        for (Partie p : parties.values()) {
            List<String> ids = new ArrayList<>();
            for (Manche r : p.manches) ids.add(r.id);
            for (String id : p.scores.keySet()) if (!ids.contains(id)) ids.add(id);
            for (String id : ids) {
                Map<String, Integer> per = p.scores.get(id);
                if (per == null) continue;
                Manche r = p.manche(id);
                String label = (several ? p.name + " · " : "") + (r == null ? id : r.name);
                per.forEach((player, v) -> out.get(player).merge(label, v, Integer::sum));
            }
        }
        return out;
    }

    /** Labels of the rounds still listed, in order, as in {@link #labelledScores()}. */
    public List<String> activeLabels() {
        List<String> out = new ArrayList<>();
        boolean several = parties.size() > 1;
        for (Partie p : parties.values()) {
            if (p.archived) continue;
            for (Manche r : p.activeManches()) out.add((several ? p.name + " · " : "") + r.name);
        }
        return out;
    }

    /** Copies the live scores to the published ones. */
    public void publish(long now) {
        published = new Published(now, labelledScores());
    }

    /** Live total minus published total (the whole live total before the first publish). */
    public int unpublishedDelta(String player) {
        return total(player) - (published == null ? 0 : published.total(player));
    }
}
