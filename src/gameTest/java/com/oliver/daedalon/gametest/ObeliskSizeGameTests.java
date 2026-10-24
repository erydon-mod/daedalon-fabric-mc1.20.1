package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorAssemblySelection;
import com.oliver.daedalon.block.ObeliskPartBlock;
import com.oliver.daedalon.block.ObeliskPartsGeometry;
import com.oliver.daedalon.block.SizedDecorBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Real shared-size, helper collision, blocked editing and placement regressions. */
public final class ObeliskSizeGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void allFinishesKeepExistingSizesAndShareCompleteFiveBlockInteractionCells(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        int variants = 0;
        for (Block block : ModBlocks.blocks()) {
            if (!(block instanceof SizedDecorBlock)) continue;
            context.assertTrue(block.getDefaultState().get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.MEDIUM,
                    "Existing obelisks must still default to two blocks");
            context.assertTrue(block.getStateManager().getStates().size() == 4, "Every finish needs all four sizes");
            for (var size : new SizedDecorBlock.Size[]{SizedDecorBlock.Size.SMALL, SizedDecorBlock.Size.MEDIUM, SizedDecorBlock.Size.LARGE}) {
                BlockState state = block.getDefaultState().with(SizedDecorBlock.SIZE, size);
                var collision = state.getCollisionShape(world, anchor);
                context.assertTrue(collision == SizedDecorBlock.fullShape(size), "Legacy sizes retain their exact cached collision");
                context.assertTrue(Math.abs(collision.getMax(Direction.Axis.Y) - 3 * size.scale()) < 1e-6,
                        "Existing obelisk heights changed");
            }
            BlockState xl = block.getDefaultState().with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.EXTRA_LARGE);
            clear(world, anchor);
            world.setBlockState(anchor, xl, Block.NOTIFY_ALL);
            context.assertTrue(SizedDecorBlock.fullShape(SizedDecorBlock.Size.EXTRA_LARGE).getMax(Direction.Axis.Y) == 5,
                    "Extra Large must be exactly five blocks high");
            context.assertTrue(DecorAssemblySelection.cells(world, anchor).size() == 45,
                    "Axiom assembly selection must include the owner and all44 real interaction cells");
            double volume = 0;
            for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
                BlockPos pos = anchor.add(offset);
                BlockState state = world.getBlockState(pos);
                context.assertTrue(pos.equals(anchor) || ObeliskPartBlock.isOwnedBy(state, pos, anchor),
                        "The five-block model must own each occupied cell");
                var collision = state.getCollisionShape(world, pos, ShapeContext.absent());
                context.assertTrue(collision == ObeliskPartsGeometry.collision(offset.getX(), offset.getY(), offset.getZ()),
                        "All materials must reuse the same cached local collision");
                context.assertTrue(state.getOutlineShape(world, pos) == collision,
                        "XL outline rays must stay in the same local cell as physical collision");
                var bounds = collision.getBoundingBox();
                context.assertTrue(bounds.minX >= 0 && bounds.maxX <= 1 && bounds.minY == 0 && bounds.maxY == 1
                                && bounds.minZ >= 0 && bounds.maxZ <= 1,
                        "Each physical collision cell must stay within its own block");
                volume += bounds.getXLength() * bounds.getYLength() * bounds.getZLength();
                if (!pos.equals(anchor)) context.assertTrue(!state.hasBlockBreakParticles()
                                && state.getOpacity(null, pos) == 0 && state.isTransparent(null, pos),
                        "Invisible cells must be debris-free and safe for asynchronous lighting");
            }
            double width = SizedDecorBlock.fullShape(SizedDecorBlock.Size.EXTRA_LARGE).getBoundingBox().getXLength();
            context.assertTrue(Math.abs(volume - width * width * 5) < 1e-9,
                    "The 45 cells must cover the complete scaled profile without duplicated collision volume");
            variants++;
        }
        context.assertTrue(variants == 55, "Cover all 27 stone materials, aged forms and Bronze");
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void blockedGrowthAndHelperDebuggingPreserveNeighbouringBuilds(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        clear(world, anchor);
        var block = requireObelisk("aganite_obeliskos_monument");
        BlockState large = block.getDefaultState().with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE);
        world.setBlockState(anchor, large, Block.NOTIFY_ALL);
        BlockPos obstruction = anchor.add(1, 4, 0);
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ServerPlayerEntity player = operator(context);
        player.setStackInHand(Hand.MAIN_HAND, Items.DEBUG_STICK.getDefaultStack());
        editSize(player, anchor);
        context.assertTrue(world.getBlockState(anchor) == large && world.getBlockState(obstruction).isOf(Blocks.STONE),
                "Blocked enlargement must retain the original size and preserve neighbouring blocks");
        world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        editSize(player, anchor);
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.EXTRA_LARGE,
                "A clear enlargement must reach Extra Large");
        BlockPos rim = anchor.add(1, 4, 0);
        context.assertTrue(DecorAssemblySelection.cells(world, rim).size() == 45,
                "Selection from an upper overhanging cell must resolve the complete assembly");
        for (int[] direction : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            Vec3d centre = Vec3d.ofBottomCenter(anchor).add(0, 4.5, 0);
            Vec3d start = centre.add(direction[0] * 3, 0, direction[1] * 3);
            Vec3d end = centre.add(direction[0] * -.5, 0, direction[1] * -.5);
            var hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, player));
            context.assertTrue(world.getBlockState(hit.getBlockPos()).getBlock() instanceof ObeliskPartBlock,
                    "Every side and diagonal ray above the owner must reach the actual five-block model");
            Vec3d point = hit.getPos();
            BlockPos cell = hit.getBlockPos();
            context.assertTrue(point.x >= cell.getX() - 1e-6 && point.x <= cell.getX() + 1 + 1e-6
                            && point.y >= cell.getY() - 1e-6 && point.y <= cell.getY() + 1 + 1e-6
                            && point.z >= cell.getZ() - 1e-6 && point.z <= cell.getZ() + 1 + 1e-6,
                    "The reported hit must belong to its real packet/collision cell");
        }
        editSize(player, rim);
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.SMALL,
                "Debugging an upper cell must edit its owner and wrap to Small");
        context.assertTrue(DecorAssemblySelection.cells(world, anchor).isEmpty(), "Legacy small obelisks stay owner-only");
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) if (!offset.equals(BlockPos.ORIGIN)) {
            context.assertTrue(world.getBlockState(anchor.add(offset)).isAir(), "Shrinking must remove every owned helper");
        }
        BlockPos high = new BlockPos(anchor.getX(), world.getTopY() - 4, anchor.getZ());
        context.assertTrue(!SizedDecorBlock.canChangeSize(world, high, large.with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.EXTRA_LARGE)),
                "The complete five-block model must fit inside world height");
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void materialSwapsKeepTheAssemblyAndBreakingAWetRimRestoresWater(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        clear(world, anchor);
        BlockPos wet = anchor.add(1, 0, 0);
        world.setBlockState(wet, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        BlockState first = requireObelisk("aganite_obeliskos_monument").getDefaultState()
                .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.EXTRA_LARGE);
        world.setBlockState(anchor, first, Block.NOTIFY_ALL);
        context.assertTrue(world.getFluidState(wet).getFluid() == Fluids.WATER, "A partial wet edge must preserve surrounding water");
        BlockState second = requireObelisk("glacium_aged_obeliskos_monument").getDefaultState()
                .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.EXTRA_LARGE);
        world.setBlockState(anchor, second, Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor) == second && DecorAssemblySelection.cells(world, anchor).size() == 45,
                "A material/finish swap must retain its new owner and all 45 cells");
        context.assertTrue(world.getFluidState(wet).getFluid() == Fluids.WATER, "A material swap must not erase edge water");
        context.assertTrue(world.getBlockState(wet).getBlock().getPickStack(world, wet, world.getBlockState(wet)).isOf(second.getBlock().asItem()),
                "Picking an upper/rim cell must return the correct owner's material");
        world.breakBlock(wet, false);
        context.assertTrue(world.getBlockState(anchor).isAir(), "Breaking a helper must remove its one owner");
        context.assertTrue(world.getBlockState(wet).isOf(Blocks.WATER), "Removing the assembly must restore a wet edge's source");
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) context.assertTrue(
                !(world.getBlockState(anchor.add(offset)).getBlock() instanceof ObeliskPartBlock),
                "Breaking must not leave invisible parts behind");
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void nbtExtraLargePlacementValidatesBeforeWritingAndSupportsBlocksOnItsCrown(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        clear(world, anchor);
        world.setBlockState(anchor.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ServerPlayerEntity player = operator(context);
        ItemStack stack = requireObelisk("aganite_obeliskos_monument").asItem().getDefaultStack();
        stack.getOrCreateSubNbt("BlockStateTag").putString("size", "extra_large");
        player.setStackInHand(Hand.MAIN_HAND, stack);
        BlockPos obstruction = anchor.add(1, 4, 0);
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ItemUsageContext use = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(
                Vec3d.ofCenter(anchor.down()).add(0, .5, 0), Direction.UP, anchor.down(), false));
        context.assertTrue(!stack.getItem().useOnBlock(use).isAccepted() && world.getBlockState(anchor).isAir(),
                "Blocked NBT placement must fail before writing an owner");
        world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(stack.getItem().useOnBlock(use).isAccepted(), "A clear NBT-selected five-block obelisk must place");
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.EXTRA_LARGE
                        && DecorAssemblySelection.cells(world, anchor).size() == 45,
                "NBT placement must build the complete selected-size assembly");
        player.setStackInHand(Hand.MAIN_HAND, Items.STONE.getDefaultStack());
        BlockPos crown = anchor.up(4);
        ItemUsageContext top = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(
                Vec3d.ofBottomCenter(anchor.up(5)), Direction.UP, crown, false));
        context.assertTrue(Items.STONE.useOnBlock(top).isAccepted() && world.getBlockState(anchor.up(5)).isOf(Blocks.STONE),
                "Normal block items must place directly above the complete five-block crown");
        world.setBlockState(anchor.up(5), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        clear(world, anchor);
        world.setBlockState(anchor.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.complete();
    }

    private static SizedDecorBlock requireObelisk(String path) {
        return (SizedDecorBlock) ModBlocks.blocksById().get(new net.minecraft.util.Identifier("daedalon", path));
    }
    private static ServerPlayerEntity operator(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        context.getWorld().getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        return player;
    }
    private static void editSize(ServerPlayerEntity player, BlockPos pos) {
        player.getMainHandStack().getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "size");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)));
    }
    private static void clear(ServerWorld world, BlockPos anchor) {
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            world.setBlockState(anchor.add(offset), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        }
    }
}
