package com.oliver.daedalon.client.compat;

import com.oliver.daedalon.block.LanternSupport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Selected cuboids use region coordinates, independently of the live world. */
final class AxiomLanternSupports {
    private final Map<Long, Box> boxes = new ConcurrentHashMap<>();

    boolean set(int x, int y, int z, Box box) {
        long key = BlockPos.asLong(x, y, z);
        Box old = box == null ? boxes.remove(key) : boxes.put(key, box);
        return !java.util.Objects.equals(old, box);
    }

    Vec3d offset(int x, int y, int z, Direction facing) {
        Box support = boxes.get(BlockPos.asLong(x - facing.getOffsetX(), y, z - facing.getOffsetZ()));
        return support == null ? Vec3d.ZERO : LanternSupport.offset(support, facing);
    }

    void clear() { boxes.clear(); }
}
