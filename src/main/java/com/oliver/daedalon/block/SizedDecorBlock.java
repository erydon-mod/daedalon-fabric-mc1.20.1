package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** A ground-anchored obelisk with Small, Medium and Large heights of two, three and five blocks. */
public final class SizedDecorBlock extends Block {
    public static final EnumProperty<Size> SIZE = new EnumProperty<>("size", Size.class, java.util.List.of(Size.values())) {
        @Override public java.util.Optional<Size> parse(String name) {
            // The unpublished four-size review build used this value for the same five-block model.
            return super.parse("extra_large".equals(name) ? "large" : name);
        }
    };
    public static final BooleanProperty OFFSET = BooleanProperty.of("offset");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape[] SHAPES = createShapes();
    private static final VoxelShape[][] OFFSET_SHAPES = createOffsetShapes();

    public SizedDecorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(SIZE, Size.MEDIUM)
                .with(OFFSET, false).with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(SIZE, OFFSET, FACING); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = collisionShape(state);
        if (!state.get(OFFSET)) return shape;
        return VoxelShapes.union(shape, context.isHolding(Items.DEBUG_STICK)
                ? DecorShapeTransforms.DEBUG_EDIT_TARGET : DecorShapeTransforms.TARGET_POST).simplify();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return collisionShape(state);
    }

    @Override public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return collisionShape(state);
    }

    private static VoxelShape collisionShape(BlockState state) {
        return state.get(SIZE) == Size.LARGE ? ObeliskPartsGeometry.collision(state, 0, 0, 0) : fullShape(state);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState candidate = getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
        var properties = context.getStack().getSubNbt("BlockStateTag");
        if (properties != null) {
            candidate = candidate.with(SIZE, SIZE.parse(properties.getString("size")).orElse(Size.MEDIUM))
                    .with(OFFSET, OFFSET.parse(properties.getString("offset")).orElse(false))
                    .with(FACING, FACING.parse(properties.getString("facing")).orElse(candidate.get(FACING)));
        }
        return canChangeSize(context.getWorld(), context.getBlockPos(), candidate) ? candidate : null;
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(FACING)));
    }

    /** Small and Medium retain their owner-only behaviour; the five-block model reserves its actual cells. */
    public static boolean canChangeSize(WorldAccess world, BlockPos anchor, BlockState state) {
        if (state.get(SIZE) != Size.LARGE) return true;
        for (BlockPos offset : ObeliskPartsGeometry.offsets(state)) {
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
        if (!world.isClient && !previous.isOf(this) && state.get(SIZE) == Size.LARGE) syncParts(world, pos, state);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && (state.get(SIZE) == Size.LARGE
                || next.getBlock() instanceof SizedDecorBlock && next.get(SIZE) == Size.LARGE)) {
            if (next.getBlock() instanceof SizedDecorBlock) syncParts(world, pos, next);
            else if (state.get(SIZE) == Size.LARGE) clearParts(world, pos);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }

    private static void syncParts(World world, BlockPos anchor, BlockState state) {
        if (state.get(SIZE) != Size.LARGE) {
            clearParts(world, anchor);
            return;
        }
        if (!canChangeSize(world, anchor, state)) return;
        // Remove only cells owned by this anchor which the new footprint no longer needs.
        // Owner resolution checks the new footprint, so removing an old part cannot break the monument.
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            if (offset.equals(BlockPos.ORIGIN) || ObeliskPartsGeometry.contains(state, offset)) continue;
            restoreOwnedCell(world, anchor.add(offset), anchor);
        }
        for (BlockPos offset : ObeliskPartsGeometry.offsets(state)) {
            if (offset.equals(BlockPos.ORIGIN)) continue;
            BlockPos pos = anchor.add(offset);
            BlockState existing = world.getBlockState(pos);
            boolean allowsWater = ObeliskPartsGeometry.allowsWater(state, offset.getX(), offset.getY(), offset.getZ());
            if (ObeliskPartBlock.isOwnedBy(existing, pos, anchor)) {
                // Moving back out of an alcove can fill a previously partial
                // helper cell. Retain edge water, but never waterlog a full cell.
                if (existing.get(ObeliskPartBlock.WATERLOGGED) && !allowsWater) {
                    world.setBlockState(pos, existing.with(ObeliskPartBlock.WATERLOGGED, false), Block.NOTIFY_ALL);
                }
                continue;
            }
            boolean wet = allowsWater
                    && world.getFluidState(pos).isIn(FluidTags.WATER);
            world.setBlockState(pos, ModBlocks.obeliskosPart().stateForOffset(offset)
                    .with(ObeliskPartBlock.WATERLOGGED, wet), Block.NOTIFY_ALL);
        }
    }

    private static void clearParts(World world, BlockPos anchor) {
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            if (offset.equals(BlockPos.ORIGIN)) continue;
            BlockPos pos = anchor.add(offset);
            restoreOwnedCell(world, pos, anchor);
        }
    }

    private static void restoreOwnedCell(World world, BlockPos pos, BlockPos anchor) {
        BlockState current = world.getBlockState(pos);
        if (ObeliskPartBlock.isOwnedBy(current, pos, anchor)) {
            world.setBlockState(pos, current.get(ObeliskPartBlock.WATERLOGGED)
                    ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    public static VoxelShape fullShape(Size size) { return SHAPES[size.ordinal()]; }

    public static VoxelShape fullShape(BlockState state) {
        return state.get(OFFSET) ? OFFSET_SHAPES[state.get(SIZE).ordinal()]
                [DecorShapeTransforms.horizontalIndex(state.get(FACING))] : fullShape(state.get(SIZE));
    }

    private static VoxelShape[][] createOffsetShapes() {
        VoxelShape[][] result = new VoxelShape[Size.values().length][4];
        for (Size size : Size.values()) for (Direction facing : Direction.Type.HORIZONTAL) {
            result[size.ordinal()][DecorShapeTransforms.horizontalIndex(facing)] = fullShape(size).offset(
                    facing.getOffsetX() * (double) DecorShapeTransforms.OFFSET_DISTANCE,
                    DecorShapeTransforms.OFFSET_BASE_Y,
                    facing.getOffsetZ() * (double) DecorShapeTransforms.OFFSET_DISTANCE);
        }
        return result;
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] result = new VoxelShape[Size.values().length];
        for (Size size : Size.values()) {
            double width = 0.758984466 * size.scale();
            result[size.ordinal()] = VoxelShapes.cuboid(
                    0.5 - width * 0.5, 0.0, 0.5 - width * 0.5,
                    0.5 + width * 0.5, size == Size.LARGE ? 5.0 : 3.0 * size.scale(), 0.5 + width * 0.5
            );
        }
        return result;
    }

    public enum Size implements StringIdentifiable {
        SMALL("small", 2.0F / 3.0F),
        MEDIUM("medium", 1.0F),
        LARGE("large", 5.0F / 3.0F);
        private final String name;
        private final float scale;
        Size(String name, float scale) { this.name = name; this.scale = scale; }
        public float scale() { return scale; }
        @Override public String asString() { return name; }
        @Override public String toString() { return name; }
    }
}
