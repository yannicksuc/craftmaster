package fr.lordfinn.craftmaster.garage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The panel of a garage door: the builder's own blocks, as they are in the closed door, relative to the motor.
 * <p>
 * Found automatically from the block right under the motor: every block of the same kind touching it (left, right,
 * below) in the wall's plane, below the motor. The plane is the one where the panel is the widest (on a tie, the
 * one facing the motor's front). A door of mixed blocks is given as a box instead ({@code /cm plateau garage zone}).
 */
public final class GaragePanel {

    /** Biggest door handled. */
    public static final int MAX_BLOCKS = 1024;
    /** How far from the motor a door can reach, per axis (blocks). */
    public static final int MAX_REACH = 32;

    /** A block of the closed door, relative to the motor. */
    public record Cell(int dx, int dy, int dz, BlockState state) {
        public BlockPos pos(BlockPos motor) {
            return motor.offset(dx, dy, dz);
        }
    }

    private final List<Cell> cells;
    /** Axis the door faces (X or Z): the players are pushed along it. */
    private final Direction.Axis normal;
    private final int bottomDy;
    private final int height;
    private final Map<Long, Integer> columnBottom = new HashMap<>();

    public GaragePanel(List<Cell> cells, Direction.Axis normal) {
        this.cells = List.copyOf(cells);
        this.normal = normal;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Cell c : cells) {
            lo = Math.min(lo, c.dy());
            hi = Math.max(hi, c.dy());
        }
        bottomDy = cells.isEmpty() ? 0 : lo;
        height = cells.isEmpty() ? 0 : hi - lo + 1;
        for (Cell c : cells) columnBottom.merge(column(c), row(c), Math::min);
    }

    public static final GaragePanel EMPTY = new GaragePanel(List.of(), Direction.Axis.Z);

    public List<Cell> cells() {
        return cells;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    public Direction.Axis normal() {
        return normal;
    }

    /** Height of the doorway: from the lowest row of the door to the top of its highest block. */
    public int height() {
        return height;
    }

    /** Lowest row of the door, relative to the motor (negative: below it). */
    public int bottomDy() {
        return bottomDy;
    }

    /** Width of the door along its plane. */
    public int width() {
        if (cells.isEmpty()) return 0;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Cell c : cells) {
            int w = normal == Direction.Axis.X ? c.dz() : c.dx();
            lo = Math.min(lo, w);
            hi = Math.max(hi, w);
        }
        return hi - lo + 1;
    }

    /** The cell's row in the door (0 = lowest). */
    public int row(Cell c) {
        return c.dy() - bottomDy;
    }

    /** Row of the lowest block of the cell's column. */
    public int columnBottom(Cell c) {
        return columnBottom.getOrDefault(column(c), 0);
    }

    private static long column(Cell c) {
        return ((long) c.dx() << 32) ^ (c.dz() & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------- finding it

    /**
     * Finds the panel automatically: the blocks of the same kind as the one right under the motor, touching it in a
     * vertical plane, below the motor. Blocks holding data (chests, signs...) are not part of a door.
     *
     * @param facing the motor's front: on a tie, the door is in the plane facing it
     */
    public static GaragePanel detect(BlockGetter level, BlockPos motor, Direction facing) {
        BlockPos seed = motor.below();
        BlockState first = level.getBlockState(seed);
        if (!movable(level, seed, first)) return EMPTY;
        Block kind = first.getBlock();
        Direction.Axis preferred = facing.getAxis().isHorizontal() ? facing.getAxis() : Direction.Axis.Z;
        Direction.Axis other = preferred == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        List<Cell> a = flood(level, motor, seed, kind, preferred);
        List<Cell> b = flood(level, motor, seed, kind, other);
        return b.size() > a.size() ? new GaragePanel(b, other) : new GaragePanel(a, preferred);
    }

    /**
     * The panel given as a box (relative to the motor): every block in it that can move, whatever its kind.
     * The door faces the box's thinnest horizontal side (on a tie, the motor's front).
     */
    public static GaragePanel inBox(BlockGetter level, BlockPos motor, int[] box, Direction facing) {
        List<Cell> cells = new ArrayList<>();
        int x0 = Math.min(box[0], box[3]), x1 = Math.max(box[0], box[3]);
        int y0 = Math.min(box[1], box[4]), y1 = Math.max(box[1], box[4]);
        int z0 = Math.min(box[2], box[5]), z1 = Math.max(box[2], box[5]);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos p = motor.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (movable(level, p, s) && cells.size() < MAX_BLOCKS) cells.add(new Cell(x, y, z, s));
                }
        int dx = x1 - x0, dz = z1 - z0;
        Direction.Axis normal = dx < dz ? Direction.Axis.X : dz < dx ? Direction.Axis.Z
                : facing.getAxis().isHorizontal() ? facing.getAxis() : Direction.Axis.Z;
        return new GaragePanel(cells, normal);
    }

    /** A block that can be part of a door: not air or a liquid, holding no data, not unbreakable. */
    static boolean movable(BlockGetter level, BlockPos pos, BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && s.getDestroySpeed(level, pos) >= 0;
    }

    private static List<Cell> flood(BlockGetter level, BlockPos motor, BlockPos seed, Block kind, Direction.Axis normal) {
        Direction side = normal == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction[] dirs = {side, side.getOpposite(), Direction.UP, Direction.DOWN};
        Map<BlockPos, BlockState> found = new LinkedHashMap<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        found.put(seed, level.getBlockState(seed));
        todo.add(seed);
        while (!todo.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos p = todo.poll();
            for (Direction d : dirs) {
                BlockPos n = p.relative(d);
                if (found.containsKey(n) || n.getY() >= motor.getY()) continue;
                if (Math.abs(n.getX() - motor.getX()) > MAX_REACH || Math.abs(n.getZ() - motor.getZ()) > MAX_REACH
                        || motor.getY() - n.getY() > MAX_REACH) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() != kind || !movable(level, n, s)) continue;
                found.put(n, s);
                todo.add(n);
            }
        }
        List<Cell> cells = new ArrayList<>(found.size());
        found.forEach((p, s) -> cells.add(new Cell(p.getX() - motor.getX(), p.getY() - motor.getY(), p.getZ() - motor.getZ(), s)));
        return cells;
    }
}
