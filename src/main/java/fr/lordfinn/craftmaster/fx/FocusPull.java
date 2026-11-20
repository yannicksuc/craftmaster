package fr.lordfinn.craftmaster.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A controllable focus pull for the depth of field effects (macro, mise_au_point, mise_au_point_inverse): the focus
 * goes from one target to another, after a delay, over a duration, with an easing; the blur amount and whether the
 * focus is kept at the end (else the blur fades out: everything sharp).
 *
 * <p>Targets: {@code centre} (what is at the centre of the picture), {@code fond} (the background behind it), a
 * distance in blocks ({@code d:12}), a point of the world ({@code p:x,y,z}), an entity ({@code e:<uuid>}) or a player
 * ({@code j:<pseudo>}); the camera's distance to a point, an entity or a player is measured every frame.
 *
 * <p>Canonical text (stored in an intro shot, carried after a {@code |} in a shot's effect to the games):
 * {@code de=centre vers=fond delai=0.4 duree=1.2 courbe=lisse flou=100 garder=oui}.
 */
public record FocusPull(String from, String to, double delay, double duration, String easing, int blur, boolean keep) {

    public static final String CENTRE = "centre", BACKGROUND = "fond";
    public static final List<String> EASINGS = List.of("lisse", "lineaire", "entree", "sortie");
    /** The depth of field effects a focus pull drives. */
    public static final List<String> DOF_EFFECTS = List.of("macro", "mise_au_point", "mise_au_point_inverse");
    public static final double MAX_SECONDS = 30;
    public static final int MAX_BLUR = 300;

    public FocusPull {
        from = from == null || from.isBlank() ? CENTRE : from;
        to = to == null || to.isBlank() ? from : to;
        delay = finite(delay, 0.4);
        duration = Math.max(0.05, finite(duration, 1.2));
        easing = EASINGS.contains(easing) ? easing : "lisse";
        blur = Math.max(0, Math.min(MAX_BLUR, blur));
    }

    private static double finite(double v, double fallback) {
        return Double.isFinite(v) ? Math.max(0, Math.min(MAX_SECONDS, v)) : fallback;
    }

    /** The presets as focus pulls: mise_au_point (background → centre), the reverse one, macro (centre). */
    public static FocusPull preset(String effect) {
        return switch (effect) {
            case "mise_au_point" -> new FocusPull(BACKGROUND, CENTRE, 0.4, 1.2, "lisse", 100, true);
            case "mise_au_point_inverse" -> new FocusPull(CENTRE, BACKGROUND, 0.4, 1.2, "lisse", 100, true);
            default -> new FocusPull(CENTRE, CENTRE, 0, 0.05, "lisse", 100, true);
        };
    }

    // ---------------------------------------------------------------- text

    public String canonical() {
        return "de=" + from + " vers=" + to + " delai=" + num(delay) + " duree=" + num(duration) + " courbe=" + easing
                + " flou=" + blur + " garder=" + (keep ? "oui" : "non");
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceFirst("\\.?0+$", "");
    }

    /** From the canonical text (lenient: what is unknown keeps its default); null when blank. */
    public static FocusPull parseCanonical(String text) {
        if (text == null || text.isBlank()) return null;
        Map<String, String> kv = new LinkedHashMap<>();
        for (String part : text.trim().split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq > 0) kv.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return new FocusPull(kv.get("de"), kv.get("vers"), toDouble(kv.get("delai"), 0.4), toDouble(kv.get("duree"), 1.2),
                kv.get("courbe"), (int) toDouble(kv.get("flou"), 100), !"non".equals(kv.get("garder")));
    }

    private static double toDouble(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** "du centre vers le fond, après 0,4 s en 1,2 s (lisse), flou 100 %" */
    public String describe() {
        return "de " + targetName(from) + " vers " + targetName(to) + ", après " + fr(delay) + " s en " + fr(duration) + " s ("
                + easing + "), flou " + blur + " %" + (keep ? "" : ", puis tout net");
    }

    private static String fr(double v) {
        return num(v).replace('.', ',');
    }

    public static String targetName(String t) {
        if (t.equals(CENTRE)) return "centre";
        if (t.equals(BACKGROUND)) return "fond";
        if (t.startsWith("d:")) return t.substring(2) + " blocs";
        if (t.startsWith("p:")) return "point " + t.substring(2).replace(',', ' ');
        if (t.startsWith("e:")) return "entité";
        if (t.startsWith("j:")) return t.substring(2);
        return t;
    }

    // ---------------------------------------------------------------- typed by an organiser

    /** What a typed word needs from the server: the point or the entity the organiser looks at. */
    public interface Picker {
        /** "p:x,y,z" of what they look at ("ici"), or null. */
        String point();

        /** "e:<uuid>" of the entity they look at, or null. */
        String entity();

        /** "j:<pseudo>" when this is a connected player, or null. */
        String player(String name);
    }

    /**
     * Changes {@code base} (null = the preset of the shot's effect) from words: {@code de <cible>}, {@code vers
     * <cible>}, {@code duree <x>}, {@code delai <x>}, {@code flou <pourcent>}, {@code courbe <c>}, {@code garder
     * oui|non}; a target is centre, fond, a distance (12, 12b, 12 blocs), ici (the point looked at), entite (the
     * entity looked at), x y z, or a player's name; a time is in seconds (1.5, 1.5s) or beats (2t, 2 temps) at
     * {@code beatSeconds}.
     *
     * @throws IllegalArgumentException with a French message
     */
    public static FocusPull parseWords(String words, FocusPull base, double beatSeconds, Picker picker) {
        FocusPull f = base == null ? preset("mise_au_point") : base;
        String from = f.from, to = f.to, easing = f.easing;
        double delay = f.delay, duration = f.duration;
        int blur = f.blur;
        boolean keep = f.keep;
        List<String> w = new ArrayList<>(List.of(words.trim().toLowerCase(Locale.ROOT).split("\\s+")));
        if (w.size() == 1 && w.getFirst().isEmpty()) throw new IllegalArgumentException("rien à régler (de, vers, duree, delai, flou, courbe, garder).");
        int i = 0;
        while (i < w.size()) {
            String key = w.get(i++);
            switch (key) {
                case "de", "depuis", "du" -> {
                    int[] used = new int[1];
                    from = target(w, i, picker, used);
                    i += used[0];
                }
                case "vers", "a", "à", "jusqu'a", "puis" -> {
                    int[] used = new int[1];
                    to = target(w, i, picker, used);
                    i += used[0];
                }
                case "duree", "durée", "en" -> {
                    int[] used = new int[1];
                    duration = time(w, i, beatSeconds, used);
                    i += used[0];
                }
                case "delai", "délai", "apres", "après" -> {
                    int[] used = new int[1];
                    delay = time(w, i, beatSeconds, used);
                    i += used[0];
                }
                case "flou", "ouverture" -> {
                    if (i >= w.size()) throw new IllegalArgumentException("flou : un pourcentage (0 à " + MAX_BLUR + ", 100 = normal).");
                    String v = w.get(i++).replace("%", "");
                    try {
                        blur = (int) Math.round(Double.parseDouble(v.replace(',', '.')));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("flou : « " + v + " » n'est pas un pourcentage.");
                    }
                }
                case "courbe" -> {
                    if (i >= w.size()) throw new IllegalArgumentException("courbe : " + String.join(", ", EASINGS) + ".");
                    String c = w.get(i++).replace("é", "e");
                    if (!EASINGS.contains(c)) throw new IllegalArgumentException("courbe « " + c + " » inconnue : " + String.join(", ", EASINGS) + ".");
                    easing = c;
                }
                case "garder", "garde" -> {
                    if (i >= w.size()) throw new IllegalArgumentException("garder oui ou non.");
                    String v = w.get(i++);
                    keep = !(v.equals("non") || v.equals("off") || v.equals("false"));
                }
                default -> throw new IllegalArgumentException("« " + key + " » : de, vers, duree, delai, flou, courbe ou garder.");
            }
        }
        if (blur > MAX_BLUR) throw new IllegalArgumentException("flou : " + MAX_BLUR + " % au plus.");
        return new FocusPull(from, to, delay, duration, easing, blur, keep);
    }

    private static String target(List<String> w, int i, Picker picker, int[] used) {
        if (i >= w.size()) throw new IllegalArgumentException("cible manquante : centre, fond, une distance, ici, entite, x y z ou un pseudo.");
        String t = w.get(i);
        used[0] = 1;
        switch (t) {
            case "centre", "center", "sujet", "le" -> {
                if (t.equals("le") && i + 1 < w.size()) {
                    used[0] = 2;
                    return target(w, i + 1, picker, new int[1]);
                }
                return CENTRE;
            }
            case "fond", "salle", "loin", "arriere", "arrière" -> {
                return BACKGROUND;
            }
            case "ici", "vise", "visé", "point", "bloc" -> {
                String p = picker == null ? null : picker.point();
                if (p == null) throw new IllegalArgumentException("« ici » : regarde un bloc (à 64 blocs au plus).");
                return p;
            }
            case "entite", "entité", "mob" -> {
                String e = picker == null ? null : picker.entity();
                if (e == null) throw new IllegalArgumentException("« entite » : regarde une entité (à 64 blocs au plus).");
                return e;
            }
            default -> { }
        }
        // x y z
        if (i + 2 < w.size() && isNumber(t) && isNumber(w.get(i + 1)) && isNumber(w.get(i + 2))) {
            used[0] = 3;
            return "p:" + num(Double.parseDouble(t.replace(',', '.'))) + "," + num(Double.parseDouble(w.get(i + 1).replace(',', '.')))
                    + "," + num(Double.parseDouble(w.get(i + 2).replace(',', '.')));
        }
        // a distance: 12, 12b, 12 blocs
        String d = t.replaceFirst("(b|blocs?|m)$", "");
        if (isNumber(d)) {
            if (i + 1 < w.size() && w.get(i + 1).matches("blocs?|b|m")) used[0] = 2;
            double v = Double.parseDouble(d.replace(',', '.'));
            if (v < 0.2 || v > 512) throw new IllegalArgumentException("distance : de 0,2 à 512 blocs.");
            return "d:" + num(v);
        }
        if (t.startsWith("p:") || t.startsWith("e:") || t.startsWith("d:") || t.startsWith("j:")) return t;
        String j = picker == null ? null : picker.player(t);
        if (j != null) return j;
        throw new IllegalArgumentException("cible « " + t + " » inconnue : centre, fond, une distance (12), ici, entite, x y z ou un pseudo connecté.");
    }

    private static double time(List<String> w, int i, double beatSeconds, int[] used) {
        if (i >= w.size()) throw new IllegalArgumentException("une durée : 1.5 (secondes) ou 2t (temps de la musique).");
        String t = w.get(i);
        used[0] = 1;
        boolean beats = t.endsWith("t") || (i + 1 < w.size() && w.get(i + 1).startsWith("temps"));
        if (!t.endsWith("t") && i + 1 < w.size() && w.get(i + 1).matches("temps|s|sec|secondes?")) used[0] = 2;
        String v = t.replaceFirst("(t|s|sec|secondes?|temps)$", "");
        if (!isNumber(v)) throw new IllegalArgumentException("« " + t + " » : une durée en secondes (1.5) ou en temps (2t).");
        double x = Double.parseDouble(v.replace(',', '.'));
        if (beats) {
            if (beatSeconds <= 0) throw new IllegalArgumentException("pas de rythme sur cette intro : la durée en secondes (1.5).");
            x *= beatSeconds;
        }
        if (x > MAX_SECONDS) throw new IllegalArgumentException("au plus " + (int) MAX_SECONDS + " s.");
        return x;
    }

    private static boolean isNumber(String s) {
        return s.matches("-?\\d+([.,]\\d+)?");
    }

    // ---------------------------------------------------------------- playing

    /** 0..1: how far the focus went from {@code from} to {@code to}, {@code seconds} after the shot started. */
    public double progress(double seconds) {
        double x = Math.max(0, Math.min(1, (seconds - delay) / duration));
        return switch (easing) {
            case "lineaire" -> x;
            case "entree" -> x * x;
            case "sortie" -> 1 - (1 - x) * (1 - x);
            default -> x * x * (3 - 2 * x);
        };
    }

    /** 1 while the blur is on; when not kept, it fades out over 0.6 s once the focus has arrived. */
    public double strength(double seconds) {
        if (keep) return 1;
        double after = seconds - delay - duration;
        return after <= 0 ? 1 : Math.max(0, 1 - after / 0.6);
    }

    // ---------------------------------------------------------------- in a shot's effect

    /** "macro+look_intro|de=… vers=…": the effects, and the focus pull after a {@code |}. */
    public static String attach(String effects, FocusPull f) {
        return f == null ? effects : effects + "|" + f.canonical();
    }

    /** The effects part of a shot's effect (before {@code |}). */
    public static String effectsOf(String spec) {
        if (spec == null) return "";
        int bar = spec.indexOf('|');
        return bar < 0 ? spec : spec.substring(0, bar);
    }

    /** The focus pull carried in a shot's effect, or null. */
    public static FocusPull of(String spec) {
        if (spec == null) return null;
        int bar = spec.indexOf('|');
        return bar < 0 ? null : parseCanonical(spec.substring(bar + 1));
    }
}
