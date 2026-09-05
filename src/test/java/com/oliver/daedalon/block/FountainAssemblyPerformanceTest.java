package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises real assembled shapes, not only enum and source-text assertions. */
final class FountainAssemblyPerformanceTest {
    @Test
    void measuresFullDryAndWetAssemblies() {
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (boolean filled : new boolean[]{false, true}) {
                long start = System.nanoTime();
                VoxelShape[] collision = FountainBasinGeometry.copyCollisionPartShapes(
                        style, FountainBasinBlock.Size.LARGE);
                VoxelShape[] outline = FountainBasinGeometry.copyOutlinePartShapes(
                        style, FountainBasinBlock.Size.LARGE, filled);
                double base = style.basinFloorY(FountainBasinBlock.Size.LARGE);
                VoxelShape plinth = VoxelShapes.cuboid(0.0, base, 0.0, 1.0, base + 2.0, 1.0);
                FountainAssemblyLayout.mergeIntoParts(collision, plinth, false);
                FountainAssemblyLayout.mergeIntoParts(outline, plinth, true);
                base += 2.0;
                for (FountainBowlModel.Size size : FountainBowlModel.sequence(FountainBowlModel.TierCount.THREE)) {
                    FountainAssemblyLayout.mergeIntoParts(collision,
                            FountainBowlModel.collisionShape(style.bowlStyle(), size).offset(0, base, 0), false);
                    FountainAssemblyLayout.mergeIntoParts(outline,
                            FountainBowlModel.outlineShape(style.bowlStyle(), size, filled).offset(0, base, 0), true);
                    base += style.bowlStyle().connectionRise(size);
                }
                System.out.printf("FOUNTAIN_LAYOUT %s filled=%s elapsed_ms=%.3f%n",
                        style, filled, (System.nanoTime() - start) / 1_000_000.0D);
                assertEquals(FountainBasinGeometry.PART_SHAPE_COUNT, collision.length);
                // Check that cheaper construction did not defer a large cost to
                // the repeated picking/movement operations used during play.
                Vec3d rayStart = new Vec3d(-1, 0.5, 0.5);
                Vec3d rayEnd = new Vec3d(2, 0.5, 0.5);
                Box movingBox = new Box(-0.6, 0.05, 0.1, -0.1, 0.9, 0.9);
                start = System.nanoTime();
                int queries = 0;
                for (int repeat = 0; repeat < 100; repeat++) {
                    for (int part = 0; part < outline.length; part++) {
                        if (!outline[part].isEmpty()) {
                            outline[part].raycast(rayStart, rayEnd, BlockPos.ORIGIN);
                            collision[part].calculateMaxDistance(Direction.Axis.X, movingBox, 1.0);
                            queries++;
                        }
                    }
                }
                System.out.printf("FOUNTAIN_QUERIES %s filled=%s pairs=%d elapsed_ms=%.3f%n",
                        style, filled, queries, (System.nanoTime() - start) / 1_000_000.0D);
            }
        }
    }
}
