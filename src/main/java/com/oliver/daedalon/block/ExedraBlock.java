package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Curved bench with proportional width/depth and a fixed one-metre height. */
public final class ExedraBlock extends BenchBlock {
    public static final EnumProperty<DecorOrientation> ORIENTATION = DecorOrientation.PROPERTY;
    public static final double HEIGHT = 1.0;
    public static final double DEPTH_PER_WIDTH = 1.013337 / 1.89977;
    private static final VoxelShape[][] SHAPES = createShapes(widthBoxes());

    private static final VoxelShape[][] DIAGONAL_SHAPES = diagonalShapes();

    public ExedraBlock(Settings settings) {
        super(settings, SHAPES);
        setDefaultState(getDefaultState().with(ORIENTATION, DecorOrientation.STRAIGHT));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ORIENTATION);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(FACING, state.get(ORIENTATION).mirrorFacing(state.get(FACING), mirror));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(ORIENTATION) == DecorOrientation.DIAGONAL
                ? DIAGONAL_SHAPES[state.get(WIDTH) - 2][DecorShapeTransforms.horizontalIndex(state.get(FACING))]
                : super.getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    private static VoxelShape[][] diagonalShapes() {
        VoxelShape[][] result = new VoxelShape[3][4];
        for (int width = 0; width < 3; width++) {
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                int direction = DecorShapeTransforms.horizontalIndex(facing);
                result[width][direction] = DecorShapeTransforms.diagonal(SHAPES[width][direction]);
            }
        }
        return result;
    }

    private static double[][][] widthBoxes() {
        double[][][] widths = new double[3][ExedraShape.BOXES.length][6];
        for (int width = 2; width <= 4; width++) {
            for (int i = 0; i < ExedraShape.BOXES.length; i++) {
                double[] box = ExedraShape.BOXES[i];
                widths[width - 2][i] = new double[]{box[0] * width, box[1], box[2] * width,
                        box[3] * width, box[4], box[5] * width};
            }
        }
        return widths;
    }
}
