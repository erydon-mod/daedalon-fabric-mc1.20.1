package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class FriezeAtlasRepeatGuardTest {
    @Test void touchingEitherOuterEdgeSelectsTheIdenticalInteriorCopyForTheWholeQuad() {
        assertShift(new float[]{0, 0.017F, 1F / 12, 0.041F}, 0.5F);
        assertShift(new float[]{1, 0.983F, 11F / 12, 0.959F}, -0.5F);
        assertShift(new float[]{0, 0, 0, 0}, 0.5F);
        assertShift(new float[]{1, 1, 1, 1}, -0.5F);
        assertShift(new float[]{-1e-7F, 0.017F, 1F / 12, 0.041F}, 0.5F);
        assertShift(new float[]{0.000002F, 0.02F, 0.08F, 0.04F}, 0.5F);
        assertShift(new float[]{0.999998F, 0.98F, 0.92F, 0.96F}, -0.5F);
    }

    @Test void interiorRepeatBoundariesAndQuadsSpanningBothCopiesRemainUnchanged() {
        for (float[] u : new float[][]{
                {0.25F, 0.25F, 0.31F, 0.31F}, {0.5F, 0.5F, 0.58F, 0.58F},
                {0, 0.5F, 0.5F, 0}, {0.5F, 1, 1, 0.5F}, {0, 1, 1, 0},
                {-0.01F, 0, 0.02F, 0}, {1.01F, 1, 0.98F, 1},
                {Float.NaN, 0, 0.02F, 0}}) {
            float[] before = u.clone();
            assertFalse(FriezeAtlasRepeatGuard.protect(quad(u, new float[]{0.2F, 0.2F, 0.3F, 0.3F})));
            assertArrayEquals(before, u);
        }
    }

    @Test void projectedFacesAtEveryWorldPhaseKeepTheirScaleAndPbrSamplingInsideTheSprite() {
        for (int tile = 0; tile < 6; tile++) for (boolean reverse : new boolean[]{false, true}) {
            float[] u = new float[4];
            float[] local = {0, 0.017F, 1, 0.31F};
            for (int vertex = 0; vertex < 4; vertex++) {
                float projected = reverse ? (6 - tile - local[vertex]) / 6 : (tile + local[vertex]) / 6;
                u[vertex] = WorldTexturePhase.detailedSurface().mapU(projected, 0);
            }
            float[] before = u.clone(), v = {0, 0.13F, 0.71F, 1}, beforeV = v.clone();
            FriezeAtlasRepeatGuard.protect(quad(u, v));
            for (int vertex = 0; vertex < 4; vertex++) {
                assertEquals(before[vertex] - before[0], u[vertex] - u[0], 1e-7,
                        "Every interpolated UV and tangent must retain its authored scale");
                assertEquals(0, Math.IEEEremainder(u[vertex] - before[vertex], 0.5), 1e-7);
                assertTrue(u[vertex] >= 1F / 6 - 1e-6 && u[vertex] <= 5F / 6 + 1e-6,
                        "All six world repeat columns must use an interior copy");
            }
            assertArrayEquals(beforeV, v);
            assertFalse(FriezeAtlasRepeatGuard.protect(quad(u, v)));
        }
    }

    @Test void filteredSilverAlbedoNormalAndSpecularCannotReadTheAdjacentAtlasSprite() {
        // A flat 16px Silver sprite has no authored purple/blue stripe. Linear
        // filtering at its outside boundary nevertheless blends its neighbour.
        for (int[] channels : new int[][]{{206, 0}, {128, 255}, {255, 0}}) {
            assertNotEquals(channels[0], atlasSample(0, channels[0], channels[1]));
            float[] u = {0, 0.001F, 1F / 12, 0.017F};
            FriezeAtlasRepeatGuard.protect(quad(u, new float[4]));
            for (float value : u) assertEquals(channels[0], atlasSample(value, channels[0], channels[1]));
        }
    }

    private static double atlasSample(float spriteU, int inside, int outside) {
        // Sprite occupies pixel columns 8..23 of a larger block/PBR atlas.
        double pixel = 8 + spriteU * 16 - 0.5, fraction = pixel - Math.floor(pixel);
        int left = (int) Math.floor(pixel), right = left + 1;
        int a = left >= 8 && left < 24 ? inside : outside;
        int b = right >= 8 && right < 24 ? inside : outside;
        return a + (b - a) * fraction;
    }

    private static void assertShift(float[] u, float offset) {
        float[] before = u.clone(), v = {0.13F, 0.17F, 0.81F, 0.95F}, beforeV = v.clone();
        assertTrue(FriezeAtlasRepeatGuard.protect(quad(u, v)));
        for (int vertex = 0; vertex < 4; vertex++) {
            assertEquals(offset, u[vertex] - before[vertex], 1e-7);
            assertEquals(0, Math.IEEEremainder(u[vertex] - before[vertex], 0.5), 1e-7,
                    "The sampled horizontal repeat phase must not change");
            assertTrue(u[vertex] > 0 && u[vertex] < 1);
        }
        assertArrayEquals(beforeV, v, "Vertical phase must remain unchanged");
        assertFalse(FriezeAtlasRepeatGuard.protect(quad(u, v)), "Applying the guard twice must be stable");
    }

    private static MutableQuadView quad(float[] u, float[] v) {
        return (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "u" -> u[(int) args[0]];
                    case "v" -> v[(int) args[0]];
                    case "uv" -> {
                        int vertex = (int) args[0];
                        u[vertex] = (float) args[1]; v[vertex] = (float) args[2];
                        yield proxy;
                    }
                    default -> throw new AssertionError("Guard must not edit positions, normals, light, colours, faces or tags: " + method.getName());
                });
    }
}
