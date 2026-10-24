package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class StatueDiagonalTransformTest {
    @Test
    void diagonalStatuesRotateAtMeasuredSupportPivotAndKeepSizeTextureDensity() throws Exception {
        var type = Class.forName("com.oliver.daedalon.client.model.obj.ObjMeshBakedModel$StatueTransform");
        var constructor = type.getDeclaredConstructor(float.class, int.class, float.class, float.class,
                float.class, float.class, float.class, float.class);
        constructor.setAccessible(true);
        for (int turn = 0; turn < 8; turn++) {
            for (float scale : new float[]{1F / 3, 2F / 3, 1}) {
                float[][] points = {{.87F, .62F, .41F}, {.37F, .02F, .41F}, {.37F, 3.02F, .41F}, {.37F, .02F, .91F}};
                float[][] normals = {{1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {1, 0, 0}};
                float[][] uv = {{.2F, .8F}, {.2F, .8F}, {.2F, .8F}, {.2F, .8F}};
                Direction[] nominal = {Direction.EAST};
                MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                        new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> {
                            String name = method.getName();
                            return switch (name) {
                                case "x", "y", "z" -> points[(int) args[0]]["xyz".indexOf(name)];
                                case "u", "v" -> uv[(int) args[0]][name.equals("u") ? 0 : 1];
                                case "normalX", "normalY", "normalZ" -> normals[(int) args[0]]["XYZ".indexOf(name.charAt(6))];
                                case "hasNormal" -> true;
                                case "pos", "normal", "uv" -> {
                                    float[] target = name.equals("pos") ? points[(int) args[0]]
                                            : name.equals("normal") ? normals[(int) args[0]] : uv[(int) args[0]];
                                    for (int axis = 0; axis < target.length; axis++) target[axis] = (float) args[axis + 1];
                                    yield proxy;
                                }
                                case "cullFace" -> proxy;
                                case "nominalFace" -> {
                                    if (args == null || args.length == 0) yield nominal[0];
                                    nominal[0] = (Direction) args[0]; yield proxy;
                                }
                                default -> throw new AssertionError("Unexpected vertex mutation: " + name);
                            };
                        });
                var transform = (RenderContext.QuadTransform) constructor.newInstance(scale, turn,
                        .37F, .02F, .41F, -.3F, 0F, .4F);
                assertTrue(transform.transform(quad));
                double angle = turn * Math.PI / 4;
                assertEquals(.2 + .5 * scale * Math.cos(angle), points[0][0], 1e-6);
                assertEquals(.9 + .5 * scale * Math.sin(angle), points[0][2], 1e-6);
                assertEquals(.6 * scale, points[0][1], 1e-6);
                assertEquals(.2, points[1][0], 1e-6);
                assertEquals(.9, points[1][2], 1e-6);
                assertEquals(3 * scale, points[2][1], 1e-6);
                assertEquals(Math.cos(angle), normals[0][0], 1e-6);
                assertEquals(Math.sin(angle), normals[0][2], 1e-6);
                assertEquals(.5 - .3 * scale, uv[0][0], 1e-6);
                assertEquals(.5 + .3 * scale, uv[0][1], 1e-6);
            }
        }
    }
}
