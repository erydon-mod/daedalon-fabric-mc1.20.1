package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.AnthophorosBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.OperatorEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

public final class AnthophorosGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void hollowWidthsAndBronzeShareStaticShapesAndReliableControls(TestContext context) {
        var world = context.getWorld();
        var anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        var player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        var stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        var hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
        var blocks = ModBlocks.blocks().stream().filter(b -> b instanceof AnthophorosBlock).toList();
        context.assertTrue(blocks.size() == 55, "All 54 stones/aged and bronze must register");
        VoxelShape[][] cached = new VoxelShape[3][4];
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (var block : blocks) {
            context.assertTrue(block.getDefaultState().get(AnthophorosBlock.WIDTH) == 3, "Default width must be 3m");
            context.assertTrue(block.getStateManager().getStates().size() == 12, "Only width and facing are needed");
            for (int width = 2; width <= 4; width++) {
                for (int f = 0; f < facings.length; f++) {
                    var state = block.getDefaultState().with(AnthophorosBlock.WIDTH, width).with(AnthophorosBlock.FACING, facings[f]);
                    world.setBlockState(anchor, state, Block.NOTIFY_ALL);
                    var shape = state.getCollisionShape(world, anchor);
                    var bounds = shape.getBoundingBox();
                    boolean alongX = facings[f].getAxis() == Direction.Axis.Z;
                    double spanX = bounds.maxX - bounds.minX;
                    double spanZ = bounds.maxZ - bounds.minZ;
                    context.assertTrue(Math.abs((alongX ? spanX : spanZ) - width) < 1e-6, "Width mismatch");
                    context.assertTrue(Math.abs((alongX ? spanZ : spanX) - AnthophorosBlock.DEPTH) < 1e-6, "Depth must not scale");
                    context.assertTrue(bounds.minY == 0 && bounds.maxY == 1, "Height must stay 1m");
                    if (cached[width-2][f] == null) cached[width-2][f] = shape;
                    context.assertTrue(cached[width-2][f] == shape, "Every finish must reuse cached shapes");
                    var floorHit = shape.raycast(Vec3d.of(anchor).add(.5, 2, .5), Vec3d.of(anchor).add(.5, 0, .5), anchor);
                    context.assertTrue(floorHit != null && Math.abs(floorHit.getPos().y - anchor.getY() - AnthophorosBlock.FLOOR_HEIGHT) < 1e-6,
                            "Interior must remain hollow and selectable down to the floor");
                    context.assertTrue(world.getBlockEntity(anchor) == null, "No controller is needed");
                }
            }
            world.setBlockState(anchor, block.getDefaultState(), Block.NOTIFY_ALL);
            stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "waterlogged");
            int expected = 3;
            for (int i = 0; i < 12; i++) {
                Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
                expected = expected == 4 ? 2 : expected + 1;
                context.assertTrue(world.getBlockState(anchor).get(AnthophorosBlock.WIDTH) == expected, "Width control stopped");
            }
            player.setSneaking(true);
            Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            context.assertTrue(world.getBlockState(anchor).get(AnthophorosBlock.WIDTH) == 2, "Sneak must reverse width");
            player.setSneaking(false);
            Items.DEBUG_STICK.canMine(world.getBlockState(anchor), world, anchor, player);
            Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            context.assertTrue(world.getBlockState(anchor).get(AnthophorosBlock.FACING) == Direction.WEST, "Facing must cycle independently");
        }
        for (BlockPos pos : BlockPos.iterate(anchor.add(-2,0,-2),anchor.add(2,0,2))) {
            if (!pos.equals(anchor)) context.assertTrue(world.getBlockState(pos).isAir(), "No hidden neighbour writes");
        }
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.complete();
    }
}
