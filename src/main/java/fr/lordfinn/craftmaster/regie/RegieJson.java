package fr.lordfinn.craftmaster.regie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON for the voice control: the show context (server → client → local service) and the actions (service →
 * client → server). Actions come back as plain maps, numbers as Integer when they fit (Long otherwise, Double
 * when not whole), so {@code ActionExecutor} can read {@code ((Number) a.get("points")).intValue()} safely.
 */
public final class RegieJson {

    private RegieJson() {}

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    public static String write(Object value) {
        return GSON.toJson(value);
    }

    /**
     * Parses a JSON list of action objects. Anything that is not an object with a text "type" is dropped.
     *
     * @throws IllegalArgumentException if the text is not a JSON list
     */
    public static List<Map<String, Object>> readActions(String json) {
        List<Object> raw;
        try {
            raw = GSON.fromJson(json, new TypeToken<List<Object>>() {}.getType());
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("not a JSON list: " + e.getMessage(), e);
        }
        if (raw == null) throw new IllegalArgumentException("empty");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : raw) {
            if (normalize(o) instanceof Map<?, ?> m && m.get("type") instanceof String) {
                @SuppressWarnings("unchecked") Map<String, Object> action = (Map<String, Object>) m;
                out.add(action);
            }
        }
        return out;
    }

    /**
     * The JSON list without its "say" actions: on the client, what Claude says is shown from the service's reply,
     * the server has nothing to run for it. Not a list → "[]".
     */
    public static String withoutSay(String json) {
        List<Object> raw;
        try {
            raw = GSON.fromJson(json, new TypeToken<List<Object>>() {}.getType());
        } catch (JsonParseException e) {
            return "[]";
        }
        if (raw == null) return "[]";
        List<Object> kept = new ArrayList<>();
        for (Object o : raw) if (!(o instanceof Map<?, ?> m && "say".equals(m.get("type")))) kept.add(normalize(o));
        return write(kept);
    }

    /** Actions this game runs itself (the in-game clip records this game's view): "clip.start", "clip.stop". */
    public static final List<String> LOCAL = List.of("clip.start", "clip.stop");

    /** The local actions' types of a list, in order (see {@link #LOCAL}). */
    public static List<String> localTypes(String json) {
        List<String> out = new ArrayList<>();
        try {
            List<Object> raw = GSON.fromJson(json, new TypeToken<List<Object>>() {}.getType());
            if (raw != null) for (Object o : raw)
                if (o instanceof Map<?, ?> m && LOCAL.contains(String.valueOf(m.get("type")))) out.add(String.valueOf(m.get("type")));
        } catch (JsonParseException e) {
            // nothing local
        }
        return out;
    }

    /** The list without the local actions (sent to the server). */
    public static String withoutLocal(String json) {
        List<Object> raw;
        try {
            raw = GSON.fromJson(json, new TypeToken<List<Object>>() {}.getType());
        } catch (JsonParseException e) {
            return "[]";
        }
        if (raw == null) return "[]";
        List<Object> kept = new ArrayList<>();
        for (Object o : raw) if (!(o instanceof Map<?, ?> m && LOCAL.contains(String.valueOf(m.get("type"))))) kept.add(normalize(o));
        return write(kept);
    }

    /** Map / List / number normalization, recursively (Long that fits → Integer). */
    static Object normalize(Object o) {
        if (o instanceof Long l) return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) l.intValue() : l;
        if (o instanceof Double d && d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE) return d.intValue();
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), normalize(v)));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object v : l) out.add(normalize(v));
            return out;
        }
        return o;
    }
}
