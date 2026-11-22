package com.oliver.daedalon.mixin.client.iris;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.model.obj.ComplementaryBasinWater;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(value = ProgramSource.class, remap = false)
abstract class ComplementaryWaterProgramMixin {
    @Unique
    private static final AtomicBoolean DAEDALON$LOGGED_BASIN_WATER = new AtomicBoolean();

    @Shadow(remap = false)
    public abstract String getName();

    @ModifyReturnValue(method = "getFragmentSource", at = @At("RETURN"), remap = false, require = 0)
    private Optional<String> daedalon$shadeStationaryWater(Optional<String> fragment) {
        String program = getName();
        if (fragment.isEmpty() || !"gbuffers_water".equals(program)) return fragment;
        String source = fragment.get();
        String adapted = ComplementaryBasinWater.adaptFragment(program, source);
        if (adapted == source) return fragment;
        if (DAEDALON$LOGGED_BASIN_WATER.compareAndSet(false, true)) {
            Daedalon.LOGGER.info("Using Complementary water shading for stationary Louterion basins");
        }
        return Optional.of(adapted);
    }
}
