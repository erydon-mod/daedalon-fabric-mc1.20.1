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
        var bounds=MonopterosRenderBounds.get(section.getChunkX(),section.getChunkY(),section.getChunkZ());
        if (bounds!=null) {
            int x=MonopterosRenderBounds.frustumCentre(bounds.minX,bounds.maxX),
                    y=MonopterosRenderBounds.frustumCentre(bounds.minY,bounds.maxY),
                    z=MonopterosRenderBounds.frustumCentre(bounds.minZ,bounds.maxZ);
            callback.setReturnValue(viewport.isBoxVisible(x,y,z,
                    MonopterosRenderBounds.frustumExtent(bounds.minX,bounds.maxX,x),
                    MonopterosRenderBounds.frustumExtent(bounds.minY,bounds.maxY,y),
                    MonopterosRenderBounds.frustumExtent(bounds.minZ,bounds.maxZ,z)));
        }
    }
}
