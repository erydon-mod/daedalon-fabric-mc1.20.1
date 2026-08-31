package com.oliver.daedalon.block;

import net.minecraft.block.Block;
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

import java.util.Objects;

/**
 * Shared placed-state and transform profile for Daedalon's multi-height statues.
 * Each statue supplies its source-facing direction, fitted-mesh support point,
 * and a hand-authored three-block collision shape.
 */
public abstract class StatueBlock extends Block {
    public static final BooleanProperty OFFSET = BooleanProperty.of("offset");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<StatueSize> SIZE =
            EnumProperty.of("size", StatueSize.class);

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final Profile profile;

    protected StatueBlock(Settings settings, Profile profile) {
        super(settings);
        this.profile = Objects.requireNonNull(profile, "profile");
        setDefaultState(getStateManager().getDefaultState()
                .with(SIZE, StatueSize.MEDIUM)
                .with(OFFSET, false)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, OFFSET, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(
                FACING,
                context.getHorizontalPlayerFacing()
        );
    }

    public final StatueSize effectiveSize(BlockState state) {
        return state.get(SIZE);
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        if (state.get(OFFSET) && context.isHolding(Items.DEBUG_STICK)) {
            return profile.debugEditOutlineShapeFor(
                    effectiveSize(state),
                    state.get(FACING)
            );
        }
        return profile.outlineShapeFor(
                effectiveSize(state),
                state.get(OFFSET),
                state.get(FACING)
        );
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return profile.outlineShapeFor(
                effectiveSize(state),
                state.get(OFFSET),
                state.get(FACING)
        );
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return profile.collisionShapeFor(
                effectiveSize(state),
                state.get(OFFSET),
                state.get(FACING)
        );
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(FACING)));
    }

    public final float modelSupportCenterX() {
        return profile.modelSupportCenterX();
    }

    public final float modelGroundContactY() {
        return profile.modelGroundContactY();
    }

    public final float modelSupportCenterZ() {
        return profile.modelSupportCenterZ();
    }

    public final int rotationStepsFromSource(Direction facing) {
        return profile.rotationStepsFromSource(facing);
    }

    public final Profile statueProfile() {
        return profile;
    }

    protected static Profile createProfile(
            Direction sourceFacing,
            float modelSupportCenterX,
            float modelGroundContactY,
            float modelSupportCenterZ,
            VoxelShape threeHighSourceShape
    ) {
        return new Profile(
                sourceFacing,
                modelSupportCenterX,
                modelGroundContactY,
                modelSupportCenterZ,
                threeHighSourceShape
        );
    }

    protected static int rotationSteps(Direction sourceFacing, Direction facing) {
        if (sourceFacing.getAxis().isVertical() || facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("Statue facing must be horizontal");
        }
        return Math.floorMod(
                horizontalClockwiseIndex(facing)
                        - horizontalClockwiseIndex(sourceFacing),
                HORIZONTAL_DIRECTIONS.length
        );
    }

    private static int horizontalClockwiseIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException(
                    "Statue facing must be horizontal: " + direction
            );
        };
    }

    private static int horizontalIndex(Direction direction) {
        return horizontalClockwiseIndex(direction);
    }

    /**
     * Immutable geometry profile shared by every material variant in one
     * statue family. Its twelve collision shapes are built only once.
     */
    public static final class Profile {
        private final Direction sourceFacing;
        private final float modelSupportCenterX;
        private final float modelGroundContactY;
        private final float modelSupportCenterZ;
        private final VoxelShape[][][] collisionShapes;
        private final VoxelShape[][][] outlineShapes;
        private final VoxelShape[][][] debugEditOutlineShapes;

        private Profile(
                Direction sourceFacing,
                float modelSupportCenterX,
                float modelGroundContactY,
                float modelSupportCenterZ,
                VoxelShape threeHighSourceShape
        ) {
            if (sourceFacing.getAxis().isVertical()) {
                throw new IllegalArgumentException(
                        "Statue source facing must be horizontal"
                );
            }
            this.sourceFacing = sourceFacing;
            this.modelSupportCenterX = modelSupportCenterX;
            this.modelGroundContactY = modelGroundContactY;
            this.modelSupportCenterZ = modelSupportCenterZ;
            ShapeCaches shapeCaches = createShapeCaches(threeHighSourceShape);
            this.collisionShapes = shapeCaches.collision();
            this.outlineShapes = shapeCaches.outline();
            this.debugEditOutlineShapes = shapeCaches.debugEditOutline();
        }

        public float modelSupportCenterX() {
            return modelSupportCenterX;
        }

        public float modelGroundContactY() {
            return modelGroundContactY;
        }

        public float modelSupportCenterZ() {
            return modelSupportCenterZ;
        }

        public int rotationStepsFromSource(Direction facing) {
            return rotationSteps(sourceFacing, facing);
        }

        private VoxelShape collisionShapeFor(
                StatueSize size,
                boolean offset,
                Direction facing
        ) {
            return collisionShapes[size.ordinal()][offset ? 1 : 0]
                    [horizontalIndex(facing)];
        }

        private VoxelShape outlineShapeFor(
                StatueSize size,
                boolean offset,
                Direction facing
        ) {
            return outlineShapes[size.ordinal()][offset ? 1 : 0]
                    [horizontalIndex(facing)];
        }

        private VoxelShape debugEditOutlineShapeFor(
                StatueSize size,
                Direction facing
        ) {
            return debugEditOutlineShapes[size.ordinal()][1]
                    [horizontalIndex(facing)];
        }

        private ShapeCaches createShapeCaches(VoxelShape threeHighSourceShape) {
            StatueSize[] sizes = StatueSize.values();
            VoxelShape[][][] collision =
                    new VoxelShape[sizes.length][2][HORIZONTAL_DIRECTIONS.length];
            VoxelShape[][][] outline =
                    new VoxelShape[sizes.length][2][HORIZONTAL_DIRECTIONS.length];
            VoxelShape[][][] debugEditOutline =
                    new VoxelShape[sizes.length][2][HORIZONTAL_DIRECTIONS.length];
            for (StatueSize size : sizes) {
                VoxelShape scaled = scaleFromSupport(
                        threeHighSourceShape,
                        size.scale()
                );
                for (Direction direction : HORIZONTAL_DIRECTIONS) {
                    int directionIndex = horizontalIndex(direction);
                    VoxelShape centred = rotateFromSource(scaled, direction);
                    VoxelShape offset = translate(
                            centred,
                            direction.getOffsetX() * DecorShapeTransforms.OFFSET_DISTANCE,
                            DecorShapeTransforms.OFFSET_BASE_Y,
                            direction.getOffsetZ() * DecorShapeTransforms.OFFSET_DISTANCE
                    );
                    collision[size.ordinal()][0][directionIndex] = centred;
                    collision[size.ordinal()][1][directionIndex] = offset;
                    outline[size.ordinal()][0][directionIndex] = centred;
                    outline[size.ordinal()][1][directionIndex] = VoxelShapes.union(
                            offset,
                            DecorShapeTransforms.TARGET_POST
                    ).simplify();
                    debugEditOutline[size.ordinal()][0][directionIndex] = centred;
                    debugEditOutline[size.ordinal()][1][directionIndex] = VoxelShapes.union(
                            offset,
                            DecorShapeTransforms.DEBUG_EDIT_TARGET
                    ).simplify();
                }
            }
            return new ShapeCaches(collision, outline, debugEditOutline);
        }

        private static VoxelShape translate(
                VoxelShape shape,
                double x,
                double y,
                double z
        ) {
            final VoxelShape[] translated = {VoxelShapes.empty()};
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    translated[0] = VoxelShapes.union(
                            translated[0],
                            VoxelShapes.cuboid(
                                    minX + x,
                                    minY + y,
                                    minZ + z,
                                    maxX + x,
                                    maxY + y,
                                    maxZ + z
                            )
                    )
            );
            return translated[0].simplify();
        }

        private VoxelShape scaleFromSupport(VoxelShape shape, double scale) {
            final VoxelShape[] scaled = {VoxelShapes.empty()};
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    scaled[0] = VoxelShapes.union(
                            scaled[0],
                            VoxelShapes.cuboid(
                                    0.5 + (minX - modelSupportCenterX) * scale,
                                    Math.max(0.0, (minY - modelGroundContactY) * scale),
                                    0.5 + (minZ - modelSupportCenterZ) * scale,
                                    0.5 + (maxX - modelSupportCenterX) * scale,
                                    Math.max(0.0, (maxY - modelGroundContactY) * scale),
                                    0.5 + (maxZ - modelSupportCenterZ) * scale
                            )
                    )
            );
            return scaled[0];
        }

        private VoxelShape rotateFromSource(VoxelShape shape, Direction facing) {
            VoxelShape rotated = shape;
            int steps = rotationStepsFromSource(facing);
            for (int step = 0; step < steps; step++) {
                final VoxelShape[] next = {VoxelShapes.empty()};
                rotated.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                        next[0] = VoxelShapes.union(
                                next[0],
                                VoxelShapes.cuboid(
                                        1.0 - maxZ,
                                        minY,
                                        minX,
                                        1.0 - minZ,
                                        maxY,
                                        maxX
                                )
                        )
                );
                rotated = next[0];
            }
            return rotated;
        }

        private record ShapeCaches(
                VoxelShape[][][] collision,
                VoxelShape[][][] outline,
                VoxelShape[][][] debugEditOutline
        ) {
        }
    }

    public enum StatueSize implements StringIdentifiable {
        SMALL("small", 1),
        MEDIUM("medium", 2),
        LARGE("large", 3);

        private final int heightInBlocks;
        private final String serializedName;

        StatueSize(String serializedName, int heightInBlocks) {
            this.heightInBlocks = heightInBlocks;
            this.serializedName = serializedName;
        }

        public int heightInBlocks() {
            return heightInBlocks;
        }

        public float scale() {
            return heightInBlocks / 3.0F;
        }

        @Override
        public String asString() {
            return serializedName;
        }

        @Override
        public String toString() {
            return serializedName;
        }
    }
}
