package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STUB (chantier C) — to be REPLACED by chantier A's ShowContext (data model v2). Same signature.
 *
 * <p>Builds the show context the voice control sends to the local service, from the v1 data model: the latest
 * season is the active one, it has a single "Partie 1" whose manches are the season's tasks. The key names and
 * shapes are the contract documented in docs/design/emission.md (section 4, "Contexte"); A's version must keep
 * them (extra keys are fine, everything is optional for the service).
 */
public final class ShowContext {

    private ShowContext() {}

    public static Map<String, Object> build(MinecraftServer server, ServerPlayer admin) {
        CraftMasterData data = CraftMaster.data();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("version", 1);
        ctx.put("now", System.currentTimeMillis());
        ctx.put("idleMinutes", 4);
        if (data == null) return ctx;

        List<Integer> seasons = data.allSeasons();
        int active = seasons.isEmpty() ? -1 : seasons.getLast();
        ctx.put("activeSeason", active < 0 ? null : active);
        ctx.put("activePartie", 1);

        List<Map<String, Object>> seasonList = new ArrayList<>();
        for (int s : seasons) {
            List<Map<String, Object>> manches = new ArrayList<>();
            List<String> tasks = data.tasks(s);
            for (int i = 0; i < tasks.size(); i++) {
                manches.add(map("id", tasks.get(i), "name", tasks.get(i), "aliases", List.of(), "order", i + 1,
                        "onStage", false, "scene", null, "timer", 0, "archived", false));
            }
            Map<String, Object> partie = map("id", 1, "name", "Partie 1", "status", "en_cours", "archived", false, "manches", manches);
            seasonList.add(map("id", s, "name", "Saison " + s, "hidden", data.isHidden(s), "archived", false, "parties", List.of(partie)));
        }
        ctx.put("seasons", seasonList);

        // Players: everyone with a score in the active season, plus whoever is online
        Set<String> online = new LinkedHashSet<>();
        server.getPlayerList().getPlayers().forEach(p -> online.add(p.getPlainTextName()));
        Set<String> names = new LinkedHashSet<>(active < 0 ? List.of() : data.players(active));
        names.addAll(online);
        List<Map<String, Object>> players = new ArrayList<>();
        for (String n : names) players.add(map("pseudo", n, "aliases", List.of(), "online", online.contains(n), "archived", false));
        ctx.put("players", players);

        ctx.put("currentManche", ActionExecutor.stubCurrentManche);
        ctx.put("currentMancheStartedAt", ActionExecutor.stubCurrentStartedAt);
        ctx.put("lastScoreAt", ActionExecutor.stubLastScoreAt);
        ctx.put("lastScoreManche", ActionExecutor.stubLastScoreManche);

        Map<String, Map<String, Integer>> live = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        if (active >= 0) {
            for (String player : data.players(active)) {
                data.scores(active, player).forEach((task, v) -> live.computeIfAbsent(task, k -> new LinkedHashMap<>()).put(player, v));
                totals.put(player, data.total(active, player));
            }
        }
        // v1 has no publishing: what players see is the live score
        ctx.put("scores", map("live", live, "published", live));
        ctx.put("totals", map("live", totals, "published", totals));
        ctx.put("curtains", List.of("stage"));
        ctx.put("scenes", List.of());
        ctx.put("recentActions", List.copyOf(ActionExecutor.stubRecent));
        return ctx;
    }

    /** LinkedHashMap from key/value pairs (accepts null values, unlike Map.of). */
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
