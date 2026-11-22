package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/** Vertical continuation of a lantern body; no item, entity or tick. */
public final class LanternPartBlock extends Block {
    /** Minecraft integer properties are unsigned: zero is the legacy lower part. */
    public static final IntProperty OFFSET_Y=IntProperty.of("offset_y",0,1);
    public LanternPartBlock(Settings settings) {
        super(settings.dynamicBounds().nonOpaque().noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK));
        setDefaultState(getStateManager().getDefaultState().with(OFFSET_Y,0));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(OFFSET_Y); }
    public static int cellYOffset(BlockState state) { return state.get(OFFSET_Y)==0?-1:1; }
    public static BlockState withCellYOffset(BlockState state,int offset) {
        if(offset!=-1 && offset!=1) throw new IllegalArgumentException("Lantern parts must continue one cell above or below their owner");
        return state.with(OFFSET_Y,offset<0?0:1);
    }
    public static BlockPos resolveAnchorPos(BlockView world,BlockPos pos,BlockState state) {
        if (!(state.getBlock() instanceof LanternPartBlock)) return null;
        int offset=cellYOffset(state);
        BlockPos anchor=pos.up(-offset);BlockState owner=LanternSupport.loadedState(world,anchor);
        return owner.getBlock() instanceof LanternBlock && LanternBlock.needsPart(owner,offset)?anchor:null;
    }
    public static boolean isOwnedBy(BlockState state,BlockPos pos,BlockPos anchor) { return state.getBlock() instanceof LanternPartBlock && pos.up(-cellYOffset(state)).equals(anchor); }
    public static boolean isOrphaned(BlockView world,BlockPos pos,BlockState state) { return state.getBlock() instanceof LanternPartBlock && resolveAnchorPos(world,pos,state)==null; }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        BlockPos anchor=resolveAnchorPos(world,pos,state);
        if (anchor==null) return VoxelShapes.empty();
        BlockState owner=LanternSupport.loadedState(world,anchor);
        return LanternGeometry.placedShape(((LanternBlock)owner.getBlock()).style(),owner,LanternSupport.offset(world,anchor,owner),cellYOffset(state));
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return getOutlineShape(state,world,pos,context); }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos) { return 0; }
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos) { return true; }
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state) {
        BlockPos anchor=resolveAnchorPos(world,pos,state);
        return anchor==null?ItemStack.EMPTY:world.getBlockState(anchor).getBlock().getPickStack(world,anchor,world.getBlockState(anchor));
    }
    @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player) {
        BlockPos anchor=resolveAnchorPos(world,pos,state);
        if (!world.isClient && anchor!=null) world.breakBlock(anchor,!player.isCreative(),player);
        super.onBreak(world,pos,state,player);
    }
}
