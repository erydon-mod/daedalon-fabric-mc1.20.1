package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** The wall-facing Krene fountain at its approved fixed size. */
public final class FacingDecorBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final float MODEL_SCALE = 2.0F;
    /** Fitted source-space shift that puts Krene's rear plane on the block boundary. */
    public static final float MODEL_REAR_SHIFT = -0.0540768F;
    private static final VoxelShape[] SHAPES = createShapes();

    public FacingDecorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[horizontalIndex(state.get(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    public static int rotationStepsFromSouth(Direction facing) {
        return Math.floorMod(horizontalIndex(facing) - horizontalIndex(Direction.SOUTH), 4);
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[4];
        VoxelShape south = VoxelShapes.cuboid(
                0.5 + (0.2353437505 - 0.5) * MODEL_SCALE,
                0.0,
                0.0,
                0.5 + (0.7646562495 - 0.5) * MODEL_SCALE,
                MODEL_SCALE,
                0.5 + (0.6959232125 - 0.5 + MODEL_REAR_SHIFT) * MODEL_SCALE
        );
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape rotated = south;
            for (int step = 0; step < rotationStepsFromSouth(facing); step++) {
                final VoxelShape[] next = {VoxelShapes.empty()};
                rotated.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                        next[0] = VoxelShapes.union(next[0], VoxelShapes.cuboid(
                                1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
                rotated = next[0];
            }
            shapes[horizontalIndex(facing)] = rotated.simplify();
        }
        return shapes;
    }

    private static int horizontalIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3;
            default -> throw new IllegalArgumentException("Decor facing must be horizontal: " + direction);
        };
    }
}
