package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A static one-metre suspension extension shared by every lantern material. */
public final class LanternChainBlock extends Block {
    public LanternChainBlock(Settings settings) {
        super(settings.dynamicBounds().nonOpaque().noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK));
        setDefaultState(getStateManager().getDefaultState().with(LanternBlock.SIZE,LanternBlock.Size.LARGE).with(LanternBlock.CANOPY,false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(LanternBlock.SIZE,LanternBlock.CANOPY); }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return LanternGeometry.chain(state.get(LanternBlock.SIZE),state.get(LanternBlock.CANOPY)); }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return getOutlineShape(state,world,pos,context); }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos) { return 0; }
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos) { return true; }
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state) {
        BlockPos owner=LanternPlacement.resolveOwner(world,pos);
        return owner==null?ItemStack.EMPTY:world.getBlockState(owner).getBlock().getPickStack(world,owner,world.getBlockState(owner));
    }
    @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player) {
        BlockPos owner=LanternPlacement.resolveOwner(world,pos);
        if (!world.isClient && owner!=null) world.breakBlock(owner,!player.isCreative(),player);
        super.onBreak(world,pos,state,player);
    }
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved) {
        if (!world.isClient && !(next.getBlock() instanceof LanternChainBlock)) {
            BlockPos owner=LanternPlacement.resolveOwner(world,pos.down());
            if (owner!=null && world.getBlockState(owner).get(LanternBlock.MOUNT)==LanternBlock.Mount.HANGING) world.breakBlock(owner,true);
            LanternPlacement.clearChainAbove(world,pos);
        }
        super.onStateReplaced(state,world,pos,next,moved);
    }
}
