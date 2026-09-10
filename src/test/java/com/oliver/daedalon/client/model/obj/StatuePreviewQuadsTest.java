package com.oliver.daedalon.client.model.obj;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class StatuePreviewQuadsTest {
    @Test void oversizedOffsetGeometryIsNotClampedToTheAnchorBlock() {
        var quads = StatuePreviewQuads.bake(VoxelShapes.cuboid(-2, .25, -1, 3, 4.25, 2),
                null, .125F, .1875F, .75F, .875F);
        assertEquals(6, quads.size());
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (var quad : quads) for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                float value = coordinate(quad, vertex, axis);
                min[axis] = Math.min(min[axis], value);
                max[axis] = Math.max(max[axis], value);
            }
            float u = Float.intBitsToFloat(quad.getVertexData()[vertex * 8 + 4]);
            float v = Float.intBitsToFloat(quad.getVertexData()[vertex * 8 + 5]);
            assertTrue(u >= .125F && u <= .1875F);
            assertTrue(v >= .75F && v <= .875F);
        }
        assertArrayEquals(new float[]{-2, .25F, -1}, min);
        assertArrayEquals(new float[]{3, 4.25F, 2}, max);
        assertThrows(UnsupportedOperationException.class, () -> quads.clear());
    }

    @Test void everyFaceWindsOutwardsSoThePreviewIsVisibleFromAllSides() {
        var quads = StatuePreviewQuads.bake(VoxelShapes.cuboid(-1, 0, -.5, 2, 4, 1.5),
                null, 0, 1, 0, 1);
        for (var quad : quads) {
            float[] a = new float[3], b = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                a[axis] = coordinate(quad, 1, axis) - coordinate(quad, 0, axis);
                b[axis] = coordinate(quad, 2, axis) - coordinate(quad, 0, axis);
            }
            Direction face = quad.getFace();
            float dot = (a[1]*b[2]-a[2]*b[1])*face.getOffsetX()
                    + (a[2]*b[0]-a[0]*b[2])*face.getOffsetY()
                    + (a[0]*b[1]-a[1]*b[0])*face.getOffsetZ();
            assertTrue(dot > 0, "Inward face: " + face);
        }
    }

    @Test void excessiveShapeComplexityFallsBackToOneBox() {
        var shape = VoxelShapes.empty();
        for (int x = 0; x < 129; x++) {
            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(x * 2, 0, 0, x * 2 + 1, 1, 1));
        }
        assertEquals(6, StatuePreviewQuads.bake(shape, null, 0, 1, 0, 1).size());
        assertTrue(StatuePreviewQuads.bake(VoxelShapes.empty(), null, 0, 1, 0, 1).isEmpty());
    }

    @Test void itemAndDirectionalRequestsProduceNoExtraFaces() {
        var cache = new StatuePreviewQuads();
        assertTrue(cache.get(null, null, null).isEmpty());
        for (Direction face : Direction.values()) assertTrue(cache.get(null, face, null).isEmpty());
    }

    private static float coordinate(BakedQuad quad, int vertex, int axis) {
        return Float.intBitsToFloat(quad.getVertexData()[vertex * 8 + axis]);
    }
}
