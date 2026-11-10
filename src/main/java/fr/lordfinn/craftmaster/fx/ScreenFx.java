package fr.lordfinn.craftmaster.fx;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The screen effects (post-processing of the world view, docs/wiki/Effets.md): their ids, French names, the order
 * in which a combination is drawn, and the parsing of what an organiser types or says ("noir et blanc",
 * "fisheye+masque_rond", "judas", "aucun"). Pure logic, shared by the server (commands, actions) and the client.
 *
 * <p>A combination is written with "+" (or ","): {@code fisheye+masque_rond+sepia}. Whatever order it is written
 * in, the effects are drawn in {@link #ORDER}: distortions first, then blur, colour, film look, and the masks last
 * (a peephole mask stays round on top of a fisheye).
 */
public final class ScreenFx {

    private ScreenFx() {}

    /** One effect: id (also the post effect file craftmaster:fx/&lt;id&gt;), French and English names. */
    public record Kind(String id, String fr, String en) {}

    /** Every effect, in drawing order. */
    public static final List<Kind> ORDER = List.of(
            new Kind("ondule", "ondulation", "wobble"),
            new Kind("fisheye", "fish-eye", "fisheye"),
            new Kind("pixel", "pixelisé", "pixelated"),
            new Kind("flou", "flou", "blur"),
            new Kind("reve", "rêve", "dream glow"),
            new Kind("noir_blanc", "noir et blanc", "black & white"),
            new Kind("sepia", "sépia", "sepia"),
            new Kind("infrarouge", "infrarouge", "thermal"),
            new Kind("negatif", "négatif", "negative"),
            new Kind("bd", "bande dessinée", "comic"),
            new Kind("vhs", "cassette VHS", "VHS tape"),
            new Kind("vieux_film", "vieux film", "old film"),
            new Kind("vignette", "vignette", "vignette"),
            new Kind("citrouille", "masque citrouille", "pumpkin mask"),
            new Kind("jumelles", "jumelles", "binoculars"),
            new Kind("masque_rond", "masque rond", "round mask"));

    /** At most this many effects at once (each one is a few full-screen passes). */
    public static final int MAX_COMBINED = 6;

    /** Words meaning "no effect". */
    public static final Set<String> NONE_WORDS = Set.of("aucun", "aucune", "rien", "none", "off", "stop", "normal", "enleve",
            "retire", "sans", "clear");

    /** Ready-made combinations, usable like an effect. */
    public static final Map<String, String> PRESETS = new LinkedHashMap<>();
    /** Other names (normalized: lower case, no accent, "_" for spaces) → id. */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        PRESETS.put("judas", "fisheye+masque_rond");
        PRESETS.put("camera_cachee", "fisheye+vhs+vignette");
        PRESETS.put("souvenir", "sepia+reve+vignette");
        PRESETS.put("film_muet", "vieux_film+vignette");
        PRESETS.put("cauchemar", "ondule+negatif+vignette");

        alias("noir_blanc", "noir_et_blanc", "nb", "n&b", "n_b", "noir", "gris", "bw", "black_white", "black_and_white",
                "grayscale", "greyscale", "monochrome");
        alias("sepia", "sepias", "vieille_photo");
        alias("pixel", "pixels", "pixelise", "pixelisee", "pixelisation", "pixelated", "mosaique", "retro");
        alias("fisheye", "fish_eye", "oeil_de_poisson", "œil_de_poisson", "grand_angle", "fish", "barrel");
        alias("vignette", "vignettage", "bords_sombres");
        alias("masque_rond", "rond", "cercle", "hublot", "iris", "round", "circle");
        alias("citrouille", "masque_citrouille", "pumpkin", "halloween");
        alias("flou", "floue", "flous", "blur", "blurry", "brouille");
        alias("vhs", "cassette", "k7", "scanlines", "magnetoscope", "vieille_tele");
        alias("vieux_film", "film", "vieux_films", "old_film", "cinema_muet", "pellicule", "vintage");
        alias("infrarouge", "infra_rouge", "thermique", "thermal", "ir", "chaleur", "camera_thermique");
        alias("reve", "dream", "glow", "halo", "onirique", "flash_back", "flashback");
        alias("ondule", "ondulation", "onde", "vagues", "vertige", "ivre", "wobble", "drunk", "tourbillon");
        alias("jumelles", "jumelle", "binoculars", "longue_vue");
        alias("bd", "bande_dessinee", "comic", "comics", "cartoon", "dessin", "dessin_anime", "cel");
        alias("negatif", "negative", "invert", "inverse", "inversion");
    }

    private static void alias(String id, String... names) {
        ALIASES.put(id, id);
        for (String n : names) ALIASES.put(n, id);
    }

    public static Kind kind(String id) {
        for (Kind k : ORDER) if (k.id().equals(id)) return k;
        return null;
    }

    public static boolean known(String id) {
        return kind(id) != null;
    }

    /** All ids, in drawing order. */
    public static List<String> ids() {
        return ORDER.stream().map(Kind::id).toList();
    }

    /** Ids and preset names, for suggestions. */
    public static List<String> suggestions() {
        List<String> out = new ArrayList<>();
        out.add("aucun");
        out.addAll(ids());
        out.addAll(PRESETS.keySet());
        return out;
    }

    /** Lower case, no accents, separators turned into "_". */
    static String normalize(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace('’', '\'').replaceAll("^(l'|le |la |les |en |un |une |effet |effets |mode |filtre )+", "");
        return n.replaceAll("[\\s\\-']+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    /**
     * The effects of a combination, in drawing order, without duplicates. Empty for "aucun" (or null / blank).
     *
     * @throws IllegalArgumentException with a French message naming the unknown effect, or if there are too many
     */
    public static List<String> parse(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        Set<String> wanted = new LinkedHashSet<>();
        for (String part : spec.split("[+,;]")) {
            String n = normalize(part);
            if (n.isEmpty() || NONE_WORDS.contains(n)) continue;
            String preset = PRESETS.get(n);
            if (preset != null) {
                wanted.addAll(List.of(preset.split("\\+")));
                continue;
            }
            String id = ALIASES.get(n);
            if (id == null) throw new IllegalArgumentException("effet inconnu « " + part.trim() + " » (effets : " + String.join(", ", ids()) + ").");
            wanted.add(id);
        }
        List<String> out = new ArrayList<>();
        for (Kind k : ORDER) if (wanted.contains(k.id())) out.add(k.id());
        if (out.size() > MAX_COMBINED) throw new IllegalArgumentException("trop d'effets à la fois (" + MAX_COMBINED + " au plus).");
        return out;
    }

    /** Like {@link #parse} but never throws: unknown names are left out (for what the client receives). */
    public static List<String> parseLenient(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        Set<String> wanted = new LinkedHashSet<>();
        for (String part : spec.split("[+,;]")) {
            try {
                wanted.addAll(parse(part));
            } catch (IllegalArgumentException ignored) {
                // unknown name: ignored
            }
        }
        List<String> out = new ArrayList<>();
        for (Kind k : ORDER) if (wanted.contains(k.id()) && out.size() < MAX_COMBINED) out.add(k.id());
        return out;
    }

    /** Canonical spelling: ids joined by "+", "" for none. */
    public static String join(List<String> ids) {
        return String.join("+", ids);
    }

    /** French description: "fish-eye + masque rond", or "aucun effet". */
    public static String describe(List<String> ids) {
        if (ids.isEmpty()) return "aucun effet";
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Kind k = kind(id);
            names.add(k == null ? id : k.fr());
        }
        return String.join(" + ", names);
    }
}
