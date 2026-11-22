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
import org.jetbrains.annotations.Nullable;

public final class UrnBlock extends Block {
    public static final BooleanProperty OFFSET = BooleanProperty.of("offset");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<UrnSize> SIZE =
            EnumProperty.of("size", UrnSize.class);

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };
    private final UrnStyle style;

    public UrnBlock(Settings settings, UrnStyle style) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(SIZE, UrnSize.MEDIUM)
                .with(OFFSET, false)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, OFFSET, FACING);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(
                FACING,
                context.getHorizontalPlayerFacing()
        );
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        if (state.get(OFFSET) && context.isHolding(Items.DEBUG_STICK)) {
            return debugEditOutlineShapeFor(
                    style,
                    state.get(SIZE),
                    state.get(FACING)
            );
        }
        return outlineShapeFor(
                style,
                state.get(SIZE),
                state.get(OFFSET),
                state.get(FACING)
        );
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return outlineShapeFor(
                style,
                state.get(SIZE),
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
        return collisionShapeFor(
                style,
                state.get(SIZE),
                state.get(OFFSET),
                state.get(FACING)
        );
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    static VoxelShape collisionShapeFor(
            UrnStyle style,
            UrnSize size,
            boolean offset,
            Direction facing
    ) {
        return ShapeCache.COLLISION[style.ordinal()][size.ordinal()][offset ? 1 : 0]
                [DecorShapeTransforms.horizontalIndex(facing)];
    }

    static VoxelShape outlineShapeFor(
            UrnStyle style,
            UrnSize size,
            boolean offset,
            Direction facing
    ) {
        return ShapeCache.OUTLINE[style.ordinal()][size.ordinal()][offset ? 1 : 0]
                [DecorShapeTransforms.horizontalIndex(facing)];
    }

    static VoxelShape debugEditOutlineShapeFor(
            UrnStyle style,
            UrnSize size,
            Direction facing
    ) {
        return ShapeCache.DEBUG_EDIT_OUTLINE[style.ordinal()][size.ordinal()][1]
                [DecorShapeTransforms.horizontalIndex(facing)];
    }

    /*
     * UrnStyle constants build their base shapes through UrnBlock methods.
     * Keeping these caches in a lazy holder prevents UrnBlock initialization
     * from asking UrnStyle.values() while that enum is still being created.
     */
    private static final class ShapeCache {
        private static final VoxelShape[][][][] COLLISION =
                createShapeCache(false);
        private static final VoxelShape[][][][] OUTLINE =
                createShapeCache(true);
        private static final VoxelShape[][][][] DEBUG_EDIT_OUTLINE =
                createDebugEditShapeCache();

        private ShapeCache() {
        }
    }

    private static VoxelShape[][][][] createDebugEditShapeCache() {
        VoxelShape[][][][] cache = createShapeCache(true);
        for (UrnStyle style : UrnStyle.values()) {
            for (UrnSize size : UrnSize.values()) {
                for (Direction facing : HORIZONTAL_DIRECTIONS) {
                    int facingIndex = DecorShapeTransforms.horizontalIndex(facing);
                    cache[style.ordinal()][size.ordinal()][1][facingIndex] =
                            VoxelShapes.union(
                                    cache[style.ordinal()][size.ordinal()][1][facingIndex],
                                    DecorShapeTransforms.DEBUG_EDIT_TARGET
                            ).simplify();
                }
            }
        }
        return cache;
    }

    private static VoxelShape[][][][] createShapeCache(boolean includeTargetPost) {
        UrnStyle[] styles = UrnStyle.values();
        UrnSize[] sizes = UrnSize.values();
        VoxelShape[][][][] cache =
                new VoxelShape[styles.length][sizes.length][2][HORIZONTAL_DIRECTIONS.length];
        for (UrnStyle style : styles) {
            for (UrnSize size : sizes) {
                for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                    boolean offset = offsetIndex == 1;
                    for (Direction facing : HORIZONTAL_DIRECTIONS) {
                        VoxelShape body = DecorShapeTransforms.transformFromNorth(
                                style.baseShape,
                                size.scale(),
                                offset ? DecorShapeTransforms.OFFSET_DISTANCE : 0.0,
                                offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0,
                                facing
                        );
                        cache[style.ordinal()][size.ordinal()][offsetIndex]
                                [DecorShapeTransforms.horizontalIndex(facing)] =
                                includeTargetPost && offset
                                        ? VoxelShapes.union(
                                                body,
                                                DecorShapeTransforms.TARGET_POST
                                        ).simplify()
                                        : body;
                    }
                }
            }
        }
        return cache;
    }

    private static VoxelShape makeLayeredShape(double... layers) {
        if (layers.length == 0 || layers.length % 4 != 0) {
            throw new IllegalArgumentException(
                    "Urn collision layers must be minY, maxY, halfX, halfZ groups"
            );
        }
        VoxelShape result = VoxelShapes.empty();
        for (int index = 0; index < layers.length; index += 4) {
            double minY = layers[index];
            double maxY = layers[index + 1];
            double halfX = layers[index + 2];
            double halfZ = layers[index + 3];
            result = VoxelShapes.union(
                    result,
                    DecorShapeTransforms.octagonalLayer(
                            8.0 - halfX,
                            minY,
                            8.0 - halfZ,
                            8.0 + halfX,
                            maxY,
                            8.0 + halfZ
                    )
            );
        }
        return result.simplify();
    }

    private static VoxelShape makeDiotaShape() {
        // Measured triangle-clipped bands retain the square base, carved body,
        // small stone ears and separate hanging-ring arcs. The gaps through
        // the hoops stay open; every size and facing shares the cached shape.
        return VoxelShapes.union(
                Block.createCuboidShape(4.48, 0.0, 4.48, 11.52, 0.736, 11.52),
                Block.createCuboidShape(4.83, 0.736, 4.83, 11.17, 2.08, 11.17),
                DecorShapeTransforms.octagonalLayer(5.12, 2.08, 4.81, 10.88, 3.28, 11.19),
                DecorShapeTransforms.octagonalLayer(6.23, 3.28, 6.04, 9.77, 4.48, 9.96),
                DecorShapeTransforms.octagonalLayer(5.9, 4.48, 5.67, 10.1, 5.36, 10.33),
                DecorShapeTransforms.octagonalLayer(5.31, 5.36, 5.02, 10.69, 6.64, 10.98),
                DecorShapeTransforms.octagonalLayer(4.8, 6.64, 4.45, 11.2, 8, 11.55),
                DecorShapeTransforms.octagonalLayer(4.38, 8, 3.99, 11.62, 9.44, 12.01),
                DecorShapeTransforms.octagonalLayer(4.14, 9.44, 3.72, 11.86, 10.72, 12.28),
                DecorShapeTransforms.octagonalLayer(4.04, 10.72, 3.61, 11.96, 12.048, 12.39),
                DecorShapeTransforms.octagonalLayer(4.04, 12.048, 3.61, 11.96, 13.2, 12.39),
                DecorShapeTransforms.octagonalLayer(5.4, 13.2, 5.11, 10.6, 14.4, 10.89),
                DecorShapeTransforms.octagonalLayer(4.65, 14.4, 4.29, 11.35, 16, 11.71),
                Block.createCuboidShape(11.35, 12.048, 7.71, 11.96, 12.8, 8.38),
                Block.createCuboidShape(11.34, 12.8, 7.71, 12.22, 13.44, 8.38),
                Block.createCuboidShape(11.34, 13.44, 7.71, 12.25, 14.256, 8.38),
                Block.createCuboidShape(4.04, 12.048, 7.71, 4.65, 12.8, 8.38),
                Block.createCuboidShape(3.78, 12.8, 7.71, 4.66, 13.44, 8.38),
                Block.createCuboidShape(3.75, 13.44, 7.71, 4.66, 14.256, 8.38),
                Block.createCuboidShape(12.23, 11.2, 7.15, 12.66, 11.76, 8.6),
                Block.createCuboidShape(11.77, 11.76, 7.04, 12.4, 13.2, 7.43),
                Block.createCuboidShape(11.97, 11.76, 8.42, 12.59, 13.2, 8.8),
                Block.createCuboidShape(11.7, 13.2, 7.25, 12.13, 13.76, 8.69),
                Block.createCuboidShape(3.34, 11.2, 7.15, 3.77, 11.76, 8.6),
                Block.createCuboidShape(3.6, 11.76, 7.04, 4.23, 13.2, 7.43),
                Block.createCuboidShape(3.41, 11.76, 8.42, 4.03, 13.2, 8.8),
                Block.createCuboidShape(3.87, 13.2, 7.25, 4.3, 13.76, 8.69)
        ).simplify();
    }

    public enum UrnStyle {
        KONCHE("konche", "Konche", makeLayeredShape(
                0.0, 2.0, 4.92, 4.86,
                2.0, 4.0, 4.33, 4.30,
                4.0, 6.0, 3.40, 3.30,
                6.0, 8.0, 5.64, 5.83,
                8.0, 10.0, 6.86, 6.95,
                10.0, 12.0, 7.00, 6.99,
                12.0, 14.0, 7.11, 6.99,
                14.0, 16.0, 8.00, 8.00
        )),
        DIOTA("diota", "Diota", makeDiotaShape()),
        KYLIX("kylix", "Kylix", makeLayeredShape(
                0.0, 2.0, 4.66, 4.70,
                2.0, 4.0, 3.98, 3.96,
                4.0, 6.0, 7.38, 7.19,
                6.0, 8.0, 8.00, 8.00,
                8.0, 10.0, 8.00, 8.00,
                10.0, 10.60, 8.00, 8.00
        )),
        KALYX("kalyx", "Kalyx", makeLayeredShape(
                0.0, 2.0, 3.62, 3.63,
                2.0, 4.0, 3.00, 3.10,
                4.0, 6.0, 2.54, 2.65,
                6.0, 8.0, 3.91, 4.05,
                8.0, 10.0, 4.50, 4.47,
                10.0, 12.0, 4.50, 4.59,
                12.0, 14.0, 5.25, 5.29,
                14.0, 16.0, 6.07, 6.08
        )),
        AMPHORA("amphora", "Amphora", makeLayeredShape(
                0.0, 2.0, 2.23, 2.26,
                2.0, 4.0, 2.87, 2.78,
                4.0, 6.0, 3.17, 3.14,
                6.0, 8.0, 3.28, 3.28,
                8.0, 10.0, 3.28, 3.28,
                10.0, 12.0, 3.24, 3.19,
                12.0, 14.0, 3.18, 1.68,
                14.0, 16.0, 3.18, 1.81
        )),
        LEKYTHOS("lekythos", "Lekythos", makeLayeredShape(
                0.0, 2.0, 2.17, 1.88,
                2.0, 4.0, 2.58, 2.30,
                4.0, 6.0, 2.78, 2.45,
                6.0, 8.0, 2.86, 2.53,
                8.0, 10.0, 2.86, 2.53,
                10.0, 12.0, 2.75, 2.46,
                12.0, 14.0, 2.86, 1.23,
                14.0, 16.0, 2.80, 1.79
        )),
        PELIKE("pelike", "Pelike", makeLayeredShape(
                0.0, 2.0, 5.17, 5.28,
                2.0, 4.0, 6.26, 6.16,
                4.0, 6.0, 6.73, 6.72,
                6.0, 8.0, 6.73, 6.73,
                8.0, 10.0, 6.73, 6.73,
                10.0, 12.0, 6.62, 6.04,
                12.0, 14.0, 6.62, 5.00,
                14.0, 16.0, 6.47, 4.22
        )),
        PITHOS("pithos", "Pithos", makeLayeredShape(
                0.0, 2.0, 4.50, 4.51,
                2.0, 4.0, 3.56, 3.55,
                4.0, 6.0, 4.90, 4.85,
                6.0, 8.0, 5.48, 5.50,
                8.0, 10.0, 5.79, 5.77,
                10.0, 12.0, 5.79, 5.77,
                12.0, 14.0, 5.79, 5.77,
                14.0, 16.0, 4.69, 4.69
        )),
        RHABDOS("rhabdos", "Rhabdos", makeLayeredShape(
                0.0, 2.0, 3.32, 3.49,
                2.0, 4.0, 2.58, 2.63,
                4.0, 6.0, 4.32, 4.31,
                6.0, 8.0, 4.89, 4.90,
                8.0, 10.0, 4.94, 4.94,
                10.0, 12.0, 4.94, 4.94,
                12.0, 14.0, 4.94, 4.94,
                14.0, 16.0, 4.00, 4.04
        )),
        SALPINX("salpinx", "Salpinx", makeLayeredShape(
                0.0, 2.0, 3.40, 3.29,
                2.0, 4.0, 2.88, 2.81,
                4.0, 6.0, 3.20, 3.06,
                6.0, 8.0, 4.12, 4.14,
                8.0, 10.0, 4.12, 4.14,
                10.0, 12.0, 4.29, 4.05,
                12.0, 14.0, 5.20, 4.83,
                14.0, 16.0, 6.44, 6.47
        )),
        STAMNOS("stamnos", "Stamnos", makeLayeredShape(
                0.0, 2.0, 6.06, 5.96,
                2.0, 4.0, 6.74, 6.73,
                4.0, 6.0, 7.20, 7.18,
                6.0, 8.0, 8.00, 7.18,
                8.0, 10.0, 8.00, 7.16,
                10.0, 12.0, 8.00, 6.27,
                12.0, 12.87, 4.77, 4.77
        ));

        private final String shapeId;
        private final String displayName;
        private final String resourceStem;
        private final VoxelShape baseShape;

        UrnStyle(String shapeId, String displayName, VoxelShape baseShape) {
            this.shapeId = shapeId;
            this.displayName = displayName;
            this.resourceStem = "urn_" + shapeId;
            this.baseShape = baseShape;
        }

        public String shapeId() {
            return shapeId;
        }

        public String displayName() {
            return displayName;
        }

        public String resourceStem() {
            return resourceStem;
        }

        public static UrnStyle fromId(String shapeId) {
            for (UrnStyle style : values()) {
                if (style.shapeId.equals(shapeId)
                        || style.resourceStem.equals(shapeId)) {
                    return style;
                }
            }
            throw new IllegalArgumentException("Unknown urn style: " + shapeId);
        }
    }

    public enum UrnSize implements StringIdentifiable {
        SMALL("small", 0.6156F),
        MEDIUM("medium", 1.0F),
        LARGE("large", 1.3844F);

        private final String serializedName;
        private final float scale;

        UrnSize(String serializedName, float scale) {
            this.serializedName = serializedName;
            this.scale = scale;
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
