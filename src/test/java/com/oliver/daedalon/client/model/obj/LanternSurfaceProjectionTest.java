package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual placed-surface projector with the shipped lantern triangles. */
final class LanternSurfaceProjectionTest {
    private static final double TIE_ALLOWANCE = 3.0E-5;
    private static final BlockPos ORIGIN = new BlockPos(-7, 83, 11);

    @Test void shallowRoofsAndUndersidesUseTheHorizontalTexturePlane() {
        var quad = new Triangle();
        quad.set(new float[]{0, 0, 0}, new float[]{1, .15F, 0}, new float[]{0, .15F, 1});
        assertEquals(Direction.DOWN, LanternSurfaceEmitter.projectionFace(quad.view));
        quad.set(new float[]{0, 0, 0}, new float[]{0, .15F, 1}, new float[]{1, .15F, 0});
        assertEquals(Direction.UP, LanternSurfaceEmitter.projectionFace(quad.view));
    }

    @Test void everyOpaqueBodyAndSuspensionFaceKeepsBoundedTexelStretchInEveryPose() throws Exception {
        ResourceManager resources = resources();
        var quad = new Triangle();
        Set<String> checkedGroups = new HashSet<>();
        int checked = 0;
        int formerlyStretched = 0;
        for (String model : List.of("georgian", "gothic", "byzantine", "suspension")) {
            var id = new Identifier("daedalon", "models/mesh/lantern_" + model + ".json");
            ObjMeshDefinition definition = ObjMeshDefinition.load(resources, id);
            ObjMeshData mesh = ObjMeshParser.parse(resources, definition);
            for (var size : LanternBlock.Size.values()) {
                assertTrue(size.metres == .75F || size.metres == 1.25F);
                for (Direction facing : Direction.Type.HORIZONTAL) {
                    for (var face : mesh.faces()) {
                        if (face.material().equals("glass") || face.material().equals("flame")) continue;
                        String group = face.groupName().isEmpty() ? face.objectName() : face.groupName();
                        LanternMeshTransform pose = pose(model, group, size, facing);
                        for (int vertex = 0; vertex < 3; vertex++) {
                            var point = mesh.positions().get(face.vertices().get(vertex).positionIndex());
                            quad.points[vertex][0] = pose.x(point.x() + .5F, point.z() + .5F);
                            quad.points[vertex][1] = pose.y(point.y());
                            quad.points[vertex][2] = pose.z(point.x() + .5F, point.z() + .5F);
                        }
                        double[] normal = quad.normal();
                        double length = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1]
                                + normal[2] * normal[2]);
                        if (length == 0) continue;
                        Direction projection = LanternSurfaceEmitter.projectionFace(quad.view);
                        double component = Math.abs(normal[projection.getAxis().ordinal()]);
                        double largest = Math.max(Math.abs(normal[0]),
                                Math.max(Math.abs(normal[1]), Math.abs(normal[2])));
                        assertTrue(component >= largest * (1 - TIE_ALLOWANCE),
                                () -> model + "/" + group + " selected an edge-on texture plane");

                        // Orthogonal projection's minimum singular value equals projected area / surface area.
                        // Its reciprocal measures the longest possible texel stretch on this triangle.
                        double projectedArea = quad.projectedDoubleArea(projection);
                        double stretch = length / projectedArea;
                        assertTrue(stretch <= Math.sqrt(3) * (1 + TIE_ALLOWANCE),
                                () -> model + "/" + group + " stretches texels by " + stretch);
                        if (Math.abs(normal[1]) / length < Math.sqrt(.9998)
                                && Math.max(Math.abs(normal[0]), Math.abs(normal[2])) / length < .25) {
                            formerlyStretched++;
                        }
                        checkedGroups.add(model + "/" + group);
                        checked++;
                    }
                }
            }
        }
        for (String model : List.of("georgian", "gothic", "byzantine")) {
            for (String mount : List.of("pier", "wall", "hanging")) {
                assertTrue(checkedGroups.contains(model + "/" + mount + "_stone"), model + "/" + mount);
            }
        }
        assertTrue(checkedGroups.stream().anyMatch(group -> group.startsWith("suspension/")));
        assertTrue(checked > 500_000, "Replay all opaque triangles at both sizes and four facings");
        assertTrue(formerlyStretched > 0, "The actual assets must include the former shallow-surface failure");
    }

    private static LanternMeshTransform pose(String model, String group, LanternBlock.Size size, Direction facing) {
        if (model.equals("suspension")) return LanternMeshTransform.FULL_CHAIN;
        var mount = group.startsWith("pier_") ? LanternBlock.Mount.PIER
                : group.startsWith("wall_") ? LanternBlock.Mount.WALL : LanternBlock.Mount.HANGING;
        return LanternMeshTransform.forMount(mount, size, facing);
    }

    private static final class Triangle {
        private final float[][] points = new float[3][3];
        private final QuadView view = (QuadView) Proxy.newProxyInstance(QuadView.class.getClassLoader(),
                new Class<?>[]{QuadView.class}, (proxy, method, args) -> {
                    int axis = switch (method.getName()) {
                        case "x" -> 0;
                        case "y" -> 1;
                        case "z" -> 2;
                        default -> throw new AssertionError("Unexpected projection read " + method.getName());
                    };
                    return points[(int) args[0]][axis];
                });

        private void set(float[] a, float[] b, float[] c) {
            System.arraycopy(a, 0, points[0], 0, 3);
            System.arraycopy(b, 0, points[1], 0, 3);
            System.arraycopy(c, 0, points[2], 0, 3);
        }

        private double[] normal() {
            double ax = points[1][0] - points[0][0], ay = points[1][1] - points[0][1];
            double az = points[1][2] - points[0][2];
            double bx = points[2][0] - points[0][0], by = points[2][1] - points[0][1];
            double bz = points[2][2] - points[0][2];
            return new double[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
        }

        private double projectedDoubleArea(Direction projection) {
            double[] s = new double[3], t = new double[3];
            for (int vertex = 0; vertex < 3; vertex++) {
                s[vertex] = LanternWorldUv.s(projection, ORIGIN, points[vertex][0], points[vertex][2]);
                t[vertex] = LanternWorldUv.t(projection, ORIGIN, points[vertex][1], points[vertex][2]);
            }
            return Math.abs((s[1] - s[0]) * (t[2] - t[0]) - (s[2] - s[0]) * (t[1] - t[0]));
        }
    }

    private static ResourceManager resources() {
        ResourcePack pack = (ResourcePack) Proxy.newProxyInstance(ResourcePack.class.getClassLoader(),
                new Class<?>[]{ResourcePack.class}, (proxy, method, args) ->
                        method.getName().equals("getName") ? "shipped lantern meshes" : null);
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) {
                        throw new AssertionError("Unexpected resource read " + method.getName());
                    }
                    Identifier id = (Identifier) args[0];
                    Path path = Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
                    return Files.isRegularFile(path)
                            ? Optional.of(new Resource(pack, () -> Files.newInputStream(path))) : Optional.empty();
                });
    }
}
