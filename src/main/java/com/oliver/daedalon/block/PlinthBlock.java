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

/** Half-, one-, and two-metre plinths that can also control a fountain assembly. */
public final class PlinthBlock extends Block {
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    public static final BooleanProperty OFFSET = BooleanProperty.of("offset");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    private final Style style;
    private final String fountainMaterialKey;

    public PlinthBlock(Settings settings, Style style, String fountainMaterialKey) {
        super(settings.dynamicBounds());
        this.style = style;
        if (fountainMaterialKey.isBlank()) {
            throw new IllegalArgumentException("Fountain material key must not be blank");
        }
        this.fountainMaterialKey = fountainMaterialKey;
        setDefaultState(getStateManager().getDefaultState()
                .with(SIZE, Size.MEDIUM)
                .with(OFFSET, false)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, OFFSET, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
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
    public VoxelShape getOutlineShape(BlockState state,
                                      BlockView world,
                                      BlockPos pos,
                                      ShapeContext context) {
        VoxelShape outline = shape(state);
        if (!state.get(OFFSET)) {
            return outline;
        }
        VoxelShape target = context.isHolding(Items.DEBUG_STICK)
                ? DecorShapeTransforms.DEBUG_EDIT_TARGET
                : DecorShapeTransforms.TARGET_POST;
        return VoxelShapes.union(outline, target).simplify();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockView world,
                                        BlockPos pos,
                                        ShapeContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public double connectionRise(BlockState state) {
        return state.get(SIZE).height();
    }

    public VoxelShape assemblyCollisionShape(BlockState state) {
        return style.profile.centredShapes[state.get(SIZE).ordinal()];
    }

    public VoxelShape assemblyOutlineShape(BlockState state, boolean filled) {
        return assemblyCollisionShape(state);
    }

    public Style style() {
        return style;
    }

    /** Selects the internal bowl material without creating bowl block or item IDs. */
    public String fountainMaterialKey() {
        return fountainMaterialKey;
    }

    private VoxelShape shape(BlockState state) {
        return style.profile.shapes[state.get(SIZE).ordinal()][state.get(OFFSET) ? 1 : 0]
                [DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    public enum Size implements StringIdentifiable {
        SMALL("small", 0.25F, 0.5F),
        MEDIUM("medium", 0.5F, 1.0F),
        LARGE("large", 1.0F, 2.0F);

        private final String name;
        private final float renderScale;
        private final float height;

        Size(String name, float renderScale, float height) {
            this.name = name;
            this.renderScale = renderScale;
            this.height = height;
        }

        public float renderScale() {
            return renderScale;
        }

        public float height() {
            return height;
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

    private static final class Profile {
        private final VoxelShape[] centredShapes = new VoxelShape[Size.values().length];
        private final VoxelShape[][][] shapes = new VoxelShape[Size.values().length][2][4];

        private Profile(double width, double height, double depth) {
            if (!(width > 0.0D) || !(height > 0.0D) || !(depth > 0.0D)) {
                throw new IllegalArgumentException("Plinth dimensions must be positive");
            }
            for (Size size : Size.values()) {
                double sourceScale = size.height();
                VoxelShape centred = VoxelShapes.cuboid(
                        0.5D - width * sourceScale * 0.5D,
                        0.0D,
                        0.5D - depth * sourceScale * 0.5D,
                        0.5D + width * sourceScale * 0.5D,
                        height * sourceScale,
                        0.5D + depth * sourceScale * 0.5D
                );
                centredShapes[size.ordinal()] = centred;
                for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    int facingIndex = DecorShapeTransforms.horizontalIndex(facing);
                    for (int offsetIndex = 0; offsetIndex < 2; offsetIndex++) {
                        boolean offset = offsetIndex == 1;
                        shapes[size.ordinal()][offsetIndex][facingIndex] =
                                DecorShapeTransforms.transformFromNorth(
                                        centred,
                                        1.0D,
                                        offset ? DecorShapeTransforms.OFFSET_DISTANCE : 0.0D,
                                        offset ? DecorShapeTransforms.OFFSET_BASE_Y : 0.0D,
                                        facing
                                );
                    }
                }
            }
        }
    }

    public enum Style {
        ASTRAGALOS("astragalos", "plinth_astragalos", "Astragalos", 0.603108658, 1.0, 0.602949901),
        BATHRON("bathron", "plinth_bathron", "Bathron", 0.811787311, 1.0, 0.812034223),
        KION("kion", "plinth_kion", "Kion", 0.998954339, 1.0, 1.0),
        STEPHANOS("stephanos", "plinth_stephanos", "Stephanos", 0.831943020, 1.0, 0.831934729),
        TRIPHYLLON("triphyllon", "plinth_triphyllon", "Triphyllon", 0.957103561, 1.0, 0.973928089);

        private final String styleId;
        private final String resourceStem;
        private final String displayName;
        private final Profile profile;

        Style(String styleId, String resourceStem, String displayName,
              double width, double height, double depth) {
            this.styleId = styleId;
            this.resourceStem = resourceStem;
            this.displayName = displayName;
            this.profile = new Profile(width, height, depth);
        }

        public String styleId() {
            return styleId;
        }

        public String resourceStem() {
            return resourceStem;
        }

        public String displayName() {
            return displayName;
        }

        /** Legacy standalone lookup; assembled basins select their matching bowl style. */
        public FountainBowlModel.Style fountainBowlStyle() {
            return FountainBowlModel.Style.GOTHIC;
        }
    }
}
