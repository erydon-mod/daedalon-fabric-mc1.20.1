package com.oliver.daedalon.mixin.client.sodium;

import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=OcclusionCuller.class,remap=false)
abstract class OcclusionCullerMixin {
    @Inject(method="isWithinFrustum",at=@At("HEAD"),cancellable=true,remap=false)
    private static void daedalon$includePavilionRoof(Viewport viewport,RenderSection section,CallbackInfoReturnable<Boolean> callback) {
        if (MonopterosRenderBounds.get(section.getChunkX(),section.getChunkY(),section.getChunkZ())!=null) {
            callback.setReturnValue(viewport.isBoxVisible(section.getCenterX(),section.getCenterY()+3,section.getCenterZ(),
                    12.125F,12.125F,12.125F));
        }
    }
}
