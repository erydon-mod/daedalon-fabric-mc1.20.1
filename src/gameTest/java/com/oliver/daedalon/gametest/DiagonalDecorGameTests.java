package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.DecorShapeTransforms;
import com.oliver.daedalon.block.ExedraBlock;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.item.DecorRotationControl;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import java.util.Collections;
import java.util.IdentityHashMap;

public final class DiagonalDecorGameTests {
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void actualDebugStickTurnsEveryProfileThroughOneEightPositionControl(TestContext context) {
        var world = context.getWorld();
        var anchor = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var player = context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
        player.changeGameMode(GameMode.CREATIVE);
        ItemStack stick = Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND, stick);
        var usage = new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false));
        var profiles = Collections.newSetFromMap(new IdentityHashMap<StatueBlock.Profile, Boolean>());
        var blocks = new java.util.ArrayList<Block>();
        blocks.add(Registries.BLOCK.get(new Identifier("daedalon", "aganite_exedra")));
        for (var block : Registries.BLOCK) {
            if (block instanceof StatueBlock statue && profiles.add(statue.statueProfile())) blocks.add(block);
        }
        String[] directions = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        int edits = 0;
        for (var block : blocks) {
            var initial = block.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.NORTH);
            var controls = DaedalonDebugProperties.ordered(block.getStateManager().getProperties());
            var controlNames = controls.stream().map(property -> property.getName()).toList();
            context.assertTrue(controlNames.equals(block instanceof ExedraBlock
                            ? java.util.List.of("width", "facing") : java.util.List.of("size", "offset", "facing")),
                    "Only one rotation control may appear: " + block + " / " + controlNames);
            world.setBlockState(anchor, initial, Block.NOTIFY_ALL);
            // A stick that remembered the former separate half-step control must recover here.
            stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "orientation");
            player.setSneaking(false);
            for (int step = 1; step <= 8; step++) {
                context.assertTrue(Items.DEBUG_STICK.useOnBlock(usage).isAccepted(), "Orientation edit was not handled");
                var state = world.getBlockState(anchor);
                context.assertTrue(DecorRotationControl.valueName(state).equals(directions[step % 8]),
                        "Debug stick must turn clockwise by 45 degrees: " + block + " / " + step + " / " + state);
                context.assertTrue(state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.NORTH)
                                .with(DecorOrientation.PROPERTY, DecorOrientation.STRAIGHT) == initial,
                        "Orientation edits must retain every other selected control");
                edits++;
            }
            context.assertTrue(world.getBlockState(anchor) == initial, "Eight right-clicks must restore the original pose");
            context.assertTrue(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT).equals("facing"),
                    "The combined control must retain the existing remembered facing name");
            player.setSneaking(true);
            for (int step = 1; step <= 8; step++) {
                Items.DEBUG_STICK.useOnBlock(usage);
                context.assertTrue(DecorRotationControl.valueName(world.getBlockState(anchor)).equals(directions[Math.floorMod(-step, 8)]),
                        "Crouch must reverse every 45-degree step: " + block + " / " + step);
                edits++;
            }
            context.assertTrue(world.getBlockState(anchor) == initial, "Eight reverse edits must restore the original pose");
            player.setSneaking(false);
            Items.DEBUG_STICK.canMine(world.getBlockState(anchor), world, anchor, player);
            context.assertTrue(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)
                            .equals(block instanceof ExedraBlock ? "width" : "size"),
                    "Selecting the next control must not expose a second orientation attribute");
            world.removeBlock(anchor, false);
        }
        context.assertTrue(profiles.size() > 20 && edits == blocks.size() * 16, "Cover every statue/bust mesh profile and Exedra");
        System.out.println("DAEDALON_EIGHT_WAY_DEBUG passed profiles=" + profiles.size() + " edits=" + edits);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyStatueProfileKeepsDiagonalShapesGroundedAndOffsetsAligned(TestContext context) {
        var profiles = Collections.newSetFromMap(new IdentityHashMap<StatueBlock.Profile, Boolean>());
        int blocks = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof StatueBlock statue) || !Registries.BLOCK.getId(block).getNamespace().equals("daedalon")) continue;
            blocks++;
            context.assertTrue(block.getDefaultState().get(StatueBlock.ORIENTATION) == DecorOrientation.STRAIGHT,
                    "Existing statue defaults must remain straight");
            context.assertTrue(block.getStateManager().getStates().size() == 48, "Statue must have exactly eight poses per size/offset");
            if (!profiles.add(statue.statueProfile())) continue;
            for (var size : StatueBlock.StatueSize.values()) {
                for (Direction facing : FACINGS) {
                    BlockState cardinal = block.getDefaultState().with(StatueBlock.SIZE, size).with(StatueBlock.FACING, facing);
                    var straight = shape(context, cardinal);
                    BlockState diagonal = cardinal.with(StatueBlock.ORIENTATION, DecorOrientation.DIAGONAL);
                    var turned = shape(context, diagonal);
                    checkDiagonalBounds(context, straight, turned);
                    context.assertTrue(shape(context, diagonal) == turned, "Diagonal shapes must be cached");
                    var offset = shape(context, diagonal.with(StatueBlock.OFFSET, true)).getBoundingBox();
                    var centre = turned.getBoundingBox();
                    double d = Math.sqrt(.5) * DecorShapeTransforms.OFFSET_DISTANCE;
                    double x = d * (facing.getOffsetX() - facing.getOffsetZ());
                    double z = d * (facing.getOffsetX() + facing.getOffsetZ());
                    near(context, centre.minX + x, offset.minX, "Offset X must follow diagonal facing");
                    near(context, centre.minZ + z, offset.minZ, "Offset Z must follow diagonal facing");
                    near(context, centre.minY, offset.minY, "Offset must stay grounded");
                    checkTransforms(context, diagonal);
                }
            }
        }
        context.assertTrue(blocks > 1000 && profiles.size() > 20, "Must cover every statue/bust material and mesh profile");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void exedraHasEightCachedPosesAtEveryWidthWithoutChangingHeight(TestContext context) {
        var block = Registries.BLOCK.get(new Identifier("daedalon", "aganite_exedra"));
        for (int width = 2; width <= 4; width++) {
            for (Direction facing : FACINGS) {
                var straight = block.getDefaultState().with(ExedraBlock.WIDTH, width).with(ExedraBlock.FACING, facing);
                var diagonal = straight.with(ExedraBlock.ORIENTATION, DecorOrientation.DIAGONAL);
                var shape = shape(context, diagonal);
                checkDiagonalBounds(context, shape(context, straight), shape);
                near(context, 1, shape.getMax(Direction.Axis.Y), "Exedra height must remain one metre");
                context.assertTrue(shape(context, diagonal) == shape, "Exedra diagonal shape must be cached");
                checkTransforms(context, diagonal);
                Block other = Registries.BLOCK.get(new Identifier("daedalon", "aganite_aged_exedra"));
                context.assertTrue(shape(context, other.getDefaultState().with(ExedraBlock.WIDTH, width)
                        .with(ExedraBlock.FACING, facing).with(ExedraBlock.ORIENTATION, DecorOrientation.DIAGONAL)) == shape,
                        "Exedra materials must share diagonal geometry");
            }
        }
        context.complete();
    }

    private static VoxelShape shape(TestContext context, BlockState state) {
        return state.getCollisionShape(context.getWorld(), BlockPos.ORIGIN, ShapeContext.absent());
    }

    private static void checkDiagonalBounds(TestContext context, VoxelShape straight, VoxelShape diagonal) {
        double[] extremes = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double d = Math.sqrt(.5);
        straight.forEachBox((x0, y0, z0, x1, y1, z1) -> {
            extremes[0] = Math.min(extremes[0], .5 + d * (x0 - z1));
            extremes[1] = Math.min(extremes[1], .5 + d * (x0 + z0 - 1));
            extremes[2] = Math.max(extremes[2], .5 + d * (x1 - z0));
            extremes[3] = Math.max(extremes[3], .5 + d * (x1 + z1 - 1));
        });
        var box = diagonal.getBoundingBox();
        near(context, extremes[0], box.minX, "Rotated collision minX");
        near(context, extremes[1], box.minZ, "Rotated collision minZ");
        near(context, extremes[2], box.maxX, "Rotated collision maxX");
        near(context, extremes[3], box.maxZ, "Rotated collision maxZ");
        near(context, straight.getMin(Direction.Axis.Y), box.minY, "Rotation must preserve ground contact");
        near(context, straight.getMax(Direction.Axis.Y), box.maxY, "Rotation must preserve height");
    }

    private static void checkTransforms(TestContext context, BlockState state) {
        var rotated = state;
        for (int i = 0; i < 4; i++) rotated = rotated.rotate(BlockRotation.CLOCKWISE_90);
        context.assertTrue(rotated == state, "Four copied quarter turns must restore original state");
        for (var mirror : new BlockMirror[]{BlockMirror.FRONT_BACK, BlockMirror.LEFT_RIGHT}) {
            context.assertTrue(state.mirror(mirror).mirror(mirror) == state, "Mirroring twice must restore diagonal pose");
            Direction facing = state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING);
            Direction reflected = mirror.getRotation(facing).rotate(facing).rotateYCounterclockwise();
            context.assertTrue(state.mirror(mirror).get(net.minecraft.state.property.Properties.HORIZONTAL_FACING) == reflected,
                    "Mirror must reverse the diagonal half step");
        }
    }

    private static void near(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 1e-6, message + ": expected " + expected + ", got " + actual);
    }
}
