package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.event.GameEvent;

/** Hollow planter with contained earth or water and one shared mesh per width. */
public final class AnthophorosBlock extends BenchBlock implements Waterloggable {
    public static final EnumProperty<Contents> CONTENTS = EnumProperty.of("contents", Contents.class);
    public static final double HEIGHT = 1.0;
    public static final double DEPTH = 1.188486;
    public static final double FLOOR_HEIGHT = 0.26;
    private static final VoxelShape[][] SHAPES = createShapes(shellBoxes());

    public AnthophorosBlock(Settings settings) {
        super(settings, SHAPES);
        setDefaultState(getDefaultState().with(CONTENTS, Contents.EMPTY));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(CONTENTS);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        Contents contents = state.get(CONTENTS);
        boolean addingEarth = contents.canFillWith(Contents.EARTH)
                && (held.isOf(Items.DIRT) || held.isOf(Items.COARSE_DIRT) || held.isOf(Items.ROOTED_DIRT));
        boolean removingEarth = contents == Contents.EARTH && held.getItem() instanceof ShovelItem;
        if (!addingEarth && !removingEarth) return ActionResult.PASS;
        if (!world.isClient && world.setBlockState(pos,
                state.with(CONTENTS, addingEarth ? Contents.EARTH : Contents.EMPTY), Block.NOTIFY_ALL)) {
            if (!player.getAbilities().creativeMode) {
                if (addingEarth) {
                    held.decrement(1);
                } else {
                    Block.dropStack(world, pos, new ItemStack(Items.DIRT));
                    held.damage(1, player, user -> user.sendToolBreakStatus(hand));
                }
            }
            world.playSound(null, pos, addingEarth ? SoundEvents.BLOCK_GRAVEL_PLACE : SoundEvents.BLOCK_GRAVEL_BREAK,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return state.get(CONTENTS).canFillWith(Contents.WATER) && fluid == Fluids.WATER;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!canFillWithFluid(world, pos, state, fluidState.getFluid())) return false;
        // Like the fountain, this is contained water: do not expose an ordinary
        // FluidState or schedule water ticks that would spill through the shell.
        return world.isClient() || world.setBlockState(pos, state.with(CONTENTS, Contents.WATER), Block.NOTIFY_ALL);
    }

    @Override
    public ItemStack tryDrainFluid(WorldAccess world, BlockPos pos, BlockState state) {
        if (state.get(CONTENTS) != Contents.WATER) return ItemStack.EMPTY;
        if (!world.isClient() && !world.setBlockState(pos, state.with(CONTENTS, Contents.EMPTY), Block.NOTIFY_ALL)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.WATER_BUCKET);
    }

    public enum Contents implements StringIdentifiable {
        EMPTY("empty"), EARTH("earth"), WATER("water");

        private final String id;

        Contents(String id) {
            this.id = id;
        }

        /** Filling never replaces existing contents or accepts an empty fill. */
        public boolean canFillWith(Contents fill) {
            return this == EMPTY && (fill == EARTH || fill == WATER);
        }

        @Override
        public String asString() {
            return id;
        }
    }

    private static double[][][] shellBoxes() {
        double[][][] widths = new double[3][][];
        for (int width = 2; width <= 4; width++) {
            double half = width / 2.0;
            double depth = DEPTH / 2.0;
            // Five cached boxes cover the floor and walls, leaving the interior
            // open. No mesh scans, controllers, neighbour updates or ticks.
            widths[width - 2] = new double[][]{
                    {-half, 0, -depth, half, FLOOR_HEIGHT, depth},
                    {-half, FLOOR_HEIGHT, -depth, half, HEIGHT, -0.40},
                    {-half, FLOOR_HEIGHT, 0.40, half, HEIGHT, depth},
                    {-half, FLOOR_HEIGHT, -0.40, -half + 0.30, HEIGHT, 0.40},
                    {half - 0.30, FLOOR_HEIGHT, -0.40, half, HEIGHT, 0.40}
            };
        }
        return widths;
    }
}
