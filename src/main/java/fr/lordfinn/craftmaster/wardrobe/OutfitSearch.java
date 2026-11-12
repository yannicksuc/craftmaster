package fr.lordfinn.craftmaster.wardrobe;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search bar of the outfit screen: every word typed must be found in the outfit (its name, the names and ids
 * of its pieces), without caring about case or accents ("medecin" finds "Médecin"). Words typed in French also
 * look for their English translation, so "chapeau" finds Selfexpression's "Mafia hat" and "médecin" its "Doctor's
 * robe" (its names are English).
 */
public final class OutfitSearch {

    private OutfitSearch() {}

    /** French word (normalised, see {@link #normalize}) → English words found in clothes names and ids. */
    private static final Map<String, String[]> FR_EN = new HashMap<>();

    private static void fr(String fr, String... en) {
        FR_EN.put(normalize(fr), en);
    }

    static {
        // Jobs and characters
        fr("médecin", "doctor", "medic", "medical"); fr("docteur", "doctor", "medic", "medical"); fr("infirmier", "doctor", "medic", "nurse");
        fr("infirmière", "doctor", "medic", "nurse");
        fr("policier", "police"); fr("police", "police"); fr("gendarme", "police"); fr("flic", "police");
        fr("pompier", "fire", "firefighter"); fr("chevalier", "knight", "crusader"); fr("roi", "king", "royal");
        fr("reine", "queen"); fr("prince", "prince"); fr("princesse", "princess"); fr("empereur", "emperor");
        fr("bouffon", "jester"); fr("clown", "jester", "clown"); fr("mafieux", "mafia", "gangster"); fr("mafia", "mafia");
        fr("capitaine", "captain"); fr("marin", "sailor", "captain"); fr("mineur", "miner"); fr("ouvrier", "builder", "worker");
        fr("jardinier", "gardener"); fr("forgeron", "blacksmith", "smith"); fr("ingénieur", "engineer"); fr("avocat", "lawer", "lawyer");
        fr("soldat", "soldier", "military"); fr("militaire", "soldier", "military"); fr("prisonnier", "prisoner");
        fr("bagnard", "prisoner"); fr("voleur", "thief", "robber"); fr("pirate", "pirate"); fr("cowboy", "cowboy");
        fr("pilote", "pilot"); fr("astronaute", "space", "astronaut", "cosmonaut"); fr("espace", "space");
        fr("cuisinier", "chef", "cook"); fr("chef", "chef", "cook"); fr("serveur", "waiter"); fr("boulanger", "baker");
        fr("vampire", "vampir", "vampire"); fr("sorcier", "wizard", "witch", "mage"); fr("sorcière", "witch");
        fr("chasseur", "hunter"); fr("samouraï", "samurai"); fr("ninja", "ninja"); fr("viking", "viking");
        fr("lutin", "elf"); fr("elfe", "elf"); fr("noël", "christmas", "christmass", "new_year", "santa");
        fr("père noël", "santa"); fr("bonhomme de neige", "snowman"); fr("vagabond", "tramp", "wanderer");
        fr("voyageur", "traveler", "wanderer"); fr("survivant", "survivor"); fr("zombie", "zombie"); fr("squelette", "skeleton");
        fr("démon", "demon"); fr("ange", "angel"); fr("plongeur", "diver"); fr("détective", "detective");
        fr("scientifique", "scientist", "chemical"); fr("chimique", "chemical"); fr("robot", "robot", "cyber");
        fr("futur", "future", "cyber"); fr("médiéval", "medieval"); fr("renard", "fox"); fr("citrouille", "pumpkin");
        fr("automne", "autumn"); fr("hiver", "winter", "snow"); fr("neige", "snow"); fr("été", "summer");
        // Clothes
        fr("chapeau", "hat", "cap"); fr("casquette", "cap"); fr("bonnet", "hat", "cap"); fr("casque", "helmet");
        fr("couronne", "crown"); fr("capuche", "hood"); fr("masque", "mask"); fr("lunettes", "glasses", "goggles");
        fr("bandeau", "bandage", "headband"); fr("veste", "jacket", "coat", "vest"); fr("manteau", "coat", "mantle", "cloak");
        fr("cape", "cape", "cloak", "mantle"); fr("chemise", "shirt"); fr("tshirt", "t-shirt", "shirt"); fr("t-shirt", "t-shirt", "shirt");
        fr("pull", "sweater", "hoodie", "jumper"); fr("sweat", "hoodie", "sweatshirt"); fr("gilet", "vest");
        fr("robe", "robe", "dress"); fr("tablier", "apron"); fr("uniforme", "uniform"); fr("combinaison", "jumpsuit", "suit");
        fr("costume", "suit", "costume", "strict"); fr("tenue", "suit", "clothes"); fr("armure", "armor", "chestplate");
        fr("pantalon", "trousers", "pants", "leggins", "leggings", "jeans"); fr("jean", "jeans"); fr("short", "shorts");
        fr("jupe", "skirt"); fr("bottes", "boots"); fr("chaussures", "shoes", "sneakers", "boots"); fr("baskets", "sneakers");
        fr("chaussettes", "socks"); fr("sac", "bag", "backpack");
        // Colours
        fr("rouge", "red"); fr("bleu", "blue"); fr("vert", "green"); fr("jaune", "yellow"); fr("noir", "black");
        fr("blanc", "white"); fr("gris", "gray", "grey"); fr("orange", "orange"); fr("violet", "purple"); fr("rose", "pink");
        fr("marron", "brown"); fr("cyan", "cyan"); fr("doré", "gold", "golden"); fr("or", "gold", "golden");
        fr("argent", "silver"); fr("fer", "iron"); fr("cuir", "leather"); fr("diamant", "diamond"); fr("maille", "chainmail");
    }

    /** Lower case, no accents, separators as spaces. */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[_:'’.,;/()\\[\\]-]+", " ").replaceAll("\\s+", " ").strip();
    }

    /** The words of a query ("bonhomme de neige" kept together when it is a known expression). */
    static List<String> words(String query) {
        String q = normalize(query);
        List<String> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (String expr : FR_EN.keySet()) {
            if (expr.contains(" ") && (" " + q + " ").contains(" " + expr + " ")) {
                out.add(expr);
                q = (" " + q + " ").replace(" " + expr + " ", " ").strip();
            }
        }
        for (String w : q.split(" ")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Does {@code text} (all the words describing an outfit) match {@code query}? An empty query matches. */
    public static boolean matches(String query, String text) {
        String hay = " " + normalize(text) + " ";
        for (String w : words(query)) if (!matchesWord(w, hay)) return false;
        return true;
    }

    private static boolean matchesWord(String w, String hay) {
        if (hay.contains(w)) return true;
        String[] en = FR_EN.get(w);
        if (en == null && w.endsWith("s")) en = FR_EN.get(w.substring(0, w.length() - 1));
        if (en == null && !w.endsWith("s")) en = FR_EN.get(w + "s");
        // Translations count as whole words only (or with an s): "elf" is not in "selfexpression", "cap" not in "captain"
        if (en != null) {
            for (String e : en) {
                String n = normalize(e);
                if (hay.contains(" " + n + " ") || hay.contains(" " + n + "s ")) return true;
            }
        }
        return false;
    }
}
