package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.AnthophorosFillProfile;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PlanterContentsSurfaceTest {
    private static final double EPSILON = 1e-6;
    private static final int[] STRIP_COUNTS = {3, 3, 5};

    @Test
    void everyWidthAndFacingCoversTheMeasuredFootprintWithoutGapsOrOverlaps() {
        int checked = 0;
        for (int width = 2; width <= 4; width++) {
            var profile = AnthophorosFillProfile.forWidth(width);
            assertEquals(STRIP_COUNTS[width - 2], profile.tileCount());
            for (int turn = 0; turn < 4; turn++) {
                var capture = new CapturedMesh();
                PlanterContentsSurface.emitEarthQuads(profile, null, null, turn, capture.emitter);
                assertEquals(STRIP_COUNTS[width - 2], capture.quads.size());
                double previousX = -profile.halfWidth(), area = 0;
                double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
                for (var quad : capture.quads) {
                    double x0 = local(quad.xyz[0], turn)[0], x1 = local(quad.xyz[3], turn)[0];
                    assertEquals(previousX, x0, EPSILON, "Consecutive strips must share the same edge");
                    assertTrue(x1 > x0 && x1 - x0 <= 1 + EPSILON);
                    if (previousX > -profile.halfWidth() + EPSILON) {
                        assertEquals(Math.rint(x0 + .5), x0 + .5, EPSILON, "Joins must coincide with one-metre repeats");
                    }
                    previousX = x1;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        double[] source = local(quad.xyz[vertex], turn);
                        assertEquals(vertex >= 2 ? x1 : x0, source[0], EPSILON);
                        assertEquals(vertex == 1 || vertex == 2 ? profile.halfDepth() : -profile.halfDepth(), source[1], EPSILON);
                        assertEquals(profile.surfaceY(), quad.xyz[vertex][1], EPSILON);
                        assertArrayEquals(new float[]{0, 1, 0}, quad.normals[vertex]);
                        minX = Math.min(minX, quad.xyz[vertex][0]); maxX = Math.max(maxX, quad.xyz[vertex][0]);
                        minZ = Math.min(minZ, quad.xyz[vertex][2]); maxZ = Math.max(maxZ, quad.xyz[vertex][2]);
                    }
                    double a = upwardCross(quad.xyz[0], quad.xyz[1], quad.xyz[2]);
                    double b = upwardCross(quad.xyz[0], quad.xyz[2], quad.xyz[3]);
                    assertTrue(a > 0 && b > 0, "Every emitted triangle must face upward");
                    area += (a + b) / 2;
                }
                assertEquals(profile.halfWidth(), previousX, EPSILON);
                assertEquals(4 * profile.halfWidth() * profile.halfDepth(), area, EPSILON);
                assertEquals(2 * profile.halfWidth(), turn % 2 == 0 ? maxX - minX : maxZ - minZ, EPSILON);
                assertEquals(2 * profile.halfDepth(), turn % 2 == 0 ? maxZ - minZ : maxX - minX, EPSILON);
                checked++;
            }
        }
        assertEquals(12, checked);
    }

    @Test
    void eachStripKeepsAtlasBoundedUvsExactDensityAndContinuousRepeatPhase() {
        RenderMaterial material = (RenderMaterial) Proxy.newProxyInstance(RenderMaterial.class.getClassLoader(),
                new Class<?>[]{RenderMaterial.class}, (proxy, method, args) -> {
                    throw new AssertionError("The soil emitter must pass its supplied material unchanged");
                });
        assertEquals(64, AnthophorosFillProfile.TILE_PIXELS);
        assertEquals(4, AnthophorosFillProfile.ATLAS_MIP_LEVELS);
        for (int width = 2; width <= 4; width++) {
            var profile = AnthophorosFillProfile.forWidth(width);
            for (int turn = 0; turn < 4; turn++) {
                var capture = new CapturedMesh();
                PlanterContentsSurface.emitEarthQuads(profile, null, material, turn, capture.emitter);
                CapturedQuad previous = null;
                for (var quad : capture.quads) {
                    var distinct = new HashSet<List<Float>>();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        assertTrue(quad.uv[vertex][0] >= 0 && quad.uv[vertex][0] <= 1);
                        assertTrue(quad.uv[vertex][1] >= 0 && quad.uv[vertex][1] <= 1);
                        double[] source = local(quad.xyz[vertex], turn);
                        double phaseDifference = quad.uv[vertex][0] - source[0] - .5;
                        assertEquals(Math.rint(phaseDifference), phaseDifference, EPSILON,
                                "Each strip must retain the same source-centred periodic field");
                        assertEquals(source[1] + .5, quad.uv[vertex][1], EPSILON);
                        distinct.add(List.of(quad.uv[vertex][0], quad.uv[vertex][1]));
                    }
                    assertEquals(4, distinct.size());
                    assertEquals(.08, quad.uv[0][1], EPSILON);
                    assertEquals(.92, quad.uv[1][1], EPSILON);
                    double stripWidth = distance(quad.xyz[0], quad.xyz[3]);
                    double stripDepth = distance(quad.xyz[0], quad.xyz[1]);
                    assertEquals(64, (quad.uv[3][0] - quad.uv[0][0]) * 64 / stripWidth, 1e-4);
                    assertEquals(64, (quad.uv[1][1] - quad.uv[0][1]) * 64 / stripDepth, 1e-4);
                    if (previous != null) {
                        assertArrayEquals(previous.xyz[3], quad.xyz[0], 1e-6F);
                        assertArrayEquals(previous.xyz[2], quad.xyz[1], 1e-6F);
                        assertEquals(1, previous.uv[3][0], EPSILON);
                        assertEquals(0, quad.uv[0][0], EPSILON);
                        assertEquals(previous.uv[3][1], quad.uv[0][1], EPSILON);
                        assertEquals(previous.uv[2][1], quad.uv[1][1], EPSILON);
                    }
                    previous = quad;
                    assertArrayEquals(new int[]{-1, -1, -1, -1}, quad.colors);
                    assertEquals(1, quad.materialWrites);
                    assertSame(material, quad.material);
                    assertEquals(1, quad.cullWrites);
                    assertNull(quad.cullFace);
                    assertEquals(Direction.UP, quad.nominalFace);
                    assertEquals(ShaderTerrainNormalBridge.encodeObjTag(0), quad.tag);
                    assertTrue(ShaderTerrainNormalBridge.isObjQuad(quad.tag));
                    assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(quad.tag));
                    assertEquals(1, quad.spriteBakes);
                    assertEquals(MutableQuadView.BAKE_NORMALIZED, quad.bakeFlags);
                }
            }
        }
    }

    @Test
    void sourceCentreAndScaledPackOverridesRetainTheSameOneMetreRepeat() {
        for (int width = 2; width <= 4; width++) {
            var capture = new CapturedMesh();
            PlanterContentsSurface.emitEarthQuads(AnthophorosFillProfile.forWidth(width), null, null, 0, capture.emitter);
            for (var quad : capture.quads) {
                double x0 = local(quad.xyz[0], 0)[0], x1 = local(quad.xyz[3], 0)[0];
                if (x0 < 0 && x1 > 0) {
                    double centreU = quad.uv[0][0] + (quad.uv[3][0] - quad.uv[0][0]) * -x0 / (x1 - x0);
                    assertEquals(.5, centreU, EPSILON);
                    assertEquals(.5, (quad.uv[0][1] + quad.uv[1][1]) / 2, EPSILON);
                }
                for (int scale : new int[]{1, 2, 4}) {
                    // Matching overrides retain this one-metre repeat independently
                    // of their pixel resolution; no canvas crop is inferred.
                    assertEquals(64 * scale, (quad.uv[3][0] - quad.uv[0][0]) * 64 * scale / (x1 - x0), 1e-4);
                }
            }
        }
    }

    private static double[] local(float[] world, int turn) {
        double x = world[0] - .5, z = world[2] - .5;
        for (int step = 0; step < turn; step++) {
            double oldX = x; x = z; z = -oldX;
        }
        return new double[]{x, z};
    }

    private static double distance(float[] a, float[] b) {
        return Math.hypot(a[0] - b[0], a[2] - b[2]);
    }

    private static double upwardCross(float[] a, float[] b, float[] c) {
        return (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
    }

    private static final class CapturedQuad {
        final float[][] xyz = new float[4][3], uv = new float[4][2], normals = new float[4][3];
        final int[] colors = new int[4];
        RenderMaterial material;
        Direction cullFace, nominalFace;
        int materialWrites, cullWrites, tag, spriteBakes, bakeFlags;
    }

    private static final class CapturedMesh {
        final List<CapturedQuad> quads = new ArrayList<>();
        CapturedQuad current = new CapturedQuad();
        final QuadEmitter emitter = (QuadEmitter) Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),
                new Class<?>[]{QuadEmitter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "pos", "normal" -> {
                        float[][] target = method.getName().equals("pos") ? current.xyz : current.normals;
                        for (int axis = 0; axis < 3; axis++) target[(int) args[0]][axis] = (float) args[axis + 1];
                        yield proxy;
                    }
                    case "uv" -> { current.uv[(int) args[0]][0] = (float) args[1]; current.uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                    case "color" -> { for (int vertex = 0; vertex < 4; vertex++) current.colors[vertex] = (int) args[vertex]; yield proxy; }
                    case "material" -> { current.material = (RenderMaterial) args[0]; current.materialWrites++; yield proxy; }
                    case "cullFace" -> { current.cullFace = (Direction) args[0]; current.cullWrites++; yield proxy; }
                    case "nominalFace" -> { current.nominalFace = (Direction) args[0]; yield proxy; }
                    case "tag" -> { current.tag = (int) args[0]; yield proxy; }
                    case "spriteBake" -> {
                        assertNull(args[0], "The test intercepts atlas baking without a renderer or texture manager");
                        current.bakeFlags = (int) args[1]; current.spriteBakes++; yield proxy;
                    }
                    case "emit" -> { quads.add(current); current = new CapturedQuad(); yield proxy; }
                    default -> throw new AssertionError("Unexpected production emitter operation: " + method.getName());
                });
    }
}
