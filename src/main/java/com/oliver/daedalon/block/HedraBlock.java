package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Straight bench; only its seat length and support spacing change with width. */
public final class HedraBlock extends BenchBlock {
    public static final double HEIGHT = 1.0;
    public static final double DEPTH = 0.861762;
    private static final VoxelShape[][] SHAPES = createShapes(HedraShape.BOXES);
    private static final VoxelShape[][] OUTLINES = createShapes(outlineBoxes());

    public HedraBlock(Settings settings) {
        super(settings, SHAPES);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINES[state.get(WIDTH) - 2][DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    private static double[][][] outlineBoxes() {
        double[][][] widths = new double[3][1][6];
        for (int width = 2; width <= 4; width++) {
            // A whole-block-deep selection target includes the open space
            // beneath the seat; collision retains the measured five-box mesh.
            widths[width - 2][0] = new double[]{-width / 2.0, 0.0, -0.5,
                    width / 2.0, HEIGHT, 0.5};
        }
        return widths;
    }
}
