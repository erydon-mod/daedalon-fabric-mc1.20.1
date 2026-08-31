package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Geometry helpers for decorative blocks whose visible model can move beyond
 * the cell that owns the block state.
 */
public final class DecorShapeTransforms {
    public static final float OIL_BURNER_OFFSET_SCALE = 0.6156F;
    public static final float OFFSET_DISTANCE = 0.8003375F;
    public static final float OFFSET_BASE_Y = 0.0F;

    /*
     * Offset decor still needs an in-cell target for Minecraft's initial
     * outline hit test. The narrow post avoids stealing most neighbouring hits.
     */
    static final VoxelShape TARGET_POST =
            Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    /*
     * A wider owner-cell target is exposed only while holding the debug stick.
     * It makes every offset block state editable from a cardinal direction
     * without changing the normal outline or collision.
     */
    static final VoxelShape DEBUG_EDIT_TARGET = VoxelShapes.union(
            Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0),
            Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0)
    ).simplify();

    private DecorShapeTransforms() {
    }

    static VoxelShape transformFromNorth(
            VoxelShape shape,
            double scale,
            double northOffset,
            double baseY,
            Direction facing
    ) {
        final VoxelShape[] transformed = {VoxelShapes.empty()};
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                transformed[0] = VoxelShapes.union(
                        transformed[0],
                        VoxelShapes.cuboid(
                                0.5 + (minX - 0.5) * scale,
                                minY * scale + baseY,
                                0.5 + (minZ - 0.5) * scale - northOffset,
                                0.5 + (maxX - 0.5) * scale,
                                maxY * scale + baseY,
                                0.5 + (maxZ - 0.5) * scale - northOffset
                        )
                )
        );

        VoxelShape rotated = transformed[0];
        int turns = horizontalIndex(facing);
        for (int turn = 0; turn < turns; turn++) {
            rotated = rotateClockwise(rotated);
        }
        return rotated.simplify();
    }

    static VoxelShape octagonalLayer(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        double cut = Math.min(maxX - minX, maxZ - minZ) / 8.0;
        return VoxelShapes.union(
                Block.createCuboidShape(minX + cut, minY, minZ, maxX - cut, maxY, maxZ),
                Block.createCuboidShape(minX, minY, minZ + cut, maxX, maxY, maxZ - cut)
        );
    }

    static VoxelShape radialLayer(double radius, double minY, double maxY) {
        double min = 8.0 - radius;
        double max = 8.0 + radius;
        return VoxelShapes.union(
                Block.createCuboidShape(
                        min,
                        minY,
                        8.0 - radius * 0.35,
                        max,
                        maxY,
                        8.0 + radius * 0.35
                ),
                Block.createCuboidShape(
                        8.0 - radius * 0.85,
                        minY,
                        8.0 - radius * 0.72,
                        8.0 + radius * 0.85,
                        maxY,
                        8.0 + radius * 0.72
                ),
                Block.createCuboidShape(
                        8.0 - radius * 0.72,
                        minY,
                        min,
                        8.0 + radius * 0.72,
                        maxY,
                        max
                )
        );
    }

    static int horizontalIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException(
                    "Decor facing must be horizontal: " + direction
            );
        };
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        final VoxelShape[] rotated = {VoxelShapes.empty()};
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                rotated[0] = VoxelShapes.union(
                        rotated[0],
                        VoxelShapes.cuboid(
                                1.0 - maxZ,
                                minY,
                                minX,
                                1.0 - minZ,
                                maxY,
                                maxX
                        )
                )
        );
        return rotated[0];
    }
}
