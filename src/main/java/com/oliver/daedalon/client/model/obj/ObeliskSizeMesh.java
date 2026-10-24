package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.SizedDecorBlock;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One repeat-safe five-block mesh per shared source; the smaller sizes use their original path. */
final class ObeliskSizeMesh {
    // The resulting mesh contains vertex data, never a reference back to its source key.
    // Old reload entries can therefore expire with the source's baked models.
    private static final Map<Mesh, Mesh> CACHE = new WeakHashMap<>();

    private ObeliskSizeMesh() { }

    static synchronized Mesh forSource(Mesh source) {
        Mesh result = CACHE.get(source);
        if (result != null) return result;
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException("No renderer while preparing the five-block obelisk");
        var builder = renderer.meshBuilder();
        double scale = SizedDecorBlock.Size.LARGE.scale();
        source.forEach(quad -> {
            Vertex a = Vertex.read(quad, 0, scale), b = Vertex.read(quad, 1, scale);
            Vertex c = Vertex.read(quad, 2, scale), d = Vertex.read(quad, 3, scale);
            for (Vertex[] triangle : new Vertex[][]{{a, b, c}, {a, c, d}}) {
                for (Patch patch : splitTriangle(triangle[0], triangle[1], triangle[2])) {
                    var points = patch.vertices();
                    for (int i = 1; i + 1 < points.size(); i++) {
                        Vertex p = points.get(0), q = points.get(i), r = points.get(i + 1);
                        if (areaSquared(p, q, r) == 0) continue;
                        var emitter = builder.getEmitter();
                        // Retain material, terrain tag, tint and lighting/nominal face metadata.
                        emitter.copyFrom(quad);
                        write(emitter, 0, p, patch.uCell(), patch.vCell());
                        write(emitter, 1, q, patch.uCell(), patch.vCell());
                        write(emitter, 2, r, patch.uCell(), patch.vCell());
                        write(emitter, 3, r, patch.uCell(), patch.vCell());
                        emitter.cullFace(null);
                        emitter.emit();
                    }
                }
            }
        });
        result = builder.build();
        CACHE.put(source, result);
        return result;
    }

    private static void write(MutableQuadView quad, int index, Vertex value, int uCell, int vCell) {
        quad.pos(index, (float) value.x, (float) value.y, (float) value.z);
        quad.uv(index, (float) (value.u - uCell), (float) (value.v - vCell));
        quad.normal(index, (float) value.nx, (float) value.ny, (float) value.nz);
        quad.color(index, value.color);
        quad.lightmap(index, value.lightmap);
    }

    /** Clip before wrapping: wrapping individual corners would stretch across the stitched atlas. */
    static List<Patch> splitTriangle(Vertex a, Vertex b, Vertex c) {
        if (areaSquared(a, b, c) == 0) return List.of();
        double minU = Math.min(a.u, Math.min(b.u, c.u)), maxU = Math.max(a.u, Math.max(b.u, c.u));
        double minV = Math.min(a.v, Math.min(b.v, c.v)), maxV = Math.max(a.v, Math.max(b.v, c.v));
        List<Patch> result = new ArrayList<>();
        for (int v = lower(minV); v <= upper(minV, maxV); v++) {
            for (int u = lower(minU); u <= upper(minU, maxU); u++) {
                List<Vertex> points = List.of(a, b, c);
                points = clip(points, 0, u, true);
                points = clip(points, 0, u + 1, false);
                points = clip(points, 1, v, true);
                points = clip(points, 1, v + 1, false);
                if (points.size() >= 3 && area(points) > 0) result.add(new Patch(u, v, List.copyOf(points)));
            }
        }
        return result;
    }

    private static int lower(double value) { return (int) Math.floor(value); }
    private static int upper(double min, double max) {
        return min == max ? lower(min) : (int) Math.floor(Math.nextDown(max));
    }

    private static List<Vertex> clip(List<Vertex> input, int axis, double boundary, boolean above) {
        if (input.isEmpty()) return input;
        List<Vertex> output = new ArrayList<>();
        Vertex previous = input.get(input.size() - 1);
        double before = previous.uv(axis);
        boolean beforeInside = above ? before >= boundary : before <= boundary;
        for (Vertex current : input) {
            double now = current.uv(axis);
            boolean inside = above ? now >= boundary : now <= boundary;
            if (inside != beforeInside) {
                Vertex intersection = previous.interpolate(current, (boundary - before) / (now - before));
                output.add(intersection.withUv(axis, boundary));
            }
            if (inside) output.add(current);
            previous = current;
            before = now;
            beforeInside = inside;
        }
        return output;
    }

    static double area(List<Vertex> polygon) {
        double result = 0;
        for (int i = 1; i + 1 < polygon.size(); i++) result += .5 * Math.sqrt(areaSquared(polygon.get(0), polygon.get(i), polygon.get(i + 1)));
        return result;
    }

    static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
        double bx = c.x - a.x, by = c.y - a.y, bz = c.z - a.z;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        return nx * nx + ny * ny + nz * nz;
    }

    record Patch(int uCell, int vCell, List<Vertex> vertices) { }

    record Vertex(double x, double y, double z, double u, double v,
                  double nx, double ny, double nz, int color, int lightmap) {
        static Vertex read(QuadView quad, int index, double scale) {
            return new Vertex(.5 + (quad.x(index) - .5) * scale, quad.y(index) * scale,
                    .5 + (quad.z(index) - .5) * scale,
                    .5 + (quad.u(index) - .5) * scale, .5 + (quad.v(index) - .5) * scale,
                    quad.normalX(index), quad.normalY(index), quad.normalZ(index), quad.color(index), quad.lightmap(index));
        }
        double uv(int axis) { return axis == 0 ? u : v; }
        Vertex withUv(int axis, double value) {
            return new Vertex(x, y, z, axis == 0 ? value : u, axis == 1 ? value : v, nx, ny, nz, color, lightmap);
        }
        Vertex interpolate(Vertex other, double t) {
            return new Vertex(lerp(x, other.x, t), lerp(y, other.y, t), lerp(z, other.z, t),
                    lerp(u, other.u, t), lerp(v, other.v, t),
                    lerp(nx, other.nx, t), lerp(ny, other.ny, t), lerp(nz, other.nz, t),
                    color(color, other.color, t), light(lightmap, other.lightmap, t));
        }
        private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
        private static int color(int a, int b, double t) {
            int result = 0;
            for (int shift : new int[]{0, 8, 16, 24}) {
                int channel = (int) Math.round(lerp((a >>> shift) & 255, (b >>> shift) & 255, t));
                result |= channel << shift;
            }
            return result;
        }
        private static int light(int a, int b, double t) {
            int lower = (int) Math.round(lerp(a & 65535, b & 65535, t));
            int upper = (int) Math.round(lerp(a >>> 16, b >>> 16, t));
            return lower | upper << 16;
        }
    }
}
