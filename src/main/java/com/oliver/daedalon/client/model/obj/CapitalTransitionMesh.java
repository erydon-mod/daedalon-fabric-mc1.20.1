package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalTransition;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.util.math.Direction;

/** One shallow square-to-round collar, shared by every capital carving and material. */
final class CapitalTransitionMesh {
    static final int SEGMENTS = 16;
    private static final java.util.Map<Key, Mesh> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Key[][] KEYS = java.util.Arrays.stream(CapitalTransition.values())
            .map(transition -> java.util.Arrays.stream(CapitalBlock.Size.values())
                    .map(size -> new Key(transition, size)).toArray(Key[]::new)).toArray(Key[][]::new);

    private CapitalTransitionMesh() { }

    static Mesh forState(CapitalTransition transition, CapitalBlock.Size size) {
        return CACHE.computeIfAbsent(KEYS[transition.ordinal()][size.ordinal()], CapitalTransitionMesh::create);
    }

    private static Mesh create(Key key) {
        var builder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter = builder.getEmitter();
        double[][][] rings = rings(key.transition, key.size);
        double centre = key.size == CapitalBlock.Size.DOUBLE ? 1 : 0.5;
        for (int ring = 0; ring < 2; ring++) for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            face(emitter, centre, rings[ring][i], rings[ring + 1][i], rings[ring + 1][next], rings[ring][next]);
        }
        // The top is concealed by the original lower rim, including its slightly irregular Meshy outline.
        double[] topCentre = {centre, rings[2][0][1], centre};
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            face(emitter, centre, topCentre, rings[2][next], rings[2][i], rings[2][i]);
        }
        double min = rings[0][8][0], max = rings[0][0][0];
        face(emitter, centre, new double[]{min, 0, min}, new double[]{max, 0, min},
                new double[]{max, 0, max}, new double[]{min, 0, max});
        return builder.build();
    }

    /** Keep the square world-aligned while the capital carving turns independently above it. */
    static double[][][] rings(CapitalTransition transition, CapitalBlock.Size size) {
        double scale = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
        double centre = scale * 0.5;
        double half = transition.baseWidth() * scale * 0.5;
        double radius = CapitalTransition.JOIN_DIAMETER * scale * 0.5;
        double[][][] rings = new double[3][SEGMENTS][3];
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS;
            double x = Math.cos(angle), z = Math.sin(angle);
            double squareRadius = half / Math.max(Math.abs(x), Math.abs(z));
            rings[0][i] = new double[]{centre + x * squareRadius, 0, centre + z * squareRadius};
            rings[1][i] = new double[]{centre + x * squareRadius, scale / 32, centre + z * squareRadius};
            rings[2][i] = new double[]{centre + x * radius, CapitalTransition.HEIGHT * scale, centre + z * radius};
        }
        return rings;
    }

    private static void face(QuadEmitter emitter, double centre, double[] a, double[] b, double[] c, double[] d) {
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length; ny /= length; nz /= length;
        Direction direction = Math.abs(ny) >= Math.abs(nx) && Math.abs(ny) >= Math.abs(nz)
                ? (ny >= 0 ? Direction.UP : Direction.DOWN)
                : Math.abs(nx) >= Math.abs(nz) ? (nx >= 0 ? Direction.EAST : Direction.WEST)
                : (nz >= 0 ? Direction.SOUTH : Direction.NORTH);
        double[][] points = {a, b, c, d};
        for (int vertex = 0; vertex < 4; vertex++) {
            double[] p = points[vertex];
            emitter.pos(vertex, (float) p[0], (float) p[1], (float) p[2]);
            double u = direction.getAxis() == Direction.Axis.X ? p[2] : p[0];
            double v = direction.getAxis() == Direction.Axis.Y ? p[2] : p[1];
            emitter.uv(vertex, (float) (0.5 + (u - centre) / 6),
                    (float) (0.5 + (v - (direction.getAxis() == Direction.Axis.Y ? centre : 0)) / 6));
            emitter.normal(vertex, (float) nx, (float) ny, (float) nz);
            emitter.color(vertex, -1);
        }
        emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(0));
        emitter.nominalFace(direction);
        emitter.cullFace(null);
        emitter.emit();
    }

    private record Key(CapitalTransition transition, CapitalBlock.Size size) { }
}
