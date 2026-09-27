package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A circular-shaft capital with standard and two-block-wide placement. */
public class CapitalBlock extends Block {
    public static final EnumProperty<CapitalOrientation> ORIENTATION = EnumProperty.of(
            "capital_orientation", CapitalOrientation.class,
            CapitalOrientation.STRAIGHT, CapitalOrientation.DIAGONAL);
    public static final EnumProperty<CapitalOrientation> IONIC_ORIENTATION = EnumProperty.of(
            "capital_orientation", CapitalOrientation.class);
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    private final Style style;

    protected CapitalBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(orientationProperty(), CapitalOrientation.STRAIGHT)
                .with(SIZE, Size.STANDARD));
    }

    public static CapitalBlock create(Settings settings, Style style) {
        return style == Style.GREEK_IONIC ? new Ionic(settings) : new CapitalBlock(settings, style);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, SIZE);
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
            builder.add(IONIC_ORIENTATION, SIZE);
        }
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return shapeForCell(state, 0, 0, 0);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return shapeForCell(state, 0, 0, 0);
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

    public enum Size implements StringIdentifiable {
        STANDARD("standard"), DOUBLE("double");
        private final String id;
        Size(String id) { this.id = id; }
        @Override public String asString() { return id; }
    }

    public VoxelShape shapeForCell(BlockState state, int x, int y, int z) {
        if (state.get(SIZE) == Size.STANDARD) return style.shape(orientation(state));
        VoxelShape source = style.shape(orientation(state));
        // The fitted capital doubles about its original centre and occupies two vertical cells.
        return source.getBoundingBoxes().stream().map(box -> VoxelShapes.cuboid(
                        box.minX * 2 - x, box.minY * 2 - y, box.minZ * 2 - z,
                        box.maxX * 2 - x, box.maxY * 2 - y, box.maxZ * 2 - z))
                .reduce(VoxelShapes.empty(), VoxelShapes::union);
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Size size = CapitalSupportPlacement.isLargeColumnTop(context.getWorld(), context.getBlockPos())
                || context.getPlayer() != null && context.getPlayer().isSneaking()
                ? Size.DOUBLE : Size.STANDARD;
        BlockState state = getDefaultState().with(SIZE, size);
        return size == Size.STANDARD || canOccupy(context.getWorld(), context.getBlockPos()) ? state : null;
    }

    private static boolean canOccupy(World world, BlockPos anchor) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            BlockPos pos = anchor.add(x, y, z);
            if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos)) return false;
            BlockState found = world.getBlockState(pos);
            if (!found.isAir() && !CapitalPartBlock.isOwnedBy(found, pos, anchor)) return false;
        }
        return true;
    }

    private static void sync(World world, BlockPos anchor, BlockState state) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            BlockPos pos = anchor.add(x, y, z);
            if (state.get(SIZE) == Size.DOUBLE) {
                world.setBlockState(pos, com.oliver.daedalon.registry.ModBlocks.capitalPart()
                        .stateForOffset(x, y, z), Block.NOTIFY_ALL);
            } else if (CapitalPartBlock.isOwnedBy(world.getBlockState(pos), pos, anchor)) {
                world.removeBlock(pos, false);
            }
        }
    }

    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && state.get(SIZE) == Size.DOUBLE && canOccupy(world, pos)) sync(world, pos, state);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && next.isOf(this) && next.get(SIZE) == Size.DOUBLE
                && state.get(SIZE) != Size.DOUBLE && !canOccupy(world, pos)) {
            // Debug-stick edits bypass onUse; never let a size edit replace neighbouring blocks.
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        } else if (!world.isClient && (!next.isOf(this) || next.get(SIZE) != state.get(SIZE))) {
            sync(world, pos, next.isOf(this) ? next : state.with(SIZE, Size.STANDARD));
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }

    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos,
                                        PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!player.isSneaking() || !player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
        Size next = state.get(SIZE) == Size.STANDARD ? Size.DOUBLE : Size.STANDARD;
        if (next == Size.DOUBLE && !canOccupy(world, pos)) return ActionResult.FAIL;
        if (!world.isClient) {
            world.setBlockState(pos, state.with(SIZE, next), Block.NOTIFY_ALL);
        }
        return ActionResult.success(world.isClient);
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
