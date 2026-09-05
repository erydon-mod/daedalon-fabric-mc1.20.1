package com.oliver.daedalon.block;

import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Turns repeated uses of one plinth into a fixed fountain stack. */
public final class FountainAssemblyPlacement {
    private FountainAssemblyPlacement() {
    }

    /** Returns null when normal BlockItem placement should continue. */
    public static ActionResult tryAttach(ItemUsageContext context, Block requestedBlock) {
        if (!(requestedBlock instanceof PlinthBlock)) {
            return null;
        }

        World world = context.getWorld();
        BlockPos anchorPos = resolveAnchor(world, context.getBlockPos());
        if (anchorPos == null) {
            return null;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(anchorPos) instanceof FountainBasinBlockEntity basin)) {
            return ActionResult.FAIL;
        }

        BlockState plinthState = requestedBlock.getDefaultState()
                .with(PlinthBlock.OFFSET, false)
                .with(PlinthBlock.FACING, context.getHorizontalPlayerFacing());
        FountainBasinBlockEntity.AdditionResult result = basin.addPlinthUse(plinthState);
        PlayerEntity player = context.getPlayer();
        switch (result) {
            case PLINTH_ADDED -> message(player, "message.daedalon.fountain_plinth_added");
            case BOWL_ADDED -> message(player, "message.daedalon.fountain_bowl_added");
            case COMPLETE -> {
                message(player, "message.daedalon.fountain_assembly_complete");
                return ActionResult.FAIL;
            }
            case WRONG_PLINTH -> {
                message(player, "message.daedalon.fountain_plinth_mismatch");
                return ActionResult.FAIL;
            }
            case BLOCKED -> {
                message(player, "message.daedalon.fountain_assembly_blocked");
                return ActionResult.FAIL;
            }
        }

        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }

    private static BlockPos resolveAnchor(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof FountainBasinBlock) {
            return pos.toImmutable();
        }
        if (state.getBlock() instanceof FountainBasinPartBlock) {
            return FountainBasinPartBlock.resolveAnchorPos(world, pos, state);
        }
        return null;
    }

    private static void message(PlayerEntity player, String key) {
        if (player != null) {
            player.sendMessage(Text.translatable(key), true);
        }
    }
}
