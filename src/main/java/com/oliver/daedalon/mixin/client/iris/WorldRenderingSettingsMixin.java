package com.oliver.daedalon.mixin.client.iris;

import com.oliver.daedalon.block.PanelBlock;
import com.oliver.daedalon.client.model.obj.PanelShaderMaterialIds;
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
        var adjusted = PanelShaderMaterialIds.classify(ids,
                ModBlocks.blocks().stream().filter(block -> block instanceof PanelBlock)
                        .flatMap(block -> block.getStateManager().getStates().stream()).toList(),
                Fluids.WATER.getDefaultState().getBlockState(), Blocks.LADDER.getDefaultState());
        if (adjusted != ids) {
            org.slf4j.LoggerFactory.getLogger("Daedalon/PanelShaders")
                    .info("Classified unmapped panels as non-solid Complementary reflection surfaces");
        }
        return adjusted;
    }
}
