package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TallDecorPlacementTest {
    @Test
    void riseUsesTheFirstWholeCellAboveTheOutline() {
        assertEquals(1, TallDecorPlacement.placementRise(1.0D));
        assertEquals(1, TallDecorPlacement.placementRise(1.00000001D));
        assertEquals(2, TallDecorPlacement.placementRise(1.5D));
        assertEquals(2, TallDecorPlacement.placementRise(2.0D));
        assertEquals(4, TallDecorPlacement.placementRise(3.02D));
    }

    @Test
    void horizontalHitBoundsAcceptEdgesButRejectUnrelatedModels() {
        assertTrue(TallDecorPlacement.contains(0.0D, 0.0D, 1.0D));
        assertTrue(TallDecorPlacement.contains(1.00005D, 0.0D, 1.0D));
        assertFalse(TallDecorPlacement.contains(1.01D, 0.0D, 1.0D));
    }

    @Test
    void extendedOutlineHitIsNormalisedOnlyToVanillasPacketLimit() {
        assertEquals(1.0D, TallDecorPlacement.clampForVanillaPacketCheck(1.5D));
        assertEquals(-1.0D, TallDecorPlacement.clampForVanillaPacketCheck(-2.0D));
        assertEquals(0.625D, TallDecorPlacement.clampForVanillaPacketCheck(0.625D));
    }

    @Test
    void outerFountainPartHitIsValidatedInItsOwnWorldCell() {
        BlockPos anchorPos = new BlockPos(10, 64, 20);
        BlockPos partPos = anchorPos.add(2, 0, 0);
        VoxelShape partOutline = FountainBasinGeometry.outlinePartShape(
                FountainBasinBlock.Style.GOTHIC,
                FountainBasinBlock.Size.LARGE,
                false,
                2,
                0,
                0
        );
        Vec3d hit = new Vec3d(
                partPos.getX() + midpoint(partOutline, Direction.Axis.X),
                partPos.getY() + midpoint(partOutline, Direction.Axis.Y),
                partPos.getZ() + midpoint(partOutline, Direction.Axis.Z)
        );

        assertTrue(TallDecorPlacement.containsHit(partOutline, partPos, hit));
        assertFalse(TallDecorPlacement.containsHit(partOutline, anchorPos, hit));
        assertEquals(
                new Vec3d(1.0D, 0.25D, 0.0D),
                TallDecorPlacement.clampForVanillaPacketCheck(new Vec3d(2.25D, 0.25D, 0.0D))
        );
    }

    @Test
    void outerFountainPartRejectsAHitOutsideItsActualShape() {
        BlockPos anchorPos = new BlockPos(10, 64, 20);
        BlockPos partPos = anchorPos.add(2, 0, 0);
        VoxelShape partOutline = FountainBasinGeometry.outlinePartShape(
                FountainBasinBlock.Style.GOTHIC,
                FountainBasinBlock.Size.LARGE,
                false,
                2,
                0,
                0
        );
        Vec3d spoofedHit = new Vec3d(
                partPos.getX() + midpoint(partOutline, Direction.Axis.X),
                partPos.getY() + partOutline.getMax(Direction.Axis.Y) + 0.25D,
                partPos.getZ() + midpoint(partOutline, Direction.Axis.Z)
        );

        assertFalse(TallDecorPlacement.containsHit(partOutline, partPos, spoofedHit));
    }

    private static double midpoint(VoxelShape shape, Direction.Axis axis) {
        return (shape.getMin(axis) + shape.getMax(axis)) * 0.5D;
    }
}
