package com.oliver.daedalon.block;

import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

final class FountainAssemblyLayoutTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void waterChangesReuseCollisionAndPlacementExactly() {
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                FountainAssemblyLayout dry = FountainAssemblyLayout.create(style, size, false, null, 0);
                FountainAssemblyLayout wet = dry.withWaterlogged(true);
                FountainAssemblyLayout dryAgain = wet.withWaterlogged(false);
                assertSame(dry.bowls(), wet.bowls());
                assertEquals(dry.maximumY(), wet.maximumY(), EPSILON);
                for (int y = 0; y <= FountainBasinGeometry.MAX_PART_Y; y++) {
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            assertSame(dry.collisionPartShape(x, y, z), wet.collisionPartShape(x, y, z));
                            assertSame(dry.collisionPartShape(x, y, z), dryAgain.collisionPartShape(x, y, z));
                        }
                    }
                }
            }
        }
    }

    @Test
    void plinthsUseHalfOneAndTwoMetreConnections() {
        assertEquals(0.5D, PlinthBlock.Size.SMALL.height(), EPSILON);
        assertEquals(1.0D, PlinthBlock.Size.MEDIUM.height(), EPSILON);
        assertEquals(2.0D, PlinthBlock.Size.LARGE.height(), EPSILON);
    }

    @Test
    void bowlsUseRequestedDiametersAndConnectInsideTheRim() {
        assertEquals(1.0D, FountainBowlModel.Size.SMALL.diameter(), EPSILON);
        assertEquals(1.5D, FountainBowlModel.Size.MEDIUM.diameter(), EPSILON);
        assertEquals(2.0D, FountainBowlModel.Size.LARGE.diameter(), EPSILON);
        for (FountainBowlModel.Style style : FountainBowlModel.Style.values()) {
            for (FountainBowlModel.Size size : FountainBowlModel.Size.values()) {
                assertTrue(style.connectionRise(size) <= style.waterFloorY(size));
                assertTrue(style.waterFloorY(size) < style.waterSurfaceY(size));
                assertTrue(style.connectionRise(size) < style.waterSurfaceY(size));
                assertTrue(style.waterSurfaceY(size) < style.modelHeight(size));
            }
        }
        assertEquals(0.643D, FountainBowlModel.Style.GOTHIC.connectionRise(
                FountainBowlModel.Size.SMALL
        ), EPSILON);
        assertEquals(0.410D, FountainBowlModel.Style.GEORGIAN.connectionRise(
                FountainBowlModel.Size.SMALL
        ), EPSILON);
        assertEquals(0.500D, FountainBowlModel.Style.GEORGIAN.waterFloorY(
                FountainBowlModel.Size.SMALL
        ), EPSILON);
        assertEquals(0.550D, FountainBowlModel.Style.GREEK.connectionRise(
                FountainBowlModel.Size.SMALL
        ), EPSILON);
    }

    @Test
    void tallestFixedStackFitsTheBoundedInteractionGrid() {
        for (FountainBasinBlock.Style basinStyle : FountainBasinBlock.Style.values()) {
            FountainBowlModel.Style bowlStyle = basinStyle.bowlStyle();
            double top = basinStyle.basinFloorY(FountainBasinBlock.Size.LARGE)
                    + PlinthBlock.Size.LARGE.height();
            for (FountainBowlModel.Size size
                    : FountainBowlModel.sequence(FountainBowlModel.TierCount.THREE)) {
                double base = top;
                top = base + bowlStyle.connectionRise(size);
                assertTrue(
                        base + bowlStyle.modelHeight(size)
                                <= FountainBasinGeometry.MAX_PART_Y + 1.0D,
                        basinStyle.name()
                );
            }
        }
        assertEquals(5, FountainBasinGeometry.MAX_PART_Y);
    }

    @Test
    void tierCountOwnsTheFixedDescendingBowlSequence() {
        assertEquals(
                List.of(),
                FountainBowlModel.sequence(FountainBowlModel.TierCount.NONE)
        );
        assertEquals(
                List.of(FountainBowlModel.Size.SMALL),
                FountainBowlModel.sequence(FountainBowlModel.TierCount.ONE)
        );
        assertEquals(
                List.of(FountainBowlModel.Size.MEDIUM, FountainBowlModel.Size.SMALL),
                FountainBowlModel.sequence(FountainBowlModel.TierCount.TWO)
        );
        assertEquals(
                List.of(
                        FountainBowlModel.Size.LARGE,
                        FountainBowlModel.Size.MEDIUM,
                        FountainBowlModel.Size.SMALL
                ),
                FountainBowlModel.sequence(FountainBowlModel.TierCount.THREE)
        );
        assertEquals(3, FountainAssemblyLayout.maximumBowlCount());
    }

    @Test
    void everyPlinthSizeOffersEveryTierCount() {
        assertEquals(
                List.of(FountainBowlModel.TierCount.values()),
                FountainBowlModel.TierCount.allowed()
        );
        for (FountainBowlModel.TierCount tierCount : FountainBowlModel.TierCount.values()) {
            assertEquals(
                    tierCount,
                    FountainBowlModel.TierCount.fromBowlCount(tierCount.bowlCount())
            );
        }
    }

    @Test
    void fourPieceStackKeepsEverySelectionCellToOneBox() {
        VoxelShape[] parts = new VoxelShape[FountainBasinGeometry.PART_SHAPE_COUNT];
        Arrays.fill(parts, VoxelShapes.empty());
        List<VoxelShape> pieces = List.of(
                VoxelShapes.cuboid(0.1D, 0.25D, 0.1D, 0.9D, 1.25D, 0.9D),
                VoxelShapes.cuboid(-0.5D, 1.25D, -0.5D, 1.5D, 2.0D, 1.5D),
                VoxelShapes.cuboid(0.0D, 2.0D, 0.0D, 1.0D, 2.75D, 1.0D),
                VoxelShapes.cuboid(0.25D, 2.75D, 0.25D, 0.75D, 3.5D, 0.75D)
        );
        for (VoxelShape piece : pieces) {
            FountainAssemblyLayout.mergeIntoParts(parts, piece, true);
        }

        int occupiedParts = 0;
        for (VoxelShape part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            occupiedParts++;
            int[] boxes = {0};
            part.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> boxes[0]++);
            assertEquals(1, boxes[0]);
        }
        assertTrue(occupiedParts >= pieces.size());
    }
}
