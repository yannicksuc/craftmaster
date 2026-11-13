package fr.lordfinn.craftmaster.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** State of the show on this server: active season, round being played, time of the last score, scenery placed. */
public final class Live {

    /** The round being played: season id, game id, round id. */
    public record Current(int season, int partie, String manche) {}

    public Integer season;
    public Current current;
    /** The last round ended, so that "next" goes on after it once nothing is being played. */
    public Current ended;
    /** Epoch milliseconds of the last score given, 0 = never. */
    public long lastScoreAt;
    /** Id of the round that received the last score (the voice control uses it to guess the current round). */
    public String lastScoreManche;
    /** Last scenery placed at the anchor. */
    public String scene;

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (season != null) m.put("season", season);
        if (current != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("season", current.season());
            c.put("partie", current.partie());
            c.put("manche", current.manche());
            m.put("manche", c);
        }
        if (ended != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("season", ended.season());
            c.put("partie", ended.partie());
            c.put("manche", ended.manche());
            m.put("ended", c);
        }
        Maps.putIf(m, "lastScoreAt", lastScoreAt);
        if (lastScoreManche != null) m.put("lastScoreManche", lastScoreManche);
        Maps.putIf(m, "scene", scene);
        return m;
    }

    static Live fromYaml(Map<String, Object> m) {
        Live l = new Live();
        if (m.get("season") != null) l.season = Maps.toInt(m.get("season"));
        Map<String, Object> c = Maps.map(m, "manche");
        if (c.get("manche") != null)
            l.current = new Current(Maps.toInt(c.get("season")), Maps.toInt(c.get("partie")), String.valueOf(c.get("manche")));
        Map<String, Object> e = Maps.map(m, "ended");
        if (e.get("manche") != null)
            l.ended = new Current(Maps.toInt(e.get("season")), Maps.toInt(e.get("partie")), String.valueOf(e.get("manche")));
        l.lastScoreAt = Maps.toLong(m.get("lastScoreAt"));
        l.lastScoreManche = m.get("lastScoreManche") == null ? null : String.valueOf(m.get("lastScoreManche"));
        l.scene = m.get("scene") == null ? null : String.valueOf(m.get("scene"));
        return l;
    }
}
