package fr.lordfinn.craftmaster.wardrobe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tags of the outfits, to find them by theme: job, theme, setting, colour, kind of clothes, material, use, hobby
 * (and the body part, from the pieces). They are found in the names of an outfit (its own and its pieces', English or
 * French: "Doctor's robe" is a Médecin, "Red empire clothes" is Rouge), not in the item ids (every Selfexpression
 * id ends in _chestplate, _helmet...), without Minecraft.
 * Organisers add their own tags to an outfit ({@link Outfit#tags()}), in the category {@link #CUSTOM}.
 */
public final class OutfitTags {

    private OutfitTags() {}

    /** A tag: its category ({@link #CATEGORIES} label) and its name, both in French. */
    public record Tag(String category, String name) {}

    public static final String BODY = "Partie du corps", CUSTOM = "Tags de l'organisateur";

    /** Category → tag → the words (whole words, normalised by {@link OutfitSearch#normalize}) that give it. */
    public static final Map<String, Map<String, String[]>> CATEGORIES = new LinkedHashMap<>();

    private static Map<String, String[]> cat;

    private static void category(String name) {
        cat = new LinkedHashMap<>();
        CATEGORIES.put(name, cat);
    }

    private static void tag(String name, String... words) {
        String[] n = new String[words.length];
        for (int i = 0; i < words.length; i++) n[i] = OutfitSearch.normalize(words[i]);
        cat.put(name, n);
    }

    static {
        category("Métier");
        tag("Médecin", "doctor", "medic", "medical", "nurse", "surgeon", "médecin", "docteur", "infirmier");
        tag("Policier", "police", "policeman", "sheriff", "swat", "detective", "policier");
        tag("Pompier", "firefighter", "fireman", "pompier");
        tag("Militaire", "soldier", "military", "army", "sniper", "commando", "trooper", "stormtrooper", "soldat");
        tag("Chevalier", "knight", "crusader", "paladin", "chevalier");
        tag("Ouvrier", "builder", "worker", "engineer", "mechanic", "ouvrier", "ingénieur");
        tag("Mineur", "miner", "mineur");
        tag("Forgeron", "blacksmith", "smith", "forgeron");
        tag("Jardinier", "gardener", "farmer", "jardinier");
        tag("Cuisinier", "chef", "cook", "baker", "cuisinier");
        tag("Marin", "captain", "sailor", "pirate", "capitaine", "marin");
        tag("Pilote", "pilot", "pilote");
        tag("Scientifique", "scientist", "chemical", "hev", "lab", "scientifique");
        tag("Avocat", "lawer", "lawyer", "judge", "avocat");
        tag("Prisonnier", "prisoner", "prisonnier");
        tag("Chasseur", "hunter", "chasseur");
        tag("Voleur", "thief", "thiefs", "robber", "outlaw", "bandit", "voleur");
        tag("Mafieux", "mafia", "gangster", "mafieux");
        tag("Royauté", "king", "kings", "queen", "prince", "princess", "emperor", "royal", "crown", "roi", "reine");
        tag("Bouffon", "jester", "clown", "bouffon");
        tag("Cowboy", "cowboy", "sheriff", "gunfighter", "west", "western");

        category("Thème");
        tag("Médiéval", "medieval", "knight", "crusader", "king", "kings", "jester", "blacksmith", "médiéval");
        tag("Futuriste", "future", "cyber", "robot", "computer", "electro", "sigma", "futuriste");
        tag("Espace", "space", "astronaut", "cosmonaut", "espace");
        tag("Noël", "christmas", "christmass", "santa", "elf", "snowman", "noël");
        tag("Halloween", "pumpkin", "skeleton", "skeletons", "zombie", "vampir", "vampirs", "vampire", "ghost", "death");
        tag("Far west", "west", "cowboy", "sheriff", "gunfighter", "outlaw");
        tag("Apocalypse", "apocalypse", "apocalypsise", "survivor", "survivors", "stalker", "zombie");
        tag("Fantastique", "demon", "angel", "elf", "wizard", "witch", "mage", "dragon", "fairy", "magic");
        tag("Science-fiction", "mandalorian", "mandalorians", "stormtrooper", "stormtroopers", "combine", "hev", "cyber");
        tag("Hiver", "winter", "snow", "insulated", "frost", "hiver");
        tag("Automne", "autumn", "leaf", "maple", "mapel", "automne");
        tag("Animaux", "fox", "wolf", "wolfs", "cat", "bear", "frog", "frogs", "sniffer", "leopard", "bunny", "rabbit", "bee", "sheep");
        tag("Empire", "empire", "emperor");

        category("Contexte");
        tag("Ville", "police", "lawer", "lawyer", "strict", "mafia", "casual", "urban");
        tag("Campagne", "farmer", "gardener", "west", "village");
        tag("Mer", "captain", "sailor", "pirate", "diver", "sea");
        tag("Montagne", "mountain", "ranger", "taiga");
        tag("Forêt", "forest", "ranger", "hunter", "taiga", "wanderer", "leaf");
        tag("Château", "knight", "king", "kings", "jester", "medieval", "royal");
        tag("Chantier", "builder", "worker", "engineer", "miner", "blacksmith");
        tag("Hôpital", "doctor", "medic", "medical", "nurse");
        tag("Prison", "prisoner");
        tag("Champ de bataille", "soldier", "military", "army", "sniper", "battle", "crusader", "swat");

        category("Couleur");
        tag("Rouge", "red", "rouge");
        tag("Bleu", "blue", "bleu");
        tag("Vert", "green", "lime", "vert");
        tag("Jaune", "yellow", "jaune");
        tag("Orange", "orange");
        tag("Violet", "purple", "violet");
        tag("Rose", "pink", "rose", "love");
        tag("Noir", "black", "dark", "noir");
        tag("Blanc", "white", "blanc");
        tag("Gris", "gray", "grey", "gris");
        tag("Marron", "brown", "marron");
        tag("Cyan", "cyan", "aqua");
        tag("Doré", "gold", "golden", "doré");

        category("Type");
        tag("Veste", "jacket", "vest", "blazer", "veste");
        tag("Manteau", "coat", "mantle", "cloak", "cape", "manteau");
        tag("Sweat", "hoodie", "hoody", "sweatshirt", "sweater", "jumper", "pull");
        tag("T-shirt", "t shirt", "tshirt", "shirt", "chemise");
        tag("Robe", "robe", "dress", "gown", "rob");
        tag("Uniforme", "uniform", "suit", "uniforme");
        tag("Combinaison", "jumpsuit", "overalls", "siut", "combinaison");
        tag("Armure", "armor", "armour", "chestplate", "armored", "armure");
        tag("Chapeau", "hat", "cap", "crown", "beret", "chapeau");
        tag("Casque", "helmet", "casque");
        tag("Capuche", "hood", "capuche");
        tag("Masque", "mask", "bandage", "masque");
        tag("Lunettes", "glasses", "goggles", "lunettes");
        tag("Pantalon", "trousers", "pants", "jeans", "leggins", "leggings", "shorts", "greaves", "pantalon");
        tag("Chaussures", "boots", "shoes", "sneakers", "socks", "valenki", "bottes", "chaussures");
        tag("Sac", "bag", "backpack", "sac");
        tag("Tablier", "apron", "tablier");

        category("Matière");
        tag("Cuir", "leather", "cuir");
        tag("Fer", "iron", "fer");
        tag("Or", "gold", "golden");
        tag("Diamant", "diamond", "diamant");
        tag("Netherite", "netherite");
        tag("Mailles", "chainmail", "mailles");
        tag("Cuivre", "copper", "cuivre");
        tag("Tissu", "cloth", "clothes", "wool", "cotton", "tissu");
        tag("Métal", "metal", "steel", "armored", "iron", "copper");

        category("Utilité");
        tag("Protection", "armor", "armour", "armored", "chestplate", "helmet", "swat", "protection");
        tag("Protection chimique", "chemical", "hev", "gas");
        tag("Chaud", "insulated", "winter", "warm", "sweater", "cozy", "fur");
        tag("Travail", "work", "apron", "worker", "builder", "jumpsuit", "engineer");
        tag("Chic", "strict", "tuxedo", "romantic", "royal", "king", "emperor", "mafia", "lawer", "lawyer");
        tag("Déguisement", "jester", "clown", "pumpkin", "snowman", "fox", "costume", "frog", "frogs");
        tag("Discrétion", "thief", "thiefs", "ninja", "hood", "assassin", "stalker", "camouflage");

        category("Loisir");
        tag("Sport", "sporty", "sport", "parkour", "team", "football", "runner");
        tag("Jeu vidéo", "battle royale", "mcc", "error", "computer", "pixel");
        tag("Fête", "party", "new year", "christmas", "christmass", "birthday", "year");
        tag("Musique", "music", "rock", "dj", "headphones");
        tag("Plage", "beach", "summer", "swim", "sunshine");
        tag("Jardinage", "gardener", "farmer", "garden");
        tag("Aventure", "traveler", "wanderer", "explorer", "adventurer", "hunter", "survivor");
    }

    /** The tags found in {@code text} (an outfit's words), in the order of {@link #CATEGORIES}. */
    public static List<Tag> auto(String text) {
        String hay = " " + OutfitSearch.normalize(text) + " ";
        List<Tag> out = new ArrayList<>();
        for (var c : CATEGORIES.entrySet()) {
            for (var t : c.getValue().entrySet()) {
                for (String w : t.getValue()) {
                    if (hay.contains(" " + w + " ")) {
                        out.add(new Tag(c.getKey(), t.getKey()));
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Body part tags from which pieces there are (head, body, legs, feet), "Tenue complète" with all four. */
    public static List<Tag> body(boolean[] present) {
        String[] names = {"Tête", "Corps", "Jambes", "Pieds"};
        List<Tag> out = new ArrayList<>();
        boolean all = true;
        for (int i = 0; i < 4; i++) {
            if (present[i]) out.add(new Tag(BODY, names[i]));
            else all = false;
        }
        if (all) out.add(new Tag(BODY, "Tenue complète"));
        return out;
    }

    /** Every tag of an outfit: found in its words, its body parts, its organiser's tags. */
    public static List<Tag> of(String text, boolean[] present, List<String> custom) {
        Set<Tag> out = new LinkedHashSet<>(auto(text));
        out.addAll(body(present));
        for (String c : custom) if (!c.isBlank()) out.add(new Tag(CUSTOM, c.strip()));
        return List.copyOf(out);
    }

    /** "médiéval, Rouge ,  " → ["médiéval", "Rouge"] (organisers type tags separated by commas). */
    public static List<String> parse(String typed) {
        List<String> out = new ArrayList<>();
        for (String s : typed.split("[,;#]")) {
            String t = s.strip();
            if (!t.isEmpty() && t.length() <= 32 && out.stream().noneMatch(t::equalsIgnoreCase)) out.add(t);
            if (out.size() >= 16) break;
        }
        return out;
    }
}
