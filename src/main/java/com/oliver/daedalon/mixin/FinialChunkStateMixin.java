package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FinialRaycast;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client network updates skip block lifecycle callbacks, including same-block size changes. */
@Mixin(WorldChunk.class)
abstract class FinialChunkStateMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void daedalon$refreshFinialCells(BlockPos pos,BlockState next,boolean moved,
                                           CallbackInfoReturnable<BlockState> callback) {
        BlockState previous=callback.getReturnValue();
        if(previous==null || !(previous.getBlock() instanceof FinialBlock || next.getBlock() instanceof FinialBlock)) return;
        WorldChunk chunk=(WorldChunk)(Object)this;
        FinialRaycast.update(chunk.getWorld(),pos,chunk.getBlockState(pos));
    }
}
