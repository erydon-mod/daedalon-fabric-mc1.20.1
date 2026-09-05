package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalOrientation;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class CapitalOrientationTransformTest {
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
