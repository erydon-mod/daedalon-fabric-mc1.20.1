package com.oliver.daedalon.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class SpartanStatueBlock extends StatueBlock {
    /* Centre of the two-foot footprint in the fitted three-block source mesh. */
    public static final float MODEL_SUPPORT_CENTER_X = 0.41134763F;
    public static final float MODEL_SUPPORT_CENTER_Z = -0.14984404F;
    public static final float MODEL_GROUND_CONTACT_Y = 0.0625F;

    /*
     * The source mesh faces east. These boxes follow its body, shield and
     * diagonal spear. Coordinates are in blocks for the three-block mesh.
     */
    private static final VoxelShape THREE_HIGH_EAST_SHAPE = VoxelShapes.union(
            // Feet, legs, torso and helmet.
            VoxelShapes.cuboid(-0.16, 0.00, -0.48, 1.00, 0.28, 0.24),
            VoxelShapes.cuboid(-0.12, 0.24, -0.43, 0.80, 0.78, 0.30),
            VoxelShapes.cuboid(-0.06, 0.72, -0.38, 0.74, 1.30, 0.41),
            VoxelShapes.cuboid(-0.12, 1.20, -0.34, 0.88, 2.24, 0.40),
            VoxelShapes.cuboid(-0.08, 2.16, -0.34, 0.94, 2.72, 0.36),
            VoxelShapes.cuboid(0.12, 2.66, -0.26, 0.66, 2.96, 0.28),

            // Round shield.
            VoxelShapes.cuboid(0.45, 1.30, -0.79, 1.16, 1.58, -0.33),
            VoxelShapes.cuboid(0.34, 1.52, -0.87, 1.21, 2.13, -0.29),
            VoxelShapes.cuboid(0.28, 2.07, -0.60, 0.87, 2.35, -0.29),

            // Spear arm and stepped diagonal shaft.
            VoxelShapes.cuboid(-0.22, 1.55, 0.28, 0.27, 2.22, 0.96),
            VoxelShapes.cuboid(-0.10, 0.82, 0.31, 0.20, 1.24, 0.46),
            VoxelShapes.cuboid(-0.10, 1.16, 0.37, 0.20, 1.58, 0.60),
            VoxelShapes.cuboid(-0.10, 1.50, 0.49, 0.20, 1.92, 0.82),
            VoxelShapes.cuboid(-0.08, 1.84, 0.70, 0.22, 2.24, 1.08),
            VoxelShapes.cuboid(0.00, 2.16, 1.00, 0.27, 2.51, 1.48),
            VoxelShapes.cuboid(0.08, 2.44, 1.36, 0.29, 2.77, 1.72),
            VoxelShapes.cuboid(0.15, 2.70, 1.64, 0.31, 3.02, 1.88)
    );

    private static final Profile PROFILE = createProfile(
            Direction.EAST,
            MODEL_SUPPORT_CENTER_X,
            MODEL_GROUND_CONTACT_Y,
            MODEL_SUPPORT_CENTER_Z,
            THREE_HIGH_EAST_SHAPE
    );

    public SpartanStatueBlock(Settings settings) {
        super(settings, PROFILE);
    }

    public static int rotationStepsFromEast(Direction facing) {
        return rotationSteps(Direction.EAST, facing);
    }
}
