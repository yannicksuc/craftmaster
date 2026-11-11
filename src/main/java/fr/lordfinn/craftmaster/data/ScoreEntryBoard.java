package fr.lordfinn.craftmaster.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.trial.Scale;

import java.util.List;
import java.util.Map;

/**
 * What the referee's score entry screen shows (pure, no server): the rounds of the season's active game × its
 * players, with the live scores, the scale of each round (points of the places when nobody is scored yet) and how
 * many changes can be undone / redone.
 *
 * <pre>
 * {season, name, seasons[], partie {id, name}, players[], current (round id or null), undo, redo,
 *  manches[ {id, name, number, current, scale[], scores {player: n}} ]}
 * </pre>
 */
public final class ScoreEntryBoard {

    /** The screen shows at most this many players (8 heads fit side by side). */
    public static final int MAX_PLAYERS = 8;

    private ScoreEntryBoard() {}

    /** The board of a season (null if it does not exist). */
    public static JsonObject build(CraftMasterData data, int seasonId) {
        Season s = data.season(seasonId);
        if (s == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("season", s.id);
        o.addProperty("name", s.name);
        JsonArray seasons = new JsonArray();
        for (int id : data.allSeasons()) seasons.add(id);
        o.add("seasons", seasons);
        o.addProperty("undo", data.history().size());
        o.addProperty("redo", data.redoSize());
        // What Undo / Redo would do, shown on their buttons ("↶ Annuler : Château — 5 scores")
        if (!data.history().isEmpty()) {
            o.addProperty("undoText", data.history().getFirst().description());
            o.addProperty("undoAt", data.history().getFirst().at());
        }
        if (data.nextRedo() != null) o.addProperty("redoText", data.nextRedo().description());

        JsonArray players = new JsonArray();
        List<String> names = s.players.values().stream().filter(p -> !p.archived).map(p -> p.name).toList();
        for (String p : names) players.add(p);
        o.add("players", players);

        Partie partie = s.activePartie();
        Manche current = data.currentManche();
        o.addProperty("current", current == null ? null : current.id);
        JsonArray manches = new JsonArray();
        if (partie != null) {
            JsonObject po = new JsonObject();
            po.addProperty("id", partie.id);
            po.addProperty("name", partie.name);
            o.add("partie", po);
            for (Manche m : partie.activeManches()) {
                JsonObject mo = new JsonObject();
                mo.addProperty("id", m.id);
                mo.addProperty("name", m.name);
                mo.addProperty("number", partie.number(m));
                mo.addProperty("current", current == m);
                JsonArray scale = new JsonArray();
                for (int p : Scale.parse(m.results.scaleOrDefault())) scale.add(p);
                mo.add("scale", scale);
                JsonObject scores = new JsonObject();
                for (Map.Entry<String, Integer> e : partie.scores.getOrDefault(m.id, Map.of()).entrySet())
                    scores.addProperty(e.getKey(), e.getValue());
                mo.add("scores", scores);
                manches.add(mo);
            }
        }
        o.add("manches", manches);
        return o;
    }
}
