package com.oliver.daedalon.block;

import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** A scalable, hollow fountain basin with bucket-controlled, contained water. */
public final class FountainBasinBlock extends Block implements Waterloggable, BlockEntityProvider {
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final float MEDIUM_MODEL_SCALE = 4.0F;
    public static final float LARGE_MODEL_SCALE = 5.0F;
    public static final float NORMALIZED_MODEL_HEIGHT = 0.26585404F;

    /** Medium-size coordinates measured against the supplied Gothic basin mesh. */
    public static final float MEDIUM_BASIN_FLOOR_Y = 0.40F;
    public static final float MEDIUM_BASE_HALF_WIDTH = 1.9866667F;
    public static final float MEDIUM_BASE_STRAIGHT_HALF_LENGTH = 0.80F;
    public static final float MEDIUM_RIM_HALF_WIDTH = 1.9733334F;
    public static final float MEDIUM_RIM_STRAIGHT_HALF_LENGTH = 0.73333335F;

    /** Medium-size coordinates measured just inside the Gothic basin's rim. */
    public static final float MEDIUM_WATER_SURFACE_Y = 0.9066667F;
    public static final float MEDIUM_WATER_HALF_WIDTH = 1.5866667F;
    public static final float MEDIUM_WATER_BEVEL = 0.6533333F;

    private final Style style;

    public FountainBasinBlock(Settings settings, Style style) {
        super(settings.dynamicBounds().pistonBehavior(PistonBehavior.BLOCK));
        FountainBasinGeometry.initialize();
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(SIZE, Size.MEDIUM)
                .with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SIZE, WATERLOGGED);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FountainBasinBlockEntity(pos, state);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState placement = getDefaultState().with(
                WATERLOGGED,
                context.getWorld().getFluidState(context.getBlockPos()).getFluid() == Fluids.WATER
        );
        return canOccupyParts(context.getWorld(), context.getBlockPos(), placement)
                ? placement
                : null;
    }

    @Override
    public void onBlockAdded(BlockState state,
                             World world,
                             BlockPos pos,
                             BlockState oldState,
                             boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && !oldState.isOf(this)) {
            syncParts(world, pos, state);
        }
    }

    @Override
    public ActionResult onUse(BlockState state,
                              World world,
                              BlockPos pos,
                              PlayerEntity player,
                              Hand hand,
                              BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND
                || !player.shouldCancelInteraction()
                || !player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }
        FountainBasinBlockEntity basin = blockEntity(world, pos);
        if (basin == null || !basin.hasPlinth()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            basin.removeTopPlinthUse();
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void onStateReplaced(BlockState state,
                                World world,
                                BlockPos pos,
                                BlockState newState,
                                boolean moved) {
        if (!world.isClient) {
            if (newState.isOf(this)) {
                // Any state edit also repairs prototype basins placed before the
                // per-cell interaction parts were introduced.
                syncParts(world, pos, newState);
            } else {
                removeOwnedParts(world, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        BlockState filledState = state.with(WATERLOGGED, true);
        return !state.get(WATERLOGGED)
                && fluid == Fluids.WATER
                && prepareStateChange(world, pos, filledState);
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world,
                                    BlockPos pos,
                                    BlockState state,
                                    FluidState fluidState) {
        BlockState filledState = state.with(WATERLOGGED, true);
        if (state.get(WATERLOGGED)
                || fluidState.getFluid() != Fluids.WATER
                || !prepareStateChange(world, pos, filledState)) {
            return false;
        }
        if (!world.isClient()) {
            return world.setBlockState(pos, filledState, Block.NOTIFY_ALL);
        }
        // Deliberately do not schedule a fluid tick. The basin stores one bucket as
        // block state and renders it inside the bowl, so it cannot spread outside.
        return true;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state,
                                      BlockView world,
                                      BlockPos pos,
                                      ShapeContext context) {
        return outlinePartShape(world, pos, state, 0, 0, 0);
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return outlinePartShape(world, pos, state, 0, 0, 0);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockView world,
                                        BlockPos pos,
                                        ShapeContext context) {
        FountainBasinBlockEntity basin = blockEntity(world, pos);
        return basin == null || !basin.hasPlinth()
                ? FountainBasinGeometry.collisionPartShape(style, state.get(SIZE), 0, 0, 0)
                : basin.collisionPartShape(state, 0, 0, 0);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    // This hollow decor is non-occluding. Never ask its block entity for a
    // dynamic outline from the asynchronous lighting/chunk-generation path.
    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public Style style() {
        return style;
    }

    /** Used by the debug stick so growing a basin never overwrites another build. */
    public boolean canChangeSize(WorldAccess world, BlockPos pos, BlockState updatedState) {
        return prepareStateChange(world, pos, updatedState);
    }

    public static VoxelShape collisionShape(Size size) {
        return FountainBasinGeometry.collisionShape(size);
    }

    public static VoxelShape collisionShape(Style style, Size size) {
        return FountainBasinGeometry.collisionShape(style, size);
    }

    public static VoxelShape outlineShape(Size size, boolean waterlogged) {
        return FountainBasinGeometry.outlineShape(size, waterlogged);
    }

    public static VoxelShape outlineShape(Style style, Size size, boolean waterlogged) {
        return FountainBasinGeometry.outlineShape(style, size, waterlogged);
    }

    static VoxelShape collisionPartShape(BlockView world,
                                         BlockPos anchorPos,
                                         BlockState anchorState,
                                         int offsetX,
                                         int offsetY,
                                         int offsetZ) {
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        if (basin == null || !basin.hasPlinth()) {
            return FountainBasinGeometry.collisionPartShape(
                    styleOf(anchorState), anchorState.get(SIZE), offsetX, offsetY, offsetZ
            );
        }
        return basin.collisionPartShape(anchorState, offsetX, offsetY, offsetZ);
    }

    static VoxelShape outlinePartShape(BlockView world,
                                       BlockPos anchorPos,
                                       BlockState anchorState,
                                       int offsetX,
                                       int offsetY,
                                       int offsetZ) {
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        if (basin == null || !basin.hasPlinth()) {
            return FountainBasinGeometry.outlinePartShape(
                    styleOf(anchorState),
                    anchorState.get(SIZE),
                    anchorState.get(WATERLOGGED),
                    offsetX,
                    offsetY,
                    offsetZ
            );
        }
        return basin.outlinePartShape(anchorState, offsetX, offsetY, offsetZ);
    }

    /**
     * Returns the contained water surface height relative to one cell already
     * visited by vanilla's fluid scan, or zero when that cell is not part of a
     * filled fountain. This keeps normal water behaviour without a second world
     * scan for every loaded entity.
     */
    public static double containedWaterSurfaceHeight(BlockView world,
                                                     BlockPos cellPos,
                                                     BlockState cellState,
                                                     Box entityBox) {
        BlockPos anchorPos;
        if (cellState.getBlock() instanceof FountainBasinBlock) {
            anchorPos = cellPos;
        } else if (cellState.getBlock() instanceof FountainBasinPartBlock) {
            anchorPos = FountainBasinPartBlock.resolveAnchorPos(world, cellPos, cellState);
        } else {
            return 0.0D;
        }
        if (anchorPos == null) {
            return 0.0D;
        }

        BlockState anchorState = world.getBlockState(anchorPos);
        if (!(anchorState.getBlock() instanceof FountainBasinBlock anchorBlock)
                || !anchorState.get(WATERLOGGED)) {
            return 0.0D;
        }

        Box box = entityBox.contract(0.001D);
        double containedDepth = FountainBasinGeometry.containedWaterHeight(
                anchorPos,
                anchorBlock.style(),
                anchorState.get(SIZE),
                box
        );
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        if (basin != null) {
            containedDepth = Math.max(
                    containedDepth,
                    basin.layout(anchorState).containedBowlWaterHeight(anchorPos, box)
            );
        }
        return containedDepth <= 0.0D
                ? 0.0D
                : box.minY + containedDepth - cellPos.getY();
    }

    static double containedWaterHeight(BlockPos anchorPos, Size size, Box box) {
        return FountainBasinGeometry.containedWaterHeight(anchorPos, size, box);
    }

    static double containedWaterHeight(BlockPos anchorPos, Style style, Size size, Box box) {
        return FountainBasinGeometry.containedWaterHeight(anchorPos, style, size, box);
    }

    private static boolean canOccupyParts(BlockView world,
                                          BlockPos anchorPos,
                                          BlockState anchorState) {
        return canOccupyAssembly(
                world,
                anchorPos,
                anchorState,
                plinthState(world, anchorPos),
                bowlCount(world, anchorPos)
        );
    }

    /** Validates and primes the layout cache for an immediately following state edit. */
    private boolean prepareStateChange(BlockView world,
                                       BlockPos anchorPos,
                                       BlockState updatedState) {
        if (!updatedState.isOf(this)) {
            return false;
        }
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        return basin == null
                ? canOccupyAssembly(world, anchorPos, updatedState, null, 0)
                : basin.prepareStateChange(world, updatedState);
    }

    public static boolean canOccupyAssembly(BlockView world,
                                            BlockPos anchorPos,
                                            BlockState anchorState,
                                            BlockState plinthState,
                                            int bowlCount) {
        FountainAssemblyLayout layout = FountainAssemblyLayout.create(
                styleOf(anchorState),
                anchorState.get(SIZE),
                anchorState.get(WATERLOGGED),
                plinthState,
                bowlCount
        );
        return canOccupyAssembly(world, anchorPos, layout);
    }

    public static boolean canOccupyAssembly(BlockView world,
                                            BlockPos anchorPos,
                                            FountainAssemblyLayout layout) {
        if (layout.maximumY() > FountainBasinGeometry.MAX_PART_Y + 1.0D) {
            return false;
        }
        for (int offsetY = 0; offsetY <= FountainBasinGeometry.MAX_PART_Y; offsetY++) {
            for (int offsetX = -FountainBasinGeometry.PART_RADIUS;
                 offsetX <= FountainBasinGeometry.PART_RADIUS;
                 offsetX++) {
                for (int offsetZ = -FountainBasinGeometry.PART_RADIUS;
                     offsetZ <= FountainBasinGeometry.PART_RADIUS;
                     offsetZ++) {
                    if (isAnchorOffset(offsetX, offsetY, offsetZ)
                            || layout.outlinePartShape(offsetX, offsetY, offsetZ).isEmpty()) {
                        continue;
                    }
                    BlockPos partPos = anchorPos.add(offsetX, offsetY, offsetZ);
                    if (world.isOutOfHeightLimit(partPos)) {
                        return false;
                    }
                    BlockState existing = world.getBlockState(partPos);
                    if (!existing.isAir()
                            && !existing.isReplaceable()
                            && !FountainBasinPartBlock.isOwnedBy(existing, partPos, anchorPos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void refreshAssembly(World world, BlockPos anchorPos, BlockState anchorState) {
        if (!world.isClient && anchorState.getBlock() instanceof FountainBasinBlock) {
            syncParts(world, anchorPos, anchorState);
        }
    }

    private static void syncParts(World world, BlockPos anchorPos, BlockState anchorState) {
        FountainBasinPartBlock partBlock = ModBlocks.fountainBasinPart();
        boolean[] required = new boolean[FountainBasinGeometry.PART_SHAPE_COUNT];
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        FountainAssemblyLayout layout = basin == null || !basin.hasPlinth()
                ? null
                : basin.layout(anchorState);

        for (int offsetY = 0; offsetY <= FountainBasinGeometry.MAX_PART_Y; offsetY++) {
            for (int offsetX = -FountainBasinGeometry.PART_RADIUS;
                 offsetX <= FountainBasinGeometry.PART_RADIUS;
                 offsetX++) {
                for (int offsetZ = -FountainBasinGeometry.PART_RADIUS;
                     offsetZ <= FountainBasinGeometry.PART_RADIUS;
                     offsetZ++) {
                    VoxelShape partShape = layout == null
                            ? FountainBasinGeometry.outlinePartShape(
                                    styleOf(anchorState),
                                    anchorState.get(SIZE),
                                    anchorState.get(WATERLOGGED),
                                    offsetX,
                                    offsetY,
                                    offsetZ
                            )
                            : layout.outlinePartShape(offsetX, offsetY, offsetZ);
                    if (!isAnchorOffset(offsetX, offsetY, offsetZ)
                            && !partShape.isEmpty()) {
                        required[FountainBasinGeometry.partIndex(
                                offsetX,
                                offsetY,
                                offsetZ
                        )] = true;
                    }
                }
            }
        }

        for (int offsetY = 0; offsetY <= FountainBasinGeometry.MAX_PART_Y; offsetY++) {
            for (int offsetX = -FountainBasinGeometry.PART_RADIUS;
                 offsetX <= FountainBasinGeometry.PART_RADIUS;
                 offsetX++) {
                for (int offsetZ = -FountainBasinGeometry.PART_RADIUS;
                     offsetZ <= FountainBasinGeometry.PART_RADIUS;
                     offsetZ++) {
                    if (isAnchorOffset(offsetX, offsetY, offsetZ)) {
                        continue;
                    }
                    int index = FountainBasinGeometry.partIndex(offsetX, offsetY, offsetZ);
                    BlockPos partPos = anchorPos.add(offsetX, offsetY, offsetZ);
                    BlockState existing = world.getBlockState(partPos);
                    boolean owned = FountainBasinPartBlock.isOwnedBy(
                            existing,
                            partPos,
                            anchorPos
                    );
                    if (owned && !required[index]) {
                        world.setBlockState(
                                partPos,
                                Blocks.AIR.getDefaultState(),
                                Block.NOTIFY_ALL | Block.SKIP_DROPS
                        );
                    } else if (!owned && required[index]
                            && (existing.isAir() || existing.isReplaceable())) {
                        world.setBlockState(
                                partPos,
                                partBlock.stateForOffset(offsetX, offsetY, offsetZ),
                                Block.NOTIFY_ALL | Block.SKIP_DROPS
                        );
                    }
                }
            }
        }
    }

    private static BlockState plinthState(BlockView world, BlockPos anchorPos) {
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        return basin == null ? null : basin.plinthState();
    }

    private static int bowlCount(BlockView world, BlockPos anchorPos) {
        FountainBasinBlockEntity basin = blockEntity(world, anchorPos);
        return basin == null ? 0 : basin.bowlCount();
    }

    private static FountainBasinBlockEntity blockEntity(BlockView world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof FountainBasinBlockEntity basin ? basin : null;
    }

    private static void removeOwnedParts(World world, BlockPos anchorPos) {
        for (int offsetY = 0; offsetY <= FountainBasinGeometry.MAX_PART_Y; offsetY++) {
            for (int offsetX = -FountainBasinGeometry.PART_RADIUS;
                 offsetX <= FountainBasinGeometry.PART_RADIUS;
                 offsetX++) {
                for (int offsetZ = -FountainBasinGeometry.PART_RADIUS;
                     offsetZ <= FountainBasinGeometry.PART_RADIUS;
                     offsetZ++) {
                    if (isAnchorOffset(offsetX, offsetY, offsetZ)) {
                        continue;
                    }
                    BlockPos partPos = anchorPos.add(offsetX, offsetY, offsetZ);
                    if (FountainBasinPartBlock.isOwnedBy(
                            world.getBlockState(partPos),
                            partPos,
                            anchorPos
                    )) {
                        world.setBlockState(
                                partPos,
                                Blocks.AIR.getDefaultState(),
                                Block.NOTIFY_ALL | Block.SKIP_DROPS
                        );
                    }
                }
            }
        }
    }

    private static boolean isAnchorOffset(int offsetX, int offsetY, int offsetZ) {
        return offsetX == 0 && offsetY == 0 && offsetZ == 0;
    }

    private static Style styleOf(BlockState state) {
        if (state.getBlock() instanceof FountainBasinBlock basin) {
            return basin.style();
        }
        throw new IllegalArgumentException("Expected a fountain basin block state");
    }

    public enum Size implements StringIdentifiable {
        SMALL("small", 3.0F),
        MEDIUM("medium", MEDIUM_MODEL_SCALE),
        LARGE("large", LARGE_MODEL_SCALE);

        private final String name;
        private final float modelScale;

        Size(String name, float modelScale) {
            this.name = name;
            this.modelScale = modelScale;
        }

        public float modelScale() {
            return modelScale;
        }

        public float mediumRatio() {
            return modelScale / MEDIUM_MODEL_SCALE;
        }

        public float renderScale() {
            return modelScale / LARGE_MODEL_SCALE;
        }

        public float basinFloorY() {
            return MEDIUM_BASIN_FLOOR_Y * mediumRatio();
        }

        public float basinFloorY(Style style) {
            return style.basinFloorY(this);
        }

        public float waterSurfaceY() {
            return MEDIUM_WATER_SURFACE_Y * mediumRatio();
        }

        public float waterSurfaceY(Style style) {
            return style.waterSurfaceY(this);
        }

        public float waterHalfWidth() {
            return MEDIUM_WATER_HALF_WIDTH * mediumRatio();
        }

        public float waterHalfWidth(Style style) {
            return style.waterHalfWidth(this);
        }

        public float waterBevel() {
            return MEDIUM_WATER_BEVEL * mediumRatio();
        }

        public float waterBevel(Style style) {
            return style.waterBevel(this);
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

    public enum Style {
        GOTHIC(
                "gothic_fountain_basin",
                "fountain_gothic_basin",
                "Gothic Fountain Basin",
                FountainBowlModel.Style.GOTHIC,
                new GeometryProfile(
                        0.26585404F,
                        0.10000000F,
                        0.49666668F,
                        0.20000000F,
                        0.49333335F,
                        0.18333334F,
                        0.22666667F,
                        0.39666668F,
                        0.16333333F
                )
        ),
        GEORGIAN(
                "georgian_fountain_basin",
                "fountain_georgian_basin",
                "Georgian Fountain Basin",
                FountainBowlModel.Style.GREEK,
                new GeometryProfile(
                        0.21243666F,
                        0.08000000F,
                        0.42000000F,
                        0.17000000F,
                        0.41500000F,
                        0.17000000F,
                        0.19000000F,
                        0.37000000F,
                        0.15000000F
                )
        ),
        GREEK(
                "greek_fountain_basin",
                "fountain_greek_basin",
                "Greek Fountain Basin",
                FountainBowlModel.Style.GEORGIAN,
                new GeometryProfile(
                        0.20023336F,
                        0.08000000F,
                        0.48000000F,
                        0.18000000F,
                        0.46000000F,
                        0.16000000F,
                        0.18000000F,
                        0.29500000F,
                        0.12200000F
                )
        );

        private final String idSuffix;
        private final String resourceStem;
        private final String displayName;
        private final FountainBowlModel.Style bowlStyle;
        private final GeometryProfile geometry;

        Style(String idSuffix,
              String resourceStem,
              String displayName,
              FountainBowlModel.Style bowlStyle,
              GeometryProfile geometry) {
            this.idSuffix = idSuffix;
            this.resourceStem = resourceStem;
            this.displayName = displayName;
            this.bowlStyle = bowlStyle;
            this.geometry = geometry;
        }

        public String idSuffix() {
            return idSuffix;
        }

        public String resourceStem() {
            return resourceStem;
        }

        public String displayName() {
            return displayName;
        }

        public FountainBowlModel.Style bowlStyle() {
            return bowlStyle;
        }

        public GeometryProfile geometry() {
            return geometry;
        }

        public float modelHeight(Size size) {
            return geometry.modelHeight() * size.modelScale();
        }

        public float basinFloorY(Size size) {
            return geometry.basinFloorY() * size.modelScale();
        }

        public float baseHalfWidth(Size size) {
            return geometry.baseHalfWidth() * size.modelScale();
        }

        public float baseStraightHalfLength(Size size) {
            return geometry.baseStraightHalfLength() * size.modelScale();
        }

        public float rimHalfWidth(Size size) {
            return geometry.rimHalfWidth() * size.modelScale();
        }

        public float rimStraightHalfLength(Size size) {
            return geometry.rimStraightHalfLength() * size.modelScale();
        }

        public float outlineBaseHalfWidth(Size size) {
            return (this == GOTHIC ? geometry.baseHalfWidth() : 0.50000000F)
                    * size.modelScale();
        }

        public float outlineBaseStraightHalfLength(Size size) {
            float normalized = switch (this) {
                case GOTHIC -> geometry.baseStraightHalfLength();
                case GEORGIAN -> 0.20700000F;
                case GREEK -> 0.28400000F;
            };
            return normalized * size.modelScale();
        }

        public float outlineRimHalfWidth(Size size) {
            return (this == GOTHIC ? geometry.rimHalfWidth() : 0.50000000F)
                    * size.modelScale();
        }

        public float outlineRimStraightHalfLength(Size size) {
            float normalized = switch (this) {
                case GOTHIC -> geometry.rimStraightHalfLength();
                case GEORGIAN -> 0.20700000F;
                case GREEK -> 0.28400000F;
            };
            return normalized * size.modelScale();
        }

        public float waterSurfaceY(Size size) {
            return geometry.waterSurfaceY() * size.modelScale();
        }

        public float waterHalfWidth(Size size) {
            FountainWaterShape outline = waterOutline();
            return (outline == null ? geometry.waterHalfWidth() : outline.halfWidth()) * size.modelScale();
        }

        public float waterBevel(Size size) {
            return geometry.waterBevel() * size.modelScale();
        }

        /** Gothic retains its authored octagon; the other basins use measured contours. */
        public FountainWaterShape waterOutline() {
            return switch (this) {
                case GOTHIC -> null;
                case GEORGIAN -> FountainWaterShape.GEORGIAN;
                case GREEK -> FountainWaterShape.GREEK;
            };
        }
    }

    public record GeometryProfile(float modelHeight,
                                  float basinFloorY,
                                  float baseHalfWidth,
                                  float baseStraightHalfLength,
                                  float rimHalfWidth,
                                  float rimStraightHalfLength,
                                  float waterSurfaceY,
                                  float waterHalfWidth,
                                  float waterBevel) {
    }
}
