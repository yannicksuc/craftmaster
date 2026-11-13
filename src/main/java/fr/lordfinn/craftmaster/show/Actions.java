package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.data.Spot;
import fr.lordfinn.craftmaster.data.Stage;
import fr.lordfinn.craftmaster.trial.ResultSheet;
import fr.lordfinn.craftmaster.trial.Scale;
import fr.lordfinn.craftmaster.trial.Stopwatch;
import fr.lordfinn.craftmaster.data.Task;

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
    public record MancheStarted(int season, int number, String name, int timer, List<Spot> tp, List<String> cast, String mancheId,
                                String timerStart) implements Effect {}

    public record MancheEnded(String name) implements Effect {}

    public record TimerStopped() implements Effect {}
    /** The organiser starts a round's held timer now (timer.go): everyone frozen is free. */
    public record TimerGo() implements Effect {}

    /** A flag on the replay's timeline, now (coloured by who is on air); {@code text} may be empty. */
    public record ReplayFlagged(String text) implements Effect {}

    /** Timer tablets of a group ("" = all): start, pause, toggle, reset, restart. */
    public record TabletOrder(String order, String group) implements Effect {}

    public record Published(int season) implements Effect {}

    public record RevealStarted(int season) implements Effect {}

    public record RevealStopped() implements Effect {}

    public record CurtainMoved(String name, boolean open) implements Effect {}

    /** A named garage door opens (true), closes (false) or switches (null): garage/GarageDoors. */
    public record GarageMoved(String name, Boolean open) implements Effect {}

    public record ScenePlaced(String name) implements Effect {}

    // ---- chantier H: judging (results sheet, stopwatches, buzzers)

    /** A value was written on a results sheet (or a disqualification): announced to everyone. */
    public record ResultRecorded(String player, String text, boolean dq, String manche) implements Effect {}

    public record StopwatchStarted(String player) implements Effect {}

    /** {@code ms}: the time measured (also written on the sheet when recorded). */
    public record StopwatchStopped(String player, long ms) implements Effect {}

    /** {@code player} null = every stopwatch. */
    public record StopwatchReset(String player) implements Effect {}

    public record BuzzersReset() implements Effect {}
    /** TV direction (chantier E): a camera / overlay / credits action, fields checked by BroadcastActions. */
    public record Broadcast(Map<String, Object> action) implements Effect {}

    // ---- tasks, envelopes (chantier D): played by fr.lordfinn.craftmaster.task.Tasks#play

    /** Effects of the task actions; ActionExecutor hands them all to {@code Tasks.play}. */
    public sealed interface TaskEffect extends Effect {}

    /** The task becomes visible to everyone; {@code announce}: say so to all (forced by the organiser). */
    public record TaskRevealed(int season, String manche, boolean announce) implements TaskEffect {}

    public record TaskHidden(int season, String manche) implements TaskEffect {}

    /** The next [secret] part of the task's text is revealed to everyone ("révèle la suite"). */
    public record TaskSecretShown(int season, String manche) implements TaskEffect {}

    /** The task's card shows on every screen for {@code seconds} (revealed first if it was not). */
    public record TaskShown(int season, String manche, int seconds) implements TaskEffect {}

    /** Sealed envelopes of the round for these players (empty = the online cast); {@code wax} null = the task's. */
    public record EnvelopesGiven(int season, String manche, List<String> players, String wax) implements TaskEffect {}

    // Studio set (chantier F)
    /** Every loaded spotlight: {@code order} = follow (then {@code player}), stop, on or off. */
    public record SpotlightOrder(String order, String player) implements Effect {}

    /**
     * tele.show: what a named studio screen shows (media/MediaServer.show). {@code kind}: clip, lien, direct, image,
     * off. Nothing in the data changes (no undo entry).
     */
    public record ScreenShow(String tele, String kind, String value) implements Effect {}
    /**
     * Screen effects (post-processing of the view, fx/): {@code target} = "diffusion", "tous", "candidats", "tout"
     * or an exact pseudo; {@code effect} canonical ("fisheye+masque_rond", "" = none); {@code seconds} 0 = until
     * changed. Played by fx.ScreenEffectsServer; nothing in the data changes (no undo entry).
     */
    public record ScreenEffect(String target, String effect, int seconds, int fadeTicks) implements Effect {}

    /** The result: one French line per action (or the error), the effects to play, and whether it worked. */
    public record Outcome(List<String> lines, List<Effect> effects, boolean ok, boolean changed) {}

    /** An invalid action: its message is shown as is. */
    public static final class Invalid extends RuntimeException {
        public Invalid(String message) {
            super(message);
        }
    }

    public static final List<String> TYPES = List.of(
            "score.set", "score.add", "score.remove", "scores.publish", "reveal.start", "reveal.stop", "undo", "redo",
            "player.add", "player.update", "player.remove",
            "season.create", "season.update", "season.archive", "season.delete", "season.activate",
            "partie.create", "partie.update", "partie.archive", "partie.delete", "partie.activate",
            "manche.create", "manche.update", "manche.archive", "manche.delete", "manche.tp.add", "manche.tp.clear", "manche.zones",
            "manche.start", "manche.end", "timer.stop", "timer.go", "replay.flag", "curtain.open", "curtain.close", "scene.set", "say",
            // chantier H
            "result.set", "result.dq", "result.remove", "results.clear", "results.config", "results.apply",
            "stopwatch.start", "stopwatch.stop", "stopwatch.reset", "buzzer.reset",
            // TV direction (chantier E): no data change, played by broadcast.Director
            "camera.cut", "camera.follow", "camera.auto", "camera.transition", "broadcast.lowerthird", "broadcast.task",
            "broadcast.scores", "broadcast.letterbox", "show.intro", "show.outro", "show.stop",
            "task.set", "task.clear", "task.reveal", "task.hide", "task.show", "envelope.give", "task.reveal_more",
            // Studio set (chantier F)
            "spotlight.follow", "spotlight.stop", "spotlight.on", "spotlight.off",
            // Studio screens: clips, Twitch links and lives (media/)
            "tele.show",
            // TV direction, lieux / travellings / intros (chantier E): no data change for these four
            "camera.pov", "camera.path", "camera.mosaic", "camera.focus", "camera.lieu",
            // ... and the round intros, which are round data (undoable), and playing one
            "intro.set", "intro.shot.add", "intro.shot.set", "intro.shot.remove", "intro.clear", "intro.play", "intro.auto", "intro.shot.move",
            // Garage doors (garage/): no data change
            "garage.open", "garage.close", "garage.toggle");

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
        } else if ("redo".equals(type(actions.getFirst()))) {
            int count = Math.max(1, intOr(actions.getFirst(), "count", 1));
            List<String> redone = data.redo(count);
            if (redone.isEmpty()) lines.add("Rien à refaire.");
            for (String d : redone) lines.add("Refait : " + d);
            changed = !redone.isEmpty();
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
                // What these two say depends on what runs in the world: ActionExecutor writes the line
                case "reveal.stop" -> effects.add(new RevealStopped());
                case "undo" -> throw new Invalid("« undo » doit être la première action de la liste.");
                case "redo" -> throw new Invalid("« redo » doit être la première action de la liste.");
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
                case "manche.zones" -> mancheZones(a);
                case "manche.start" -> mancheStart(a);
                case "manche.end" -> mancheEnd();
                case "timer.stop" -> effects.add(new TimerStopped());
                case "timer.go" -> effects.add(new TimerGo());
                case "replay.flag" -> effects.add(new ReplayFlagged(str(a, "text") == null ? "" : str(a, "text")));
                case "tablet.start", "tablet.pause", "tablet.toggle", "tablet.reset", "tablet.restart" -> {
                    String g = str(a, "group");
                    // The tablets compare their group in lower case
                    effects.add(new TabletOrder(type.substring("tablet.".length()), g == null ? "" : g.toLowerCase(Locale.ROOT)));
                }
                case "curtain.open" -> curtain(a, true);
                case "curtain.close" -> curtain(a, false);
                case "garage.open" -> garage(a, true);
                case "garage.close" -> garage(a, false);
                case "garage.toggle" -> garage(a, null);
                case "scene.set" -> sceneSet(a);
                case "say" -> line(required(a, "text"));
                // ---- chantier H: judging
                case "result.set" -> resultSet(a);
                case "result.dq" -> resultDq(a);
                case "result.remove" -> resultRemove(a);
                case "results.clear" -> resultsClear(a);
                case "results.config" -> resultsConfig(a);
                case "results.apply" -> resultsApply(a);
                case "stopwatch.start" -> stopwatchStart(a);
                case "stopwatch.stop" -> stopwatchStop(a);
                case "stopwatch.reset" -> stopwatchReset(a);
                case "buzzer.reset" -> {
                    effects.add(new BuzzersReset());
                    line("Buzzers remis à zéro.");
                }
                // ---- tasks, envelopes (chantier D)
                case "task.set" -> taskSet(a);
                case "task.clear" -> taskClear(a);
                case "task.reveal" -> taskReveal(a);
                case "task.hide" -> taskHide(a);
                case "task.reveal_more" -> taskRevealMore(a);
                case "task.show" -> taskShow(a);
                case "envelope.give" -> envelopeGive(a);
                // Studio set (chantier F): world only, nothing in the data changes
                case "spotlight.follow" -> spotlightFollow(a);
                case "spotlight.stop", "spotlight.on", "spotlight.off" -> effects.add(new SpotlightOrder(type.substring("spotlight.".length()), null));
                // Studio screens (media/): world only, nothing in the data changes
                case "tele.show" -> teleShow(a);
                // ---- round intros (chantier E, TV direction)
                case "intro.set" -> introSet(a);
                case "intro.shot.add" -> introShotAdd(a);
                case "intro.shot.set" -> introShotSet(a);
                case "intro.shot.remove" -> introShotRemove(a);
                case "intro.clear" -> introClear(a);
                case "intro.auto" -> introAuto(a);
                case "intro.shot.move" -> introShotMove(a);
                case "intro.play" -> introPlay(a);
                // Screen effects (fx/): world only, nothing in the data changes
                case "fx.set" -> screenEffect(a, false);
                case "fx.clear" -> screenEffect(a, true);
                // (a guarded pattern must come after the constant labels) TV direction (chantier E): checked here, resolved and played by broadcast.Director
                case String t when fr.lordfinn.craftmaster.broadcast.BroadcastActions.handles(t) ->
                        effects.add(new Broadcast(fr.lordfinn.craftmaster.broadcast.BroadcastActions.validate(t, a)));
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
                if (r == null && cur == null) {
                    // Nothing being played: the round after the last one ended
                    Live.Current e = data.live().ended;
                    Partie ep = e == null || e.season() != s.id ? null : s.parties.get(e.partie());
                    Manche em = ep == null ? null : ep.manche(e.manche());
                    if (em != null) {
                        r = em;
                        cur = e;
                    }
                }
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
            data.live().lastScoreManche = f.manche().id;
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
            // chantier H: the results sheets follow
            for (Partie p : s.parties.values()) for (Manche m : p.manches) renameKey(m.results.entries, old, newName);
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
            if (str(a, "timerStart") != null) r.timerStart = timerStart(a);
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
            if (!r.timerStart.equals(fr.lordfinn.craftmaster.task.RoundStart.LAUNCH))
                b.append(", départ ").append(fr.lordfinn.craftmaster.task.RoundStart.describe(r.timerStart));
            return b.toString();
        }

        /** The "timerStart" field: lancement, lettre or bouton. */
        private static String timerStart(Map<String, Object> a) {
            String v = str(a, "timerStart");
            String mode = fr.lordfinn.craftmaster.task.RoundStart.parse(v);
            if (mode == null) throw new Invalid("départ du chrono « " + v + " » inconnu (lancement, lettre ou bouton).");
            return mode;
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
            if (str(a, "timerStart") != null) {
                any = true;
                r.timerStart = timerStart(a);
                line("Chrono de « " + r.name + " » lancé " + fr.lordfinn.craftmaster.task.RoundStart.describe(r.timerStart) + ".");
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

        /**
         * The round's trial zones ({@code zones}: names, a list or "a b c"; empty or "aucune" = none): its replays
         * record each of them, and a contestant out of all of them is recorded apart.
         */
        private void mancheZones(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            List<String> names = new ArrayList<>();
            Object z = a.get("zones");
            if (z instanceof List<?> l) for (Object o : l) names.add(String.valueOf(o).trim());
            else if (z != null) for (String n : String.valueOf(z).split("[\\s,]+")) names.add(n.trim());
            names.removeIf(n -> n.isEmpty() || n.equalsIgnoreCase("aucune") || n.equalsIgnoreCase("none"));
            List<String> known = new ArrayList<>(fr.lordfinn.craftmaster.trial.TrialZones.zones().keySet());
            for (String n : names)
                if (!known.contains(n)) throw new Invalid("Pas de zone « " + n + " » (" + (known.isEmpty() ? "aucune zone : /craftMasterZone create" : String.join(", ", known)) + ").");
            f.manche().zones.clear();
            f.manche().zones.addAll(names);
            line(names.isEmpty() ? "« " + f.manche().name + " » n'a plus de zone : ses replays prendront la zone déduite de ses caméras."
                    : "Zones de « " + f.manche().name + " » : " + String.join(", ", names) + " (une vidéo chacune dans les replays).");
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
            // No written task, no letter to read: the timer starts with the round
            String start = r.timerStart.equals(fr.lordfinn.craftmaster.task.RoundStart.LETTER) && (r.task == null || r.task.isEmpty())
                    ? fr.lordfinn.craftmaster.task.RoundStart.LAUNCH : r.timerStart;
            effects.add(new MancheStarted(s.id, p.number(r), r.name, r.timer, List.copyOf(r.tp), cast, r.id, start));
            // Starting the round reveals its task, unless only the envelopes may (chantier D)
            if (r.task != null && r.task.revealOnStart && !r.task.isEmpty()) effects.add(new TaskRevealed(s.id, r.id, false));
            line("Manche " + p.number(r) + " — " + r.name + " lancée" + (r.timer > 0 ? " (chrono " + clock(r.timer)
                    + (start.equals(fr.lordfinn.craftmaster.task.RoundStart.LAUNCH) ? "" : ", départ " + fr.lordfinn.craftmaster.task.RoundStart.describe(start)
                    + (start.equals(fr.lordfinn.craftmaster.task.RoundStart.BUTTON) ? "" : ", /cfti go pour le lancer avant")) + ")"
                    : start.equals(fr.lordfinn.craftmaster.task.RoundStart.BUTTON) ? " (joueurs figés jusqu'au bouton « Commencer »)" : "") + ".");
        }

        private void mancheEnd() {
            Manche r = data.currentManche();
            if (r == null) throw new Invalid("aucune manche en cours.");
            data.live().ended = data.live().current;
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

        /**
         * tele.show {tele, clip? | link? | live? | image? | off?}: one of the five (clip "dernier" when none is given);
         * the screen's name and the rest are resolved in the world by media/MediaServer.
         */
        private void teleShow(Map<String, Object> a) {
            String tele = required(a, "tele");
            String kind, value = "";
            if (Boolean.TRUE.equals(bool(a, "off"))) kind = "off";
            else if (str(a, "live") != null) { kind = "direct"; value = str(a, "live"); }
            else if (str(a, "link") != null) { kind = "lien"; value = str(a, "link"); }
            else if (str(a, "image") != null) { kind = "image"; value = str(a, "image"); }
            else { kind = "clip"; value = str(a, "clip") == null ? "dernier" : str(a, "clip"); }
            // The line comes from the world (MediaServer.show says what is shown, or why not)
            effects.add(new ScreenShow(tele, kind, value));
        }

        /**
         * fx.set {target?="diffusion", effect, seconds?, fade?} / fx.clear {target?="tout"}: the target is a word
         * (diffusion, tous, candidats, tout), a cast member by name or nickname, or a pseudo (checked when played).
         */
        private void screenEffect(Map<String, Object> a, boolean clear) {
            String target = str(a, "target");
            if (target == null) target = clear ? "tout" : "diffusion";
            String t = target.toLowerCase(Locale.ROOT);
            if (!fr.lordfinn.craftmaster.fx.ScreenEffectsServer.BROADCAST_WORDS.contains(t) && !fr.lordfinn.craftmaster.fx.ScreenEffectsServer.ALL_WORDS.contains(t)
                    && !fr.lordfinn.craftmaster.fx.ScreenEffectsServer.CAST_WORDS.contains(t) && !fr.lordfinn.craftmaster.fx.ScreenEffectsServer.EVERYTHING_WORDS.contains(t)) {
                Season s = data.activeSeason();
                SeasonPlayer p = s == null ? null : castMember(s, target);
                if (p != null) target = p.name;
            } else if (!clear && fr.lordfinn.craftmaster.fx.ScreenEffectsServer.EVERYTHING_WORDS.contains(t)) {
                throw new Invalid("« tout » ne sert qu'à retirer les effets (cibles : diffusion, tous, candidats, un joueur).");
            }
            java.util.List<String> fx;
            String spec;
            if (clear) {
                fx = List.of();
                spec = "";
            } else try {
                String raw = required(a, "effect").trim();
                // "+sepia" adds to what is already on, "-flou" takes one off (merged on the server at play time)
                boolean delta = fr.lordfinn.craftmaster.fx.ScreenEffectsServer.isDelta(raw);
                fx = fr.lordfinn.craftmaster.fx.ScreenFx.parse(delta ? raw.replace('-', '+') : raw);
                spec = delta ? fr.lordfinn.craftmaster.fx.ScreenEffectsServer.canonicalDelta(raw) : fr.lordfinn.craftmaster.fx.ScreenFx.join(fx);
            } catch (IllegalArgumentException e) {
                throw new Invalid(e.getMessage());
            }
            int seconds = a.get("seconds") == null ? 0 : (int) Math.round(num(a, "seconds"));
            if (seconds < 0 || seconds > 3600) throw new Invalid("« seconds » : de 0 à 3600.");
            double fade = a.get("fade") == null ? -1 : num(a, "fade");
            int fadeTicks = fade < 0 ? fr.lordfinn.craftmaster.fx.ScreenEffectsServer.DEFAULT_FADE_TICKS : (int) Math.round(Math.min(fade, 10) * 20);
            effects.add(new ScreenEffect(target, spec, seconds, fadeTicks));
        }

        /** Spotlights on a player: a cast member by name or nickname, or any exact pseudo (a guest, the master). */
        private void spotlightFollow(Map<String, Object> a) {
            String pseudo = required(a, "player");
            Season s = data.activeSeason();
            SeasonPlayer p = s == null ? null : castMember(s, pseudo);
            effects.add(new SpotlightOrder("follow", p != null ? p.name : pseudo));
        }

        private void curtain(Map<String, Object> a, boolean open) {
            String name = curtainName(a);
            effects.add(new CurtainMoved(name, open));
            line((open ? "Ouverture" : "Fermeture") + " du rideau « " + name + " ».");
        }

        /** garage.open / garage.close / garage.toggle {name?}: a door named with /craftMasterGarage nom (the only one if none given). */
        private void garage(Map<String, Object> a, Boolean open) {
            String asked = str(a, "name");
            String name = fr.lordfinn.craftmaster.garage.GarageDoors.resolve(asked);
            if (name == null) {
                java.util.Set<String> names = fr.lordfinn.craftmaster.garage.GarageDoors.names().keySet();
                throw new Invalid(asked == null && names.size() > 1 ? "quelle porte de garage ? (" + String.join(", ", names) + ")"
                        : "porte de garage « " + (asked == null ? "" : asked) + " » inconnue (portes : " + (names.isEmpty() ? "aucune" : String.join(", ", names)) + ").");
            }
            effects.add(new GarageMoved(name, open));
            line((open == null ? "Porte de garage « " + name + " » actionnée" : (open ? "Ouverture" : "Fermeture") + " de la porte de garage « " + name + " »") + ".");
        }

        private void sceneSet(Map<String, Object> a) {
            String name = sceneField(Map.of("scene", required(a, "name")));
            if (name == null) throw new Invalid("précisez un décor.");
            if (data.stage().anchor == null) throw new Invalid("pas d'ancre de scène : définissez-la d'abord.");
            data.live().scene = name;
            effects.add(new ScenePlaced(name));
            line("Décor « " + name + " » posé sur la scène.");
        }

        // ------------------------------------------------------------ chantier H: results sheet, stopwatches

        /** The cast in show order (not archived): the lines of a results sheet. */
        private static List<String> cast(Season s) {
            List<String> out = new ArrayList<>();
            for (SeasonPlayer p : s.players.values()) if (!p.archived) out.add(p.name);
            return out;
        }

        /** Season, round (current by default) and its game, all writable. */
        private record Sheet(Season season, Found found) {
            ResultSheet sheet() {
                return found.manche().results;
            }
        }

        private Sheet sheet(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            if (s.parties.isEmpty()) throw new Invalid(s.name + " n'a encore aucune manche.");
            Found f = mancheOrCurrent(s, a);
            writable(f.partie());
            return new Sheet(s, f);
        }

        private static ResultSheet.Kind kindField(Map<String, Object> a) {
            String k = str(a, "kind");
            if (k == null) return null;
            ResultSheet.Kind kind = ResultSheet.Kind.parse(k);
            if (kind == null) throw new Invalid("type de résultat « " + k + " » inconnu (temps, nombre, distance, note, poids).");
            return kind;
        }

        private void resultSet(Map<String, Object> a) {
            Sheet sh = sheet(a);
            ResultSheet r = sh.sheet();
            ResultSheet.Kind kind = kindField(a);
            // The first value of a sheet can choose its kind ("42 secondes" makes it a time sheet)
            if (kind != null && kind != r.kind) {
                if (r.entries.values().stream().anyMatch(e -> e.value() != null))
                    throw new Invalid("la feuille de « " + sh.found().manche().name + " » est en " + r.kind.fr.toLowerCase(Locale.ROOT)
                            + " : changez son type d'abord (results.config).");
                r.kind = kind;
            }
            Object raw = a.get("value");
            if (raw == null || String.valueOf(raw).isBlank()) throw new Invalid("champ « value » manquant.");
            Double value = raw instanceof Number n ? n.doubleValue() : ResultSheet.parse(r.kind, String.valueOf(raw));
            if (value == null || !Double.isFinite(value)) throw new Invalid("« " + raw + " » n'est pas une valeur (ex. 42, 1:05, 12,5).");
            String player = scoredPlayer(sh.season(), required(a, "player"));
            r.set(player, value);
            String text = r.format(r.entries.get(player).value());
            line("✎ " + player + " : " + text + " (" + sh.found().manche().name + ")");
            effects.add(new ResultRecorded(player, text, false, sh.found().manche().name));
        }

        private void resultDq(Map<String, Object> a) {
            Sheet sh = sheet(a);
            Boolean dq = bool(a, "dq");
            boolean on = dq == null || dq;
            String player = on ? scoredPlayer(sh.season(), required(a, "player")) : existingPlayer(sh.season(), required(a, "player")).name;
            ResultSheet.Entry e = sh.sheet().entries.get(player);
            if (!on && (e == null || !e.dq())) throw new Invalid(player + " n'est pas disqualifié dans « " + sh.found().manche().name + " ».");
            sh.sheet().disqualify(player, on);
            line((on ? "✖ " + player + " est disqualifié" : "✔ " + player + " n'est plus disqualifié") + " (" + sh.found().manche().name + ")");
            effects.add(new ResultRecorded(player, on ? "disqualifié" : "réintégré", on, sh.found().manche().name));
        }

        private void resultRemove(Map<String, Object> a) {
            Sheet sh = sheet(a);
            SeasonPlayer cast = castMember(sh.season(), required(a, "player"));
            String player = cast == null ? required(a, "player") : cast.name;
            if (sh.sheet().entries.remove(player) == null)
                throw new Invalid(player + " n'a pas de résultat pour « " + sh.found().manche().name + " ».");
            line("Résultat effacé : " + player + " (" + sh.found().manche().name + ")");
        }

        private void resultsClear(Map<String, Object> a) {
            Sheet sh = sheet(a);
            int n = sh.sheet().entries.size();
            sh.sheet().entries.clear();
            line("Feuille de résultats de « " + sh.found().manche().name + " » vidée (" + n + " ligne(s)).");
        }

        private void resultsConfig(Map<String, Object> a) {
            Sheet sh = sheet(a);
            ResultSheet r = sh.sheet();
            ResultSheet.Kind kind = kindField(a);
            String order = str(a, "order");
            String scale = str(a, "scale");
            if (kind == null && order == null && scale == null) throw new Invalid("rien à changer (kind, order ou scale).");
            List<String> what = new ArrayList<>();
            if (kind != null) {
                r.kind = kind;
                what.add(kind.fr.toLowerCase(Locale.ROOT));
            }
            if (order != null) {
                String o = order.toLowerCase(Locale.ROOT);
                if (o.equals("auto") || o.equals("defaut") || o.equals("défaut")) r.lowerWins = null;
                else if (o.startsWith("low") || o.contains("petit") || o.contains("moins") || o.equals("asc")) r.lowerWins = true;
                else if (o.startsWith("high") || o.contains("grand") || o.contains("plus") || o.equals("desc")) r.lowerWins = false;
                else throw new Invalid("ordre « " + order + " » inconnu (lower = le plus petit gagne, higher = le plus grand gagne).");
            }
            if (order != null || kind != null) what.add(r.lowerWins() ? "le plus petit gagne" : "le plus grand gagne");
            if (scale != null) {
                try {
                    r.scale = Scale.normalize(scale);
                } catch (IllegalArgumentException e) {
                    throw new Invalid("barème invalide : " + e.getMessage() + ".");
                }
                what.add("barème " + r.scale.replace(",", "-"));
            }
            line("Feuille de « " + sh.found().manche().name + " » : " + String.join(", ", what) + ".");
        }

        private void resultsApply(Map<String, Object> a) {
            Sheet sh = sheet(a);
            ResultSheet r = sh.sheet();
            String scale = str(a, "scale");
            if (scale != null) {
                try {
                    r.scale = Scale.normalize(scale);
                } catch (IllegalArgumentException e) {
                    throw new Invalid("barème invalide : " + e.getMessage() + ".");
                }
            }
            List<ResultSheet.Placed> ranking = r.rank(cast(sh.season()));
            List<String> given = new ArrayList<>();
            for (ResultSheet.Placed p : ranking) {
                if (p.points() == null) continue;
                Map<String, Object> set = new LinkedHashMap<>();
                set.put("season", sh.season().id);
                set.put("partie", sh.found().partie().id);
                set.put("manche", sh.found().manche().id);
                set.put("player", p.player());
                set.put("value", p.points());
                score(set, false);
                given.add(p.player() + " " + p.points());
            }
            if (given.isEmpty()) throw new Invalid("la feuille de « " + sh.found().manche().name + " » est vide : notez d'abord des résultats.");
            line("Barème " + r.scaleOrDefault().replace(",", "-") + " appliqué (" + sh.found().manche().name + ") : " + String.join(", ", given) + ".");
        }

        /** A stopwatch's player: the cast name if they are in it, else the name as given. */
        private String watchPlayer(Map<String, Object> a) {
            String raw = required(a, "player");
            Season s = data.activeSeason();
            SeasonPlayer p = s == null ? null : castMember(s, raw);
            return p != null ? p.name : raw;
        }

        private void stopwatchStart(Map<String, Object> a) {
            String player = watchPlayer(a);
            effects.add(new StopwatchStarted(player));
            line("⏱ Chrono de " + player + " lancé.");
        }

        private void stopwatchStop(Map<String, Object> a) {
            String player = watchPlayer(a);
            String known = Stopwatch.SHARED.find(player);
            long ms = known == null ? -1 : Stopwatch.SHARED.peek(known, System.currentTimeMillis());
            if (ms < 0) throw new Invalid("le chrono de " + player + " ne tourne pas.");
            effects.add(new StopwatchStopped(known, ms));
            Boolean record = bool(a, "record");
            if (record == null || record) {
                Map<String, Object> set = new LinkedHashMap<>(a);
                set.put("player", known);
                set.put("value", ms / 1000.0);
                set.put("kind", "time");
                set.remove("record");
                resultSet(set);
            } else {
                line("⏱ " + known + " : " + Stopwatch.format(ms) + " (non noté).");
            }
        }

        private void stopwatchReset(Map<String, Object> a) {
            String raw = str(a, "player");
            String player = raw == null ? null : watchPlayer(a);
            effects.add(new StopwatchReset(player));
            line(player == null ? "Tous les chronos remis à zéro." : "Chrono de " + player + " remis à zéro.");
        }

        // ------------------------------------------------------------ tasks (chantier D)

        /** The round of a task action ("manche", else the round being played). */
        private Found taskManche(Season s, Map<String, Object> a) {
            return mancheOrCurrent(s, a);
        }

        private void taskSet(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            writable(f.partie());
            Manche r = f.manche();
            Task t = r.task == null ? new Task() : r.task;
            List<String> changed = new ArrayList<>();
            String title = a.containsKey("title") ? String.valueOf(a.get("title") == null ? "" : a.get("title")).strip() : null;
            if (title != null) {
                if (title.length() > 80) throw new Invalid("titre trop long (80 caractères au plus).");
                t.title = title;
                changed.add("titre « " + title + " »");
            }
            Object rawText = a.get("text");
            if (rawText != null) {
                String text = Task.cleanText(String.valueOf(rawText));
                if (Boolean.TRUE.equals(bool(a, "append")) && !t.text.isBlank()) t.text = t.text + "\n\n" + text;
                else t.text = text;
                if (t.text.length() > 4000) throw new Invalid("texte trop long (4000 caractères au plus).");
                changed.add(Boolean.TRUE.equals(bool(a, "append")) ? "paragraphe ajouté" : "texte (" + t.text.length() + " caractères)");
            }
            Object rawRules = a.get("rules");
            if (rawRules != null) {
                List<String> rules = rawRules instanceof List<?> l
                        ? l.stream().filter(java.util.Objects::nonNull).map(x -> String.valueOf(x).strip()).filter(x -> !x.isEmpty()).toList()
                        : Task.parseRules(String.valueOf(rawRules));
                t.rules.clear();
                t.rules.addAll(rules);
                changed.add(rules.isEmpty() ? "aucune règle" : rules.size() + " règle(s)");
            }
            Integer seconds = integer(a, "seconds");
            if (seconds != null) {
                t.seconds = Math.max(0, seconds);
                changed.add("durée affichée " + (t.seconds == 0 ? "celle du chrono" : clock(t.seconds)));
            }
            String wax = str(a, "wax");
            if (wax != null) {
                fr.lordfinn.craftmaster.task.WaxColor c = fr.lordfinn.craftmaster.task.WaxColor.parse(wax);
                if (c == null) throw new Invalid("couleur de cire « " + wax + " » inconnue (rouge, or, noir, blanc, bleu, vert, violet, rose, orange, cyan, marron, gris).");
                t.wax = c.id();
                changed.add("cire " + c.french);
            }
            Boolean onStart = bool(a, "revealOnStart");
            if (onStart != null) {
                t.revealOnStart = onStart;
                changed.add(onStart ? "révélée au lancement" : "révélée à l'ouverture d'une enveloppe seulement");
            }
            if (changed.isEmpty()) throw new Invalid("rien à changer dans la tâche de « " + r.name + " ».");
            r.task = t;
            line("Tâche de « " + r.name + " » : " + String.join(", ", changed) + ".");
        }

        // ------------------------------------------------------------ round intros (chantier E)

        /** The round of an intro action ("manche" absent = the current one), writable. */
        private Manche introManche(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = mancheOrCurrent(s, a);
            writable(f.partie());
            return f.manche();
        }

        /** intro.set {manche?, auto?, music?, titleCard?, bpm?, beats?, offset?, chained?, look?, typedTask?} */
        private void introSet(Map<String, Object> a) {
            Manche r = introManche(a);
            fr.lordfinn.craftmaster.data.MancheIntro in = r.intro;
            List<String> changed = new ArrayList<>();
            Boolean auto = bool(a, "auto");
            if (auto != null) {
                in.auto = auto;
                changed.add(auto ? "jouée au lancement de la manche" : "lancée à la main seulement");
            }
            if (a.containsKey("music")) {
                in.music = fr.lordfinn.craftmaster.data.MancheIntro.normalizeMusic(a.get("music") == null ? "" : String.valueOf(a.get("music")));
                changed.add(in.music.isEmpty() ? "sans musique" : "musique « " + in.music + " »");
            }
            Boolean card = bool(a, "titleCard");
            if (card != null) {
                in.titleCard = card;
                changed.add(card ? "titre de la manche à la fin" : "sans titre automatique");
            }
            if (a.containsKey("bpm")) {
                Double bpm = fr.lordfinn.craftmaster.data.MancheIntro.parseBpm(a.get("bpm") == null ? "" : String.valueOf(a.get("bpm")));
                if (bpm == null) throw new Invalid("tempo « " + a.get("bpm") + " » : un nombre de BPM, « theme », « intro » ou « off ».");
                in.bpm = bpm;
                if (bpm == 0) changed.add("plans à leur durée (pas calés sur la musique)");
            }
            if (a.get("beats") instanceof Number n) in.beats = (int) Math.max(0, Math.min(64, Math.round(n.doubleValue())));
            if (a.get("offset") instanceof Number n) in.offset = Math.max(0, Math.min(30, n.doubleValue()));
            if (in.bpm > 0 && (a.containsKey("bpm") || a.containsKey("beats") || a.containsKey("offset"))) changed.add(in.rhythmText());
            else if (a.containsKey("beats") || a.containsKey("offset")) changed.add("rythme enregistré (actif avec un tempo)");
            Boolean chained = bool(a, "chained");
            if (chained != null) {
                in.chained = chained;
                changed.add(chained ? "lancement enchaîné (noir, TP, titre, révélation à la fin de l'intro)"
                        : "la manche démarre tout de suite, l'intro joue par-dessus");
            }
            if (a.containsKey("look")) {
                in.look = fr.lordfinn.craftmaster.broadcast.BroadcastActions.effectId(a.get("look") == null ? "" : String.valueOf(a.get("look")));
                changed.add(in.look.isEmpty() ? "sans look commun" : "look « " + in.look + " » sur tous les plans");
            }
            Boolean typed = bool(a, "typedTask");
            if (typed != null) {
                in.typedTask = typed;
                changed.add(typed ? "consigne tapée en bas de l'image à la fin" : "sans consigne tapée");
            }
            if (changed.isEmpty()) throw new Invalid("rien à changer (auto, music, titleCard, bpm, beats, offset, chained, look ou typedTask).");
            line("Intro de « " + r.name + " » : " + String.join(", ", changed) + ".");
        }

        /** A shot of an intro from the action's fields (camera | path | follow, seconds, transition, effect, card, text). */
        private fr.lordfinn.craftmaster.data.MancheIntro.Shot introShot(Map<String, Object> a, fr.lordfinn.craftmaster.data.MancheIntro.Shot base) {
            String kind = base == null ? null : base.kind(), ref = base == null ? null : base.ref();
            if (Boolean.TRUE.equals(bool(a, "task"))) {
                kind = fr.lordfinn.craftmaster.data.MancheIntro.TASK;
                ref = "";
            }
            for (String[] k : new String[][]{{"camera", "camera"}, {"path", "trajet"}, {"follow", "suivi"}}) {
                Object v = a.get(k[0]);
                if (v == null || String.valueOf(v).isBlank()) continue;
                kind = k[1];
                ref = v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.longValue()) : String.valueOf(v).trim();
            }
            if (kind == null) throw new Invalid("donnez une caméra (« camera »), un trajet (« path »), un joueur à suivre (« follow ») ou le lieu de la tâche (« task »).");
            double seconds = base == null ? 0 : base.seconds();
            if (a.get("seconds") != null) {
                seconds = num(a, "seconds");
                if (seconds < 0 || seconds > fr.lordfinn.craftmaster.data.MancheIntro.MAX_SECONDS)
                    throw new Invalid("« seconds » doit être entre 0 (durée du trajet / 4 s) et 120.");
            }
            String transition = base == null ? "" : base.transition();
            if (a.get("transition") != null) {
                String t = String.valueOf(a.get("transition")).trim();
                transition = fr.lordfinn.craftmaster.broadcast.BroadcastActions.transitionId(
                        fr.lordfinn.craftmaster.broadcast.BroadcastActions.parseTransition(t));
            }
            String effect = base == null ? "" : base.effect();
            if (a.containsKey("effect")) effect = fr.lordfinn.craftmaster.broadcast.BroadcastActions.effectId(a.get("effect") == null ? "" : String.valueOf(a.get("effect")));
            String card = base == null ? "" : base.card(), text = base == null ? "" : base.text();
            if (a.containsKey("card")) card = fr.lordfinn.craftmaster.data.MancheIntro.normalizeCard(a.get("card") == null ? "" : String.valueOf(a.get("card")));
            if (a.containsKey("text")) text = a.get("text") == null ? "" : String.valueOf(a.get("text")).strip();
            if (text.length() > 80) throw new Invalid("texte de carte trop long (80 caractères au plus).");
            return new fr.lordfinn.craftmaster.data.MancheIntro.Shot(kind, ref, seconds, transition, effect, card, text);
        }

        private int introIndex(Manche r, Map<String, Object> a) {
            int i = requiredInt(a, "index");
            if (i < 1 || i > r.intro.shots.size())
                throw new Invalid("plan " + i + " inexistant (l'intro de « " + r.name + " » a " + r.intro.shots.size() + " plan(s)).");
            return i - 1;
        }

        /** intro.shot.add {manche?, camera | path | follow, seconds?, transition?, effect?, card?, text?, at?} */
        private void introShotAdd(Map<String, Object> a) {
            Manche r = introManche(a);
            if (r.intro.shots.size() >= fr.lordfinn.craftmaster.data.MancheIntro.MAX_SHOTS)
                throw new Invalid("une intro a " + fr.lordfinn.craftmaster.data.MancheIntro.MAX_SHOTS + " plans au plus.");
            fr.lordfinn.craftmaster.data.MancheIntro.Shot s = introShot(a, null);
            Integer at = integer(a, "at");
            int i = at == null ? r.intro.shots.size() : Math.max(0, Math.min(r.intro.shots.size(), at - 1));
            r.intro.shots.add(i, s);
            line("Intro de « " + r.name + " », plan " + (i + 1) + " : " + s.describe() + ".");
        }

        /** intro.shot.set {manche?, index, …fields to change} */
        private void introShotSet(Map<String, Object> a) {
            Manche r = introManche(a);
            int i = introIndex(r, a);
            fr.lordfinn.craftmaster.data.MancheIntro.Shot s = introShot(a, r.intro.shots.get(i));
            r.intro.shots.set(i, s);
            line("Intro de « " + r.name + " », plan " + (i + 1) + " : " + s.describe() + ".");
        }

        /** intro.shot.remove {manche?, index} */
        private void introShotRemove(Map<String, Object> a) {
            Manche r = introManche(a);
            int i = introIndex(r, a);
            r.intro.shots.remove(i);
            line("Intro de « " + r.name + " » : plan " + (i + 1) + " retiré (" + r.intro.shots.size() + " restant(s)).");
        }

        /** intro.shot.move {manche?, index, to}: the shot goes to place {@code to} (1 = first). */
        private void introShotMove(Map<String, Object> a) {
            Manche r = introManche(a);
            int i = introIndex(r, a);
            int to = Math.max(1, Math.min(r.intro.shots.size(), requiredInt(a, "to"))) - 1;
            if (to == i) throw new Invalid("le plan " + (i + 1) + " est déjà à cette place.");
            fr.lordfinn.craftmaster.data.MancheIntro.Shot s = r.intro.shots.remove(i);
            r.intro.shots.add(to, s);
            line("Intro de « " + r.name + " » : plan " + (i + 1) + " déplacé en " + (to + 1) + " (" + s.describe() + ").");
        }

        /** intro.clear {manche?} */
        private void introClear(Map<String, Object> a) {
            Manche r = introManche(a);
            if (r.intro.isEmpty()) throw new Invalid("« " + r.name + " » n'a pas d'intro.");
            r.intro = new fr.lordfinn.craftmaster.data.MancheIntro();
            line("Intro de « " + r.name + " » effacée.");
        }

        /**
         * intro.auto {manche?}: the intro made from the round's cameras ({@link fr.lordfinn.craftmaster.data.MancheIntro#autoBuild}):
         * its wide camera, up to 4 others, the studio's throne camera, the task location shot; replaces its shots.
         */
        private void introAuto(Map<String, Object> a) {
            Manche r = introManche(a);
            fr.lordfinn.craftmaster.camera.CameraList all = fr.lordfinn.craftmaster.camera.Cameras.list();
            fr.lordfinn.craftmaster.camera.CameraList lieu = all.lieu(r.id);
            fr.lordfinn.craftmaster.camera.CameraSpot wide = lieu.withRole(fr.lordfinn.craftmaster.camera.CameraSpot.WIDE);
            List<String> inserts = new ArrayList<>();
            for (fr.lordfinn.craftmaster.camera.CameraSpot c : lieu.all())
                if (!c.name().equals(fr.lordfinn.craftmaster.broadcast.TaskFraming.NAME)) {
                    if (wide == null) wide = c;
                    else if (!c.key().equals(wide.key())) inserts.add(c.key());
                }
            fr.lordfinn.craftmaster.camera.CameraSpot throne = all.group(fr.lordfinn.craftmaster.camera.CameraSpot.STUDIO)
                    .withRole(fr.lordfinn.craftmaster.camera.CameraSpot.THRONE);
            fr.lordfinn.craftmaster.data.MancheIntro built = fr.lordfinn.craftmaster.data.MancheIntro.autoBuild(
                    wide == null ? null : wide.key(), inserts, throne == null ? null : throne.key());
            r.intro = built;
            line("Intro de « " + r.name + " » construite : " + built.shots.size() + " plans sur la musique d'intro ("
                    + (wide == null ? "pas de caméra dans le lieu" : "large " + wide.key()) + ", " + Math.min(4, inserts.size()) + " insert(s), "
                    + (throne == null ? "pas de caméra trône" : "trône " + throne.key()) + ", lieu de la tâche à la fin)."
                    + (wide == null ? " Placez des caméras dans le lieu de la manche pour plus de plans." : "")
                    + " Jouée au lancement, enchaînée ; /tv intro " + r.id + " voir pour la retoucher.");
        }

        /** intro.play {manche?}: nothing changes in the data, the director plays it. */
        private void introPlay(Map<String, Object> a) {
            Season s = season(a, false);
            Manche r = mancheOrCurrent(s, a).manche();
            if (r.intro.shots.isEmpty()) throw new Invalid("« " + r.name + " » n'a pas encore d'intro (ajoutez des plans : intro.shot.add).");
            Map<String, Object> play = new LinkedHashMap<>();
            play.put("type", "intro.play");
            play.put("manche", r.id);
            Integer from = integer(a, "from");
            if (from != null) {
                if (from < 1 || from > r.intro.shots.size())
                    throw new Invalid("plan " + from + " inexistant (l'intro de « " + r.name + " » a " + r.intro.shots.size() + " plan(s)).");
                play.put("from", from);
            }
            effects.add(new Broadcast(play));
        }

        private void taskClear(Map<String, Object> a) {
            Season s = season(a, false);
            writable(s);
            Found f = manche(s, a, required(a, "manche"));
            if (f.manche().task == null) throw new Invalid("« " + f.manche().name + " » n'a pas de tâche.");
            f.manche().task = null;
            line("Tâche de « " + f.manche().name + " » effacée.");
        }

        private Found withTask(Map<String, Object> a, Season s) {
            Found f = taskManche(s, a);
            if (f.manche().task == null || f.manche().task.isEmpty())
                throw new Invalid("« " + f.manche().name + " » n'a pas encore de tâche écrite.");
            return f;
        }

        private void taskReveal(Map<String, Object> a) {
            Season s = season(a, false);
            Found f = withTask(a, s);
            effects.add(new TaskRevealed(s.id, f.manche().id, true));
        }

        private void taskHide(Map<String, Object> a) {
            Season s = season(a, false);
            Found f = taskManche(s, a);
            effects.add(new TaskHidden(s.id, f.manche().id));
        }

        private void taskRevealMore(Map<String, Object> a) {
            Season s = season(a, false);
            Found f = withTask(a, s);
            if (fr.lordfinn.craftmaster.task.TaskMarkup.secretCount(f.manche().task.text) == 0)
                throw new Invalid("la tâche de « " + f.manche().name + " » n'a pas de partie [secret].");
            effects.add(new TaskSecretShown(s.id, f.manche().id));
        }

        private void taskShow(Map<String, Object> a) {
            Season s = season(a, false);
            Found f = withTask(a, s);
            int seconds = intOr(a, "seconds", 12);
            if (seconds < 1 || seconds > 120) throw new Invalid("« seconds » : de 1 à 120 secondes.");
            effects.add(new TaskShown(s.id, f.manche().id, seconds));
            line("Carte de la tâche « " + f.manche().name + " » affichée " + seconds + " s.");
        }

        private void envelopeGive(Map<String, Object> a) {
            Season s = season(a, false);
            Found f = withTask(a, s);
            String wax = str(a, "wax");
            if (wax != null && fr.lordfinn.craftmaster.task.WaxColor.parse(wax) == null)
                throw new Invalid("couleur de cire « " + wax + " » inconnue.");
            List<String> players = strings(a, "players");
            if (players == null) players = List.of();
            List<String> out = new ArrayList<>();
            for (String p : players) {
                if (p.equalsIgnoreCase("all") || p.equalsIgnoreCase("tous") || p.equalsIgnoreCase("cast")) {
                    out.clear();
                    break;
                }
                SeasonPlayer cast = castMember(s, p);
                out.add(cast == null ? p : cast.name);
            }
            effects.add(new EnvelopesGiven(s.id, f.manche().id, out, wax == null ? null : fr.lordfinn.craftmaster.task.WaxColor.parse(wax).id()));
        }
    }
}

