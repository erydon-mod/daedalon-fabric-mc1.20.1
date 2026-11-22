package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/** Thin supports and their adjacent owner cells share accurate lantern targeting. */
public final class LanternRaycast {
    private LanternRaycast() {}
    public static BlockHitResult closest(BlockView world,Vec3d start,Vec3d end,BlockPos cell,BlockState cellState,BlockHitResult best) {
        // Vanilla tests an owner's shape against the entire ray. A translated
        // hit in a later DDA cell must not skip an opaque cell in between.
        if(best!=null && best.getBlockPos().equals(cell) && !contains(cell,best.getPos())
                && wallPart(world,cell,cellState)) best=null;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0) continue;
            BlockPos neighbor=cell.add(dx,0,dz);BlockState state=world.getBlockState(neighbor);
            BlockPos anchor=state.getBlock() instanceof LanternPartBlock?LanternPartBlock.resolveAnchorPos(world,neighbor,state):neighbor;
            if(anchor==null) continue;
            BlockState owner=anchor.equals(neighbor)?state:world.getBlockState(anchor);
            if(!(owner.getBlock() instanceof LanternBlock) || owner.get(LanternBlock.MOUNT)!=LanternBlock.Mount.WALL) continue;
            var shape=state.getOutlineShape(world,neighbor,ShapeContext.absent());
            if(shape.isEmpty()) continue;
            var hit=shape.raycast(start,end,neighbor);
            if(hit!=null && contains(cell,hit.getPos()) && (best==null || hit.getPos().squaredDistanceTo(start)<best.getPos().squaredDistanceTo(start))) best=hit;
        }
        return best;
    }
    private static boolean wallPart(BlockView world,BlockPos pos,BlockState state) {
        if(state.getBlock() instanceof LanternBlock) return state.get(LanternBlock.MOUNT)==LanternBlock.Mount.WALL;
        if(!(state.getBlock() instanceof LanternPartBlock)) return false;
        BlockPos anchor=LanternPartBlock.resolveAnchorPos(world,pos,state);
        return anchor!=null && world.getBlockState(anchor).get(LanternBlock.MOUNT)==LanternBlock.Mount.WALL;
    }
    private static boolean contains(BlockPos cell,Vec3d point) {
        double epsilon=1e-7;
        return point.x>=cell.getX()-epsilon && point.x<=cell.getX()+1+epsilon
                && point.y>=cell.getY()-epsilon && point.y<=cell.getY()+1+epsilon
                && point.z>=cell.getZ()-epsilon && point.z<=cell.getZ()+1+epsilon;
    }
}
