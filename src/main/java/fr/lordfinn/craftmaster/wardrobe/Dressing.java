package fr.lordfinn.craftmaster.wardrobe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * What changing clothes at a wardrobe does to the four worn pieces (head, body, legs, feet), without Minecraft:
 * the server applies the result to the player, the tests check it with plain values.
 *
 * <p>Two kinds of wardrobe:
 * <ul>
 *   <li><b>show</b> (ready outfits, endless): putting on an outfit, or a single piece of one (to mix outfits), gives
 *   marked copies; what the player wore in that place is kept aside ({@code saved}, slot by slot) the first time;
 *   another copy simply replaces a copy; "take back" removes the copies and gives the kept clothes back;</li>
 *   <li><b>storage</b> (survival): the outfit's real pieces and the worn ones swap places (all four, or one).</li>
 * </ul>
 * An empty piece in an outfit put on whole means "nothing there": an outfit is the whole look.
 */
public final class Dressing {

    private Dressing() {}

    public static final int PIECES = 4;
    public static final boolean[] ALL = {true, true, true, true};

    /** How the logic sees an item. */
    public interface Kind<T> {
        T none();

        boolean empty(T item);

        /** A copy given by a show wardrobe (it goes back to the wardrobe, it is never kept). */
        boolean costume(T item);

        /** The marked copy of a piece of a ready outfit. */
        T costumeCopy(T item);
    }

    /**
     * @param worn        the four pieces to wear now
     * @param saved       the player's own clothes kept aside, slot by slot ({@code none} = nothing kept there),
     *                    {@code null} = not in a show costume
     * @param toInventory the player's own things that go back to the inventory
     * @param outfit      storage: what the outfit holds now; show: {@code null}
     */
    public record Result<T>(List<T> worn, List<T> saved, List<T> toInventory, List<T> outfit) {}

    /** The mask of a single piece (0 head, 1 body, 2 legs, 3 feet). */
    public static boolean[] only(int piece) {
        boolean[] m = new boolean[PIECES];
        m[piece] = true;
        return m;
    }

    /** Show wardrobe: wear copies of the pieces of {@code outfit} picked by {@code mask}. */
    public static <T> Result<T> putOnCopy(Kind<T> k, List<T> worn, List<T> saved, List<T> outfit, boolean[] mask) {
        check(worn);
        check(outfit);
        List<T> newWorn = new ArrayList<>(worn);
        List<T> newSaved = saved == null ? nothing(k) : new ArrayList<>(saved);
        List<T> inv = new ArrayList<>();
        for (int i = 0; i < PIECES; i++) {
            if (!mask[i]) continue;
            T cur = worn.get(i);
            if (!k.costume(cur) && !k.empty(cur)) {
                // The player's own piece: kept aside, unless something is kept there already (then it is theirs too)
                if (k.empty(newSaved.get(i))) newSaved.set(i, cur);
                else inv.add(cur);
            }
            T piece = outfit.get(i);
            newWorn.set(i, k.empty(piece) ? k.none() : k.costumeCopy(piece));
        }
        return new Result<>(newWorn, newSaved, inv, null);
    }

    /** {@link #putOnCopy(Kind, List, List, List, boolean[])} of the whole outfit. */
    public static <T> Result<T> putOnCopy(Kind<T> k, List<T> worn, List<T> saved, List<T> outfit) {
        return putOnCopy(k, worn, saved, outfit, ALL);
    }

    /**
     * Show wardrobe: the copies go, the clothes kept aside come back. A piece of their own the player put on during
     * the costume stays worn where nothing was kept aside, and goes to the inventory otherwise.
     * {@code null} if not in a costume.
     */
    public static <T> Result<T> takeBack(Kind<T> k, List<T> worn, List<T> saved) {
        check(worn);
        if (saved == null) return null;
        check(saved);
        List<T> newWorn = new ArrayList<>();
        List<T> inv = new ArrayList<>();
        for (int i = 0; i < PIECES; i++) {
            T cur = worn.get(i), kept = saved.get(i);
            boolean own = !k.empty(cur) && !k.costume(cur);
            if (!k.empty(kept)) {
                newWorn.add(kept);
                if (own) inv.add(cur);
            } else {
                newWorn.add(own ? cur : k.none());
            }
        }
        return new Result<>(newWorn, null, inv, null);
    }

    /**
     * Takes everything off (to start a new outfit from nothing): costume copies go, the player's own pieces are kept
     * aside with the rest (or go to the inventory if something is kept aside there already). "Mes affaires" then
     * gives everything back. {@code saved} is {@code null} if nothing is kept aside.
     */
    public static <T> Result<T> stripAll(Kind<T> k, List<T> worn, List<T> saved) {
        check(worn);
        List<T> newSaved = saved == null ? nothing(k) : new ArrayList<>(saved);
        List<T> inv = new ArrayList<>();
        for (int i = 0; i < PIECES; i++) {
            T cur = worn.get(i);
            if (k.empty(cur) || k.costume(cur)) continue;
            if (k.empty(newSaved.get(i))) newSaved.set(i, cur);
            else inv.add(cur);
        }
        return new Result<>(nothing(k), allEmpty(k, newSaved) ? null : newSaved, inv, null);
    }

    /**
     * Takes one piece off: a costume copy gives back what was kept aside in that place (or nothing), a piece of the
     * player's own goes to the inventory. {@code saved} of the result is {@code null} once no costume copy is worn
     * and nothing is kept aside any more (the costume is over).
     */
    public static <T> Result<T> takeOff(Kind<T> k, List<T> worn, List<T> saved, int piece) {
        check(worn);
        List<T> newWorn = new ArrayList<>(worn);
        List<T> newSaved = saved == null ? null : new ArrayList<>(saved);
        List<T> inv = new ArrayList<>();
        T cur = worn.get(piece);
        if (k.costume(cur)) {
            newWorn.set(piece, newSaved == null ? k.none() : newSaved.get(piece));
            if (newSaved != null) newSaved.set(piece, k.none());
        } else if (!k.empty(cur)) {
            inv.add(cur);
            newWorn.set(piece, k.none());
        }
        if (newSaved != null && allEmpty(k, newSaved) && newWorn.stream().noneMatch(k::costume)) newSaved = null;
        return new Result<>(newWorn, newSaved, inv, null);
    }

    /**
     * Storage wardrobe: the outfit's pieces picked by {@code mask} are worn and what was worn goes in their place.
     * Costume copies of a show wardrobe are not stored (they disappear).
     */
    public static <T> Result<T> swap(Kind<T> k, List<T> worn, List<T> outfit, boolean[] mask) {
        check(worn);
        check(outfit);
        List<T> newWorn = new ArrayList<>(worn);
        List<T> stored = new ArrayList<>(outfit);
        for (int i = 0; i < PIECES; i++) {
            if (!mask[i]) continue;
            T cur = worn.get(i);
            stored.set(i, k.costume(cur) ? k.none() : cur);
            newWorn.set(i, outfit.get(i));
        }
        return new Result<>(newWorn, null, List.of(), stored);
    }

    public static <T> Result<T> swap(Kind<T> k, List<T> worn, List<T> outfit) {
        return swap(k, worn, outfit, ALL);
    }

    /** Storage wardrobe: put what is worn away in a new outfit (the player is left without those pieces). */
    public static <T> Result<T> store(Kind<T> k, List<T> worn) {
        return swap(k, worn, nothing(k));
    }

    /** A show copy of what is worn, unmarked (an organiser making a ready outfit out of what they wear). */
    public static <T> List<T> template(Kind<T> k, List<T> worn, UnaryOperator<T> unmark) {
        check(worn);
        List<T> out = new ArrayList<>();
        for (T t : worn) out.add(k.empty(t) ? k.none() : unmark.apply(t));
        return out;
    }

    public static <T> boolean allEmpty(Kind<T> k, List<T> pieces) {
        for (T t : pieces) if (!k.empty(t)) return false;
        return true;
    }

    public static <T> List<T> nothing(Kind<T> k) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < PIECES; i++) out.add(k.none());
        return out;
    }

    private static void check(List<?> pieces) {
        if (pieces.size() != PIECES) throw new IllegalArgumentException("4 pieces expected, got " + pieces.size());
    }
}
