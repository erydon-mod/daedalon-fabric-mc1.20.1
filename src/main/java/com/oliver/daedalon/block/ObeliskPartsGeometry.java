package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/** Forty-five material-independent local cells for the five-block obelisk's existing box profile. */
public final class ObeliskPartsGeometry {
    public static final int RADIUS = 1;
    public static final int HEIGHT = 5;
    private static final VoxelShape[] COLLISION = new VoxelShape[45];
    private static final List<BlockPos> OFFSETS;

    static {
        List<BlockPos> offsets = new ArrayList<>(45);
        VoxelShape whole = SizedDecorBlock.fullShape(SizedDecorBlock.Size.EXTRA_LARGE);
        for (int y = 0; y < HEIGHT; y++) for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            int index = index(x, y, z);
            double minX = Math.max(x, whole.getMin(Direction.Axis.X)) - x;
            double maxX = Math.min(x + 1, whole.getMax(Direction.Axis.X)) - x;
            double minZ = Math.max(z, whole.getMin(Direction.Axis.Z)) - z;
            double maxZ = Math.min(z + 1, whole.getMax(Direction.Axis.Z)) - z;
            COLLISION[index] = VoxelShapes.cuboid(minX, 0, minZ, maxX, 1, maxZ);
            offsets.add(new BlockPos(x, y, z));
        }
        OFFSETS = List.copyOf(offsets);
    }

    private ObeliskPartsGeometry() { }
    public static List<BlockPos> offsets() { return OFFSETS; }
    public static VoxelShape collision(int x, int y, int z) {
        return valid(x, y, z) ? COLLISION[index(x, y, z)] : VoxelShapes.empty();
    }
    public static VoxelShape outline(int x, int y, int z) {
        return collision(x, y, z);
    }
    public static boolean allowsWater(int x, int y, int z) { return valid(x, y, z) && (x != 0 || z != 0); }
    private static int index(int x, int y, int z) { return (y * 3 + z + 1) * 3 + x + 1; }
    private static boolean valid(int x, int y, int z) { return x >= -1 && x <= 1 && z >= -1 && z <= 1 && y >= 0 && y < HEIGHT; }
}
