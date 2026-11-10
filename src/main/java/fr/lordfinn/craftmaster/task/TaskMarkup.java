package fr.lordfinn.craftmaster.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The small markup of a task text, rendered on the letter (and, simplified, on the overlay card):
 * <pre>
 * # Tâche 4 : physique        heading (red capitals)
 * (blank line)                paragraph break
 * ---                         small centred separator
 * > Psst… regarde sous la table   handwritten note (blue ink)
 * !règle Le plus rapide gagne.    the final rule (centred, bold)
 * [tampon: URGENT]            red dashed stamp
 * [signature: La Régie]       signature at the bottom (default "Le CraftMaster")
 * **gras**  __souligné__  ~~barré~~  {rouge}encre rouge{/}  (rouge, bleu, vert, gris, noir)
 * [secret]la suite[/secret]   hidden (black bar) until the organiser reveals it ("révèle la suite")
 * {joueur} {equipe} {temps}   the reader's name, their team, the round's time
 * </pre>
 * The secret parts never leave the server for a non-organiser until revealed: {@link #redact} turns each hidden
 * part into a placeholder {@code [secret:N]} (N = its length), revealed parts into plain text.
 */
public final class TaskMarkup {

    private TaskMarkup() {}

    public enum Kind { HEADING, LINE, GAP, SEPARATOR, NOTE, RULE, STAMP }

    public static final int BOLD = 1, UNDERLINE = 2, STRIKE = 4;
    /** {@link Span#secret}: not a secret, a hidden part (bar, text unknown), a hidden part an organiser can read. */
    public static final int VISIBLE = 0, HIDDEN = 1, HIDDEN_READABLE = 2;

    /** A run of text with one style; {@code color} is a colour id ("" = the ink of the block). */
    public record Span(String text, int style, String color, int secret) {}

    public record Block(Kind kind, List<Span> spans) {
        public String plain() {
            StringBuilder b = new StringBuilder();
            for (Span s : spans) b.append(s.secret() == HIDDEN ? "█".repeat(Math.min(12, s.text().length())) : s.text());
            return b.toString();
        }
    }

    /** {@code signature}: null = the default one. */
    public record Doc(List<Block> blocks, String signature) {}

    /** Colour ids → RGB of the ink. */
    public static int inkColor(String id) {
        return switch (id) {
            case "red" -> 0xA8282A;
            case "blue" -> 0x2A4A9A;
            case "green" -> 0x2F6A2A;
            case "gray" -> 0x7A746A;
            default -> 0x2E2A26;
        };
    }

    /** "rouge", "red"... → colour id, or null. */
    static String colorId(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "rouge", "red" -> "red";
            case "bleu", "blue" -> "blue";
            case "vert", "green" -> "green";
            case "gris", "gray", "grey" -> "gray";
            case "noir", "black" -> "black";
            default -> null;
        };
    }

    // ---------------------------------------------------------------- secrets (server side)

    private static final String OPEN = "[secret]", CLOSE = "[/secret]";

    /** Number of [secret] parts in the text. */
    public static int secretCount(String text) {
        if (text == null) return 0;
        int n = 0;
        for (int i = text.indexOf(OPEN); i >= 0; i = text.indexOf(OPEN, i + OPEN.length())) n++;
        return n;
    }

    /**
     * What a reader may receive: the first {@code shown} secret parts become plain text; the others stay tagged
     * for an organiser ({@code readable}) or become a placeholder {@code [secret:N]} that carries only their length.
     * An unclosed part runs to the end of the text.
     */
    public static String redact(String text, int shown, boolean readable) {
        if (text == null || !text.contains(OPEN)) return text;
        StringBuilder out = new StringBuilder();
        int i = 0, part = 0;
        while (true) {
            int a = text.indexOf(OPEN, i);
            if (a < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, a);
            int b = text.indexOf(CLOSE, a + OPEN.length());
            String inner = text.substring(a + OPEN.length(), b < 0 ? text.length() : b);
            if (part < shown) out.append(inner);
            else if (readable) out.append(OPEN).append(inner).append(CLOSE);
            else out.append("[secret:").append(Math.max(1, Math.min(60, inner.codePointCount(0, inner.length())))).append(']');
            part++;
            if (b < 0) break;
            i = b + CLOSE.length();
        }
        return out.toString();
    }

    /** The text without any markup (chat fallback of a vanilla client, voice context). Secrets must be redacted first. */
    public static String plain(String text) {
        Doc d = parse(text, Map.of());
        StringBuilder b = new StringBuilder();
        for (Block bl : d.blocks()) {
            switch (bl.kind()) {
                case GAP -> b.append('\n');
                case SEPARATOR -> b.append("—\n");
                case STAMP -> b.append('[').append(bl.plain()).append("]\n");
                default -> b.append(bl.plain()).append('\n');
            }
        }
        return b.toString().strip();
    }

    // ---------------------------------------------------------------- parsing (client side)

    /** Replaces the variables ({joueur}, {equipe}, {temps} and their English names) with {@code vars} values. */
    public static String substitute(String text, Map<String, String> vars) {
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            switch (e.getKey()) {
                case "player" -> out = out.replace("{joueur}", v).replace("{player}", v);
                case "team" -> out = out.replace("{equipe}", v).replace("{équipe}", v).replace("{team}", v);
                case "time" -> out = out.replace("{temps}", v).replace("{time}", v);
                default -> out = out.replace("{" + e.getKey() + "}", v);
            }
        }
        return out;
    }

    public static Doc parse(String text, Map<String, String> vars) {
        List<Block> blocks = new ArrayList<>();
        String signature = null;
        if (text == null) return new Doc(blocks, null);
        String[] lines = substitute(text.replace("\r", ""), vars).split("\n", -1);
        boolean[] secret = {false};
        for (String raw : lines) {
            String line = raw.strip();
            String low = line.toLowerCase(Locale.ROOT);
            if (line.isEmpty()) {
                if (!blocks.isEmpty() && blocks.getLast().kind() != Kind.GAP) blocks.add(new Block(Kind.GAP, List.of()));
                continue;
            }
            if (line.matches("-{3,}")) {
                blocks.add(new Block(Kind.SEPARATOR, List.of()));
            } else if (line.startsWith("#")) {
                blocks.add(new Block(Kind.HEADING, spans(line.replaceFirst("^#+\\s*", ""), secret)));
            } else if (line.startsWith(">")) {
                blocks.add(new Block(Kind.NOTE, spans(line.substring(1).strip(), secret)));
            } else if (low.startsWith("!règle") || low.startsWith("!regle") || low.startsWith("!rule")) {
                blocks.add(new Block(Kind.RULE, spans(line.replaceFirst("^![^\\s]*\\s*", ""), secret)));
            } else if (bracket(low, "tampon") || bracket(low, "stamp")) {
                blocks.add(new Block(Kind.STAMP, spans(line.substring(line.indexOf(':') + 1, line.length() - 1).strip(), secret)));
            } else if (bracket(low, "signature")) {
                signature = line.substring(line.indexOf(':') + 1, line.length() - 1).strip();
            } else {
                blocks.add(new Block(Kind.LINE, spans(line, secret)));
            }
        }
        while (!blocks.isEmpty() && blocks.getLast().kind() == Kind.GAP) blocks.removeLast();
        return new Doc(blocks, signature);
    }

    private static boolean bracket(String low, String name) {
        return low.startsWith("[" + name) && low.endsWith("]") && low.indexOf(':') > 0 && !low.startsWith("[secret");
    }

    /** Inline styles; {@code secret[0]} carries an open [secret] over to the next lines. */
    static List<Span> spans(String s, boolean[] secret) {
        List<Span> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int style = 0;
        String color = "";
        int i = 0;
        while (i < s.length()) {
            String rest = s.substring(i);
            String tok = null;
            int nStyle = style, nSecret = -1;
            String nColor = color;
            if (rest.startsWith("**")) { tok = "**"; nStyle = style ^ BOLD; }
            else if (rest.startsWith("__")) { tok = "__"; nStyle = style ^ UNDERLINE; }
            else if (rest.startsWith("~~")) { tok = "~~"; nStyle = style ^ STRIKE; }
            else if (rest.startsWith(OPEN)) { tok = OPEN; nSecret = 1; }
            else if (rest.startsWith(CLOSE)) { tok = CLOSE; nSecret = 0; }
            else if (rest.startsWith("{/}")) { tok = "{/}"; nColor = ""; }
            else if (rest.startsWith("[secret:")) {
                int end = rest.indexOf(']');
                int n = end > 0 ? parseInt(rest.substring(8, end)) : -1;
                if (n > 0) {
                    flush(out, cur, style, color, secret[0]);
                    out.add(new Span("x".repeat(n), style, color, HIDDEN));
                    i += end + 1;
                    continue;
                }
            } else if (rest.startsWith("{")) {
                int end = rest.indexOf('}');
                String id = end > 0 ? colorId(rest.substring(1, end)) : null;
                if (id != null) { tok = rest.substring(0, end + 1); nColor = id; }
            }
            if (tok == null) {
                cur.appendCodePoint(s.codePointAt(i));
                i += Character.charCount(s.codePointAt(i));
                continue;
            }
            flush(out, cur, style, color, secret[0]);
            style = nStyle;
            color = nColor;
            if (nSecret >= 0) secret[0] = nSecret == 1;
            i += tok.length();
        }
        flush(out, cur, style, color, secret[0]);
        return out;
    }

    private static void flush(List<Span> out, StringBuilder cur, int style, String color, boolean secret) {
        if (cur.isEmpty()) return;
        out.add(new Span(cur.toString(), style, color, secret ? HIDDEN_READABLE : VISIBLE));
        cur.setLength(0);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
