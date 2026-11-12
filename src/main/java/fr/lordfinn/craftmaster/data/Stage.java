package fr.lordfinn.craftmaster.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** The show's set: named curtains, the scenery anchor, the saved sceneries and the reveal timing. */
public final class Stage {

    /** Name of the main curtain, used when an action names none. */
    public static final String MAIN_CURTAIN = "stage";

    /** Size of a saved scenery (its blocks are a vanilla structure file, see show.Scenes). */
    public record Scene(int sizeX, int sizeY, int sizeZ) {}

    /** Durations of the staged reveal, in seconds. */
    public record RevealTiming(double curtain, double drum, double step, double podium) {
        public static final RevealTiming DEFAULT = new RevealTiming(3, 4, 3, 5);
    }

    /** Curtain name → one of its blocks. */
    public final Map<String, BlockSpot> curtains = new LinkedHashMap<>();
    /** Where sceneries are placed (their lowest north-west corner). */
    public BlockSpot anchor;
    public final Map<String, Scene> scenes = new LinkedHashMap<>();
    public RevealTiming reveal = RevealTiming.DEFAULT;
    /** How the buzzers play (trial.Buzzers.Mode id: first, order, auto, free) and the automatic re-arm delay (s). */
    public String buzzerMode = "first";
    public int buzzerRearm = 5;

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> c = new LinkedHashMap<>();
        curtains.forEach((n, b) -> c.put(n, b.toYaml()));
        m.put("curtains", c);
        if (anchor != null) m.put("anchor", anchor.toYaml());
        Map<String, Object> s = new LinkedHashMap<>();
        scenes.forEach((n, sc) -> s.put(n, new LinkedHashMap<>(Map.of("x", sc.sizeX(), "y", sc.sizeY(), "z", sc.sizeZ()))));
        m.put("scenes", s);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("curtain", reveal.curtain());
        r.put("drum", reveal.drum());
        r.put("step", reveal.step());
        r.put("podium", reveal.podium());
        m.put("reveal", r);
        m.put("buzzers", new LinkedHashMap<>(Map.of("mode", buzzerMode, "rearm", buzzerRearm)));
        return m;
    }

    static Stage fromYaml(Map<String, Object> m) {
        Stage st = new Stage();
        Maps.map(m, "curtains").forEach((n, b) -> {
            BlockSpot spot = BlockSpot.fromYaml(Maps.map(b));
            if (spot != null) st.curtains.put(n, spot);
        });
        st.anchor = BlockSpot.fromYaml(Maps.map(m, "anchor"));
        Maps.map(m, "scenes").forEach((n, sc) -> {
            Map<String, Object> size = Maps.map(sc);
            st.scenes.put(n, new Scene(Maps.toInt(size.get("x")), Maps.toInt(size.get("y")), Maps.toInt(size.get("z"))));
        });
        Map<String, Object> r = Maps.map(m, "reveal");
        if (!r.isEmpty()) {
            RevealTiming d = RevealTiming.DEFAULT;
            st.reveal = new RevealTiming(
                    r.containsKey("curtain") ? Maps.toDouble(r.get("curtain")) : d.curtain(),
                    r.containsKey("drum") ? Maps.toDouble(r.get("drum")) : d.drum(),
                    r.containsKey("step") ? Maps.toDouble(r.get("step")) : d.step(),
                    r.containsKey("podium") ? Maps.toDouble(r.get("podium")) : d.podium());
        }
        Map<String, Object> bz = Maps.map(m, "buzzers");
        if (bz.get("mode") instanceof String mode) st.buzzerMode = mode;
        if (bz.containsKey("rearm")) st.buzzerRearm = Math.max(1, Maps.toInt(bz.get("rearm")));
        return st;
    }
}
