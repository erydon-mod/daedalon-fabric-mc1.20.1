package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.registry.tag.FluidTags;
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

/** A ground-anchored obelisk available at one-, two-, three- and five-block heights. */
public final class SizedDecorBlock extends Block {
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    private static final VoxelShape[] SHAPES = createShapes();

    public SizedDecorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(SIZE, Size.MEDIUM));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(SIZE); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(SIZE) == Size.EXTRA_LARGE ? ObeliskPartsGeometry.outline(0, 0, 0)
                : SHAPES[state.get(SIZE).ordinal()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(SIZE) == Size.EXTRA_LARGE ? ObeliskPartsGeometry.collision(0, 0, 0)
                : getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        var properties = context.getStack().getSubNbt("BlockStateTag");
        if (properties != null && Size.EXTRA_LARGE.asString().equals(properties.getString("size"))) {
            BlockState candidate = getDefaultState().with(SIZE, Size.EXTRA_LARGE);
            return canChangeSize(context.getWorld(), context.getBlockPos(), candidate) ? candidate : null;
        }
        return getDefaultState();
    }

    /** Existing sizes retain their owner-only behaviour; the five-block model reserves its actual cells. */
    public static boolean canChangeSize(WorldAccess world, BlockPos anchor, BlockState state) {
        if (state.get(SIZE) != Size.EXTRA_LARGE) return true;
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            BlockPos part = anchor.add(offset);
            if (world.isOutOfHeightLimit(part) || !world.getWorldBorder().contains(part)) return false;
            if (part.equals(anchor)) continue;
            BlockState existing = world.getBlockState(part);
            if (!existing.isAir() && !existing.isReplaceable()
                    && !ObeliskPartBlock.isOwnedBy(existing, part, anchor)
                    && !ObeliskPartBlock.isOrphaned(world, part, existing)) return false;
        }
        return true;
    }

    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState previous, boolean notify) {
        super.onBlockAdded(state, world, pos, previous, notify);
        if (!world.isClient && !previous.isOf(this) && state.get(SIZE) == Size.EXTRA_LARGE) syncParts(world, pos, state);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && (state.get(SIZE) == Size.EXTRA_LARGE
                || next.getBlock() instanceof SizedDecorBlock && next.get(SIZE) == Size.EXTRA_LARGE)) {
            if (next.getBlock() instanceof SizedDecorBlock) syncParts(world, pos, next);
            else if (state.get(SIZE) == Size.EXTRA_LARGE) clearParts(world, pos);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }

    private static void syncParts(World world, BlockPos anchor, BlockState state) {
        if (state.get(SIZE) != Size.EXTRA_LARGE) {
            clearParts(world, anchor);
            return;
        }
        if (!canChangeSize(world, anchor, state)) return;
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            if (offset.equals(BlockPos.ORIGIN)) continue;
            BlockPos pos = anchor.add(offset);
            BlockState existing = world.getBlockState(pos);
            if (ObeliskPartBlock.isOwnedBy(existing, pos, anchor)) continue;
            boolean wet = ObeliskPartsGeometry.allowsWater(offset.getX(), offset.getY(), offset.getZ())
                    && world.getFluidState(pos).isIn(FluidTags.WATER);
            world.setBlockState(pos, ModBlocks.obeliskosPart().stateForOffset(offset)
                    .with(ObeliskPartBlock.WATERLOGGED, wet), Block.NOTIFY_ALL);
        }
    }

    private static void clearParts(World world, BlockPos anchor) {
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            if (offset.equals(BlockPos.ORIGIN)) continue;
            BlockPos pos = anchor.add(offset);
            BlockState current = world.getBlockState(pos);
            if (ObeliskPartBlock.isOwnedBy(current, pos, anchor)) {
                world.setBlockState(pos, current.get(ObeliskPartBlock.WATERLOGGED)
                        ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    public static VoxelShape fullShape(Size size) { return SHAPES[size.ordinal()]; }

    private static VoxelShape[] createShapes() {
        VoxelShape[] result = new VoxelShape[Size.values().length];
        for (Size size : Size.values()) {
            double width = 0.758984466 * size.scale();
            result[size.ordinal()] = VoxelShapes.cuboid(
                    0.5 - width * 0.5, 0.0, 0.5 - width * 0.5,
                    0.5 + width * 0.5, size == Size.EXTRA_LARGE ? 5.0 : 3.0 * size.scale(), 0.5 + width * 0.5
            );
        }
        return result;
    }

    public enum Size implements StringIdentifiable {
        SMALL("small", 1.0F / 3.0F),
        MEDIUM("medium", 2.0F / 3.0F),
        LARGE("large", 1.0F),
        EXTRA_LARGE("extra_large", 5.0F / 3.0F);
        private final String name;
        private final float scale;
        Size(String name, float scale) { this.name = name; this.scale = scale; }
        public float scale() { return scale; }
        @Override public String asString() { return name; }
        @Override public String toString() { return name; }
    }
}
