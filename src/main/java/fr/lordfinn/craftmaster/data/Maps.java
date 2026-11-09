package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers to read the YAML tree (maps of maps) loosely: wrong types read as empty / zero instead of failing. */
final class Maps {

    private Maps() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    static Map<String, Object> map(Map<String, Object> parent, String key) {
        return map(parent.get(key));
    }

    static List<Object> list(Object v) {
        return v instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>();
    }

    static List<String> strings(Object v) {
        List<String> out = new ArrayList<>();
        for (Object o : list(v)) if (o != null) out.add(String.valueOf(o));
        return out;
    }

    static String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    static boolean bool(Object v) {
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v == null) return 0;
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0;
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }

    static Integer intKey(String k) {
        try { return Integer.parseInt(k.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** YAML may load "1:" as an Integer key; the file always uses string keys. */
    static Map<String, Object> normalize(Map<?, ?> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(String.valueOf(k), normalizeValue(v)));
        return out;
    }

    private static Object normalizeValue(Object v) {
        if (v instanceof Map<?, ?> m) return normalize(m);
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(normalizeValue(o));
            return out;
        }
        return v;
    }

    /** Puts the value unless it is a default (false, 0, null, empty), to keep the file short. */
    static void putIf(Map<String, Object> m, String key, Object v) {
        if (v == null || Boolean.FALSE.equals(v) || (v instanceof Number n && n.doubleValue() == 0)
                || (v instanceof List<?> l && l.isEmpty()) || (v instanceof Map<?, ?> mm && mm.isEmpty())) return;
        m.put(key, v);
    }
}
