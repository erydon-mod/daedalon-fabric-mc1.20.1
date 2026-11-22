package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/** Cached, material-independent occupied cells for the centred and four alcove positions. */
public final class ObeliskPartsGeometry {
    public static final int RADIUS = 1;
    public static final int HEIGHT = 5;
    private static final VoxelShape[][] COLLISION = new VoxelShape[5][45];
    private static final List<List<BlockPos>> OCCUPIED;
    private static final List<BlockPos> OFFSETS;

    static {
        List<BlockPos> offsets = new ArrayList<>(45);
        for (int y = 0; y < HEIGHT; y++) for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            offsets.add(new BlockPos(x, y, z));
        }
        OFFSETS = List.copyOf(offsets);
        List<List<BlockPos>> occupied = new ArrayList<>(5);
        for (int frame = 0; frame < 5; frame++) {
            VoxelShape whole = SizedDecorBlock.fullShape(SizedDecorBlock.Size.LARGE);
            if (frame != 0) {
                Direction facing = switch (frame) {
                    case 1 -> Direction.NORTH; case 2 -> Direction.EAST;
                    case 3 -> Direction.SOUTH; case 4 -> Direction.WEST;
                    default -> throw new IllegalStateException();
                };
                whole = whole.offset(facing.getOffsetX() * (double) DecorShapeTransforms.OFFSET_DISTANCE, 0,
                        facing.getOffsetZ() * (double) DecorShapeTransforms.OFFSET_DISTANCE);
            }
            List<BlockPos> cells = new ArrayList<>();
            for (BlockPos offset : OFFSETS) {
                int x = offset.getX(), y = offset.getY(), z = offset.getZ();
                double minX = Math.max(x, whole.getMin(Direction.Axis.X)) - x;
                double maxX = Math.min(x + 1, whole.getMax(Direction.Axis.X)) - x;
                double minZ = Math.max(z, whole.getMin(Direction.Axis.Z)) - z;
                double maxZ = Math.min(z + 1, whole.getMax(Direction.Axis.Z)) - z;
                boolean intersects = maxX > minX && maxZ > minZ;
                COLLISION[frame][index(x, y, z)] = intersects
                        ? VoxelShapes.cuboid(minX, 0, minZ, maxX, 1, maxZ) : VoxelShapes.empty();
                if (intersects) cells.add(offset);
            }
            occupied.add(List.copyOf(cells));
        }
        OCCUPIED = List.copyOf(occupied);
    }

    private ObeliskPartsGeometry() { }
    /** The bounded union of every possible footprint, used for owned-cell cleanup. */
    public static List<BlockPos> offsets() { return OFFSETS; }
    public static List<BlockPos> offsets(BlockState owner) { return OCCUPIED.get(frame(owner)); }
    public static boolean contains(BlockState owner, BlockPos offset) {
        return !collision(owner, offset.getX(), offset.getY(), offset.getZ()).isEmpty();
    }
    public static VoxelShape collision(int x, int y, int z) {
        return valid(x, y, z) ? COLLISION[0][index(x, y, z)] : VoxelShapes.empty();
    }
    public static VoxelShape collision(BlockState owner, int x, int y, int z) {
        return valid(x, y, z) ? COLLISION[frame(owner)][index(x, y, z)] : VoxelShapes.empty();
    }
    public static VoxelShape outline(int x, int y, int z) {
        return collision(x, y, z);
    }
    public static boolean allowsWater(int x, int y, int z) { return valid(x, y, z) && (x != 0 || z != 0); }
    public static boolean allowsWater(BlockState owner, int x, int y, int z) {
        VoxelShape shape = collision(owner, x, y, z);
        if (shape.isEmpty()) return false;
        var box = shape.getBoundingBox();
        return box.getXLength() < 1 || box.getZLength() < 1;
    }
    private static int frame(BlockState owner) {
        return owner.get(SizedDecorBlock.OFFSET) ? 1 + DecorShapeTransforms.horizontalIndex(owner.get(SizedDecorBlock.FACING)) : 0;
    }
    private static int index(int x, int y, int z) { return (y * 3 + z + 1) * 3 + x + 1; }
    private static boolean valid(int x, int y, int z) { return x >= -1 && x <= 1 && z >= -1 && z <= 1 && y >= 0 && y < HEIGHT; }
}
