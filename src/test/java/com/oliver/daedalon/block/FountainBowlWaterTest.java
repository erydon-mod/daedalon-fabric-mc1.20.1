package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FountainBowlWaterTest {
    @Test
    void raisedWaterUsesTheMeasuredOutlineAtEverySizeAndPlacement() {
        BlockPos anchor = new BlockPos(17, 32, -19);
        double base = 1.25;
        for (FountainBowlModel.Style style : FountainBowlModel.Style.values()) {
            for (FountainBowlModel.Size size : FountainBowlModel.Size.values()) {
                double diameter = size.diameter();
                double surface = anchor.getY() + base + style.waterSurfaceY(size);
                FountainWaterShape outline = style.waterOutline();
                for (int i = 0; i < outline.vertexCount(); i += 7) {
                    double x = anchor.getX() + 0.5 + outline.x(i) * diameter * 0.97;
                    double z = anchor.getZ() + 0.5 + outline.z(i) * diameter * 0.97;
                    Box inside = new Box(x - 0.0001, surface - 0.001, z - 0.0001,
                            x + 0.0001, surface + 0.02, z + 0.0001);
                    assertEquals(0.001, FountainBowlModel.containedWaterHeight(anchor, base, style, size, inside),
                            1.0E-6, style + " " + size + " vertex " + i);
                    assertEquals(0, FountainBowlModel.containedWaterHeight(anchor, base, style, size,
                            inside.offset(0, 0.03, 0)));
                    x = anchor.getX() + 0.5 + outline.x(i) * diameter * 1.1;
                    z = anchor.getZ() + 0.5 + outline.z(i) * diameter * 1.1;
                    Box outside = new Box(x - 0.0001, surface - 0.001, z - 0.0001,
                            x + 0.0001, surface + 0.02, z + 0.0001);
                    assertEquals(0, FountainBowlModel.containedWaterHeight(anchor, base, style, size, outside));
                }
            }
        }
    }

    @Test
    void waterSelectionIsRaisedWithoutChangingCachedSolidCollision() {
        for (FountainBowlModel.Style style : FountainBowlModel.Style.values()) {
            for (FountainBowlModel.Size size : FountainBowlModel.Size.values()) {
                double surface = style.waterSurfaceY(size);
                var collision = FountainBowlModel.collisionShape(style, size);
                var wet = FountainBowlModel.outlineShape(style, size, true);
                var hit = wet.raycast(new Vec3d(0.5, surface + 0.05, 0.5),
                        new Vec3d(0.5, surface - 0.05, 0.5), BlockPos.ORIGIN);
                assertNotNull(hit);
                assertEquals(surface, hit.getPos().y, 1.0E-6);
                assertSame(wet, FountainBowlModel.outlineShape(style, size, true));
                assertSame(collision, FountainBowlModel.outlineShape(style, size, false));
                assertSame(collision, FountainBowlModel.collisionShape(style, size));
                double gap = (style.modelHeight(size) - surface) / size.diameter();
                assertTrue(gap > 0.005 && gap < 0.012);
            }
        }
    }
}
