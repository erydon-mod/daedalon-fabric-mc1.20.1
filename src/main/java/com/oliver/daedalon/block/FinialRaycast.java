package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.World;

/** Fitted bases can lie below their owner or across a steep coping's owner offset. */
public final class FinialRaycast {
    private static final java.util.Map<BlockView,Index> WORLDS=new java.util.WeakHashMap<>();
    private static final long[] EMPTY=new long[0];
    private FinialRaycast() { }
    private static final class Index {
        final java.util.Map<Long,it.unimi.dsi.fastutil.longs.LongSet> cells=new java.util.HashMap<>();
        final java.util.Map<Long,long[]> owners=new java.util.HashMap<>();
        final java.util.Map<Long,it.unimi.dsi.fastutil.longs.LongSet> chunks=new java.util.HashMap<>();
    }
    public static void registerServer() {
        ServerChunkEvents.CHUNK_LOAD.register(FinialRaycast::loadChunk);
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk) -> forgetChunk(world,chunk.getPos().x,chunk.getPos().z));
        ServerWorldEvents.UNLOAD.register((server,world) -> clearWorld(world));
    }
    public static void loadChunk(World world,WorldChunk chunk) {
        forgetChunk(world,chunk.getPos().x,chunk.getPos().z);
        var sections=chunk.getSectionArray();
        int bottom=world.getBottomY()>>4;
        for(int section=0;section<sections.length;section++) {
            var data=sections[section];
            if(data==null || !data.hasAny(state -> state.getBlock() instanceof FinialBlock)) continue;
            for(int y=0;y<16;y++) for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                var state=data.getBlockState(x,y,z);
                if(state.getBlock() instanceof FinialBlock)
                    update(world,new BlockPos((chunk.getPos().x<<4)+x,((bottom+section)<<4)+y,(chunk.getPos().z<<4)+z),state);
            }
        }
    }
    public static synchronized void update(BlockView world,BlockPos owner,BlockState state) {
        Index index=WORLDS.get(world);
        if(index==null && !(state.getBlock() instanceof FinialBlock)) return;
        if(index==null) { index=new Index(); WORLDS.put(world,index); }
        remove(index,owner.asLong());
        if(!(state.getBlock() instanceof FinialBlock)) return;
        var bounds=state.getOutlineShape(world,owner,ShapeContext.absent()).getBoundingBox().offset(owner);
        var touched=new it.unimi.dsi.fastutil.longs.LongArrayList();
        for(int y=(int)Math.floor(bounds.minY);y<(int)Math.ceil(bounds.maxY);y++)
            for(int x=(int)Math.floor(bounds.minX);x<(int)Math.ceil(bounds.maxX);x++)
                for(int z=(int)Math.floor(bounds.minZ);z<(int)Math.ceil(bounds.maxZ);z++) {
                    long cell=BlockPos.asLong(x,y,z);
                    index.cells.computeIfAbsent(cell,ignored -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet()).add(owner.asLong());
                    touched.add(cell);
                }
        index.owners.put(owner.asLong(),touched.toLongArray());
        index.chunks.computeIfAbsent(ChunkPos.toLong(owner.getX()>>4,owner.getZ()>>4),
                ignored -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet()).add(owner.asLong());
    }
    private static void remove(Index index,long owner) {
        long[] old=index.owners.remove(owner);
        if(old==null) return;
        BlockPos pos=BlockPos.fromLong(owner);
        long chunk=ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4);
        var inChunk=index.chunks.get(chunk);
        if(inChunk!=null) { inChunk.remove(owner); if(inChunk.isEmpty()) index.chunks.remove(chunk); }
        for(long cell:old) {
            var owners=index.cells.get(cell);
            if(owners==null) continue;
            owners.remove(owner);
            if(owners.isEmpty()) index.cells.remove(cell);
        }
    }
    public static synchronized void forgetChunk(BlockView world,int x,int z) {
        Index index=WORLDS.get(world);
        if(index==null) return;
        var inChunk=index.chunks.get(ChunkPos.toLong(x,z));
        if(inChunk!=null) for(long owner:inChunk.toLongArray()) remove(index,owner);
    }
    public static synchronized void clearWorld(BlockView world) { WORLDS.remove(world); }
    private static synchronized long[] owners(BlockView world,BlockPos cell) {
        Index index=WORLDS.get(world);
        if(index==null) return EMPTY;
        var owners=index.cells.get(cell.asLong());
        return owners==null ? EMPTY : owners.toLongArray();
    }
    public static BlockHitResult closest(BlockView world,Vec3d start,Vec3d end,BlockPos cell,
                                          BlockState support,BlockHitResult best) {
        for(long key:owners(world,cell)) {
            BlockPos owner=BlockPos.fromLong(key);
            BlockState state=world.getBlockState(owner);
            if(!(state.getBlock() instanceof FinialBlock)) continue;
            var hit=state.getOutlineShape(world,owner,ShapeContext.absent()).raycast(start,end,owner);
            if(hit!=null && (best==null || hit.getPos().squaredDistanceTo(start)<best.getPos().squaredDistanceTo(start))) best=hit;
        }
        return best;
    }
}
