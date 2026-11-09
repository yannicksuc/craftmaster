package fr.lordfinn.craftmaster.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Shapes drawn facing north (in pixels), turned for the 4 horizontal directions like the blockstate models. */
final class Shapes4 {
    private Shapes4() {}

    static Map<Direction, VoxelShape> of(double[]... boxes) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = Shapes.empty();
            for (double[] b : boxes) s = Shapes.or(s, turn(b, d));
            out.put(d, s.optimize());
        }
        return out;
    }

    private static VoxelShape turn(double[] b, Direction d) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (d) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
