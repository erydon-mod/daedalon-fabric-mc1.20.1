package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Cached, face-handed tile-unit projection for fitted capitals before blockSurface sprite baking. */
final class CapitalTransitionUvTransform implements RenderContext.QuadTransform {
    private static final int CELL_VARIANTS = 4 * 3 * 4;
    private static final CapitalTransitionUvTransform[] CACHE = createCache();
    private final int xTile, yTile, zTile, cellX, cellY, cellZ;

    private CapitalTransitionUvTransform(int x, int y, int z, int cellX, int cellY, int cellZ) {
        xTile = x; yTile = Math.floorMod(-y, 6); zTile = z;
        this.cellX = cellX; this.cellY = cellY; this.cellZ = cellZ;
    }

    private static CapitalTransitionUvTransform[] createCache() {
        var result = new CapitalTransitionUvTransform[216 * CELL_VARIANTS];
        for (int x = 0; x < 6; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 6; z++)
            for (int cx = -1; cx <= 2; cx++) for (int cy = 0; cy < 3; cy++) for (int cz = -1; cz <= 2; cz++)
                result[(x * 36 + y * 6 + z) * CELL_VARIANTS + cellIndex(cx, cy, cz)] =
                        new CapitalTransitionUvTransform(x, y, z, cx, cy, cz);
        return result;
    }

    static CapitalTransitionUvTransform forCell(BlockPos anchor, int cellX, int cellZ) {
        return forCell(anchor, cellX, 0, cellZ);
    }

    static CapitalTransitionUvTransform forCell(BlockPos anchor, int cellX, int cellY, int cellZ) {
        if (cellX < -1 || cellX > 2 || cellY < 0 || cellY > 2 || cellZ < -1 || cellZ > 2)
            throw new IllegalArgumentException("Capital texture cell outside the current fitted geometry bounds");
        int phase = Math.floorMod(anchor.getX() + cellX, 6) * 36
                + Math.floorMod(anchor.getY() + cellY, 6) * 6 + Math.floorMod(anchor.getZ() + cellZ, 6);
        return CACHE[phase * CELL_VARIANTS + cellIndex(cellX, cellY, cellZ)];
    }

    private static int cellIndex(int x, int y, int z) { return x + 1 + y * 4 + (z + 1) * 12; }

    /** UV-only metadata for a cached patch. Lighting face and terrain normals remain untouched. */
    static void markProjection(MutableQuadView quad, Direction face) {
        for (int vertex = 0; vertex < 4; vertex++) quad.uv(vertex, -1 - face.getId(), -1);
    }

    @Override public boolean transform(MutableQuadView quad) {
        Direction face = quad.nominalFace();
        float encoded = -quad.u(0) - 1;
        if (quad.v(0) == -1 && encoded >= 0 && encoded <= 5 && encoded == (int) encoded)
            face = Direction.byId((int) encoded);
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex) - cellX, y = quad.y(vertex) - cellY, z = quad.z(vertex) - cellZ;
            // Capitals use blockSurface: its material transform supplies U/12 and V/6.
            // Dividing here as well would make the collar six times too coarse.
            float u = xTile + x, v = yTile + 1 - y;
            if (face != null) switch (face) {
                case NORTH -> u = 6 - xTile - x;
                case EAST -> u = 6 - zTile - z;
                case WEST -> u = zTile + z;
                case UP -> v = zTile + z;
                case DOWN -> v = 6 - zTile - z;
                default -> { }
            }
            quad.uv(vertex, u, v);
        }
        return true;
    }
}
