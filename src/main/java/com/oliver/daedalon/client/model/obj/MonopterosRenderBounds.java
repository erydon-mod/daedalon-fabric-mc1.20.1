package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;

/** Expands only sections which emitted a Monopteros mesh, never the global view distance.
 * Reads use an immutable primitive-key snapshot: no locks, allocations or world queries
 * during frustum checks. Stale bounds are conservatively retained until chunk unload.
 */
public final class MonopterosRenderBounds {
    private static volatile Long2ObjectMap<Box> sections = Long2ObjectMaps.emptyMap();

    private MonopterosRenderBounds() {}

    public static void register() {
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> forgetChunk(chunk.getPos().x, chunk.getPos().z));
    }

    public static synchronized void mark(BlockPos pos) {
        int x=pos.getX()>>4, y=pos.getY()>>4, z=pos.getZ()>>4;
        long key=ChunkSectionPos.asLong(x,y,z);
        if (sections.containsKey(key)) return;
        Long2ObjectMap<Box> updated=new Long2ObjectOpenHashMap<>(sections);
        // Cover the largest 8m roof at every owner position in this section.
        updated.put(key,new Box(x*16-4.125,y*16-1.125,z*16-4.125,
                x*16+20.125,y*16+23.125,z*16+20.125));
        sections=updated;
    }

    public static Box get(int sectionX,int sectionY,int sectionZ) {
        return sections.get(ChunkSectionPos.asLong(sectionX,sectionY,sectionZ));
    }

    public static synchronized void forgetChunk(int x,int z) {
        if (sections.isEmpty()) return;
        Long2ObjectMap<Box> updated=new Long2ObjectOpenHashMap<>(sections);
        updated.keySet().removeIf((long key) -> ChunkSectionPos.unpackX(key)==x && ChunkSectionPos.unpackZ(key)==z);
        sections=updated;
    }

    public static synchronized void clear() { sections=Long2ObjectMaps.emptyMap(); }
}
