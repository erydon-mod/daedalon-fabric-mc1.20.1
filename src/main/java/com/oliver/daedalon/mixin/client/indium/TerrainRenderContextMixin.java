package com.oliver.daedalon.mixin.client.indium;

import com.oliver.daedalon.client.model.obj.ShaderTerrainNormalBridge;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TerrainRenderContext.class, remap = false)
abstract class TerrainRenderContextMixin {
    @Shadow
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void daedalon$keepTriangleVertexOrder(MutableQuadViewImpl quad, Material material, CallbackInfo ci) {
        // Fabric represents a triangle as A-B-C-C. Sodium's FLIP orientation
        // rotates that to B-C-C-A, so Iris sees a zero-area first triangle when
        // it generates shader tangents. Keep genuine quads unchanged.
        if (quad.x(2) == quad.x(3)
                && quad.y(2) == quad.y(3)
                && quad.z(2) == quad.z(3)) {
            quad.orientation(ModelQuadOrientation.NORMAL);
        }
    }

    @Inject(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void daedalon$captureVertexNormals(MutableQuadViewImpl quad, Material material, CallbackInfo ci) {
        boolean containedWater = ShaderTerrainNormalBridge.isContainedWaterQuad(quad.tag());
        if ((!ShaderTerrainNormalBridge.isObjQuad(quad.tag()) && !containedWater)
                || !quad.hasAllVertexNormals()) {
            ShaderTerrainNormalBridge.discard();
            return;
        }

        ModelQuadOrientation orientation = quad.orientation();
        int i0 = orientation.getVertexIndex(0);
        int i1 = orientation.getVertexIndex(1);
        int i2 = orientation.getVertexIndex(2);
        int i3 = orientation.getVertexIndex(3);
        if (containedWater) {
            ShaderTerrainNormalBridge.publishContainedWater(
                    vertices,
                    quad.normalX(i0), quad.normalY(i0), quad.normalZ(i0),
                    quad.normalX(i1), quad.normalY(i1), quad.normalZ(i1),
                    quad.normalX(i2), quad.normalY(i2), quad.normalZ(i2),
                    quad.normalX(i3), quad.normalY(i3), quad.normalZ(i3)
            );
        } else {
            ShaderTerrainNormalBridge.publish(
                    vertices,
                    quad.normalX(i0), quad.normalY(i0), quad.normalZ(i0),
                    quad.normalX(i1), quad.normalY(i1), quad.normalZ(i1),
                    quad.normalX(i2), quad.normalY(i2), quad.normalZ(i2),
                    quad.normalX(i3), quad.normalY(i3), quad.normalZ(i3)
            );
        }
    }
}
