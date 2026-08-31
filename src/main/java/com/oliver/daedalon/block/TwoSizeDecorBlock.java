package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Ground-anchored decor available at one and two blocks high. */
public class TwoSizeDecorBlock extends Block {
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    public static final BooleanProperty OFFSET = BooleanProperty.of("offset");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private final Profile profile;
    private final boolean movable;

    protected TwoSizeDecorBlock(Settings settings, Profile profile) {
        this(settings, profile, false);
    }

    protected TwoSizeDecorBlock(Settings settings, Profile profile, boolean movable) {
        super(settings);
        this.profile = profile;
        this.movable = movable;
        BlockState defaultState = getStateManager().getDefaultState().with(SIZE, Size.SMALL);
        if (movable) {
            defaultState = defaultState.with(OFFSET, false).with(FACING, Direction.NORTH);
        }
        setDefaultState(defaultState);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        if (!movable) {
            return super.getPlacementState(context);
        }
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return movable ? state.with(FACING, rotation.rotate(state.get(FACING))) : state;
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return movable ? rotate(state, mirror.getRotation(state.get(FACING))) : state;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!movable) {
            return profile.shapes[state.get(SIZE).ordinal()][0][0];
        }
        int offsetIndex = state.get(OFFSET) ? 1 : 0;
        VoxelShape outline = profile.shapes[state.get(SIZE).ordinal()][offsetIndex]
                [DecorShapeTransforms.horizontalIndex(state.get(FACING))];
        if (!state.get(OFFSET)) {
            return outline;
        }
        VoxelShape target = context.isHolding(Items.DEBUG_STICK)
                ? DecorShapeTransforms.DEBUG_EDIT_TARGET
                : DecorShapeTransforms.TARGET_POST;
        return VoxelShapes.union(outline, target).simplify();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!movable) {
            return profile.shapes[state.get(SIZE).ordinal()][0][0];
        }
        return profile.shapes[state.get(SIZE).ordinal()][state.get(OFFSET) ? 1 : 0]
                [DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public static Profile createProfile(double width, double height, double depth) {
        return new Profile(width, height, depth);
    }

    public final boolean supportsOffsetAndFacing() {
        return movable;
    }

    public enum Size implements StringIdentifiable {
        // Geometry is baked at the largest supported size. Keeping runtime
        // factors at or below one also keeps projected UVs inside their atlas
        // sprite, so the two-block state cannot sample a neighbouring texture.
        SMALL("small", 0.5F), LARGE("large", 1.0F);

        private final String name;
        private final float scale;

        Size(String name, float scale) {
            this.name = name;
            this.scale = scale;
        }

        public float scale() {
            return scale;
        }

        @Override
        public String asString() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Profile {
        private final VoxelShape[][][] shapes;

        private Profile(double width, double height, double depth) {
            if (!(width > 0.0) || !(height > 0.0) || !(depth > 0.0)) {
                throw new IllegalArgumentException("Two-size decor dimensions must be positive");
            }
            double sourceScale = 1.0 / Size.SMALL.scale();
            this.shapes = new VoxelShape[Size.values().length][2][4];
            for (Size size : Size.values()) {
                double scale = sourceScale * size.scale();
                VoxelShape centred = VoxelShapes.cuboid(
                        0.5 - width * scale * 0.5,
                        0.0,
                        0.5 - depth * scale * 0.5,
                        0.5 + width * scale * 0.5,
                        height * scale,
                        0.5 + depth * scale * 0.5
                );
                for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    int facingIndex = DecorShapeTransforms.horizontalIndex(facing);
                    for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                        boolean offset = offsetIndex == 1;
                        shapes[size.ordinal()][offsetIndex][facingIndex] =
                                DecorShapeTransforms.transformFromNorth(
                                        centred,
                                        1.0,
                                        offset ? DecorShapeTransforms.OFFSET_DISTANCE : 0.0,
                                        offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0,
                                        facing
                                );
                    }
                }
            }
        }
    }
}
