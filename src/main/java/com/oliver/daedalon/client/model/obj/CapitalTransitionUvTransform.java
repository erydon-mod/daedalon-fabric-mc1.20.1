package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Cached, face-handed world projection for the collar only, before material sprite baking. */
final class CapitalTransitionUvTransform implements RenderContext.QuadTransform {
    private static final CapitalTransitionUvTransform[] CACHE = createCache();
    private final int xTile, yTile, zTile, cellX, cellZ;

    private CapitalTransitionUvTransform(int x, int y, int z, int cellX, int cellZ) {
        xTile = x; yTile = Math.floorMod(-y, 6); zTile = z;
        this.cellX = cellX; this.cellZ = cellZ;
    }

    private static CapitalTransitionUvTransform[] createCache() {
        var result = new CapitalTransitionUvTransform[216 * 4];
        for (int x = 0; x < 6; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 6; z++)
            for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++)
                result[(x * 36 + y * 6 + z) * 4 + cx + 2 * cz] = new CapitalTransitionUvTransform(x, y, z, cx, cz);
        return result;
    }

    static CapitalTransitionUvTransform forCell(BlockPos anchor, int cellX, int cellZ) {
        int phase = Math.floorMod(anchor.getX() + cellX, 6) * 36
                + Math.floorMod(anchor.getY(), 6) * 6 + Math.floorMod(anchor.getZ() + cellZ, 6);
        return CACHE[phase * 4 + cellX + 2 * cellZ];
    }

    @Override public boolean transform(MutableQuadView quad) {
        Direction face = quad.nominalFace();
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex) - cellX, y = quad.y(vertex), z = quad.z(vertex) - cellZ;
            float u = (xTile + x) / 6, v = (yTile + 1 - y) / 6;
            if (face != null) switch (face) {
                case NORTH -> u = (6 - xTile - x) / 6;
                case EAST -> u = (6 - zTile - z) / 6;
                case WEST -> u = (zTile + z) / 6;
                case UP -> v = (zTile + z) / 6;
                case DOWN -> v = (6 - zTile - z) / 6;
                default -> { }
            }
            quad.uv(vertex, u, v);
        }
        return true;
    }
}
