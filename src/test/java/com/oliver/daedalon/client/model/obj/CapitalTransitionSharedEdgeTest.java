package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalTransition;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Replays the production face emitter, including real loft diagonals and cell clipping. */
final class CapitalTransitionSharedEdgeTest {
    @Test void emittedStripDiagonalsAndBandJoinsKeepOneBasisWithoutChangingTheLightingFace() throws Exception {
        var emit = CapitalTransitionMesh.class.getDeclaredMethod("face", MeshBuilder[].class, int.class,
                double[].class, double[].class, double[].class, double[].class);
        emit.setAccessible(true);
        int stripsWithDifferentLightingFaces = 0, checked = 0;
        BlockPos anchor = new BlockPos(5, 83, -7);
        var material = WorldTexturePhase.blockSurface();
        for (var support : CapitalTransition.values()) {
            if (support == CapitalTransition.NONE) continue;
            for (var size : CapitalBlock.Size.values()) {
                int width = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
                var rings = CapitalTransitionMesh.rings(support, size);
                for (int i = 0; i < rings[0].length; i++) {
                    int next = (i + 1) % rings[0].length;
                    var cells = new ArrayList<List<CapturedQuad>>();
                    var builders = new MeshBuilder[width * width];
                    for (int cell = 0; cell < builders.length; cell++) {
                        var output = new ArrayList<CapturedQuad>(); cells.add(output); builders[cell] = builder(output);
                    }
                    for (int ring = 0; ring < 2; ring++) emit.invoke(null, builders, width,
                            rings[ring][i], rings[ring + 1][i], rings[ring + 1][next], rings[ring][next]);
                    double expectedArea = 0, emittedArea = 0;
                    for (int ring = 0; ring < 2; ring++) {
                        var a = rings[ring][i]; var b = rings[ring + 1][i];
                        var c = rings[ring + 1][next]; var d = rings[ring][next];
                        expectedArea += area(a, b, c) + area(a, c, d);
                    }
                    Map<String, float[]> shared = new HashMap<>();
                    var lightingFaces = new HashSet<Direction>();
                    for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) {
                        for (var quad : cells.get(x + width * z)) {
                            emittedArea += area(quad.xyz[0], quad.xyz[1], quad.xyz[2]);
                            lightingFaces.add(quad.lightingFace);
                            assertEquals(-1, quad.uv[0][1]);
                            int projectionId = (int) -quad.uv[0][0] - 1;
                            Direction projection = Direction.byId(projectionId);
                            assertTrue(projection.getAxis().isHorizontal(), "The necking and sloped loft use radial side planes");
                            for (int vertex = 0; vertex < 3; vertex++) for (int edge = 0; edge < 2; edge++)
                                assertTrue(CapitalProjection.sectorDistance(projection, edge,
                                        quad.xyz[vertex][0], quad.xyz[vertex][2], width * 0.5) >= -2e-6,
                                        "Every emitted UV patch must remain within its selected radial sector");
                            CapitalTransitionUvTransform.forCell(anchor, x, z).transform(quad.view);
                            for (int vertex = 0; vertex < 4; vertex++) {
                                String point = projectionId + ":" + Math.round(quad.xyz[vertex][0] * 1e6) + ":"
                                        + Math.round(quad.xyz[vertex][1] * 1e6) + ":" + Math.round(quad.xyz[vertex][2] * 1e6);
                                float[] uv = {material.mapU(quad.uv[vertex][0], 0), material.mapV(quad.uv[vertex][1])};
                                float[] previous = shared.putIfAbsent(point, uv);
                                if (previous != null) {
                                    assertEquals(0, Math.IEEEremainder(previous[0] - uv[0], 0.5), 1e-6,
                                            "A shared strip vertex changed horizontal material phase");
                                    assertEquals(0, Math.IEEEremainder(previous[1] - uv[1], 1), 1e-6,
                                            "A shared strip vertex changed vertical material phase");
                                }
                            }
                        }
                    }
                    assertEquals(expectedArea, emittedArea, 1e-6,
                            "UV sector/cell subdivision must preserve the approved rendered surface area");
                    if (lightingFaces.size() > 1) stripsWithDifferentLightingFaces++;
                    checked++;
                }
            }
        }
        assertTrue(checked > 400, "Replay every head/shaft strip and both sizes");
        assertTrue(stripsWithDifferentLightingFaces > 100,
                "The regression must exercise actual strips whose geometric lighting planes disagree");
    }

    @Test void collarAndCarvingSamplesUseTheSameRadialFieldRegardlessOfTheirLightingNormals() {
        var anchors = new ArrayList<BlockPos>();
        for (int phase = 0; phase < 6; phase++) anchors.add(new BlockPos(40 + phase, 83 + phase, 27 + phase));
        anchors.add(new BlockPos(-7, 13, 5));
        var planes = new HashSet<Direction>();
        int checked = 0;
        for (var support : CapitalTransition.values()) {
            if (support == CapitalTransition.NONE) continue;
            for (var size : CapitalBlock.Size.values()) {
                double centre = size == CapitalBlock.Size.DOUBLE ? 1 : 0.5;
                var rim = CapitalTransitionMesh.rings(support, size)[2];
                for (var point : rim) {
                    Direction plane = CapitalProjection.projectionPlane(point[0], point[2], centre);
                    planes.add(plane);
                    float[][] positions = new float[4][3];
                    for (int i = 0; i < 4; i++) for (int axis = 0; axis < 3; axis++) positions[i][axis] = (float) point[axis];
                    for (BlockPos anchor : anchors) {
                        var collar = new CapturedQuad(positions, new float[4][2], Direction.UP);
                        var carving = new CapturedQuad(positions, new float[4][2], Direction.NORTH);
                        CapitalTransitionUvTransform.markProjection(collar.view, plane);
                        CapitalTransitionUvTransform.markProjection(carving.view, plane);
                        int x = (int) Math.floor(point[0]), z = (int) Math.floor(point[2]);
                        var projection = CapitalTransitionUvTransform.forCell(anchor, x, 0, z);
                        projection.transform(collar.view); projection.transform(carving.view);
                        for (int i = 0; i < 4; i++) assertArrayEquals(collar.uv[i], carving.uv[i],
                                "The same overlap point and UV plane must not depend on the source mesh's lighting normal");
                        checked++;
                    }
                }
            }
        }
        assertEquals(2856, checked, "Compare all approved rim points, both sizes and every world repeat phase");
        assertEquals(new HashSet<>(CapitalProjection.SIDES), planes, "Exercise all four directional projection patches");
    }

    @Test void genuineFlatCapsKeepTheirOwnPlaneAndDirectionalCutsHaveDeterministicOwnership() {
        assertEquals(Direction.UP, CapitalProjection.genuineHorizontal(new double[]{0, 0, 0},
                new double[]{0, 0, 1}, new double[]{1, 0, 1}));
        assertEquals(Direction.DOWN, CapitalProjection.genuineHorizontal(new double[]{0, 0, 0},
                new double[]{1, 0, 0}, new double[]{1, 0, 1}));
        assertNull(CapitalProjection.genuineHorizontal(new double[]{0, 0, 0},
                new double[]{0, 0.1, 1}, new double[]{1, 0.1, 1}));
        assertEquals(Direction.EAST, CapitalProjection.projectionPlane(0.75, 0.75, 0.5));
        assertEquals(Direction.WEST, CapitalProjection.projectionPlane(0.25, 0.25, 0.5));
        assertTrue(CapitalProjection.onSectorBoundary(0.75, 0.75, 0.8, 0.8, 0.9, 0.9, 0.5));
    }

    @Test void upperCarvingCellsUseTheirActualYPhaseAndRemainCached() {
        BlockPos anchor = new BlockPos(-7, 12, 5);
        var quad = new CapturedQuad(new float[][]{{.2F, 1.1F, .3F}, {.2F, 1.2F, .3F},
                {.4F, 1.2F, .3F}, {.4F, 1.2F, .3F}}, new float[4][2], Direction.SOUTH);
        var transform = CapitalTransitionUvTransform.forCell(anchor, 0, 1, 0);
        assertSame(transform, CapitalTransitionUvTransform.forCell(anchor.add(6, 6, 6), 0, 1, 0));
        transform.transform(quad.view);
        assertEquals(5 + 1 - 0.1F, quad.uv[0][1], 1e-6);
        var tinyOverhang = new CapturedQuad(new float[][]{{.2F, 2.0000012F, .3F}, {.3F, 2.0000012F, .3F},
                {.3F, 2.0000012F, .4F}, {.3F, 2.0000012F, .4F}}, new float[4][2], Direction.NORTH);
        var overhangTransform = CapitalTransitionUvTransform.forCell(anchor, 0, 2, 0);
        assertSame(overhangTransform, CapitalTransitionUvTransform.forCell(anchor.add(6, 6, 6), 0, 2, 0));
        overhangTransform.transform(tinyOverhang.view);
        assertEquals(5 - (2.0000012F - 2), tinyOverhang.uv[0][1], 1e-6,
                "The existing Tuscan float overhang must keep its own texture cell without moving vertices");
        assertThrows(IllegalArgumentException.class, () -> CapitalTransitionUvTransform.forCell(anchor, 0, 3, 0));
    }

    private static MeshBuilder builder(List<CapturedQuad> output) {
        float[][] positions = new float[4][3], uv = new float[4][2];
        Direction[] face = {null};
        QuadEmitter emitter = (QuadEmitter) Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),
                new Class<?>[]{QuadEmitter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "pos" -> { for (int axis = 0; axis < 3; axis++) positions[(int) args[0]][axis] = (float) args[axis + 1]; yield proxy; }
                    case "uv" -> { uv[(int) args[0]][0] = (float) args[1]; uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                    case "normal", "color", "tag", "cullFace" -> proxy;
                    case "nominalFace" -> { face[0] = (Direction) args[0]; yield proxy; }
                    case "emit" -> { output.add(new CapturedQuad(positions, uv, face[0])); yield proxy; }
                    default -> throw new AssertionError("Unexpected production emitter operation: " + method.getName());
                });
        return (MeshBuilder) Proxy.newProxyInstance(MeshBuilder.class.getClassLoader(), new Class<?>[]{MeshBuilder.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEmitter")) return emitter;
                    throw new AssertionError("Unexpected production builder operation: " + method.getName());
                });
    }

    private static double area(double[] a, double[] b, double[] c) {
        return area(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]);
    }

    private static double area(float[] a, float[] b, float[] c) {
        return area(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]);
    }

    private static double area(double ax, double ay, double az, double bx, double by, double bz,
                               double cx, double cy, double cz) {
        double x = (by - ay) * (cz - az) - (bz - az) * (cy - ay);
        double y = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
        double z = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        return Math.sqrt(x * x + y * y + z * z) * 0.5;
    }

    private static final class CapturedQuad {
        final float[][] xyz = new float[4][3], uv = new float[4][2];
        final Direction lightingFace;
        final MutableQuadView view;

        CapturedQuad(float[][] positions, float[][] sourceUv, Direction lightingFace) {
            this.lightingFace = lightingFace;
            for (int i = 0; i < 4; i++) {
                System.arraycopy(positions[i], 0, xyz[i], 0, 3); System.arraycopy(sourceUv[i], 0, uv[i], 0, 2);
            }
            view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                    new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "x", "y", "z" -> xyz[(int) args[0]]["xyz".indexOf(method.getName())];
                        case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                        case "uv" -> { uv[(int) args[0]][0] = (float) args[1]; uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                        case "nominalFace" -> {
                            assertTrue(args == null || args.length == 0, "UV projection must preserve the geometric lighting face");
                            yield lightingFace;
                        }
                        default -> throw new AssertionError("UV correction must not mutate geometry, normals, lighting or tags: " + method.getName());
                    });
        }
    }
}
