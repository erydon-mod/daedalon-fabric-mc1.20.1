package com.oliver.daedalon.block;

import net.minecraft.util.shape.VoxelShape;

/** Straight bench; only its seat length and support spacing change with width. */
public final class HedraBlock extends BenchBlock {
    public static final double HEIGHT = 0.5;
    public static final double DEPTH = 0.430881;
    private static final VoxelShape[][] SHAPES = createShapes(HedraShape.BOXES);

    public HedraBlock(Settings settings) {
        super(settings, SHAPES);
    }
}
