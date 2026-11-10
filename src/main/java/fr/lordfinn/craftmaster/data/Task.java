package fr.lordfinn.craftmaster.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The text of a round's task (chantier D), read by the players on the card of their sealed envelope: a short
 * title, the text (paragraphs separated by blank lines, line breaks kept), optional rules shown as bullets, an
 * optional duration shown on the card (0 = the round's own timer) and the colour of the wax seal.
 *
 * <p>Whether it was revealed is not part of the data (it is not undone with the scores): see
 * {@code fr.lordfinn.craftmaster.task.TaskStore}.
 */
public final class Task {

    /** Colours of the wax, also the envelope block states (fr.lordfinn.craftmaster.task.WaxColor). */
    public static final String DEFAULT_WAX = "red";

    public String title = "";
    public String text = "";
    public final List<String> rules = new ArrayList<>();
    /** Duration shown on the card in seconds; 0 = the round's timer (or none). */
    public int seconds;
    public String wax = DEFAULT_WAX;
    /** Starting the round reveals the task (else only an envelope opened or the organiser does). */
    public boolean revealOnStart = true;

    public Task copy() {
        Task t = new Task();
        t.title = title;
        t.text = text;
        t.rules.addAll(rules);
        t.seconds = seconds;
        t.wax = wax;
        t.revealOnStart = revealOnStart;
        return t;
    }

    /** Nothing written yet. */
    public boolean isEmpty() {
        return title.isBlank() && text.isBlank() && rules.isEmpty();
    }

    /** The duration to show: its own, else the round's timer. */
    public int shownSeconds(Manche m) {
        return seconds > 0 ? seconds : m == null ? 0 : m.timer;
    }

    /**
     * Tidies a text typed or dictated: Windows line ends, trailing spaces, the literal "\n" typed in a command,
     * more than one blank line in a row, blank lines at both ends.
     */
    public static String cleanText(String s) {
        if (s == null) return "";
        String t = s.replace("\r\n", "\n").replace('\r', '\n').replace("\\n", "\n");
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : t.split("\n", -1)) {
            String l = line.stripTrailing();
            if (l.isBlank()) {
                if (++blank > 1) continue;
                l = "";
            } else {
                blank = 0;
            }
            out.append(l).append('\n');
        }
        return out.toString().strip();
    }

    /** "a | b | c" or one rule per line → the rules, blank ones and leading bullets removed. */
    public static List<String> parseRules(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String r : s.replace("\\n", "\n").split("[|\n]")) {
            String t = r.strip();
            while (!t.isEmpty() && "•-*·".indexOf(t.charAt(0)) >= 0) t = t.substring(1).strip();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    Map<String, Object> toYaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        Maps.putIf(m, "title", title.isEmpty() ? null : title);
        Maps.putIf(m, "text", text.isEmpty() ? null : text);
        Maps.putIf(m, "rules", new ArrayList<>(rules));
        Maps.putIf(m, "seconds", seconds);
        if (!DEFAULT_WAX.equals(wax)) m.put("wax", wax);
        if (!revealOnStart) m.put("revealOnStart", false);
        return m;
    }

    static Task fromYaml(Map<String, Object> m) {
        Task t = new Task();
        t.title = Maps.str(m.get("title"), "");
        t.text = Maps.str(m.get("text"), "");
        t.rules.addAll(Maps.strings(m.get("rules")));
        t.seconds = Math.max(0, Maps.toInt(m.get("seconds")));
        t.wax = Maps.str(m.get("wax"), DEFAULT_WAX).toLowerCase(Locale.ROOT);
        t.revealOnStart = !m.containsKey("revealOnStart") || Maps.bool(m.get("revealOnStart"));
        return t;
    }
}
