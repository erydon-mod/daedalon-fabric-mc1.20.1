package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;

/** Runs only in build/gametest, with the production registries and mixins loaded. */
public final class FountainGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void repeatedControlsKeepEveryFountainEditable(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        // Complete the mock client's initial login teleport before sending use packets.
        player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(1));
        ItemStack stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        context.assertTrue(player.isCreativeLevelTwoOp(), "Test player must be allowed to use the debug stick");

        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                BlockState state = Registries.BLOCK.get(new Identifier("daedalon", "aganite_" + style.idSuffix()))
                        .getDefaultState().with(FountainBasinBlock.SIZE, size);
                world.setBlockState(anchor, state, Block.NOTIFY_ALL);
                FountainBasinBlockEntity basin = (FountainBasinBlockEntity) world.getBlockEntity(anchor);
                context.assertTrue(basin != null, "Placed basin must have its controller");
                BlockState plinth = Registries.BLOCK.get(new Identifier("daedalon", "aganite_astragalos_plinth"))
                        .getDefaultState().with(PlinthBlock.SIZE, PlinthBlock.Size.LARGE);
                context.assertTrue(basin.addPlinthUse(plinth) == FountainBasinBlockEntity.AdditionResult.PLINTH_ADDED,
                        "Plinth placement failed for " + style + " " + size);
                context.assertTrue(basin.replaceTierCount(FountainBowlModel.TierCount.THREE), "Three bowls must fit");
                FountainAssemblyLayout dry = basin.layout(state);
                BlockPos rim = anchor.east();
                context.assertTrue(world.getBlockState(rim).getBlock() instanceof FountainBasinPartBlock,
                        "Rim must have a local interaction cell");
                // Null deliberately fails if lighting tries to query a chunk or controller.
                for (BlockState lightingState : new BlockState[]{state, world.getBlockState(rim)}) {
                    context.assertTrue(lightingState.getOpacity(null, anchor) == 0
                                    && lightingState.isTransparent(null, anchor),
                            "Lighting queries must not resolve dynamic fountain shapes");
                }
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(rim), Direction.UP, rim, false);

                stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "waterlogged");
                long start = System.nanoTime();
                for (int toggle = 0; toggle < 64; toggle++) {
                    Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
                    BlockState current = world.getBlockState(anchor);
                    context.assertTrue(current.get(FountainBasinBlock.WATERLOGGED) == (toggle % 2 == 0),
                            "Water toggle stopped responding: " + style + " " + size + " toggle " + toggle);
                    context.assertTrue(world.getBlockEntity(anchor) == basin && basin.bowlCount() == 3,
                            "Water changes must preserve the controller and all tiers");
                    context.assertTrue(basin.layout(current).collisionPartShape(0, 0, 0)
                                    == dry.collisionPartShape(0, 0, 0),
                            "Water changes must reuse collision geometry");
                }
                System.out.printf("FountainGameTest %s %s: 64 debug water toggles %.3f ms%n",
                        style, size, (System.nanoTime() - start) / 1_000_000.0);

                // Ordinary left-click still selects the next control after repeated water edits.
                Items.DEBUG_STICK.canMine(world.getBlockState(rim), world, rim, player);
                context.assertTrue("facing".equals(stick.getOrCreateNbt()
                        .getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)), "Control selection must still work");
                for (String control : new String[]{"plinth_size", "tiers", "basin_size"}) {
                    stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, control);
                    Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
                }
                context.assertTrue(basin.bowlCount() == 0, "Tier control must cycle independently to none");
                context.assertTrue(basin.plinthState().get(PlinthBlock.SIZE) != PlinthBlock.Size.LARGE,
                        "Plinth control must remain editable");
                context.assertTrue(world.getBlockState(anchor).get(FountainBasinBlock.SIZE) != size,
                        "Basin size control must remain editable");

                Waterloggable proxy = (Waterloggable) world.getBlockState(rim).getBlock();
                context.assertTrue(proxy.tryFillWithFluid(world, rim, world.getBlockState(rim), Fluids.WATER.getDefaultState()),
                        "Bucket filling through a rim cell must reach the basin");
                context.assertTrue(proxy.tryDrainFluid(world, rim, world.getBlockState(rim)).isOf(Items.WATER_BUCKET),
                        "Bucket draining through a rim cell must reach the basin");
                context.assertTrue(world.getFluidState(rim).isEmpty(), "Proxy cells must not create outside water");

                // Loading saved component data must neither erase it nor rebuild the world.
                var saved = basin.createNbt();
                basin.readNbt(saved);
                context.assertTrue(saved.equals(basin.createNbt()), "Component data must survive an NBT round trip");
                stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "waterlogged");
                Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
                context.assertTrue(world.getBlockState(anchor).get(FountainBasinBlock.WATERLOGGED),
                        "The loaded fountain must remain editable");
                world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void rimHitPassesVanillaReachWithoutMovingHitToAnchor(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(1));
        ItemStack stick = Items.DEBUG_STICK.getDefaultStack();
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "waterlogged");
        player.setStackInHand(Hand.MAIN_HAND, stick);
        int sequence = 0;
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            BlockState state = Registries.BLOCK.get(new Identifier("daedalon", "aganite_" + style.idSuffix()))
                    .getDefaultState().with(FountainBasinBlock.SIZE, FountainBasinBlock.Size.LARGE);
            world.setBlockState(anchor, state, Block.NOTIFY_ALL);
            player.setPosition(anchor.getX() + 7.0, anchor.getY() - 1.1, anchor.getZ() + 0.5);
            Vec3d eye = player.getEyePos();
            BlockHitResult hit = world.raycast(new RaycastContext(eye,
                    new Vec3d(anchor.getX() + 0.5, eye.y, anchor.getZ() + 0.5),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            context.assertTrue(hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(anchor),
                    "Raycast must hit a real rim cell, not the distant anchor: " + style);
            context.assertTrue(eye.squaredDistanceTo(Vec3d.ofCenter(anchor)) > 6.0 * 6.0,
                    "This regression must exercise an anchor beyond server interaction reach");
            context.assertTrue(eye.squaredDistanceTo(hit.getPos()) < 5.0 * 5.0,
                    "The visible rim must still be inside creative-mode reach");
            player.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, sequence++));
            context.assertTrue(world.getBlockState(anchor).get(FountainBasinBlock.WATERLOGGED),
                    "Vanilla packet reach validation rejected a reachable rim: " + style);
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.complete();
    }
}
