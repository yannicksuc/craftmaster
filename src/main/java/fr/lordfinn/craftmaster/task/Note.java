package fr.lordfinn.craftmaster.task;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hidden note (chantier D): its text and look, who may read it, and where it is hidden. Hiding places are
 * {@link Spot}s (a block, a painting, a note pinned on a face); bottles and taken notes are items pointing at the
 * note by id, so editing the note changes every copy. Every first find is logged for the organisers.
 */
public final class Note {

    /** Paper looks of the reading screen. */
    public static final List<String> LOOKS = List.of("paper", "parchment", "notebook", "postit");
    /** Who may read it: everybody, the season's cast only, or the listed players. */
    public static final String ALL = "all", CAST = "cast", PLAYERS = "players";
    public static final List<String> READERS = List.of(ALL, CAST, PLAYERS);
    public static final int MAX_PIECES = 6;

    /**
     * Ways of hiding it. {@code UNDER}: under a carpet / slab, found by crouching and using it; {@code BOOK}: in a
     * book of a bookshelf; {@code BEHIND}: behind a painting or an item frame; {@code FALSE_BOTTOM}: false bottom of
     * a chest, crouch and use it; {@code INSIDE}: in a pot, a vase, a barrel... (use it); {@code PINNED}: a sheet
     * pinned on a face of a block (the underside of a table, the back of a wardrobe).
     */
    public enum Method {
        UNDER(true), BOOK(false), BEHIND(false), FALSE_BOTTOM(true), INSIDE(false), PINNED(false);

        /** The finder must crouch (the carpet is lifted, the bottom is felt for). */
        public final boolean sneak;

        Method(boolean sneak) {
            this.sneak = sneak;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Method of(String s) {
            for (Method m : values()) if (m.id().equalsIgnoreCase(s) || m.name().equalsIgnoreCase(s)) return m;
            return null;
        }
    }

    /**
     * A hiding place: a block ({@code entity} null) or an entity (painting, item frame) of a world. {@code piece}:
     * which torn piece is there (0 when the note is whole). {@code takenBy}: the player who took it (note "taken
     * once"), the place is then empty.
     */
    public static final class Spot {
        public String world;
        public int x, y, z;
        public String entity;
        public String method;
        public int piece;
        public String takenBy;
        /** For a pinned sheet: the side it shows (to put it back after a reset). */
        public String face;

        public Spot() {}

        public Spot(String world, int x, int y, int z, String entity, Method method, int piece) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entity = entity;
            this.method = method.id();
            this.piece = piece;
        }

        public Method method() {
            Method m = Method.of(method);
            return m == null ? Method.INSIDE : m;
        }

        public boolean isAt(String world, int x, int y, int z) {
            return entity == null && this.world.equals(world) && this.x == x && this.y == y && this.z == z;
        }

        public boolean isEntity(String uuid) {
            return uuid.equals(entity);
        }
    }

    /** A player found (a piece of) the note for the first time. */
    public record Find(String player, long at, int piece, String method) {}

    public String id;
    public String title = "";
    public String text = "";
    public String look = "paper";
    public String readers = ALL;
    public List<String> players = new ArrayList<>();
    /** Taken by the first finder (it goes into their inventory), else every allowed player reads it in place. */
    public boolean once;
    /** Invisible ink: blank unless read with the UV lamp in hand. */
    public boolean ink;
    /** Torn in that many pieces, hidden apart (1 = whole). */
    public int pieces = 1;
    public List<Spot> spots = new ArrayList<>();
    public List<Find> finds = new ArrayList<>();
    public long createdAt;

    public Note() {}

    public Note(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** After loading a hand-edited file: no nulls, values in range. */
    void normalize() {
        if (title == null) title = "";
        if (text == null) text = "";
        if (look == null || !LOOKS.contains(look)) look = "paper";
        if (readers == null || !READERS.contains(readers)) readers = ALL;
        if (players == null) players = new ArrayList<>();
        if (spots == null) spots = new ArrayList<>();
        spots.removeIf(s -> s == null || s.world == null);
        if (finds == null) finds = new ArrayList<>();
        finds.removeIf(f -> f == null || f.player() == null);
        pieces = Math.max(1, Math.min(MAX_PIECES, pieces));
    }

    /**
     * May this player read it? {@code inCast}: whether the player is in the active season's cast. Organisers
     * always may (checked by the caller).
     */
    public boolean readableBy(String player, boolean inCast) {
        return switch (readers) {
            case CAST -> inCast;
            case PLAYERS -> players.stream().anyMatch(p -> p.equalsIgnoreCase(player));
            default -> true;
        };
    }

    /** Pieces this player found (0-based; {0} for a whole note found). */
    public List<Integer> piecesFoundBy(String player) {
        List<Integer> out = new ArrayList<>();
        for (Find f : finds) if (f.player().equalsIgnoreCase(player) && !out.contains(f.piece())) out.add(f.piece());
        out.sort(Integer::compare);
        return out;
    }

    public boolean foundBy(String player, int piece) {
        for (Find f : finds) if (f.player().equalsIgnoreCase(player) && f.piece() == piece) return true;
        return false;
    }

    /** Next piece without a hiding place (the organiser hides them in turn), or the last one. */
    public int nextPieceToHide() {
        for (int p = 0; p < pieces; p++) {
            final int piece = p;
            if (spots.stream().noneMatch(s -> s.piece == piece)) return p;
        }
        return pieces - 1;
    }

    /** "Indice n°1 !" → "indice-n-1". */
    public static String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return n.isEmpty() ? "note" : n.length() > 32 ? n.substring(0, 32).replaceAll("-+$", "") : n;
    }

    /**
     * The text cut in {@code n} pieces of about the same length, at word boundaries (line breaks kept): what each
     * torn piece of paper shows.
     */
    public static List<String> split(String text, int n) {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text.strip();
        if (n <= 1) {
            out.add(t);
            return out;
        }
        // Words with the separator that follows them, so the pieces put back together give the text again
        List<String> words = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\S+\\s*").matcher(t);
        while (m.find()) words.add(m.group());
        int left = t.length();
        int at = 0;
        for (int p = 0; p < n; p++) {
            int piecesLeft = n - p;
            int share = (int) Math.ceil(left / (double) piecesLeft);
            StringBuilder b = new StringBuilder();
            // Takes words up to an even share of what is left, keeping at least one word for each piece after it
            while (at < words.size() && (p == n - 1 || words.size() - at > piecesLeft - 1)
                    && (b.isEmpty() || p == n - 1 || b.length() + words.get(at).length() / 2 <= share))
                b.append(words.get(at++));
            left -= b.length();
            out.add(b.toString());
        }
        for (int i = 0; i < out.size(); i++) out.set(i, out.get(i).strip());
        return out;
    }
}
