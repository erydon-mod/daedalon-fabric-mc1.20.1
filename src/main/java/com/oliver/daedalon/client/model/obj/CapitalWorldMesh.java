package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** World-only carving geometry, shared across every material and placed position. */
final class CapitalWorldMesh {
    private static final int ORIENTATIONS = CapitalOrientation.values().length;
    private final Mesh source;
    private final AtomicReferenceArray<Variant> variants = new AtomicReferenceArray<>(
            CapitalBlock.Size.values().length * ORIENTATIONS);

    CapitalWorldMesh(Mesh source) {
        this.source = source;
    }

    void emit(CapitalOrientation orientation, CapitalBlock.Size size, BlockPos anchor, RenderContext context) {
        for (CellMesh cell : variant(orientation, size).cells) {
            context.pushTransform(CapitalTransitionUvTransform.forCell(anchor, cell.cell.x, cell.cell.y, cell.cell.z));
            try { cell.mesh.outputTo(context.getEmitter()); }
            finally { context.popTransform(); }
        }
    }

    Stats stats(CapitalOrientation orientation, CapitalBlock.Size size) {
        return variant(orientation, size).stats;
    }

    int cachedVariantCount() {
        int result = 0;
        for (int i = 0; i < variants.length(); i++) if (variants.get(i) != null) result++;
        return result;
    }

    private Variant variant(CapitalOrientation orientation, CapitalBlock.Size size) {
        int index = size.ordinal() * ORIENTATIONS + orientation.ordinal();
        Variant value = variants.get(index);
        if (value != null) return value;
        synchronized (variants) {
            value = variants.get(index);
            if (value == null) {
                value = build(orientation, size);
                variants.set(index, value);
            }
        }
        return value;
    }

    private Variant build(CapitalOrientation orientation, CapitalBlock.Size size) {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException("No renderer while preparing a capital world mesh");
        Map<Cell, MeshBuilder> builders = new LinkedHashMap<>();
        QuadEmitter scratch = renderer.meshBuilder().getEmitter();
        var transform = CapitalOrientationTransform.forSize(orientation, size);
        double centre = size == CapitalBlock.Size.DOUBLE ? 1 : .5;
        int[] counts = new int[3];
        double[] discardedArea = new double[1];
        source.forEach(quad -> {
            scratch.copyFrom(quad);
            if (transform != null) transform.transform(scratch);
            Vertex a = Vertex.read(scratch, 0), b = Vertex.read(scratch, 1);
            Vertex c = Vertex.read(scratch, 2), d = Vertex.read(scratch, 3);
            for (Vertex[] triangle : new Vertex[][]{{a, b, c}, {a, c, d}}) {
                if (areaSquared(triangle[0], triangle[1], triangle[2]) == 0) continue;
                counts[0]++;
                for (Patch patch : splitTriangle(triangle[0], triangle[1], triangle[2], centre)) {
                    MeshBuilder builder = builders.computeIfAbsent(patch.cell, ignored -> renderer.meshBuilder());
                    List<Vertex> points = patch.vertices;
                    for (int i = 1; i + 1 < points.size(); i++) {
                        Vertex p = points.get(0), q = points.get(i), r = points.get(i + 1);
                        if (areaSquared(p, q, r) == 0) continue;
                        if (floatAreaSquared(p, q, r) == 0) {
                            counts[2]++;
                            discardedArea[0] += Math.sqrt(areaSquared(p, q, r)) * .5;
                            continue;
                        }
                        QuadEmitter emitter = builder.getEmitter();
                        emitter.copyFrom(scratch); // RenderMaterial, tint, terrain tag and lighting face remain authoritative.
                        writeVertex(emitter, 0, p); writeVertex(emitter, 1, q);
                        writeVertex(emitter, 2, r); writeVertex(emitter, 3, r);
                        emitter.cullFace(null);
                        CapitalTransitionUvTransform.markProjection(emitter, patch.projection);
                        emitter.emit();
                        counts[1]++;
                    }
                }
            }
        });
        CellMesh[] cells = builders.entrySet().stream()
                .map(entry -> new CellMesh(entry.getKey(), entry.getValue().build())).toArray(CellMesh[]::new);
        return new Variant(cells, new Stats(counts[0], counts[1], cells.length,
                (long) counts[1] * 4 * 36, counts[2], discardedArea[0]));
    }

    private static void writeVertex(MutableQuadView quad, int index, Vertex value) {
        quad.pos(index, (float) value.x, (float) value.y, (float) value.z);
        // Do not normalize inserted normals: retain the original triangle's linear interpolation.
        quad.normal(index, (float) value.nx, (float) value.ny, (float) value.nz);
        quad.color(index, value.color);
        quad.lightmap(index, value.lightmap);
    }

    /** Pure clipping path shared by production baking and geometry/UV audits. */
    static List<Patch> splitTriangle(Vertex a, Vertex b, Vertex c, double centre) {
        if (areaSquared(a, b, c) == 0) return List.of();
        Direction horizontal = CapitalProjection.genuineHorizontal(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z);
        int x = lower(Math.min(a.x, Math.min(b.x, c.x))), y = lower(Math.min(a.y, Math.min(b.y, c.y)));
        int z = lower(Math.min(a.z, Math.min(b.z, c.z)));
        if (x == upper(Math.min(a.x, Math.min(b.x, c.x)), Math.max(a.x, Math.max(b.x, c.x)))
                && y == upper(Math.min(a.y, Math.min(b.y, c.y)), Math.max(a.y, Math.max(b.y, c.y)))
                && z == upper(Math.min(a.z, Math.min(b.z, c.z)), Math.max(a.z, Math.max(b.z, c.z)))
                && !straddles(a.x - a.z, b.x - b.z, c.x - c.z)
                && !straddles(a.x + a.z - 2 * centre, b.x + b.z - 2 * centre, c.x + c.z - 2 * centre)) {
            Direction projection = horizontal != null ? horizontal
                    : CapitalProjection.projectionPlane((a.x + b.x + c.x) / 3, (a.z + b.z + c.z) / 3, centre);
            return List.of(new Patch(new Cell(x, y, z), List.of(a, b, c), projection));
        }
        List<List<Vertex>> sectors = new ArrayList<>();
        sectors.add(List.of(a, b, c));
        // A projection sector must not change inside one cached triangle.
        for (Plane plane : new Plane[]{new Plane(1, 0, -1, 0), new Plane(1, 0, 1, 2 * centre)}) {
            List<List<Vertex>> next = new ArrayList<>();
            for (List<Vertex> polygon : sectors) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (Vertex vertex : polygon) {
                    double distance = plane.distance(vertex);
                    min = Math.min(min, distance); max = Math.max(max, distance);
                }
                if (min < 0 && max > 0) {
                    next.add(clip(polygon, plane, true));
                    next.add(clip(polygon, plane, false));
                } else next.add(polygon);
            }
            sectors = next;
        }
        List<Patch> result = new ArrayList<>();
        for (List<Vertex> sector : sectors) {
            if (sector.size() < 3) continue;
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (Vertex vertex : sector) for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], vertex.axis(axis));
                max[axis] = Math.max(max[axis], vertex.axis(axis));
            }
            for (int cy = lower(min[1]); cy <= upper(min[1], max[1]); cy++)
                for (int cz = lower(min[2]); cz <= upper(min[2], max[2]); cz++)
                    for (int cx = lower(min[0]); cx <= upper(min[0], max[0]); cx++) {
                        List<Vertex> polygon = sector;
                        int[] cell = {cx, cy, cz};
                        for (int axis = 0; axis < 3; axis++) {
                            double px = axis == 0 ? 1 : 0, py = axis == 1 ? 1 : 0, pz = axis == 2 ? 1 : 0;
                            polygon = clip(polygon, new Plane(px, py, pz, cell[axis]), true);
                            polygon = clip(polygon, new Plane(px, py, pz, cell[axis] + 1), false);
                        }
                        polygon = deduplicate(polygon);
                        if (polygon.size() >= 3 && polygonArea(polygon) > 0) {
                            double midX = 0, midZ = 0;
                            for (Vertex vertex : polygon) { midX += vertex.x; midZ += vertex.z; }
                            Direction projection = horizontal != null ? horizontal : CapitalProjection.projectionPlane(
                                    midX / polygon.size(), midZ / polygon.size(), centre);
                            result.add(new Patch(new Cell(cx, cy, cz), List.copyOf(polygon), projection));
                        }
                    }
        }
        return result;
    }

    private static boolean straddles(double a, double b, double c) {
        return Math.min(a, Math.min(b, c)) < 0 && Math.max(a, Math.max(b, c)) > 0;
    }

    private static int lower(double value) { return (int) Math.floor(value); }
    private static int upper(double min, double max) {
        return min == max ? lower(min) : (int) Math.floor(Math.nextDown(max));
    }

    static List<Vertex> clip(List<Vertex> input, Plane plane, boolean above) {
        if (input.isEmpty()) return input;
        List<Vertex> output = new ArrayList<>();
        Vertex previous = input.get(input.size() - 1);
        double previousDistance = plane.distance(previous);
        boolean previousInside = above ? previousDistance >= 0 : previousDistance <= 0;
        for (Vertex current : input) {
            double distance = plane.distance(current);
            boolean inside = above ? distance >= 0 : distance <= 0;
            if (inside != previousInside) {
                double t = previousDistance / (previousDistance - distance);
                Vertex intersection = previous.interpolate(current, t);
                // Exact integer boundaries avoid tiny duplicate owner cells from roundoff.
                if (plane.x == 1 && plane.y == 0 && plane.z == 0) intersection = intersection.position(0, plane.offset);
                if (plane.x == 0 && plane.y == 1 && plane.z == 0) intersection = intersection.position(1, plane.offset);
                if (plane.x == 0 && plane.y == 0 && plane.z == 1) intersection = intersection.position(2, plane.offset);
                output.add(intersection);
            }
            if (inside) output.add(current);
            previous = current; previousDistance = distance; previousInside = inside;
        }
        return output;
    }

    private static List<Vertex> deduplicate(List<Vertex> polygon) {
        List<Vertex> result = new ArrayList<>();
        for (Vertex vertex : polygon) if (result.isEmpty() || !vertex.samePosition(result.get(result.size() - 1))) result.add(vertex);
        if (result.size() > 1 && result.get(0).samePosition(result.get(result.size() - 1))) result.remove(result.size() - 1);
        return result;
    }

    static double polygonArea(List<Vertex> polygon) {
        double result = 0;
        for (int i = 1; i + 1 < polygon.size(); i++) result += Math.sqrt(areaSquared(polygon.get(0), polygon.get(i), polygon.get(i + 1))) * .5;
        return result;
    }

    static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
        double bx = c.x - a.x, by = c.y - a.y, bz = c.z - a.z;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        return nx * nx + ny * ny + nz * nz;
    }

    /** Match the positions and cross product representable in a renderer's float mesh. */
    static double floatAreaSquared(Vertex a, Vertex b, Vertex c) {
        float ax = (float) b.x - (float) a.x, ay = (float) b.y - (float) a.y, az = (float) b.z - (float) a.z;
        float bx = (float) c.x - (float) a.x, by = (float) c.y - (float) a.y, bz = (float) c.z - (float) a.z;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        return (double) nx * nx + (double) ny * ny + (double) nz * nz;
    }

    record Vertex(double x, double y, double z, double nx, double ny, double nz, int color, int lightmap) {
        static Vertex read(QuadView quad, int index) {
            if (!quad.hasNormal(index)) throw new IllegalArgumentException("Baked capital OBJ vertex lacks its supplied normal");
            return new Vertex(quad.x(index), quad.y(index), quad.z(index), quad.normalX(index), quad.normalY(index),
                    quad.normalZ(index), quad.color(index), quad.lightmap(index));
        }
        static Vertex point(double x, double y, double z) { return new Vertex(x, y, z, 0, 1, 0, -1, 0); }
        double[] point() { return new double[]{x, y, z}; }
        double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        boolean samePosition(Vertex other) { return x == other.x && y == other.y && z == other.z; }
        Vertex position(int axis, double value) {
            return new Vertex(axis == 0 ? value : x, axis == 1 ? value : y, axis == 2 ? value : z, nx, ny, nz, color, lightmap);
        }
        Vertex interpolate(Vertex other, double t) {
            return new Vertex(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t,
                    nx + (other.nx - nx) * t, ny + (other.ny - ny) * t, nz + (other.nz - nz) * t,
                    interpolatePacked(color, other.color, t, 8), interpolatePacked(lightmap, other.lightmap, t, 16));
        }
    }

    private static int interpolatePacked(int a, int b, double t, int bits) {
        int result = 0, mask = bits == 16 ? 0xffff : 0xff;
        for (int shift = 0; shift < 32; shift += bits) {
            int from = (a >>> shift) & mask, to = (b >>> shift) & mask;
            result |= ((int) Math.round(from + (to - from) * t) & mask) << shift;
        }
        return result;
    }

    record Plane(double x, double y, double z, double offset) {
        double distance(Vertex vertex) { return x * vertex.x + y * vertex.y + z * vertex.z - offset; }
    }
    record Cell(int x, int y, int z) { }
    record Patch(Cell cell, List<Vertex> vertices, Direction projection) { }
    record Stats(int sourceTriangles, int emittedTriangles, int cellCount, long estimatedVertexBytes,
                 int discardedFloatTriangles, double discardedArea) { }
    private record CellMesh(Cell cell, Mesh mesh) { }
    private record Variant(CellMesh[] cells, Stats stats) { }
}
