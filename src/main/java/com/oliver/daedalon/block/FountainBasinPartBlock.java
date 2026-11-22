package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Invisible per-cell interaction geometry for a fountain basin anchor.
 *
 * <p>Vanilla only checks shapes belonging to the block cells immediately around
 * a ray or entity. These parts let the large model use vanilla raycasting and
 * collision without rendering or storing a second copy of the fountain.</p>
 */
public final class FountainBasinPartBlock extends Block implements Waterloggable {
    private static final int HORIZONTAL_OFFSET_BIAS = 2;

    public static final IntProperty OFFSET_X = IntProperty.of("offset_x", 0, 4);
    public static final IntProperty OFFSET_Y = IntProperty.of(
            "offset_y", 0, FountainBasinGeometry.MAX_PART_Y
    );
    public static final IntProperty OFFSET_Z = IntProperty.of("offset_z", 0, 4);
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    private static final BooleanProperty LOUTERION = BooleanProperty.of("louterion");

    public FountainBasinPartBlock(Settings settings) {
        // The visible owner emits material-correct debris when broken. These
        // invisible hit cells use an empty model and must not emit missing-texture debris.
        super(settings.dynamicBounds().noBlockBreakParticles());
        setDefaultState(getStateManager().getDefaultState()
                .with(OFFSET_X, HORIZONTAL_OFFSET_BIAS)
                .with(OFFSET_Y, 0)
                .with(OFFSET_Z, HORIZONTAL_OFFSET_BIAS)
                .with(LOUTERION, false)
                .with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z, WATERLOGGED, LOUTERION);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
                                                 WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }

    public BlockState stateForOffset(int offsetX, int offsetY, int offsetZ) {
        return getDefaultState()
                .with(OFFSET_X, offsetX + HORIZONTAL_OFFSET_BIAS)
                .with(OFFSET_Y, offsetY)
                .with(OFFSET_Z, offsetZ + HORIZONTAL_OFFSET_BIAS);
    }

    BlockState louterionStateForOffset(BlockPos offset) {
        return stateForOffset(offset.getX(), offset.getY(), offset.getZ()).with(LOUTERION, true);
    }

    static boolean isLouterionPart(BlockState state) {
        return state.getBlock() instanceof FountainBasinPartBlock && state.get(LOUTERION);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        int offsetX = offsetX(state);
        int offsetZ = offsetZ(state);
        return withHorizontalOffset(
                state,
                FountainPartOffsetTransform.rotatedX(offsetX, offsetZ, rotation),
                FountainPartOffsetTransform.rotatedZ(offsetX, offsetZ, rotation)
        );
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        int offsetX = offsetX(state);
        int offsetZ = offsetZ(state);
        return withHorizontalOffset(
                state,
                FountainPartOffsetTransform.mirroredX(offsetX, mirror),
                FountainPartOffsetTransform.mirroredZ(offsetZ, mirror)
        );
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state,
                                      BlockView world,
                                      BlockPos pos,
                                      ShapeContext context) {
        Anchor anchor = resolveAnchor(world, pos, state);
        if (anchor == null) {
            return VoxelShapes.empty();
        }
        if (LouterionBasinBlock.isLarge(anchor.state())) {
            return LouterionBasinGeometry.part(anchor.state().get(LouterionBasinBlock.WATERLOGGED),
                    offsetX(state), state.get(OFFSET_Y), offsetZ(state));
        }
        return FountainBasinBlock.outlinePartShape(
                world,
                anchor.pos(),
                anchor.state(),
                offsetX(state),
                state.get(OFFSET_Y),
                offsetZ(state)
        );
    }

    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return getOutlineShape(state, world, pos, ShapeContext.absent());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockView world,
                                        BlockPos pos,
                                        ShapeContext context) {
        Anchor anchor = resolveAnchor(world, pos, state);
        if (anchor == null) {
            return VoxelShapes.empty();
        }
        if (LouterionBasinBlock.isLarge(anchor.state())) {
            return LouterionBasinGeometry.part(false, offsetX(state), state.get(OFFSET_Y), offsetZ(state));
        }
        return FountainBasinBlock.collisionPartShape(
                world,
                anchor.pos(),
                anchor.state(),
                offsetX(state),
                state.get(OFFSET_Y),
                offsetZ(state)
        );
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    // Lighting runs off-thread. Vanilla's default transparency query asks for
    // the outline, which would synchronously load the anchor's chunk while that
    // chunk is waiting for lighting. Invisible interaction cells never occlude.
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
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor == null
                ? ItemStack.EMPTY
                : anchor.state().getBlock().getPickStack(world, anchor.pos(), anchor.state());
    }

    // Keep the hit in its real cell until vanilla validates reach. The anchor
    // stores contained bowl water. Only the raised base's open edge cells carry
    // ordinary surrounding water; upper interaction cells remain fluid-free.
    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor != null && ((Waterloggable) anchor.state().getBlock())
                .canFillWithFluid(world, anchor.pos(), anchor.state(), fluid);
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluid) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor != null && ((Waterloggable) anchor.state().getBlock())
                .tryFillWithFluid(world, anchor.pos(), anchor.state(), fluid);
    }

    @Override
    public ItemStack tryDrainFluid(WorldAccess world, BlockPos pos, BlockState state) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor == null ? ItemStack.EMPTY
                : ((Waterloggable) anchor.state().getBlock())
                        .tryDrainFluid(world, anchor.pos(), anchor.state());
    }

    @Override
    public ActionResult onUse(BlockState state,
                              World world,
                              BlockPos pos,
                              PlayerEntity player,
                              Hand hand,
                              BlockHitResult hit) {
        Anchor anchor = resolveAnchor(world, pos, state);
        if (anchor == null) {
            return ActionResult.PASS;
        }
        return anchor.state().getBlock().onUse(
                anchor.state(),
                world,
                anchor.pos(),
                player,
                hand,
                hit.withBlockPos(anchor.pos())
        );
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        Anchor anchor = resolveAnchor(world, pos, state);
        if (anchor != null && LouterionBasinBlock.isLarge(anchor.state())) {
            return anchor.state().calcBlockBreakingDelta(player, world, anchor.pos());
        }
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            Anchor anchor = resolveAnchor(world, pos, state);
            if (anchor != null) {
                world.breakBlock(anchor.pos(), !player.isCreative(), player);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    public static BlockPos resolveAnchorPos(BlockView world, BlockPos partPos, BlockState partState) {
        Anchor anchor = resolveAnchor(world, partPos, partState);
        return anchor == null ? null : anchor.pos();
    }

    public static boolean isOwnedBy(BlockState partState,
                                    BlockPos partPos,
                                    BlockPos expectedAnchorPos) {
        if (!(partState.getBlock() instanceof FountainBasinPartBlock)) {
            return false;
        }
        return anchorPos(partPos, partState).equals(expectedAnchorPos);
    }

    private static Anchor resolveAnchor(BlockView world, BlockPos partPos, BlockState partState) {
        if (!(partState.getBlock() instanceof FountainBasinPartBlock)) {
            return null;
        }
        BlockPos anchorPos = anchorPos(partPos, partState);
        boolean louterionPart = partState.get(LOUTERION);
        if (louterionPart && world instanceof WorldView view && !view.isChunkLoaded(anchorPos)) return null;
        BlockState anchorState = world.getBlockState(anchorPos);
        boolean valid = louterionPart
                ? LouterionBasinBlock.isLarge(anchorState)
                    && !LouterionBasinGeometry.part(true, offsetX(partState), partState.get(OFFSET_Y), offsetZ(partState)).isEmpty()
                : anchorState.getBlock() instanceof FountainBasinBlock;
        return valid ? new Anchor(anchorPos, anchorState) : null;
    }

    static BlockPos anchorPos(BlockPos partPos, BlockState state) {
        return partPos.add(
                -offsetX(state),
                -state.get(OFFSET_Y),
                -offsetZ(state)
        );
    }

    private static int offsetX(BlockState state) {
        return state.get(OFFSET_X) - HORIZONTAL_OFFSET_BIAS;
    }

    private static int offsetZ(BlockState state) {
        return state.get(OFFSET_Z) - HORIZONTAL_OFFSET_BIAS;
    }

    private static BlockState withHorizontalOffset(BlockState state, int offsetX, int offsetZ) {
        return state
                .with(OFFSET_X, offsetX + HORIZONTAL_OFFSET_BIAS)
                .with(OFFSET_Z, offsetZ + HORIZONTAL_OFFSET_BIAS);
    }

    private record Anchor(BlockPos pos, BlockState state) {
    }
}
