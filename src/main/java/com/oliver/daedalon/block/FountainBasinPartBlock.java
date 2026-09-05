package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

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

    public FountainBasinPartBlock(Settings settings) {
        // The visible owner emits material-correct debris when broken. These
        // invisible hit cells use an empty model and must not emit missing-texture debris.
        super(settings.dynamicBounds().noBlockBreakParticles());
        setDefaultState(getStateManager().getDefaultState()
                .with(OFFSET_X, HORIZONTAL_OFFSET_BIAS)
                .with(OFFSET_Y, 0)
                .with(OFFSET_Z, HORIZONTAL_OFFSET_BIAS));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    public BlockState stateForOffset(int offsetX, int offsetY, int offsetZ) {
        return getDefaultState()
                .with(OFFSET_X, offsetX + HORIZONTAL_OFFSET_BIAS)
                .with(OFFSET_Y, offsetY)
                .with(OFFSET_Z, offsetZ + HORIZONTAL_OFFSET_BIAS);
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
                : anchor.state().getBlock().asItem().getDefaultStack();
    }

    // Keep the hit in its real cell until vanilla validates reach. The anchor
    // stores the water; proxy cells never gain fluid state or schedule ticks.
    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor != null && ((FountainBasinBlock) anchor.state().getBlock())
                .canFillWithFluid(world, anchor.pos(), anchor.state(), fluid);
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluid) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor != null && ((FountainBasinBlock) anchor.state().getBlock())
                .tryFillWithFluid(world, anchor.pos(), anchor.state(), fluid);
    }

    @Override
    public ItemStack tryDrainFluid(WorldAccess world, BlockPos pos, BlockState state) {
        Anchor anchor = resolveAnchor(world, pos, state);
        return anchor == null ? ItemStack.EMPTY
                : ((FountainBasinBlock) anchor.state().getBlock())
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
        if (anchor == null || !(anchor.state().getBlock() instanceof FountainBasinBlock basin)) {
            return ActionResult.PASS;
        }
        return basin.onUse(
                anchor.state(),
                world,
                anchor.pos(),
                player,
                hand,
                hit.withBlockPos(anchor.pos())
        );
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
        BlockState anchorState = world.getBlockState(anchorPos);
        return anchorState.getBlock() instanceof FountainBasinBlock
                ? new Anchor(anchorPos, anchorState)
                : null;
    }

    private static BlockPos anchorPos(BlockPos partPos, BlockState state) {
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
