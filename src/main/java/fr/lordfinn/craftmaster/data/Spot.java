package fr.lordfinn.craftmaster.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** A position players are teleported to (a round's TP point): dimension id, coordinates and facing. */
public record Spot(String world, double x, double y, double z, float yaw, float pitch) {

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("yaw", (double) yaw);
        m.put("pitch", (double) pitch);
        return m;
    }

    static Spot fromYaml(Map<String, Object> m) {
        if (m.get("world") == null) return null;
        return new Spot(String.valueOf(m.get("world")), Maps.toDouble(m.get("x")), Maps.toDouble(m.get("y")),
                Maps.toDouble(m.get("z")), (float) Maps.toDouble(m.get("yaw")), (float) Maps.toDouble(m.get("pitch")));
    }

    /** "12.5 64 -3 (minecraft:overworld)" */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f (%s)", x, y, z, world);
    }
}
