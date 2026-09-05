package com.oliver.daedalon.block;

import net.minecraft.util.shape.VoxelShape;

/** Curved bench with proportional width/depth and a fixed one-metre height. */
public final class ExedraBlock extends BenchBlock {
    public static final double HEIGHT = 1.0;
    public static final double DEPTH_PER_WIDTH = 1.013337 / 1.89977;
    private static final VoxelShape[][] SHAPES = createShapes(widthBoxes());

    public ExedraBlock(Settings settings) {
        super(settings, SHAPES);
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
