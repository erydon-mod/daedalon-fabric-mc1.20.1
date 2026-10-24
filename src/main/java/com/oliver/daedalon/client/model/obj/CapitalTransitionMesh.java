package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalTransition;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** One shallow square-to-round collar, shared by every capital carving and material. */
final class CapitalTransitionMesh {
    static final int SEGMENTS = 16;
    private static final java.util.Map<Key, Mesh[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Key[][] KEYS = java.util.Arrays.stream(CapitalTransition.values())
            .map(transition -> java.util.Arrays.stream(CapitalBlock.Size.values())
                    .map(size -> new Key(transition, size)).toArray(Key[]::new)).toArray(Key[][]::new);

    private CapitalTransitionMesh() { }

    static void emit(CapitalTransition transition, CapitalBlock.Size size, BlockPos pos, RenderContext context) {
        Mesh[] cells = CACHE.computeIfAbsent(KEYS[transition.ordinal()][size.ordinal()], CapitalTransitionMesh::create);
        int width = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
        for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) {
            context.pushTransform(CapitalTransitionUvTransform.forCell(pos, x, z));
            try { cells[x + z * width].outputTo(context.getEmitter()); }
            finally { context.popTransform(); }
        }
    }

    private static Mesh[] create(Key key) {
        int width = key.size == CapitalBlock.Size.DOUBLE ? 2 : 1;
        var builders = new net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder[width * width];
        for (int i = 0; i < builders.length; i++) builders[i] = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        double[][][] rings = rings(key.transition, key.size);
        int count = rings[0].length;
        double centre = key.size == CapitalBlock.Size.DOUBLE ? 1 : 0.5;
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            face(builders, width, rings[ring][i], rings[ring + 1][i], rings[ring + 1][next], rings[ring][next]);
        }
        // The top is concealed by the original lower rim, including its slightly irregular Meshy outline.
        double[] topCentre = {centre, rings[2][0][1], centre};
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            face(builders, width, topCentre, rings[2][next], rings[2][i], rings[2][i]);
        }
        var profile = key.transition.profile(key.size);
        for (int i = 0; i < profile.triangleCount(); i++) {
            double[] a = rings[0][profile.triangleVertex(i, 0)], b = rings[0][profile.triangleVertex(i, 1)],
                    c = rings[0][profile.triangleVertex(i, 2)];
            // The resource triangulation is counter-clockwise in X/Z, hence downward in world space.
            face(builders, width, a, b, c, c);
        }
        return java.util.Arrays.stream(builders).map(net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder::build).toArray(Mesh[]::new);
    }

    /** Keep the square world-aligned while the capital carving turns independently above it. */
    static double[][][] rings(CapitalTransition transition, CapitalBlock.Size size) {
        double scale = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
        double centre = scale * 0.5;
        double radius = CapitalTransition.JOIN_DIAMETER * scale * 0.5;
        var profile = transition.profile(size);
        double[][][] rings = new double[3][profile.size()][3];
        for (int i = 0; i < profile.size(); i++) {
            double angle = profile.angle(i);
            double x = Math.cos(angle), z = Math.sin(angle);
            rings[0][i] = new double[]{profile.x(i) * scale, 0, profile.z(i) * scale};
            rings[1][i] = new double[]{profile.x(i) * scale, scale / 32, profile.z(i) * scale};
            rings[2][i] = new double[]{centre + x * radius, CapitalTransition.HEIGHT * scale, centre + z * radius};
        }
        return rings;
    }

    private static void face(net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder[] builders, int width,
                             double[] a, double[] b, double[] c, double[] d) {
        if (c != d) {
            // Shaft flutes make the loft non-planar. Give each triangle its own correct terrain normal.
            face(builders, width, a, b, c, c);
            face(builders, width, a, c, d, d);
            return;
        }
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-12) return;
        nx /= length; ny /= length; nz /= length;
        Direction direction = Math.abs(ny) >= Math.abs(nx) && Math.abs(ny) >= Math.abs(nz)
                ? (ny >= 0 ? Direction.UP : Direction.DOWN)
                : Math.abs(nx) >= Math.abs(nz) ? (nx >= 0 ? Direction.EAST : Direction.WEST)
                : (nz >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction horizontal = CapitalProjection.genuineHorizontal(a, b, c);
        if (horizontal != null) {
            emitPatch(builders, width, horizontal, java.util.List.of(a, b, c), nx, ny, nz, direction);
            return;
        }
        double centre = width * 0.5;
        boolean onCut = CapitalProjection.onSectorBoundary(a[0], a[2], b[0], b[2], c[0], c[2], centre);
        Direction owner = CapitalProjection.projectionPlane((a[0] + b[0] + c[0]) / 3,
                (a[2] + b[2] + c[2]) / 3, centre);
        for (Direction sector : CapitalProjection.SIDES) {
            if (onCut && sector != owner) continue;
            var patch = onCut ? java.util.List.of(a, b, c) : clipToSector(java.util.List.of(a, b, c), sector, centre);
            emitPatch(builders, width, sector, patch, nx, ny, nz, direction);
        }
    }

    private static void emitPatch(net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder[] builders, int width,
                                  Direction projection, java.util.List<double[]> patch,
                                  double nx, double ny, double nz, Direction lightingFace) {
        for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) {
            var polygon = clipToCell(patch, x, z);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                QuadEmitter emitter = builders[x + width * z].getEmitter();
                double[][] points = {polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1)};
                if (areaSquared(points[0], points[1], points[2]) < 1e-24) continue;
                for (int vertex = 0; vertex < 4; vertex++) {
                    double[] p = points[vertex];
                    emitter.pos(vertex, (float) p[0], (float) p[1], (float) p[2]);
                    emitter.normal(vertex, (float) nx, (float) ny, (float) nz);
                    emitter.color(vertex, -1);
                }
                CapitalTransitionUvTransform.markProjection(emitter, projection);
                emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(0));
                emitter.nominalFace(lightingFace);
                emitter.cullFace(null);
                emitter.emit();
            }
        }
    }

    /** Only UV patches are subdivided; all points remain on the original rendered triangles. */
    static java.util.List<double[]> clipToSector(java.util.List<double[]> polygon, Direction sector, double centre) {
        for (int edge = 0; edge < 2; edge++) {
            if (polygon.isEmpty()) return polygon;
            var result = new java.util.ArrayList<double[]>();
            double[] previous = polygon.get(polygon.size() - 1);
            double previousDistance = CapitalProjection.sectorDistance(sector, edge, previous[0], previous[2], centre);
            for (double[] current : polygon) {
                double distance = CapitalProjection.sectorDistance(sector, edge, current[0], current[2], centre);
                if ((distance >= 0) != (previousDistance >= 0)) {
                    double t = previousDistance / (previousDistance - distance);
                    result.add(new double[]{previous[0] + (current[0] - previous[0]) * t,
                            previous[1] + (current[1] - previous[1]) * t,
                            previous[2] + (current[2] - previous[2]) * t});
                }
                if (distance >= 0) result.add(current);
                previous = current; previousDistance = distance;
            }
            polygon = result;
        }
        return polygon;
    }

    private static double areaSquared(double[] a, double[] b, double[] c) {
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        double x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        return x * x + y * y + z * z;
    }

    /** Split once at bake time, so no placed quad can cross a six-tile V wrap. */
    static java.util.List<double[]> clipToCell(java.util.List<double[]> polygon, int x, int z) {
        polygon = clip(polygon, 0, x, true);
        polygon = clip(polygon, 0, x + 1, false);
        polygon = clip(polygon, 2, z, true);
        return clip(polygon, 2, z + 1, false);
    }

    private static java.util.List<double[]> clip(java.util.List<double[]> input, int axis, double plane, boolean above) {
        if (input.isEmpty()) return input;
        var result = new java.util.ArrayList<double[]>();
        double[] previous = input.get(input.size() - 1);
        boolean previousInside = above ? previous[axis] >= plane : previous[axis] <= plane;
        for (double[] current : input) {
            boolean inside = above ? current[axis] >= plane : current[axis] <= plane;
            if (inside != previousInside) {
                double t = (plane - previous[axis]) / (current[axis] - previous[axis]);
                double[] point = new double[3];
                for (int i = 0; i < 3; i++) point[i] = previous[i] + (current[i] - previous[i]) * t;
                point[axis] = plane;
                result.add(point);
            }
            if (inside) result.add(current);
            previous = current; previousInside = inside;
        }
        return result;
    }

    private record Key(CapitalTransition transition, CapitalBlock.Size size) { }
}
