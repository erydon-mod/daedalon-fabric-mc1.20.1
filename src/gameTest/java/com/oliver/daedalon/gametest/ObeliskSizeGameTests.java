package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorAssemblySelection;
import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.DecorShapeTransforms;
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
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Real shared-size, helper collision, blocked editing and placement regressions. */
public final class ObeliskSizeGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void allFinishesUseThreeSizesAndShareCompleteFiveBlockInteractionCells(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        int variants = 0;
        for (Block block : ModBlocks.blocks()) {
            if (!(block instanceof SizedDecorBlock)) continue;
            context.assertTrue(block.getDefaultState().get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.MEDIUM,
                    "Obelisks must default to the three-block Medium size");
            context.assertTrue(block.getStateManager().getStates().size() == 24, "Every finish needs three sizes, two offsets and four directions");
            var saved = NbtHelper.fromBlockState(block.getDefaultState());
            saved.getCompound("Properties").putString("size", "extra_large");
            var migrated = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), saved);
            context.assertTrue(migrated.get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.LARGE
                            && "large".equals(NbtHelper.fromBlockState(migrated).getCompound("Properties").getString("size")),
                    "Unpublished Extra Large saves must read and resave as Large without a fourth choice");
            for (var size : new SizedDecorBlock.Size[]{SizedDecorBlock.Size.SMALL, SizedDecorBlock.Size.MEDIUM}) {
                BlockState state = block.getDefaultState().with(SizedDecorBlock.SIZE, size);
                var collision = state.getCollisionShape(world, anchor);
                context.assertTrue(collision == SizedDecorBlock.fullShape(size), "Smaller sizes reuse their complete cached collision");
                context.assertTrue(Math.abs(collision.getMax(Direction.Axis.Y) - 3 * size.scale()) < 1e-6,
                        "Small and Medium must use their approved two- and three-block heights");
            }
            BlockState xl = block.getDefaultState().with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE);
            clear(world, anchor);
            world.setBlockState(anchor, xl, Block.NOTIFY_ALL);
            context.assertTrue(SizedDecorBlock.fullShape(SizedDecorBlock.Size.LARGE).getMax(Direction.Axis.Y) == 5,
                    "Large must be exactly five blocks high");
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
                        "Large outline rays must stay in the same local cell as physical collision");
                var bounds = collision.getBoundingBox();
                context.assertTrue(bounds.minX >= 0 && bounds.maxX <= 1 && bounds.minY == 0 && bounds.maxY == 1
                                && bounds.minZ >= 0 && bounds.maxZ <= 1,
                        "Each physical collision cell must stay within its own block");
                volume += bounds.getXLength() * bounds.getYLength() * bounds.getZLength();
                if (!pos.equals(anchor)) context.assertTrue(!state.hasBlockBreakParticles()
                                && state.getOpacity(null, pos) == 0 && state.isTransparent(null, pos),
                        "Invisible cells must be debris-free and safe for asynchronous lighting");
            }
            double width = SizedDecorBlock.fullShape(SizedDecorBlock.Size.LARGE).getBoundingBox().getXLength();
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
        BlockState medium = block.getDefaultState().with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.MEDIUM);
        world.setBlockState(anchor, medium, Block.NOTIFY_ALL);
        BlockPos obstruction = anchor.add(1, 4, 0);
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ServerPlayerEntity player = operator(context);
        player.setStackInHand(Hand.MAIN_HAND, Items.DEBUG_STICK.getDefaultStack());
        editSize(player, anchor);
        context.assertTrue(world.getBlockState(anchor) == medium && world.getBlockState(obstruction).isOf(Blocks.STONE),
                "Blocked enlargement must retain the original size and preserve neighbouring blocks");
        world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        editSize(player, anchor);
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.LARGE,
                "A clear enlargement must reach the five-block Large size");
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
        context.assertTrue(DecorAssemblySelection.cells(world, anchor).isEmpty(), "Small obelisks stay owner-only");
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) if (!offset.equals(BlockPos.ORIGIN)) {
            context.assertTrue(world.getBlockState(anchor.add(offset)).isAir(), "Shrinking must remove every owned helper");
        }
        BlockPos high = new BlockPos(anchor.getX(), world.getTopY() - 4, anchor.getZ());
        context.assertTrue(!SizedDecorBlock.canChangeSize(world, high, medium.with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE)),
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
                .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE);
        world.setBlockState(anchor, first, Block.NOTIFY_ALL);
        context.assertTrue(world.getFluidState(wet).getFluid() == Fluids.WATER, "A partial wet edge must preserve surrounding water");
        BlockState second = requireObelisk("glacium_aged_obeliskos_monument").getDefaultState()
                .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE);
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
    public void nbtLargePlacementValidatesBeforeWritingAndSupportsBlocksOnItsCrown(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        clear(world, anchor);
        world.setBlockState(anchor.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ServerPlayerEntity player = operator(context);
        ItemStack stack = requireObelisk("aganite_obeliskos_monument").asItem().getDefaultStack();
        stack.getOrCreateSubNbt("BlockStateTag").putString("size", "large");
        player.setStackInHand(Hand.MAIN_HAND, stack);
        BlockPos obstruction = anchor.add(1, 4, 0);
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ItemUsageContext use = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(
                Vec3d.ofCenter(anchor.down()).add(0, .5, 0), Direction.UP, anchor.down(), false));
        context.assertTrue(!stack.getItem().useOnBlock(use).isAccepted() && world.getBlockState(anchor).isAir(),
                "Blocked NBT placement must fail before writing an owner");
        stack.getOrCreateSubNbt("BlockStateTag").putString("size", "extra_large");
        context.assertTrue(!stack.getItem().useOnBlock(use).isAccepted() && world.getBlockState(anchor).isAir(),
                "The unpublished size alias must obey the same blocked-placement check");
        world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(stack.getItem().useOnBlock(use).isAccepted(), "A clear NBT-selected five-block obelisk must place");
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.LARGE
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void alcoveOffsetMatchesPlinthDistanceAndLargeCellsFollowEveryDirection(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        SizedDecorBlock block = requireObelisk("aganite_obeliskos_monument");
        for (var size : SizedDecorBlock.Size.values()) for (Direction facing : Direction.Type.HORIZONTAL) {
            BlockState state = block.getDefaultState().with(SizedDecorBlock.SIZE, size)
                    .with(SizedDecorBlock.OFFSET, true).with(SizedDecorBlock.FACING, facing);
            var centred = SizedDecorBlock.fullShape(size).getBoundingBox();
            var moved = SizedDecorBlock.fullShape(state).getBoundingBox();
            double distance = DecorShapeTransforms.OFFSET_DISTANCE;
            context.assertTrue(Math.abs(moved.minX - centred.minX - facing.getOffsetX() * distance) < 1e-9
                            && Math.abs(moved.minZ - centred.minZ - facing.getOffsetZ() * distance) < 1e-9
                            && moved.minY == centred.minY && moved.maxY == centred.maxY,
                    "Every size must use the same horizontal alcove offset as statues and plinths without changing height");
            context.assertTrue(DecorPreviewBounds.bounds(state).equals(moved), "Axiom's preview must include the full displaced monument");
            context.assertTrue(state.rotate(BlockRotation.CLOCKWISE_90).get(SizedDecorBlock.FACING) == facing.rotateYClockwise()
                            && state.mirror(BlockMirror.LEFT_RIGHT).get(SizedDecorBlock.FACING)
                            == (facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing),
                    "Rotation and mirroring must carry the offset direction");
            context.assertTrue(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), NbtHelper.fromBlockState(state)) == state,
                    "Saved offset/size/facing must round-trip exactly");
            clear(world, anchor);
            world.setBlockState(anchor, state, Block.NOTIFY_ALL);
            if (size != SizedDecorBlock.Size.LARGE) continue;
            context.assertTrue(DecorAssemblySelection.cells(world, anchor).size() == 30,
                    "Large alcove placement must reserve only its thirty occupied cells");
            double volume = 0;
            for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
                BlockPos pos = anchor.add(offset);
                BlockState found = world.getBlockState(pos);
                var collision = ObeliskPartsGeometry.collision(state, offset.getX(), offset.getY(), offset.getZ());
                if (collision.isEmpty()) {
                    context.assertTrue(found.isAir(), "Offset must leave unoccupied alcove cells free");
                    continue;
                }
                context.assertTrue(pos.equals(anchor) || ObeliskPartBlock.isOwnedBy(found, pos, anchor),
                        "Every displaced collision cell must belong to the one owner");
                context.assertTrue(found.getCollisionShape(world, pos) == collision,
                        "Owner and helpers must reuse the correct directional collision cache");
                var box = collision.getBoundingBox();
                context.assertTrue(box.minX >= 0 && box.maxX <= 1 && box.minZ >= 0 && box.maxZ <= 1,
                        "Displaced collision must stay local to its actual helper cell");
                volume += box.getXLength() * box.getYLength() * box.getZLength();
            }
            context.assertTrue(Math.abs(volume - moved.getXLength() * moved.getZLength() * 5) < 1e-9,
                    "The shifted helper cells must cover the whole mesh footprint exactly once");
        }
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void blockedOffsetDirectionPreservesNeighboursAndSuccessfulEditsRestoreOldWetCells(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        clear(world, anchor);
        BlockPos wet = anchor.add(-1, 0, -1);
        world.setBlockState(wet, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        BlockState north = requireObelisk("aganite_obeliskos_monument").getDefaultState()
                .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE).with(SizedDecorBlock.OFFSET, true)
                .with(SizedDecorBlock.FACING, Direction.NORTH);
        world.setBlockState(anchor, north, Block.NOTIFY_ALL);
        ServerPlayerEntity player = operator(context);
        player.setStackInHand(Hand.MAIN_HAND, Items.DEBUG_STICK.getDefaultStack());
        BlockPos obstruction = anchor.add(0, 4, 1);
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        editControl(player, anchor, "facing");
        context.assertTrue(world.getBlockState(anchor) == north && world.getBlockState(obstruction).isOf(Blocks.STONE),
                "A blocked direction edit must preserve the old state and all neighbouring construction");
        world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        editControl(player, anchor, "facing");
        BlockState east = world.getBlockState(anchor);
        context.assertTrue(east.isOf(north.getBlock()) && east.get(SizedDecorBlock.FACING) == Direction.EAST
                        && east.get(SizedDecorBlock.OFFSET) && DecorAssemblySelection.cells(world, anchor).size() == 30,
                "A clear direction edit must move the complete thirty-cell assembly without breaking its owner");
        context.assertTrue(world.getBlockState(wet).isOf(Blocks.WATER), "Vacated wet edge cells must restore their water");
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) {
            if (ObeliskPartsGeometry.contains(east, offset)) continue;
            context.assertTrue(!(world.getBlockState(anchor.add(offset)).getBlock() instanceof ObeliskPartBlock),
                    "Moving an offset must not leave old helper cells behind");
        }
        editControl(player, anchor, "offset");
        context.assertTrue(!world.getBlockState(anchor).get(SizedDecorBlock.OFFSET)
                        && DecorAssemblySelection.cells(world, anchor).size() == 45,
                "Turning offset off must rebuild the complete centred footprint");
        editSize(player, anchor);
        context.assertTrue(world.getBlockState(anchor).get(SizedDecorBlock.SIZE) == SizedDecorBlock.Size.SMALL,
                "Shrinking must retain the selected size and clear every no-longer-needed helper");
        for (BlockPos offset : ObeliskPartsGeometry.offsets()) context.assertTrue(
                !(world.getBlockState(anchor.add(offset)).getBlock() instanceof ObeliskPartBlock),
                "Shrinking must remove all owned helper cells");
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void centringOffsetClearsWaterOnlyFromRetainedCellsThatBecomeSolid(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = operator(context);
        player.setStackInHand(Hand.MAIN_HAND, Items.DEBUG_STICK.getDefaultStack());
        for (Direction facing : Direction.Type.HORIZONTAL) {
            clear(world, anchor);
            BlockPos central = anchor.up(2);
            BlockPos edge = central.offset(facing);
            world.setBlockState(central, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(edge, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            BlockState offset = requireObelisk("aganite_obeliskos_monument").getDefaultState()
                    .with(SizedDecorBlock.SIZE, SizedDecorBlock.Size.LARGE).with(SizedDecorBlock.OFFSET, true)
                    .with(SizedDecorBlock.FACING, facing);
            world.setBlockState(anchor, offset, Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(central).get(ObeliskPartBlock.WATERLOGGED)
                            && world.getBlockState(edge).get(ObeliskPartBlock.WATERLOGGED),
                    "The offset footprint must preserve water in both partial helper cells");
            editControl(player, anchor, "offset");
            context.assertTrue(world.getBlockState(anchor) == offset.with(SizedDecorBlock.OFFSET, false),
                    "Water normalization must not break or alter the centred monument");
            context.assertTrue(ObeliskPartBlock.isOwnedBy(world.getBlockState(central), central, anchor)
                            && !world.getBlockState(central).get(ObeliskPartBlock.WATERLOGGED)
                            && world.getFluidState(central).isEmpty(),
                    "A retained helper that becomes completely solid must lose its water");
            context.assertTrue(ObeliskPartBlock.isOwnedBy(world.getBlockState(edge), edge, anchor)
                            && world.getBlockState(edge).get(ObeliskPartBlock.WATERLOGGED),
                    "A retained partial edge must keep its surrounding water");
            world.breakBlock(anchor, false);
            context.assertTrue(world.getBlockState(central).isAir() && world.getBlockState(edge).isOf(Blocks.WATER),
                    "Removal must restore retained edge water without creating water in the solid column");
        }
        clear(world, anchor);
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
        editControl(player, pos, "size");
    }
    private static void editControl(ServerPlayerEntity player, BlockPos pos, String name) {
        player.getMainHandStack().getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, name);
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
