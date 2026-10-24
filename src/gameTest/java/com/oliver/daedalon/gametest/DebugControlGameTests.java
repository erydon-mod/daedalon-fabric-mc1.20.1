package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.*;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks the real registered blocks and the transformed vanilla debug-stick entry points. */
public final class DebugControlGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void everyRegisteredModelExposesOnlyItsOrderedPlayerControls(TestContext context) {
        Set<Class<?>> families = new HashSet<>();
        int checked = 0;
        for (Block block : Registries.BLOCK) {
            if (!Registries.BLOCK.getId(block).getNamespace().equals("daedalon")) continue;
            BlockState state = block.getDefaultState();
            List<String> actual = controls(state);
            List<String> expected;
            if (block instanceof CapitalPartBlock || block instanceof FountainBasinPartBlock
                    || block instanceof MonopterosPartBlock) expected = List.of();
            else if (block instanceof CapitalBlock) expected = List.of("size", "capital_orientation");
            else if (block instanceof FinialBlock || block instanceof SizedDecorBlock
                    || block instanceof TwoSizeDecorBlock) expected = List.of("size");
            else if (block instanceof StatueBlock || block instanceof UrnBlock) expected = List.of("size", "offset", "facing");
            else if (block instanceof PlinthBlock) expected = List.of("size", "offset", "facing", "waterlogged");
            else if (block instanceof FountainBasinBlock) expected = List.of("size", "raised_base", "waterlogged");
            else if (block instanceof BenchBlock) expected = List.of("width", "facing");
            else if (block instanceof MonopterosBlock) expected = List.of("diameter");
            else if (block instanceof CorbelBlock) expected = List.of("size", "facing");
            else if (block instanceof PanelBlock) expected = List.of("size", "ceiling", "facing");
            else if (block instanceof FriezeBlock frieze) {
                expected = frieze.style().sections() > 1 ? List.of("facing", "pattern", "manual_corner") : List.of("facing", "manual_corner");
                List<String> manual = frieze.style().sections() > 1
                        ? List.of("facing", "pattern", "manual_corner", "join") : List.of("facing", "manual_corner", "join");
                context.assertTrue(controls(state.with(FriezeBlock.MANUAL_CORNER, true)).equals(manual),
                        "Manual frieze joins must follow facing/pattern: " + Registries.BLOCK.getId(block));
            } else if (block instanceof FacingDecorBlock) expected = List.of("facing");
            else expected = List.of();
            context.assertTrue(actual.equals(expected), "Unexpected controls for " + Registries.BLOCK.getId(block)
                    + ": expected " + expected + ", got " + actual);
            families.add(block.getClass());
            checked++;
        }
        context.assertTrue(checked > 100 && families.size() >= 16, "Audit must cover the live model registry");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void freshAndLegacySticksSelectAndEditTheSameRealUrnControls(TestContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        var urn = Registries.BLOCK.stream().filter(block -> block instanceof UrnBlock).findFirst().orElseThrow();
        BlockState original = urn.getDefaultState();
        world.setBlockState(pos, original, Block.NOTIFY_ALL);
        var stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        var usage = new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        Items.DEBUG_STICK.canMine(original, world, pos, player);
        context.assertTrue(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT).equals("size"),
                "A fresh left-click must select the primary Size control");
        context.assertTrue(world.getBlockState(pos) == original, "Selecting a control must preserve every state value");
        Items.DEBUG_STICK.useOnBlock(usage);
        context.assertTrue(world.getBlockState(pos) == original.with(UrnBlock.SIZE, UrnBlock.UrnSize.LARGE),
                "Size must cycle medium to large through the real debug-stick entry point");
        Items.DEBUG_STICK.canMine(world.getBlockState(pos), world, pos, player);
        context.assertTrue(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT).equals("offset"),
                "The next selected setting must be Offset");
        Items.DEBUG_STICK.useOnBlock(usage);
        context.assertTrue(world.getBlockState(pos).get(UrnBlock.OFFSET), "Offset false must cycle to true");

        var legacy = Items.DEBUG_STICK.getDefaultStack();
        String id = Registries.BLOCK.getId(urn).toString();
        legacy.getOrCreateSubNbt("DebugProperty").putString(id, "facing");
        player.setStackInHand(Hand.MAIN_HAND, legacy);
        BlockState before = world.getBlockState(pos);
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)));
        context.assertTrue(world.getBlockState(pos) == before.with(UrnBlock.FACING, Direction.EAST),
                "A legacy per-block Facing selection must remain selected and cycle north to east");
        context.assertTrue(legacy.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT).equals("facing")
                        && legacy.getSubNbt("DebugProperty").getString(id).equals("facing"),
                "Both current shared and legacy vanilla selections must remain readable");
        world.removeBlock(pos, false);
        context.complete();
    }

    private static List<String> controls(BlockState state) {
        return DaedalonDebugProperties.ordered(state).stream().map(property -> property.getName()).toList();
    }
}
