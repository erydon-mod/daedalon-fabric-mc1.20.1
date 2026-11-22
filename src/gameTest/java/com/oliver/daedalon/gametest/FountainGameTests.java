package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorAssemblySelection;
import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.FountainWaterShape;
import com.oliver.daedalon.block.LouterionBasinBlock;
import com.oliver.daedalon.block.PlinthBlock;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.item.LouterionBasinItem;
import com.oliver.daedalon.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;

/** Runs only in build/gametest, with the production registries and mixins loaded. */
public final class FountainGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void louterionBucketWaterStaysInTheBasin(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        for (String material : new String[]{"aganite", "aganite_aged"}) {
            Identifier id = new Identifier("daedalon", material + "_louterion_basin");
            LouterionBasinBlock basin = (LouterionBasinBlock) Registries.BLOCK.get(id);
            BlockState dry = basin.getDefaultState();
            var savedWithoutProperties = new NbtCompound();
            savedWithoutProperties.putString("Name", id.toString());
            var blockLookup = Registries.BLOCK.getReadOnlyWrapper();
            context.assertTrue(dry.get(LouterionBasinBlock.SIZE) == TwoSizeDecorBlock.Size.SMALL
                            && !dry.get(LouterionBasinBlock.WATERLOGGED)
                            && NbtHelper.toBlockState(blockLookup, savedWithoutProperties) == dry,
                    "Old Louterion placements must load small and dry with the same block ID");

            var placement = new AutomaticItemPlacementContext(world, anchor, Direction.NORTH,
                    new ItemStack(basin.asItem()), Direction.UP);
            context.assertTrue(basin.getPlacementState(placement) == dry, "Dry placements must remain dry");
            world.setBlockState(anchor, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            BlockState wet = basin.getPlacementState(placement);
            context.assertTrue(wet.get(LouterionBasinBlock.WATERLOGGED), "Placement in source water must fill the basin");
            var savedWet = NbtHelper.fromBlockState(wet);
            savedWet.getCompound("Properties").remove("size");
            context.assertTrue(NbtHelper.toBlockState(blockLookup, savedWet) == wet,
                    "A saved waterlogged Louterion from before sizing must stay wet and small");
            world.setBlockState(anchor, dry, Block.NOTIFY_ALL);

            context.assertTrue(basin.tryDrainFluid(world, anchor, dry).isEmpty(), "A dry Louterion cannot supply a bucket");
            context.assertTrue(!basin.tryFillWithFluid(world, anchor, dry, Fluids.LAVA.getDefaultState()),
                    "A Louterion only accepts water");
            context.assertTrue(basin.tryFillWithFluid(world, anchor, dry, Fluids.WATER.getStill(false)),
                    "A water bucket must fill a dry Louterion");
            context.assertTrue(world.getBlockState(anchor) == wet && world.getFluidState(anchor).isEmpty(),
                    "Filling must set waterlogged without exposing a full-block fluid");
            context.assertTrue(wet.getCollisionShape(world, anchor) == dry.getCollisionShape(world, anchor),
                    "Water must leave the Louterion's existing collision intact");
            context.assertTrue(NbtHelper.toBlockState(blockLookup, NbtHelper.fromBlockState(wet)) == wet,
                    "A filled Louterion must survive a saved state round trip");
            context.assertTrue(!basin.tryFillWithFluid(world, anchor, wet, Fluids.WATER.getStill(false)),
                    "A filled Louterion cannot take a second water bucket");
            context.assertTrue(basin.tryDrainFluid(world, anchor, wet).isOf(Items.WATER_BUCKET)
                            && world.getBlockState(anchor) == dry,
                    "Draining must return one water bucket and leave the Louterion in place");
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void tallLouterionDoublesItsShapeAndReservesItsRim(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        BlockPos upper = anchor.up();
        LouterionBasinBlock basin = (LouterionBasinBlock) Registries.BLOCK.get(new Identifier("daedalon", "aganite_louterion_basin"));
        BlockState small = basin.getDefaultState().with(LouterionBasinBlock.WATERLOGGED, true);
        BlockState large = small.with(LouterionBasinBlock.SIZE, TwoSizeDecorBlock.Size.LARGE);
        var catalogueStacks = ModItems.catalogueStacks(basin.asItem());
        context.assertTrue(catalogueStacks.size() == 2, "The creative catalogue must offer both Louterion heights");
        ItemStack normalStack = catalogueStacks.get(0);
        ItemStack largeStack = catalogueStacks.get(1);
        context.assertTrue(normalStack.getItem() == basin.asItem() && largeStack.getItem() == basin.asItem()
                        && !LouterionBasinItem.isLarge(normalStack) && LouterionBasinItem.isLarge(largeStack)
                        && !normalStack.getName().equals(largeStack.getName()),
                "Catalogue variants must share the original item ID and show distinct sizes");
        var placement = new AutomaticItemPlacementContext(world, anchor, Direction.NORTH, largeStack, Direction.UP);
        var player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        ItemStack stick = Items.DEBUG_STICK.getDefaultStack();
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "size");
        player.setStackInHand(Hand.MAIN_HAND, stick);
        var lowerHit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.EAST, anchor, false);

        world.setBlockState(anchor, small, Block.NOTIFY_ALL);
        BlockPos corner = upper.east().south();
        world.setBlockState(corner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(basin.getPlacementState(placement) == null,
                "A tagged tall Louterion needs a clear upper cell");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, lowerHit));
        context.assertTrue(world.getBlockState(anchor) == small && world.getBlockState(corner).isOf(Blocks.STONE),
                "A blocked resize must leave the basin, water and the obstructing rim cell untouched");
        context.assertTrue(!basin.canPlaceAt(large, world, anchor.withY(world.getTopY() - 1)),
                "The tall basin cannot extend above the build limit");

        world.setBlockState(corner, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        BlockState placed = basin.getPlacementState(placement);
        context.assertTrue(placed != null && LouterionBasinBlock.isLarge(placed),
                "A tagged tall Louterion fits under clear headroom");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, lowerHit));
        context.assertTrue(world.getBlockState(anchor) == large && world.getFluidState(anchor).isEmpty(),
                "Resize must keep the water contained in the same block");
        ItemStack pickedLarge = basin.getPickStack(world, anchor, large);
        context.assertTrue(ItemStack.canCombine(pickedLarge, largeStack)
                        && ItemStack.canCombine(basin.getPickStack(world, anchor, small), normalStack),
                "Pick Block must copy the size using the normal dry catalogue item");
        Box smallBox = LouterionBasinBlock.fullCollisionShape(small).getBoundingBox();
        Box largeBox = LouterionBasinBlock.fullCollisionShape(large).getBoundingBox();
        context.assertTrue(Math.abs(largeBox.getXLength() - 2 * smallBox.getXLength()) < 1.0E-6
                        && Math.abs(largeBox.getYLength() - 2 * smallBox.getYLength()) < 1.0E-6
                        && Math.abs(largeBox.getZLength() - 2 * smallBox.getZLength()) < 1.0E-6
                        && DecorPreviewBounds.bounds(large).equals(largeBox),
                "Large must double all three dimensions and Axiom must see the full placed shape");
        Vec3d bowlCenter = Vec3d.ofCenter(anchor).add(0.0D, 1.36D, 0.0D);
        Box diagonalContact = Box.of(bowlCenter.add(0.64D, 0.0D, 0.64D), 0.04D, 0.04D, 0.04D);
        context.assertTrue(world.getBlockCollisions(null, diagonalContact).iterator().hasNext(),
                "Vanilla collision queries must reach the diagonal upper rim");
        context.assertTrue(!world.getBlockCollisions(null, Box.of(bowlCenter, 0.2D, 0.04D, 0.2D)).iterator().hasNext(),
                "Bowl center above its floor must remain open, even when filled");
        context.assertTrue(!world.getBlockCollisions(null,
                        Box.of(Vec3d.ofCenter(anchor).add(0.42D, 0.35D, 0.42D), 0.04D, 0.04D, 0.04D)).iterator().hasNext(),
                "Collision near the shaft must not fill the original square pedestal bounds");

        var sideRay = new RaycastContext(bowlCenter.add(2.5D, 0.0D, 0.0D), bowlCenter,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult upperHit = world.raycast(sideRay);
        BlockState rim = world.getBlockState(upperHit.getBlockPos());
        context.assertTrue(upperHit.getType() == HitResult.Type.BLOCK && upperHit.getBlockPos().equals(upper.east())
                        && anchor.equals(FountainBasinPartBlock.resolveAnchorPos(world, upperHit.getBlockPos(), rim)),
                "A side ray must hit the rim in its real cell and find the owner");
        context.assertTrue(DecorAssemblySelection.cells(world, corner).size() == 14,
                "Axiom must include the owner, four foot edges and nine upper cells");
        BlockPos occluder = upper.east(2);
        world.setBlockState(occluder, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(world.raycast(sideRay).getBlockPos().equals(occluder),
                "A nearer block must obscure the basin's upper outline");
        world.setBlockState(occluder, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        var downRay = new RaycastContext(bowlCenter.add(0.0D, 1.0D, 0.0D), bowlCenter.add(0.0D, -0.4D, 0.0D),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player);
        BlockHitResult waterHit = world.raycast(downRay);
        double waterLevel = FountainWaterShape.LOUTERION.surfaceY() * LouterionBasinBlock.LARGE_SCALE;
        context.assertTrue(waterHit.getBlockPos().equals(upper)
                        && Math.abs(waterHit.getPos().y - anchor.getY() - waterLevel) < 1.0E-5D,
                "Looking down into a wet basin must target the water surface");
        Waterloggable rimBlock = (Waterloggable) rim.getBlock();

        context.assertTrue(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), NbtHelper.fromBlockState(large)) == large,
                "Water and tall size must survive a save round trip together");
        context.assertTrue(rimBlock.tryDrainFluid(world, upperHit.getBlockPos(), rim).isOf(Items.WATER_BUCKET)
                        && LouterionBasinBlock.isLarge(world.getBlockState(anchor)),
                "Draining a tall basin from its rim must preserve its size");
        context.assertTrue(world.raycast(downRay).getPos().y < waterHit.getPos().y - 0.05D,
                "The dry outline must target the bowl floor below the previous water surface");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, upperHit));
        context.assertTrue(world.getBlockState(anchor) == basin.getDefaultState() && world.getBlockState(corner).isAir()
                        && world.raycast(sideRay).getType() == HitResult.Type.MISS,
                "Resizing from the rim must immediately clear Large part cells");
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void missingOldLouterionPartsRecoverWithoutReplacingBlocks(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        LouterionBasinBlock basin = (LouterionBasinBlock) Registries.BLOCK.get(new Identifier("daedalon", "aganite_louterion_basin"));
        BlockState largeWet = basin.getDefaultState().with(LouterionBasinBlock.SIZE, TwoSizeDecorBlock.Size.LARGE)
                .with(LouterionBasinBlock.WATERLOGGED, true);
        BlockState loaded = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), NbtHelper.fromBlockState(largeWet));
        world.setBlockState(anchor, loaded, Block.NOTIFY_ALL);
        for (BlockPos cell : DecorAssemblySelection.cells(world, anchor)) {
            if (!cell.equals(anchor)) world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.FORCE_STATE);
        }
        BlockPos existingCorner = anchor.up().east().south();
        world.setBlockState(existingCorner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor) == largeWet && world.getBlockState(anchor.up()).isAir(),
                "Fixture must represent a saved Large, filled basin that predates part cells");

        context.assertTrue(basin.tryDrainFluid(world, anchor, loaded).isOf(Items.WATER_BUCKET),
                "An obstructed old Large basin must still give up its water");
        BlockState dry = world.getBlockState(anchor);
        context.assertTrue(dry == largeWet.with(LouterionBasinBlock.WATERLOGGED, false)
                        && world.getBlockState(existingCorner).isOf(Blocks.STONE)
                        && DecorAssemblySelection.cells(world, anchor).size() == 13,
                "Repair must keep the saved size, leave the obstruction and reserve the remaining cells");
        BlockPos clearRim = anchor.up().east();
        world.setBlockState(clearRim, Blocks.AIR.getDefaultState(), Block.FORCE_STATE);
        ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(world, world.getWorldChunk(anchor));
        context.waitAndRun(2, () -> {
            BlockState clearRimState = world.getBlockState(clearRim);
            context.assertTrue(world.getBlockState(anchor) == dry
                            && world.getBlockState(existingCorner).isOf(Blocks.STONE)
                            && anchor.equals(FountainBasinPartBlock.resolveAnchorPos(world, clearRim, clearRimState)),
                    "The chunk-load repair must restore missing parts without changing contents or the obstruction");
            context.assertTrue(((Waterloggable) clearRimState.getBlock()).tryFillWithFluid(world, clearRim,
                            clearRimState, Fluids.WATER.getStill(false)) && world.getBlockState(anchor) == largeWet,
                    "The repaired rim must fill the original saved basin");
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(clearRim).isAir() && world.getBlockState(existingCorner).isOf(Blocks.STONE),
                    "Removing a migrated basin must clean up its parts and preserve nearby blocks");
            world.setBlockState(existingCorner, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            context.complete();
        });
    }

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
                context.assertTrue(state.hasBlockBreakParticles(),
                        "The visible fountain must retain its material-coloured breaking particles");
                for (BlockState partState : world.getBlockState(rim).getBlock().getStateManager().getStates()) {
                    context.assertTrue(!partState.hasBlockBreakParticles(),
                            "Invisible fountain cells must never emit missing-texture breaking particles");
                }
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

                // Waterlogged is last, so ordinary left-click wraps to the primary Basin Size control.
                Items.DEBUG_STICK.canMine(world.getBlockState(rim), world, rim, player);
                context.assertTrue("basin_size".equals(stick.getOrCreateNbt()
                        .getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)), "Waterlogged selection must wrap to Basin Size");
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
