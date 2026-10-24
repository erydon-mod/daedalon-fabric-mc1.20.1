package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** Invisible Large-only interaction cells: no item, mesh, block entity or custom ticking. */
public final class ObeliskPartBlock extends Block implements Waterloggable {
    public static final IntProperty OFFSET_X = IntProperty.of("offset_x", 0, 2);
    public static final IntProperty OFFSET_Y = IntProperty.of("offset_y", 0, 4);
    public static final IntProperty OFFSET_Z = IntProperty.of("offset_z", 0, 2);
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public ObeliskPartBlock(Settings settings) {
        super(settings.dynamicBounds().noBlockBreakParticles());
        setDefaultState(getStateManager().getDefaultState().with(OFFSET_X, 1).with(OFFSET_Y, 0)
                .with(OFFSET_Z, 1).with(WATERLOGGED, false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z, WATERLOGGED);
    }
    public BlockState stateForOffset(BlockPos offset) {
        return getDefaultState().with(OFFSET_X, offset.getX() + 1).with(OFFSET_Y, offset.getY()).with(OFFSET_Z, offset.getZ() + 1);
    }
    public static BlockPos anchorPos(BlockPos pos, BlockState state) {
        return pos.add(1 - state.get(OFFSET_X), -state.get(OFFSET_Y), 1 - state.get(OFFSET_Z));
    }
    public static BlockPos resolveAnchorPos(BlockView world, BlockPos pos, BlockState state) {
        BlockPos anchor = anchorPos(pos, state);
        BlockState owner = world.getBlockState(anchor);
        return owner.getBlock() instanceof SizedDecorBlock && owner.get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.LARGE ? anchor : null;
    }
    public static boolean isOwnedBy(BlockState state, BlockPos pos, BlockPos anchor) {
        return state.getBlock() instanceof ObeliskPartBlock && anchorPos(pos, state).equals(anchor);
    }
    public static boolean isOrphaned(BlockView world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof ObeliskPartBlock && resolveAnchorPos(world, pos, state) == null;
    }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return resolveAnchorPos(world, pos, state) == null ? VoxelShapes.empty()
                : ObeliskPartsGeometry.outline(state.get(OFFSET_X) - 1, state.get(OFFSET_Y), state.get(OFFSET_Z) - 1);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return resolveAnchorPos(world, pos, state) == null ? VoxelShapes.empty()
                : ObeliskPartsGeometry.collision(state.get(OFFSET_X) - 1, state.get(OFFSET_Y), state.get(OFFSET_Z) - 1);
    }
    @Override public boolean canReplace(BlockState state, ItemPlacementContext context) { return isOrphaned(context.getWorld(), context.getBlockPos(), state); }
    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }
    // Lighting may run off-thread: these must never resolve an owner or ask for a dynamic outline.
    @Override public int getOpacity(BlockState state, BlockView world, BlockPos pos) { return 0; }
    @Override public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return true; }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
    @Override public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        BlockPos anchor = resolveAnchorPos(world, pos, state);
        return anchor == null ? ItemStack.EMPTY : world.getBlockState(anchor).getBlock().asItem().getDefaultStack();
    }
    @Override public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos anchor = resolveAnchorPos(world, pos, state);
            if (anchor != null) world.breakBlock(anchor, !player.isCreative(), player);
        }
        super.onBreak(world, pos, state, player);
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && !next.isOf(this)) {
            BlockPos anchor = resolveAnchorPos(world, pos, state);
            if (anchor != null) world.breakBlock(anchor, true);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }
    @Override public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }
    @Override public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.get(WATERLOGGED) && fluid == Fluids.WATER
                && ObeliskPartsGeometry.allowsWater(state.get(OFFSET_X) - 1, state.get(OFFSET_Y), state.get(OFFSET_Z) - 1);
    }
    @Override public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluid) {
        if (!canFillWithFluid(world, pos, state, fluid.getFluid())) return false;
        if (!world.isClient()) {
            world.setBlockState(pos, state.with(WATERLOGGED, true), Block.NOTIFY_ALL);
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return true;
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
                                                         WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        int x = state.get(OFFSET_X) - 1, z = state.get(OFFSET_Z) - 1;
        return state.with(OFFSET_X, FountainPartOffsetTransform.rotatedX(x, z, rotation) + 1)
                .with(OFFSET_Z, FountainPartOffsetTransform.rotatedZ(x, z, rotation) + 1);
    }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(OFFSET_X, FountainPartOffsetTransform.mirroredX(state.get(OFFSET_X) - 1, mirror) + 1)
                .with(OFFSET_Z, FountainPartOffsetTransform.mirroredZ(state.get(OFFSET_Z) - 1, mirror) + 1);
    }
}
