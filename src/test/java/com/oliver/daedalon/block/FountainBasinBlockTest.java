package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FountainBasinBlockTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void sizesAreThreeFourAndFiveBlocksWide() {
        assertEquals(3.0F, FountainBasinBlock.Size.SMALL.modelScale(), EPSILON);
        assertEquals(4.0F, FountainBasinBlock.Size.MEDIUM.modelScale(), EPSILON);
        assertEquals(5.0F, FountainBasinBlock.Size.LARGE.modelScale(), EPSILON);
    }

    @Test
    void collisionUsesASolidFloorAndAHollowStoneRim() {
        VoxelShape collision = FountainBasinGeometry.collisionShape(
                FountainBasinBlock.Size.MEDIUM
        );

        assertTrue(contains(collision, 0.5D, 0.15D, 0.5D));
        assertFalse(contains(collision, 0.5D, 0.60D, 0.5D));
        assertTrue(contains(collision, 2.25D, 0.60D, 0.5D));
        assertTrue(contains(collision, 1.80D, 0.60D, 1.80D));
        assertFalse(contains(collision, 1.30D, 0.60D, 1.30D));
        assertFalse(contains(collision, 2.60D, 0.60D, 0.5D));
    }

    @Test
    void filledOutlineCanPickTheWaterButWaterNeverBecomesCollision() {
        FountainBasinBlock.Size size = FountainBasinBlock.Size.MEDIUM;
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            double waterY = style.waterSurfaceY(size) - 0.005D;
            assertFalse(contains(
                    FountainBasinGeometry.collisionShape(style, size),
                    0.5D, waterY, 0.5D
            ), style.name());
            assertFalse(contains(
                    FountainBasinGeometry.outlineShape(style, size, false),
                    0.5D, waterY, 0.5D
            ), style.name());
            assertTrue(contains(
                    FountainBasinGeometry.outlineShape(style, size, true),
                    0.5D, waterY, 0.5D
            ), style.name());
        }
    }

    @Test
    void clippedPartsCoverEverySizeAtItsOuterCells() {
        assertTrue(FountainBasinGeometry.collisionPartShape(
                FountainBasinBlock.Size.SMALL, 2, 0, 0
        ).isEmpty());
        assertFalse(FountainBasinGeometry.collisionPartShape(
                FountainBasinBlock.Size.MEDIUM, 2, 0, 0
        ).isEmpty());
        assertFalse(FountainBasinGeometry.collisionPartShape(
                FountainBasinBlock.Size.LARGE, 2, 1, 0
        ).isEmpty());
        assertTrue(FountainBasinGeometry.collisionPartShape(
                FountainBasinBlock.Size.LARGE, 0, 1, 0
        ).isEmpty());
        assertFalse(FountainBasinGeometry.outlinePartShape(
                FountainBasinBlock.Size.LARGE, true, 0, 1, 0
        ).isEmpty());
    }

    @Test
    void assembledShapesAreSplitOnceIntoOnlyTheirOccupiedCells() {
        VoxelShape[] parts = FountainBasinGeometry.splitIntoParts(
                VoxelShapes.cuboid(-0.25D, 0.20D, 0.25D, 1.25D, 1.20D, 0.75D)
        );

        assertFalse(parts[FountainBasinGeometry.partIndex(-1, 0, 0)].isEmpty());
        assertFalse(parts[FountainBasinGeometry.partIndex(0, 0, 0)].isEmpty());
        assertFalse(parts[FountainBasinGeometry.partIndex(1, 1, 0)].isEmpty());
        assertTrue(parts[FountainBasinGeometry.partIndex(0, 0, 1)].isEmpty());
        assertTrue(parts[FountainBasinGeometry.partIndex(0, 2, 0)].isEmpty());
    }

    @Test
    void containedWaterDepthUsesTheExactOctagonalInterior() {
        BlockPos anchor = new BlockPos(10, 20, 30);
        FountainBasinBlock.Size size = FountainBasinBlock.Size.MEDIUM;

        assertEquals(
                size.waterSurfaceY() - 0.30D,
                FountainBasinGeometry.containedWaterHeight(
                        anchor,
                        size,
                        new Box(10.3D, 20.3D, 30.3D, 10.7D, 22.0D, 30.7D)
                ),
                EPSILON
        );
        assertEquals(
                0.0D,
                FountainBasinGeometry.containedWaterHeight(
                        anchor,
                        size,
                        new Box(11.7D, 20.3D, 31.7D, 11.8D, 22.0D, 31.8D)
                ),
                EPSILON
        );
        assertEquals(
                0.0D,
                FountainBasinGeometry.containedWaterHeight(
                        anchor,
                        size,
                        new Box(10.3D, 20.0D, 30.3D, 10.7D, 20.29D, 30.7D)
                ),
                EPSILON
        );
    }

    private static boolean contains(VoxelShape shape, double x, double y, double z) {
        boolean[] result = {false};
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (x >= minX - EPSILON && x <= maxX + EPSILON
                    && y >= minY - EPSILON && y <= maxY + EPSILON
                    && z >= minZ - EPSILON && z <= maxZ + EPSILON) {
                result[0] = true;
            }
        });
        return result[0];
    }
}
