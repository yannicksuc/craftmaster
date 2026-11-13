package fr.lordfinn.craftmaster.camera;

import java.util.Locale;

/**
 * Pure logic of how a studio camera is mounted, mirroring the studio lights (see {@code studio.FurnitureBlock} and
 * client FurnitureRenderer): on the floor on its own tripod legs, on top of a tripod stand or a rig rod, hung
 * upside-down under a ceiling (on a short drop pole) or a rod, on a wall (on a small arm, like the studio light's
 * bracket) or on a rod's side (on the same arm, reaching the rod's).
 *
 * <p>Sides are indexed like {@code Direction.ordinal()} (0 down, 1 up, 2 north, 3 south, 4 west, 5 east), as in
 * {@code studio.RigLinks}. {@link #side()} is the horizontal direction from the camera toward its wall or rod
 * ({@link Kind#WALL}, {@link Kind#ROD_SIDE}), -1 otherwise.
 *
 * <p>The camera entity stands at the mount's <em>anchor</em> ({@link #anchor}): the feet on the floor, else the
 * point where the camera's stem meets its support (the top of the stand / rod, the end of the drop pole, the arm's
 * top). The lens is {@link #lensDy()} above (below when hung) that anchor: the broadcast shot is taken from there.
 */
public record CameraMount(Kind kind, int side) {

    public enum Kind {
        /** On the floor, on its own tripod (the only one with legs; every camera saved before the mounts). */
        FLOOR("floor", "au sol"),
        /** On top of a tripod stand block. */
        TRIPOD("tripod", "sur un pied de trépied"),
        /** On top of a rig rod. */
        ROD_TOP("rod_top", "sur une barre"),
        /** Hung upside-down under a ceiling, on a short drop pole. */
        CEILING("ceiling", "suspendue au plafond"),
        /** Hung upside-down under a rig rod. */
        ROD_UNDER("rod_under", "suspendue sous une barre"),
        /** On a small arm fixed to a wall. */
        WALL("wall", "sur un bras au mur"),
        /** Standing on the arm of a rig rod's side. */
        ROD_SIDE("rod_side", "sur le côté d'une barre");

        final String id, label;

        Kind(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** What the camera item was used on. */
    public enum Support { BLOCK, TRIPOD, ROD }

    public static final CameraMount FLOOR = new CameraMount(Kind.FLOOR, -1);

    public static final int DOWN = 0, UP = 1;
    private static final String[] SIDE_NAMES = {"down", "up", "north", "south", "west", "east"};
    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1}, STEP_Z = {0, 0, -1, 1, 0, 0};

    /** Floor tripod: from the feet to the lens (roughly a standing player's eyes). */
    public static final double LEGS_LENS = 1.5;
    /** Mounted: the pan head's stem between the support and the body, then from the body's bottom to the lens. */
    public static final double STEM = 3 / 16.0, BODY_TO_LENS = 3.5 / 16.0, MOUNTED_LENS = STEM + BODY_TO_LENS;
    /** Under a ceiling: the drop pole (the rod's arm, its plate on the ceiling) the camera hangs from. */
    public static final double DROP = 0.5;
    /**
     * Like the studio light on a wall: it stands on its bracket's tube (top at 9 px) in the middle of its block. On a
     * rod's side the same bracket reaches the rod's own arm (a tube from the rod to the middle of the camera's block).
     */
    public static final double WALL_ARM_TOP = 9 / 16.0;

    public CameraMount {
        if (kind == null) kind = Kind.FLOOR;
        boolean sided = kind == Kind.WALL || kind == Kind.ROD_SIDE;
        if (!sided) side = -1;
        else if (side < 2 || side > 5) throw new IllegalArgumentException("a wall / rod side mount needs a horizontal side");
    }

    /**
     * The mount for a camera item used on face {@code face} of a block, or null if a camera cannot go there.
     *
     * @param support what the block is: a sturdy face of an ordinary block (or any top face), a tripod stand, a rod
     *                (null: an ordinary block whose clicked face is not sturdy)
     */
    public static CameraMount choose(int face, Support support) {
        if (face == UP) {
            if (support == Support.TRIPOD) return new CameraMount(Kind.TRIPOD, -1);
            if (support == Support.ROD) return new CameraMount(Kind.ROD_TOP, -1);
            return FLOOR; // any top face: as before the mounts
        }
        if (support == null || support == Support.TRIPOD) return null; // the tripod's legs hold nothing
        if (face == DOWN) return new CameraMount(support == Support.ROD ? Kind.ROD_UNDER : Kind.CEILING, -1);
        if (face < 2 || face > 5) return null;
        // The clicked face points from the support toward the camera: the camera's side toward it is the opposite
        return new CameraMount(support == Support.ROD ? Kind.ROD_SIDE : Kind.WALL, face ^ 1);
    }

    public boolean legs() {
        return kind == Kind.FLOOR;
    }

    /** Upside-down under its support. */
    public boolean hung() {
        return kind == Kind.CEILING || kind == Kind.ROD_UNDER;
    }

    public boolean onRod() {
        return kind == Kind.ROD_TOP || kind == Kind.ROD_UNDER || kind == Kind.ROD_SIDE;
    }

    /** From the anchor (the entity's position) up to the lens (blocks; negative when hung). */
    public double lensDy() {
        if (legs()) return LEGS_LENS;
        return hung() ? -MOUNTED_LENS : MOUNTED_LENS;
    }

    public int stepX() {
        return side < 0 ? 0 : STEP_X[side];
    }

    public int stepZ() {
        return side < 0 ? 0 : STEP_Z[side];
    }

    /**
     * Where the camera entity stands for an item used on block {@code (bx, by, bz)} at {@code (cx, cy, cz)} (the
     * click on its face): {x, y, z}.
     */
    public double[] anchor(int bx, int by, int bz, double cx, double cy, double cz) {
        return switch (kind) {
            case FLOOR -> new double[]{cx, cy, cz};
            case TRIPOD, ROD_TOP -> new double[]{bx + 0.5, by + 1, bz + 0.5};
            case CEILING -> new double[]{cx, by - DROP, cz};
            case ROD_UNDER -> new double[]{bx + 0.5, by, bz + 0.5};
            case WALL, ROD_SIDE -> {
                // In the block in front of the wall / rod, in its middle
                int fx = bx - stepX(), fz = bz - stepZ();
                yield new double[]{fx + 0.5, by + WALL_ARM_TOP, fz + 0.5};
            }
        };
    }

    /**
     * The block the camera is fixed to (the stand, the rod, the ceiling, the wall), from its anchor: {x, y, z}; null on
     * the floor.
     */
    public int[] supportBlock(double ax, double ay, double az) {
        int x = (int) Math.floor(ax), z = (int) Math.floor(az);
        return switch (kind) {
            case FLOOR -> null;
            case TRIPOD, ROD_TOP -> new int[]{x, (int) Math.floor(ay + 0.5) - 1, z};
            case CEILING -> new int[]{x, (int) Math.floor(ay + DROP + 0.5), z};
            case ROD_UNDER -> new int[]{x, (int) Math.floor(ay + 0.5), z};
            case WALL, ROD_SIDE -> new int[]{x + stepX(), (int) Math.floor(ay), z + stepZ()};
        };
    }

    /** "floor", "rod_top", "wall:north" (saved in cameras.json and with the entity). */
    public String id() {
        return side < 0 ? kind.id : kind.id + ":" + SIDE_NAMES[side];
    }

    /** "" or unknown (cameras saved before the mounts) → {@link #FLOOR}. */
    public static CameraMount parse(String id) {
        if (id == null || id.isBlank()) return FLOOR;
        String s = id.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String k = colon < 0 ? s : s.substring(0, colon);
        for (Kind kind : Kind.values()) {
            if (!kind.id.equals(k)) continue;
            if (kind != Kind.WALL && kind != Kind.ROD_SIDE) return new CameraMount(kind, -1);
            if (colon < 0) return FLOOR;
            String side = s.substring(colon + 1);
            for (int i = 2; i < 6; i++) if (SIDE_NAMES[i].equals(side)) return new CameraMount(kind, i);
            return FLOOR;
        }
        return FLOOR;
    }

    /** "au sol", "suspendue sous une barre" (player-facing). */
    public String label() {
        return kind.label;
    }
}
