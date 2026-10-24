package com.oliver.daedalon.client.model.obj;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
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

    @Test void cacheStartsEmptyAndHasOnlyEightGeometrySlots() throws Exception {
        Mesh source = (Mesh) Proxy.newProxyInstance(Mesh.class.getClassLoader(), new Class<?>[]{Mesh.class},
                (proxy, method, args) -> { throw new AssertionError("Constructor must not traverse or copy the source mesh"); });
        CapitalWorldMesh cache = new CapitalWorldMesh(source);
        assertEquals(0, cache.cachedVariantCount());
        var field = CapitalWorldMesh.class.getDeclaredField("variants"); field.setAccessible(true);
        assertEquals(8, ((AtomicReferenceArray<?>) field.get(cache)).length());
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
        report.put("cache_policy", "At most eight lazy variants per shared source geometry; no material, position or support keys");
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
        return new Source(points, triangles, scale);
    }

    private static InputStreamReader reader(String path) {
        var input = CapitalWorldMeshTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    private record Source(List<float[]> raw, List<int[]> triangles, float[] scale) {
        List<Vertex> transform(CapitalBlock.Size size, CapitalOrientation orientation) {
            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (float[] point : raw) for (int i = 0; i < 3; i++) { min[i] = Math.min(min[i], point[i]); max[i] = Math.max(max[i], point[i]); }
            float fit = 1F / Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
            float cx = (min[0] + max[0]) * .5F, cz = (min[2] + max[2]) * .5F;
            float multiple = size == CapitalBlock.Size.DOUBLE ? 2 : 1, centre = multiple * .5F;
            List<Vertex> result = new ArrayList<>();
            for (float[] p : raw) {
                float x = (p[0] - cx) * fit * scale[0] + .5F, y = (p[1] - min[1]) * fit * scale[1];
                float z = (p[2] - cz) * fit * scale[2] + .5F;
                x -= .5F; z -= .5F;
                result.add(Vertex.point(centre + multiple * (orientation.cosine * x - orientation.sine * z),
                        y * multiple, centre + multiple * (orientation.sine * x + orientation.cosine * z)));
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
