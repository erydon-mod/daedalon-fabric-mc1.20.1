package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.MonopterosBlock;
import com.oliver.daedalon.block.SizedDecorBlock;
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
                if (block instanceof CapitalBlock && state.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE) {
                    context.assertTrue(box.maxY - box.minY == 2.0,
                            "Double capital preview must include both vertical cells");
                }
                if (block instanceof SizedDecorBlock) {
                    var full = SizedDecorBlock.fullShape(state.get(SizedDecorBlock.SIZE)).getBoundingBox();
                    context.assertTrue(box.minX == full.minX && box.minY == full.minY && box.minZ == full.minZ
                                    && box.maxX == full.maxX && box.maxY == full.maxY && box.maxZ == full.maxZ,
                            "Every obelisk preview must retain its complete body bounds");
                    if (state.get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.EXTRA_LARGE) {
                        context.assertTrue(box.getYLength() == 5 && box.getXLength() > 1,
                                "Extra Large previews must include the five-block height and scaled overhang");
                    }
                }
            }
        }
        context.assertTrue(supported > 4000 && helpers == 4, "Missing decorations or duplicate helper previews");
        context.complete();
    }
}
