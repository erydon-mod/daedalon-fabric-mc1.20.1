package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** One static furniture block; width chooses a shared, pre-sized OBJ mesh. */
public class BenchBlock extends Block {
    public static final IntProperty WIDTH = IntProperty.of("width", 2, 4);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final int DEFAULT_WIDTH = 3;
    private final VoxelShape[][] shapes;

    protected BenchBlock(Settings settings, VoxelShape[][] shapes) {
        super(settings);
        this.shapes = shapes;
        setDefaultState(getStateManager().getDefaultState()
                .with(WIDTH, DEFAULT_WIDTH).with(FACING, Direction.SOUTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(WIDTH, FACING);
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
        return shapes[state.get(WIDTH) - 2][DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes[state.get(WIDTH) - 2][DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    public static int rotationStepsFromSouth(Direction facing) {
        return Math.floorMod(DecorShapeTransforms.horizontalIndex(facing) - 2, 4);
    }

    protected static VoxelShape[][] createShapes(double[][][] boxesByWidth) {
        VoxelShape[][] shapes = new VoxelShape[3][4];
        for (int width = 2; width <= 4; width++) {
            VoxelShape south = VoxelShapes.empty();
            for (double[] box : boxesByWidth[width - 2]) {
                south = VoxelShapes.union(south, VoxelShapes.cuboid(
                        0.5 + box[0], box[1], 0.5 + box[2],
                        0.5 + box[3], box[4], 0.5 + box[5]));
            }
            south = south.simplify();
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                VoxelShape rotated = south;
                for (int step = 0; step < rotationStepsFromSouth(facing); step++) {
                    final VoxelShape[] next = {VoxelShapes.empty()};
                    rotated.forEachBox((x0, y0, z0, x1, y1, z1) ->
                            next[0] = VoxelShapes.union(next[0], VoxelShapes.cuboid(
                                    1.0 - z1, y0, x0, 1.0 - z0, y1, x1)));
                    rotated = next[0];
                }
                shapes[width - 2][DecorShapeTransforms.horizontalIndex(facing)] = rotated.simplify();
            }
        }
        return shapes;
    }
}
