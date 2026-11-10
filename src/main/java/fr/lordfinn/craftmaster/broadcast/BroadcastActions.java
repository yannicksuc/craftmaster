package fr.lordfinn.craftmaster.broadcast;

import fr.lordfinn.craftmaster.camera.CameraList;
import fr.lordfinn.craftmaster.show.Actions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV direction's actions of the show schema (chantier E), checked without a server: they change no data (nothing
 * to undo), they become an {@link Actions.Broadcast} effect that {@link Director} plays. Which camera or player a
 * name means is only known in the world, so the director resolves it (and says so when it cannot).
 * <pre>
 * camera.cut        {camera, transition?}       camera: number, name or role ("2", "Plan large", "large", "trone")
 * camera.follow     {player, transition?}       follow shot on a player
 * camera.auto       {enabled}                   automatic direction on / off
 * camera.transition {transition}                default transition: coupe | fondu | enchaine
 * broadcast.lowerthird {player? | text?, subtitle?, seconds?}
 * broadcast.task    {seconds?}                  task card on the broadcast (chantier D's card)
 * broadcast.scores  {mode}                      auto | on | off | show (show it now)
 * broadcast.letterbox {enabled}                 cinema bars
 * show.intro {} · show.outro {} · show.stop {}  opening / closing credits, stop them
 * </pre>
 */
public final class BroadcastActions {

    private BroadcastActions() {}

    public static final List<String> TYPES = List.of("camera.cut", "camera.follow", "camera.auto", "camera.transition",
            "broadcast.lowerthird", "broadcast.task", "broadcast.scores", "broadcast.letterbox", "show.intro", "show.outro", "show.stop");

    public static boolean handles(String type) {
        return TYPES.contains(type);
    }

    /** Checks an action's fields and returns them normalized (type included); throws {@link Actions.Invalid}. */
    public static Map<String, Object> validate(String type, Map<String, Object> a) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        switch (type) {
            case "camera.cut" -> {
                out.put("camera", required(a, "camera"));
                transition(a, out, false);
            }
            case "camera.follow" -> {
                out.put("player", required(a, "player"));
                transition(a, out, false);
            }
            case "camera.auto" -> out.put("enabled", bool(a, "enabled", true));
            case "camera.transition" -> transition(a, out, true);
            case "broadcast.lowerthird" -> {
                String player = str(a, "player"), text = str(a, "text");
                if (player == null && text == null) throw new Actions.Invalid("donnez un joueur (« player ») ou un texte (« text »).");
                if (player != null) out.put("player", player);
                if (text != null) out.put("text", text);
                String sub = str(a, "subtitle");
                if (sub != null) out.put("subtitle", sub);
                out.put("seconds", seconds(a, 7));
            }
            case "broadcast.task" -> out.put("seconds", seconds(a, 12));
            case "broadcast.scores" -> {
                String mode = str(a, "mode");
                out.put("mode", mode == null ? "show" : scoresMode(mode));
                out.put("seconds", seconds(a, 12));
            }
            case "broadcast.letterbox" -> out.put("enabled", bool(a, "enabled", true));
            case "show.intro", "show.outro", "show.stop" -> {}
            default -> throw new Actions.Invalid("action inconnue « " + type + " ».");
        }
        return out;
    }

    /** "coupe" / "cut" → 0, "fondu" / "fade" / "noir" → 1, "enchaîné" / "crossfade" / "dissolve" → 2. */
    public static int parseTransition(String s) {
        String t = CameraList.fold(s);
        return switch (t) {
            case "cut", "coupe", "sec", "coupe seche", "0" -> BroadcastNetwork.CUT;
            case "fade", "fondu", "noir", "fondu au noir", "1" -> BroadcastNetwork.FADE;
            case "crossfade", "enchaine", "fondu enchaine", "dissolve", "mix", "2" -> BroadcastNetwork.CROSSFADE;
            default -> throw new Actions.Invalid("transition « " + s + " » inconnue (coupe, fondu ou enchaine).");
        };
    }

    public static String transitionName(int t) {
        return switch (t) {
            case BroadcastNetwork.FADE -> "fondu";
            case BroadcastNetwork.CROSSFADE -> "fondu enchaîné";
            default -> "coupe";
        };
    }

    private static String scoresMode(String s) {
        return switch (CameraList.fold(s)) {
            case "auto", "automatique" -> "auto";
            case "on", "toujours", "oui", "true", "always" -> "on";
            case "off", "jamais", "non", "false", "never", "cache" -> "off";
            case "show", "montre", "affiche", "maintenant", "now" -> "show";
            default -> throw new Actions.Invalid("mode « " + s + " » inconnu (auto, on, off ou show).");
        };
    }

    private static void transition(Map<String, Object> a, Map<String, Object> out, boolean required) {
        String t = required ? required(a, "transition") : str(a, "transition");
        if (t != null) out.put("transition", parseTransition(t));
    }

    private static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String required(Map<String, Object> a, String key) {
        String s = str(a, key);
        if (s == null) throw new Actions.Invalid("champ « " + key + " » manquant.");
        return s;
    }

    private static boolean bool(Map<String, Object> a, String key, boolean required) {
        Object v = a.get(key);
        if (v == null) {
            if (required) throw new Actions.Invalid("champ « " + key + " » manquant.");
            return false;
        }
        if (v instanceof Boolean b) return b;
        return switch (CameraList.fold(String.valueOf(v))) {
            case "true", "oui", "yes", "1", "vrai", "on" -> true;
            case "false", "non", "no", "0", "faux", "off" -> false;
            default -> throw new Actions.Invalid("« " + key + " » doit être vrai ou faux (reçu : " + v + ").");
        };
    }

    private static int seconds(Map<String, Object> a, int def) {
        Object v = a.get("seconds");
        if (v == null) return def;
        double d;
        try {
            d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new Actions.Invalid("« seconds » doit être un nombre (reçu : " + v + ").");
        }
        if (d <= 0 || d > 600) throw new Actions.Invalid("« seconds » doit être entre 1 et 600.");
        return (int) Math.round(d);
    }
}
