package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** A fixed one-block column capital fitted over ERYDON's circular shaft. */
public class CapitalBlock extends Block {
    public static final EnumProperty<CapitalOrientation> ORIENTATION = EnumProperty.of(
            "capital_orientation", CapitalOrientation.class,
            CapitalOrientation.STRAIGHT, CapitalOrientation.DIAGONAL);
    public static final EnumProperty<CapitalOrientation> IONIC_ORIENTATION = EnumProperty.of(
            "capital_orientation", CapitalOrientation.class);
    private final Style style;

    protected CapitalBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(orientationProperty(), CapitalOrientation.STRAIGHT));
    }

    public static CapitalBlock create(Settings settings, Style style) {
        return style == Style.GREEK_IONIC ? new Ionic(settings) : new CapitalBlock(settings, style);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    public EnumProperty<CapitalOrientation> orientationProperty() {
        return style == Style.GREEK_IONIC ? IONIC_ORIENTATION : ORIENTATION;
    }

    public CapitalOrientation orientation(BlockState state) {
        return state.get(orientationProperty());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        int degrees = switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> -90;
            default -> 0;
        };
        return state.with(orientationProperty(), orientation(state).turn(degrees, style == Style.GREEK_IONIC));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return mirror == BlockMirror.NONE ? state : state.with(orientationProperty(),
                orientation(state).turn(-2 * orientation(state).degrees, style == Style.GREEK_IONIC));
    }

    // State properties are declared during Block construction, before instance fields exist.
    private static final class Ionic extends CapitalBlock {
        private Ionic(Settings settings) { super(settings, Style.GREEK_IONIC); }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(IONIC_ORIENTATION);
        }
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return style.shape(orientation(state));
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return style.shape(orientation(state));
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

    public enum Style {
        BYZANTINE("byzantine", "Byzantine", 0.970513107, 0.970429544),
        CORINTHIAN("corinthian", "Corinthian", 0.903620366, 0.902639683),
        GOTHIC("gothic", "Gothic", 0.979547970, 0.978666574),
        GREEK_IONIC("greek_ionic", "Greek Ionic", 1.317061573, 0.941511460),
        ROMAN_COMPOSITE("roman_composite", "Roman Composite", 0.912417446, 0.914758735),
        TUSCAN("tuscan", "Tuscan", 0.992720929, 1.000345088);

        private final String styleId;
        private final String displayName;
        private final String resourceStem;
        private final VoxelShape[] shapes;

        Style(String styleId, String displayName, double width, double depth) {
            this.styleId = styleId;
            this.displayName = displayName;
            this.resourceStem = "capital_" + styleId;
            this.shapes = new VoxelShape[CapitalOrientation.values().length];
            for (CapitalOrientation orientation : CapitalOrientation.values()) {
                double halfX = (Math.abs(orientation.cosine) * width + Math.abs(orientation.sine) * depth) * 0.5;
                double halfZ = (Math.abs(orientation.sine) * width + Math.abs(orientation.cosine) * depth) * 0.5;
                shapes[orientation.ordinal()] = VoxelShapes.cuboid(
                        0.5 - halfX, 0.0, 0.5 - halfZ,
                        0.5 + halfX, 1.0, 0.5 + halfZ
                );
            }
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

        private VoxelShape shape(CapitalOrientation orientation) {
            return shapes[orientation.ordinal()];
        }
    }
}
