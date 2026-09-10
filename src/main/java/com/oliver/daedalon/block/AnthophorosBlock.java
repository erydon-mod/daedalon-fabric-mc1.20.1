package com.oliver.daedalon.block;

import net.minecraft.util.shape.VoxelShape;

/** Static, hollow planter; width selects a shared pre-sized repeated-panel mesh. */
public final class AnthophorosBlock extends BenchBlock {
    public static final double HEIGHT = 1.0;
    public static final double DEPTH = 1.188486;
    public static final double FLOOR_HEIGHT = 0.26;
    private static final VoxelShape[][] SHAPES = createShapes(shellBoxes());

    public AnthophorosBlock(Settings settings) {
        super(settings, SHAPES);
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
