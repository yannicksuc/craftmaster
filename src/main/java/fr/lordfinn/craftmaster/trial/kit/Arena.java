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

    /**
     * The viewers see the inventory of a contestant playing here ({@link InventoryCam}): true = yes even in the
     * "trials only" mode, false = never, null = as the show's mode says.
     */
    public Boolean inventoryCam;

    /** Game mode of the contestants during the trial: "aventure" (default) or "survie". */
    public String mode;
    /** The common voice channel opens when the trial starts. */
    public boolean vocalLancement;
    /**
     * The trial's « suite » steps, one per press of {@code /cm epreuve suite}: revele (next hidden part of the task),
     * vocal, vocalfin, chrono:&lt;seconds&gt;, panneaux. Empty = one step, revele.
     */
    public List<String> etapes = new ArrayList<>();

    /**
     * What « lancer l'épreuve » loads before anyone goes there: the reference and the copies, widened by this many
     * blocks, their start points, plus {@link #chargementZones}.
     */
    public int chargementMarge = 16;
    /** Other places to load first, in the arena's world: {x0, z0, x1, z1}. */
    public List<int[]> chargementZones = new ArrayList<>();

    /** The drawing field (walked on between the red and the green banner), or null. */
    public Field field;
    /** Items each contestant receives (vanilla item JSON). */
    public List<JsonElement> kit = new ArrayList<>();

    /** Codes the contestants type in this set ({@link Codes}), by name ("poules"). */
    public java.util.Map<String, Code> codes = new java.util.LinkedHashMap<>();

    /**
     * A code: its accepted answers and, per word, its other accepted spellings (« cot » : « cote », « cotte »).
     * Compared by {@link fr.lordfinn.craftmaster.trial.TextMatch}: spaces, case, accents and punctuation don't count.
     */
    public static final class Code {
        public List<String> reponses = new ArrayList<>();
        public java.util.Map<String, List<String>> variantes = new java.util.LinkedHashMap<>();
        /**
         * When not empty, the answer is read as syllables of this alphabet only (syllable → its other spellings),
         * and must have exactly the answer's syllables in the same order: « co », « cot » (« cote », « cotte »), « ri ».
         */
        public java.util.Map<String, List<String>> syllabes = new java.util.LinkedHashMap<>();
    }
    /** Contestant → offset of their copy from the reference. */
    public Map<String, int[]> copies = new LinkedHashMap<>();

    /**
     * Everyone plays on this one ground ({@link CommonGround}): nothing is copied or rebuilt (the copies listed above
     * are kept but ignored), each contestant has a start, the drawing is only recorded.
     */
    public boolean terrainCommun;
    /** On a common ground: the contestants see each other. */
    public boolean joueursVisibles = true;
    /** false: a contestant who is done stays a normal player, with no offer to watch and no F7 until the trial ends. */
    public boolean finSpectateur = true;
    /** On a common ground: starts set by hand, contestant (lower case) → x, y, z. */
    public Map<String, double[]> departs = new LinkedHashMap<>();

    /** The copies in play (none on a common ground). */
    public Map<String, int[]> liveCopies() {
        return terrainCommun ? Map.of() : copies;
    }

    /** The field drawn on: on a common ground, the whole box, blind (nothing ever goes on the floor). */
    public Field drawingField() {
        if (!terrainCommun) return field;
        Field f = new Field();
        f.x0 = x0;
        f.z0 = z0;
        f.x1 = x1;
        f.z1 = z1;
        f.y = y0;
        f.blind = true;
        return f;
    }

    public static final class Field {
        /** Floor of the field in the reference: the blocks walked on (inclusive x/z, one y). */
        public int x0, z0, x1, z1, y;
        /** Nothing shows while they walk: the path is only recorded, and becomes a picture at the end. */
        public boolean blind;
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
