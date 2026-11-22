package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;

/** Keep finite metal sprites inside their own bilinear sampling footprint. */
final class LanternSpriteUv {
    private LanternSpriteUv() { }

    static void protect(MutableQuadView quad, int width, int height) {
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.uv(vertex, inset(quad.u(vertex), width), inset(quad.v(vertex), height));
        }
    }

    static float inset(float coordinate, int pixels) {
        if (pixels < 1 || !Float.isFinite(coordinate) || coordinate < -1.0e-5F || coordinate > 1.00001F) {
            throw new IllegalArgumentException("Lantern finite-sprite UV must be clipped before atlas baking");
        }
        float minimum = .5F / pixels;
        // Apply one affine map to the whole primitive, preserving its UV handedness
        // and derivatives. Stone repeat sheets and authored glass/flame cards bypass this.
        return minimum + Math.max(0, Math.min(1, coordinate)) * (1 - 2 * minimum);
    }
}
