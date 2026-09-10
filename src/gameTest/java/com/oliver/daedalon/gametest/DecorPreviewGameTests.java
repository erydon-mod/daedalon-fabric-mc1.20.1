package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.MonopterosBlock;
import net.minecraft.registry.Registries;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public final class DecorPreviewGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void allDecorationStatesHaveUsablePreviewBounds(TestContext context) {
        int supported = 0;
        int helpers = 0;
        for (var block : Registries.BLOCK) {
            if (!Registries.BLOCK.getId(block).getNamespace().equals("daedalon")) continue;
            if (!DecorPreviewBounds.supports(block.getDefaultState())) { helpers++; continue; }
            supported++;
            for (var state : block.getStateManager().getStates()) {
                var box = DecorPreviewBounds.bounds(state);
                context.assertTrue(Double.isFinite(box.minX + box.minY + box.minZ + box.maxX + box.maxY + box.maxZ)
                        && box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ,
                        "Invalid preview bounds for " + state);
                if (block instanceof MonopterosBlock) {
                    var size = state.get(MonopterosBlock.DIAMETER);
                    context.assertTrue(box.maxX - box.minX == size.metres && box.maxY - box.minY == size.height,
                            "Monopteros preview must include its full structure");
                }
            }
        }
        context.assertTrue(supported > 4000 && helpers == 2, "Missing decorations or duplicate helper previews");
        context.complete();
    }
}
