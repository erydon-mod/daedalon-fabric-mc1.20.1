package com.oliver.daedalon.glaux;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GlauxShapeTest {
    @Test void measuredShapeRemovesEmptySpaceAroundTheTwoCellOwl() {
        double lower = volume(GlauxShape.get(false, Direction.SOUTH));
        double upper = volume(GlauxShape.get(true, Direction.SOUTH));
        // Previous outline was two large rectangular prisms, almost 3.57 m3.
        assertTrue(lower + upper < 2.7, "The tighter silhouette must remove substantial empty space");
        assertTrue(lower > .3 && upper > .3);
        assertFalse(contains(GlauxShape.get(false, Direction.SOUTH), 1.15, .25, 1.1));
        assertFalse(contains(GlauxShape.get(true, Direction.SOUTH), -.1, .8, -.1));
        assertTrue(contains(GlauxShape.get(true, Direction.SOUTH), .68, .7, .6));
    }

    @Test void cachedShapesRotateConsistentlyAndStayWithinTheirCellHeight() {
        for (boolean upper : new boolean[]{false, true}) {
            VoxelShape south = GlauxShape.get(upper, Direction.SOUTH);
            for (Direction facing : Direction.Type.HORIZONTAL) {
                VoxelShape shape = GlauxShape.get(upper, facing);
                assertSame(shape, GlauxShape.get(upper, facing));
                assertEquals(volume(south), volume(shape), 1e-7);
                assertEquals(0, shape.getMin(Direction.Axis.Y), 1e-7);
                assertEquals(1, shape.getMax(Direction.Axis.Y), 1e-7);
            }
        }
    }

    private static double volume(VoxelShape shape) {
        double[] total = {0};
        shape.forEachBox((a,b,c,d,e,f) -> total[0] += (d-a)*(e-b)*(f-c));
        return total[0];
    }
    private static boolean contains(VoxelShape shape, double x, double y, double z) {
        return shape.getBoundingBoxes().stream().anyMatch(box -> box.contains(x,y,z));
    }
}
