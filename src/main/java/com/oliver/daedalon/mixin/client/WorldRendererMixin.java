package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
    @Inject(method="setWorld",at=@At("HEAD"))
    private void daedalon$resetPavilionBounds(ClientWorld world,CallbackInfo callback) {
        MonopterosRenderBounds.clear();
    }
}
