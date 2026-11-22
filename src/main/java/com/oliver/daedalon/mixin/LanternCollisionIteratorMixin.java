package com.oliver.daedalon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternPartBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.BlockCollisionSpliterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Admit only lantern owners/helpers to vanilla's existing corner-overhang collision candidates. */
@Mixin(BlockCollisionSpliterator.class)
public abstract class LanternCollisionIteratorMixin {
    @WrapOperation(method="computeNext",at=@At(value="INVOKE",
            target="Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"))
    private boolean daedalon$admitLanternCorners(BlockState state,Block block,Operation<Boolean> original) {
        return original.call(state,block) || block==Blocks.MOVING_PISTON
                && (state.getBlock() instanceof LanternBlock || state.getBlock() instanceof LanternPartBlock);
    }
}
