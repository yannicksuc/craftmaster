package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The context given to the voice control (docs/design/emission.md §4): everything it needs to turn a sentence into
 * actions, as plain maps, lists, strings, numbers and booleans (ready for JSON). Built on the server thread.
 */
public final class ShowContext {

    private ShowContext() {}

    /**
     * <pre>
     * activeSeason, activePartie       ids (null if none)
     * currentManche                    id of the round being played, or null
     * currentMancheInfo                {season, partie, id, name, number} or null
     * now, idleMinutes                 server time (ms) and the "next round after silence" threshold
     * lastScoreManche                  id of the round that got the last score
     * scores {live, published}         active game: {roundId|roundLabel: {pseudo: n}}
     * totals {live, published}         active season: {pseudo: total}
     * recentActions[]                  descriptions of the 10 last changes
     * lastScoreAt                      epoch ms (null if never), secondsSinceLastScore
     * timer                            {manche, remaining} or null;  revealRunning
     * seasons[]                        {id, name, hidden, archived, active, published, publishedAt,
     *                                   players[] {pseudo, aliases[], archived},
     *                                   parties[] {id, name, status, archived, active,
     *                                              manches[] {id, name, aliases[], order, number, onStage, scene,
     *                                                         timer, tpPoints, archived, status}},
     *                                   live[] / publishedRanking[] {rank, pseudo, total}, unpublished {pseudo: delta},
     *                                   scores {partieId: {mancheId: {pseudo: points}}}}
     * players[]                        cast of the active season {pseudo, aliases[], online}
     * onlinePlayers[]                  names
     * curtains[], scenes[], currentScene
     * history[]                        10 last changes {at, description}
     * you                              name of the organiser asking
     * </pre>
     * A round's status: "current", "played" (it has scores) or "todo".
     */
    public static Map<String, Object> build(MinecraftServer server, ServerPlayer player) {
        CraftMasterData data = CraftMaster.data();
        Map<String, Object> out = new LinkedHashMap<>();
        if (data == null) return out;
        Season active = data.activeSeason();
        Live live = data.live();
        Live.Current cur = live.current;
        Manche current = data.currentManche();

        out.put("activeSeason", active == null ? null : active.id);
        Partie activePartie = active == null ? null : active.activePartie();
        out.put("activePartie", activePartie == null ? null : activePartie.id);
        if (current != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("season", cur.season());
            c.put("partie", cur.partie());
            c.put("id", current.id);
            c.put("name", current.name);
            c.put("number", data.season(cur.season()).parties.get(cur.partie()).number(current));
            // The voice service reads the round by its id; the details stay available next to it
            out.put("currentManche", current.id);
            out.put("currentMancheInfo", c);
        } else {
            out.put("currentManche", null);
            out.put("currentMancheInfo", null);
        }
        out.put("now", System.currentTimeMillis());
        out.put("idleMinutes", IDLE_MINUTES);
        out.put("lastScoreManche", live.lastScoreManche);
        putScores(out, active, activePartie);
        out.put("lastScoreAt", live.lastScoreAt == 0 ? null : live.lastScoreAt);
        out.put("secondsSinceLastScore", live.lastScoreAt == 0 ? null : (System.currentTimeMillis() - live.lastScoreAt) / 1000);
        if (Show.timerRunning()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("manche", current == null ? null : current.name);
            t.put("remaining", Show.timerRemaining(server));
            out.put("timer", t);
        } else {
            out.put("timer", null);
        }
        out.put("revealRunning", Reveal.running());

        List<Object> seasons = new ArrayList<>();
        for (Season s : data.seasonMap().values()) seasons.add(season(s, s == active, cur));
        out.put("seasons", seasons);

        List<String> online = server.getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().name()).toList();
        List<Object> cast = new ArrayList<>();
        if (active != null) {
            for (SeasonPlayer p : active.players.values()) {
                if (p.archived) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pseudo", p.name);
                m.put("aliases", new ArrayList<>(p.aliases));
                m.put("online", online.stream().anyMatch(p.name::equalsIgnoreCase));
                cast.add(m);
            }
        }
        out.put("players", cast);
        out.put("onlinePlayers", new ArrayList<>(online));
        out.put("curtains", new ArrayList<>(data.stage().curtains.keySet()));
        out.put("scenes", new ArrayList<>(data.stage().scenes.keySet()));
        out.put("currentScene", live.scene);

        List<Object> history = new ArrayList<>();
        for (CraftMasterData.Change c : data.history()) {
            if (history.size() == 10) break;
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("at", c.at());
            h.put("description", c.description());
            history.add(h);
        }
        out.put("history", history);
        out.put("recentActions", history.stream().map(h -> ((Map<?, ?>) h).get("description")).toList());
        out.put("you", player == null ? null : player.getGameProfile().name());
        return out;
    }

    private static Map<String, Object> season(Season s, boolean active, Live.Current cur) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("hidden", s.hidden);
        m.put("archived", s.archived);
        m.put("active", active);
        m.put("published", s.published != null);
        m.put("publishedAt", s.published == null ? null : s.published.at());

        List<Object> players = new ArrayList<>();
        for (SeasonPlayer p : s.players.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("pseudo", p.name);
            pm.put("aliases", new ArrayList<>(p.aliases));
            pm.put("archived", p.archived);
            players.add(pm);
        }
        m.put("players", players);

        Partie activePartie = s.activePartie();
        List<Object> parties = new ArrayList<>();
        Map<String, Object> scores = new LinkedHashMap<>();
        for (Partie p : s.parties.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id);
            pm.put("name", p.name);
            pm.put("status", p.status);
            pm.put("archived", p.archived);
            pm.put("active", p == activePartie);
            List<Object> manches = new ArrayList<>();
            for (int i = 0; i < p.manches.size(); i++) {
                Manche r = p.manches.get(i);
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", r.id);
                rm.put("name", r.name);
                rm.put("aliases", new ArrayList<>(r.aliases));
                rm.put("order", i + 1);
                rm.put("number", r.archived ? null : p.number(r));
                rm.put("onStage", r.onStage);
                rm.put("scene", r.scene);
                rm.put("timer", r.timer);
                rm.put("tpPoints", r.tp.size());
                rm.put("archived", r.archived);
                boolean isCurrent = cur != null && cur.season() == s.id && cur.partie() == p.id && cur.manche().equals(r.id);
                rm.put("status", isCurrent ? "current" : p.scores.containsKey(r.id) ? "played" : "todo");
                manches.add(rm);
            }
            pm.put("manches", manches);
            parties.add(pm);
            Map<String, Object> per = new LinkedHashMap<>();
            p.scores.forEach((manche, byPlayer) -> per.put(manche, new LinkedHashMap<>(byPlayer)));
            scores.put(String.valueOf(p.id), per);
        }
        m.put("parties", parties);
        m.put("live", ranking(s.liveRanking()));
        m.put("publishedRanking", ranking(s.publishedRanking()));
        Map<String, Object> unpublished = new LinkedHashMap<>();
        for (String p : s.scoredPlayers()) {
            int d = s.unpublishedDelta(p);
            if (d != 0) unpublished.put(p, d);
        }
        m.put("unpublished", unpublished);
        m.put("scores", scores);
        return m;
    }

    /**
     * After this many minutes without a score, a score given without naming a round belongs to the next round
     * (rule applied by the voice service).
     */
    public static final int IDLE_MINUTES = 4;

    /**
     * Scores of the active game in the shape the voice service reads:
     * scores {live {roundId: {pseudo: n}}, published {roundLabel: {pseudo: n}}} and totals {live|published {pseudo: n}}.
     */
    private static void putScores(Map<String, Object> out, Season season, Partie partie) {
        Map<String, Object> live = new LinkedHashMap<>();
        if (partie != null) partie.scores.forEach((round, perPlayer) -> live.put(round, new LinkedHashMap<>(perPlayer)));
        Map<String, Map<String, Integer>> published = new LinkedHashMap<>();
        if (season != null && season.published != null)
            season.published.scores().forEach((player, perRound) ->
                    perRound.forEach((round, n) -> published.computeIfAbsent(round, k -> new LinkedHashMap<>()).put(player, n)));
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("live", live);
        scores.put("published", published);
        out.put("scores", scores);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("live", totals(season == null ? List.of() : season.liveRanking()));
        totals.put("published", totals(season == null ? List.of() : season.publishedRanking()));
        out.put("totals", totals);
    }

    private static Map<String, Integer> totals(List<CraftMasterData.Entry> entries) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (CraftMasterData.Entry e : entries) out.put(e.player(), e.total());
        return out;
    }

    private static List<Object> ranking(List<CraftMasterData.Entry> entries) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("rank", i + 1);
            e.put("pseudo", entries.get(i).player());
            e.put("total", entries.get(i).total());
            out.add(e);
        }
        return out;
    }
}
