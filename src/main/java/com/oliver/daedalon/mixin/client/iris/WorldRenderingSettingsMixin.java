package com.oliver.daedalon.mixin.client.iris;

import com.oliver.daedalon.block.PanelBlock;
import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.client.model.obj.PanelShaderMaterialIds;
import com.oliver.daedalon.client.model.obj.LanternShaderMaterialIds;
import com.oliver.daedalon.registry.ModBlocks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = WorldRenderingSettings.class, remap = false)
abstract class WorldRenderingSettingsMixin {
    @ModifyVariable(method = "setBlockStateIds", at = @At("HEAD"), argsOnly = true, remap = false)
    private Object2IntMap<BlockState> daedalon$classifySurfacePanels(Object2IntMap<BlockState> ids) {
        // ERYDON classifies the returned BlockMaterialMapping map before Iris passes it here.
        // It deliberately skips luminous states, so preserve each lantern's matching OFF surface.
        var surfaces = LanternShaderMaterialIds.inheritOffSurfaceIds(ids,
                ModBlocks.blocks().stream().filter(block -> block instanceof LanternBlock)
                        .flatMap(block -> block.getStateManager().getStates().stream())
                        .filter(state -> state.get(LanternBlock.LIT)).toList(),
                state -> state.with(LanternBlock.LIT, false));
        var adjusted = PanelShaderMaterialIds.classify(surfaces,
                java.util.stream.Stream.concat(
                        ModBlocks.blocks().stream().filter(block -> block instanceof PanelBlock || block instanceof LanternBlock),
                        java.util.stream.Stream.of(ModBlocks.lanternPart(),ModBlocks.lanternChain()))
                        .filter(java.util.Objects::nonNull)
                        .flatMap(block -> block.getStateManager().getStates().stream()).toList(),
                Fluids.WATER.getDefaultState().getBlockState(), Blocks.LADDER.getDefaultState());
        if (surfaces != ids) {
            org.slf4j.LoggerFactory.getLogger("Daedalon/PanelShaders")
                    .info("Preserved mapped OFF-state surface materials on unmapped lit lantern states");
        }
        if (adjusted != surfaces) {
            org.slf4j.LoggerFactory.getLogger("Daedalon/PanelShaders")
                    .info("Classified unmapped panels and lanterns as non-solid Complementary reflection surfaces");
        }
        return adjusted;
    }
}
