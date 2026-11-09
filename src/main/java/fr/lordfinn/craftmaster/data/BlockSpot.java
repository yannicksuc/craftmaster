package fr.lordfinn.craftmaster.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** A block of the stage (a curtain block, the scenery anchor): dimension id and block coordinates. */
public record BlockSpot(String world, int x, int y, int z) {

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    static BlockSpot fromYaml(Map<String, Object> m) {
        if (m.get("world") == null) return null;
        return new BlockSpot(String.valueOf(m.get("world")), Maps.toInt(m.get("x")), Maps.toInt(m.get("y")), Maps.toInt(m.get("z")));
    }

    public String describe() {
        return x + " " + y + " " + z + " (" + world + ")";
    }
}
