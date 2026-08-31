package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
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

/** A wall-facing architectural corbel whose top remains fixed at Y=16. */
public final class CorbelBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<CorbelSize> SIZE =
            EnumProperty.of("size", CorbelSize.class);
    public static final float SOURCE_TOP_Y = 3.0F;
    public static final float TOP_ANCHOR_Y = 1.0F;
    public static final float LARGE_WIDTH_IN_BLOCKS = 1.0F;

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private final Style style;

    public CorbelBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(SIZE, CorbelSize.MEDIUM)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(
                FACING,
                context.getHorizontalPlayerFacing().getOpposite()
        );
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return ShapeCache.SHAPES[style.ordinal()][state.get(SIZE).ordinal()]
                [horizontalIndex(state.get(FACING))];
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public Style style() {
        return style;
    }

    public static int rotationStepsFromSouth(Direction facing) {
        return Math.floorMod(horizontalIndex(facing) - horizontalIndex(Direction.SOUTH), 4);
    }

    private static int horizontalIndex(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException(
                    "Corbel facing must be horizontal: " + direction
            );
        };
    }

    private static final class ShapeCache {
        private static final VoxelShape[][][] SHAPES = createShapes();

        private static VoxelShape[][][] createShapes() {
            Style[] styles = Style.values();
            CorbelSize[] sizes = CorbelSize.values();
            VoxelShape[][][] shapes =
                    new VoxelShape[styles.length][sizes.length][HORIZONTAL_DIRECTIONS.length];
            for (Style style : styles) {
                for (CorbelSize size : sizes) {
                    double depth = style.scaledDepth(size);
                    double halfWidth = style.scaledWidth(size) * 0.5;
                    VoxelShape source = VoxelShapes.cuboid(
                            0.5 - halfWidth,
                            TOP_ANCHOR_Y - style.scaledHeight(size),
                            0.0,
                            0.5 + halfWidth,
                            TOP_ANCHOR_Y,
                            depth
                    );
                    for (Direction facing : HORIZONTAL_DIRECTIONS) {
                        VoxelShape rotated = source;
                        for (int step = 0; step < rotationStepsFromSouth(facing); step++) {
                            rotated = rotateClockwise(rotated);
                        }
                        shapes[style.ordinal()][size.ordinal()][horizontalIndex(facing)] =
                                rotated.simplify();
                    }
                }
            }
            return shapes;
        }

        private static VoxelShape rotateClockwise(VoxelShape shape) {
            final VoxelShape[] rotated = {VoxelShapes.empty()};
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    rotated[0] = VoxelShapes.union(
                            rotated[0],
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
            return rotated[0];
        }
    }

    public enum Style {
        BAROQUE("baroque", "Baroque", 1.902824958, 1.308359563),
        CORINTHIAN("corinthian", "Corinthian", 1.746882920, 1.685652571),
        GEORGIAN("georgian", "Georgian", 1.321256838, 0.876921578),
        IONIC("ionic", "Ionic", 1.979330101, 0.646160985),
        RENAISSANCE("renaissance", "Renaissance", 2.273921200, 1.110684798);

        private final String styleId;
        private final String displayName;
        private final String resourceStem;
        private final double fullWidth;
        private final double fullDepth;

        Style(String styleId, String displayName, double fullWidth, double fullDepth) {
            this.styleId = styleId;
            this.displayName = displayName;
            this.resourceStem = "corbel_" + styleId;
            this.fullWidth = fullWidth;
            this.fullDepth = fullDepth;
        }

        public String styleId() {
            return styleId;
        }

        public String displayName() {
            return displayName;
        }

        public String resourceStem() {
            return resourceStem;
        }

        /**
         * Preserves the approved small and medium sizes while fitting every
         * large corbel uniformly to exactly one block of placed width.
         */
        public float modelScale(CorbelSize size) {
            return size == CorbelSize.LARGE
                    ? (float) (LARGE_WIDTH_IN_BLOCKS / fullWidth)
                    : size.scale();
        }

        /** The placed width after applying the selected corbel size. */
        public float scaledWidth(CorbelSize size) {
            return (float) (fullWidth * modelScale(size));
        }

        /** The placed height; the top remains anchored while this grows downward. */
        public float scaledHeight(CorbelSize size) {
            return SOURCE_TOP_Y * modelScale(size);
        }

        /** The placed depth after applying the selected corbel size. */
        public float scaledDepth(CorbelSize size) {
            return (float) (fullDepth * modelScale(size));
        }

        /**
         * Moves the model's centred source depth so its rear plane lands on
         * the supporting wall boundary before the facing rotation is applied.
         */
        public float wallAnchorShift(CorbelSize size) {
            return -0.5F + scaledDepth(size) * 0.5F;
        }

    }

    public enum CorbelSize implements StringIdentifiable {
        SMALL("small", 1, 2.0F / 9.0F),
        MEDIUM("medium", 2, 1.0F / 3.0F),
        LARGE("large", 3, 2.0F / 3.0F);

        private final int heightInBlocks;
        private final float scale;
        private final String serializedName;

        CorbelSize(String serializedName, int heightInBlocks, float scale) {
            this.heightInBlocks = heightInBlocks;
            this.scale = scale;
            this.serializedName = serializedName;
        }

        public int heightInBlocks() {
            return heightInBlocks;
        }

        public float scale() {
            return scale;
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
