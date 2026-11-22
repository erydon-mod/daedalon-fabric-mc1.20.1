package com.oliver.daedalon.block;

import com.oliver.daedalon.item.LouterionBasinItem;
import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/** A freestanding fountain whose water is contained inside the bowl. */
public final class LouterionBasinBlock extends FixedDecorBlock implements Waterloggable {
    public static final EnumProperty<TwoSizeDecorBlock.Size> SIZE = TwoSizeDecorBlock.SIZE;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final float LARGE_SCALE = 2.0F;

    public LouterionBasinBlock(Settings settings) {
        super(settings.pistonBehavior(PistonBehavior.BLOCK), Style.LOUTERION_BASIN);
        setDefaultState(getDefaultState().with(SIZE, TwoSizeDecorBlock.Size.SMALL).with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, WATERLOGGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        FluidState fluid = world.getFluidState(pos);
        BlockState state = getDefaultState().with(WATERLOGGED, fluid.getFluid() == Fluids.WATER);
        var properties = context.getStack().getSubNbt("BlockStateTag");
        if (properties != null) {
            state = state.with(SIZE, SIZE.parse(properties.getString("size")).orElse(TwoSizeDecorBlock.Size.SMALL));
        }
        return canPlaceAt(state, world, pos) ? state : null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (!isLarge(state)) return true;
        for (BlockPos offset : LouterionBasinGeometry.LARGE_OFFSETS) {
            BlockPos cell = pos.add(offset);
            if (!canCheckPart(world, cell)) return false;
            BlockState existing = world.getBlockState(cell);
            if (!existing.isAir() && !FountainBasinPartBlock.isOwnedBy(existing, cell, pos)) return false;
        }
        return true;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState previous, boolean notify) {
        super.onBlockAdded(state, world, pos, previous, notify);
        if (!world.isClient && !previous.isOf(this) && isLarge(state)) occupyParts(world, pos);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient) {
            if (next.isOf(this) && isLarge(next)) {
                // Also repairs older Large placements on their next edit, if the footprint is clear.
                occupyParts(world, pos);
            } else if (isLarge(state) && !isLarge(next)) {
                releaseParts(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && isLarge(state)) occupyParts(world, pos);
        return ActionResult.PASS;
    }

    static void occupyParts(World world, BlockPos anchor) {
        FountainBasinPartBlock part = ModBlocks.fountainBasinPart();
        for (BlockPos offset : LouterionBasinGeometry.LARGE_OFFSETS) {
            BlockPos cell = anchor.add(offset);
            if (!canCheckPart(world, cell)) continue;
            BlockState existing = world.getBlockState(cell);
            BlockState desired = part.louterionStateForOffset(offset);
            // Older Large saves had no part cells. Restore the accessible pieces
            // without touching occupied cells, forcing chunk loads, or changing water/size.
            if (existing != desired && (existing.isAir() || FountainBasinPartBlock.isOwnedBy(existing, cell, anchor))) {
                world.setBlockState(cell, desired, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
            }
        }
    }

    private static boolean canCheckPart(WorldView world, BlockPos cell) {
        return !world.isOutOfHeightLimit(cell) && world.getWorldBorder().contains(cell) && world.isChunkLoaded(cell);
    }

    private static void releaseParts(World world, BlockPos anchor) {
        for (BlockPos offset : LouterionBasinGeometry.LARGE_OFFSETS) {
            BlockPos cell = anchor.add(offset);
            if (!canCheckPart(world, cell)) continue;
            BlockState existing = world.getBlockState(cell);
            if (FountainBasinPartBlock.isLouterionPart(existing) && FountainBasinPartBlock.isOwnedBy(existing, cell, anchor)) {
                BlockState released = existing.get(FountainBasinPartBlock.WATERLOGGED)
                        ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                world.setBlockState(cell, released, Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean wet = state.get(WATERLOGGED);
        return isLarge(state) ? LouterionBasinGeometry.part(wet, 0, 0, 0) : LouterionBasinGeometry.outline(false, wet);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return isLarge(state) ? LouterionBasinGeometry.part(false, 0, 0, 0) : LouterionBasinGeometry.collision(false);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        if (isLarge(state) && asItem() instanceof LouterionBasinItem basinItem) {
            return basinItem.largeStack();
        }
        return super.getPickStack(world, pos, state);
    }

    public static boolean isLarge(BlockState state) {
        return state.getBlock() instanceof LouterionBasinBlock && state.get(SIZE) == TwoSizeDecorBlock.Size.LARGE;
    }

    public static VoxelShape fullCollisionShape(BlockState state) {
        return LouterionBasinGeometry.collision(isLarge(state));
    }

    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.get(WATERLOGGED) && fluid == Fluids.WATER;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluid) {
        if (!canFillWithFluid(world, pos, state, fluid.getFluid())) return false;
        // The contained surface is drawn by the OBJ model. Leave the world fluid empty.
        return world.isClient() || world.setBlockState(pos, state.with(WATERLOGGED, true), Block.NOTIFY_ALL);
    }

    @Override
    public ItemStack tryDrainFluid(WorldAccess world, BlockPos pos, BlockState state) {
        if (!state.get(WATERLOGGED)) return ItemStack.EMPTY;
        if (!world.isClient() && !world.setBlockState(pos, state.with(WATERLOGGED, false), Block.NOTIFY_ALL)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.WATER_BUCKET);
    }
}
