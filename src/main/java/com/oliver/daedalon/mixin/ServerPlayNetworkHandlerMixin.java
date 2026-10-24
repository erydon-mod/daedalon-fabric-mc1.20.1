package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.TallDecorPlacement;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FinialSupportPlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
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
    private Vec3d daedalon$validateExtendedOutlineHit(Vec3d hit, Vec3d registeredBlockCenter,
                                                     PlayerInteractBlockC2SPacket packet) {
        Vec3d delta=TallDecorPlacement.serverValidationDelta(
                player.getServerWorld(),
                hit,
                registeredBlockCenter
        );
        return player.getStackInHand(packet.getHand()).getItem() instanceof BlockItem item
                && item.getBlock() instanceof FinialBlock
                ? FinialSupportPlacement.serverValidationDelta(player.getServerWorld(),hit,registeredBlockCenter,delta)
                : delta;
    }
}
