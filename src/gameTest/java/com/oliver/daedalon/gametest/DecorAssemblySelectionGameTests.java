package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorAssemblySelection;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.MonopterosBlock;
import com.oliver.daedalon.block.MonopterosPartBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public final class DecorAssemblySelectionGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 400)
    public void selectingAnyPartIncludesOnlyItsCompleteObject(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(160, 10, 160));
        BlockPos neighbour = anchor.add(5, 0, 0);
        world.setBlockState(neighbour, Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        for (String id : new String[]{"bronze_monopteros_dome", "calacattum_gothic_fountain_basin",
                "calacattum_georgian_fountain_basin", "calacattum_greek_fountain_basin"}) {
            Block block = Registries.BLOCK.get(new Identifier("daedalon", id));
            for (BlockState state : block.getStateManager().getStates()) {
                world.setBlockState(anchor, state, Block.NOTIFY_ALL);
                Set<BlockPos> expected = new HashSet<>();
                expected.add(anchor);
                for (int y = 0; y < 7; y++) for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                    BlockPos pos = anchor.add(x, y, z);
                    var part = world.getBlockState(pos);
                    if (MonopterosPartBlock.isOwnedBy(part, pos, anchor)
                            || FountainBasinPartBlock.isOwnedBy(part, pos, anchor)) expected.add(pos);
                }
                var selected = DecorAssemblySelection.cells(world, anchor);
                context.assertTrue(new HashSet<>(selected).equals(expected), "Selection must contain every owned cell");
                context.assertTrue(selected.size() == expected.size(), "No duplicate selected cells");
                context.assertTrue(!selected.contains(neighbour), "Nearby builds must remain outside the selection");
                for (BlockPos part : expected) {
                    context.assertTrue(new HashSet<>(DecorAssemblySelection.cells(world, part)).equals(expected),
                            "Every part must select the same complete object");
                }
                world.removeBlock(anchor, false);
            }
        }
        context.assertTrue(DecorAssemblySelection.cells(world, neighbour).isEmpty(), "Ordinary blocks use normal selection");
        world.removeBlock(neighbour, false);
        context.complete();
    }
}
