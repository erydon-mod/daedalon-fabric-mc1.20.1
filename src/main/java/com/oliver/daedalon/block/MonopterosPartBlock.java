package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Invisible static collision/selection cells: no block entities, renderer, fluids or ticks. */
public final class MonopterosPartBlock extends Block {
    public static final IntProperty X=IntProperty.of("offset_x",0,8);
    public static final IntProperty Y=IntProperty.of("offset_y",0,5);
    public static final IntProperty Z=IntProperty.of("offset_z",0,8);
    public MonopterosPartBlock(Settings settings) { super(settings.dynamicBounds()); }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(X,Y,Z); }
    public BlockState stateForOffset(BlockPos offset) {
        return getDefaultState().with(X,offset.getX()+4).with(Y,offset.getY()).with(Z,offset.getZ()+4);
    }
    public static BlockPos anchorPos(BlockPos pos,BlockState state) { return pos.add(4-state.get(X),-state.get(Y),4-state.get(Z)); }
    public static BlockPos resolveAnchorPos(BlockView world,BlockPos pos,BlockState state) {
        BlockPos anchor=anchorPos(pos,state);
        return world.getBlockState(anchor).getBlock() instanceof MonopterosBlock ? anchor : null;
    }
    public static boolean isOwnedBy(BlockState state,BlockPos pos,BlockPos anchor) {
        return state.getBlock() instanceof MonopterosPartBlock && anchorPos(pos,state).equals(anchor);
    }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        BlockState anchor=world.getBlockState(anchorPos(pos,state));
        if (!(anchor.getBlock() instanceof MonopterosBlock)) return VoxelShapes.empty();
        return MonopterosGeometry.part(anchor.get(MonopterosBlock.DIAMETER),state.get(X)-4,state.get(Y),state.get(Z)-4);
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return getOutlineShape(state,world,pos,context);
    }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos) { return 0; }
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos) { return true; }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state) {
        BlockPos anchor=resolveAnchorPos(world,pos,state);
        return anchor==null ? ItemStack.EMPTY : world.getBlockState(anchor).getBlock().asItem().getDefaultStack();
    }
    @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player) {
        if (!world.isClient) {
            BlockPos anchor=resolveAnchorPos(world,pos,state);
            if (anchor!=null) world.breakBlock(anchor,!player.isCreative(),player);
        }
        super.onBreak(world,pos,state,player);
    }
    @Override public BlockState rotate(BlockState state,BlockRotation rotation) {
        int x=state.get(X)-4,z=state.get(Z)-4;
        return state.with(X,FountainPartOffsetTransform.rotatedX(x,z,rotation)+4)
                .with(Z,FountainPartOffsetTransform.rotatedZ(x,z,rotation)+4);
    }
    @Override public BlockState mirror(BlockState state,BlockMirror mirror) {
        return state.with(X,FountainPartOffsetTransform.mirroredX(state.get(X)-4,mirror)+4)
                .with(Z,FountainPartOffsetTransform.mirroredZ(state.get(Z)-4,mirror)+4);
    }
}
