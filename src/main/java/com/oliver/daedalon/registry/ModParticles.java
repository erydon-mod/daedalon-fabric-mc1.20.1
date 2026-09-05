package com.oliver.daedalon.registry;

import com.oliver.daedalon.Daedalon;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    public static final DefaultParticleType FOUNTAIN_DROP = FabricParticleTypes.simple();

    private ModParticles() { }

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(Daedalon.MOD_ID, "fountain_drop"), FOUNTAIN_DROP);
    }
}
