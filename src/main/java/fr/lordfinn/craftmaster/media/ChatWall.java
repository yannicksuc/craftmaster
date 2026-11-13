package fr.lordfinn.craftmaster.media;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The chat wall of the studio screens: a screen whose address is {@code chat:twitch}, {@code chat:jeu} or
 * {@code chat:tout} shows the latest chat messages (the Twitch chat read by the broadcast game, the players' chat, or
 * both). Pure logic, unit tested: the address, cleaning a message, the author's colour, the history kept by the
 * server, the Twitch IRC tags and moderation lines, word wrapping. The server side is {@link ChatWallServer}, the
 * drawing fr.lordfinn.craftmaster.client.media.ChatWallClient.
 */
public final class ChatWall {

    private ChatWall() {}

    /** Address prefix of a chat wall. */
    public static final String SCHEME = "chat:";
    /** Longest message kept (characters), longest author name. */
    public static final int MAX_TEXT = 200, MAX_AUTHOR = 25;
    /** Messages kept per source, for someone arriving. */
    public static final int HISTORY = 50;

    /** Where a message comes from. */
    public enum Source { TWITCH, JEU }

    /** What a chat wall shows. */
    public enum Mode {
        TWITCH("twitch"), JEU("jeu"), TOUT("tout");

        public final String word;

        Mode(String word) {
            this.word = word;
        }

        public boolean shows(Source s) {
            return this == TOUT || (this == TWITCH) == (s == Source.TWITCH);
        }

        /** The canonical address, e.g. "chat:twitch". */
        public String address() {
            return SCHEME + word;
        }
    }

    /**
     * The mode of a screen address, or null when it is not a chat wall. "chat" alone or "chat:" = both; accepted
     * words: twitch; jeu, game, minecraft, mc; tout, all, both, les2. Case ignored.
     */
    public static Mode mode(String url) {
        if (url == null) return null;
        String u = url.strip().toLowerCase(Locale.ROOT);
        if (u.equals("chat")) return Mode.TOUT;
        if (!u.startsWith(SCHEME)) return null;
        return word(u.substring(SCHEME.length()));
    }

    /** "twitch" / "jeu" / "tout" (and their synonyms, "" = tout) → the mode, or null. */
    public static Mode word(String w) {
        if (w == null) return null;
        return switch (w.strip().toLowerCase(Locale.ROOT)) {
            case "twitch", "tw" -> Mode.TWITCH;
            case "jeu", "game", "minecraft", "mc" -> Mode.JEU;
            case "", "tout", "all", "both", "les2", "lesdeux" -> Mode.TOUT;
            default -> null;
        };
    }

    // ---------------------------------------------------------------- cleaning

    /**
     * A message fit for the wall, or null to ignore it: control characters and Minecraft formatting codes removed,
     * spaces collapsed, at most {@link #MAX_TEXT} characters (cut with "…"); empty messages and bot commands
     * ("!tache") are ignored.
     */
    public static String cleanText(String text) {
        String t = clean(text);
        if (t.isEmpty() || t.startsWith("!")) return null;
        if (t.codePointCount(0, t.length()) > MAX_TEXT) t = t.substring(0, t.offsetByCodePoints(0, MAX_TEXT - 1)).stripTrailing() + "…";
        return t;
    }

    /** An author name fit for the wall ("?" when nothing is left). */
    public static String cleanAuthor(String author) {
        String a = clean(author).replace(" ", "");
        if (a.isEmpty()) return "?";
        return a.codePointCount(0, a.length()) > MAX_AUTHOR ? a.substring(0, a.offsetByCodePoints(0, MAX_AUTHOR)) : a;
    }

    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            // A Minecraft formatting code ("§c"): both characters go
            if (cp == '§') {
                if (i < s.length()) i += Character.charCount(s.codePointAt(i));
                continue;
            }
            // Control characters, bidi overrides and the like
            if (type == Character.CONTROL || type == Character.UNASSIGNED || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE || (type == Character.FORMAT && cp != 0x200D)) {
                if (Character.isWhitespace(cp) || cp == '\t') space = true;
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = true;
                continue;
            }
            if (space && !b.isEmpty()) b.append(' ');
            space = false;
            b.appendCodePoint(cp);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- colours

    /** No colour given: the wall picks one from the name. */
    public static final int NO_COLOR = -1;

    /** A stable, bright hue from the name (the same name always gets the same colour), as 0xRRGGBB. */
    public static int nameColor(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int h = 0x811C9DC5;
        for (int i = 0; i < n.length(); i++) {
            h ^= n.charAt(i);
            h *= 0x01000193;
        }
        float hue = Math.floorMod(h, 360) / 360f;
        return hsv(hue, 0.55f, 1f);
    }

    /** A colour that reads on the wall's dark panel: too dark ones are lightened toward white. */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < 10 && luminance(r, g, b) < 0.45; i++) {
            r = Math.min(255, r + (255 - r) / 4 + 1);
            g = Math.min(255, g + (255 - g) / 4 + 1);
            b = Math.min(255, b + (255 - b) / 4 + 1);
        }
        return (r << 16) | (g << 8) | b;
    }

    /** The colour of an author: the given one (made readable), else one from the name. */
    public static int authorColor(String author, int given) {
        return given == NO_COLOR ? nameColor(author) : readable(given & 0xFFFFFF);
    }

    /** "#1E90FF" → 0x1E90FF, anything else → {@link #NO_COLOR}. */
    public static int parseColor(String s) {
        if (s == null || !s.matches("#[0-9A-Fa-f]{6}")) return NO_COLOR;
        return Integer.parseInt(s.substring(1), 16);
    }

    private static double luminance(int r, int g, int b) {
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float r, g, b;
        int sector = (int) (h * 6) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    // ---------------------------------------------------------------- history

    /** One message on the wall: {@code id} is unique (Twitch message id, or given by the server). */
    public record Line(Source source, String id, String author, int color, String text) {}

    /**
     * The messages kept by the server, {@link #HISTORY} per source (a busy Twitch chat does not push the players'
     * messages away), each id once (several organisers' games may forward the same Twitch message).
     */
    public static final class History {
        /** Every kept message, oldest first, sources mixed in arrival order. */
        private final List<Line> lines = new ArrayList<>();
        private final LinkedHashSet<String> seen = new LinkedHashSet<>();
        private final int max;

        public History() {
            this(HISTORY);
        }

        public History(int max) {
            this.max = max;
        }

        /** Adds a message; false when it is a repeat (same id). */
        public boolean add(Line l) {
            if (!seen.add(l.id())) return false;
            while (seen.size() > max * 8) seen.removeFirst();
            lines.add(l);
            if (lines.stream().filter(x -> x.source() == l.source()).count() > max) {
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).source() == l.source()) {
                        lines.remove(i);
                        break;
                    }
                }
            }
            return true;
        }

        /** Removes the message with this id; true if it was there. */
        public boolean removeId(String id) {
            return lines.removeIf(l -> l.id().equals(id));
        }

        /** Removes every message of this author from this source (a ban or a timeout on Twitch). */
        public boolean removeAuthor(Source source, String author) {
            return lines.removeIf(l -> l.source() == source && l.author().equalsIgnoreCase(author));
        }

        /** Removes every message of a source. */
        public boolean clear(Source source) {
            return lines.removeIf(l -> l.source() == source);
        }

        /** Every kept message, oldest first. */
        public List<Line> all() {
            return List.copyOf(lines);
        }
    }

    // ---------------------------------------------------------------- Twitch IRC (tags, moderation)

    /** The IRCv3 tags of a raw line ("@color=#FF0000;display-name=Bob;id=… :…"), unescaped; empty without tags. */
    public static Map<String, String> tags(String line) {
        Map<String, String> out = new HashMap<>();
        if (line == null || !line.startsWith("@")) return out;
        int sp = line.indexOf(' ');
        String raw = sp < 0 ? line.substring(1) : line.substring(1, sp);
        for (String kv : raw.split(";")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            out.put(kv.substring(0, eq), unescape(kv.substring(eq + 1)));
        }
        return out;
    }

    private static String unescape(String v) {
        StringBuilder b = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c != '\\' || i + 1 >= v.length()) {
                b.append(c);
                continue;
            }
            char n = v.charAt(++i);
            b.append(switch (n) {
                case ':' -> ';';
                case 's' -> ' ';
                case 'r' -> '\r';
                case 'n' -> '\n';
                default -> n;
            });
        }
        return b.toString();
    }

    /** A moderation line of the Twitch chat: a message deleted ({@code id}), a user banned ({@code user}), or all cleared. */
    public record Moderation(String id, String user) {
        public boolean clearAll() {
            return id.isEmpty() && user.isEmpty();
        }
    }

    /**
     * CLEARMSG (one message deleted: its id) or CLEARCHAT (a user timed out or banned: their name; no name = the whole
     * chat cleared), else null.
     */
    public static Moderation moderation(String line) {
        if (line == null) return null;
        String l = line;
        Map<String, String> t = tags(l);
        if (l.startsWith("@")) {
            int sp = l.indexOf(' ');
            if (sp < 0) return null;
            l = l.substring(sp + 1);
        }
        String[] parts = l.split(" ", 4);
        if (parts.length < 3) return null;
        String cmd = parts[1];
        if (cmd.equals("CLEARMSG")) {
            String id = t.getOrDefault("target-msg-id", "");
            return id.isEmpty() ? null : new Moderation(id, "");
        }
        if (cmd.equals("CLEARCHAT")) {
            String user = parts.length >= 4 && parts[3].startsWith(":") ? parts[3].substring(1).strip() : "";
            return new Moderation("", user);
        }
        return null;
    }

    // ---------------------------------------------------------------- wrapping

    /**
     * Word wrap: the [start, end) character ranges of {@code text}'s lines, each at most {@code maxWidth} wide as
     * measured by {@code width} (the font). Spaces at a break are dropped; a word wider than a line is cut.
     */
    public static List<int[]> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<int[]> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        int n = text.length(), start = 0;
        while (start < n) {
            while (start < n && text.charAt(start) == ' ') start++;
            if (start >= n) break;
            int end = start, lastSpace = -1;
            while (end < n) {
                int next = end + Character.charCount(text.codePointAt(end));
                if (width.applyAsInt(text.substring(start, next)) > maxWidth) break;
                if (text.charAt(end) == ' ') lastSpace = end;
                end = next;
            }
            if (end >= n) {
                out.add(new int[]{start, n});
                break;
            }
            if (end > start && text.charAt(end) == ' ') {
                // The line is full right before a space: break there
                out.add(new int[]{start, end});
                start = end + 1;
            } else if (lastSpace > start) {
                out.add(new int[]{start, lastSpace});
                start = lastSpace + 1;
            } else {
                // One word wider than the line (or not even one character fits): cut it
                if (end == start) end = start + Character.charCount(text.codePointAt(start));
                out.add(new int[]{start, end});
                start = end;
            }
        }
        return out;
    }
}
