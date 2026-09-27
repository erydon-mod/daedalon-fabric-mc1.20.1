package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.CapitalPartBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public final class CapitalGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void allCapitalsCyclePersistAndTransform(TestContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        var stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        var usage = new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        int count = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof CapitalBlock capital)) continue;
            count++;
            int variants = capital.style() == CapitalBlock.Style.GREEK_IONIC ? 4 : 2;
            context.assertTrue(block.getStateManager().getStates().size() == variants * 2, "Orientation and size are the only capital states");
            BlockState original = block.getDefaultState();
            context.assertTrue(capital.orientation(original) == CapitalOrientation.STRAIGHT, "Old appearance must be the default");
            context.assertTrue(original.get(CapitalBlock.SIZE) == CapitalBlock.Size.STANDARD, "Legacy capitals remain standard size");
            var legacy = new NbtCompound(); legacy.putString("Name", Registries.BLOCK.getId(block).toString());
            var restoredLegacy = BlockState.CODEC.parse(NbtOps.INSTANCE, legacy).result().orElseThrow();
            context.assertTrue(restoredLegacy == original, "Legacy saves without orientation must remain straight");
            world.setBlockState(pos, original, Block.NOTIFY_ALL);
            stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "capital_orientation");
            for (int step = 1; step <= variants; step++) {
                Items.DEBUG_STICK.useOnBlock(usage);
                BlockState current = world.getBlockState(pos);
                context.assertTrue(capital.orientation(current).ordinal() == step % variants, "Debug cycle must follow 0,45,90,135 degrees");
                var saved = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, current).result().orElseThrow();
                context.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow() == current, "Orientation must survive serialization");
                context.assertTrue(current.getCollisionShape(world, pos) == current.getCollisionShape(world, pos), "Shapes must be cached");
                context.assertTrue(current.rotate(BlockRotation.CLOCKWISE_90).rotate(BlockRotation.COUNTERCLOCKWISE_90) == current, "Tool rotations must round trip");
                context.assertTrue(current.mirror(BlockMirror.LEFT_RIGHT).mirror(BlockMirror.LEFT_RIGHT) == current, "Tool mirrors must round trip");
                context.assertTrue(world.getBlockEntity(pos) == null, "Capital orientation must not require a block entity");
            }
            player.setSneaking(true);
            Items.DEBUG_STICK.useOnBlock(usage);
            context.assertTrue(capital.orientation(world.getBlockState(pos)).ordinal() == variants - 1, "Sneaking must reverse the cycle");
            player.setSneaking(false);
        }
        context.assertTrue(count == 324, "All six capital styles and 54 finishes must be tested");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void doubleCapitalOccupiesAndReleasesEightCells(TestContext context) {
        var world = context.getWorld();
        Block block = Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockState large = block.getDefaultState().with(CapitalBlock.SIZE, CapitalBlock.Size.DOUBLE);
        world.setBlockState(anchor, large, Block.NOTIFY_ALL);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos cell = anchor.add(x, y, z);
            context.assertTrue(x == 0 && y == 0 && z == 0
                    ? world.getBlockState(cell).isOf(block)
                    : world.getBlockState(cell).getBlock() instanceof com.oliver.daedalon.block.CapitalPartBlock,
                    "Double capital must occupy all eight cells");
        }
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            context.assertTrue(world.getBlockState(anchor.add(x, y, z)).isAir(), "Removed capital must clear its cells");
        world.setBlockState(anchor, block.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos occupied = anchor.add(1, 0, 0);
        world.setBlockState(occupied, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(anchor, large, Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE) == CapitalBlock.Size.STANDARD,
                "Resizing must fail when a neighbouring cell is occupied");
        context.assertTrue(world.getBlockState(occupied).isOf(Blocks.STONE),
                "Resizing must never replace a neighbouring block");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void capitalsAlignToEveryQuadrantOfLargeErydonColumn(TestContext context) {
        var world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos anchor = support.up();
        Block capitalBlock = Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.CREATIVE);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            world.setBlockState(support.add(x, 0, z), LargeColumnFixture.BLOCK.getDefaultState()
                    .with(LargeColumnFixture.X, x).with(LargeColumnFixture.Z, z)
                    .with(LargeColumnFixture.SECTION, LargeColumnFixture.Section.CAPITAL_UPPER), Block.NOTIFY_ALL);

        for (int clickedX = 0; clickedX < 2; clickedX++) for (int clickedZ = 0; clickedZ < 2; clickedZ++) {
            BlockPos clicked = support.add(clickedX, 0, clickedZ);
            player.setStackInHand(Hand.MAIN_HAND, capitalBlock.asItem().getDefaultStack());
            var hit = new BlockHitResult(new Vec3d(clicked.getX() + 0.5, clicked.getY() + 1, clicked.getZ() + 0.5),
                    Direction.UP, clicked, false);
            capitalBlock.asItem().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            BlockState placed = world.getBlockState(anchor);
            context.assertTrue(placed.isOf(capitalBlock) && placed.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE,
                    "Capital must be double and anchored at the column corner for every clicked quadrant");
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                context.assertTrue(world.getBlockState(anchor.add(x, y, z)).getBlock() instanceof CapitalPartBlock,
                        "Aligned double capital must reserve the complete two-by-two footprint");
            }
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.complete();
    }
}
