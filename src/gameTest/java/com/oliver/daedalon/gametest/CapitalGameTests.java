package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
            context.assertTrue(block.getStateManager().getStates().size() == variants, "Only the required capital states may exist");
            BlockState original = block.getDefaultState();
            context.assertTrue(capital.orientation(original) == CapitalOrientation.STRAIGHT, "Old appearance must be the default");
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
}
