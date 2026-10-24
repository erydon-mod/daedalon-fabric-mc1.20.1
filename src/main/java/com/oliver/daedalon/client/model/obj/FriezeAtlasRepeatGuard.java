package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;

/** Selects the identical interior repeat copy without moving a frieze's texture phase. */
final class FriezeAtlasRepeatGuard {
    private static final float PERIOD = 0.5F;
    private static final float EDGE_EPSILON = 1.0e-6F;

    private FriezeAtlasRepeatGuard() { }

    /**
     * Accepts final sprite-normalised UVs, immediately before spriteBake. Detailed
     * frieze sheets contain two identical horizontal halves; flat finishes are
     * uniform. Every vertex of an affected quad receives the same period offset.
     */
    static boolean protect(MutableQuadView quad) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = quad.u(vertex);
            if (!Float.isFinite(u)) return false;
            min = Math.min(min, u);
            max = Math.max(max, u);
        }
        if (min < -EDGE_EPSILON || max > 1 + EDGE_EPSILON) return false;
        // Move the whole primitive, including small triangles next to the edge:
        // changing individual vertices would stretch the texture or its tangents.
        // Choose only a complete copy that increases the outside-edge clearance.
        float clearance = Math.min(min, 1 - max), offset;
        if (max < PERIOD - EDGE_EPSILON
                && Math.min(min + PERIOD, 1 - max - PERIOD) > clearance + EDGE_EPSILON) {
            offset = PERIOD;
        } else if (min > PERIOD + EDGE_EPSILON
                && Math.min(min - PERIOD, 1 - max + PERIOD) > clearance + EDGE_EPSILON) {
            offset = -PERIOD;
        } else return false;
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.uv(vertex, quad.u(vertex) + offset, quad.v(vertex));
        }
        return true;
    }
}
