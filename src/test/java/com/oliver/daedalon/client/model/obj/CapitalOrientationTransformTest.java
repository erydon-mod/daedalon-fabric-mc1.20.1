package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.CapitalBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class CapitalOrientationTransformTest {
    @Test void additionalUniformFitKeepsTheExistingTopAndMovesTheBaseDown() {
        for (CapitalBlock.Size size : CapitalBlock.Size.values()) for (CapitalOrientation turn : CapitalOrientation.values()) {
            float top = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
            float fit = 1.75F;
            float[][] points = {{.5F, 1, .5F}, {.5F, 0, .5F}, {1, .5F, .5F}, {.5F, .5F, 1}};
            float[][] normals = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
            MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                    new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "x", "y", "z" -> points[(int) args[0]]["xyz".indexOf(method.getName())];
                        case "normalX", "normalY", "normalZ" -> normals[(int) args[0]]["XYZ".indexOf(method.getName().charAt(6))];
                        case "hasNormal" -> true;
                        case "pos", "normal" -> {
                            float[] target = (method.getName().equals("pos") ? points : normals)[(int) args[0]];
                            for (int axis = 0; axis < 3; axis++) target[axis] = (float) args[axis + 1];
                            yield proxy;
                        }
                        case "cullFace", "nominalFace" -> args == null || args.length == 0 ? Direction.UP : proxy;
                        default -> throw new AssertionError("Placed fit must not alter UVs or vertex channels: " + method.getName());
                    });
            CapitalOrientationTransform.forSize(turn, size, fit).transform(quad);
            assertArrayEquals(new float[]{top * .5F, top, top * .5F}, points[0], 1e-6F);
            assertEquals(top * (1 - fit), points[1][1], 1e-6F);
            assertEquals(top * fit * .5F, distance(points[2], points[3]) / Math.sqrt(2), 1e-6);
            assertArrayEquals(new float[]{0, 1, 0}, normals[0], 1e-6F);
            assertEquals(turn.cosine, normals[2][0]);
            assertEquals(turn.sine, normals[2][2]);
            assertSame(CapitalOrientationTransform.forSize(turn, size),
                    CapitalOrientationTransform.forSize(turn, size, 1));
        }
        for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> CapitalOrientationTransform.forSize(
                    CapitalOrientation.STRAIGHT, CapitalBlock.Size.STANDARD, invalid));
    }

    private static double distance(float[] a, float[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void doubleCapitalSpansTwoCellsInEveryAxis() {
        float[][] point = {{1F, 1F, 0.5F}, {0F, 0F, 0F}, {0F, 0F, 0F}, {0F, 0F, 0F}};
        MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x" -> point[(int) args[0]][0];
                    case "y" -> point[(int) args[0]][1];
                    case "z" -> point[(int) args[0]][2];
                    case "hasNormal" -> false;
                    case "pos" -> {
                        int vertex = (int) args[0];
                        for (int axis = 0; axis < 3; axis++) point[vertex][axis] = (float) args[axis + 1];
                        yield proxy;
                    }
                    case "cullFace", "nominalFace" -> args == null || args.length == 0 ? Direction.UP : proxy;
                    default -> throw new AssertionError("Unexpected quad operation: " + method.getName());
                });
        assertTrue(CapitalOrientationTransform.forSize(CapitalOrientation.STRAIGHT,
                CapitalBlock.Size.DOUBLE).transform(quad));
        assertArrayEquals(new float[]{2F, 2F, 1F}, point[0]);
    }

    @Test
    void rotatesPositionsAndNormalsWithoutTouchingUvsOrOtherVertexData() {
        double diagonal = Math.sqrt(0.5);
        double[][] expected = {{0.5 + diagonal / 2, 0.5 + diagonal / 2}, {0.5, 1}, {0.5 - diagonal / 2, 0.5 + diagonal / 2}};
        CapitalOrientation[] turns = {CapitalOrientation.DIAGONAL, CapitalOrientation.STRAIGHT_90, CapitalOrientation.DIAGONAL_135};
        for (int i = 0; i < turns.length; i++) {
            float[][] points = {{1, 0.2F, 0.5F}, {1, 0.9F, 0.5F}, {1, 0.8F, 0.9F}, {1, 0.1F, 0.9F}};
            float[][] normals = {{1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {1, 0, 0}};
            Direction[] cull = {Direction.EAST}, nominal = {Direction.EAST};
            MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                    new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        return switch (name) {
                            case "x", "y", "z" -> points[(int) args[0]]["xyz".indexOf(name)];
                            case "normalX", "normalY", "normalZ" -> normals[(int) args[0]]["XYZ".indexOf(name.charAt(6))];
                            case "hasNormal" -> true;
                            case "pos", "normal" -> {
                                float[] target = (name.equals("pos") ? points : normals)[(int) args[0]];
                                for (int axis = 0; axis < 3; axis++) target[axis] = (float) args[axis + 1];
                                yield proxy;
                            }
                            case "cullFace", "nominalFace" -> {
                                Direction[] target = name.equals("cullFace") ? cull : nominal;
                                if (args == null || args.length == 0) yield target[0];
                                target[0] = (Direction) args[0]; yield proxy;
                            }
                            default -> throw new AssertionError("Rotation must not alter UVs, colours, light or tags: " + name);
                        };
                    });
            var transform = CapitalOrientationTransform.forOrientation(turns[i]);
            assertSame(transform, CapitalOrientationTransform.forOrientation(turns[i]));
            assertTrue(transform.transform(quad));
            assertEquals(expected[i][0], points[0][0], 1e-6);
            assertEquals(expected[i][1], points[0][2], 1e-6);
            assertEquals(0.2F, points[0][1]);
            assertEquals(2 * (expected[i][0] - 0.5), normals[0][0], 1e-6);
            assertEquals(2 * (expected[i][1] - 0.5), normals[0][2], 1e-6);
            assertNull(cull[0], "Rotated overhangs must not be culled against an adjacent block");
        }
        assertNull(CapitalOrientationTransform.forOrientation(CapitalOrientation.STRAIGHT));
    }
}
