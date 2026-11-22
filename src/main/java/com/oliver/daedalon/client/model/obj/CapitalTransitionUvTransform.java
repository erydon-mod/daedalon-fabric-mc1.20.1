package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Cached, face-handed tile-unit projection for fitted capitals before blockSurface sprite baking. */
final class CapitalTransitionUvTransform implements RenderContext.QuadTransform {
    private static final int MIN_HORIZONTAL_CELL = -4, MAX_HORIZONTAL_CELL = 5;
    private static final int MIN_VERTICAL_CELL = -3, MAX_VERTICAL_CELL = 2;
    private static final int HORIZONTAL_CELLS = MAX_HORIZONTAL_CELL - MIN_HORIZONTAL_CELL + 1;
    private static final int VERTICAL_CELLS = MAX_VERTICAL_CELL - MIN_VERTICAL_CELL + 1;
    // Allocate phase arrays only for cells reached by a prepared mesh, then publish
    // each immutable transform once. Warm lookups need no allocation or locks.
    private static final AtomicReferenceArray<AtomicReferenceArray<CapitalTransitionUvTransform>> CACHE =
            new AtomicReferenceArray<>(HORIZONTAL_CELLS * VERTICAL_CELLS * HORIZONTAL_CELLS);
    private final int xTile, yTile, zTile, cellX, cellY, cellZ;

    private CapitalTransitionUvTransform(int x, int y, int z, int cellX, int cellY, int cellZ) {
        xTile = x; yTile = Math.floorMod(-y, 6); zTile = z;
        this.cellX = cellX; this.cellY = cellY; this.cellZ = cellZ;
    }

    static CapitalTransitionUvTransform forCell(BlockPos anchor, int cellX, int cellZ) {
        return forCell(anchor, cellX, 0, cellZ);
    }

    static CapitalTransitionUvTransform forCell(BlockPos anchor, int cellX, int cellY, int cellZ) {
        if (cellX < MIN_HORIZONTAL_CELL || cellX > MAX_HORIZONTAL_CELL
                || cellY < MIN_VERTICAL_CELL || cellY > MAX_VERTICAL_CELL
                || cellZ < MIN_HORIZONTAL_CELL || cellZ > MAX_HORIZONTAL_CELL)
            throw new IllegalArgumentException("Capital texture cell outside the current fitted geometry bounds");
        int index = cellIndex(cellX, cellY, cellZ);
        var phases = CACHE.get(index);
        if (phases == null) {
            var created = new AtomicReferenceArray<CapitalTransitionUvTransform>(216);
            phases = CACHE.compareAndSet(index, null, created) ? created : CACHE.get(index);
        }
        int x = Math.floorMod(anchor.getX() + cellX, 6), y = Math.floorMod(anchor.getY() + cellY, 6);
        int z = Math.floorMod(anchor.getZ() + cellZ, 6), phase = x * 36 + y * 6 + z;
        var transform = phases.get(phase);
        if (transform == null) {
            var created = new CapitalTransitionUvTransform(x, y, z, cellX, cellY, cellZ);
            transform = phases.compareAndSet(phase, null, created) ? created : phases.get(phase);
        }
        return transform;
    }

    private static int cellIndex(int x, int y, int z) {
        return x - MIN_HORIZONTAL_CELL + HORIZONTAL_CELLS * (y - MIN_VERTICAL_CELL
                + VERTICAL_CELLS * (z - MIN_HORIZONTAL_CELL));
    }

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
