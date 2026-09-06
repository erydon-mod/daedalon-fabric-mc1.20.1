package com.oliver.daedalon.client;

import com.oliver.daedalon.block.MonopterosBlock;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;

public final class DomeParticleBurst {
    private DomeParticleBurst() {}

    public static ParticleS2CPacket apply(ParticleS2CPacket packet, boolean high) {
        // Normal mode allocates nothing. The count guard prevents multiplying
        // again when vanilla dispatches the same packet onto the main thread.
        if (!high || packet.getCount() != 16
                || !(packet.getParameters() instanceof BlockStateParticleEffect effect)
                || effect.getType() != ParticleTypes.BLOCK
                || !(effect.getBlockState().getBlock() instanceof MonopterosBlock)) return packet;
        return new ParticleS2CPacket(effect, packet.isLongDistance(),
                packet.getX(), packet.getY(), packet.getZ(),
                packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ(), packet.getSpeed(), 64);
    }
}
