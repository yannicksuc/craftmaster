package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A trial's set ({@code config/craftmaster/arenas.json}): a reference box built by the organiser, copied side by
 * side once per contestant (each plays alone in their copy: the flowers' path, the drawing field, the village),
 * rebuilt identical from the reference by the reset, with a spawn point (teleport + respawn), the items each
 * contestant receives, and how it follows its round (stopwatch at the letter, timer and locked signs at the hidden
 * part's reveal). Plain data for Gson; the logic is in {@link Arenas}.
 */
public final class Arena {

    public String name;
    /** Dimension id ("minecraft:overworld"). */
    public String world = "minecraft:overworld";
    /** Reference box, inclusive. */
    public int x0, y0, z0, x1, y1, z1;
    /** Spawn point in the reference (copies: shifted with them); absent = the middle of the top. */
    public boolean hasSpawn;
    public double sx, sy, sz;
    public float syaw;
    /** Air between two copies (their barrier walls not counted). */
    public int gap = 3;
    /** Barrier walls and ceiling around each copy: nobody leaves it nor reaches a neighbour's. */
    public boolean walls = true;
    /** The contestants' inventories are emptied before they get the kit. */
    public boolean clearInventory;

    /** Its round (id), or null: what follows the round's letter. */
    public String manche;
    /** Each contestant's stopwatch starts when they open the round's letter. */
    public boolean chronoLettre;
    /** Seconds of the round's timer started when the next hidden part of its task is revealed (0 = none). */
    public int chronoSuite;
    /** The signs of every copy are locked (waxed) at the hidden part's reveal, and their text sent to the organisers. */
    public boolean panneauxSuite;

    /** Standing on the ground outside the box (the starting platform) ends the attempt and locks the inventory. */
    public boolean solFin;
    /** With {@link #solFin}: the score is the longest alphabetical run of the inventory, in the player's language. */
    public boolean alphabet;

    /** The drawing field (walked on between the red and the green banner), or null. */
    public Field field;
    /** Items each contestant receives (vanilla item JSON). */
    public List<JsonElement> kit = new ArrayList<>();
    /** Contestant → offset of their copy from the reference. */
    public Map<String, int[]> copies = new LinkedHashMap<>();

    public static final class Field {
        /** Floor of the field in the reference: the blocks walked on (inclusive x/z, one y). */
        public int x0, z0, x1, z1, y;
        /** Block of the blank field, block of the ink. */
        public String paper = "minecraft:white_concrete", ink = "minecraft:black_concrete";
    }

    public int sizeX() {
        return x1 - x0 + 1;
    }

    public int sizeY() {
        return y1 - y0 + 1;
    }

    public int sizeZ() {
        return z1 - z0 + 1;
    }

    public BlockPos min() {
        return new BlockPos(x0, y0, z0);
    }

    /** The box shifted by {@code off} (null = the reference). */
    public boolean contains(BlockPos p, int[] off) {
        int dx = off == null ? 0 : off[0], dy = off == null ? 0 : off[1], dz = off == null ? 0 : off[2];
        return p.getX() >= x0 + dx && p.getX() <= x1 + dx && p.getY() >= y0 + dy && p.getY() <= y1 + dy && p.getZ() >= z0 + dz && p.getZ() <= z1 + dz;
    }

    public AABB box(int[] off) {
        int dx = off == null ? 0 : off[0], dy = off == null ? 0 : off[1], dz = off == null ? 0 : off[2];
        return new AABB(x0 + dx, y0 + dy, z0 + dz, x1 + dx + 1, y1 + dy + 1, z1 + dz + 1);
    }

    /** Offset of the {@code i}-th copy (1, 2...): side by side towards +X, walls and gap between them. */
    public int[] slot(int i) {
        return new int[]{i * (sizeX() + gap + (walls ? 2 : 0)), 0, 0};
    }
}
