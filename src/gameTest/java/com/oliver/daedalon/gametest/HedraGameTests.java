package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.HedraBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.DecorMaterial;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

public final class HedraGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void widthsFacingsAndDebugStickUseOnlyTheOwnerBlock(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        ItemStack stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        VoxelShape[][] shared = new VoxelShape[3][4];
        VoxelShape[][] sharedOutlines = new VoxelShape[3][4];
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (DecorMaterial material : DecorMaterial.values()) {
            for (boolean aged : new boolean[]{false, true}) {
                String id = material.id() + (aged ? "_aged" : "") + "_hedra";
                Block block = Registries.BLOCK.get(new Identifier("daedalon", id));
                context.assertTrue(block instanceof HedraBlock, "Missing hedra " + id);
                context.assertTrue(block.getDefaultState().get(HedraBlock.WIDTH) == 3, "Default must be 3m");
                context.assertTrue(block.getStateManager().getStates().size() == 12, "Only width and facing states are needed");
                for (int width = 2; width <= 4; width++) {
                    for (int f = 0; f < facings.length; f++) {
                        Direction facing = facings[f];
                        BlockState state = block.getDefaultState().with(HedraBlock.WIDTH, width).with(HedraBlock.FACING, facing);
                        world.setBlockState(anchor, state, Block.NOTIFY_ALL);
                        VoxelShape shape = state.getCollisionShape(world, anchor, ShapeContext.absent());
                        var bounds = shape.getBoundingBox();
                        boolean alongX = facing.getAxis() == Direction.Axis.Z;
                        double spanX = bounds.maxX - bounds.minX;
                        double spanZ = bounds.maxZ - bounds.minZ;
                        context.assertTrue(Math.abs((alongX ? spanX : spanZ) - width) < 1.0E-6, "Collision width mismatch");
                        context.assertTrue(Math.abs(bounds.maxY - 1.0) < 1.0E-6 && Math.abs(bounds.minY) < 1.0E-6, "Height/ground must stay fixed");
                        double expectedDepth = HedraBlock.DEPTH;
                        context.assertTrue(Math.abs((alongX ? spanZ : spanX) - expectedDepth) < 2.0E-6,
                                "Depth must stay fixed across widths");
                        context.assertTrue(world.getBlockEntity(anchor) == null, "Hedra must not allocate a controller");
                        if (shared[width-2][f] == null) shared[width-2][f] = shape;
                        context.assertTrue(shared[width-2][f] == shape, "Materials must share cached collision shapes");
                        VoxelShape outline = state.getOutlineShape(world, anchor, ShapeContext.absent());
                        var selection = outline.getBoundingBox();
                        double selectionX = selection.maxX - selection.minX;
                        double selectionZ = selection.maxZ - selection.minZ;
                        double selectionWidth = alongX ? selectionX : selectionZ;
                        double selectionDepth = alongX ? selectionZ : selectionX;
                        context.assertTrue(Math.abs(selectionWidth-width) < 1.0E-6
                                && Math.abs(selectionDepth-1.0) < 1.0E-6
                                && Math.abs(selection.maxY-selection.minY-1.0) < 1.0E-6,
                                "Selection must cover the complete width and a one-block height/depth");
                        context.assertTrue(outline != shape, "Selection must remain separate from collision");
                        if (sharedOutlines[width-2][f] == null) sharedOutlines[width-2][f] = outline;
                        context.assertTrue(sharedOutlines[width-2][f] == outline, "Materials must share cached selection shapes");
                    }
                }
            }
        }
        Block block = Registries.BLOCK.get(new Identifier("daedalon", "aganite_hedra"));
        world.setBlockState(anchor, block.getDefaultState(), Block.NOTIFY_ALL);
        Vec3d throughGapStart = Vec3d.of(anchor).add(0.5, 0.4, 2.0);
        Vec3d throughGapEnd = Vec3d.of(anchor).add(0.5, 0.4, -1.0);
        BlockState defaultState = block.getDefaultState();
        context.assertTrue(defaultState.getOutlineShape(world, anchor).raycast(throughGapStart, throughGapEnd, anchor) != null,
                "The space below the seat must be easy to select");
        context.assertTrue(defaultState.getCollisionShape(world, anchor).raycast(throughGapStart, throughGapEnd, anchor) == null,
                "The larger selection must not block the space beneath the seat");
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
        // A remembered control absent on this furniture must select width first.
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "offset");
        int expectedWidth = 3;
        for (int i = 0; i < 36; i++) {
            Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            expectedWidth = expectedWidth == 4 ? 2 : expectedWidth + 1;
            context.assertTrue(world.getBlockState(anchor).get(HedraBlock.WIDTH) == expectedWidth, "Debug width cycle stopped");
        }
        player.setSneaking(true);
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(world.getBlockState(anchor).get(HedraBlock.WIDTH) == 2, "Sneak must reverse width cycle");
        player.setSneaking(false);
        Items.DEBUG_STICK.canMine(world.getBlockState(anchor), world, anchor, player);
        context.assertTrue("facing".equals(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)), "Left click must select facing");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(world.getBlockState(anchor).get(HedraBlock.FACING) == Direction.WEST, "Facing must rotate independently");
        for (BlockPos pos : BlockPos.iterate(anchor.add(-2,0,-2),anchor.add(2,0,2))) {
            if (!pos.equals(anchor)) context.assertTrue(world.getBlockState(pos).isAir(), "Width edits must not create proxy blocks");
        }
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.complete();
    }
}
