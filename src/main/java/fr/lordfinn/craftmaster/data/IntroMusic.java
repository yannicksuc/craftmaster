package fr.lordfinn.craftmaster.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The intro musics: the original CraftMaster theme (« ré mi♭ ré — la si♭ la — sol fa♯ mi♭ ré ») in moods and in
 * worlds (sounds {@code craftmaster:theme.*}, note blocks, 13 to 19 s, -14 LUFS). They all end on the same « appel »:
 * the head of the theme, an upbeat, then a big D chord that opens the trial; the intro's last shot (the task location)
 * goes on it. Times from the scores (Downloads/fanfare-craftmaster, *.py: FIN), lengths from the .ogg files.
 * The task intro v2 ({@link MancheIntro#INTRO_MUSIC}) is also listed, with its own times.
 */
public final class IntroMusic {

    private IntroMusic() {}

    /**
     * One music.
     *
     * @param key      short name, also the sound's path after {@code theme.} ("maladresse", "univers.japonais")
     * @param label    its name in the menus
     * @param mood     what it sounds like
     * @param world    the world it is dressed for ("" = the theme's own colour)
     * @param use      what it suits
     * @param bpm      tempo (0 = not steady: the Greek one speeds up)
     * @param tempo    the tempo as told ("108", "72 → 176")
     * @param meter    beats in a bar as told
     * @param seconds  length of the file
     * @param appel    start of the « appel » (head of the theme): the task location shot starts there
     * @param chord    the final chord
     * @param end      end of the final chord (the file rings a little longer)
     * @param bars     starts of the bars before the appel (seconds): the cuts of an automatic intro fall on them
     */
    public record Track(String key, String label, String mood, String world, String use, double bpm, String tempo, String meter,
                        double seconds, double appel, double chord, double end, double[] bars) {
        /** Sound id played by the games. */
        public String sound() {
            return key.equals(MancheIntro.INTRO_MUSIC) ? "craftmaster:show.intro" : "craftmaster:theme." + key;
        }

        /**
         * Every beat of the music (seconds from its start, to its end): steady at {@link #bpm}; the Greek one speeds up
         * from 72 to 176 BPM over its first 28 beats (its score: m2-grec.py, {@code sec(b)}), then stays at 176.
         */
        public double[] beatTimes() {
            List<Double> out = new ArrayList<>();
            for (int k = 0; k < 400; k++) {
                double at = bpm > 0 ? k * 60 / bpm : greek(k);
                if (at > seconds + 1e-6) break;
                out.add(at);
            }
            double[] a = new double[out.size()];
            for (int k = 0; k < a.length; k++) a[k] = out.get(k);
            return a;
        }

        /** Second of beat {@code k} of the Greek one (72 → 176 BPM over 28 beats). */
        static double greek(double k) {
            double t0 = 72, t1 = 176, b = 28;
            if (k <= b) return 60 * b / (t1 - t0) * Math.log((t0 + (t1 - t0) * k / b) / t0);
            return greek(b) + (k - b) * 60 / t1;
        }

        /** "Maladresse · le thème au xylophone… · 108 BPM, 4 temps · 17,2 s · appel à 15,6 s" */
        public String describe() {
            return label + (world.isEmpty() ? "" : " (" + world + ")") + " · " + mood + " · " + tempo + " BPM, " + meter + " · "
                    + MancheIntro.fmt(seconds) + " · appel à " + MancheIntro.fmt(appel) + ", accord à " + MancheIntro.fmt(chord)
                    + " · pour : " + use;
        }
    }

    /** LordFinn's favourite of the theme's moods. */
    public static final String FAVOURITE = "maladresse";
    /** The music of an automatic intro when none is chosen (LordFinn, 20/11: maladresse, in place of the task intro v2). */
    public static final String DEFAULT_AUTO = FAVOURITE;
    /** Shortest shot of an automatic intro cut on the bars (seconds). */
    public static final double MIN_SHOT = 2.0;

    public static final List<Track> ALL = List.of(
            // les plans d'intro d'épreuve actuels (v2) : 5 mesures à 120, l'accord final à 8 s
            new Track(MancheIntro.INTRO_MUSIC, "Intro d'épreuve v2", "blocs musicaux, 5 mesures, accord sombre puis accord final", "",
                    "intro d'épreuve (l'actuelle)", 120, "120", "4 temps", 10, 8, 8, 10, new double[]{2, 4, 6}),
            // générique, présentation
            new Track("base", "Thème", "le thème de base, couleur fanfare (train, cuivres, flûte)", "", "générique, présentation", 112, "112", "4 temps", 14.501, 12.857, 13.929, 14.464, new double[]{2.143, 4.286, 6.429, 8.571, 10.714}),
            // épreuve de logique, de calme, d'énigme
            new Track("reflexion", "Réflexion", "lent, en suspens, flûte sur arpèges de harpe", "", "épreuve de logique, de calme, d'énigme", 92, "92", "4 temps", 15.675, 13.043, 14.348, 15.000, new double[]{2.609, 5.217, 7.826, 10.435}),
            // épreuve chronométrée, course, rush
            new Track("urgence", "Urgence", "martelé, ostinato serré qui monte", "", "épreuve chronométrée, course, rush", 144, "144", "4 temps", 14.747, 13.333, 14.167, 14.583, new double[]{1.667, 3.333, 5, 6.667, 8.333, 10, 11.667}),
            // la préférée ; épreuve drôle, bricolage, fabrication
            new Track("maladresse", "Maladresse", "le thème au xylophone, la reprise trébuche (wah-wah)", "", "la préférée ; épreuve drôle, bricolage, fabrication", 108, "108", "4 temps", 17.239, 15.556, 16.667, 17.222, new double[]{2.222, 4.444, 6.667, 8.889, 11.111, 13.333, 15.556}),
            // finale, épreuve de prestige, résultats
            new Track("victoire", "Victoire", "en majeur, cuivres et flûte, batterie de marche", "", "finale, épreuve de prestige, résultats", 126, "126", "4 temps", 16.766, 15.238, 16.190, 16.667, new double[]{1.905, 3.81, 5.714, 7.619, 9.524, 11.429, 13.333}),
            // avant un verdict, épreuve à éliminations
            new Track("suspense", "Suspense", "la tête du thème monte d'un demi-ton à chaque fois", "", "avant un verdict, épreuve à éliminations", 84, "84", "4 temps", 16.287, 14.286, 15.714, 16.429, new double[]{2.857, 5.714, 8.571, 11.429, 14.286}),
            // science-fiction, technologie, espace
            new Track("univers.futuriste", "Futuriste", "maladresse en synthwave : grosse caisse, basse en octaves, arpèges", "futuriste", "science-fiction, technologie, espace", 124, "124", "4 temps", 15.403, 13.548, 14.516, 15.000, new double[]{1.935, 3.871, 5.806, 7.742, 9.677, 11.613}),
            // Antiquité, mythologie, Méditerranée
            new Track("univers.grec", "Grec", "maladresse façon sirtaki, accélère du début à la fin, bouzouki", "grec", "Antiquité, mythologie, Méditerranée", 0, "72 → 176", "4 temps", 15.667, 14.439, 15.120, 15.461, new double[]{3.03, 5.581, 7.784, 9.721, 11.451, 13.014, 14.439}),
            // épreuve zen, jardin, Japon, minutie
            new Track("univers.japonais", "Japonais", "maladresse zen : koto, shamisen, gamme in", "japonais (zen)", "épreuve zen, jardin, Japon, minutie", 66, "66", "4 temps", 19.168, 16.364, 18.182, 19.091, new double[]{3.636, 7.273, 10.909, 14.545}),
            // machines, engrenages, fête foraine, inventions
            new Track("univers.steampunk", "Steampunk", "maladresse en valse à 3 temps, boîte à musique, orgue de manège", "steampunk", "machines, engrenages, fête foraine, inventions", 168, "168", "3 temps (valse)", 13.387, 11.786, 12.500, 12.857, new double[]{1.071, 2.143, 3.214, 4.286, 5.357, 6.429, 7.5, 8.571, 9.643, 10.714, 11.786}),
            // château, taverne, chevaliers
            new Track("univers.medieval", "Médiéval", "maladresse en barde de taverne, gigue à 6/8, luth", "médiéval", "château, taverne, chevaliers", 252, "252", "6/8, en croches", 14.373, 12.857, 13.810, 14.286, new double[]{1.429, 2.857, 4.286, 5.714, 7.143, 8.571, 10, 11.429}),
            // Far West, ranch, désert
            new Track("univers.western", "Western", "maladresse western : banjo, guitare en contretemps, sabots", "western", "Far West, ranch, désert", 120, "120", "4 temps", 15.547, 14.000, 15.000, 15.500, new double[]{2, 4, 6, 8, 10, 12}));

    /** The track for a name: "maladresse", "theme.maladresse", "craftmaster:theme.univers.japonais", "japonais", "zen"…; null if none. */
    public static Track find(String name) {
        if (name == null) return null;
        String k = fold(name);
        if (k.startsWith("craftmaster:")) k = k.substring("craftmaster:".length());
        if (k.equals("show.intro") || k.equals("musique intro") || k.equals("musique_intro")) k = MancheIntro.INTRO_MUSIC;
        if (k.startsWith("theme.")) k = k.substring("theme.".length());
        if (k.equals("theme") || k.equals("thème")) return null;     // the show's theme (show.theme), not one of these
        if (k.equals("zen")) k = "japonais";
        if (k.equals("defaut") || k.equals("par defaut")) k = FAVOURITE;
        for (Track t : ALL) {
            String key = fold(t.key());
            if (key.equals(k) || key.equals("univers." + k) || fold(t.label()).equals(k)) return t;
        }
        return null;
    }

    /** The track of an intro's music cue (as stored: "intro" or a sound id), or null. */
    public static Track ofCue(String music) {
        if (music == null || music.isEmpty()) return null;
        if (music.equals(MancheIntro.INTRO_MUSIC)) return find(MancheIntro.INTRO_MUSIC);
        return music.startsWith("craftmaster:theme.") ? find(music) : null;
    }

    /** Names typed in the commands (Tab). */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Track t : ALL) out.add(t.key());
        return out;
    }

    /**
     * Starts of {@code wanted} shots before the appel, on bar starts, each at least {@link #MIN_SHOT} long: the later
     * shots one or two bars each, the first one takes what is left. Fewer starts when the music is too short for them.
     */
    public static List<Double> cuts(Track t, int wanted) {
        List<Double> back = new ArrayList<>();
        double end = t.appel();
        for (int k = 1; k < wanted; k++) {
            double best = -1;
            for (double c : t.bars()) if (c <= end - MIN_SHOT + 1e-6 && c > best) best = c;
            if (best < MIN_SHOT - 1e-6) break;
            back.add(best);
            end = best;
        }
        List<Double> out = new ArrayList<>();
        out.add(0.0);
        for (int k = back.size() - 1; k >= 0; k--) out.add(back.get(k));
        return wanted <= 0 ? List.of() : out;
    }

    static String fold(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
