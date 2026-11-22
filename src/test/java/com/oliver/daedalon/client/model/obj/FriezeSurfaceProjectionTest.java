package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real shipped triangles through the placed-frieze projector, without a client or GPU. */
final class FriezeSurfaceProjectionTest {
    private static final float EPSILON = 2.0e-6F;

    @Test void allActualComponentsKeepWorldPhaseAndDensityInEveryFacing() throws Exception {
        ResourceManager resources = resources();
        Method normal = method("geometricNormal", List.class, List.class);
        Method closest = method("closestAxis", ObjMeshData.Vec3.class);
        Triangle quad = new Triangle();
        Set<String> components = new HashSet<>();
        Set<Integer> phases = new HashSet<>();
        int checked = 0;
        for (String style : List.of("corinthian", "ionic", "gothic", "byzantine")) {
            var definition = ObjMeshDefinition.load(resources,
                    new Identifier("daedalon", "models/mesh/" + style + "_frieze.json"));
            assertTrue(definition.forceUvProjection());
            assertTrue(definition.faceOrientedUvs());
            assertEquals(6, definition.boxProjectionSpan());
            assertEquals(new ObjMeshDefinition.Vec3(1, 1, 1), definition.scale());
            assertEquals(new ObjMeshDefinition.Vec3(0, 0, 0), definition.translate());
            ObjMeshData mesh = ObjMeshParser.parse(resources, definition);
            assertTrue(mesh.materialTextures().isEmpty(), "A fixed MTL texture would bypass the variant surface");
            int faceIndex = 0;
            for (var face : mesh.faces()) {
                assertEquals("none", face.material());
                var geometric = (ObjMeshData.Vec3) normal.invoke(null, mesh.positions(), face.vertices());
                if (geometric == null) continue;
                Direction sourceFace = (Direction) closest.invoke(null, geometric);
                // Spread every real component over all 216 XYZ repeat origins,
                // including negative coordinates, chunk edges and the world limit.
                int phase = faceIndex++ % 216;
                BlockPos pos = new BlockPos(-7 + phase / 36, -65 + phase / 6 % 6,
                        29_999_994 + phase % 6);
                phases.add(phase);
                components.add(style + "/" + face.objectName());
                for (int steps = 0; steps < 4; steps++) {
                    quad.set(mesh, face, sourceFace);
                    FriezeWorldTransform.forBlock(steps, pos).transform(quad.view);
                    float[] expectedU = new float[4], expectedV = new float[4];
                    for (int vertex = 0; vertex < 4; vertex++) {
                        expectedU[vertex] = expectedU(quad.face, pos, quad.points[vertex]);
                        expectedV[vertex] = expectedV(quad.face, pos, quad.points[vertex]);
                        quad.uv[vertex][0] = WorldTexturePhase.detailedSurface().mapU(quad.uv[vertex][0], 0);
                        quad.uv[vertex][1] = WorldTexturePhase.detailedSurface().mapV(quad.uv[vertex][1]);
                    }
                    FriezeAtlasRepeatGuard.protect(quad.view);
                    for (int vertex = 0; vertex < 4; vertex++) {
                        float u = quad.uv[vertex][0], v = quad.uv[vertex][1];
                        assertTrue(Float.isFinite(u) && Float.isFinite(v));
                        assertTrue(u >= -EPSILON && u <= 1 + EPSILON && v >= -EPSILON && v <= 1 + EPSILON,
                                "Actual frieze primitive escaped the sprite");
                        assertEquals(0, Math.IEEEremainder(u - expectedU[vertex], .5), EPSILON,
                                "Only a complete identical horizontal repeat may move");
                        assertEquals(expectedV[vertex], v, EPSILON);
                        assertEquals(expectedU[vertex] - expectedU[0], u - quad.uv[0][0], EPSILON,
                                "Primitive interpolation and texture density must survive the atlas guard");
                    }
                    checked++;
                }
            }
            for (String join : List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")) {
                assertTrue(components.stream().anyMatch(name -> name.startsWith(style + "/" + join + "_")),
                        style + "/" + join + " must exercise actual geometry");
            }
        }
        assertEquals(216, phases.size());
        assertTrue(checked > 1_900_000, "Check every nondegenerate shipped triangle in all four facings");
    }

    private static float expectedU(Direction face, BlockPos pos, float[] p) {
        int tile = switch (face) {
            case NORTH -> -pos.getX() - 1;
            case EAST -> -pos.getZ() - 1;
            case WEST -> pos.getZ();
            default -> pos.getX();
        };
        float local = switch (face) {
            case NORTH -> 1 - p[0]; case EAST -> 1 - p[2]; case WEST -> p[2]; default -> p[0];
        };
        return (Math.floorMod(tile, 6) + local) / 12;
    }

    private static float expectedV(Direction face, BlockPos pos, float[] p) {
        int tile = switch (face) { case UP -> pos.getZ(); case DOWN -> -pos.getZ() - 1; default -> -pos.getY(); };
        float local = switch (face) { case UP -> p[2]; case DOWN -> 1 - p[2]; default -> 1 - p[1]; };
        return (Math.floorMod(tile, 6) + local) / 6;
    }

    private static final class Triangle {
        final float[][] points = new float[4][3], uv = new float[4][2];
        Direction face;
        final MutableQuadView view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x", "y", "z" -> points[(int) args[0]]["xyz".indexOf(method.getName())];
                    case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                    case "hasNormal" -> false;
                    case "pos" -> { for (int axis = 0; axis < 3; axis++) points[(int) args[0]][axis] = (float) args[axis + 1]; yield proxy; }
                    case "uv" -> { uv[(int) args[0]][0] = (float) args[1]; uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                    case "nominalFace" -> { if (args == null || args.length == 0) yield face; face = (Direction) args[0]; yield proxy; }
                    case "cullFace" -> proxy;
                    default -> throw new AssertionError("Unexpected frieze edit: " + method.getName());
                });

        void set(ObjMeshData mesh, ObjMeshData.Face source, Direction nominal) {
            face = nominal;
            for (int vertex = 0; vertex < 4; vertex++) {
                var p = mesh.positions().get(source.vertices().get(Math.min(vertex, source.vertices().size() - 1)).positionIndex());
                points[vertex][0] = p.x(); points[vertex][1] = p.y(); points[vertex][2] = p.z();
            }
        }
    }

    private static Method method(String name, Class<?>... arguments) throws Exception {
        Method result = ObjMeshBakedModel.class.getDeclaredMethod(name, arguments);
        result.setAccessible(true);
        return result;
    }

    private static ResourceManager resources() {
        ResourcePack pack = (ResourcePack) Proxy.newProxyInstance(ResourcePack.class.getClassLoader(), new Class<?>[]{ResourcePack.class},
                (proxy, method, args) -> method.getName().equals("getName") ? "actual frieze source assets" : null);
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) throw new AssertionError(method.getName());
                    Identifier id = (Identifier) args[0];
                    Path path = Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
                    return Files.isRegularFile(path) ? Optional.of(new Resource(pack, () -> Files.newInputStream(path))) : Optional.empty();
                });
    }
}
