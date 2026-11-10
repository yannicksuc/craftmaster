package fr.lordfinn.craftmaster.trial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a task prop remembers: how many times each player used it, who touched it first and when (and each
 * player's first time). Decides whether a use counts (once per player, cooldown). Pure: the clock is passed in.
 */
public final class PropStats {

    /** The answer to a use. {@code refused}: once-per-player or cooldown; otherwise counted. */
    public record Use(boolean refused, boolean firstEver, boolean firstForPlayer, int playerCount, int total) {}

    private final Map<String, Integer> clicks = new LinkedHashMap<>();
    private final Map<String, Long> firstAt = new LinkedHashMap<>();
    /** Last counted use per player (not saved: a cooldown does not survive a restart). */
    private final Map<String, Long> lastUse = new HashMap<>();
    private String first = "";
    private long firstTime;
    private int total;

    /** A player uses the prop at {@code now} (ms). */
    public Use use(String player, long now, boolean oncePerPlayer, long cooldownMs) {
        int count = clicks.getOrDefault(player, 0);
        if (oncePerPlayer && count > 0) return new Use(true, false, false, count, total);
        Long last = lastUse.get(player);
        if (last != null && now - last < cooldownMs) return new Use(true, false, false, count, total);
        lastUse.put(player, now);
        boolean firstEver = first.isEmpty();
        if (firstEver) {
            first = player;
            firstTime = now;
        }
        boolean firstForPlayer = count == 0;
        if (firstForPlayer) firstAt.put(player, now);
        clicks.put(player, count + 1);
        total++;
        return new Use(false, firstEver, firstForPlayer, count + 1, total);
    }

    public String first() { return first; }
    public long firstTime() { return firstTime; }
    public int total() { return total; }
    public Map<String, Integer> clicks() { return Map.copyOf(clicks); }
    public Map<String, Long> firstTimes() { return Map.copyOf(firstAt); }

    /** Players by their first use, earliest first. */
    public java.util.List<String> order() {
        return firstAt.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
    }

    public void reset() {
        clicks.clear();
        firstAt.clear();
        lastUse.clear();
        first = "";
        firstTime = 0;
        total = 0;
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("first", first);
        o.addProperty("firstTime", firstTime);
        o.addProperty("total", total);
        JsonObject c = new JsonObject();
        clicks.forEach(c::addProperty);
        o.add("clicks", c);
        JsonObject f = new JsonObject();
        firstAt.forEach(f::addProperty);
        o.add("firstAt", f);
        return o.toString();
    }

    public static PropStats fromJson(String json) {
        PropStats s = new PropStats();
        try {
            JsonObject o = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json).getAsJsonObject();
            s.first = o.has("first") ? o.get("first").getAsString() : "";
            s.firstTime = o.has("firstTime") ? o.get("firstTime").getAsLong() : 0;
            s.total = o.has("total") ? o.get("total").getAsInt() : 0;
            if (o.has("clicks")) o.getAsJsonObject("clicks").entrySet().forEach(e -> s.clicks.put(e.getKey(), e.getValue().getAsInt()));
            if (o.has("firstAt")) o.getAsJsonObject("firstAt").entrySet().forEach(e -> s.firstAt.put(e.getKey(), e.getValue().getAsLong()));
        } catch (RuntimeException e) {
            s.reset();
        }
        return s;
    }
}
