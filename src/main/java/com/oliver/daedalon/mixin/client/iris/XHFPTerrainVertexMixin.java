package com.oliver.daedalon.mixin.client.iris;

import com.oliver.daedalon.client.model.obj.ShaderTerrainNormalBridge;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPTerrainVertex;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = XHFPTerrainVertex.class, remap = false)
abstract class XHFPTerrainVertexMixin {
    @Inject(method = "write", at = @At("HEAD"), remap = false)
    private void daedalon$beginNormalWrite(long pointer,
                                         Material material,
                                         ChunkVertexEncoder.Vertex[] vertices,
                                         int sectionIndex,
                                         CallbackInfoReturnable<Long> cir) {
        ShaderTerrainNormalBridge.claim(vertices);
    }

    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/system/MemoryUtil;memPutInt(JI)V",
                    ordinal = 7
            ),
            remap = false
    )
    private void daedalon$writeVertexNormal(long address, int flatNormal) {
        MemoryUtil.memPutInt(address, ShaderTerrainNormalBridge.nextNormal(flatNormal));
    }

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void daedalon$finishNormalWrite(long pointer,
                                          Material material,
                                          ChunkVertexEncoder.Vertex[] vertices,
                                          int sectionIndex,
                                          CallbackInfoReturnable<Long> cir) {
        ShaderTerrainNormalBridge.release();
    }
}
