package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class LanternWorldUvTest {
    private static final double EPSILON = 2.0E-7;

    @Test void worldProjectionMatchesAllSixNativeCtmFacesAtNegativeAndLargePositions() {
        for (BlockPos origin : List.of(new BlockPos(-7, -8, -9),
                new BlockPos(30_000_000, -64, -30_000_000), new BlockPos(-30_000_000, 319, 30_000_000))) {
            double x = origin.getX() + .25, y = origin.getY() + .375, z = origin.getZ() + .75;
            for (Direction face : Direction.values()) {
                double expectedS = switch (face) {
                    case NORTH -> -x;
                    case EAST -> -z;
                    case WEST -> z;
                    default -> x;
                };
                double expectedT = switch (face) {
                    case UP -> z;
                    case DOWN -> -z;
                    default -> 1 - y;
                };
                assertEquals(expectedS, LanternWorldUv.s(face, origin, .25, .75), 0.0);
                assertEquals(expectedT, LanternWorldUv.t(face, origin, .375, .75), 0.0);
            }
        }
        assertEquals(-6, LanternWorldUv.origin(-.001, true));
        assertEquals(-1, LanternWorldUv.origin(-.001, false));
        assertEquals(-6, LanternWorldUv.origin(-6, true));
        assertEquals(6, LanternWorldUv.origin(6, true));
    }

    @Test void repeatedStoneCutsVerticalSeamsButKeepsItsDuplicatedHorizontalWindowContinuous() {
        List<LanternWorldUv.Vertex> source = triangle(5.8, 5.8);
        List<LanternWorldUv.Patch> patches = LanternWorldUv.split(source, true);
        assertEquals(2, patches.size());
        assertEquals(area(source), totalArea(patches), EPSILON);
        assertEquals(0, patches.get(0).uOrigin());
        assertEquals(0, patches.get(1).uOrigin());
        assertEquals(List.of(0.0, 6.0), patches.stream().map(LanternWorldUv.Patch::vOrigin).toList());
        assertPatches(patches);
        assertTrue(patches.stream().flatMap(p -> p.vertices().stream()).anyMatch(v -> v.s() > 6),
                "U crosses the duplicated period without a cut or a fold");
        List<LanternWorldUv.Vertex> seam = patches.stream().flatMap(p -> p.vertices().stream())
                .filter(v -> v.t() == 6).toList();
        assertEquals(4, seam.size());
        for (LanternWorldUv.Vertex vertex : seam) {
            assertTrue(seam.stream().anyMatch(other -> other != vertex
                    && other.x() == vertex.x() && other.y() == vertex.y() && other.z() == vertex.z()));
        }
    }

    @Test void plainMetalSplitsBothOneTileAxesWithoutLosingSurfaceArea() {
        List<LanternWorldUv.Vertex> source = List.of(vertex(0, 0, -.25, .75), vertex(1, 0, .25, .75),
                vertex(1, 1, .25, 1.25), vertex(0, 1, -.25, 1.25));
        List<LanternWorldUv.Patch> patches = LanternWorldUv.split(source, false);
        assertEquals(4, patches.size());
        assertEquals(1, totalArea(patches), EPSILON);
        assertPatches(patches);
        assertEquals(List.of(-1.0, 0.0), patches.stream().map(LanternWorldUv.Patch::uOrigin).distinct().toList());
        assertEquals(List.of(0.0, 1.0), patches.stream().map(LanternWorldUv.Patch::vOrigin).distinct().toList());
    }

    @Test void negativeAndHugeWorldPeriodsHaveTheSameLocalTextureAndClipping() {
        List<LanternWorldUv.Patch> reference = LanternWorldUv.split(triangle(5.8, 5.8), true);
        for (double offset : List.of(-30_000_006.0, -6.0, 6.0, 30_000_000.0)) {
            List<LanternWorldUv.Patch> actual = LanternWorldUv.split(triangle(offset + 5.8, offset + 5.8), true);
            assertEquals(reference.size(), actual.size());
            assertEquals(.5, totalArea(actual), EPSILON);
            assertPatches(actual);
            for (int i = 0; i < actual.size(); i++) {
                var expected = reference.get(i);var measured = actual.get(i);
                assertEquals(expected.vertices().size(), measured.vertices().size());
                for (int j = 0; j < measured.vertices().size(); j++) {
                    assertEquals(expected.u(expected.vertices().get(j)), measured.u(measured.vertices().get(j)), EPSILON);
                    assertEquals(expected.v(expected.vertices().get(j)), measured.v(measured.vertices().get(j)), EPSILON);
                }
            }
        }
    }

    @Test void exactBoundaryVerticesAndTriangleGhostCornersNeverEmitEmptyPatches() {
        var a = vertex(0, 0, -.25, 5.5);var b = vertex(1, 0, .25, 5.5);var c = vertex(0, 1, -.25, 6);
        var source = List.of(a, b, c, c);
        var patches = LanternWorldUv.split(source, true);
        assertEquals(1, patches.size());
        assertEquals(.5, totalArea(patches), EPSILON);
        assertPatches(patches);
        assertTrue(LanternWorldUv.split(List.of(a, a, a), true).isEmpty());
    }

    @Test void edgeInterpolationKeepsUnitNormalsAndLinearWorldCoordinates() {
        var a = new LanternWorldUv.Vertex(0, 0, 0, 1, 0, 0, -2, 5);
        var b = new LanternWorldUv.Vertex(2, 4, 6, 0, 1, 0, 2, 7);
        var middle = a.lerp(b, .5);
        assertEquals(1, middle.x());assertEquals(2, middle.y());assertEquals(3, middle.z());
        assertEquals(0, middle.s());assertEquals(6, middle.t());
        assertEquals(Math.sqrt(.5), middle.nx(), EPSILON);assertEquals(Math.sqrt(.5), middle.ny(), EPSILON);
        assertUnit(middle);
        assertUnit(a.lerp(new LanternWorldUv.Vertex(1, 0, 0, -1, 0, 0, 0, 0), .5));
    }

    @Test void bothSizesAllMountsAndFourFacingsRetainOneWorldTilePerMetre() {
        BlockPos origin = new BlockPos(-7, -5, 11);
        for (float size : new float[]{.75F, 1.25F}) for (int mount = 0; mount < 3; mount++) {
            for (int steps = 0; steps < 4; steps++) {
                List<LanternWorldUv.Vertex> surface = new ArrayList<>();
                Direction face = switch (steps) {case 1 -> Direction.WEST;case 2 -> Direction.NORTH;
                    case 3 -> Direction.EAST;default -> Direction.SOUTH;};
                for (float[] point : new float[][]{{-.2F,.25F}, {.2F,.25F}, {.2F,.75F}, {-.2F,.75F}}) {
                    float px = point[0] * size, pz = .2F * size + (mount == 1 ? -.5F : 0);
                    float x = .5F + rotateX(px, pz, steps), z = .5F + rotateZ(px, pz, steps);
                    float yOffset = mount == 0 ? 0 : mount == 1 ? .5F - size / 2 : .5F - size;
                    float y = point[1] * size + yOffset;
                    surface.add(new LanternWorldUv.Vertex(x, y, z, face.getOffsetX(), 0, face.getOffsetZ(),
                            LanternWorldUv.s(face, origin, x, z), LanternWorldUv.t(face, origin, y, z)));
                }
                assertEquals(.4 * size, Math.abs(surface.get(1).s() - surface.get(0).s()), EPSILON);
                assertEquals(.5 * size, Math.abs(surface.get(2).t() - surface.get(1).t()), EPSILON);
                for (boolean repeat : new boolean[]{true, false}) {
                    var patches = LanternWorldUv.split(surface, repeat);
                    assertEquals(area(surface), totalArea(patches), EPSILON);
                    assertPatches(patches);
                }
            }
        }
    }

    private static List<LanternWorldUv.Vertex> triangle(double s, double t) {
        return List.of(vertex(0, 0, s, t), vertex(1, 0, s + .4, t), vertex(0, 1, s, t + .4));
    }

    private static LanternWorldUv.Vertex vertex(float x, float y, double s, double t) {
        return new LanternWorldUv.Vertex(x, y, 0, 0, 0, 1, s, t);
    }

    private static void assertPatches(List<LanternWorldUv.Patch> patches) {
        assertFalse(patches.isEmpty());
        for (var patch : patches) {
            assertTrue(area(patch.vertices()) > 1.0E-12);
            for (var vertex : patch.vertices()) {
                assertTrue(patch.u(vertex) >= 0 && patch.u(vertex) <= 1);
                assertTrue(patch.v(vertex) >= 0 && patch.v(vertex) <= 1);
                assertUnit(vertex);
                var first = patch.vertices().get(0);
                assertEquals(vertex.s() - first.s(), (patch.u(vertex) - patch.u(first))
                        * (patch.repeatSheet() ? 12 : 1), 1.0E-6);
                assertEquals(vertex.t() - first.t(), (patch.v(vertex) - patch.v(first))
                        * (patch.repeatSheet() ? 6 : 1), 1.0E-6);
            }
        }
    }

    private static void assertUnit(LanternWorldUv.Vertex v) {
        assertEquals(1, Math.sqrt(v.nx() * v.nx() + v.ny() * v.ny() + v.nz() * v.nz()), EPSILON);
    }

    private static double totalArea(List<LanternWorldUv.Patch> patches) {
        return patches.stream().mapToDouble(p -> area(p.vertices())).sum();
    }

    private static double area(List<LanternWorldUv.Vertex> polygon) {
        double total = 0;var a = polygon.get(0);
        for (int i = 1; i < polygon.size() - 1; i++) {
            var b = polygon.get(i);var c = polygon.get(i + 1);
            double bx = b.x() - a.x(), by = b.y() - a.y(), bz = b.z() - a.z();
            double cx = c.x() - a.x(), cy = c.y() - a.y(), cz = c.z() - a.z();
            double x = by * cz - bz * cy, y = bz * cx - bx * cz, z = bx * cy - by * cx;
            total += Math.sqrt(x * x + y * y + z * z) / 2;
        }
        return total;
    }

    private static float rotateX(float x, float z, int steps) {
        return switch (steps) {case 1 -> -z;case 2 -> -x;case 3 -> z;default -> x;};
    }

    private static float rotateZ(float x, float z, int steps) {
        return switch (steps) {case 1 -> x;case 2 -> -z;case 3 -> -x;default -> z;};
    }
}
