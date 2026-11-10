package fr.lordfinn.craftmaster.trial;

import java.util.ArrayList;
import java.util.List;

/**
 * A points scale: the points of the 1st, 2nd, 3rd... place, "5,4,3,2,1" by default. Places past the end of the
 * scale get 0. Tied contestants share a place, so they all get that place's points (two firsts get 5 each, the
 * next one is third and gets 3), as on the show.
 */
public final class Scale {

    public static final int MAX_PLACES = 20, MAX_POINTS = 1000;

    private Scale() {}

    /** "5,4,3,2,1", "5-4-3-2-1", "5 4 3 2 1", "10;6;3" → the points; throws IllegalArgumentException if invalid. */
    public static int[] parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("barème vide");
        List<Integer> out = new ArrayList<>();
        for (String s : text.trim().split("[\\s,;/]+|(?<=\\d)-")) {
            if (s.isBlank()) continue;
            int v;
            try {
                v = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("« " + s.trim() + " » n'est pas un nombre entier");
            }
            if (Math.abs(v) > MAX_POINTS) throw new IllegalArgumentException("au plus " + MAX_POINTS + " points par place");
            out.add(v);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("barème vide");
        if (out.size() > MAX_PLACES) throw new IllegalArgumentException("au plus " + MAX_PLACES + " places");
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The scale written back the canonical way: "5,4,3,2,1". */
    public static String normalize(String text) {
        StringBuilder b = new StringBuilder();
        for (int v : parse(text)) b.append(b.isEmpty() ? "" : ",").append(v);
        return b.toString();
    }

    /** Points of a place (1-based); 0 past the end of the scale. */
    public static int pointsFor(int[] scale, int place) {
        return place >= 1 && place <= scale.length ? scale[place - 1] : 0;
    }
}
