package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player of a season's cast: exact Minecraft name, nicknames the voice control understands, and whether they
 * were archived (kept in the ranking for their points, but no longer teleported nor offered to the voice control).
 */
public final class SeasonPlayer {

    public String name;
    public final List<String> aliases = new ArrayList<>();
    public boolean archived;

    public SeasonPlayer(String name) {
        this.name = name;
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        Maps.putIf(m, "aliases", new ArrayList<>(aliases));
        Maps.putIf(m, "archived", archived);
        return m;
    }

    static SeasonPlayer fromYaml(String name, Map<String, Object> m) {
        SeasonPlayer p = new SeasonPlayer(name);
        p.aliases.addAll(Maps.strings(m.get("aliases")));
        p.archived = Maps.bool(m.get("archived"));
        return p;
    }

    /** Same name or one of the nicknames, ignoring case. */
    public boolean matches(String s) {
        if (name.equalsIgnoreCase(s)) return true;
        for (String a : aliases) if (a.equalsIgnoreCase(s)) return true;
        return false;
    }
}
