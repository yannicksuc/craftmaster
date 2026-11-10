package fr.lordfinn.craftmaster.camera;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * The studio's cameras, by number, and how a spoken or typed reference finds one: "2", "caméra 2", "Plan large",
 * "large", "trône"… Pure logic (no Minecraft), used by the server's registry and by the broadcast clients.
 */
public final class CameraList {

    private final TreeMap<Integer, CameraSpot> byNumber = new TreeMap<>();

    public CameraList() {}

    public CameraList(List<CameraSpot> spots) {
        for (CameraSpot s : spots) byNumber.put(s.number(), s);
    }

    public List<CameraSpot> all() {
        return new ArrayList<>(byNumber.values());
    }

    public boolean isEmpty() {
        return byNumber.isEmpty();
    }

    public int size() {
        return byNumber.size();
    }

    public CameraSpot get(int number) {
        return byNumber.get(number);
    }

    public void put(CameraSpot spot) {
        byNumber.put(spot.number(), spot);
    }

    public CameraSpot remove(int number) {
        return byNumber.remove(number);
    }

    /** Smallest number not used yet (1 for an empty studio). */
    public int nextFree() {
        int n = 1;
        while (byNumber.containsKey(n)) n++;
        return n;
    }

    /** The first camera with that role, lowest number first; null if none. */
    public CameraSpot withRole(String role) {
        String r = CameraSpot.normalizeRole(role);
        if (r.isEmpty()) return null;
        for (CameraSpot s : byNumber.values()) if (s.role().equals(r)) return s;
        return null;
    }

    /** The wide shot: the camera marked "large", else the lowest number; null in an empty studio. */
    public CameraSpot wide() {
        CameraSpot w = withRole(CameraSpot.WIDE);
        return w != null ? w : byNumber.isEmpty() ? null : byNumber.firstEntry().getValue();
    }

    /**
     * The camera {@code delta} steps after {@code current} in number order, wrapping around (the mouse wheel).
     * Without a current camera, the first (or last, going back) one.
     */
    public CameraSpot step(int current, int delta) {
        if (byNumber.isEmpty()) return null;
        List<CameraSpot> list = all();
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).number() == current) i = k;
        if (i < 0) return delta >= 0 ? list.getFirst() : list.getLast();
        int n = list.size();
        return list.get(((i + delta) % n + n) % n);
    }

    /**
     * Finds a camera from what an organiser typed or said: its number ("2", "caméra 2", "cam 2", "deux"), its exact
     * name, a role ("large", "plan large", "trône", "podium"), then a name that contains the words. Case and accents
     * are ignored. Null when nothing (or several cameras equally) match.
     */
    public CameraSpot resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String key = fold(ref);
        String bare = key.replaceFirst("^(la |le |l')?(camera|cam|plan|shot)\\s*(n°|no|numero)?\\s*", "").trim();
        Integer n = number(bare);
        if (n == null) n = number(key);
        if (n != null) return byNumber.get(n);
        for (CameraSpot s : byNumber.values()) if (fold(s.name()).equals(key)) return s;
        String role = CameraSpot.normalizeRole(key);
        if (role.isEmpty()) role = CameraSpot.normalizeRole(bare);
        if (role.isEmpty() && (key.contains("trone") || key.contains("throne"))) role = CameraSpot.THRONE;
        if (role.isEmpty() && key.contains("podium")) role = CameraSpot.PODIUM;
        if (role.isEmpty() && key.contains("large")) role = CameraSpot.WIDE;
        if (!role.isEmpty()) {
            CameraSpot r = withRole(role);
            if (r != null) return r;
            if (role.equals(CameraSpot.WIDE)) return wide();
        }
        List<CameraSpot> partial = byNumber.values().stream()
                .filter(s -> fold(s.name()).contains(bare.isEmpty() ? key : bare)).toList();
        return partial.size() == 1 ? partial.getFirst() : null;
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

    /** Sorted copy, lowest number first. */
    public static List<CameraSpot> sorted(List<CameraSpot> spots) {
        List<CameraSpot> out = new ArrayList<>(spots);
        out.sort(Comparator.comparingInt(CameraSpot::number));
        return out;
    }
}
