package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FountainBaseProfile;
import com.oliver.daedalon.block.FountainBasinBlock;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Nine shared raised-base meshes; geometry never depends on material or position. */
final class FountainRaisedBaseMesh {
    private static final ConcurrentMap<Key, Mesh> CACHE = new ConcurrentHashMap<>();
    private static final Key[][] KEYS = keys();

    private FountainRaisedBaseMesh() { }

    static void emit(FountainBasinBlock.Style style, FountainBasinBlock.Size size, BlockPos anchor, RenderContext context) {
        CACHE.computeIfAbsent(KEYS[style.ordinal()][size.ordinal()], FountainRaisedBaseMesh::create).outputTo(context.getEmitter());
    }

    private static Key[][] keys() {
        var styles = FountainBasinBlock.Style.values(); var sizes = FountainBasinBlock.Size.values();
        Key[][] result = new Key[styles.length][sizes.length];
        for (var style : styles) for (var size : sizes) result[style.ordinal()][size.ordinal()] = new Key(style, size);
        return result;
    }

    private static Mesh create(Key key) {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException("No renderer while baking a fountain base");
        var builder = renderer.meshBuilder();
        FountainBaseProfile profile = FountainBaseProfile.forStyle(key.style);
        double scale = key.size.modelScale();
        double[][] bottom = new double[profile.vertexCount()][3], top = new double[profile.vertexCount()][3];
        for (int i = 0; i < profile.vertexCount(); i++) {
            bottom[i] = new double[]{.5 + profile.x(i) * scale, 0, .5 + profile.z(i) * scale};
            top[i] = new double[]{bottom[i][0], 1 + profile.topOffset(i) * scale, bottom[i][2]};
        }
        for (int i = 0; i < profile.vertexCount(); i++) {
            int next = (i + 1) % profile.vertexCount();
            double nx = bottom[next][2] - bottom[i][2], nz = bottom[i][0] - bottom[next][0];
            double length = Math.hypot(nx, nz);
            if (length == 0) continue;
            nx /= length; nz /= length;
            Direction face = sideProjection(bottom[i], bottom[next], nx, nz);
            double split = verticalWrapY(profile, key.size);
            if (split > 1e-6 && split < Math.min(top[i][1], top[next][1])) {
                double[] a = {bottom[i][0], split, bottom[i][2]}, b = {bottom[next][0], split, bottom[next][2]};
                face(builder.getEmitter(), profile, key.size, bottom[i], a, b, bottom[next], face, nx, 0, nz, 1);
                face(builder.getEmitter(), profile, key.size, a, top[i], top[next], b, face, nx, 0, nz, 0);
            } else {
                int row = rawV(profile, key.size, .5) >= 1 ? 1 : 0;
                face(builder.getEmitter(), profile, key.size, bottom[i], top[i], top[next], bottom[next], face, nx, 0, nz, row);
            }
        }
        for (int i = 0; i < profile.triangleCount(); i++) {
            int a = profile.triangleVertex(i, 0), b = profile.triangleVertex(i, 1), c = profile.triangleVertex(i, 2);
            face(builder.getEmitter(), profile, key.size, bottom[a], bottom[b], bottom[c], bottom[c], Direction.DOWN, 0, -1, 0, 0);
            double[] p = top[a], q = top[c], r = top[b];
            double nx = (q[1] - p[1]) * (r[2] - p[2]) - (q[2] - p[2]) * (r[1] - p[1]);
            double ny = (q[2] - p[2]) * (r[0] - p[0]) - (q[0] - p[0]) * (r[2] - p[2]);
            double nz = (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0) face(builder.getEmitter(), profile, key.size, p, q, r, r, Direction.UP,
                    nx / length, ny / length, nz / length, 0);
        }
        return builder.build();
    }

    private static Direction sideProjection(double[] a, double[] b, double nx, double nz) {
        double x = (a[0] + b[0]) * .5 - .5, z = (a[2] + b[2]) * .5 - .5;
        boolean xAxis = Math.abs(x) >= Math.abs(z);
        // Same axis-stabilized choice as the existing basin's box projector.
        if (Math.abs(xAxis ? nx : nz) >= .35) return xAxis
                ? nx >= 0 ? Direction.EAST : Direction.WEST : nz >= 0 ? Direction.SOUTH : Direction.NORTH;
        return Math.abs(nx) >= Math.abs(nz) ? nx >= 0 ? Direction.EAST : Direction.WEST
                : nz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void face(QuadEmitter emitter, FountainBaseProfile profile, FountainBasinBlock.Size size,
                             double[] a, double[] b, double[] c, double[] d, Direction face,
                             double nx, double ny, double nz, int row) {
        double[][] points = {a, b, c, d};
        for (int vertex = 0; vertex < 4; vertex++) {
            double[] p = points[vertex];
            double[] uv = rawUv(profile, size, face, p[0], p[1], p[2], row);
            emitter.pos(vertex, (float) p[0], (float) p[1], (float) p[2]);
            emitter.normal(vertex, (float) nx, (float) ny, (float) nz);
            emitter.uv(vertex, (float) uv[0], (float) uv[1]);
            emitter.color(vertex, -1);
        }
        emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(0));
        emitter.nominalFace(Direction.getFacing((float) nx, (float) ny, (float) nz));
        emitter.cullFace(null); emitter.emit();
    }

    /** Original basin crop/projection, continued below its old base plane without rescaling. */
    static double[] rawUv(FountainBaseProfile profile, FountainBasinBlock.Size size, Direction face,
                          double x, double y, double z, int row) {
        double scale = size.renderScale(), span = profile.sourceUvSpan();
        double sourceX = .5 + (x - .5) / scale, sourceZ = .5 + (z - .5) / scale;
        double u = (sourceX - profile.sourceUvMinX()) / span;
        double v = 1 - (y - 1) / (scale * span);
        switch (face) {
            case NORTH -> u = 1 - u;
            case EAST -> u = 1 - (sourceZ - profile.sourceUvMinZ()) / span;
            case WEST -> u = (sourceZ - profile.sourceUvMinZ()) / span;
            case UP -> v = (sourceZ - profile.sourceUvMinZ()) / span;
            case DOWN -> v = 1 - (sourceZ - profile.sourceUvMinZ()) / span;
            default -> { }
        }
        return new double[]{.5 + (u - .5) * scale, .5 + (v - .5) * scale - row};
    }

    static double rawV(FountainBaseProfile profile, FountainBasinBlock.Size size, double y) {
        return .5 + .5 * size.renderScale() - (y - 1) / profile.sourceUvSpan();
    }

    static double verticalWrapY(FountainBaseProfile profile, FountainBasinBlock.Size size) {
        return 1 + profile.sourceUvSpan() * (.5 + .5 * size.renderScale() - 1);
    }

    private record Key(FountainBasinBlock.Style style, FountainBasinBlock.Size size) { }
}
