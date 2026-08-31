package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.TallDecorPlacement;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Redirect(
            method = "onPlayerInteractBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;subtract(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d daedalon$validateExtendedOutlineHit(Vec3d hit, Vec3d registeredBlockCenter) {
        return TallDecorPlacement.serverValidationDelta(
                player.getServerWorld(),
                hit,
                registeredBlockCenter
        );
    }
}
