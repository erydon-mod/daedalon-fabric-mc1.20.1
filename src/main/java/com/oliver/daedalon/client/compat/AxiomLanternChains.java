package com.oliver.daedalon.client.compat;

import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternPlacement;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Only region edits populate this index; previewing never queries a live world. */
final class AxiomLanternChains {
    private final Map<Long, Link> links = new ConcurrentHashMap<>();

    boolean set(int x, int y, int z, LanternBlock.Size size, boolean canopy) {
        long key = BlockPos.asLong(x, y, z);
        Link next = size == null ? null : new Link(size, canopy);
        Link old = next == null ? links.remove(key) : links.put(key, next);
        return !java.util.Objects.equals(old, next);
    }

    boolean contains(int x, int y, int z) { return links.containsKey(BlockPos.asLong(x, y, z)); }

    Span span(int x, int y, int z, LanternBlock.Size size, boolean ownCanopy) {
        int count = 0;
        boolean canopy = ownCanopy;
        for (int i = 1; i <= LanternPlacement.MAX_CHAIN; i++) {
            Link link = links.get(BlockPos.asLong(x, y + i, z));
            if (link == null || link.size != size) break;
            count++;
            canopy = link.canopy;
        }
        return new Span(count, canopy);
    }

    void clear() { links.clear(); }
    private record Link(LanternBlock.Size size, boolean canopy) {}
    record Span(int count, boolean canopy) {}
}
