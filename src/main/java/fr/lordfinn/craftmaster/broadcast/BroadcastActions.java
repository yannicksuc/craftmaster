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
 * camera.cut        {camera, transition?, effect?}  camera: number, name or role ("2", "Plan large", "large", "trone"),
 *                                                in the focus; or elsewhere: "studio:2", "ensuri:1", "caméra 2 du studio"
 * camera.follow     {player, transition?, effect?}  follow shot on a player (in a round: selects their zone too)
 * camera.pov        {player, transition?, effect?}  through a player's eyes
 * camera.path       {path, transition?, effect?}    plays a recorded travelling
 * camera.mosaic     {lieu?, enabled?}            split screen of the zones (or cameras) of a lieu; enabled false = off
 * camera.focus      {lieu?, zone?, player?, cut?}   lieu / zone the keys address: lieu "auto" = follow the show,
 *                                                zone "next" / "prev"; player = their zone; cut = show its wide camera
 * camera.lieu       {camera, lieu, zone?}        moves a camera to the studio or a round (and a zone of it)
 * camera.auto       {enabled}                   automatic direction on / off (on: also ends camera.stay and camera.framing)
 * camera.goto       {player}                    « on va voir Ensuri » : a placed camera that sees them well, else following them
 * camera.stay       {player?, enabled?}         the automatic direction stays on a player (enabled false / no player = stop)
 * camera.framing    {size}                      automatic framing: wider | tighter | auto
 * camera.avoid      {camera, enabled?}          the automatic choices never take this camera (enabled false = they may again)
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
            "broadcast.lowerthird", "broadcast.task", "broadcast.scores", "broadcast.letterbox", "show.intro", "show.outro", "show.stop",
            "camera.pov", "camera.path", "camera.mosaic", "camera.focus", "camera.lieu", "camera.voice",
            "camera.goto", "camera.stay", "camera.framing", "camera.avoid");

    public static boolean handles(String type) {
        return TYPES.contains(type);
    }

    /** Checks an action's fields and returns them normalized (type included); throws {@link Actions.Invalid}. */
    public static Map<String, Object> validate(String type, Map<String, Object> a) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        switch (type) {
            case "camera.cut" -> {
                out.put("camera", cameraRef(a));
                transition(a, out, false);
                effect(a, out);
            }
            case "camera.follow", "camera.pov" -> {
                out.put("player", required(a, "player"));
                transition(a, out, false);
                effect(a, out);
            }
            case "camera.path" -> {
                out.put("path", required(a, "path"));
                transition(a, out, false);
                effect(a, out);
            }
            case "camera.mosaic" -> {
                String lieu = str(a, "lieu");
                if (lieu != null) out.put("lieu", lieu);
                if (a.get("enabled") != null) out.put("enabled", bool(a, "enabled", false));
                transition(a, out, false);
            }
            case "camera.focus" -> {
                String lieu = str(a, "lieu"), zone = str(a, "zone"), player = str(a, "player");
                if (lieu == null && zone == null && player == null)
                    throw new Actions.Invalid("donnez un lieu (« lieu » : studio, une manche ou auto), une zone ou un joueur.");
                if (lieu != null) out.put("lieu", lieu);
                if (zone != null) out.put("zone", zone);
                if (player != null) out.put("player", player);
                if (a.get("cut") != null) out.put("cut", bool(a, "cut", false));
            }
            case "camera.lieu" -> {
                out.put("camera", cameraRef(a));
                out.put("lieu", required(a, "lieu"));
                String zone = str(a, "zone");
                if (zone != null) out.put("zone", zone);
            }
            case "camera.auto", "camera.voice" -> out.put("enabled", bool(a, "enabled", true));
            case "camera.goto" -> out.put("player", required(a, "player"));
            case "camera.stay" -> {
                String player = str(a, "player");
                boolean on = a.get("enabled") == null ? player != null : bool(a, "enabled", false);
                out.put("enabled", on);
                if (on) out.put("player", required(a, "player"));
            }
            case "camera.framing" -> out.put("size", framingSize(required(a, "size")));
            case "camera.avoid" -> {
                out.put("camera", cameraRef(a));
                out.put("enabled", a.get("enabled") == null || bool(a, "enabled", false));
            }
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

    /** "plus large" / "wider" → wider, "plus serré" / "tighter" → tighter, "auto" / "normal" → auto. */
    static String framingSize(String s) {
        String t = CameraList.fold(s).replaceAll("[_-]+", " ").strip();
        return switch (t) {
            case "wider", "large", "plus large", "larger", "wide", "+1", "1" -> "wider";
            case "tighter", "serre", "plus serre", "tight", "close", "proche", "plus pres", "-1" -> "tighter";
            case "auto", "normal", "automatique", "0", "defaut" -> "auto";
            default -> throw new Actions.Invalid("cadrage « " + s + " » inconnu (wider, tighter ou auto).");
        };
    }

    /**
     * "coupe" / "cut" → 0, "fondu" / "fade" / "noir" → 1, "enchaîné" / "crossfade" / "dissolve" → 2,
     * "miroir" / "kaléidoscope" → 3, "flash" / "flash blanc" → 4, "surimpression" / "double exposition" → 5.
     */
    public static int parseTransition(String s) {
        String t = CameraList.fold(s).replaceAll("[_-]+", " ").strip();
        return switch (t) {
            case "cut", "coupe", "sec", "coupe seche", "0" -> BroadcastNetwork.CUT;
            case "fade", "fondu", "noir", "fondu au noir", "1" -> BroadcastNetwork.FADE;
            case "crossfade", "enchaine", "fondu enchaine", "dissolve", "mix", "2" -> BroadcastNetwork.CROSSFADE;
            case "miroir", "miroirs", "kaleidoscope", "kaleido", "mirror", "3" -> BroadcastNetwork.MIRROR;
            case "flash", "flash blanc", "blanc", "white flash", "white", "4" -> BroadcastNetwork.FLASH;
            case "surimpression", "surimpressions", "double exposition", "double exposure", "superposition", "overlay", "5" ->
                    BroadcastNetwork.DOUBLE_EXPOSURE;
            default -> throw new Actions.Invalid("transition « " + s + " » inconnue (" + String.join(", ", TRANSITION_IDS) + ").");
        };
    }

    /** The stored names of the transitions, by code (what {@link #transitionId} gives back). */
    public static final List<String> TRANSITION_IDS = List.of("coupe", "fondu", "enchaine", "miroir", "flash", "surimpression");

    /** Code → the name saved in the data (intro shots): "coupe", "fondu", "enchaine", "miroir", "flash", "surimpression". */
    public static String transitionId(int t) {
        return t >= 0 && t < TRANSITION_IDS.size() ? TRANSITION_IDS.get(t) : "coupe";
    }

    /** Code → French name for messages. */
    public static String transitionName(int t) {
        return switch (t) {
            case BroadcastNetwork.FADE -> "fondu";
            case BroadcastNetwork.CROSSFADE -> "fondu enchaîné";
            case BroadcastNetwork.MIRROR -> "miroir";
            case BroadcastNetwork.FLASH -> "flash";
            case BroadcastNetwork.DOUBLE_EXPOSURE -> "surimpression";
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

    /** A camera reference; a JSON number (2.0) becomes "2". */
    private static String cameraRef(Map<String, Object> a) {
        Object v = a.get("camera");
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return String.valueOf(n.longValue());
        return required(a, "camera");
    }

    private static void effect(Map<String, Object> a, Map<String, Object> out) {
        String e = effectId(str(a, "effect"));
        if (!e.isEmpty()) out.put("effect", e);
    }

    /**
     * A screen effect id as the client's effects know them ("Noir et blanc" → "noir_blanc"): lower case, no accents,
     * underscores; "", "aucun", "none" → "". Unknown ids are kept (the client ignores what it does not have).
     */
    public static String effectId(String e) {
        if (e == null) return "";
        // A combination ("macro+look_intro", an intro shot's effect with the intro's look): each part on its own
        if (e.indexOf('+') >= 0 || e.indexOf(',') >= 0) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (String p : e.split("[+,]")) {
                String id = effectId(p);
                if (!id.isEmpty() && !parts.contains(id)) parts.add(id);
            }
            return String.join("+", parts);
        }
        String k = CameraList.fold(e).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return switch (k) {
            case "", "aucun", "aucune", "none", "non", "off", "sans", "normal" -> "";
            case "noir_et_blanc", "nb", "black_and_white", "grayscale", "gris" -> "noir_blanc";
            default -> k;
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
