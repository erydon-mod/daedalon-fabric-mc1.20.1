package com.oliver.daedalon.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** A north-authored, pedestal-supported Zeus statue. */
public final class ZeusStatueBlock extends StatueBlock {
    public static final float MODEL_SUPPORT_CENTER_X = 0.5041394F;
    public static final float MODEL_SUPPORT_CENTER_Z = 0.4360307F;
    public static final float MODEL_GROUND_CONTACT_Y = 0.0F;

    /*
     * The fitted source faces north. Boxes are deliberately layered around
     * the pedestal, figure, staff and thunderbolt instead of using one broad
     * bounding box. Coordinates precede runtime support-point recentering.
     */
    private static final VoxelShape THREE_HIGH_NORTH_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0.034, 0.00, 0.056, 0.974, 0.32, 0.816),
            VoxelShapes.cuboid(0.164, 0.28, 0.116, 0.904, 1.55, 0.736),
            VoxelShapes.cuboid(0.124, 1.45, 0.116, 0.914, 2.35, 0.746),
            VoxelShapes.cuboid(0.314, 2.30, 0.206, 0.664, 2.89, 0.746),
            VoxelShapes.cuboid(0.034, 0.43, 0.606, 0.124, 2.55, 0.686),
            VoxelShapes.cuboid(-0.096, 2.45, 0.656, 0.074, 3.00, 0.766),
            VoxelShapes.cuboid(0.884, 1.05, 0.266, 1.024, 1.75, 0.836),
            VoxelShapes.cuboid(0.944, 1.70, 0.426, 1.094, 2.31, 0.946)
    );

    private static final Profile PROFILE = createProfile(
            Direction.NORTH,
            MODEL_SUPPORT_CENTER_X,
            MODEL_GROUND_CONTACT_Y,
            MODEL_SUPPORT_CENTER_Z,
            THREE_HIGH_NORTH_SHAPE
    );

    public ZeusStatueBlock(Settings settings) {
        super(settings, PROFILE);
    }
}
