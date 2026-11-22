package com.oliver.daedalon.client.model.obj;

import com.google.gson.JsonParser;
import com.oliver.daedalon.block.PanelBlock;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Actual prepared panel triangles through the existing wall/ceiling rendering route. */
final class PanelGeometryTest {
    private static final float EPSILON = 3.0e-6F;
    private static final Path MANIFEST = Path.of("docs/evidence/coffered-panels-source.json");
    private static final Class<?> PROJECTOR = nested("UvProjector");
    private static final Method NORMAL = method(ObjMeshBakedModel.class, "geometricNormal", List.class, List.class);
    private static final Method CLOSEST = method(ObjMeshBakedModel.class, "closestAxis", ObjMeshData.Vec3.class);
    private static final Method PROJECTOR_CREATE = method(PROJECTOR, "create", ObjMeshDefinition.class,
            List.class, ObjMeshData.Bounds.class);
    private static final Method PROJECT_UVS = method(ObjMeshBakedModel.class, "projectUvs", float[].class,
            float[].class, List.class, List.class, String.class, ObjMeshData.Vec3.class, Direction.class, PROJECTOR);
    private static final List<Phase> PHASES = List.of(new Phase(new BlockPos(-7, 83, 11), 5),
            new Phase(new BlockPos(-6, 83, 11), 0), new Phase(new BlockPos(-5, 83, 11), 1),
            new Phase(new BlockPos(-4, 83, 11), 2), new Phase(new BlockPos(-3, 83, 11), 3),
            new Phase(new BlockPos(29_999_994, -64, -30_000_000), 4));

    private record Model(String meshName, double depth, double[][] bounds, boolean supplied) { }
    private record Phase(BlockPos pos, int column) { }

    @Test void preparedGeometryKeepsCollisionNormalsAndTextureDensityInEverySizeFacingAndMount() throws Exception {
        ResourceManager resources = resources();
        var layout = WorldTexturePhase.detailedSurface();
        Triangle quad = new Triangle();
        int checkedModels = 0;
        for (Model model : models()) {
            var definition = ObjMeshDefinition.load(resources,
                    new Identifier("daedalon", "models/mesh/" + model.meshName + ".json"));
            assertTrue(definition.forceUvProjection());
            assertTrue(definition.faceOrientedUvs());
            assertFalse(definition.smoothNormals(), "Preserve the measured prepared corner normals");
            assertFalse(definition.fitToBlock(), "The prepared envelope must not be refitted at runtime");
            assertEquals(6, definition.boxProjectionSpan());
            assertEquals(new ObjMeshDefinition.Vec3(1, 1, 1), definition.scale());
            assertEquals(new ObjMeshDefinition.Vec3(0, 0, 0), definition.translate());
            ObjMeshData mesh = ObjMeshParser.parse(resources, definition);
            assertTrue(mesh.materialTextures().isEmpty(), "All finishes must use the shared variant surface");
            assertTrue(mesh.sourceTriangleCount() > 0);
            if (model.supplied) assertTrue(mesh.sourceTriangleCount() <= 6000, "New panels exceed the agreed triangle cap");
            assertBounds(model, mesh.originalBounds());
            Object projector = PROJECTOR_CREATE.invoke(null, definition, mesh.positions(), mesh.originalBounds());
            int checkedFaces = 0;
            for (var face : mesh.faces()) {
                assertEquals("none", face.material());
                assertTrue(face.isTriangle());
                var normal = (ObjMeshData.Vec3) NORMAL.invoke(null, mesh.positions(), face.vertices());
                if (normal == null) continue;
                Direction nominal = (Direction) CLOSEST.invoke(null, normal);
                float[] u = new float[3], v = new float[3];
                PROJECT_UVS.invoke(null, u, v, mesh.positions(), face.vertices(), face.material(), normal, nominal, projector);
                quad.set(mesh, face, nominal, u, v);
                float[][] sourceNormals = quad.copyNormals();
                double[] sourceNormal = quad.geometricNormal();
                boolean stableNormal = quad.normalIsStable();
                for (int size = 1; size <= 3; size++) for (boolean ceiling : new boolean[]{false, true}) {
                    for (int turn = 0; turn < 4; turn++) {
                        quad.set(mesh, face, nominal, u, v);
                        var mapping = new PanelBlock.Transform(size, ceiling, turn);
                        Direction front = ceiling ? Direction.DOWN
                                : List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST).get(turn);
                        assertEquals(front, mapping.direction(Direction.SOUTH));
                        new PanelMeshTransform(mapping).transform(quad.view);
                        double[] transformedNormal = quad.geometricNormal();
                        for (int vertex = 0; vertex < 3; vertex++) {
                            assertContained(model, quad.points[vertex], size, front);
                            assertEquals(1, length(quad.normals[vertex]), 3.0e-5,
                                    "A size or mount changed the imported normal length");
                            // Imported tiny slivers can collapse under float32
                            // translation; retain their unit-normal/UV checks.
                            if (stableNormal) assertEquals(dot(sourceNormals[vertex], sourceNormal),
                                    dot(quad.normals[vertex], transformedNormal), 3.0e-3,
                                    "The rendered normal no longer agrees with the actual transformed surface");
                        }
                        // Each position chooses a concrete known repeat column in
                        // the existing panel route. It does not use frieze CTM.
                        for (Phase phase : PHASES) {
                            assertEquals(phase.column, layout.index(phase.pos.getX(), phase.pos.getY(), phase.pos.getZ()));
                            for (int vertex = 0; vertex < 3; vertex++) {
                                float mappedU = layout.mapU(quad.uv[vertex][0], phase.column);
                                float mappedV = layout.mapV(quad.uv[vertex][1]);
                                assertTrue(mappedU >= -EPSILON && mappedU <= 1 + EPSILON
                                        && mappedV >= -EPSILON && mappedV <= 1 + EPSILON,
                                        "An actual carved surface samples outside the material sprite");
                                assertEquals(phase.column / 12F, mappedU - layout.mapU(quad.uv[vertex][0], 0), EPSILON);
                                if (vertex == 0) continue;
                                double sourceTexels = Math.hypot((u[vertex] - u[0]) * 6, (v[vertex] - v[0]) * 6);
                                double placedTexels = Math.hypot((mappedU - layout.mapU(quad.uv[0][0], phase.column)) * 12,
                                        (mappedV - layout.mapV(quad.uv[0][1])) * 6);
                                assertEquals(sourceTexels * size, placedTexels, 5.0e-6,
                                        "Resizing or changing world phase stretched the actual surface texture");
                            }
                        }
                    }
                }
                checkedFaces++;
            }
            assertTrue(checkedFaces > mesh.sourceTriangleCount() * .99, "Nearly all prepared geometry must be exercised");
            checkedModels++;
        }
        assertTrue(checkedModels >= 1, "Always retain the published Gothic panel regression");
    }

    private static List<Model> models() throws Exception {
        var result = new ArrayList<Model>();
        result.add(new Model("gothic_panel", PanelBlock.DEPTH, null, false));
        if (Files.isRegularFile(MANIFEST)) {
            var names = new HashSet<String>();
            var styles = new HashSet<String>();
            for (var entry : JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject().getAsJsonArray("models")) {
                var model = entry.getAsJsonObject();
                String name = model.get("mesh_name").getAsString();
                assertTrue(names.add(name), "One shared mesh is expected per new style");
                assertTrue(styles.add(model.get("style").getAsString()), "A style must not allocate profiles per material");
                double[][] bounds = new double[2][3];
                for (int end = 0; end < 2; end++) for (int axis = 0; axis < 3; axis++) {
                    bounds[end][axis] = model.getAsJsonArray("bounds").get(end).getAsJsonArray().get(axis).getAsDouble();
                }
                result.add(new Model(name, model.get("collision_depth").getAsDouble(), bounds, true));
            }
        }
        return result;
    }

    private static void assertBounds(Model model, ObjMeshData.Bounds bounds) {
        float[] low = {bounds.minX(), bounds.minY(), bounds.minZ()}, high = {bounds.maxX(), bounds.maxY(), bounds.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            assertTrue(low[axis] >= -EPSILON);
            assertTrue(high[axis] <= (axis == 2 ? model.depth : 1) + EPSILON,
                    "Actual panel geometry exceeds its measured collision profile");
            if (model.bounds != null) {
                assertEquals(model.bounds[0][axis], low[axis], EPSILON);
                assertEquals(model.bounds[1][axis], high[axis], EPSILON);
            }
        }
        assertEquals(0, bounds.minZ(), EPSILON, "The panel's support plane must remain flush");
    }

    private static void assertContained(Model model, float[] point, int size, Direction normal) {
        for (int axis = 0; axis < 3; axis++) {
            double low = .5 - size / 2.0, high = .5 + size / 2.0;
            if (axis == normal.getAxis().ordinal()) {
                boolean positive = normal.getDirection() == Direction.AxisDirection.POSITIVE;
                low = positive ? 0 : 1 - model.depth * size;
                high = positive ? model.depth * size : 1;
            }
            assertTrue(point[axis] >= low - EPSILON && point[axis] <= high + EPSILON,
                    "Actual wall/ceiling panel geometry escapes the cached collision envelope");
        }
    }

    private static final class Triangle {
        final float[][] points = new float[4][3], normals = new float[4][3], uv = new float[4][2];
        Direction face;
        final MutableQuadView view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x", "y", "z" -> points[(int) args[0]]["xyz".indexOf(method.getName())];
                    case "normalX", "normalY", "normalZ" -> normals[(int) args[0]]["XYZ".indexOf(method.getName().charAt(6))];
                    case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                    case "hasNormal" -> true;
                    case "pos", "normal", "uv" -> {
                        float[] target = (method.getName().equals("pos") ? points : method.getName().equals("normal") ? normals : uv)[(int) args[0]];
                        for (int axis = 0; axis < target.length; axis++) target[axis] = (float) args[axis + 1];
                        yield proxy;
                    }
                    case "nominalFace" -> { if (args == null || args.length == 0) yield face; face = (Direction) args[0]; yield proxy; }
                    case "cullFace" -> proxy;
                    default -> throw new AssertionError("Unexpected panel edit: " + method.getName());
                });

        void set(ObjMeshData mesh, ObjMeshData.Face source, Direction nominal, float[] u, float[] v) {
            face = nominal;
            for (int vertex = 0; vertex < 4; vertex++) {
                int index = Math.min(vertex, 2);
                var ref = source.vertices().get(index);
                var p = mesh.positions().get(ref.positionIndex());
                assertTrue(ref.normalIndex() >= 0, "A prepared surface lost its imported normal");
                var n = mesh.normals().get(ref.normalIndex());
                points[vertex][0] = p.x(); points[vertex][1] = p.y(); points[vertex][2] = p.z();
                normals[vertex][0] = n.x(); normals[vertex][1] = n.y(); normals[vertex][2] = n.z();
                uv[vertex][0] = u[index]; uv[vertex][1] = v[index];
            }
        }

        float[][] copyNormals() { return new float[][]{normals[0].clone(), normals[1].clone(), normals[2].clone()}; }
        boolean normalIsStable() {
            double[] a = new double[3], b = new double[3];
            for (int i = 0; i < 3; i++) { a[i] = (double) points[1][i] - points[0][i]; b[i] = (double) points[2][i] - points[0][i]; }
            double nx = a[1] * b[2] - a[2] * b[1], ny = a[2] * b[0] - a[0] * b[2], nz = a[0] * b[1] - a[1] * b[0];
            double longest = Math.max(Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]),
                    Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]));
            return Math.sqrt(nx * nx + ny * ny + nz * nz) > longest * 1.0e-4;
        }
        double[] geometricNormal() {
            double[] a = new double[3], b = new double[3];
            for (int i = 0; i < 3; i++) { a[i] = (double) points[1][i] - points[0][i]; b[i] = (double) points[2][i] - points[0][i]; }
            double[] n = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
            double length = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            for (int i = 0; i < 3; i++) n[i] /= length;
            return n;
        }
    }

    private static double length(float[] vector) { return Math.sqrt(dot(vector, new double[]{vector[0], vector[1], vector[2]})); }
    private static double dot(float[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static Class<?> nested(String name) {
        try { return Class.forName(ObjMeshBakedModel.class.getName() + "$" + name); }
        catch (ClassNotFoundException exception) { throw new AssertionError(exception); }
    }
    private static Method method(Class<?> owner, String name, Class<?>... arguments) {
        try { Method result = owner.getDeclaredMethod(name, arguments); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static ResourceManager resources() {
        ResourcePack pack = (ResourcePack) Proxy.newProxyInstance(ResourcePack.class.getClassLoader(), new Class<?>[]{ResourcePack.class},
                (proxy, method, args) -> method.getName().equals("getName") ? "actual panel source assets" : null);
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getResource")) throw new AssertionError(method.getName());
                    Identifier id = (Identifier) args[0];
                    Path path = Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
                    return Files.isRegularFile(path) ? Optional.of(new Resource(pack, () -> Files.newInputStream(path))) : Optional.empty();
                });
    }
}
