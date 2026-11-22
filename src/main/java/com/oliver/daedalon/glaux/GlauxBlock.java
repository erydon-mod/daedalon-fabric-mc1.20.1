package com.oliver.daedalon.glaux;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/** One fixed two-metre bronze ornament; vanilla two-half placement/drop semantics. */
public final class GlauxBlock extends TallPlantBlock implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public GlauxBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(HALF, DoubleBlockHalf.LOWER).with(FACING, Direction.SOUTH));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder); builder.add(FACING);
    }
    @Override public @Nullable BlockState getPlacementState(ItemPlacementContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = world.getBlockState(pos.down());
            return lower.isOf(this) && lower.get(HALF) == DoubleBlockHalf.LOWER && lower.get(FACING) == state.get(FACING);
        }
        return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP);
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighborState,WorldAccess world,BlockPos pos,BlockPos neighborPos) {
        boolean paired=state.get(HALF)==DoubleBlockHalf.LOWER?direction==Direction.UP:direction==Direction.DOWN;
        if(paired&&neighborState.isOf(this)&&neighborState.get(HALF)!=state.get(HALF))
            return state.with(FACING,neighborState.get(FACING));
        return super.getStateForNeighborUpdate(state,direction,neighborState,world,pos,neighborPos);
    }
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new GlauxBlockEntity(pos, state) : null;
    }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking() || player.getMainHandStack().isOf(net.minecraft.item.Items.DEBUG_STICK)
                || player.getOffHandStack().isOf(net.minecraft.item.Items.DEBUG_STICK)) return ActionResult.PASS;
        return ActionResult.SUCCESS;
    }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return rotate(state, mirror.getRotation(state.get(FACING))); }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return GlauxShape.get(state.get(HALF) == DoubleBlockHalf.UPPER, state.get(FACING));
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return getOutlineShape(state, world, pos, context); }
    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }
    public static float rotationDegrees(Direction facing) {
        return switch (facing) { case SOUTH -> 0; case EAST -> 90; case NORTH -> 180; case WEST -> 270; default -> 0; };
    }
}
