package com.oliver.daedalon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FinialSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve vanilla debris while bounding only automatically fitted finial bases. */
@Mixin(ParticleManager.class)
abstract class FinialBreakParticlesMixin {
    @WrapOperation(method="addBlockBreakParticles",at=@At(value="INVOKE",
            target="Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape daedalon$boundedFinialDebris(BlockState state,BlockView world,BlockPos pos,
                                                   Operation<VoxelShape> original) {
        return state.getBlock() instanceof FinialBlock finial && state.get(FinialBlock.SUPPORT)!=FinialSupport.Profile.NONE
                ? finial.getBreakParticleShape(state) : original.call(state,world,pos);
    }
}
