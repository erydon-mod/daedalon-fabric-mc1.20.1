package com.oliver.daedalon.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Upgrades old Large placements and removes orphaned parts when their chunks become available. */
public final class LouterionChunkRepair {
    private static final Map<ServerWorld, RepairQueue> QUEUES = new HashMap<>();

    private LouterionChunkRepair() { }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(LouterionChunkRepair::queueLoadedChunk);
        ServerTickEvents.END_WORLD_TICK.register(LouterionChunkRepair::repairAvailable);
        ServerWorldEvents.UNLOAD.register((server, world) -> QUEUES.remove(world));
    }

    private static void queueLoadedChunk(ServerWorld world, WorldChunk chunk) {
        RepairQueue queue = QUEUES.get(world);
        if (queue != null) {
            Set<BlockPos> waiting = queue.waiting.remove(chunk.getPos().toLong());
            if (waiting != null) queue.ready.addAll(waiting);
        }
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || !section.hasAny(LouterionChunkRepair::mayNeedRepair)) continue;
            if (queue == null) queue = QUEUES.computeIfAbsent(world, ignored -> new RepairQueue());
            int sectionY = world.getBottomY() + i * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (mayNeedRepair(section.getBlockState(x, y, z))) {
                            queue.ready.add(new BlockPos(chunk.getPos().getStartX() + x, sectionY + y,
                                    chunk.getPos().getStartZ() + z));
                        }
                    }
                }
            }
        }
    }

    private static boolean mayNeedRepair(BlockState state) {
        return LouterionBasinBlock.isLarge(state) || FountainBasinPartBlock.isLouterionPart(state);
    }

    private static void repairAvailable(ServerWorld world) {
        RepairQueue queue = QUEUES.get(world);
        if (queue == null || queue.ready.isEmpty()) return;
        Set<BlockPos> ready = Set.copyOf(queue.ready);
        queue.ready.clear();
        for (BlockPos pos : ready) {
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (LouterionBasinBlock.isLarge(state)) {
                LouterionBasinBlock.occupyParts(world, pos);
                for (BlockPos offset : LouterionBasinGeometry.LARGE_OFFSETS) {
                    BlockPos target = pos.add(offset);
                    if (!world.isChunkLoaded(target)) queue.waitFor(target, pos);
                }
            } else if (FountainBasinPartBlock.isLouterionPart(state)) {
                BlockPos owner = FountainBasinPartBlock.anchorPos(pos, state);
                if (!world.isChunkLoaded(owner)) {
                    queue.waitFor(owner, pos);
                } else if (FountainBasinPartBlock.resolveAnchorPos(world, pos, state) == null) {
                    BlockState released = state.get(FountainBasinPartBlock.WATERLOGGED)
                            ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                    world.setBlockState(pos, released, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                }
            }
        }
    }

    private static final class RepairQueue {
        private final Set<BlockPos> ready = new HashSet<>();
        private final Map<Long, Set<BlockPos>> waiting = new HashMap<>();

        private void waitFor(BlockPos unloaded, BlockPos repair) {
            waiting.computeIfAbsent(ChunkPos.toLong(unloaded), ignored -> new HashSet<>()).add(repair);
        }
    }
}
