package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Invisible selection and collision cells belonging to a double capital. */
public final class CapitalPartBlock extends Block {
    public static final IntProperty X = IntProperty.of("offset_x", 0, 1);
    public static final IntProperty Y = IntProperty.of("offset_y", 0, 1);
    public static final IntProperty Z = IntProperty.of("offset_z", 0, 1);

    public CapitalPartBlock(Settings settings) { super(settings.dynamicBounds().noBlockBreakParticles()); }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    public BlockState stateForOffset(int x, int y, int z) {
        return getDefaultState().with(X, x).with(Y, y).with(Z, z);
    }

    public static BlockPos anchorPos(BlockPos pos, BlockState state) {
        return pos.add(-state.get(X), -state.get(Y), -state.get(Z));
    }

    /** Recover helper offsets changed by older debug sticks without borrowing a neighbouring assembly. */
    public static BlockPos resolveAnchorPos(BlockView world, BlockPos pos, BlockState state) {
        BlockPos expected = anchorPos(pos, state);
        BlockState expectedState=world.getBlockState(expected);
        if (expectedState.getBlock() instanceof CapitalBlock && expectedState.get(CapitalBlock.SIZE)==CapitalBlock.Size.DOUBLE) return expected;
        BlockPos found = null;
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++) {
            BlockPos candidate=pos.add(-x,-y,-z);
            if (!candidate.equals(expected) && isCompleteCapital(world,candidate)) {
                if (found!=null) return null;
                found=candidate;
            }
        }
        return found;
    }

    private static boolean isCompleteCapital(BlockView world, BlockPos anchor) {
        BlockState state=world.getBlockState(anchor);
        if (!(state.getBlock() instanceof CapitalBlock) || state.get(CapitalBlock.SIZE)!=CapitalBlock.Size.DOUBLE) return false;
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++)
            if (x+y+z>0 && !(world.getBlockState(anchor.add(x,y,z)).getBlock() instanceof CapitalPartBlock)) return false;
        return true;
    }

    public static void repairOffsets(net.minecraft.world.WorldAccess world,BlockPos anchor) {
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++) {
            if (x+y+z==0) continue;
            BlockPos pos=anchor.add(x,y,z);
            BlockState part=world.getBlockState(pos);
            if (part.getBlock() instanceof CapitalPartBlock) {
                BlockState corrected=part.with(X,x).with(Y,y).with(Z,z);
                if (corrected!=part) world.setBlockState(pos,corrected,Block.NOTIFY_LISTENERS);
            }
        }
    }

    public static boolean isOwnedBy(BlockState state, BlockPos pos, BlockPos anchor) {
        return state.getBlock() instanceof CapitalPartBlock && anchorPos(pos, state).equals(anchor);
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockPos anchor = resolveAnchorPos(world, pos, state);
        if (anchor==null) return VoxelShapes.empty();
        BlockState anchorState = world.getBlockState(anchor);
        return anchorState.getBlock() instanceof CapitalBlock capital
                && anchorState.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE
                ? capital.shapeForCell(anchorState, pos.getX()-anchor.getX(),pos.getY()-anchor.getY(),pos.getZ()-anchor.getZ())
                : VoxelShapes.empty();
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override public boolean canReplace(BlockState state, ItemPlacementContext context) {
        return getOutlineShape(state, context.getWorld(), context.getBlockPos(), ShapeContext.absent()).isEmpty();
    }

    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }
    @Override public int getOpacity(BlockState state, BlockView world, BlockPos pos) { return 0; }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    @Override public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        BlockPos anchorPos=resolveAnchorPos(world,pos,state);
        BlockState anchor=anchorPos==null ? null : world.getBlockState(anchorPos);
        return anchor!=null && anchor.getBlock() instanceof CapitalBlock ? anchor.getBlock().asItem().getDefaultStack() : ItemStack.EMPTY;
    }

    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos,
                                        PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos anchor = resolveAnchorPos(world, pos, state);
        if (anchor==null) return ActionResult.PASS;
        if (!world.isClient) repairOffsets(world,anchor);
        BlockState anchorState = world.getBlockState(anchor);
        return anchorState.getBlock() instanceof CapitalBlock capital
                && anchorState.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE
                ? capital.onUse(anchorState, world, anchor, player, hand, hit.withBlockPos(anchor))
                : ActionResult.PASS;
    }

    @Override public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos anchor = resolveAnchorPos(world, pos, state);
            if (anchor==null) { super.onBreak(world,pos,state,player); return; }
            repairOffsets(world,anchor);
            BlockState anchorState = world.getBlockState(anchor);
            if (anchorState.getBlock() instanceof CapitalBlock
                    && anchorState.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE) {
                world.breakBlock(anchor, !player.isCreative(), player);
            }
        }
        super.onBreak(world, pos, state, player);
    }
}
