package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class DecorOrientationTransformTest {
    @Test
    void allEightCachedTurnsRotateGeometryAndNormalsWithoutChangingTextureOrHeight() {
        for (int turn = 0; turn < 8; turn++) {
            float[][] points = {{1, .2F, .5F}, {1, .9F, .5F}, {1, .8F, .9F}, {1, .1F, .9F}};
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
                            default -> throw new AssertionError("Rotation changed UVs, colours, light or tags: " + name);
                        };
                    });
            var transform = DecorOrientationTransform.forTurn(turn);
            assertSame(transform, DecorOrientationTransform.forTurn(turn + 8));
            assertTrue(transform.transform(quad));
            double angle = turn * Math.PI / 4;
            assertEquals(.5 + Math.cos(angle) / 2, points[0][0], 1e-6);
            assertEquals(.5 + Math.sin(angle) / 2, points[0][2], 1e-6);
            assertEquals(.2F, points[0][1]);
            assertEquals(Math.cos(angle), normals[0][0], 1e-6);
            assertEquals(Math.sin(angle), normals[0][2], 1e-6);
            assertNull(cull[0]);
        }
    }
}
