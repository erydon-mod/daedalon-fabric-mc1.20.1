package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalOrientation;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.Direction;

/** Four immutable transforms shared by every capital material; no duplicated meshes. */
final class CapitalOrientationTransform implements RenderContext.QuadTransform {
    private static final CapitalOrientationTransform[] CACHE = createCache();
    private final float cosine;
    private final float sine;

    private CapitalOrientationTransform(CapitalOrientation orientation) {
        cosine = orientation.cosine;
        sine = orientation.sine;
    }

    private static CapitalOrientationTransform[] createCache() {
        CapitalOrientation[] values = CapitalOrientation.values();
        CapitalOrientationTransform[] cache = new CapitalOrientationTransform[values.length];
        for (CapitalOrientation orientation : values) cache[orientation.ordinal()] = new CapitalOrientationTransform(orientation);
        return cache;
    }

    static CapitalOrientationTransform forOrientation(CapitalOrientation orientation) {
        return orientation == CapitalOrientation.STRAIGHT ? null : CACHE[orientation.ordinal()];
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex) - 0.5F;
            float z = quad.z(vertex) - 0.5F;
            quad.pos(vertex, cosine * x - sine * z + 0.5F, quad.y(vertex), sine * x + cosine * z + 0.5F);
            if (quad.hasNormal(vertex)) {
                float nx = quad.normalX(vertex), nz = quad.normalZ(vertex);
                quad.normal(vertex, cosine * nx - sine * nz, quad.normalY(vertex), sine * nx + cosine * nz);
            }
        }
        quad.cullFace(null);
        Direction face = quad.nominalFace();
        if (face != null) quad.nominalFace(Direction.getFacing(
                cosine * face.getOffsetX() - sine * face.getOffsetZ(), face.getOffsetY(),
                sine * face.getOffsetX() + cosine * face.getOffsetZ()));
        return true;
    }
}
