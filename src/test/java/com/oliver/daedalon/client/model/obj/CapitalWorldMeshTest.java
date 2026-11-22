package com.oliver.daedalon.client.model.obj;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.CapitalTransition;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.oliver.daedalon.client.model.obj.CapitalWorldMesh.*;
import static org.junit.jupiter.api.Assertions.*;

final class CapitalWorldMeshTest {
    @Test void clippingPreservesAreaSurfaceAndLinearNormals() {
        Vertex a = new Vertex(-.2, .2, .1, 1, 0, 0, -1, 0);
        Vertex b = new Vertex(1.7, 1.8, .8, 0, 1, 0, -1, 0);
        Vertex c = new Vertex(.1, .4, 1.8, 0, 0, 1, -1, 0);
        var patches = splitTriangle(a, b, c, 1);
        assertTrue(patches.size() > 4);
        assertEquals(Math.sqrt(areaSquared(a, b, c)) * .5,
                patches.stream().mapToDouble(patch -> polygonArea(patch.vertices())).sum(), 1e-12);
        for (Patch patch : patches) for (Vertex vertex : patch.vertices()) {
            double[] weights = barycentric(a, b, c, vertex);
            assertEquals(vertex.x(), weights[0] * a.x() + weights[1] * b.x() + weights[2] * c.x(), 1e-12);
            assertEquals(vertex.y(), weights[0] * a.y() + weights[1] * b.y() + weights[2] * c.y(), 1e-12);
            assertEquals(weights[0], vertex.nx(), 1e-12);
            assertEquals(weights[1], vertex.ny(), 1e-12);
            assertEquals(weights[2], vertex.nz(), 1e-12);
            assertEquals(-1, vertex.color()); assertEquals(0, vertex.lightmap());
            assertCellContains(patch, vertex);
        }
    }

    @Test void integerAndSectorPlaneTrianglesHaveOneOwnerWithoutDuplicatedArea() {
        for (Vertex[] triangle : new Vertex[][]{
                {Vertex.point(1, .1, .1), Vertex.point(1, 1.8, .2), Vertex.point(1, .2, 1.8)},
                {Vertex.point(-.2, .1, -.2), Vertex.point(1.8, .1, 1.8), Vertex.point(.4, 1.8, .4)},
                {Vertex.point(.1, 1, .1), Vertex.point(1.8, 1, .2), Vertex.point(.3, 1, 1.8)}}) {
            List<Patch> patches = splitTriangle(triangle[0], triangle[1], triangle[2], 1);
            assertEquals(Math.sqrt(areaSquared(triangle[0], triangle[1], triangle[2])) * .5,
                    patches.stream().mapToDouble(patch -> polygonArea(patch.vertices())).sum(), 1e-12);
            for (Patch patch : patches) for (Vertex vertex : patch.vertices()) assertCellContains(patch, vertex);
        }
    }

    @Test void genuinelyHorizontalFacesKeepTheirOwnWorldPlaneAcrossEveryRadialSector() {
        Vertex a = Vertex.point(-.2, .4, -.2), b = Vertex.point(1.8, .4, .1), c = Vertex.point(.4, .4, 1.8);
        Direction expected = CapitalProjection.genuineHorizontal(a.point(), b.point(), c.point());
        assertNotNull(expected);
        for (Patch patch : splitTriangle(a, b, c, .5)) assertEquals(expected, patch.projection());
    }

    @Test void normalsAreNotRenormalizedAndPackedVertexChannelsInterpolateSeparately() {
        Vertex a = new Vertex(0, 0, 0, 1, 0, 0, 0xff000000, 0);
        Vertex b = new Vertex(1, 1, 1, 0, 1, 0, 0xffffffff, 0x00f000f0);
        Vertex middle = a.interpolate(b, .5);
        assertEquals(.5, middle.nx()); assertEquals(.5, middle.ny()); assertEquals(0, middle.nz());
        assertEquals(0xff808080, middle.color()); assertEquals(0x00780078, middle.lightmap());
    }

    @Test void floatCollapsedSplitFragmentsAreDetectedBeforeMeshEmission() {
        Vertex a = Vertex.point(1, 0, 1), b = Vertex.point(1 + 1e-10, 0, 1), c = Vertex.point(1, 1e-10, 1);
        assertTrue(areaSquared(a, b, c) > 0);
        assertEquals(0, floatAreaSquared(a, b, c));
    }

    @Test void cacheStartsEmptyAndHasOnlyFortySupportSizeOrientationSlots() throws Exception {
        Mesh source = (Mesh) Proxy.newProxyInstance(Mesh.class.getClassLoader(), new Class<?>[]{Mesh.class},
                (proxy, method, args) -> { throw new AssertionError("Constructor must not traverse or copy the source mesh"); });
        CapitalWorldMesh cache = new CapitalWorldMesh(source);
        assertEquals(0, cache.cachedVariantCount());
        var field = CapitalWorldMesh.class.getDeclaredField("variants"); field.setAccessible(true);
        assertEquals(40, ((AtomicReferenceArray<?>) field.get(cache)).length());
        assertEquals(CapitalTransition.values().length * CapitalBlock.Size.values().length
                * CapitalOrientation.values().length, ((AtomicReferenceArray<?>) field.get(cache)).length());
    }

    @Test void widestEnlargedCapitalKeepsItsTopAndAllPhysicalCellsInsideTheProjectorDomain() throws Exception {
        Source source = source("greek_ionic");
        var size = CapitalBlock.Size.DOUBLE;
        List<Vertex> points = source.transform(size, CapitalOrientation.DIAGONAL, 2.5F);
        assertEquals(2, points.stream().mapToDouble(Vertex::y).max().orElseThrow(), 2e-5);
        assertTrue(points.stream().mapToDouble(Vertex::y).min().orElseThrow() < -2);
        java.util.Set<Cell> cells = new java.util.HashSet<>();
        double before = 0, after = 0;
        for (int[] indices : source.triangles) {
            Vertex a = points.get(indices[0]), b = points.get(indices[1]), c = points.get(indices[2]);
            before += Math.sqrt(areaSquared(a, b, c)) * .5;
            for (Patch patch : splitTriangle(a, b, c, 1)) {
                after += polygonArea(patch.vertices());
                cells.add(patch.cell());
                for (Vertex point : patch.vertices()) assertCellContains(patch, point);
            }
        }
        assertEquals(before, after, before * 1e-10);
        assertTrue(cells.stream().anyMatch(cell -> cell.y() < 0), "The audit must reach below the capital cell");
        assertTrue(cells.stream().anyMatch(cell -> cell.x() < -1 || cell.x() > 2), "The audit must reach the wider overhang domain");
        for (Cell cell : cells) assertNotNull(CapitalTransitionUvTransform.forCell(
                BlockPos.ORIGIN, cell.x(), cell.y(), cell.z()), "Enlarged real mesh cell " + cell);
    }

    @Test void realCapitalTrianglesKeepTheirSurfaceWithBoundedSplitGrowth() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalQuads = 0; double discardedArea = 0, originalArea = 0;
        for (String style : List.of("byzantine", "corinthian", "gothic", "greek_ionic", "roman_composite", "tuscan")) {
            Source source = source(style);
            List<Map<String, Object>> variants = new ArrayList<>();
            for (CapitalBlock.Size size : CapitalBlock.Size.values()) for (CapitalOrientation orientation : CapitalOrientation.values()) {
                List<Vertex> points = source.transform(size, orientation);
                int emitted = 0, collapsed = 0; double before = 0, after = 0, tiny = 0;
                java.util.Set<Cell> cells = new java.util.HashSet<>();
                for (int[] indices : source.triangles) {
                    Vertex a = points.get(indices[0]), b = points.get(indices[1]), c = points.get(indices[2]);
                    before += Math.sqrt(areaSquared(a, b, c)) * .5;
                    for (Patch patch : splitTriangle(a, b, c, size == CapitalBlock.Size.DOUBLE ? 1 : .5)) {
                        after += polygonArea(patch.vertices()); cells.add(patch.cell());
                        assertTrue(patch.cell().x() >= -1 && patch.cell().x() <= 2 && patch.cell().z() >= -1 && patch.cell().z() <= 2
                                && patch.cell().y() >= 0 && patch.cell().y() <= 2, "Projector cell cache bounds: " + style + patch.cell());
                        for (int i = 1; i + 1 < patch.vertices().size(); i++) {
                            Vertex p = patch.vertices().get(0), q = patch.vertices().get(i), r = patch.vertices().get(i + 1);
                            double area = Math.sqrt(areaSquared(p, q, r)) * .5;
                            if (area == 0) continue;
                            if (floatAreaSquared(p, q, r) == 0) { collapsed++; tiny += area; }
                            else emitted++;
                        }
                    }
                }
                assertEquals(before, after, Math.max(1e-10, before * 1e-10), style + " surface changed");
                assertTrue(tiny < Math.max(1e-10, before * 1e-8), style + " dropped real surface area");
                assertTrue(emitted < source.triangles.size() * 3, style + " excessive split growth");
                for (Cell cell : cells) assertNotNull(CapitalTransitionUvTransform.forCell(
                        BlockPos.ORIGIN, cell.x(), cell.y(), cell.z()), style + " production projector rejected a real cell");
                totalQuads += emitted; discardedArea += tiny; originalArea += before;
                variants.add(Map.of("size", size.asString(), "orientation", orientation.asString(),
                        "source_triangles", source.triangles.size(), "emitted_triangles", emitted, "cells", cells.size(),
                        "discarded_float_fragments", collapsed, "discarded_area", tiny,
                        "estimated_vertex_bytes", (long) emitted * 144));
            }
            report.put(style, variants);
        }
        report.put("all_48_variant_quads", totalQuads);
        report.put("all_48_variant_estimated_vertex_bytes", totalQuads * 144);
        report.put("discarded_area", discardedArea);
        report.put("source_surface_area", originalArea);
        report.put("cache_policy", "At most forty lazy support/size/orientation variants per shared source geometry; no material or position keys");
        Path output = Path.of("build/reports/prepublication/capital-world-mesh-audit.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }

    private static Source source(String style) throws Exception {
        String base = "assets/daedalon/models/mesh/capital_" + style;
        var document = JsonParser.parseReader(reader(base + ".json")).getAsJsonObject();
        List<float[]> points = new ArrayList<>(); List<int[]> triangles = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(reader(base + ".obj"))) {
            for (String line; (line = input.readLine()) != null;) {
                String[] fields = line.strip().split("\\s+");
                if (fields[0].equals("v")) points.add(new float[]{Float.parseFloat(fields[1]), Float.parseFloat(fields[2]), Float.parseFloat(fields[3])});
                if (fields[0].equals("f")) for (int i = 2; i + 1 < fields.length; i++) triangles.add(new int[]{
                        Integer.parseInt(fields[1].split("/")[0]) - 1, Integer.parseInt(fields[i].split("/")[0]) - 1,
                        Integer.parseInt(fields[i + 1].split("/")[0]) - 1});
            }
        }
        float[] scale = new float[3]; for (int i = 0; i < 3; i++) scale[i] = document.getAsJsonArray("scale").get(i).getAsFloat();
        float[] translate = new float[3];
        for (int i = 0; i < 3; i++) translate[i] = document.getAsJsonArray("translate").get(i).getAsFloat();
        return new Source(points, triangles, scale, translate);
    }

    private static InputStreamReader reader(String path) {
        var input = CapitalWorldMeshTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    private record Source(List<float[]> raw, List<int[]> triangles, float[] scale, float[] translate) {
        List<Vertex> transform(CapitalBlock.Size size, CapitalOrientation orientation) {
            return transform(size, orientation, 1.0F);
        }

        List<Vertex> transform(CapitalBlock.Size size, CapitalOrientation orientation, float fitScale) {
            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (float[] point : raw) for (int i = 0; i < 3; i++) { min[i] = Math.min(min[i], point[i]); max[i] = Math.max(max[i], point[i]); }
            float fit = 1F / Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
            float cx = (min[0] + max[0]) * .5F, cz = (min[2] + max[2]) * .5F;
            float[][] placed = new float[4][3];
            MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                    new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "x", "y", "z" -> placed[(int) args[0]]["xyz".indexOf(method.getName())];
                        case "hasNormal" -> false;
                        case "pos" -> {
                            for (int axis = 0; axis < 3; axis++) placed[(int) args[0]][axis] = (float) args[axis + 1];
                            yield proxy;
                        }
                        case "cullFace", "nominalFace" -> args == null || args.length == 0 ? Direction.UP : proxy;
                        default -> throw new AssertionError("Unexpected placed geometry operation: " + method.getName());
                    });
            var transform = CapitalOrientationTransform.forSize(orientation, size, fitScale);
            List<Vertex> result = new ArrayList<>();
            for (float[] p : raw) {
                float x = (p[0] - cx) * fit * scale[0] + .5F + translate[0];
                float y = (p[1] - min[1]) * fit * scale[1] + translate[1];
                float z = (p[2] - cz) * fit * scale[2] + .5F + translate[2];
                for (int vertex = 0; vertex < 4; vertex++) {
                    placed[vertex][0] = x; placed[vertex][1] = y; placed[vertex][2] = z;
                }
                if (transform != null) transform.transform(quad);
                result.add(Vertex.point(placed[0][0], placed[0][1], placed[0][2]));
            }
            return result;
        }
    }

    private static void assertCellContains(Patch patch, Vertex point) {
        assertTrue(point.x() >= patch.cell().x() - 1e-10 && point.x() <= patch.cell().x() + 1 + 1e-10);
        assertTrue(point.y() >= patch.cell().y() - 1e-10 && point.y() <= patch.cell().y() + 1 + 1e-10);
        assertTrue(point.z() >= patch.cell().z() - 1e-10 && point.z() <= patch.cell().z() + 1 + 1e-10);
    }

    private static double[] barycentric(Vertex a, Vertex b, Vertex c, Vertex point) {
        double[] v0 = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()}, v1 = {c.x() - a.x(), c.y() - a.y(), c.z() - a.z()};
        double[] v2 = {point.x() - a.x(), point.y() - a.y(), point.z() - a.z()};
        double d00 = dot(v0, v0), d01 = dot(v0, v1), d11 = dot(v1, v1), d20 = dot(v2, v0), d21 = dot(v2, v1);
        double denom = d00 * d11 - d01 * d01;
        double v = (d11 * d20 - d01 * d21) / denom, w = (d00 * d21 - d01 * d20) / denom;
        return new double[]{1 - v - w, v, w};
    }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
}
