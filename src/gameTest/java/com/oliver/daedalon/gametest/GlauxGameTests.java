package com.oliver.daedalon.gametest;

import com.oliver.daedalon.glaux.GlauxBlock;
import com.oliver.daedalon.glaux.GlauxRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Actual server placement, break/drop and persistence contracts for the new owl. */
public final class GlauxGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void singleBronzeRegistrationHasNoSizeOrMaterialStates(TestContext context) {
        context.assertTrue(Registries.BLOCK.get(new Identifier("daedalon", "glaux")) == GlauxRegistry.BLOCK, "Glaux must be registered on a dedicated server");
        context.assertTrue(GlauxRegistry.BLOCK.getStateManager().getStates().size() == 8, "Only four facings and two internal halves are allowed");
        context.assertTrue(com.oliver.daedalon.registry.ModItems.catalogueItems().stream().filter(item -> item == GlauxRegistry.ITEM).count() == 1,
                "Creative and browser catalogue must contain exactly one Glaux");
        for (var property : GlauxRegistry.BLOCK.getStateManager().getProperties())
            context.assertTrue(property.getName().equals("facing") || property.getName().equals("half"), "No resizing or material variants");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void placementCreatesOneLowerBlockEntityAndMatchingUpperHalf(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        for (int i = 0; i < 4; i++) {
            BlockPos support = context.getAbsolutePos(new BlockPos(2 + i * 2, 1, 2));
            context.getWorld().setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            player.setYaw(i * 90);
            ItemStack stack = new ItemStack(GlauxRegistry.ITEM);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
            var result = GlauxRegistry.ITEM.place(new ItemPlacementContext(new ItemUsageContext(player, Hand.MAIN_HAND, hit)));
            BlockPos lower = support.up();
            BlockState bottom = context.getWorld().getBlockState(lower), top = context.getWorld().getBlockState(lower.up());
            context.assertTrue(result.isAccepted(), "Glaux placement failed");
            context.assertTrue(GlauxRegistry.isGlaux(bottom) && GlauxRegistry.isGlaux(top), "Both fixed-height cells must exist");
            context.assertTrue(bottom.get(GlauxBlock.HALF) == DoubleBlockHalf.LOWER && top.get(GlauxBlock.HALF) == DoubleBlockHalf.UPPER, "Incorrect owner half");
            context.assertTrue(bottom.get(GlauxBlock.FACING) == top.get(GlauxBlock.FACING), "The two cells lost their facing");
            context.assertTrue(context.getWorld().getBlockEntity(lower) != null && context.getWorld().getBlockEntity(lower.up()) == null, "Exactly one block entity is required");
            context.assertTrue(GlauxRegistry.basePos(context.getWorld(), lower.up()).equals(lower), "Upper interaction must resolve the same owl");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blockedUpperCellDoesNotConsumeOrPartiallyPlaceGlaux(TestContext context) {
        var player = survivalPlayer(context);
        BlockPos support = context.getAbsolutePos(new BlockPos(2, 1, 2)), lower = support.up();
        context.getWorld().setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        context.getWorld().setBlockState(lower.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ItemStack stack = new ItemStack(GlauxRegistry.ITEM, 2);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
        var result = GlauxRegistry.ITEM.place(new ItemPlacementContext(new ItemUsageContext(player, Hand.MAIN_HAND, hit)));
        context.assertTrue(!result.isAccepted() && stack.getCount() == 2, "Blocked placement must not consume the item");
        context.assertTrue(context.getWorld().getBlockState(lower).isAir(), "Blocked placement left a lower half");
        context.assertTrue(context.getWorld().getBlockState(lower.up()).isOf(Blocks.STONE), "Blocked placement replaced the obstruction");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void eitherHalfBreaksAsOneItemInSurvival(TestContext context) {
        var player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int half = 0; half < 2; half++) {
            BlockPos lower = fixture(context, 2 + half * 4);
            context.assertTrue(player.interactionManager.tryBreakBlock(lower.up(half)), "Player could not break Glaux");
            context.assertTrue(context.getWorld().getBlockState(lower).isAir() && context.getWorld().getBlockState(lower.up()).isAir(), "Breaking one half left an orphan");
            Box area = new Box(lower).expand(1.5);
            int count = context.getWorld().getEntitiesByClass(ItemEntity.class, area, item -> item.getStack().isOf(GlauxRegistry.ITEM)).stream().mapToInt(item -> item.getStack().getCount()).sum();
            context.assertTrue(count == 1, "Broken half " + half + " must drop exactly one owl; got " + count);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeBreakDoesNotDuplicateTheOwl(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        BlockPos lower = fixture(context, 2);
        context.assertTrue(player.interactionManager.tryBreakBlock(lower.up()), "Creative break failed");
        context.assertTrue(context.getWorld().getBlockState(lower).isAir() && context.getWorld().getBlockState(lower.up()).isAir(), "Creative upper break left a half");
        context.assertTrue(context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(lower).expand(2), item -> item.getStack().isOf(GlauxRegistry.ITEM)).isEmpty(), "Creative break created a duplicate item");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void editingEitherFacingKeepsBothHalvesTogether(TestContext context) {
        BlockPos lower = fixture(context, 2);
        context.getWorld().setBlockState(lower.up(), context.getWorld().getBlockState(lower.up()).with(GlauxBlock.FACING, Direction.WEST), Block.NOTIFY_ALL);
        context.assertTrue(context.getWorld().getBlockState(lower).get(GlauxBlock.FACING) == Direction.WEST, "Upper edit lost the lower facing");
        context.getWorld().setBlockState(lower, context.getWorld().getBlockState(lower).with(GlauxBlock.FACING, Direction.NORTH), Block.NOTIFY_ALL);
        context.assertTrue(context.getWorld().getBlockState(lower.up()).get(GlauxBlock.FACING) == Direction.NORTH, "Lower edit lost the upper facing");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void removingSupportDropsOneOwlWithoutAnOrphan(TestContext context) {
        BlockPos lower = fixture(context, 2);
        context.getWorld().setBlockState(lower.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(context.getWorld().getBlockState(lower).isAir() && context.getWorld().getBlockState(lower.up()).isAir(), "Lost support left part of Glaux behind");
        int count = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(lower).expand(2), item -> item.getStack().isOf(GlauxRegistry.ITEM)).stream().mapToInt(item -> item.getStack().getCount()).sum();
        context.assertTrue(count == 1, "Lost support should drop one whole owl, got " + count);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void debugStickRotatesWholeOwlWithoutExposingInternalHalves(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        context.getWorld().getServer().getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        BlockPos lower = fixture(context, 2), upper = lower.up();
        var stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        context.assertTrue(com.oliver.daedalon.item.DaedalonDebugProperties.ordered(context.getWorld().getBlockState(upper)).equals(java.util.List.of(GlauxBlock.FACING)), "Only Facing is a player control");
        Items.DEBUG_STICK.canMine(context.getWorld().getBlockState(upper), context.getWorld(), upper, player);
        context.assertTrue(stick.getOrCreateNbt().getString(com.oliver.daedalon.item.DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT).equals("facing"), "A stick must not select the internal half");
        Direction original = context.getWorld().getBlockState(lower).get(GlauxBlock.FACING);
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(upper), Direction.UP, upper, false)));
        var bottom = context.getWorld().getBlockState(lower);
        var top = context.getWorld().getBlockState(upper);
        context.assertTrue(bottom.get(GlauxBlock.FACING) != original && bottom.get(GlauxBlock.FACING) == top.get(GlauxBlock.FACING), "Upper debug-stick use must rotate the entire owl");
        context.assertTrue(bottom.get(GlauxBlock.HALF) == DoubleBlockHalf.LOWER && top.get(GlauxBlock.HALF) == DoubleBlockHalf.UPPER, "Debug editing changed the internal halves");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blockEntityDoesNotPersistConversationOrIdentity(TestContext context) {
        BlockPos lower = fixture(context, 2);
        var data = context.getWorld().getBlockEntity(lower).createNbt();
        context.assertTrue(data.isEmpty(), "Cosmetic/client conversation must not be saved in the world");
        context.complete();
    }

    private static BlockPos fixture(TestContext context, int x) {
        BlockPos lower = context.getAbsolutePos(new BlockPos(x, 2, 2));
        context.getWorld().setBlockState(lower.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        var state = GlauxRegistry.BLOCK.getDefaultState();
        context.getWorld().setBlockState(lower, state, Block.NOTIFY_ALL);
        context.getWorld().setBlockState(lower.up(), state.with(GlauxBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        return lower;
    }

    private static net.minecraft.server.network.ServerPlayerEntity survivalPlayer(TestContext context) {
        // TestContext's creative server-player subclass hardcodes isCreative()
        // to true, even after changeGameMode. Use a real survival player here.
        var world = context.getWorld();
        var player = new net.minecraft.server.network.ServerPlayerEntity(world.getServer(), world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "GlauxSurvival"));
        world.getServer().getPlayerManager().onPlayerConnect(
                new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player);
        player.changeGameMode(GameMode.SURVIVAL);
        context.assertTrue(!player.isCreative(), "The survival fixture must exercise the actual survival drop path");
        return player;
    }
}
