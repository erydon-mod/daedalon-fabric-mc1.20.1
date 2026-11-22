package com.oliver.daedalon.block;

import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.BlockItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Bounded click-time chain edits. No periodic search or block entity is required. */
public final class LanternPlacement {
    public static final int MAX_CHAIN=64;
    private static final ThreadLocal<ItemUsageContext> ORIGINAL_USE=new ThreadLocal<>();
    private LanternPlacement() {}
    /** Own the original face/hit before any general block-item context redirects. */
    public static ActionResult use(ItemUsageContext original,BlockItem item) {
        ActionResult extended=tryExtend(original,item.getBlock());
        if(extended!=null) return extended;
        // Vanilla reapplies BlockStateTag after getPlacementState. Old picked
        // items must not override mounting selected by this new support click.
        var tag=original.getStack().getSubNbt("BlockStateTag");
        if(tag!=null) { tag.remove("mount");tag.remove("facing");tag.remove("canopy"); }
        ItemUsageContext previous=ORIGINAL_USE.get();ORIGINAL_USE.set(original);
        try { return item.place(new OriginalPlacement(original)); }
        finally { if(previous==null) ORIGINAL_USE.remove();else ORIGINAL_USE.set(previous); }
    }
    public static Direction clickedSide(ItemPlacementContext context) {
        ItemUsageContext original=ORIGINAL_USE.get();return original==null?context.getSide():original.getSide();
    }

    public static BlockPos resolveOwner(BlockView world,BlockPos pos) {
        BlockState state=world.getBlockState(pos);
        if (state.getBlock() instanceof LanternBlock) return pos.toImmutable();
        if (state.getBlock() instanceof LanternPartBlock) return LanternPartBlock.resolveAnchorPos(world,pos,state);
        if (!(state.getBlock() instanceof LanternChainBlock)) return null;
        for (int i=1;i<=MAX_CHAIN+1;i++) {
            BlockPos below=pos.down(i);BlockState found=world.getBlockState(below);
            if (found.getBlock() instanceof LanternChainBlock) continue;
            return found.getBlock() instanceof LanternBlock && found.get(LanternBlock.MOUNT)==LanternBlock.Mount.HANGING?below:null;
        }
        return null;
    }
    public static List<BlockPos> assemblyCells(BlockView world,BlockPos hit) {
        BlockPos owner=resolveOwner(world,hit);
        if (owner==null) return List.of();
        List<BlockPos> result=new ArrayList<>();result.add(owner);
        for(int offset:new int[]{-1,1}) {
            BlockPos part=owner.up(offset);
            if(LanternPartBlock.isOwnedBy(world.getBlockState(part),part,owner)) result.add(part);
        }
        for (int i=1;i<=MAX_CHAIN;i++) {
            BlockPos chain=owner.up(i);
            if (!(world.getBlockState(chain).getBlock() instanceof LanternChainBlock)) break;
            result.add(chain);
        }
        return List.copyOf(result);
    }
    public static void clearChainAbove(World world,BlockPos owner) {
        for (int i=1;i<=MAX_CHAIN;i++) {
            BlockPos pos=owner.up(i);
            if (!(world.getBlockState(pos).getBlock() instanceof LanternChainBlock)) break;
            world.removeBlock(pos,false);
        }
    }
    public static void syncChainSize(World world,BlockPos owner,LanternBlock.Size size) {
        for (int i=1;i<=MAX_CHAIN;i++) {
            BlockPos pos=owner.up(i);BlockState state=world.getBlockState(pos);
            if (!(state.getBlock() instanceof LanternChainBlock)) break;
            if (state.get(LanternBlock.SIZE)!=size) world.setBlockState(pos,state.with(LanternBlock.SIZE,size),Block.NOTIFY_ALL);
        }
    }
    /** Returns null for ordinary item placement. Chain adjustment does not create or consume a lantern. */
    public static ActionResult tryExtend(ItemUsageContext context,Block requested) {
        if (!(requested instanceof LanternBlock) || context.getSide()!=Direction.DOWN) return null;
        PlayerEntity player=context.getPlayer();
        if (player!=null && player.shouldCancelInteraction()) return null;
        World world=context.getWorld();BlockPos owner=resolveOwner(world,context.getBlockPos());
        if (owner==null) return null;
        BlockState old=world.getBlockState(owner);
        if (old.get(LanternBlock.MOUNT)!=LanternBlock.Mount.HANGING) return null;
        if (assemblyCells(world,owner).stream().filter(p->world.getBlockState(p).getBlock() instanceof LanternChainBlock).count()>=MAX_CHAIN) return ActionResult.FAIL;
        BlockPos target=owner.down();BlockState found=world.getBlockState(target);
        if (!found.isAir() && !LanternPartBlock.isOwnedBy(found,target,owner)) return ActionResult.FAIL;
        BlockState lowered=old.with(LanternBlock.CANOPY,false);
        if (!LanternBlock.canOccupy(world,target,lowered)) return ActionResult.FAIL;
        if (player!=null && (!player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player,owner) || !world.canPlayerModifyAt(player,target)
                || LanternBlock.needsLowerPart(lowered) && !world.canPlayerModifyAt(player,target.down()))) return ActionResult.FAIL;
        if (world.isClient) return ActionResult.SUCCESS;
        // All cells are validated above, and no intervening world tick runs in this edit.
        BlockState chain=ModBlocks.lanternChain().getDefaultState().with(LanternBlock.SIZE,old.get(LanternBlock.SIZE)).with(LanternBlock.CANOPY,old.get(LanternBlock.CANOPY));
        world.setBlockState(owner,chain,Block.NOTIFY_ALL);
        world.setBlockState(target,lowered,Block.NOTIFY_ALL);
        return ActionResult.CONSUME;
    }
    public static ItemPlacementContext redirect(ItemPlacementContext context,Block requested) {
        if (!(requested instanceof LanternBlock) || clickedSide(context)!=Direction.DOWN || context.getPlayer()==null || !context.getPlayer().shouldCancelInteraction()) return context;
        ItemUsageContext original=ORIGINAL_USE.get();
        BlockPos support=original!=null?original.getBlockPos():context.canReplaceExisting()?context.getBlockPos():context.getBlockPos().offset(context.getSide().getOpposite());
        BlockPos owner=resolveOwner(context.getWorld(),support);
        if (owner==null) return context;
        BlockState found=context.getWorld().getBlockState(owner);
        if (found.get(LanternBlock.MOUNT)!=LanternBlock.Mount.HANGING) return context;
        BlockPos target=owner.down(LanternBlock.needsLowerPart(found)?2:1);
        return new LowerPlacement(context,target);
    }
    private static final class OriginalPlacement extends ItemPlacementContext {
        private OriginalPlacement(ItemUsageContext original) {
            super(original.getWorld(),original.getPlayer(),original.getHand(),original.getStack(),
                    new BlockHitResult(original.getHitPos(),original.getSide(),original.getBlockPos(),original.hitsInsideBlock()));
        }
    }
    public static final class LowerPlacement extends ItemPlacementContext {
        private final BlockPos target;
        private LowerPlacement(ItemPlacementContext original,BlockPos target) { super(original);this.target=target;canReplaceExisting=false; }
        @Override public BlockPos getBlockPos() { return target==null?super.getBlockPos():target; }
        @Override public Direction getSide() { return Direction.DOWN; }
    }
}
