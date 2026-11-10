package fr.lordfinn.craftmaster.studio;

/**
 * Pure logic of the rig rods ({@link RigRodBlock}): which of its 6 sides a rod links to, and what is drawn. A set of
 * sides is a 6-bit mask indexed like {@code Direction.ordinal()}: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east
 * (the opposite side of {@code i} is {@code i ^ 1}, its axis {@code i >> 1}: 0 = Y, 1 = Z, 2 = X).
 */
public final class RigLinks {

    private RigLinks() {}

    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    public static final int ALL = 0b111111;

    public static int bit(int side) {
        return 1 << side;
    }

    public static boolean has(int mask, int side) {
        return (mask & bit(side)) != 0;
    }

    /** The axes (bit 0 = Y, 1 = Z, 2 = X) a set of sides uses. */
    public static int axes(int mask) {
        int a = 0;
        for (int i = 0; i < 6; i++) if (has(mask, i)) a |= 1 << (i >> 1);
        return a;
    }

    /**
     * The sides a rod links to when it is placed.
     *
     * @param rods    sides with something it always links to (another rod that would link back, a tripod's top
     *                below it, a light hung on this block)
     * @param solids  sides with a sturdy face of an ordinary block (a wall, the floor, a ceiling)
     * @param clicked the side of the block it was placed against, or -1
     * @param sneak   placed while sneaking: it only links to the block it was placed against
     */
    public static int placement(int rods, int solids, int clicked, boolean sneak) {
        if (sneak) return clicked >= 0 && has(rods | solids, clicked) ? bit(clicked) : 0;
        int links = rods;
        // Placed against a wall or a ceiling: it is mounted on it (a plate)
        if (clicked >= 0 && has(solids, clicked)) links |= bit(clicked);
        // The end of a beam that touches a wall on the other side is mounted on it too
        for (int i = 0; i < 6; i++)
            if (has(links, i) && !has(links, i ^ 1) && has(solids, i ^ 1)) links |= bit(i ^ 1);
        return links;
    }

    /**
     * The arms drawn (and in the outline) for a set of links: the linked sides, except that a rod using a single
     * axis (or none: a lone rod stands upright, like LordFinn's original rod) goes right through its block.
     */
    public static int arms(int links) {
        int axes = axes(links);
        if (axes == 0) return bit(DOWN) | bit(UP);
        if (Integer.bitCount(axes) == 1) {
            int axis = Integer.numberOfTrailingZeros(axes);
            return bit(axis * 2) | bit(axis * 2 + 1);
        }
        return links;
    }

    /** A clamp (the knuckle) holds the arms together where rods meet at an angle (2 or 3 axes). */
    public static boolean knuckle(int links) {
        return Integer.bitCount(axes(links)) >= 2;
    }

    /** Turns a set of sides by a quarter turn clockwise seen from above (north to east), {@code times} times. */
    public static int rotateY(int mask, int times) {
        int[] cw = {DOWN, UP, EAST, WEST, NORTH, SOUTH}; // where each side goes: N->E, S->W, W->N, E->S
        int out = mask;
        for (int t = 0; t < Math.floorMod(times, 4); t++) {
            int next = 0;
            for (int i = 0; i < 6; i++) if (has(out, i)) next |= bit(cw[i]);
            out = next;
        }
        return out;
    }

    /** Mirrors a set of sides across the X axis (north and south swap) or the Z axis (west and east swap). */
    public static int mirror(int mask, boolean swapNorthSouth) {
        int a = swapNorthSouth ? NORTH : WEST, b = a + 1;
        int out = mask & ~(bit(a) | bit(b));
        if (has(mask, a)) out |= bit(b);
        if (has(mask, b)) out |= bit(a);
        return out;
    }
}
