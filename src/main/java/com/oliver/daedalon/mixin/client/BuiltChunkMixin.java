package com.oliver.daedalon.mixin.client;

import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla/Indigo equivalent of the optional Sodium frustum correction. */
@Mixin(ChunkBuilder.BuiltChunk.class)
abstract class BuiltChunkMixin {
    @Inject(method="getBoundingBox",at=@At("RETURN"),cancellable=true)
    private void daedalon$includePavilionRoof(CallbackInfoReturnable<Box> callback) {
        Box original=callback.getReturnValue();
        Box extended=MonopterosRenderBounds.get(((int)original.minX)>>4,((int)original.minY)>>4,((int)original.minZ)>>4);
        if (extended!=null) callback.setReturnValue(extended);
    }
}
