package fr.lordfinn.craftmaster.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A stage look ("ambiance"), like a memory of a lighting desk: the state of the spotlights, curtains, hanging
 * portraits and named screens when it was saved. A part left null was not saved and is not touched when the look is
 * played (a "lights only" look leaves the curtains as they are). Saved as JSON (Gson) in config/craftmaster/scenographie.json.
 */
public final class Look {

    /** One spotlight, found again by its place. */
    public static final class Spot {
        public String world;
        public int x, y, z;
        public String name = "";
        public boolean lit;
        public int color, intensity;
        public float cone, yaw, pitch;
        public String follow = "";
    }

    public static final String LIGHTS = "lumieres", CURTAINS = "rideaux", PORTRAITS = "tableaux", SCREENS = "ecrans";
    public static final List<String> PARTS = List.of(LIGHTS, CURTAINS, PORTRAITS, SCREENS);

    public List<Spot> spots;
    /** Curtain name → open. */
    public Map<String, Boolean> curtains;
    /** Portrait UUID → string length (blocks, -1 automatic). */
    public Map<String, Float> portraits;
    /** Screen name → what it shows (address, empty = test card). */
    public Map<String, String> screens;
    /** Default fade when played without one (seconds). */
    public double fade = 2;

    /** "lumières", "lights", "rideau"… → the stored part, or null. */
    public static String part(String word) {
        String k = fr.lordfinn.craftmaster.camera.CameraList.fold(word == null ? "" : word.strip()).toLowerCase(Locale.ROOT);
        return switch (k) {
            case "lumieres", "lumiere", "lights", "projecteurs", "projos", "projecteur", "spots" -> LIGHTS;
            case "rideaux", "rideau", "curtains" -> CURTAINS;
            case "tableaux", "tableau", "cadres", "portraits" -> PORTRAITS;
            case "ecrans", "ecran", "teles", "tele", "screens" -> SCREENS;
            default -> null;
        };
    }

    /** What the look holds, in French. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (spots != null) parts.add(spots.size() + " projecteur(s)");
        if (curtains != null) parts.add(curtains.size() + " rideau(x)");
        if (portraits != null) parts.add(portraits.size() + " tableau(x)");
        if (screens != null) parts.add(screens.size() + " écran(s)");
        return parts.isEmpty() ? "vide" : String.join(", ", parts) + ", fondu " + secondsText(fade);
    }

    static String secondsText(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(Math.round(s * 10) / 10.0).replace('.', ',')) + " s";
    }

    /** Everything saved for the show's stage: looks, cue lists and what plays them automatically. */
    public static final class Book {
        public Map<String, Look> looks = new LinkedHashMap<>();
        public Map<String, List<String>> cues = new LinkedHashMap<>();
        /** Show event → cue list played then (see {@link Cues#EVENTS}). */
        public Map<String, String> auto = new LinkedHashMap<>();
        /** Round id → cue list played when that round starts (instead of the "lancement" one). */
        public Map<String, String> rounds = new LinkedHashMap<>();
    }
}
