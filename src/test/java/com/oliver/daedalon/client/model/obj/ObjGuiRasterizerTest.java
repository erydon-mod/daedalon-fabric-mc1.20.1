package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObjGuiRasterizerTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void nearestTriangleWinsRegardlessOfSubmissionOrder() {
        ObjGuiRasterizer.Vertex farA = vertex(0.0F, 0.0F, 0.25F, 0.0F, 0.0F);
        ObjGuiRasterizer.Vertex farB = vertex(4.0F, 0.0F, 0.25F, 1.0F, 0.0F);
        ObjGuiRasterizer.Vertex farC = vertex(0.0F, 4.0F, 0.25F, 0.0F, 1.0F);
        ObjGuiRasterizer.Vertex nearA = vertex(0.0F, 0.0F, 0.75F, 0.2F, 0.3F);
        ObjGuiRasterizer.Vertex nearB = vertex(4.0F, 0.0F, 0.75F, 0.8F, 0.3F);
        ObjGuiRasterizer.Vertex nearC = vertex(0.0F, 4.0F, 0.75F, 0.2F, 0.9F);

        ObjGuiRasterizer farThenNear = new ObjGuiRasterizer(4);
        farThenNear.rasterize(farA, farB, farC, 3);
        farThenNear.rasterize(nearA, nearB, nearC, 7);
        ObjGuiRasterizer nearThenFar = new ObjGuiRasterizer(4);
        nearThenFar.rasterize(nearA, nearB, nearC, 7);
        nearThenFar.rasterize(farA, farB, farC, 3);

        for (int index = 0; index < farThenNear.sampleCount(); index++) {
            assertEquals(farThenNear.isCovered(index), nearThenFar.isCovered(index));
            if (!farThenNear.isCovered(index)) {
                continue;
            }
            assertEquals(7, farThenNear.materialAt(index));
            assertEquals(farThenNear.materialAt(index), nearThenFar.materialAt(index));
            assertEquals(0.75F, farThenNear.depthAt(index), EPSILON);
            assertEquals(farThenNear.depthAt(index), nearThenFar.depthAt(index), EPSILON);
            assertEquals(farThenNear.uAt(index), nearThenFar.uAt(index), EPSILON);
            assertEquals(farThenNear.vAt(index), nearThenFar.vAt(index), EPSILON);
            assertEquals(farThenNear.shadeAt(index), nearThenFar.shadeAt(index), EPSILON);
        }
        assertTrue(farThenNear.coveredSampleCount() > 0);
        assertEquals(farThenNear.coveredSampleCount(), nearThenFar.coveredSampleCount());
        assertEquals(0.425F, farThenNear.uAt(5), EPSILON);
        assertEquals(0.525F, farThenNear.vAt(5), EPSILON);
        assertEquals(1.0F, farThenNear.shadeAt(5), EPSILON);
    }

    @Test
    void repeatedRasterizationIsDeterministic() {
        ObjGuiRasterizer first = rasterizeFixture();
        ObjGuiRasterizer second = rasterizeFixture();

        assertEquals(first.resolution(), second.resolution());
        assertEquals(first.coveredSampleCount(), second.coveredSampleCount());
        for (int index = 0; index < first.sampleCount(); index++) {
            assertEquals(first.isCovered(index), second.isCovered(index));
            assertEquals(first.materialAt(index), second.materialAt(index));
            assertEquals(first.depthAt(index), second.depthAt(index), 0.0F);
            assertEquals(first.uAt(index), second.uAt(index), 0.0F);
            assertEquals(first.vAt(index), second.vAt(index), 0.0F);
            assertEquals(first.shadeAt(index), second.shadeAt(index), 0.0F);
        }
    }

    @Test
    void degenerateTriangleProducesNoSamples() {
        ObjGuiRasterizer rasterizer = new ObjGuiRasterizer(8);
        ObjGuiRasterizer.Vertex a = vertex(1.0F, 1.0F, 0.5F, 0.0F, 0.0F);
        ObjGuiRasterizer.Vertex b = vertex(2.0F, 2.0F, 0.5F, 0.5F, 0.5F);
        ObjGuiRasterizer.Vertex c = vertex(3.0F, 3.0F, 0.5F, 1.0F, 1.0F);

        rasterizer.rasterize(a, b, c, 4);

        assertEquals(0, rasterizer.coveredSampleCount());
        for (int index = 0; index < rasterizer.sampleCount(); index++) {
            assertFalse(rasterizer.isCovered(index));
        }
    }

    private static ObjGuiRasterizer rasterizeFixture() {
        ObjGuiRasterizer rasterizer = new ObjGuiRasterizer(8);
        rasterizer.rasterize(
                vertex(0.25F, 0.5F, 0.1F, 0.0F, 0.0F),
                vertex(7.5F, 1.0F, 0.4F, 1.0F, 0.0F),
                vertex(2.0F, 7.25F, 0.8F, 0.25F, 1.0F),
                5
        );
        return rasterizer;
    }

    private static ObjGuiRasterizer.Vertex vertex(float x,
                                                   float y,
                                                   float depth,
                                                   float u,
                                                   float v) {
        return new ObjGuiRasterizer.Vertex(x, y, depth, u, v, 0.0F, 1.0F, 0.0F);
    }
}
