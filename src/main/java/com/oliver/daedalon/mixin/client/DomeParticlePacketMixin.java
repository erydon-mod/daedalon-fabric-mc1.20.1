package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.DomeParticleBurst;
import com.oliver.daedalon.client.DomeParticleOption;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class DomeParticlePacketMixin {
    @ModifyVariable(method = "onParticle", at = @At("HEAD"), argsOnly = true)
    private ParticleS2CPacket daedalon$domeParticleCount(ParticleS2CPacket packet) {
        return DomeParticleBurst.apply(packet, DomeParticleOption.isHigh());
    }
}
