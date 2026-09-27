package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.CapitalBlock;
import net.minecraft.block.BlockState;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.Direction;

/** Four immutable transforms shared by every capital material; no duplicated meshes. */
final class CapitalOrientationTransform implements RenderContext.QuadTransform {
    private static final CapitalOrientationTransform[][] CACHE = createCache();
    private final float cosine;
    private final float sine;
    private final float scale;
    private final float center;

    private CapitalOrientationTransform(CapitalOrientation orientation, CapitalBlock.Size size) {
        cosine = orientation.cosine;
        sine = orientation.sine;
        scale = size == CapitalBlock.Size.DOUBLE ? 2.0F : 1.0F;
        center = scale * 0.5F;
    }

    private static CapitalOrientationTransform[][] createCache() {
        CapitalOrientation[] values = CapitalOrientation.values();
        CapitalOrientationTransform[][] cache = new CapitalOrientationTransform[CapitalBlock.Size.values().length][values.length];
        for (CapitalBlock.Size size : CapitalBlock.Size.values())
            for (CapitalOrientation orientation : values)
                cache[size.ordinal()][orientation.ordinal()] = new CapitalOrientationTransform(orientation, size);
        return cache;
    }

    static CapitalOrientationTransform forState(CapitalBlock capital, BlockState state) {
        CapitalOrientation orientation = capital.orientation(state);
        CapitalBlock.Size size = state.get(CapitalBlock.SIZE);
        return forSize(orientation, size);
    }

    static CapitalOrientationTransform forSize(CapitalOrientation orientation, CapitalBlock.Size size) {
        return orientation == CapitalOrientation.STRAIGHT && size == CapitalBlock.Size.STANDARD
                ? null : CACHE[size.ordinal()][orientation.ordinal()];
    }

    static CapitalOrientationTransform forOrientation(CapitalOrientation orientation) {
        return orientation == CapitalOrientation.STRAIGHT ? null
                : CACHE[CapitalBlock.Size.STANDARD.ordinal()][orientation.ordinal()];
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex) - 0.5F;
            float z = quad.z(vertex) - 0.5F;
            quad.pos(vertex, center + scale * (cosine * x - sine * z),
                    quad.y(vertex) * scale, center + scale * (sine * x + cosine * z));
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
