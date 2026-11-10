package fr.lordfinn.craftmaster.trial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The results sheet of a round, like the judge's sheet on the show: one value per contestant (a time, a count, a
 * distance, a mark of the jury, a weight) or "disqualified". From it, a ranking (ties share their place) and the
 * points proposed by the scale (5-4-3-2-1 by default). Pure data: stored with its round ({@code Manche#results}),
 * so changing it is undoable like the scores, and applied as {@code score.set} actions.
 */
public final class ResultSheet {

    /** What is measured; each kind has its natural order (a time: the smallest wins) and its unit. */
    public enum Kind {
        TIME("time", true, "Temps", "Time"),
        COUNT("count", false, "Nombre", "Count"),
        DISTANCE("distance", false, "Distance", "Distance"),
        MARK("mark", false, "Note du jury", "Jury mark"),
        WEIGHT("weight", false, "Poids", "Weight");

        public final String id;
        /** Default order: the smallest value wins (times). */
        public final boolean lowerWins;
        public final String fr, en;

        Kind(String id, boolean lowerWins, String fr, String en) {
            this.id = id;
            this.lowerWins = lowerWins;
            this.fr = fr;
            this.en = en;
        }

        /** "time", "temps", "chrono", "secondes"... → TIME; null if unknown. */
        public static Kind parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) if (kind.id.equals(k) || kind.fr.equalsIgnoreCase(k) || kind.en.equalsIgnoreCase(k)) return kind;
            return switch (k) {
                case "temps", "chrono", "secondes", "seconde", "s", "duree", "durée" -> TIME;
                case "nombre", "compte", "points", "quantite", "quantité", "n" -> COUNT;
                case "metres", "mètres", "m", "longueur", "hauteur" -> DISTANCE;
                case "note", "jury", "notes" -> MARK;
                case "poids", "kg", "masse" -> WEIGHT;
                default -> null;
            };
        }
    }

    /** One contestant's line: a value (null if only disqualified) and the disqualification mark. */
    public record Entry(Double value, boolean dq) {}

    /** A contestant in the ranking: place (1 = best, 0 = disqualified or no value) and proposed points (null = none). */
    public record Placed(String player, int place, Double value, boolean dq, Integer points) {}

    public static final String DEFAULT_SCALE = "5,4,3,2,1";

    public Kind kind = Kind.COUNT;
    /** Explicit order; null = the kind's natural order. */
    public Boolean lowerWins;
    /** Points per place, "5,4,3,2,1"; null = {@link #DEFAULT_SCALE}. */
    public String scale;
    public final Map<String, Entry> entries = new LinkedHashMap<>();

    public boolean lowerWins() {
        return lowerWins != null ? lowerWins : kind.lowerWins;
    }

    public String scaleOrDefault() {
        return scale == null ? DEFAULT_SCALE : scale;
    }

    public boolean isEmpty() {
        return entries.isEmpty() && scale == null && lowerWins == null && kind == Kind.COUNT;
    }

    /** Sets a contestant's value (and lifts a disqualification). */
    public void set(String player, double value) {
        entries.put(player, new Entry(round(value), false));
    }

    /** Disqualifies (keeps the value, so lifting it gives the value back) or lifts the disqualification. */
    public void disqualify(String player, boolean dq) {
        Entry e = entries.get(player);
        Double v = e == null ? null : e.value();
        if (!dq && v == null) entries.remove(player);
        else entries.put(player, new Entry(v, dq));
    }

    /** Values are kept to the hundredth: what the screen shows is what decides a tie. */
    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ---------------------------------------------------------------- ranking

    /**
     * The ranking of {@code cast} (contestants in show order; anyone with an entry who is not in it is added after):
     * contestants with a value first, best first, ties sharing a place (two firsts, then a third); then the
     * disqualified (0 point); then those without a value (no proposal: their score is left alone).
     */
    public List<Placed> rank(Collection<String> cast) {
        int[] points = Scale.parse(scaleOrDefault());
        LinkedHashSet<String> everyone = new LinkedHashSet<>(cast);
        everyone.addAll(entries.keySet());
        List<String> valued = new ArrayList<>();
        for (String p : everyone) {
            Entry e = entries.get(p);
            if (e != null && !e.dq() && e.value() != null) valued.add(p);
        }
        boolean lower = lowerWins();
        // Stable sort: equal values keep the show order
        valued.sort((a, b) -> {
            double va = entries.get(a).value(), vb = entries.get(b).value();
            return lower ? Double.compare(va, vb) : Double.compare(vb, va);
        });
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < valued.size(); i++) {
            String p = valued.get(i);
            double v = entries.get(p).value();
            int place = 1;
            for (String q : valued) {
                double w = entries.get(q).value();
                if (lower ? w < v : w > v) place++;
            }
            out.add(new Placed(p, place, v, false, Scale.pointsFor(points, place)));
        }
        for (String p : everyone) {
            Entry e = entries.get(p);
            if (e != null && e.dq()) out.add(new Placed(p, 0, e.value(), true, 0));
        }
        for (String p : everyone) {
            Entry e = entries.get(p);
            if (e == null || (!e.dq() && e.value() == null)) out.add(new Placed(p, 0, null, false, null));
        }
        return out;
    }

    // ---------------------------------------------------------------- values as text

    /** A value as shown: "1:02,5" / "42,3 s", "17", "12,5 m", "8", "3,2 kg" (French decimal comma). */
    public static String format(Kind kind, double v) {
        return switch (kind) {
            case TIME -> Stopwatch.format(Math.round(v * 1000));
            case DISTANCE -> decimal(v, 2) + " m";
            case WEIGHT -> decimal(v, 2) + " kg";
            case COUNT, MARK -> decimal(v, 2);
        };
    }

    public String format(double v) {
        return format(kind, v);
    }

    /** 12.50 → "12,5"; 12.0 → "12" (at most {@code digits} decimals). */
    public static String decimal(double v, int digits) {
        String s = String.format(Locale.ROOT, "%." + digits + "f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.replace('.', ',');
    }

    /**
     * Reads a value typed or said: "42", "42,5", "42.5 s", "1:02.5" (minutes:seconds), "1m30", "12 m", "3 kg",
     * "8/10" (the mark). Units are only decoration; null if it is not a number. For a distance, "1m30" is 1.30 m.
     */
    public static Double parse(String text) {
        return parse(null, text);
    }

    public static Double parse(Kind kind, String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT).replace(',', '.').replaceAll("\\s+", "");
        if (s.isEmpty()) return null;
        try {
            int slash = s.indexOf('/');
            if (slash > 0) s = s.substring(0, slash);
            // 1:02.5 / 1:02:03 (h:m:s)
            if (s.contains(":")) {
                double total = 0;
                for (String part : s.replaceAll("[^0-9.:]", "").split(":")) total = total * 60 + (part.isEmpty() ? 0 : Double.parseDouble(part));
                return total;
            }
            // 1m30 / 1min30s / 2min (minutes) — but "12m" alone is metres
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:min|mn|m)(\\d+(?:\\.\\d+)?)s?$").matcher(s);
            if (m.matches()) {
                double a = Double.parseDouble(m.group(1)), b = Double.parseDouble(m.group(2));
                return kind == Kind.DISTANCE ? a + b / 100.0 : a * 60 + b;
            }
            m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:min|mn)$").matcher(s);
            if (m.matches()) return Double.parseDouble(m.group(1)) * 60;
            m = java.util.regex.Pattern.compile("^(-?\\d+(?:\\.\\d+)?)[a-zé]*$").matcher(s);
            if (m.matches()) return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    // ---------------------------------------------------------------- storage

    public Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind.id);
        if (lowerWins != null) m.put("order", lowerWins ? "lower" : "higher");
        if (scale != null) m.put("scale", scale);
        Map<String, Object> es = new LinkedHashMap<>();
        entries.forEach((p, e) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            if (e.value() != null) x.put("value", e.value());
            if (e.dq()) x.put("dq", true);
            es.put(p, x);
        });
        m.put("entries", es);
        return m;
    }

    public static ResultSheet fromYaml(Object o) {
        ResultSheet r = new ResultSheet();
        if (!(o instanceof Map<?, ?> m)) return r;
        Kind k = Kind.parse(m.get("kind") == null ? null : String.valueOf(m.get("kind")));
        if (k != null) r.kind = k;
        Object order = m.get("order");
        if (order != null) r.lowerWins = "lower".equalsIgnoreCase(String.valueOf(order));
        if (m.get("scale") != null) r.scale = String.valueOf(m.get("scale"));
        if (m.get("entries") instanceof Map<?, ?> es) {
            es.forEach((p, v) -> {
                if (!(v instanceof Map<?, ?> x)) return;
                Double value = x.get("value") instanceof Number n ? n.doubleValue() : parse(x.get("value") == null ? null : String.valueOf(x.get("value")));
                boolean dq = Boolean.TRUE.equals(x.get("dq")) || "true".equalsIgnoreCase(String.valueOf(x.get("dq")));
                if (value != null || dq) r.entries.put(String.valueOf(p), new Entry(value, dq));
            });
        }
        return r;
    }
}
