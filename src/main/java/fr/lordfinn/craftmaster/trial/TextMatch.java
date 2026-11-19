package fr.lordfinn.craftmaster.trial;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How the trials compare what a contestant types (passwords of the flowers, codes, answers) with what is expected:
 * forgiving on everything but the letters. Case, accents (é = e, ç = c, œ = oe), apostrophes, hyphens, punctuation
 * and spaces don't count: « Pomme-de-terre », « pomme de terre » and « pommedeterre » are the same answer.
 * Accepted variants of a word (« cot » = « cote » = « cotte ») are given per answer, normalised the same way.
 */
public final class TextMatch {

    private TextMatch() {}

    /** Lower case, no accents, words of letters and digits separated by single spaces ("" for null). */
    public static String words(String s) {
        if (s == null) return "";
        String n = s.replace("œ", "oe").replace("Œ", "oe").replace("æ", "ae").replace("Æ", "ae").replace("ß", "ss");
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** The letters and digits only: what is compared. */
    public static String key(String s) {
        return words(s).replace(" ", "");
    }

    /** The first letter that counts ("" if none): « l'Éléphant » → "l", « 'Arbre » → "a". */
    public static String initial(String s) {
        String k = key(s);
        return k.isEmpty() ? "" : k.substring(0, 1);
    }

    /**
     * Does {@code typed} match one of the {@code answers}? Each word typed that is a variant of a word (from
     * {@code variants}: word → its other spellings) counts as that word.
     */
    public static boolean matches(String typed, Collection<String> answers, Map<String, ? extends Collection<String>> variants) {
        String plain = key(typed);
        if (plain.isEmpty()) return false;
        String canonical = key(canonical(typed, variants));
        for (String a : answers) {
            String k = key(a);
            if (!k.isEmpty() && (k.equals(plain) || k.equals(canonical))) return true;
        }
        return false;
    }

    public static boolean matches(String typed, Collection<String> answers) {
        return matches(typed, answers, Map.of());
    }

    /**
     * The answer read as a sequence of syllables from {@code alphabet} (syllable → its other spellings, « cot » :
     * « cote », « cotte »), separators free (none, spaces, hyphens...), longest spelling first at each step. Null if
     * something is left that is no syllable: « Co-Cot-Ri » and « cocotri » → [co, cot, ri]; « cocorico » → [co, co,
     * ri, co]; « cotx » → null.
     */
    public static List<String> syllables(String s, Map<String, ? extends Collection<String>> alphabet) {
        List<String[]> spellings = new ArrayList<>();   // {spelling key, syllable}
        for (Map.Entry<String, ? extends Collection<String>> e : alphabet.entrySet()) {
            String syl = key(e.getKey());
            if (syl.isEmpty()) continue;
            spellings.add(new String[]{syl, syl});
            for (String v : e.getValue()) if (!key(v).isEmpty()) spellings.add(new String[]{key(v), syl});
        }
        spellings.sort((a, b) -> b[0].length() - a[0].length());
        String k = key(s);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < k.length()) {
            String[] hit = null;
            for (String[] sp : spellings) if (k.startsWith(sp[0], i)) { hit = sp; break; }
            if (hit == null) return null;
            out.add(hit[1]);
            i += hit[0].length();
        }
        return out.isEmpty() ? null : out;
    }

    /** Same syllables, same order, same count as one of the answers (see {@link #syllables}). */
    public static boolean matchesSyllables(String typed, Collection<String> answers, Map<String, ? extends Collection<String>> alphabet) {
        List<String> t = syllables(typed, alphabet);
        if (t == null) return false;
        for (String a : answers) if (t.equals(syllables(a, alphabet))) return true;
        return false;
    }

    /** The typed words, each variant replaced by its word. */
    static String canonical(String typed, Map<String, ? extends Collection<String>> variants) {
        if (variants == null || variants.isEmpty()) return typed;
        List<String> out = new ArrayList<>();
        for (String w : words(typed).split(" ")) {
            String found = w;
            for (Map.Entry<String, ? extends Collection<String>> e : variants.entrySet())
                for (String v : e.getValue())
                    if (key(v).equals(w)) found = key(e.getKey());
            out.add(found);
        }
        return String.join(" ", out);
    }
}
