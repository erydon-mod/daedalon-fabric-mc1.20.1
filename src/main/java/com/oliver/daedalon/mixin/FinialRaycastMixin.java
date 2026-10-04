package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.FinialRaycast;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockView.class)
public interface FinialRaycastMixin {
    @ModifyReturnValue(method="raycastBlock",at=@At("RETURN"))
    default BlockHitResult daedalon$targetFittedFinial(BlockHitResult original,Vec3d start,Vec3d end,
                                                      BlockPos pos,VoxelShape shape,BlockState state) {
        return FinialRaycast.closest((BlockView)this,start,end,pos,state,original);
    }
}
