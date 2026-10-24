package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.DecorShapeTransforms;
import com.oliver.daedalon.block.ExedraBlock;
import com.oliver.daedalon.block.SpartanStatueBlock;
import com.oliver.daedalon.block.StatueBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.item.DecorRotationControl;
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
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
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
                if (state.getBlock() instanceof StatueBlock) assertRenderedFront(context, state);
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
                var state = world.getBlockState(anchor);
                if (state.getBlock() instanceof StatueBlock) assertRenderedFront(context, state);
                context.assertTrue(DecorRotationControl.valueName(state).equals(directions[Math.floorMod(-step, 8)]),
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
        context.assertTrue(profiles.size() == 36 && edits == blocks.size() * 16, "Cover all 36 statue/bust mesh profiles and Exedra");
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
                    assertRenderedFront(context, cardinal);
                    var straight = shape(context, cardinal);
                    BlockState diagonal = cardinal.with(StatueBlock.ORIENTATION, DecorOrientation.DIAGONAL);
                    assertRenderedFront(context, diagonal);
                    assertRenderedFront(context, cardinal.with(StatueBlock.OFFSET, true));
                    assertRenderedFront(context, diagonal.with(StatueBlock.OFFSET, true));
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
        context.assertTrue(blocks == 2016 && profiles.size() == 36, "Must cover all 36 statue/bust meshes in all 56 finishes");
        context.complete();
    }

    /**
     * Applies the renderer's actual profile transform to the independently
     * reviewed OBJ front vector, rather than trusting the saved facing label.
     * The four normalized meshes, all source hashes and the published
     * Spartan's retained east-profile convention are recorded in
     * docs/evidence/statue-source-facing.json.
     */
    static void assertRenderedFront(TestContext context, BlockState state) {
        Vec3d front = renderedFront(state);
        Direction facing = state.get(StatueBlock.FACING);
        int halfStep = state.get(StatueBlock.ORIENTATION).ordinal();
        double angle = switch (facing) {
            case NORTH -> 0;
            case EAST -> Math.PI / 2;
            case SOUTH -> Math.PI;
            case WEST -> Math.PI * 1.5;
            default -> throw new AssertionError("Statue facing must be horizontal");
        } + halfStep * Math.PI / 4;
        // Published Spartans keep their existing 90-degree physical-front
        // offset from the saved facing; no world rotation is migrated here.
        if (state.getBlock() instanceof SpartanStatueBlock) angle += Math.PI / 2;
        near(context, Math.sin(angle), front.x, "Rendered front X must match the selected pose: " + state);
        near(context, -Math.cos(angle), front.z, "Rendered front Z must match the selected pose: " + state);
    }

    private static Vec3d renderedFront(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        boolean northSource = path.endsWith("_hermes_statue") || path.endsWith("_hephaestus_statue")
                || path.endsWith("_hestia_statue") || path.endsWith("_hestia_bust");
        float sourceFrontZ = northSource ? -1.0F : 1.0F;
        Direction facing = state.get(StatueBlock.FACING);
        int halfStep = state.get(StatueBlock.ORIENTATION).ordinal();
        int renderedTurns = ((StatueBlock) state.getBlock()).rotationStepsFromSource(facing) * 2 + halfStep;
        float frontX = DecorOrientation.rotateX(0, sourceFrontZ, renderedTurns);
        float frontZ = DecorOrientation.rotateZ(0, sourceFrontZ, renderedTurns);
        return new Vec3d(frontX, 0, frontZ);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void realSculpturePlacementsFacePlayerAndPublishedSpartanKeepsItsConvention(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(30, 3, 30));
        var player = context.createMockCreativeServerPlayerInWorld();
        var profiles = Collections.newSetFromMap(new IdentityHashMap<StatueBlock.Profile, Boolean>());
        int facingPlayer = 0;
        int legacyPlacements = 0;
        world.setBlockState(anchor.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof StatueBlock statue) || !profiles.add(statue.statueProfile())) continue;
            for (Direction look : FACINGS) {
                world.removeBlock(anchor, false);
                player.refreshPositionAndAngles(anchor.getX() + .5 - look.getOffsetX() * 3,
                        anchor.getY(), anchor.getZ() + .5 - look.getOffsetZ() * 3, look.asRotation(), 0);
                context.assertTrue(player.getHorizontalFacing() == look, "Mock player must look towards the placement");
                ItemStack stack = block.asItem().getDefaultStack();
                player.setStackInHand(Hand.MAIN_HAND, stack);
                var usage = new ItemUsageContext(player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.of(anchor.down()).add(.5, 1, .5), Direction.UP, anchor.down(), false));
                context.assertTrue(stack.useOnBlock(usage).isAccepted(), "Actual sculpture item placement must succeed: " + block);
                BlockState placed = world.getBlockState(anchor);
                Direction expectedFacing = block instanceof SpartanStatueBlock ? look : look.getOpposite();
                context.assertTrue(placed == block.getDefaultState().with(StatueBlock.FACING, expectedFacing),
                        "Placement must retain default size/offset/orientation and the intended facing: " + block);
                assertRenderedFront(context, placed);
                if (block instanceof SpartanStatueBlock) {
                    legacyPlacements++;
                } else {
                    Vec3d front = renderedFront(placed);
                    near(context, -look.getOffsetX(), front.x, "Fresh sculpture front must face the player on X");
                    near(context, -look.getOffsetZ(), front.z, "Fresh sculpture front must face the player on Z");
                    facingPlayer++;
                }
            }
        }
        context.assertTrue(profiles.size() == 36 && facingPlayer == 140 && legacyPlacements == 4,
                "Place all 35 corrected meshes towards the player and retain all four published Spartan facings");
        world.removeBlock(anchor, false);
        world.removeBlock(anchor.down(), false);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void publishedSpartanRetainsEveryLegacyPoseCollisionAndRenderFrame(TestContext context) throws Exception {
        // Frozen pre-correction collision fixture. The production Spartan
        // profile and public helper deliberately retain their published EAST
        // convention, although the inspected OBJ body front points south.
        double[][] boxes = {
                {-.16, 0, -.48, 1, .28, .24}, {-.12, .24, -.43, .80, .78, .30},
                {-.06, .72, -.38, .74, 1.30, .41}, {-.12, 1.20, -.34, .88, 2.24, .40},
                {-.08, 2.16, -.34, .94, 2.72, .36}, {.12, 2.66, -.26, .66, 2.96, .28},
                {.45, 1.30, -.79, 1.16, 1.58, -.33}, {.34, 1.52, -.87, 1.21, 2.13, -.29},
                {.28, 2.07, -.60, .87, 2.35, -.29}, {-.22, 1.55, .28, .27, 2.22, .96},
                {-.10, .82, .31, .20, 1.24, .46}, {-.10, 1.16, .37, .20, 1.58, .60},
                {-.10, 1.50, .49, .20, 1.92, .82}, {-.08, 1.84, .70, .22, 2.24, 1.08},
                {0, 2.16, 1, .27, 2.51, 1.48}, {.08, 2.44, 1.36, .29, 2.77, 1.72},
                {.15, 2.70, 1.64, .31, 3.02, 1.88}
        };
        VoxelShape source = VoxelShapes.empty();
        for (double[] box : boxes) source = VoxelShapes.union(source,
                VoxelShapes.cuboid(box[0], box[1], box[2], box[3], box[4], box[5]));
        var constructor = StatueBlock.Profile.class.getDeclaredConstructor(Direction.class,
                float.class, float.class, float.class, VoxelShape.class);
        constructor.setAccessible(true);
        var legacy = constructor.newInstance(Direction.EAST, .41134763F, .0625F, -.14984404F, source);
        var collision = StatueBlock.Profile.class.getDeclaredMethod("collisionShapeFor",
                StatueBlock.StatueSize.class, boolean.class, Direction.class, DecorOrientation.class);
        collision.setAccessible(true);
        int finishes = 0;
        int poses = 0;
        int[] legacyQuarterTurns = {3, 0, 1, 2};
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof SpartanStatueBlock statue)) continue;
            finishes++;
            context.assertTrue(statue.modelSupportCenterX() == .41134763F
                            && statue.modelGroundContactY() == .0625F && statue.modelSupportCenterZ() == -.14984404F,
                    "Published Spartan render pivot must remain unchanged");
            for (BlockState state : block.getStateManager().getStates()) {
                Direction facing = state.get(StatueBlock.FACING);
                int index = java.util.List.of(FACINGS).indexOf(facing);
                context.assertTrue(statue.rotationStepsFromSource(facing) == legacyQuarterTurns[index]
                                && SpartanStatueBlock.rotationStepsFromEast(facing) == legacyQuarterTurns[index],
                        "Published Spartan renderer and public helper must retain their legacy turns");
                VoxelShape expected = (VoxelShape) collision.invoke(legacy, state.get(StatueBlock.SIZE),
                        state.get(StatueBlock.OFFSET), facing, state.get(StatueBlock.ORIENTATION));
                context.assertTrue(!VoxelShapes.matchesAnywhere(shape(context, state), expected, BooleanBiFunction.NOT_SAME),
                        "Published Spartan collision boxes must remain equivalent in every legacy pose: " + state);
                assertRenderedFront(context, state);
                poses++;
            }
        }
        context.assertTrue(finishes == 56 && poses == 2688, "Cover all published Spartan materials and 48 poses");
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
