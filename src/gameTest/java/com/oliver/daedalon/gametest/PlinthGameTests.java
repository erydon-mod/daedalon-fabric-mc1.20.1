package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.client.search.PlinthSearchVocabulary;
import net.minecraft.client.item.TooltipContext;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PlinthGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void fountainSearchAndTooltipsCoverEveryPlinth(TestContext context) {
        var fountain = TagKey.of(RegistryKeys.ITEM, new Identifier("daedalon", "fountain"));
        var waterFeature = TagKey.of(RegistryKeys.ITEM, new Identifier("daedalon", "water_feature"));
        int count = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof PlinthBlock)) continue;
            count++;
            var stack = block.asItem().getDefaultStack();
            context.assertTrue(stack.isIn(fountain) && stack.isIn(waterFeature),
                    "Every plinth must appear in fountain and water feature tag searches");
            var terms = PlinthSearchVocabulary.terms(stack);
            context.assertTrue(terms.contains("fountain") && terms.contains("fountains"),
                    "All browser aliases must include singular and plural fountain searches");
            for (var tooltipContext : new TooltipContext[]{TooltipContext.BASIC, TooltipContext.BASIC.withCreative()}) {
                var tooltip = stack.getTooltip(null, tooltipContext);
                for (String key : new String[]{"tooltip.daedalon.plinth.fountain", "tooltip.daedalon.plinth.fountain_use"}) {
                    context.assertTrue(tooltip.stream().anyMatch(line -> line.getContent() instanceof TranslatableTextContent text
                                    && text.getKey().equals(key)),
                            "Hover and Creative indexing must both include " + key);
                }
            }
        }
        context.assertTrue(count == 270, "Cover all five styles and 54 normal/aged finishes");
        var unrelated = Items.STONE.getDefaultStack();
        context.assertTrue(PlinthSearchVocabulary.terms(unrelated).isEmpty(), "Ordinary blocks must not gain fountain aliases");
        context.assertTrue(PlinthSearchVocabulary.appendTerms("stone", unrelated).equals("stone"),
                "Unrelated REI entries must keep their original search text");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void allPlinthStatesFillDrainAndPersist(TestContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        int count = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof PlinthBlock plinth)) continue;
            count++;
            NbtCompound legacy = new NbtCompound();
            legacy.putString("Name", Registries.BLOCK.getId(block).toString());
            context.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, legacy).result().orElseThrow()
                    == block.getDefaultState(), "Legacy plinths must load dry without changing appearance");
            for (BlockState dry : block.getStateManager().getStates()) {
                if (dry.get(PlinthBlock.WATERLOGGED)) continue;
                world.setBlockState(pos, dry, Block.NOTIFY_ALL);
                context.assertTrue(!plinth.canFillWithFluid(world, pos, dry, Fluids.LAVA), "Plinths must reject lava");
                context.assertTrue(plinth.tryFillWithFluid(world, pos, dry, Fluids.WATER.getStill(false)),
                        "Every plinth state must accept water");
                BlockState wet = world.getBlockState(pos);
                context.assertTrue(wet == dry.with(PlinthBlock.WATERLOGGED, true), "Filling must preserve size, offset and facing");
                context.assertTrue(wet.getFluidState().isStill(), "Waterlogged plinth must retain source water");
                var saved = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, wet).result().orElseThrow();
                context.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow() == wet,
                        "Waterlogging must survive save and load");
                context.assertTrue(plinth.tryDrainFluid(world, pos, wet).isOf(Items.WATER_BUCKET), "Bucket must recover water");
                context.assertTrue(world.getBlockState(pos) == dry, "Draining must leave the plinth intact");
            }
        }
        context.assertTrue(count == 270, "All five styles and 54 finishes must be covered");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void placementInWaterKeepsTheSource(TestContext context) {
        var world = context.getWorld();
        var player = context.createMockCreativeServerPlayerInWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            var block = Registries.BLOCK.get(new Identifier("daedalon", "aganite_" + style.styleId() + "_plinth"));
            world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            var placement = new ItemPlacementContext(player, Hand.MAIN_HAND, block.asItem().getDefaultStack(),
                    new BlockHitResult(Vec3d.ofBottomCenter(pos), Direction.UP, pos.down(), false));
            var state = block.getPlacementState(placement);
            context.assertTrue(placement.getBlockPos().equals(pos), "Placement must target water cell");
            context.assertTrue(state.get(PlinthBlock.WATERLOGGED), "Underwater placement must preserve source water");
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            context.assertTrue(world.getFluidState(pos).isStill(), "Placed plinth must contain source water");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void flowingWaterDoesNotBreakPlinths(TestContext context) {
        var world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(new BlockPos(0, 2, 0));
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            BlockPos pos = origin.add(style.ordinal(), 0, 1);
            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pos.south().down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            BlockState state = Registries.BLOCK.get(new Identifier("daedalon", "aganite_" + style.styleId() + "_plinth"))
                    .getDefaultState().with(PlinthBlock.SIZE, PlinthBlock.Size.SMALL);
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            // Water must meet the plinth before finding an easier downhill route.
            world.setBlockState(pos.up(), Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.runAtTick(40, () -> {
            for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
                BlockPos pos = origin.add(style.ordinal(), 0, 1);
                var state = world.getBlockState(pos);
                context.assertTrue(state.getBlock() instanceof PlinthBlock, "Flowing water must not wash away " + style);
                // Java's standard Waterloggable accepts source water, not flowing water.
                // Flow must leave the decoration intact; a bucket fills its water cell.
                context.assertTrue(((PlinthBlock) state.getBlock()).tryFillWithFluid(
                        world, pos, state, Fluids.WATER.getStill(false)), "Source water must fill " + style);
                world.setBlockState(pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(pos.south(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.getBlockState(pos).getStateForNeighborUpdate(
                        Direction.SOUTH, Blocks.AIR.getDefaultState(), world, pos, pos.south());
            }
        });
        context.runAtTick(60, () -> {
            for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
                BlockPos pos = origin.add(style.ordinal(), 0, 1);
                context.assertTrue(world.getFluidState(pos.south()).getFluid() != Fluids.EMPTY,
                        "Waterlogged plinth must resume flowing after a neighbor update");
            }
            context.complete();
        });
    }
}
