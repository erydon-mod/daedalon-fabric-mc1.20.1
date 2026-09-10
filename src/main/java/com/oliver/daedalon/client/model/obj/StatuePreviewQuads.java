package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.SpartanStatueBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cheap vanilla-renderer pilot for Axiom's move/paste preview. Owned by the baked
 * model, so resource reload discards both the cache and its old atlas sprites. */
final class StatuePreviewQuads {
    private static final int MAX_BOXES = 128;
    private final Map<VoxelShape, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    List<BakedQuad> get(BlockState state, Direction face, Sprite sprite) {
        // Unculled faces are emitted exactly once, including outside the anchor cell.
        if (face != null || state == null || !(state.getBlock() instanceof SpartanStatueBlock)) {
            return List.of();
        }
        VoxelShape shape = state.getBlock().getCollisionShape(
                state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
        return cache.computeIfAbsent(shape, key -> bake(key, sprite,
                sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV()));
    }

    static List<BakedQuad> bake(VoxelShape shape, Sprite sprite,
                                float minU, float maxU, float minV, float maxV) {
        if (shape.isEmpty()) return List.of();
        List<Box> boxes = shape.getBoundingBoxes();
        // Keep the cost bounded even if a future collision profile becomes complex.
        if (boxes.size() > MAX_BOXES) boxes = List.of(shape.getBoundingBox());
        List<BakedQuad> result = new ArrayList<>(boxes.size() * 6);
        for (Box box : boxes) {
            float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
            float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
            add(result, sprite, Direction.DOWN, minU, maxU, minV, maxV,
                    x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
            add(result, sprite, Direction.UP, minU, maxU, minV, maxV,
                    x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
            add(result, sprite, Direction.NORTH, minU, maxU, minV, maxV,
                    x1,y1,z0, x1,y0,z0, x0,y0,z0, x0,y1,z0);
            add(result, sprite, Direction.SOUTH, minU, maxU, minV, maxV,
                    x0,y1,z1, x0,y0,z1, x1,y0,z1, x1,y1,z1);
            add(result, sprite, Direction.WEST, minU, maxU, minV, maxV,
                    x0,y1,z0, x0,y0,z0, x0,y0,z1, x0,y1,z1);
            add(result, sprite, Direction.EAST, minU, maxU, minV, maxV,
                    x1,y1,z1, x1,y0,z1, x1,y0,z0, x1,y1,z0);
        }
        return List.copyOf(result);
    }

    private static void add(List<BakedQuad> result, Sprite sprite, Direction face,
                            float minU, float maxU, float minV, float maxV, float... xyz) {
        int[] data = new int[32];
        int normal = ((face.getOffsetX() * 127) & 255)
                | (((face.getOffsetY() * 127) & 255) << 8)
                | (((face.getOffsetZ() * 127) & 255) << 16);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            for (int axis = 0; axis < 3; axis++) {
                data[offset + axis] = Float.floatToRawIntBits(xyz[vertex * 3 + axis]);
            }
            data[offset + 3] = -1;
            data[offset + 4] = Float.floatToRawIntBits(vertex < 2 ? minU : maxU);
            data[offset + 5] = Float.floatToRawIntBits(vertex == 0 || vertex == 3 ? minV : maxV);
            data[offset + 7] = normal;
        }
        result.add(new BakedQuad(data, -1, face, sprite, true));
    }
}
