package fr.lordfinn.craftmaster.camera;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The cameras (of every lieu), and how a spoken or typed reference finds one: "2", "caméra 2", "Plan large",
 * "large", "trône", "studio:2", "caméra 2 du studio", "caméra 1 d'Ensuri"… Pure logic (no Minecraft), used by the
 * server's registry and by the broadcast clients.
 *
 * <p>A list can hold several groups (see {@link CameraSpot#group()}); {@link #group(String)} gives the view of one
 * group, where the numbers are unique. The simple methods ({@link #get(int)}, {@link #resolve(String)},
 * {@link #wide()}, {@link #step}) are meant for such a view; {@link #resolve(String, String, Map)} understands the
 * whole grammar relative to the group the operator is working in (the "focus").
 */
public final class CameraList {

    /** Studio first, then the rounds and their zones by name, then the numbers. */
    public static final Comparator<CameraSpot> ORDER = Comparator
            .comparing((CameraSpot s) -> !s.inStudio())
            .thenComparing(CameraSpot::lieu)
            .thenComparing(CameraSpot::zone)
            .thenComparingInt(CameraSpot::number);

    private final Map<String, CameraSpot> byKey = new HashMap<>();

    public CameraList() {}

    public CameraList(List<CameraSpot> spots) {
        for (CameraSpot s : spots) byKey.put(s.key(), s);
    }

    /** Every camera, in {@link #ORDER}. */
    public List<CameraSpot> all() {
        List<CameraSpot> out = new ArrayList<>(byKey.values());
        out.sort(ORDER);
        return out;
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public int size() {
        return byKey.size();
    }

    /** By unique key ("studio:2", "pont:ensuri:1"). */
    public CameraSpot get(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** By number: meant for the view of one group (in a mixed list, the first one in {@link #ORDER}). */
    public CameraSpot get(int number) {
        for (CameraSpot s : all()) if (s.number() == number) return s;
        return null;
    }

    public void put(CameraSpot spot) {
        byKey.put(spot.key(), spot);
    }

    public CameraSpot remove(String key) {
        return byKey.remove(key);
    }

    /** Smallest number not used yet in this list (1 when empty): use it on a group view. */
    public int nextFree() {
        int n = 1;
        while (get(n) != null) n++;
        return n;
    }

    /** Smallest free number in a group. */
    public int nextFree(String group) {
        return group(group).nextFree();
    }

    // ---------------------------------------------------------------- groups

    /** The cameras of one group ("studio", "pont", "pont:ensuri"). */
    public CameraList group(String group) {
        CameraList out = new CameraList();
        for (CameraSpot s : byKey.values()) if (s.group().equals(group)) out.put(s);
        return out;
    }

    /** Every camera of a lieu, whatever its zone. */
    public CameraList lieu(String lieu) {
        String l = CameraSpot.normalizeLieu(lieu);
        CameraList out = new CameraList();
        for (CameraSpot s : byKey.values()) if (s.lieu().equals(l)) out.put(s);
        return out;
    }

    /** The groups that have cameras, in {@link #ORDER}. */
    public List<String> groups() {
        Set<String> out = new LinkedHashSet<>();
        for (CameraSpot s : all()) out.add(s.group());
        return new ArrayList<>(out);
    }

    /** The zones of a lieu that have cameras, sorted ("" first when the lieu has cameras of its own). */
    public List<String> zones(String lieu) {
        Set<String> out = new LinkedHashSet<>();
        for (CameraSpot s : lieu(lieu).all()) out.add(s.zone());
        return new ArrayList<>(out);
    }

    public boolean hasGroup(String group) {
        for (CameraSpot s : byKey.values()) if (s.group().equals(group)) return true;
        return false;
    }

    // ---------------------------------------------------------------- one group

    /** The first camera with that role, lowest number first; null if none. */
    public CameraSpot withRole(String role) {
        String r = CameraSpot.normalizeRole(role);
        if (r.isEmpty()) return null;
        for (CameraSpot s : all()) if (s.role().equals(r)) return s;
        return null;
    }

    /** The wide shot: the camera marked "large", else the first one; null in an empty list. */
    public CameraSpot wide() {
        CameraSpot w = withRole(CameraSpot.WIDE);
        if (w != null) return w;
        List<CameraSpot> all = all();
        return all.isEmpty() ? null : all.getFirst();
    }

    /**
     * The camera {@code delta} steps after {@code current} in number order, wrapping around (the mouse wheel).
     * Without a current camera, the first (or last, going back) one.
     */
    public CameraSpot step(int current, int delta) {
        List<CameraSpot> list = all();
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).number() == current) i = k;
        return step(list, i, delta);
    }

    /** Same, from a camera key (a camera of another group counts as "no current camera"). */
    public CameraSpot step(String currentKey, int delta) {
        List<CameraSpot> list = all();
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).key().equals(currentKey)) i = k;
        return step(list, i, delta);
    }

    private static CameraSpot step(List<CameraSpot> list, int i, int delta) {
        if (list.isEmpty()) return null;
        if (i < 0) return delta >= 0 ? list.getFirst() : list.getLast();
        int n = list.size();
        return list.get(((i + delta) % n + n) % n);
    }

    /**
     * Finds a camera of this list from what an organiser typed or said: its number ("2", "caméra 2", "cam 2",
     * "deux"), its exact name, a role ("large", "plan large", "trône", "podium"), then a name that contains the
     * words. Case and accents are ignored. Null when nothing (or several cameras equally) match. Meant for a group
     * view (see {@link #resolve(String, String, Map)} for the whole grammar).
     */
    public CameraSpot resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String key = fold(ref);
        String bare = bare(key);
        Integer n = number(bare);
        if (n == null) n = number(key);
        if (n != null) return get(n);
        List<CameraSpot> all = all();
        for (CameraSpot s : all) if (fold(s.name()).equals(key)) return s;
        String role = roleOf(key, bare);
        if (!role.isEmpty()) {
            CameraSpot r = withRole(role);
            if (r != null) return r;
            if (role.equals(CameraSpot.WIDE)) return wide();
        }
        List<CameraSpot> partial = all.stream().filter(s -> fold(s.name()).contains(bare.isEmpty() ? key : bare)).toList();
        return partial.size() == 1 ? partial.getFirst() : null;
    }

    private static String bare(String folded) {
        return folded.replaceFirst("^(la |le |l')?(camera|cam|plan|shot)\\s*(n°|no|numero)?\\s*", "").trim();
    }

    private static String roleOf(String key, String bare) {
        String role = CameraSpot.normalizeRole(key);
        if (role.isEmpty()) role = CameraSpot.normalizeRole(bare);
        if (role.isEmpty() && (key.contains("trone") || key.contains("throne"))) role = CameraSpot.THRONE;
        if (role.isEmpty() && key.contains("podium")) role = CameraSpot.PODIUM;
        if (role.isEmpty() && key.contains("large")) role = CameraSpot.WIDE;
        return role;
    }

    // ---------------------------------------------------------------- whole grammar

    /** "caméra 2 du studio", "la 1 d'Ensuri", "plan large de la manche pont" */
    private static final Pattern OF_PLACE = Pattern.compile("^(?<cam>.+?)\\s+(?:du|de la|de l'|des|de|d')\\s*(?:manche |zone |salle )?(?<place>.+)$");
    /** "studio 2", "caméra studio 2", "pont large" */
    private static final Pattern PLACE_FIRST = Pattern.compile("^(?:(?:la |le )?(?:camera|cam|plan)\\s+)?(?<place>.+?)\\s+(?<cam>\\S+)$");

    /**
     * Finds a camera anywhere from a reference relative to the group the operator works in ({@code focus}):
     * <ul>
     *   <li>"studio:2", "pont:2", "pont:ensuri:1", "ensuri:1" (a zone of the focused round): that group;</li>
     *   <li>"caméra 2 du studio", "caméra 1 d'Ensuri", "studio 2": a place said in words — a lieu or zone id, or
     *       any phrase of {@code places} (round names and nicknames, zone names, contestants → their zone);</li>
     *   <li>anything else ("2", "Plan large", "trône"): in the focus group. A role missing there is looked for in
     *       the focus' lieu, then in the studio (the throne's reaction shot works during a round); a name missing
     *       there is looked for everywhere (if only one camera has it). A number is never looked for elsewhere.</li>
     * </ul>
     *
     * @param places folded phrase → group key, may be empty
     */
    public CameraSpot resolve(String ref, String focus, Map<String, String> places) {
        if (ref == null || ref.isBlank()) return null;
        String f = focus == null || focus.isBlank() ? CameraSpot.STUDIO : focus;
        String raw = ref.trim();
        int colon = raw.lastIndexOf(':');
        if (colon > 0) {
            String g = place(raw.substring(0, colon), f, places, true);
            return g == null ? null : inGroup(g, raw.substring(colon + 1));
        }
        String key = fold(raw);
        // A camera named like that in the focus group wins over any reading of the words
        CameraSpot exact = group(f).byName(key);
        if (exact != null) return exact;
        Matcher m = OF_PLACE.matcher(key);
        if (m.matches()) {
            String g = place(m.group("place"), f, places, false);
            if (g != null) {
                CameraSpot s = inGroup(g, m.group("cam"));
                if (s != null) return s;
            }
        }
        m = PLACE_FIRST.matcher(key);
        if (m.matches()) {
            String g = place(m.group("place"), f, places, false);
            if (g != null) {
                CameraSpot s = inGroup(g, m.group("cam"));
                if (s != null) return s;
            }
        }
        String bare = bare(key);
        // "Plan large" in a zone: the zone's own wide camera, else the round's, else the zone's first
        String zoneLieu = CameraSpot.lieuOf(f);
        if (!zoneLieu.equals(f) && number(bare) == null && number(key) == null && roleOf(key, bare).equals(CameraSpot.WIDE)) {
            CameraSpot w = group(f).withRole(CameraSpot.WIDE);
            if (w == null) w = group(zoneLieu).wide();
            if (w == null) w = group(f).wide();
            if (w != null) return w;
        }
        CameraSpot s = group(f).resolve(raw);
        if (s != null) return s;
        if (number(bare) != null || number(key) != null) return null;
        String role = roleOf(key, bare);
        if (!role.isEmpty()) {
            String lieu = CameraSpot.lieuOf(f);
            boolean wide = role.equals(CameraSpot.WIDE);
            CameraSpot r = null;
            if (!lieu.equals(f)) r = wide ? group(lieu).wide() : group(lieu).withRole(role);
            if (r == null) r = wide ? group(CameraSpot.STUDIO).wide() : group(CameraSpot.STUDIO).withRole(role);
            return r;
        }
        return byName(key);
    }

    /** The only camera with that exact (folded) name, or null. */
    private CameraSpot byName(String folded) {
        CameraSpot found = null;
        for (CameraSpot s : byKey.values()) {
            if (!fold(s.name()).equals(folded)) continue;
            if (found != null) return null;
            found = s;
        }
        return found;
    }

    /** A camera of group {@code g}; a role missing in a zone is looked for in its lieu. */
    private CameraSpot inGroup(String g, String ref) {
        CameraSpot s = group(g).resolve(ref);
        if (s != null) return s;
        String key = fold(ref);
        String role = roleOf(key, bare(key));
        String lieu = CameraSpot.lieuOf(g);
        if (role.isEmpty() || lieu.equals(g)) return null;
        return role.equals(CameraSpot.WIDE) ? group(lieu).wide() : group(lieu).withRole(role);
    }

    /**
     * The group a place phrase means, or null: an exact group key ("pont:ensuri"), a phrase of {@code places}, a
     * lieu having cameras, a zone of the focused lieu, or a zone found in only one lieu.
     *
     * @param explicit the phrase came before a colon: "studio" and "plateau" are surely the studio
     */
    public String place(String phrase, String focus, Map<String, String> places, boolean explicit) {
        if (phrase == null) return null;
        String raw = fold(phrase).replaceFirst("^(le |la |les |l'|du |de la |de l'|des |d')", "").trim();
        if (raw.isEmpty()) return null;
        String[] parts = raw.split(":", 2);
        if (parts.length == 2) {
            String l = lieuFor(parts[0], places);
            return CameraSpot.group(l == null ? parts[0] : l, parts[1]);
        }
        String p = raw.replaceFirst("^(manche|zone|salle|equipe|lieu) ", "");
        if (places != null) {
            String g = places.get(raw);
            if (g == null) g = places.get(p);
            if (g != null) return g;
        }
        if (CameraSpot.normalizeLieu(p).equals(CameraSpot.STUDIO) && (explicit || p.equals("studio") || p.equals("plateau")))
            return CameraSpot.STUDIO;
        String slug = CameraSpot.normalizeZone(p);
        if (slug.isEmpty()) return null;
        List<String> groups = groups();
        if (groups.contains(slug)) return slug;
        for (String g : groups) if (CameraSpot.lieuOf(g).equals(slug)) return slug;
        String focusLieu = CameraSpot.lieuOf(focus == null ? CameraSpot.STUDIO : focus);
        if (groups.contains(focusLieu + ":" + slug)) return focusLieu + ":" + slug;
        String found = null;
        for (String g : groups) {
            if (!CameraSpot.zoneOf(g).equals(slug)) continue;
            if (found != null) return null;
            found = g;
        }
        if (found != null) return found;
        // Typed before a colon: a group that has no camera yet (to say so), nothing for words
        return explicit ? (CameraSpot.STUDIO.equals(focusLieu) ? slug : focusLieu + ":" + slug) : null;
    }

    private static String lieuFor(String phrase, Map<String, String> places) {
        if (places == null) return null;
        String g = places.get(fold(phrase));
        return g == null ? null : CameraSpot.lieuOf(g);
    }

    private static final List<String> NUMBER_WORDS = List.of("zero", "un", "deux", "trois", "quatre", "cinq", "six", "sept",
            "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize");

    private static Integer number(String s) {
        if (s.matches("\\d{1,3}")) return Integer.parseInt(s);
        if (s.equals("une")) return 1;
        int i = NUMBER_WORDS.indexOf(s);
        return i >= 0 ? i : null;
    }

    /** Lower case, no accents, single spaces. */
    public static String fold(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('’', '\'').replaceAll("\\s+", " ");
    }

    /** Sorted copy, in {@link #ORDER}. */
    public static List<CameraSpot> sorted(List<CameraSpot> spots) {
        List<CameraSpot> out = new ArrayList<>(spots);
        out.sort(ORDER);
        return out;
    }
}
