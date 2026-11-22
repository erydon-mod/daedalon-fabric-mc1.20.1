package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.WorldChunk;
import java.util.concurrent.ConcurrentHashMap;

/** One adjacent support read per shape/bake; no saved offsets or per-quad queries. */
public final class LanternSupport {
    private record Key(BlockState support,Direction face) {}
    private static final ConcurrentHashMap<Key,Vec3d> OFFSETS=new ConcurrentHashMap<>();
    private LanternSupport() {}
    public static Vec3d offset(BlockView world,BlockPos owner,BlockState state) {
        if(world==null || state.get(LanternBlock.MOUNT)!=LanternBlock.Mount.WALL) return Vec3d.ZERO;
        Direction face=state.get(LanternBlock.FACING);
        BlockPos support=owner.offset(face.getOpposite());BlockState found=loadedState(world,support);
        String type=found.getBlock().getClass().getSimpleName();
        if(!type.equals("LayerVerticalBlock") && !type.equals("LayerVerticalTranslucentBlock")) return Vec3d.ZERO;
        return OFFSETS.computeIfAbsent(new Key(found,face),ignored->{
            var boxes=found.getCollisionShape(world,support,ShapeContext.absent()).getBoundingBoxes();
            return boxes.size()==1?offset(boxes.get(0),face):Vec3d.ZERO;
        });
    }
    /** Shape queries may run on lighting workers; never create or await chunks. */
    static BlockState loadedState(BlockView world,BlockPos pos) {
        if(world==null) return Blocks.VOID_AIR.getDefaultState();
        if(world instanceof ServerWorld serverWorld) {
            if(!serverWorld.getServer().isOnThread()) return Blocks.VOID_AIR.getDefaultState();
            // This get-now API returns null off the server thread and for any
            // chunk whose FULL future has not completed. World#getBlockState
            // instead schedules a synchronous chunk request from workers.
            WorldChunk chunk=serverWorld.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
            return chunk==null?Blocks.VOID_AIR.getDefaultState():chunk.getBlockState(pos);
        }
        return world.getBlockState(pos);
    }
    /** Offset from the adjacent owner boundary to the actual clicked cuboid face. */
    public static Vec3d offset(Box support,Direction face) {
        double x=(support.minX+support.maxX)/2-.5,y=(support.minY+support.maxY)/2-.5,z=(support.minZ+support.maxZ)/2-.5;
        return switch(face) {
            case SOUTH->new Vec3d(x,y,support.maxZ-1);
            case NORTH->new Vec3d(x,y,support.minZ);
            case EAST->new Vec3d(support.maxX-1,y,z);
            case WEST->new Vec3d(support.minX,y,z);
            default->Vec3d.ZERO;
        };
    }
}
