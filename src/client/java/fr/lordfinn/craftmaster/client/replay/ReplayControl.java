package fr.lordfinn.craftmaster.client.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.client.broadcast.ShowStateClient;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.Marker;
import fr.lordfinn.craftmaster.replay.ReplayNetwork.ReplayStatus;

import java.util.Locale;

/**
 * The replay's orders from the voice control and the Stream Deck, run on this game (while a replay plays, Server
 * Replay's viewer lets nothing but commands reach the server, so the show's actions cannot go there). The voice
 * control's actions, as its service sends them:
 * <ul>
 *   <li>{@code replay.control} {do: play | pause | toggle | back | forward | goto | speed | faster | slower |
 *   next_marker | prev_marker | start | view | mode | close, seconds, speed, view, mode};</li>
 *   <li>{@code camera.cut} {camera}, {@code camera.follow} / {@code camera.pov} {player}, {@code camera.auto}
 *   {enabled}: the replay's direction, here;</li>
 *   <li>anything else (scores, rounds…) cannot be done during a replay: said so.</li>
 * </ul>
 */
public final class ReplayControl {

    private ReplayControl() {}

    /** What one action did: a line for the voice control's panel, and whether it worked. */
    public record Result(boolean ok, String line) {
        static Result ok(String line) {
            return new Result(true, "✓ " + line);
        }

        static Result no(String line) {
            return new Result(false, "✗ " + line);
        }
    }

    public static Result run(JsonObject a) {
        String type = str(a, "type");
        try {
            return switch (type) {
                case "replay.control" -> control(a);
                case "camera.cut" -> ReplayDirector.camera(str(a, "camera")) ? Result.ok("Caméra " + str(a, "camera"))
                        : Result.no("Pas de caméra « " + str(a, "camera") + " » ici");
                case "camera.follow", "camera.pov" -> {
                    String who = ReplayDirector.player(str(a, "player"), 0, type.equals("camera.pov"));
                    yield who == null ? Result.no(str(a, "player") + " n'est pas dans ce replay")
                            : Result.ok((type.equals("camera.pov") ? "Vue de " : "On suit ") + who);
                }
                case "camera.auto" -> {
                    boolean on = !a.has("enabled") || a.get("enabled").getAsBoolean();
                    ReplayClient.setMode(on ? ReplayDirector.Mode.AUTO : ReplayDirector.Mode.DIRECT);
                    yield Result.ok("Réalisation " + (on ? "auto" : "du direct"));
                }
                default -> Result.no("Pas pendant un replay (" + type + ")");
            };
        } catch (RuntimeException e) {
            return Result.no(type + " : " + e.getMessage());
        }
    }

    private static Result control(JsonObject a) {
        String what = str(a, "do").toLowerCase(Locale.ROOT);
        ReplayStatus s = ReplayClient.status();
        switch (what) {
            case "play" -> ReplayClient.setPaused(false);
            case "pause" -> ReplayClient.setPaused(true);
            case "toggle" -> ReplayClient.togglePause();
            case "back", "forward" -> {
                long sec = a.has("seconds") ? Math.abs(a.get("seconds").getAsLong()) : 10;
                ReplayClient.step((what.equals("back") ? -sec : sec) * 1000);
            }
            case "goto" -> {
                if (!a.has("seconds")) return Result.no("À quel moment ?");
                ReplayClient.seek(a.get("seconds").getAsLong() * 1000);
                ReplayBar.caption(ReplayClient.time(a.get("seconds").getAsLong() * 1000));
            }
            case "start" -> {
                ReplayClient.seek(0);
                ReplayBar.caption("Début");
            }
            case "speed" -> {
                float x = a.has("speed") ? Float.parseFloat(str(a, "speed").replace(',', '.').replace("x", "").replace("×", "")) : 1;
                ReplayClient.speed(x);
            }
            case "faster" -> ReplayClient.stepSpeed(1);
            case "slower" -> ReplayClient.stepSpeed(-1);
            case "next_marker" -> {
                if (!ReplayClient.marker(1)) return Result.no("Plus de repère après");
            }
            case "prev_marker" -> ReplayClient.marker(-1);
            case "view" -> {
                String v = str(a, "view");
                if (v.isEmpty() || v.equalsIgnoreCase("zone")) v = "zone";
                if (!ReplayClient.view(v)) return Result.no("Pas de fichier « " + v + " » dans ce replay"
                        + (s == null ? "" : " (" + String.join(", ", s.views()) + ")"));
            }
            case "mode" -> {
                ReplayDirector.Mode m = switch (CameraSpotFold.fold(str(a, "mode"))) {
                    case "direct" -> ReplayDirector.Mode.DIRECT;
                    case "auto", "automatique" -> ReplayDirector.Mode.AUTO;
                    case "manuel", "manual" -> ReplayDirector.Mode.MANUAL;
                    case "libre", "free" -> ReplayDirector.Mode.FREE;
                    default -> null;
                };
                if (m == null) return Result.no("Réalisation « " + str(a, "mode") + " » inconnue");
                ReplayClient.setMode(m);
            }
            case "close" -> ReplayClient.close();
            default -> {
                return Result.no("Ordre de replay inconnu : " + what);
            }
        }
        return Result.ok(describe(what, a));
    }

    private static String describe(String what, JsonObject a) {
        return switch (what) {
            case "play" -> "Lecture";
            case "pause" -> "Pause";
            case "toggle" -> ReplayClient.paused() ? "Pause" : "Lecture";
            case "back" -> "Recul de " + (a.has("seconds") ? a.get("seconds").getAsLong() : 10) + " s";
            case "forward" -> "Avance de " + (a.has("seconds") ? a.get("seconds").getAsLong() : 10) + " s";
            case "goto" -> "À " + ReplayClient.time(a.get("seconds").getAsLong() * 1000);
            case "start" -> "Au début";
            case "speed", "faster", "slower" -> "Vitesse changée";
            case "next_marker" -> "Repère suivant";
            case "prev_marker" -> "Repère précédent";
            case "view" -> "Fichier : " + (str(a, "view").isEmpty() ? "zone" : str(a, "view"));
            case "mode" -> "Réalisation : " + str(a, "mode");
            case "close" -> "Replay fermé";
            default -> what;
        };
    }

    /**
     * What the voice service needs to understand an order during a replay (instead of the server's context, which
     * cannot be asked for then): the contestants, the cameras, and the replay itself.
     */
    public static String contextJson() {
        JsonObject o = new JsonObject();
        o.addProperty("now", System.currentTimeMillis());
        JsonArray players = new JsonArray();
        for (String name : ReplayDirector.contestants()) {
            JsonObject p = new JsonObject();
            p.addProperty("pseudo", name);
            p.addProperty("online", true);
            players.add(p);
        }
        o.add("players", players);
        JsonObject r = new JsonObject();
        ReplayStatus s = ReplayClient.status();
        if (s != null) {
            r.addProperty("title", s.title());
            r.addProperty("view", s.view());
            JsonArray views = new JsonArray();
            for (String v : s.views()) views.add(v);
            r.add("views", views);
            r.addProperty("time", ReplayClient.time(ReplayClient.nowMs()));
            r.addProperty("duration", ReplayClient.time(s.duration()));
            r.addProperty("paused", ReplayClient.paused());
            r.addProperty("speed", s.speed());
        }
        r.addProperty("direction", ReplayDirector.mode().label);
        JsonArray markers = new JsonArray();
        for (Marker m : ReplayClient.markers()) markers.add(ReplayClient.time(m.ms()) + " " + m.name());
        r.add("markers", markers);
        JsonArray cams = new JsonArray();
        for (CameraSpot c : ShowStateClient.focused().all()) cams.add(c.number() + " " + c.label());
        r.add("cameras", cams);
        o.add("replay", r);
        return o.toString();
    }

    /** Runs a list of actions (the voice control's), each giving its line. */
    public static java.util.List<Result> runAll(String actionsJson) {
        java.util.List<Result> out = new java.util.ArrayList<>();
        for (JsonElement e : com.google.gson.JsonParser.parseString(actionsJson).getAsJsonArray())
            if (e.isJsonObject()) out.add(run(e.getAsJsonObject()));
        return out;
    }

    private static String str(JsonObject a, String key) {
        return a.has(key) && !a.get(key).isJsonNull() ? a.get(key).getAsString() : "";
    }

    /** Lower case, no accents. */
    private static final class CameraSpotFold {
        static String fold(String s) {
            return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        }
    }
}
