package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.item.OlympianGodCycle;
import com.oliver.daedalon.item.OlympianGodItem;
import com.oliver.daedalon.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.concurrent.atomic.AtomicReference;

/** Real placement, persisted stack state and creative synchronization for the selector item. */
public final class OlympianGodItemGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void everyFinishPlacesTwoCompleteSetsWithoutReplacingAnyRealStatueItem(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(30, 3, 30));
        var player = context.createMockCreativeServerPlayerInWorld();
        context.assertTrue(ModItems.olympianGodItems().size() == 56,
                "The selector needs every standard/aged stone and both metals");
        int placements = 0;
        for (Item item : ModItems.olympianGodItems()) {
            context.assertTrue(item instanceof OlympianGodItem && !(item instanceof BlockItem),
                    "Selectors must be item-only so real statue block items remain intact");
            context.assertTrue(ModItems.catalogueItems().contains(item) && !ModItems.blockItems().contains(item),
                    "Selectors belong in Creative without pretending to be registered blocks");
            var selector = (OlympianGodItem) item;
            Identifier id = Registries.ITEM.getId(item);
            context.assertTrue(!Registries.BLOCK.containsId(id), "The selector must not register a duplicate world block");
            String prefix = id.getPath().substring(0, id.getPath().length() - ("_" + OlympianGodItem.ID_SUFFIX).length());
            ItemStack stack = item.getDefaultStack();
            player.setStackInHand(Hand.MAIN_HAND, stack);
            for (int placed = 0; placed < 24; placed++) {
                clearTarget(context, anchor);
                String subject = OlympianGodCycle.SUBJECTS.get(placed % 12);
                Identifier targetId = new Identifier("daedalon", prefix + "_" + subject + "_statue");
                Item actualItem = Registries.ITEM.get(targetId);
                context.assertTrue(selector.nextStatueItem(stack) == actualItem,
                        "Next target must keep its material and the approved order: " + id + " / " + placed);
                context.assertTrue(use(player, Hand.MAIN_HAND, anchor).isAccepted(),
                        "Ordinary clear-space placement failed: " + id + " / " + placed);
                var state = world.getBlockState(anchor);
                context.assertTrue(Registries.BLOCK.getId(state.getBlock()).equals(targetId),
                        "The selector placed the wrong real statue: " + id + " / " + placed);
                context.assertTrue(state.getBlock().asItem() == actualItem && state.getBlock().asItem() != selector,
                        "Pick/drop identity must remain the original real statue item: " + targetId);
                context.assertTrue(OlympianGodItem.nextIndex(stack) == (placed + 1) % 12 && stack.getCount() == 1,
                        "Creative must advance exactly once without consuming the selector: " + id + " / " + placed);
                if (placed == 6) {
                    stack = ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));
                    player.setStackInHand(Hand.MAIN_HAND, stack);
                    context.assertTrue(OlympianGodItem.nextIndex(stack) == 7,
                            "Saving/reloading a partially placed set must preserve its cursor: " + id);
                }
                placements++;
            }
            context.assertTrue(selector.nextStatueItem(stack) == Registries.ITEM.get(new Identifier("daedalon", prefix + "_zeus_statue")),
                    "Both complete sets must wrap back to Zeus: " + id);
        }
        clearTarget(context, anchor);
        world.removeBlock(anchor.down(), false);
        context.assertTrue(placements == 1344, "Cover all 56 finishes with two complete twelve-god cycles");
        System.out.println("DAEDALON_OLYMPIAN_SELECTOR passed finishes=56 placements=" + placements + " sets=112");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void separatePlayersStacksAndHandsKeepIndependentSavedCursorsAndPublishCreativeUpdates(TestContext context) {
        BlockPos anchor = context.getAbsolutePos(new BlockPos(30, 3, 30));
        ServerPlayerEntity first = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity second = context.createMockCreativeServerPlayerInWorld();
        OlympianGodItem item = selector("glacium");
        ItemStack firstStack = item.getDefaultStack(), secondStack = item.getDefaultStack();
        first.setStackInHand(Hand.MAIN_HAND, firstStack);
        second.setStackInHand(Hand.MAIN_HAND, secondStack);
        var synchronizedStack = new AtomicReference<ItemStack>();
        ScreenHandlerListener listener = new ScreenHandlerListener() {
            @Override public void onSlotUpdate(ScreenHandler handler, int slot, ItemStack stack) {
                if (stack.isOf(item)) synchronizedStack.set(stack.copy());
            }
            @Override public void onPropertyUpdate(ScreenHandler handler, int property, int value) { }
        };
        first.currentScreenHandler.addListener(listener);
        clearTarget(context, anchor);
        context.assertTrue(use(first, Hand.MAIN_HAND, anchor).isAccepted(), "First player's Zeus must place");
        context.assertTrue(OlympianGodItem.nextIndex(firstStack) == 1 && OlympianGodItem.nextIndex(secondStack) == 0,
                "One player must not advance another player's fresh selector");
        context.assertTrue(synchronizedStack.get() != null && OlympianGodItem.nextIndex(synchronizedStack.get()) == 1,
                "Creative placement must publish changed cursor NBT even when stack count stays the same");
        clearTarget(context, anchor);
        context.assertTrue(use(second, Hand.MAIN_HAND, anchor).isAccepted()
                        && Registries.BLOCK.getId(context.getWorld().getBlockState(anchor).getBlock()).getPath().equals("glacium_zeus_statue"),
                "The second player must still start with Zeus");
        ItemStack restored = ItemStack.fromNbt(firstStack.writeNbt(new NbtCompound()));
        first.setStackInHand(Hand.MAIN_HAND, restored);
        clearTarget(context, anchor);
        context.assertTrue(use(first, Hand.MAIN_HAND, anchor).isAccepted()
                        && Registries.BLOCK.getId(context.getWorld().getBlockState(anchor).getBlock()).getPath().equals("glacium_hera_statue"),
                "A reloaded held selector must continue with Hera");
        context.assertTrue(OlympianGodItem.nextIndex(restored) == 2 && OlympianGodItem.nextIndex(secondStack) == 1,
                "Saved progress must remain per stack rather than per item type");
        ItemStack offhand = item.getDefaultStack();
        first.setStackInHand(Hand.OFF_HAND, offhand);
        clearTarget(context, anchor);
        context.assertTrue(use(first, Hand.OFF_HAND, anchor).isAccepted() && OlympianGodItem.nextIndex(offhand) == 1
                        && OlympianGodItem.nextIndex(restored) == 2,
                "Offhand must have its own cursor and leave the main hand unchanged");
        first.currentScreenHandler.removeListener(listener);
        clearTarget(context, anchor);
        context.getWorld().removeBlock(anchor.down(), false);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void failedPlacementDoesNotAdvanceOrConsumeAndNormalPlacementNbtAndSurvivalCountsRemainValid(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(30, 3, 30));
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        OlympianGodItem item = selector("bronze");
        ItemStack stack = new ItemStack(item, 3);
        stack.getOrCreateNbt().putString("SelectorMarker", "preserve-me");
        var properties = stack.getOrCreateSubNbt(BlockItem.BLOCK_STATE_TAG_KEY);
        properties.putString("size", "large");
        properties.putString("offset", "true");
        properties.putString("orientation", "diagonal");
        properties.putString("facing", "east");
        player.setStackInHand(Hand.MAIN_HAND, stack);
        clearTarget(context, anchor);
        world.setBlockState(anchor, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        NbtCompound beforeFailure = stack.writeNbt(new NbtCompound());
        context.assertTrue(!use(player, Hand.MAIN_HAND, anchor).isAccepted(), "Occupied placement must fail normally");
        context.assertTrue(beforeFailure.equals(stack.writeNbt(new NbtCompound())) && stack.getCount() == 3,
                "Failed placement must leave the complete held stack and cursor untouched");
        clearTarget(context, anchor);
        context.assertTrue(use(player, Hand.MAIN_HAND, anchor).isAccepted(), "Cleared placement must accept Zeus");
        var state = world.getBlockState(anchor);
        context.assertTrue(Registries.BLOCK.getId(state.getBlock()).getPath().equals("bronze_zeus_statue")
                        && state.get(StatueBlock.SIZE) == StatueBlock.StatueSize.LARGE && state.get(StatueBlock.OFFSET)
                        && state.get(StatueBlock.ORIENTATION) == DecorOrientation.DIAGONAL && state.get(StatueBlock.FACING) == Direction.EAST,
                "Normal BlockStateTag size, offset and eight-way pose must reach the actual statue");
        context.assertTrue(stack.getCount() == 2 && OlympianGodItem.nextIndex(stack) == 1,
                "Survival must consume exactly one selector unit and advance once");
        context.assertTrue(player.getMainHandStack() == stack && stack.getNbt().getString("SelectorMarker").equals("preserve-me"),
                "Delegation must not replace the held item or erase unrelated selector data");
        stack.getOrCreateNbt().putInt(OlympianGodItem.NEXT_OLYMPIAN_NBT, Integer.MAX_VALUE);
        clearTarget(context, anchor);
        context.assertTrue(use(player, Hand.MAIN_HAND, anchor).isAccepted()
                        && Registries.BLOCK.getId(world.getBlockState(anchor).getBlock()).getPath().equals("bronze_zeus_statue")
                        && stack.getCount() == 1 && OlympianGodItem.nextIndex(stack) == 1,
                "Malformed saved progress must safely restart at Zeus and retain normal consumption");
        clearTarget(context, anchor);
        context.assertTrue(use(player, Hand.MAIN_HAND, anchor).isAccepted() && stack.isEmpty(),
                "The last survival unit must place normally and leave an empty hand");
        clearTarget(context, anchor);
        world.removeBlock(anchor.down(), false);
        context.complete();
    }

    private static OlympianGodItem selector(String prefix) {
        return (OlympianGodItem) Registries.ITEM.get(new Identifier("daedalon", prefix + "_" + OlympianGodItem.ID_SUFFIX));
    }

    private static void clearTarget(TestContext context, BlockPos anchor) {
        context.getWorld().setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.getWorld().setBlockState(anchor.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static net.minecraft.util.ActionResult use(ServerPlayerEntity player, Hand hand, BlockPos anchor) {
        BlockPos support = anchor.down();
        return player.getStackInHand(hand).useOnBlock(new ItemUsageContext(player, hand,
                new BlockHitResult(Vec3d.of(support).add(.5, 1, .5), Direction.UP, support, false)));
    }
}
