package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.TallDecorPlacement;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Validates mining reach against a Daedalon model's real extended outline. */
@Mixin(ServerPlayerInteractionManager.class)
abstract class ServerPlayerInteractionManagerMixin {
    @Shadow
    protected ServerWorld world;

    @Redirect(
            method = "processBlockBreakingAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;squaredDistanceTo(Lnet/minecraft/util/math/Vec3d;)D",
                    ordinal = 0
            )
    )
    private double daedalon$distanceToExtendedOutline(Vec3d eye,
                                                       Vec3d registeredBlockCenter) {
        return TallDecorPlacement.serverBreakingDistanceSquared(
                world,
                eye,
                registeredBlockCenter
        );
    }
}
