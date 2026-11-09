package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.data.Spot;
import fr.lordfinn.craftmaster.data.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interprets a list of actions (the schema of docs/design/emission.md §4) against the data, without a server: the
 * data part is applied as one undoable change, and what must happen in the world (titles, teleports, curtains...)
 * is returned as {@link Effect}s for {@link ActionExecutor} to play. If any action is invalid, nothing is changed.
 *
 * <p>An action is a map with its kind in {@code "type"} (or {@code "action"}) and its fields. Names arrive already
 * resolved (exact pseudo, round id / name / nickname, or "current" / "next"); {@code season} and {@code partie}
 * default to the active ones.
 */
public final class Actions {

    private Actions() {}

    /** What the world must show after the data changed. */
    public sealed interface Effect {}

    /** Points given live: announced in everyone's action bar ({@code delta} may be negative). */
    public record ScoreGiven(int season, String player, int delta, String manche) implements Effect {}

    /** A round starts: title, teleports, timer. {@code cast}: the season's players (not archived), in order. */
    public record MancheStarted(int season, int number, String name, int timer, List<Spot> tp, List<String> cast) implements Effect {}

    public record MancheEnded(String name) implements Effect {}

    public record TimerStopped() implements Effect {}

    public record Published(int season) implements Effect {}

    public record RevealStarted(int season) implements Effect {}

    public record RevealStopped() implements Effect {}

    public record CurtainMoved(String name, boolean open) implements Effect {}

    public record ScenePlaced(String name) implements Effect {}

    /** The result: one French line per action (or the error), the effects to play, and whether it worked. */
    public record Outcome(List<String> lines, List<Effect> effects, boolean ok, boolean changed) {}

    /** An invalid action: its message is shown as is. */
    public static final class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }

    public static final List<String> TYPES = List.of(
            "score.set", "score.add", "score.remove", "scores.publish", "reveal.start", "reveal.stop", "undo",
            "player.add", "player.update", "player.remove",
            "season.create", "season.update", "season.archive", "season.delete", "season.activate",
            "partie.create", "partie.update", "partie.archive", "partie.delete", "partie.activate",
            "manche.create", "manche.update", "manche.archive", "manche.delete", "manche.tp.add", "manche.tp.clear",
            "manche.start", "manche.end", "timer.stop", "curtain.open", "curtain.close", "scene.set", "say");

    /**
     * Applies the actions. {@code undo} is only accepted as the first action (it is done, then the rest is applied
     * as a new change). Everything else is one change of the undo journal.
     */
    public static Outcome apply(CraftMasterData data, List<Map<String, Object>> actions) {
        List<String> lines = new ArrayList<>();
        List<Effect> effects = new ArrayList<>();
        if (actions == null || actions.isEmpty()) return new Outcome(List.of("Aucune action."), effects, true, false);
        int start = 0;
        boolean changed = false;
        if ("undo".equals(type(actions.getFirst()))) {
            int count = Math.max(1, intOr(actions.getFirst(), "count", 1));
            List<String> undone = data.undo(count);
            if (undone.isEmpty()) lines.add("Rien à annuler.");
            for (String d : undone) lines.add("Annulé : " + d);
            changed = !undone.isEmpty();
            start = 1;
        }
        int kept = lines.size();
        Run run = new Run(data, lines, effects);
        data.begin();
        try {
            for (int i = start; i < actions.size(); i++) {
                Map<String, Object> a = actions.get(i);
                String type = type(a);
                try {
                    run.apply(type, a == null ? Map.of() : a);
                } catch (Invalid e) {
                    throw new Invalid((actions.size() > 1 ? "Action " + (i + 1) + " (" + type + ") : " : "") + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            data.rollback();
            if (!(e instanceof Invalid)) throw e;
            List<String> out = new ArrayList<>(lines.subList(0, kept));
            out.add(e.getMessage());
            if (actions.size() - start > 1) out.add("Rien n'a été modifié.");
            return new Outcome(out, List.of(), false, changed);
        }
        List<CraftMasterData.Change> history = data.history();
        CraftMasterData.Change top = history.isEmpty() ? null : history.getFirst();
        data.commit(describe(lines.subList(kept, lines.size())));
        history = data.history();
        boolean committed = !history.isEmpty() && history.getFirst() != top;
        return new Outcome(lines, effects, true, changed || committed);
    }

    /** 180 → "3:00". */
    public static String clock(int seconds) {
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private static String describe(List<String> lines) {
        String d = String.join(" ; ", lines);
        return d.length() > 160 ? d.substring(0, 157) + "…" : d;
    }

    static String type(Map<String, Object> a) {
        if (a == null) return "?";
        Object t = a.containsKey("type") ? a.get("type") : a.get("action");
        return t == null ? "?" : String.valueOf(t).trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- field readers

    private static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String required(Map<String, Object> a, String key) {
        String s = str(a, key);
        if (s == null) throw new Invalid("champ « " + key + " » manquant.");
        return s;
    }

    private static Integer integer(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) return null;
        if (v instanceof Number n) {
            if (n.doubleValue() != Math.rint(n.doubleValue())) throw new Invalid("« " + key + " » doit être un nombre entier.");
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new Invalid("« " + key + " » doit être un nombre entier (reçu : " + v + ").");
        }
    }

    private static int intOr(Map<String, Object> a, String key, int def) {
        try {
            Integer v = integer(a, key);
            return v == null ? def : v;
        } catch (Invalid e) {
            return def;
        }
    }

    private static int requiredInt(Map<String, Object> a, String key) {
        Integer v = integer(a, key);
        if (v == null) throw new Invalid("champ « " + key + " » manquant.");
        return v;
    }

    private static Boolean bool(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "oui", "yes", "1", "vrai" -> true;
            case "false", "non", "no", "0", "faux" -> false;
            default -> throw new Invalid("« " + key + " » doit être vrai ou faux (reçu : " + v + ").");
        };
    }

    private static boolean requiredBool(Map<String, Object> a, String key) {
        Boolean b = bool(a, key);
        if (b == null) throw new Invalid("champ « " + key + " » manquant.");
        return b;
    }

    private static double num(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (RuntimeException e) {
            throw new Invalid("« " + key + " » doit être un nombre.");
        }
    }

    /** A list field: a JSON list, or a comma-separated text. Null when absent. */
    private static List<String> strings(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) if (o != null && !String.valueOf(o).isBlank()) out.add(String.valueOf(o).trim());
        } else {
            for (String s : String.valueOf(v).split(",")) if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    // ---------------------------------------------------------------- one run

    /** Applies the actions of one list in order; the data is modified in place inside the caller's change. */
    private static final class Run {

        private final CraftMasterData data;
        private final List<String> lines;
        private final List<Effect> effects;
        Run(CraftMasterData data, List<String> lines, List<Effect> effects) {
            this.data = data;
            this.lines = lines;
            this.effects = effects;
        }

        private void line(String s) {
            lines.add(s);
        }

        void apply(String type, Map<String, Object> a) {
            switch (type) {
                case "score.set" -> score(a, false);
                case "score.add" -> score(a, true);
                case "score.remove" -> scoreRemove(a);
                case "scores.publish" -> publish(a);
                case "reveal.start" -> revealStart(a);
                case "reveal.stop" -> {
                    effects.add(new RevealStopped());
                    line("Révélation interrompue.");
                }
                case "undo" -> throw new Invalid("« undo » doit être la première action de la liste.");
                case "player.add" -> playerAdd(a);
                case "player.update" -> playerUpdate(a);
                case "player.remove" -> playerRemove(a);
                case "season.create" -> seasonCreate(a);
                case "season.update" -> seasonUpdate(a);
                case "season.archive" -> seasonArchive(a);
                case "season.delete" -> seasonDelete(a);
                case "season.activate" -> seasonActivate(a);
                case "partie.create" -> partieCreate(a);
                case "partie.update" -> partieUpdate(a);
                case "partie.archive" -> partieArchive(a);
                case "partie.delete" -> partieDelete(a);
                case "partie.activate" -> partieActivate(a);
                case "manche.create" -> mancheCreate(a);
                case "manche.update" -> mancheUpdate(a);
                case "manche.archive" -> mancheArchive(a);
                case "manche.delete" -> mancheDelete(a);
                case "manche.tp.add" -> mancheTpAdd(a);
                case "manche.tp.clear" -> mancheTpClear(a);
                case "manche.start" -> mancheStart(a);
                case "manche.end" -> mancheEnd();
                case "timer.stop" -> {
                    effects.add(new TimerStopped());
                    line("Chrono arrêté.");
                }
                case "curtain.open" -> curtain(a, true);
                case "curtain.close" -> curtain(a, false);
                case "scene.set" -> sceneSet(a);
                case "say" -> line(required(a, "text"));
                default -> throw new Invalid("action inconnue « " + type + " ».");
            }
        }

        // ------------------------------------------------------------ resolution

        /** The season named by the action ("season": number or name), else the active one. */
        private Season season(Map<String, Object> a, boolean create) {
            Object v = a.get("season");
            if (v == null || String.valueOf(v).isBlank()) {
                Season s = data.activeSeason();
                if (s != null) return s;
                if (!create) throw new Invalid("aucune saison : créez-en une d'abord.");
                s = data.seasonOrCreate(data.nextSeasonId());
                data.live().season = s.id;
                line("Saison " + s.id + " créée.");
                return s;
            }
            String key = String.valueOf(v).trim();
            Integer id = parseId(v);
            if (id != null) {
                Season s = data.season(id);
                if (s != null) return s;
                if (!create) throw new Invalid("saison " + id + " introuvable.");
                s = data.seasonOrCreate(id);
                line("Saison " + id + " créée.");
                return s;
            }
            for (Season s : data.seasonMap().values()) if (s.name.equalsIgnoreCase(key)) return s;
            throw new Invalid("saison « " + key + " » introuvable.");
        }

        private static Integer parseId(Object v) {
            if (v instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static void writable(Season s) {
            if (s.archived) throw new Invalid(s.name + " est archivée (lecture seule) : désarchivez-la d'abord.");
        }

        private static void writable(Partie p) {
            if (p.archived) throw new Invalid(p.name + " est archivée (lecture seule) : désarchivez-la d'abord.");
        }

        /** The game named by the action ("partie": number or name), else the active one (created if needed). */
        private Partie partie(Season s, Map<String, Object> a, boolean create) {
            Object v = a.get("partie");
            if (v == null || String.valueOf(v).isBlank()) {
                Partie p = create ? s.activePartieOrCreate() : s.activePartie();
                if (p == null) throw new Invalid(s.name + " n'a aucune partie.");
                return p;
            }
            Integer id = parseId(v);
            if (id != null && s.parties.containsKey(id)) return s.parties.get(id);
            String key = String.valueOf(v).trim();
            for (Partie p : s.parties.values()) if (p.name.equalsIgnoreCase(key)) return p;
            throw new Invalid("partie « " + key + " » introuvable dans " + s.name + ".");
        }

        private record Found(Partie partie, Manche manche) {}

        /**
         * The round named by {@code key} in the season: "current", "next", an id, a name or a nickname. A round of
         * the requested game first, then of any game of the season.
         */
        private Found manche(Season s, Map<String, Object> a, String key) {
            String k = key.trim().toLowerCase(Locale.ROOT);
            Live.Current cur = data.live().current;
            boolean explicitPartie = a.get("partie") != null && !String.valueOf(a.get("partie")).isBlank();
            if (k.equals("current") || k.equals("en cours") || k.equals("actuelle")) {
                Manche r = data.currentManche();
                if (r == null || cur.season() != s.id) throw new Invalid("aucune manche en cours dans " + s.name + ".");
                Partie p = s.parties.get(cur.partie());
                if (explicitPartie && partie(s, a, false) != p) throw new Invalid("la manche en cours n'est pas dans cette partie.");
                return new Found(p, r);
            }
            if (k.equals("next") || k.equals("suivante")) {
                Manche r = data.currentManche();
                Partie p = r != null && cur.season() == s.id && !explicitPartie ? s.parties.get(cur.partie()) : partie(s, a, false);
                List<Manche> list = p.activeManches();
                if (list.isEmpty()) throw new Invalid(p.name + " n'a aucune manche.");
                int i = r == null || !p.manches.contains(r) ? -1 : list.indexOf(r);
                if (r != null && p.manches.contains(r) && r.archived) {
                    // The current round was archived meanwhile: the next listed one after it
                    i = -1;
                    for (Manche m : p.manches) {
                        if (m == r) break;
                        if (!m.archived) i++;
                    }
                }
                if (i + 1 >= list.size()) throw new Invalid("plus de manche après « " + r.name + " » dans " + p.name + ".");
                return new Found(p, list.get(i + 1));
            }
            Partie p = partie(s, a, false);
            for (Manche r : p.manches) if (r.id.equalsIgnoreCase(key)) return new Found(p, r);
            for (Manche r : p.manches) if (r.matches(key)) return new Found(p, r);
            if (!explicitPartie)
                for (Partie q : s.parties.values()) for (Manche r : q.manches) if (r.matches(key)) return new Found(q, r);
            throw new Invalid("manche « " + key + " » introuvable dans " + p.name + " (" + s.name + ").");
        }

        private Found mancheOrCurrent(Season s, Map<String, Object> a) {
            String key = str(a, "manche");
            return manche(s, a, key == null ? "current" : key);
        }

        /** A cast member by exact name, name ignoring case, or nickname; null if unknown. */
        private static SeasonPlayer castMember(Season s, String pseudo) {
            return s.player(pseudo);
        }

        /** The player's name in the cast; someone new joins the cast. */
        private String scoredPlayer(Season s, String pseudo) {
            SeasonPlayer p = castMember(s, pseudo);
            if (p != null) return p.name;
            s.players.put(pseudo, new SeasonPlayer(pseudo));
            line(pseudo + " rejoint les joueurs de " + s.name + ".");
            return pseudo;
        }

        // ------------------------------------------------------------ scores

        private void score(Map<String, Object> a, boolean add) {
            Season s = season(a, true);
            writable(s);
            if (s.parties.isEmpty()) throw new Invalid(s.name + " n'a encore aucune manche.");
            Found f = mancheOrCurrent(s, a);
            writable(f.partie());
            String player = scoredPlayer(s, required(a, "player"));
            int old = f.partie().score(f.manche().id, player);
            int value = add ? old + (a.containsKey("points") ? requiredInt(a, "points") : requiredInt(a, "value")) : requiredInt(a, "value");
            f.partie().setScore(f.manche().id, player, value);
            data.live().lastScoreAt = System.currentTimeMillis();
            int delta = value - old;
            String what = add ? (delta >= 0 ? "+" : "") + delta + " pour " + player + " → " + value + " pts"
                    : player + " : " + value + " pts";
            line(what + " (" + f.manche().name + ")" + (f.manche().archived ? " — manche archivée" : ""));
            if (delta != 0) effects.add(new ScoreGiven(s.id, player, delta, f.manche().name));
        }

        private void scoreRemove(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = mancheOrCurrent(s, a);
            writable(f.partie());
            SeasonPlayer cast = castMember(s, required(a, "player"));
            String player = cast == null ? required(a, "player") : cast.name;
            if (!f.partie().removeScore(f.manche().id, player))
                throw new Invalid(player + " n'a pas de score pour « " + f.manche().name + " ».");
            line("Score retiré : " + player + " (" + f.manche().name + ")");
        }

        private void publish(Map<String, Object> a) {
            Season s = season(a, false);
            s.publish(System.currentTimeMillis());
            effects.add(new Published(s.id));
            line("Scores publiés (" + s.name + ").");
        }

        private void revealStart(Map<String, Object> a) {
            Season s = season(a, false);
            if (s.scoredPlayers().isEmpty()) throw new Invalid(s.name + " n'a aucun score à révéler.");
            effects.add(new RevealStarted(s.id));
            line("Révélation de " + s.name + " lancée.");
        }

        // ------------------------------------------------------------ cast

        private void checkAliases(Season s, SeasonPlayer self, List<String> aliases) {
            for (String al : aliases)
                for (SeasonPlayer other : s.players.values())
                    if (other != self && other.matches(al))
                        throw new Invalid("« " + al + " » désigne déjà " + other.name + ".");
        }

        private void playerAdd(Map<String, Object> a) {
            Season s = season(a, true);
            writable(s);
            String pseudo = required(a, "pseudo");
            List<String> aliases = strings(a, "aliases");
            SeasonPlayer p = s.players.values().stream().filter(x -> x.name.equalsIgnoreCase(pseudo)).findFirst().orElse(null);
            if (p != null && !p.archived && (aliases == null || aliases.isEmpty()))
                throw new Invalid(p.name + " fait déjà partie de " + s.name + ".");
            if (p == null) {
                p = new SeasonPlayer(pseudo);
                checkAliases(s, p, aliases == null ? List.of() : aliases);
                s.players.put(pseudo, p);
                line(pseudo + " ajouté aux joueurs de " + s.name + ".");
            } else {
                checkAliases(s, p, aliases == null ? List.of() : aliases);
                if (p.archived) line(p.name + " revient dans " + s.name + ".");
                p.archived = false;
            }
            if (aliases != null && !aliases.isEmpty()) {
                for (String al : aliases) if (p.aliases.stream().noneMatch(al::equalsIgnoreCase)) p.aliases.add(al);
                line("Surnoms de " + p.name + " : " + String.join(", ", p.aliases));
            }
        }

        private SeasonPlayer existingPlayer(Season s, String pseudo) {
            SeasonPlayer p = castMember(s, pseudo);
            if (p == null) throw new Invalid(pseudo + " n'est pas un joueur de " + s.name + ".");
            return p;
        }

        private void playerUpdate(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            SeasonPlayer p = existingPlayer(s, required(a, "pseudo"));
            String newName = str(a, "newPseudo");
            List<String> aliases = strings(a, "aliases");
            if (newName == null && aliases == null) throw new Invalid("rien à changer (newPseudo ou aliases).");
            if (aliases != null) {
                checkAliases(s, p, aliases);
                p.aliases.clear();
                p.aliases.addAll(aliases);
                line("Surnoms de " + p.name + " : " + (aliases.isEmpty() ? "aucun" : String.join(", ", aliases)));
            }
            if (newName != null && !newName.equals(p.name)) {
                SeasonPlayer clash = s.players.get(newName);
                if (clash != null && clash != p) throw new Invalid(newName + " est déjà un joueur de " + s.name + ".");
                String old = p.name;
                renamePlayer(s, old, newName);
                line(old + " s'appelle maintenant " + newName + ".");
            }
        }

        /** Renames in the cast (same place), in the live scores and in the published ones. */
        private static void renamePlayer(Season s, String old, String newName) {
            Map<String, SeasonPlayer> copy = new LinkedHashMap<>(s.players);
            s.players.clear();
            copy.forEach((n, p) -> {
                if (n.equals(old)) {
                    p.name = newName;
                    s.players.put(newName, p);
                } else s.players.put(n, p);
            });
            for (Partie p : s.parties.values())
                for (Map<String, Integer> per : p.scores.values()) renameKey(per, old, newName);
            if (s.published != null) renameKey(s.published.scores(), old, newName);
        }

        private static <V> void renameKey(Map<String, V> m, String old, String newName) {
            if (!m.containsKey(old)) return;
            Map<String, V> copy = new LinkedHashMap<>(m);
            m.clear();
            copy.forEach((k, v) -> m.put(k.equals(old) ? newName : k, v));
        }

        private void playerRemove(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            SeasonPlayer p = existingPlayer(s, required(a, "pseudo"));
            if (s.scoredPlayers().contains(p.name)) {
                p.archived = true;
                line(p.name + " retiré de " + s.name + " (ses points restent dans le classement).");
            } else {
                s.players.remove(p.name);
                line(p.name + " retiré de " + s.name + ".");
            }
        }

        // ------------------------------------------------------------ seasons

        private void seasonCreate(Map<String, Object> a) {
            int id = data.nextSeasonId();
            Season s = data.seasonOrCreate(id);
            String name = str(a, "name");
            if (name != null) {
                for (Season o : data.seasonMap().values())
                    if (o != s && o.name.equalsIgnoreCase(name)) throw new Invalid("une saison s'appelle déjà « " + name + " ».");
                s.name = name;
            }
            data.live().season = id;
            line(s.name + " créée (n° " + id + ") et active.");
        }

        private void seasonUpdate(Map<String, Object> a) {
            Season s = season(a, false);
            String name = str(a, "name");
            Boolean hidden = bool(a, "hidden");
            if (name == null && hidden == null) throw new Invalid("rien à changer (name ou hidden).");
            if (name != null) {
                String old = s.name;
                s.name = name;
                line(old + " renommée « " + name + " ».");
            }
            if (hidden != null) {
                s.hidden = hidden;
                line(s.name + (hidden ? " cachée aux joueurs." : " visible par les joueurs."));
            }
        }

        private void seasonArchive(Map<String, Object> a) {
            Season s = season(a, false);
            s.archived = requiredBool(a, "archived");
            line(s.name + (s.archived ? " archivée." : " désarchivée."));
        }

        private void seasonDelete(Map<String, Object> a) {
            if (a.get("season") == null) throw new Invalid("précisez la saison à supprimer (« season »).");
            Season s = season(a, false);
            data.deleteSeason(s.id);
            line(s.name + " supprimée (annulable).");
        }

        private void seasonActivate(Map<String, Object> a) {
            if (a.get("season") == null) throw new Invalid("précisez la saison à activer (« season »).");
            Season s = season(a, false);
            data.live().season = s.id;
            line(s.name + " est la saison active.");
        }

        // ------------------------------------------------------------ games

        private void partieCreate(Map<String, Object> a) {
            Season s = season(a, true);
            writable(s);
            int id = s.parties.isEmpty() ? 1 : s.parties.lastKey() + 1;
            String name = str(a, "name");
            if (name == null) name = "Partie " + id;
            for (Partie p : s.parties.values())
                if (p.name.equalsIgnoreCase(name)) throw new Invalid("une partie s'appelle déjà « " + name + " ».");
            s.parties.put(id, new Partie(id, name));
            s.activePartie = id;
            line(name + " créée dans " + s.name + " et active.");
        }

        private void partieUpdate(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Partie p = partie(s, a, false);
            String name = str(a, "name");
            String status = str(a, "status");
            if (name == null && status == null) throw new Invalid("rien à changer (name ou status).");
            if (name != null) {
                String old = p.name;
                p.name = name;
                line(old + " renommée « " + name + " ».");
            }
            if (status != null) {
                String st = Partie.normalizeStatus(status);
                if (st == null) throw new Invalid("statut inconnu « " + status + " » (preparation, en_cours, terminee).");
                p.status = st;
                line(p.name + " : " + st.replace('_', ' ') + ".");
            }
        }

        private void partieArchive(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Partie p = partie(s, a, false);
            p.archived = requiredBool(a, "archived");
            line(p.name + (p.archived ? " archivée." : " désarchivée."));
        }

        private void partieDelete(Map<String, Object> a) {
            if (a.get("partie") == null) throw new Invalid("précisez la partie à supprimer (« partie »).");
            Season s = season(a, false);
            writable(s);
            Partie p = partie(s, a, false);
            s.parties.remove(p.id);
            if (s.activePartie != null && s.activePartie == p.id) s.activePartie = null;
            Live.Current c = data.live().current;
            if (c != null && c.season() == s.id && c.partie() == p.id) data.live().current = null;
            line(p.name + " supprimée de " + s.name + " (annulable).");
        }

        private void partieActivate(Map<String, Object> a) {
            if (a.get("partie") == null) throw new Invalid("précisez la partie à activer (« partie »).");
            Season s = season(a, false);
            Partie p = partie(s, a, false);
            s.activePartie = p.id;
            data.live().season = s.id;
            line(p.name + " est la partie active de " + s.name + ".");
        }

        // ------------------------------------------------------------ rounds

        private String sceneField(Map<String, Object> a) {
            String scene = str(a, "scene");
            if (scene == null || scene.equalsIgnoreCase("none") || scene.equalsIgnoreCase("aucune") || scene.equalsIgnoreCase("aucun"))
                return null;
            for (String n : data.stage().scenes.keySet()) if (n.equalsIgnoreCase(scene)) return n;
            throw new Invalid("décor « " + scene + " » inconnu (décors : " + String.join(", ", data.stage().scenes.keySet()) + ").");
        }

        private static void checkName(Partie p, Manche self, String name) {
            for (Manche r : p.manches)
                if (r != self && r.name.equalsIgnoreCase(name))
                    throw new Invalid("une manche s'appelle déjà « " + r.name + " » dans " + p.name
                            + (r.archived ? " (archivée)." : "."));
        }

        private void mancheCreate(Map<String, Object> a) {
            Season s = season(a, true);
            writable(s);
            Partie p = partie(s, a, true);
            writable(p);
            String name = required(a, "name");
            checkName(p, null, name);
            Manche r = new Manche(s.newMancheId(name), name);
            List<String> aliases = strings(a, "aliases");
            if (aliases != null) r.aliases.addAll(aliases);
            if (bool(a, "onStage") != null) r.onStage = bool(a, "onStage");
            if (a.containsKey("scene")) r.scene = sceneField(a);
            if (integer(a, "timer") != null) r.timer = Math.max(0, integer(a, "timer"));
            String after = str(a, "after");
            if (after != null) {
                Manche ref = manche(s, a, after).manche();
                int i = p.manches.indexOf(ref);
                if (i < 0) throw new Invalid("« " + after + " » n'est pas dans " + p.name + ".");
                p.manches.add(i + 1, r);
            } else {
                p.manches.add(r);
            }
            line("Manche « " + name + " » ajoutée (n° " + p.number(r) + ", " + p.name + ")" + details(r) + ".");
        }

        private static String details(Manche r) {
            StringBuilder b = new StringBuilder();
            if (r.onStage) b.append(", sur scène");
            if (r.scene != null) b.append(", décor ").append(r.scene);
            if (r.timer > 0) b.append(", chrono ").append(clock(r.timer));
            return b.toString();
        }

        private void mancheUpdate(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            Partie p = f.partie();
            Manche r = f.manche();
            boolean any = false;
            String name = str(a, "name");
            if (name != null && !name.equals(r.name)) {
                any = true;
                Manche other = p.manches.stream().filter(m -> m != r && m.name.equalsIgnoreCase(name)).findFirst().orElse(null);
                if (other != null) {
                    if (!Boolean.TRUE.equals(bool(a, "merge"))) checkName(p, r, name);
                    CraftMasterData.mergeManche(p, r, other);
                    Live.Current c = data.live().current;
                    if (c != null && c.manche().equals(r.id)) data.live().current = new Live.Current(c.season(), c.partie(), other.id);
                    line("« " + r.name + " » fusionnée dans « " + other.name + " » (points additionnés).");
                    return;
                }
                line("Manche « " + r.name + " » renommée « " + name + " ».");
                r.name = name;
            }
            List<String> aliases = strings(a, "aliases");
            if (aliases != null) {
                any = true;
                r.aliases.clear();
                r.aliases.addAll(aliases);
                line("Surnoms de « " + r.name + " » : " + (aliases.isEmpty() ? "aucun" : String.join(", ", aliases)));
            }
            Boolean onStage = bool(a, "onStage");
            if (onStage != null) {
                any = true;
                r.onStage = onStage;
                line("« " + r.name + " » " + (onStage ? "se joue sur scène." : "ne se joue plus sur scène."));
            }
            if (a.containsKey("scene")) {
                any = true;
                r.scene = sceneField(a);
                line("Décor de « " + r.name + " » : " + (r.scene == null ? "aucun" : r.scene));
            }
            Integer timer = integer(a, "timer");
            if (timer != null) {
                any = true;
                r.timer = Math.max(0, timer);
                line("Chrono de « " + r.name + " » : " + (r.timer == 0 ? "aucun" : clock(r.timer)));
            }
            Integer order = integer(a, "order");
            if (order != null) {
                any = true;
                if (r.archived) throw new Invalid("« " + r.name + " » est archivée : pas de place dans l'ordre.");
                List<Manche> listed = new ArrayList<>(p.activeManches());
                listed.remove(r);
                int pos = Math.max(1, Math.min(order, listed.size() + 1));
                // Archived rounds keep their slots; listed ones are laid out again in the new order
                listed.add(pos - 1, r);
                List<Manche> all = new ArrayList<>(p.manches);
                int li = 0;
                for (int i = 0; i < all.size(); i++) if (!all.get(i).archived) all.set(i, listed.get(li++));
                p.manches.clear();
                p.manches.addAll(all);
                line("« " + r.name + " » est maintenant la manche n° " + pos + ".");
            }
            if (!any) throw new Invalid("rien à changer pour « " + r.name + " ».");
        }

        private void mancheArchive(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            boolean archived = requiredBool(a, "archived");
            if (!archived) checkName(f.partie(), f.manche(), f.manche().name);
            f.manche().archived = archived;
            line("Manche « " + f.manche().name + " » " + (archived ? "archivée (ses points restent)." : "désarchivée."));
        }

        private void mancheDelete(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            writable(f.partie());
            f.partie().manches.remove(f.manche());
            f.partie().scores.remove(f.manche().id);
            if (data.currentManche() == f.manche()) data.live().current = null;
            line("Manche « " + f.manche().name + " » supprimée avec ses points (annulable).");
        }

        private void mancheTpAdd(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            Spot spot = new Spot(required(a, "world"), num(a, "x"), num(a, "y"), num(a, "z"),
                    a.containsKey("yaw") ? (float) num(a, "yaw") : 0, a.containsKey("pitch") ? (float) num(a, "pitch") : 0);
            f.manche().tp.add(spot);
            line("Point de TP n° " + f.manche().tp.size() + " de « " + f.manche().name + " » : " + spot.describe());
        }

        private void mancheTpClear(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            int n = f.manche().tp.size();
            f.manche().tp.clear();
            line(n + " point(s) de TP retiré(s) de « " + f.manche().name + " ».");
        }

        private void mancheStart(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            Partie p = f.partie();
            Manche r = f.manche();
            writable(p);
            if (r.archived) throw new Invalid("« " + r.name + " » est archivée.");
            if (r.onStage && r.scene != null) {
                if (!data.stage().scenes.containsKey(r.scene)) throw new Invalid("le décor « " + r.scene + " » n'existe plus.");
                if (data.stage().anchor == null) throw new Invalid("pas d'ancre de scène pour poser le décor « " + r.scene + " ».");
                data.live().scene = r.scene;
                effects.add(new ScenePlaced(r.scene));
            }
            data.live().current = new Live.Current(s.id, p.id, r.id);
            data.live().season = s.id;
            s.activePartie = p.id;
            if (p.status.equals(Partie.PREPARATION)) p.status = Partie.EN_COURS;
            List<String> cast = s.players.values().stream().filter(x -> !x.archived).map(x -> x.name).toList();
            effects.add(new MancheStarted(s.id, p.number(r), r.name, r.timer, List.copyOf(r.tp), cast));
            line("Manche " + p.number(r) + " — " + r.name + " lancée" + (r.timer > 0 ? " (chrono " + clock(r.timer) + ")" : "") + ".");
        }

        private void mancheEnd() {
            Manche r = data.currentManche();
            if (r == null) throw new Invalid("aucune manche en cours.");
            data.live().current = null;
            effects.add(new MancheEnded(r.name));
            line("Fin de la manche « " + r.name + " ».");
        }

        // ------------------------------------------------------------ stage

        private String curtainName(Map<String, Object> a) {
            String name = str(a, "name");
            if (name == null) name = Stage.MAIN_CURTAIN;
            for (String n : data.stage().curtains.keySet()) if (n.equalsIgnoreCase(name)) return n;
            throw new Invalid("rideau « " + name + " » inconnu (rideaux : "
                    + (data.stage().curtains.isEmpty() ? "aucun" : String.join(", ", data.stage().curtains.keySet())) + ").");
        }

        private void curtain(Map<String, Object> a, boolean open) {
            String name = curtainName(a);
            effects.add(new CurtainMoved(name, open));
            line((open ? "Ouverture" : "Fermeture") + " du rideau « " + name + " ».");
        }

        private void sceneSet(Map<String, Object> a) {
            String name = sceneField(Map.of("scene", required(a, "name")));
            if (name == null) throw new Invalid("précisez un décor.");
            if (data.stage().anchor == null) throw new Invalid("pas d'ancre de scène : définissez-la d'abord.");
            data.live().scene = name;
            effects.add(new ScenePlaced(name));
            line("Décor « " + name + " » posé sur la scène.");
        }
    }
}
