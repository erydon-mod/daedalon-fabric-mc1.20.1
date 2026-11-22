package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.HedraBlock;
import com.oliver.daedalon.block.HedraOrientation;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.DecorMaterial;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
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
                context.assertTrue(block.getStateManager().getStates().size() == 48,
                        "Four orientations must retain the saved legacy facings");
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
                        context.assertTrue(Math.abs(bounds.maxY - 0.6) < 1.0E-6 && Math.abs(bounds.minY) < 1.0E-6, "Seat must stay 0.6m high and grounded");
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
                                && Math.abs(selection.maxY-selection.minY-0.6) < 1.0E-6,
                                "Selection must cover the complete width, 0.6m height and one-block depth");
                        context.assertTrue(outline != shape, "Selection must remain separate from collision");
                        if (sharedOutlines[width-2][f] == null) sharedOutlines[width-2][f] = outline;
                        context.assertTrue(sharedOutlines[width-2][f] == outline, "Materials must share cached selection shapes");
                    }
                }
            }
        }
        Block block = Registries.BLOCK.get(new Identifier("daedalon", "aganite_hedra"));
        world.setBlockState(anchor, block.getDefaultState(), Block.NOTIFY_ALL);
        Vec3d throughGapStart = Vec3d.of(anchor).add(0.5, 0.24, 2.0);
        Vec3d throughGapEnd = Vec3d.of(anchor).add(0.5, 0.24, -1.0);
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
        context.assertTrue("orientation".equals(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)), "Left click must select orientation");
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(HedraBlock.orientation(world.getBlockState(anchor)) == HedraOrientation.DIAGONAL,
                "The first orientation edit must retain the original diagonal");
        for (int i = 0; i < 12; i++) {
            HedraOrientation expected = HedraOrientation.values()[(i + 2) % 4];
            Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            BlockState edited = world.getBlockState(anchor);
            context.assertTrue(HedraBlock.orientation(edited) == expected, "Debug must cycle all four axes");
            context.assertTrue(edited.get(HedraBlock.FACING) == Direction.SOUTH, "Debug axes must be absolute");
        }
        player.setSneaking(true);
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(HedraBlock.orientation(world.getBlockState(anchor)) == HedraOrientation.STRAIGHT,
                "Sneak must reverse the four-choice orientation cycle");
        for (int i = 0; i < 12; i++) {
            Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            context.assertTrue(HedraBlock.orientation(world.getBlockState(anchor))
                            == HedraOrientation.values()[Math.floorMod(-i - 1, 4)],
                    "Reverse cycling must include both diagonals and wrap without skipping an axis");
        }
        player.setSneaking(false);
        // A pre-orientation stick must edit the bench axis, not unexpectedly resize it.
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT, "facing");
        BlockState legacyPose = block.getDefaultState().with(HedraBlock.WIDTH, 4).with(HedraBlock.FACING, Direction.EAST);
        world.setBlockState(anchor, legacyPose, Block.NOTIFY_ALL);
        Items.DEBUG_STICK.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(world.getBlockState(anchor).get(HedraBlock.WIDTH) == 4
                        && HedraBlock.orientation(world.getBlockState(anchor)) == HedraOrientation.DIAGONAL_135,
                "A legacy facing selection must cycle the current absolute orientation and preserve width");
        for (int width = 2; width <= 4; width++) {
            BlockState original = block.getDefaultState().with(HedraBlock.WIDTH, width);
            for (HedraOrientation orientation : HedraOrientation.values()) {
                BlockState posed = HedraBlock.withOrientation(original, orientation);
                var collision = posed.getCollisionShape(world, anchor).getBoundingBox();
                var outline = posed.getOutlineShape(world, anchor).getBoundingBox();
                boolean diagonal = (orientation.eighthTurns & 1) != 0;
                double spanX = orientation == HedraOrientation.STRAIGHT ? width : HedraBlock.DEPTH;
                double spanZ = orientation == HedraOrientation.STRAIGHT_90 ? width : HedraBlock.DEPTH;
                if (diagonal) spanX = spanZ = (width + HedraBlock.DEPTH) / Math.sqrt(2);
                context.assertTrue(Math.abs(collision.getXLength() - spanX) < 2e-6
                                && Math.abs(collision.getZLength() - spanZ) < 2e-6,
                        "Every selectable axis must rotate the measured collision with the mesh");
                context.assertTrue(Math.abs(outline.getYLength() - .6) < 1e-6,
                        "Rotation must keep the selection grounded at seat height");
                double angle = orientation.eighthTurns * Math.PI / 4;
                double x = Math.cos(angle) * width * .4, z = Math.sin(angle) * width * .4;
                VoxelShape exactCollision = posed.getCollisionShape(world, anchor);
                context.assertTrue(contains(exactCollision, .5 + x, .5, .5 + z),
                        "The rotated seat collision must follow the selected axis, including the opposite diagonal");
                double supportDistance = width * .5 - .285;
                double supportX = Math.cos(angle) * supportDistance, supportZ = Math.sin(angle) * supportDistance;
                context.assertTrue(contains(exactCollision, .5 + supportX, .2, .5 + supportZ),
                        "Carved support collision must follow the selected diagonal below the seat");
                context.assertTrue(!contains(exactCollision, .5 - supportZ, .2, .5 + supportX),
                        "Matching seat bounding boxes must not conceal supports rotated onto the wrong diagonal");
                for (BlockMirror mirror : BlockMirror.values()) {
                    BlockState reflected = posed.mirror(mirror);
                    int expected = mirror == BlockMirror.NONE ? orientation.eighthTurns
                            : Math.floorMod(-orientation.eighthTurns, 4);
                    context.assertTrue(HedraBlock.eighthTurns(reflected) == expected,
                            "Each mirror must swap the two diagonals without changing cardinal axes");
                    context.assertTrue(reflected.mirror(mirror).equals(posed),
                            "Mirroring twice must recover the saved pose");
                }
                context.assertTrue(HedraBlock.eighthTurns(posed.rotate(BlockRotation.CLOCKWISE_90))
                                == (HedraBlock.eighthTurns(posed) + 2) % 4,
                        "Structure rotation must rotate cardinal and diagonal axes correctly");
            }
            // Old states combine a hidden cardinal facing with one of the three
            // published orientations. Their saved physical axes must not change.
            for (Direction facing : facings) {
                for (HedraOrientation legacy : new HedraOrientation[]{HedraOrientation.STRAIGHT,
                        HedraOrientation.DIAGONAL, HedraOrientation.STRAIGHT_90}) {
                    BlockState saved = original.with(HedraBlock.FACING, facing).with(HedraBlock.ORIENTATION, legacy);
                    int expected = Math.floorMod((facing.getAxis() == Direction.Axis.X ? 2 : 0) + legacy.eighthTurns, 4);
                    context.assertTrue(HedraBlock.eighthTurns(saved) == expected,
                            "Every legacy facing/orientation combination must retain its rendered axis");
                    BlockState loaded = roundTrip(saved);
                    context.assertTrue(loaded.equals(saved) && HedraBlock.eighthTurns(loaded) == expected,
                            "Legacy saved values must round-trip without canonicalising their facing");
                }
                BlockState appended = original.with(HedraBlock.FACING, facing)
                        .with(HedraBlock.ORIENTATION, HedraOrientation.DIAGONAL_135);
                context.assertTrue(roundTrip(appended).equals(appended),
                        "The appended opposite diagonal must persist for every saved facing");
                for (HedraOrientation orientation : HedraOrientation.values()) {
                    BlockState saved = original.with(HedraBlock.FACING, facing).with(HedraBlock.ORIENTATION, orientation);
                    int turn = HedraBlock.eighthTurns(saved);
                    for (BlockRotation rotation : BlockRotation.values()) {
                        int quarterTurns = switch (rotation) {
                            case NONE -> 0;
                            case CLOCKWISE_90 -> 1;
                            case CLOCKWISE_180 -> 2;
                            case COUNTERCLOCKWISE_90 -> 3;
                        };
                        context.assertTrue(HedraBlock.eighthTurns(saved.rotate(rotation)) == (turn + 2 * quarterTurns) % 4,
                                "Structure rotations must preserve every legacy/composed axis");
                    }
                    for (BlockMirror mirror : BlockMirror.values()) {
                        int expectedMirror = mirror == BlockMirror.NONE ? turn : Math.floorMod(-turn, 4);
                        context.assertTrue(HedraBlock.eighthTurns(saved.mirror(mirror)) == expectedMirror,
                                "Both mirrors must reflect every saved/composed axis correctly");
                    }
                }
            }
        }
        float[] yaws = {0, 45, 90, 135, 180, 225, 270, 315,
                22.49F, 22.51F, 67.49F, 67.51F, 112.49F, 112.51F, 157.49F, 157.51F, -22.49F, -22.51F};
        int[] placementAxes = {0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 1, 2, 2, 3, 3, 0, 0, 3};
        for (int i = 0; i < yaws.length; i++) {
            player.setYaw(yaws[i]);
            BlockState placement = block.getPlacementState(new ItemPlacementContext(player, Hand.MAIN_HAND,
                    block.asItem().getDefaultStack(), hit));
            context.assertTrue(placement != null && HedraBlock.eighthTurns(placement) == placementAxes[i],
                    "Fresh placement must snap to the nearest of all four axes at yaw " + yaws[i]);
            context.assertTrue(placement.get(HedraBlock.WIDTH) == 3,
                    "Orientation snapping must retain the default bench width");
        }
        for (BlockPos pos : BlockPos.iterate(anchor.add(-2,0,-2),anchor.add(2,0,2))) {
            if (!pos.equals(anchor)) context.assertTrue(world.getBlockState(pos).isAir(), "Width edits must not create proxy blocks");
        }
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.complete();
    }

    private static BlockState roundTrip(BlockState state) {
        var encoded = BlockState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state)
                .result().orElseThrow();
        return BlockState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).result().orElseThrow();
    }

    private static boolean contains(VoxelShape shape, double x, double y, double z) {
        return shape.getBoundingBoxes().stream().anyMatch(box -> box.minX <= x && x <= box.maxX
                && box.minY <= y && y <= box.maxY && box.minZ <= z && z <= box.maxZ);
    }
}
