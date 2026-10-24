package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.DecorOrientation;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.Direction;

/** Eight cached turns of a bench; positions and normals share the same rotation. */
final class DecorOrientationTransform implements RenderContext.QuadTransform {
    private static final DecorOrientationTransform[] CACHE = new DecorOrientationTransform[8];
    static {
        for (int turn = 0; turn < CACHE.length; turn++) CACHE[turn] = new DecorOrientationTransform(turn);
    }
    private final int turn;
    private DecorOrientationTransform(int turn) { this.turn = turn; }
    static DecorOrientationTransform forTurn(int turn) { return CACHE[Math.floorMod(turn, 8)]; }

    @Override
    public boolean transform(MutableQuadView quad) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex) - 0.5F, z = quad.z(vertex) - 0.5F;
            quad.pos(vertex, 0.5F + DecorOrientation.rotateX(x, z, turn), quad.y(vertex),
                    0.5F + DecorOrientation.rotateZ(x, z, turn));
            if (quad.hasNormal(vertex)) {
                float nx = quad.normalX(vertex), nz = quad.normalZ(vertex);
                quad.normal(vertex, DecorOrientation.rotateX(nx, nz, turn), quad.normalY(vertex),
                        DecorOrientation.rotateZ(nx, nz, turn));
            }
        }
        quad.cullFace(null);
        Direction face = quad.nominalFace();
        if (face != null) quad.nominalFace(Direction.getFacing(
                DecorOrientation.rotateX(face.getOffsetX(), face.getOffsetZ(), turn), face.getOffsetY(),
                DecorOrientation.rotateZ(face.getOffsetX(), face.getOffsetZ(), turn)));
        return true;
    }
}
