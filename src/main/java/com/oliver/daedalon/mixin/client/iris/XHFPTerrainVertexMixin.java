package com.oliver.daedalon.mixin.client.iris;

import com.oliver.daedalon.client.model.obj.ShaderTerrainNormalBridge;
import com.oliver.daedalon.client.model.obj.ContainedWaterShaderIds;
import com.oliver.daedalon.client.model.obj.LanternGlazing;
import com.oliver.daedalon.client.model.obj.LanternFlame;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPTerrainVertex;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = XHFPTerrainVertex.class, remap = false)
abstract class XHFPTerrainVertexMixin {
    @Unique
    private static final short IRIS_FLUID_RENDER_TYPE = 1;

    @Unique
    private short daedalon$containedWaterBlockId;

    @Inject(method = "write", at = @At("HEAD"), remap = false)
    private void daedalon$beginNormalWrite(long pointer,
                                         Material material,
                                         ChunkVertexEncoder.Vertex[] vertices,
                                         int sectionIndex,
                                         CallbackInfoReturnable<Long> cir) {
        ShaderTerrainNormalBridge.claim(vertices);
        if (ShaderTerrainNormalBridge.isClaimedContainedWater()) {
            daedalon$containedWaterBlockId = daedalon$resolveWaterBlockId(ShaderTerrainNormalBridge.isClaimedStationaryWater());
        }
    }

    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/system/MemoryUtil;memPutShort(JS)V",
                    ordinal = 0
            ),
            remap = false
    )
    private void daedalon$writeBlockId(long address, short blockId) {
        // Only explicitly tagged panes/flames borrow glass/fire identities. Stone,
        // lead frames, bronze fittings and ordinary OBJ faces keep the real block.
        short resolved = blockId;
        if (ShaderTerrainNormalBridge.isClaimedContainedWater()) {
            resolved = daedalon$containedWaterBlockId;
        } else if (ShaderTerrainNormalBridge.isClaimedGlazing()) {
            resolved = (short) LanternGlazing.shaderBlockId(
                    WorldRenderingSettings.INSTANCE.getBlockStateIds(), blockId);
        } else if (ShaderTerrainNormalBridge.isClaimedFlame()) {
            resolved = (short) LanternFlame.shaderBlockId(
                    WorldRenderingSettings.INSTANCE.getBlockStateIds(), blockId);
        }
        MemoryUtil.memPutShort(
                address,
                resolved
        );
    }

    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/system/MemoryUtil;memPutShort(JS)V",
                    ordinal = 1
            ),
            remap = false
    )
    private void daedalon$writeRenderType(long address, short renderType) {
        // Iris uses 1 for fluid-rendered vertices and -1 for ordinary block faces.
        MemoryUtil.memPutShort(
                address,
                ShaderTerrainNormalBridge.isClaimedContainedWater()
                        ? IRIS_FLUID_RENDER_TYPE
                        : renderType
        );
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

    @Unique
    private static short daedalon$resolveWaterBlockId(boolean stationaryWater) {
        Object2IntMap<BlockState> blockStateIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (blockStateIds == null) {
            return -1;
        }
        int water = blockStateIds.getOrDefault(
                Fluids.WATER.getDefaultState().getBlockState(),
                -1
        );
        return (short) (stationaryWater
                ? ContainedWaterShaderIds.stationaryWater(water, blockStateIds.getOrDefault(Blocks.WATER_CAULDRON.getDefaultState(), -1))
                : water);
    }
}
