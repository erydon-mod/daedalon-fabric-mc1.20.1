package com.oliver.daedalon.block;

import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** A static hollow roof. Interaction cells are changed only on placement, resize or removal. */
public final class MonopterosBlock extends Block {
    public static final EnumProperty<Diameter> DIAMETER=EnumProperty.of("diameter",Diameter.class);
    public MonopterosBlock(Settings settings) {
        super(settings.pistonBehavior(PistonBehavior.BLOCK));
        setDefaultState(getStateManager().getDefaultState().with(DIAMETER,Diameter.SIX));
        MonopterosGeometry.offsets(Diameter.SIX);
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(DIAMETER); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return canOccupy(context.getWorld(),context.getBlockPos(),getDefaultState()) ? getDefaultState() : null;
    }
    public static boolean canOccupy(WorldAccess world,BlockPos pos,BlockState state) {
        for (BlockPos offset:MonopterosGeometry.offsets(state.get(DIAMETER))) {
            BlockPos part=pos.add(offset);
            if (world.isOutOfHeightLimit(part) || !world.getWorldBorder().contains(part)) return false;
            BlockState existing=world.getBlockState(part);
            if (!existing.isAir() && !MonopterosPartBlock.isOwnedBy(existing,part,pos)) return false;
        }
        return true;
    }
    @Override public void onBlockAdded(BlockState state,World world,BlockPos pos,BlockState oldState,boolean notify) {
        super.onBlockAdded(state,world,pos,oldState,notify);
        if (!world.isClient && !oldState.isOf(this)) sync(world,pos,state);
    }
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved) {
        if (!world.isClient) {
            if (next.isOf(this)) sync(world,pos,next);
            else clear(world,pos);
        }
        super.onStateReplaced(state,world,pos,next,moved);
    }
    private static void sync(World world,BlockPos pos,BlockState state) {
        // Check once before any writes so a resize cannot overwrite neighbouring builds.
        if (!canOccupy(world,pos,state)) return;
        Diameter size=state.get(DIAMETER);
        // Include legacy cells from the removed ornament. This bounded cleanup only
        // runs on edits, so existing roofs can refresh without ticking or load scans.
        for (BlockPos offset:BlockPos.iterate(-4,0,-4,4,5,4)) {
            if (!MonopterosGeometry.part(size,offset.getX(),offset.getY(),offset.getZ()).isEmpty()) continue;
            BlockPos part=pos.add(offset);
            if (MonopterosPartBlock.isOwnedBy(world.getBlockState(part),part,pos)) world.removeBlock(part,false);
        }
        for (BlockPos offset:MonopterosGeometry.offsets(size)) {
            world.setBlockState(pos.add(offset),ModBlocks.monopterosPart().stateForOffset(offset),Block.NOTIFY_ALL);
        }
    }
    private static void clear(World world,BlockPos pos) {
        for (BlockPos offset:BlockPos.iterate(-4,0,-4,4,5,4)) {
            BlockPos part=pos.add(offset);
            if (MonopterosPartBlock.isOwnedBy(world.getBlockState(part),part,pos)) world.removeBlock(part,false);
        }
    }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return MonopterosGeometry.part(state.get(DIAMETER),0,0,0);
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return getOutlineShape(state,world,pos,context);
    }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    public enum Diameter implements StringIdentifiable {
        FOUR(4,3),SIX(6,4),EIGHT(8,5);
        public final int metres;
        public final int height;
        Diameter(int metres,int height) { this.metres=metres; this.height=height; }
        @Override public String asString() { return Integer.toString(metres); }
        @Override public String toString() { return asString(); }
    }
}
