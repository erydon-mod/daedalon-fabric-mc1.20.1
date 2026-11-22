package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Symmetrical bench with four debug-stick axes and shared, pre-sized meshes. */
public final class HedraBlock extends BenchBlock {
    public static final EnumProperty<HedraOrientation> ORIENTATION =
            EnumProperty.of("orientation", HedraOrientation.class);
    public static final double HEIGHT = 0.6;
    public static final double DEPTH = 0.517058;
    private static final VoxelShape[][] SHAPES = createShapes(HedraShape.BOXES);
    private static final VoxelShape[][] COLLISIONS = axes(SHAPES);
    private static final VoxelShape[][] OUTLINES = axes(createShapes(outlineBoxes()));

    public HedraBlock(Settings settings) {
        super(settings, SHAPES);
        setDefaultState(getDefaultState().with(ORIENTATION, HedraOrientation.STRAIGHT));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ORIENTATION);
    }

    /** Retain saved cardinal facings, including structure rotations, without extra debug choices. */
    public static int eighthTurns(BlockState state) {
        return Math.floorMod(rotationStepsFromSouth(state.get(FACING)) * 2
                + state.get(ORIENTATION).eighthTurns, 4);
    }

    public static HedraOrientation orientation(BlockState state) {
        return HedraOrientation.fromEighthTurns(eighthTurns(state));
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState placed = super.getPlacementState(context);
        return context.getPlayer() == null ? placed
                : withOrientation(placed, HedraOrientation.fromPlayerYaw(context.getPlayer().getYaw()));
    }

    /** A debug edit selects one absolute axis, independent of the legacy facing. */
    public static BlockState withOrientation(BlockState state, HedraOrientation orientation) {
        return state.with(FACING, Direction.SOUTH).with(ORIENTATION, orientation);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        // Reflection reverses a diagonal half-turn. FACING retains that result
        // for copied builds; it is deliberately not another player control.
        Direction reflected = mirror.getRotation(state.get(FACING)).rotate(state.get(FACING));
        if ((state.get(ORIENTATION).eighthTurns & 1) != 0 && mirror != BlockMirror.NONE) {
            reflected = reflected.rotateYCounterclockwise();
        }
        return state.with(FACING, reflected);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINES[state.get(WIDTH) - 2][eighthTurns(state)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISIONS[state.get(WIDTH) - 2][eighthTurns(state)];
    }

    private static VoxelShape[][] axes(VoxelShape[][] cardinal) {
        VoxelShape[][] result = new VoxelShape[3][4];
        for (int width = 0; width < result.length; width++) {
            result[width][0] = cardinal[width][DecorShapeTransforms.horizontalIndex(Direction.SOUTH)];
            result[width][1] = DecorShapeTransforms.diagonal(result[width][0]);
            result[width][2] = cardinal[width][DecorShapeTransforms.horizontalIndex(Direction.WEST)];
            result[width][3] = DecorShapeTransforms.diagonal(result[width][2]);
        }
        return result;
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
