package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.BustBlock;
import com.oliver.daedalon.block.CorbelBlock;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FountainAssemblyPlacement;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.block.UrnBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.registry.DecorMetal;
import com.oliver.daedalon.client.search.PlinthSearchVocabulary;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class MetalDecorGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyMetalDecorVariantRetainsItsStoneControlsAndTags(TestContext context) {
        int count = 0;
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            if (!id.getNamespace().equals("daedalon") || !requestedFamily(block)) continue;
            String path = id.getPath();
            if (!path.startsWith("bronze_") && !path.startsWith("silver_")) continue;
            count++;
            String metal = path.substring(0, path.indexOf('_'));
            String suffix = path.substring(path.indexOf('_') + 1);
            Identifier stoneId = new Identifier("daedalon", suffix.equals("spartan_promachos_statue")
                    ? "statue_spartan_promachos_aganite" : "aganite_" + suffix);
            Block stone = Registries.BLOCK.get(stoneId);
            context.assertTrue(stone.getClass() == block.getClass(), "Block type changed for " + id);
            context.assertTrue(stone.getStateManager().getProperties().equals(block.getStateManager().getProperties()),
                    "Placement controls changed for " + id);
            context.assertTrue(stone.getStateManager().getStates().size() == block.getStateManager().getStates().size(),
                    "State variants changed for " + id);
            context.assertTrue(Registries.ITEM.get(id) == block.asItem(), "Missing matching block item for " + id);
            context.assertTrue(block.getDefaultState().isIn(TagKey.of(RegistryKeys.BLOCK, new Identifier("daedalon", metal))),
                    "Missing metal search tag for " + id);
            context.assertTrue(!block.getDefaultState().isIn(TagKey.of(RegistryKeys.BLOCK, new Identifier("material", "stone"))),
                    "Metal classified as stone: " + id);
            if (block instanceof PlinthBlock plinth) {
                context.assertTrue(!plinth.supportsFountains(), "Metal plinth must stay decorative: " + id);
                var stack = block.asItem().getDefaultStack();
                context.assertTrue(PlinthSearchVocabulary.terms(stack).isEmpty(), "Decorative plinth has fountain aliases: " + id);
                context.assertTrue(stack.getTooltip(null, TooltipContext.BASIC).stream().noneMatch(line ->
                                line.getContent() instanceof TranslatableTextContent text
                                        && text.getKey().startsWith("tooltip.daedalon.plinth.fountain")),
                        "Decorative plinth has fountain instructions: " + id);
            }
        }
        context.assertTrue(count == 124, "Expected 124 bronze/silver decorations, found " + count);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void metalPlinthsCannotBecomeFountainAttachments(TestContext context) {
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        Block basinBlock = Registries.BLOCK.get(new Identifier("daedalon", "aganite_gothic_fountain_basin"));
        context.getWorld().setBlockState(anchor, basinBlock.getDefaultState(), Block.NOTIFY_ALL);
        FountainBasinBlockEntity basin = (FountainBasinBlockEntity) context.getWorld().getBlockEntity(anchor);
        var player = context.createMockCreativeServerPlayerInWorld();
        for (PlinthBlock.Style style : PlinthBlock.Style.values()) {
            for (DecorMetal metal : DecorMetal.values()) {
                Block plinth = Registries.BLOCK.get(new Identifier("daedalon", metal.id() + "_" + style.styleId() + "_plinth"));
                context.assertTrue(basin.addPlinthUse(plinth.getDefaultState()) == FountainBasinBlockEntity.AdditionResult.BLOCKED,
                        "Metal plinth accepted by fountain: " + style + " " + metal);
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(plinth));
                var hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
                context.assertTrue(FountainAssemblyPlacement.tryAttach(new ItemUsageContext(player, Hand.MAIN_HAND, hit), plinth) == null,
                        "Metal plinth must retain ordinary item placement");
                context.assertTrue(!basin.hasPlinth(), "Rejected metal plinth must not change the fountain");
            }
        }
        context.complete();
    }

    private static boolean requestedFamily(Block block) {
        return block instanceof UrnBlock || block instanceof StatueBlock || block instanceof BustBlock
                || block instanceof PlinthBlock || block instanceof FinialBlock || block instanceof CorbelBlock;
    }
}
