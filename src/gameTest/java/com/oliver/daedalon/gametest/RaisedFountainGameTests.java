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
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real server state, helper-cell, debug-stick and fluid regressions for raised bases. */
public final class RaisedFountainGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final int MAX_Y = 6;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void everyBasinKeepsItsWidthAndMovesItsBowlAndContainedWaterUpOneBlock(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        for (var style : FountainBasinBlock.Style.values()) {
            for (var size : FountainBasinBlock.Size.values()) {
                clear(world, anchor);
                String label = style + " " + size;
                BlockState original = state(style, size);
                context.assertTrue(!original.get(FountainBasinBlock.RAISED_BASE), "Existing/default basins stay unraised: " + label);
                context.assertTrue(FountainBasinBlock.baseRise(original) == 0, "Default rise must be zero: " + label);
                BlockState normal = original.with(FountainBasinBlock.WATERLOGGED, true);
                world.setBlockState(anchor, normal, Block.NOTIFY_ALL);
                Map<BlockPos, VoxelShape> collision = collisionCells(world, anchor);
                Box originalBounds = collisionBounds(collision, 0);
                Box probe = basinWaterProbe(anchor, style, size, 0);
                double surface = globalContainedSurface(context, world, probe, label);

                BlockState raised = normal.with(FountainBasinBlock.RAISED_BASE, true);
                context.assertTrue(((FountainBasinBlock) raised.getBlock()).canChangeSize(world, anchor, raised),
                        "Clear raised basin must fit: " + label);
                world.setBlockState(anchor, raised, Block.NOTIFY_ALL);
                Map<BlockPos, VoxelShape> raisedCollision = collisionCells(world, anchor);
                Box raisedBounds = collisionBounds(raisedCollision, 0);
                Box raisedUpperBounds = collisionBounds(raisedCollision, 1);
                for (var cell : collision.entrySet()) {
                    BlockPos offset = cell.getKey();
                    if (offset.getY() == MAX_Y) {
                        context.assertTrue(cell.getValue().isEmpty(), "The shallow original basin must leave room to rise: " + label);
                        continue;
                    }
                    context.assertTrue(!VoxelShapes.matchesAnywhere(cell.getValue(), raisedCollision.get(offset.up()),
                                    BooleanBiFunction.NOT_SAME),
                            "Actual basin collision must move exactly +1 in every helper cell: " + label + " " + offset);
                }
                for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                    near(context, originalBounds.getMin(axis), raisedUpperBounds.getMin(axis), label + " unchanged upper min" + axis);
                    near(context, originalBounds.getMax(axis), raisedUpperBounds.getMax(axis), label + " unchanged upper max" + axis);
                    context.assertTrue(raisedBounds.getMin(axis) >= 0.5 - size.modelScale() / 2 - EPSILON
                                    && raisedBounds.getMax(axis) <= 0.5 + size.modelScale() / 2 + EPSILON,
                            "The authored foundation must stay inside the existing visual footprint: " + label + " " + axis);
                }
                near(context, originalBounds.minY + 1, raisedUpperBounds.minY, label + " upper minimum height");
                near(context, originalBounds.maxY + 1, raisedUpperBounds.maxY, label + " upper maximum height");
                context.assertTrue(VoxelShapes.matchesAnywhere(raisedCollision.get(BlockPos.ORIGIN),
                                VoxelShapes.cuboid(0.45, 0.45, 0.45, 0.55, 0.55, 0.55), BooleanBiFunction.AND),
                        "The raised foundation must fill the former space beneath its centre: " + label);
                near(context, surface + 1, globalContainedSurface(context, world, probe.offset(0, 1, 0), label),
                        label + " contained water rise");
                BlockPos oldCell = BlockPos.ofFloored(probe.getCenter().x, probe.minY, probe.getCenter().z);
                context.assertTrue(FountainBasinBlock.containedWaterSurfaceHeight(world, oldCell,
                                world.getBlockState(oldCell), probe) == 0,
                        "The former unraised bowl level must no longer contain bowl water: " + label);
            }
        }
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void assembledControlsRejectObstructionsAndPreserveTiersAndSavedState(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        var stick = Items.DEBUG_STICK.getDefaultStack();
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "raised_base");
        player.setStackInHand(Hand.MAIN_HAND, stick);
        for (var style : FountainBasinBlock.Style.values()) {
            for (var size : FountainBasinBlock.Size.values()) {
                clear(world, anchor);
                String label = style + " " + size;
                BlockState normal = state(style, size).with(FountainBasinBlock.WATERLOGGED, true);
                world.setBlockState(anchor, normal, Block.NOTIFY_ALL);
                var basin = (FountainBasinBlockEntity) world.getBlockEntity(anchor);
                context.assertTrue(basin != null, "Basin controller missing: " + label);
                BlockState plinth = Registries.BLOCK.get(new Identifier("daedalon", "aganite_astragalos_plinth"))
                        .getDefaultState().with(PlinthBlock.SIZE, PlinthBlock.Size.LARGE)
                        .with(PlinthBlock.FACING, Direction.EAST);
                context.assertTrue(basin.addPlinthUse(plinth) == FountainBasinBlockEntity.AdditionResult.PLINTH_ADDED,
                        "Large plinth must attach: " + label);
                context.assertTrue(basin.replaceTierCount(FountainBowlModel.TierCount.THREE), "All three bowls must fit: " + label);
                FountainAssemblyLayout original = basin.layout(normal);
                BlockState raised = normal.with(FountainBasinBlock.RAISED_BASE, true);
                FountainAssemblyLayout candidate = basin.layout(raised);
                BlockPos obstruction = newCell(anchor, original, candidate);
                context.assertTrue(obstruction != null, "Regression needs a newly occupied raised cell: " + label);
                world.setBlockState(obstruction, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                var savedComponents = basin.createNbt();
                Map<BlockPos, BlockState> before = snapshot(world, anchor);
                useRaisedControl(player, anchor.east());
                context.assertTrue(world.getBlockState(anchor) == normal, "Obstructed toggle changed basin state: " + label);
                context.assertTrue(world.getBlockEntity(anchor) == basin && basin.createNbt().equals(savedComponents),
                        "Obstructed toggle changed paid components/controller: " + label);
                context.assertTrue(before.equals(snapshot(world, anchor)), "Obstructed toggle changed helper cells/build: " + label);

                world.setBlockState(obstruction, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                useRaisedControl(player, anchor.east());
                context.assertTrue(world.getBlockState(anchor) == raised, "Unobstructed debug-stick toggle failed: " + label);
                context.assertTrue(world.getBlockEntity(anchor) == basin && basin.createNbt().equals(savedComponents),
                        "Raising must preserve the controller and all paid components: " + label);
                FountainAssemblyLayout actual = basin.layout(raised);
                near(context, original.plinth().baseY() + 1, actual.plinth().baseY(), label + " plinth base");
                near(context, original.plinth().connectionY() + 1, actual.plinth().connectionY(), label + " plinth connection");
                context.assertTrue(original.plinth().state() == actual.plinth().state(), "Plinth size/facing changed: " + label);
                context.assertTrue(actual.bowls().size() == 3, "Bowl count changed: " + label);
                for (int i = 0; i < 3; i++) {
                    var oldBowl = original.bowls().get(i);
                    var newBowl = actual.bowls().get(i);
                    context.assertTrue(oldBowl.style() == newBowl.style() && oldBowl.size() == newBowl.size(),
                            "Bowl style/size changed: " + label + " #" + i);
                    near(context, oldBowl.baseY() + 1, newBowl.baseY(), label + " bowl base #" + i);
                    near(context, oldBowl.connectionY() + 1, newBowl.connectionY(), label + " bowl connection #" + i);
                }
                near(context, original.maximumY() + 1, actual.maximumY(), label + " whole assembly height");

                BlockState loadedState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), NbtHelper.fromBlockState(raised));
                var loaded = new FountainBasinBlockEntity(anchor, loadedState);
                loaded.readNbt(savedComponents);
                context.assertTrue(loadedState == raised && loaded.createNbt().equals(savedComponents),
                        "Raised/size/water and components must survive state+NBT serialization: " + label);
                near(context, actual.maximumY(), loaded.layout(loadedState).maximumY(), label + " restored assembly height");
                useRaisedControl(player, anchor);
                context.assertTrue(world.getBlockState(anchor) == normal && basin.createNbt().equals(savedComponents),
                        "Returning to the existing height must preserve all state: " + label);
            }
        }
        clear(world, anchor);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void raisedPartialBaseCellsUseNativeWaterAndRestoreItWhenBroken(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 2, 3));
        for (var style : FountainBasinBlock.Style.values()) {
            for (var size : FountainBasinBlock.Size.values()) {
                clear(world, anchor);
                String label = style + " " + size;
                BlockState dry = state(style, size).with(FountainBasinBlock.RAISED_BASE, true);
                world.setBlockState(anchor, dry, Block.NOTIFY_ALL);
                var partial = new ArrayList<BlockPos>();
                var solid = new ArrayList<BlockPos>();
                var upper = new ArrayList<BlockPos>();
                for (var entry : snapshot(world, anchor).entrySet()) {
                    if (!(entry.getValue().getBlock() instanceof FountainBasinPartBlock)) continue;
                    BlockPos pos = entry.getKey();
                    context.assertTrue(world.getFluidState(pos).isEmpty(), "Dry raised cells must be dry: " + label);
                    if (pos.getY() != anchor.getY()) {
                        upper.add(pos);
                    } else {
                        VoxelShape stone = entry.getValue().getCollisionShape(world, pos);
                        if (!stone.isEmpty() && VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), stone, BooleanBiFunction.ONLY_FIRST)) {
                            partial.add(pos);
                        } else {
                            solid.add(pos);
                        }
                    }
                }
                context.assertTrue(!partial.isEmpty() && !upper.isEmpty(), "Need partial lower and contained-only upper cells: " + label);
                BlockPos rim = partial.get(0);
                Waterloggable proxy = (Waterloggable) world.getBlockState(rim).getBlock();
                context.assertTrue(proxy.tryFillWithFluid(world, rim, world.getBlockState(rim), Fluids.WATER.getStill(false)),
                        "Filling through the base must fill the one controlling basin: " + label);
                for (BlockPos pos : partial) {
                    context.assertTrue(world.getFluidState(pos).isIn(FluidTags.WATER) && world.getFluidState(pos).isStill(),
                            "A wet partial foundation cell must expose ordinary still water: " + label + " " + pos);
                }
                // Filling can add a cell containing only the high bowl's water
                // surface, so include helpers that were absent from the dry view.
                for (var entry : snapshot(world, anchor).entrySet()) {
                    if (entry.getKey().getY() > anchor.getY()
                            && FountainBasinPartBlock.isOwnedBy(entry.getValue(), entry.getKey(), anchor)
                            && !upper.contains(entry.getKey())) upper.add(entry.getKey());
                }
                context.assertTrue(world.getFluidState(anchor).isEmpty(), "The solid controller cell must remain dry: " + label);
                for (BlockPos pos : solid) context.assertTrue(world.getFluidState(pos).isEmpty(), "Solid base cell became water: " + label);
                for (BlockPos pos : upper) context.assertTrue(world.getFluidState(pos).isEmpty(), "Contained upper water must not become a fluid block: " + label);
                globalContainedSurface(context, world, basinWaterProbe(anchor, style, size, 1), label);
                context.assertTrue(proxy.tryDrainFluid(world, rim, world.getBlockState(rim)).isOf(Items.WATER_BUCKET),
                        "One bucket must drain both the basin and its lower cells: " + label);
                for (BlockPos pos : partial) context.assertTrue(world.getFluidState(pos).isEmpty(), "Drained base cell retained water: " + label);
                context.assertTrue(proxy.tryFillWithFluid(world, rim, world.getBlockState(rim), Fluids.WATER.getStill(false)),
                        "The drained raised base must fill again: " + label);
                world.breakBlock(anchor, false);
                for (BlockPos pos : partial) context.assertTrue(world.getBlockState(pos).isOf(Blocks.WATER),
                        "Breaking a wet partial base must restore ordinary water: " + label + " " + pos);
                for (BlockPos pos : solid) context.assertTrue(world.getBlockState(pos).isAir(), "Broken solid/dry base must become air: " + label);
                for (BlockPos pos : upper) context.assertTrue(world.getBlockState(pos).isAir(), "Broken upper contained-only cell must become air: " + label);
                context.assertTrue(world.getBlockState(anchor).isAir(), "Broken solid controller must become air: " + label);
            }
        }
        clear(world, anchor);
        context.complete();
    }

    private static BlockState state(FountainBasinBlock.Style style, FountainBasinBlock.Size size) {
        return Registries.BLOCK.get(new Identifier("daedalon", "aganite_" + style.idSuffix()))
                .getDefaultState().with(FountainBasinBlock.SIZE, size);
    }

    private static Box basinWaterProbe(BlockPos anchor, FountainBasinBlock.Style style, FountainBasinBlock.Size size, int rise) {
        return new Box(anchor.getX() + 0.4, anchor.getY() + rise + style.basinFloorY(size) + 0.02,
                anchor.getZ() + 0.4, anchor.getX() + 0.6,
                anchor.getY() + rise + style.waterSurfaceY(size) + 0.1, anchor.getZ() + 0.6);
    }

    private static double globalContainedSurface(TestContext context, ServerWorld world, Box probe, String label) {
        BlockPos cell = BlockPos.ofFloored(probe.getCenter().x, probe.minY, probe.getCenter().z);
        double height = FountainBasinBlock.containedWaterSurfaceHeight(world, cell, world.getBlockState(cell), probe);
        context.assertTrue(height > 0, "Contained bowl water was not found in its actual owner cell: " + label);
        return cell.getY() + height;
    }

    private static void useRaisedControl(ServerPlayerEntity player, BlockPos anchor) {
        player.getMainHandStack().getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "raised_base");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false)));
    }

    private static BlockPos newCell(BlockPos anchor, FountainAssemblyLayout oldLayout, FountainAssemblyLayout raised) {
        for (int y = MAX_Y; y >= 0; y--) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (oldLayout.outlinePartShape(x, y, z).isEmpty() && !raised.outlinePartShape(x, y, z).isEmpty()) {
                return anchor.add(x, y, z);
            }
        }
        return null;
    }

    private static Map<BlockPos, VoxelShape> collisionCells(ServerWorld world, BlockPos anchor) {
        Map<BlockPos, VoxelShape> result = new LinkedHashMap<>();
        for (var entry : snapshot(world, anchor).entrySet()) {
            BlockPos pos = entry.getKey();
            VoxelShape shape = pos.equals(anchor) || FountainBasinPartBlock.isOwnedBy(entry.getValue(), pos, anchor)
                    ? entry.getValue().getCollisionShape(world, pos, ShapeContext.absent()) : VoxelShapes.empty();
            result.put(pos.subtract(anchor), shape);
        }
        return result;
    }

    /** Numeric bounds preserve the width checks without simplifying a giant union. */
    private static Box collisionBounds(Map<BlockPos, VoxelShape> cells, int minimumCellY) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var cell : cells.entrySet()) {
            BlockPos offset = cell.getKey();
            VoxelShape shape = cell.getValue();
            if (offset.getY() < minimumCellY || shape.isEmpty()) continue;
            minX = Math.min(minX, offset.getX() + shape.getMin(Direction.Axis.X));
            minY = Math.min(minY, offset.getY() + shape.getMin(Direction.Axis.Y));
            minZ = Math.min(minZ, offset.getZ() + shape.getMin(Direction.Axis.Z));
            maxX = Math.max(maxX, offset.getX() + shape.getMax(Direction.Axis.X));
            maxY = Math.max(maxY, offset.getY() + shape.getMax(Direction.Axis.Y));
            maxZ = Math.max(maxZ, offset.getZ() + shape.getMax(Direction.Axis.Z));
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Map<BlockPos, BlockState> snapshot(ServerWorld world, BlockPos anchor) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int y = 0; y <= MAX_Y; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = anchor.add(x, y, z);
            result.put(pos, world.getBlockState(pos));
        }
        return result;
    }

    private static void clear(ServerWorld world, BlockPos anchor) {
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        for (BlockPos pos : snapshot(world, anchor).keySet()) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        }
    }

    private static void near(TestContext context, double expected, double actual, String label) {
        context.assertTrue(Math.abs(expected - actual) < EPSILON,
                label + ": expected " + expected + ", found " + actual);
    }
}
