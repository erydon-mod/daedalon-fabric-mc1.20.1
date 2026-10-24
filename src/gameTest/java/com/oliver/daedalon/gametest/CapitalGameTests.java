package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.CapitalPartBlock;
import com.oliver.daedalon.block.CapitalTransition;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.mojang.authlib.GameProfile;

public final class CapitalGameTests {
    private static final class WarningPlayer extends ServerPlayerEntity {
        private ItemStack held=ItemStack.EMPTY;
        private boolean sneaking;
        private Text message;
        private int messageCount;
        WarningPlayer(MinecraftServer server,ServerWorld world) {
            super(server,world,new GameProfile(java.util.UUID.randomUUID(),"capital-warning"));
        }
        @Override public boolean isCreativeLevelTwoOp() { return true; }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public boolean shouldCancelInteraction() { return false; }
        @Override public ItemStack getStackInHand(Hand hand) { return held==null ? ItemStack.EMPTY : held; }
        @Override public void sendMessage(Text message,boolean overlay) { this.message=message; messageCount++; }
        @Override public void sendMessageToClient(Text message,boolean overlay) { this.message=message; messageCount++; }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 400)
    public void obstructedCapitalResizeWarnsWithoutChangingTheFootprint(TestContext context) {
        var world=context.getWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(2,2,2));
        var player=new WarningPlayer(world.getServer(),world);
        var stick=Items.DEBUG_STICK.getDefaultStack();
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"size");
        player.held=stick;
        var hit=new BlockHitResult(Vec3d.ofCenter(anchor),Direction.UP,anchor,false);
        var usage=new ItemUsageContext(player,Hand.MAIN_HAND,hit);
        int checked=0;
        for(var style:CapitalBlock.Style.values()) {
            var capital=(CapitalBlock)Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon","aganite_"+style.name().toLowerCase(java.util.Locale.ROOT)+"_capital"));
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) for(int z=0;z<2;z++) {
                if(x+y+z==0) continue;
                world.setBlockState(anchor,capital.getDefaultState(),Block.NOTIFY_ALL);
                BlockPos obstruction=anchor.add(x,y,z);
                world.setBlockState(obstruction,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                java.util.Map<BlockPos,BlockState> before=new java.util.HashMap<>();
                for(int py=0;py<2;py++) for(int px=0;px<2;px++) for(int pz=0;pz<2;pz++) {
                    BlockPos pos=anchor.add(px,py,pz); before.put(pos,world.getBlockState(pos));
                }
                player.held=ItemStack.EMPTY; player.sneaking=true; player.message=null; player.messageCount=0;
                context.assertTrue(capital.onUse(world.getBlockState(anchor),world,anchor,player,Hand.MAIN_HAND,hit)==net.minecraft.util.ActionResult.FAIL,"Obstructed empty-hand resize must fail");
                assertWarning(context,player,false);
                before.forEach((pos,state) -> context.assertTrue(world.getBlockState(pos)==state,"Empty-hand failure changed the footprint"));
                player.held=stick; player.sneaking=false; player.message=null; player.messageCount=0;
                context.assertTrue(Items.DEBUG_STICK.useOnBlock(usage).isAccepted(),"Blocked debug-stick edit must be handled");
                assertWarning(context,player,true);
                before.forEach((pos,state) -> context.assertTrue(world.getBlockState(pos)==state,"Debug-stick failure changed the footprint"));
                world.removeBlock(obstruction,false);
                Items.DEBUG_STICK.useOnBlock(usage);
                context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.DOUBLE,"Clearing the obstruction must allow the same selected control to grow");
                player.held=ItemStack.EMPTY; player.sneaking=true; player.message=null;
                capital.onUse(world.getBlockState(anchor),world,anchor,player,Hand.MAIN_HAND,hit);
                context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.STANDARD && player.message==null,"Successful shrink must remain silent and keep cycling");
                world.removeBlock(anchor,false);
                checked++;
            }
        }
        context.assertTrue(checked==42,"Cover every obstructed cell in all six styles");
        context.complete();
    }

    private static void assertWarning(TestContext context,WarningPlayer player,boolean debugStick) {
        // Current Daedalon owns its controls before the older companion fallback.
        Text message=player.message;
        context.assertTrue(player.messageCount==1,"Rejected resize must send exactly one warning");
        context.assertTrue(message!=null && message.getContent() instanceof TranslatableTextContent text
                && text.getKey().equals("message.daedalon.capital_size_blocked"),
                "Resize must send the localized capital warning: "+message);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 400)
    public void capitalSelectionHidesOffsetsAndRepairsOldEdits(TestContext context) {
        var world=context.getWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(2,2,2));
        var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        player.changeGameMode(GameMode.CREATIVE);
        var stick=Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND,stick);
        int checked=0;
        for (var style:CapitalBlock.Style.values()) {
            var capital=(CapitalBlock)Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon","aganite_"+style.name().toLowerCase(java.util.Locale.ROOT)+"_capital"));
            for (int y=0;y<2;y++) for (int x=0;x<2;x++) for (int z=0;z<2;z++)
                for (var offset:java.util.List.of(CapitalPartBlock.X,CapitalPartBlock.Y,CapitalPartBlock.Z)) {
                    world.setBlockState(anchor,capital.getDefaultState().with(CapitalBlock.SIZE,CapitalBlock.Size.DOUBLE),Block.NOTIFY_ALL);
                    BlockPos clicked=anchor.add(x,y,z);
                    if (x+y+z>0) {
                        BlockState part=world.getBlockState(clicked);
                        world.setBlockState(clicked,part.with(offset,1-part.get(offset)),Block.NOTIFY_ALL);
                        context.assertTrue(anchor.equals(CapitalPartBlock.resolveAnchorPos(world,clicked,world.getBlockState(clicked))),"Corrupt offsets must resolve the same capital");
                        context.assertTrue(!world.getBlockState(clicked).getOutlineShape(world,clicked).isEmpty(),"A corrupt helper must remain selectable");
                    }
                    stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,offset.getName());
                    stick.getOrCreateSubNbt("DebugProperty").putString("daedalon:capital_part",offset.getName());
                    for (String selected:java.util.List.of("size","capital_orientation","size","capital_orientation","size")) {
                        Items.DEBUG_STICK.canMine(world.getBlockState(clicked),world,clicked,player);
                        context.assertTrue(selected.equals(stick.getOrCreateNbt().getString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT)),"Left-click selection must expose only Size and Orientation");
                    }
                    var usage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),Direction.UP,clicked,false));
                    Items.DEBUG_STICK.useOnBlock(usage);
                    context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.STANDARD,"Selection then update must shrink the whole capital");
                    for(int py=0;py<2;py++) for(int px=0;px<2;px++) for(int pz=0;pz<2;pz++)
                        if(px+py+pz>0) context.assertTrue(world.getBlockState(anchor.add(px,py,pz)).isAir(),"Old corrupt cells must be removed on shrink");
                    var anchorUsage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(anchor),Direction.UP,anchor,false));
                    Items.DEBUG_STICK.useOnBlock(anchorUsage);
                    context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.DOUBLE,"Regrowing must still work after selecting at an outer cell");
                    world.setBlockState(anchor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    checked++;
                }
        }
        context.assertTrue(checked==144,"Cover six styles, eight clicked cells and every old offset control");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void largeCapitalsCycleFromEveryOccupiedCell(TestContext context) {
        var world=context.getWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(2,2,2));
        var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        player.changeGameMode(GameMode.CREATIVE);
        var stick=Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND,stick);
        int checked=0;
        for(var style:CapitalBlock.Style.values()) {
            var capital=(CapitalBlock)Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon","aganite_"+style.name().toLowerCase(java.util.Locale.ROOT)+"_capital"));
            BlockState large=capital.getDefaultState().with(CapitalBlock.SIZE,CapitalBlock.Size.DOUBLE);
            world.setBlockState(anchor,large,Block.NOTIFY_ALL);
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) for(int z=0;z<2;z++) {
                BlockPos clicked=anchor.add(x,y,z);
                var usage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),Direction.UP,clicked,false));
                stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"capital_orientation");
                Items.DEBUG_STICK.useOnBlock(usage);
                context.assertTrue(capital.orientation(world.getBlockState(anchor))==CapitalOrientation.DIAGONAL,"Every capital cell must edit its anchor orientation");
                player.setSneaking(true); Items.DEBUG_STICK.useOnBlock(usage); player.setSneaking(false);
                context.assertTrue(capital.orientation(world.getBlockState(anchor))==CapitalOrientation.STRAIGHT,"Reverse cycle must work from the same cell");
                stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"size");
                Items.DEBUG_STICK.useOnBlock(usage);
                context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.STANDARD,"Every capital cell must shrink its anchor");
                for(int py=0;py<2;py++) for(int px=0;px<2;px++) for(int pz=0;pz<2;pz++) {
                    if(px+py+pz>0) context.assertTrue(world.getBlockState(anchor.add(px,py,pz)).isAir(),"Shrinking must clear every owned cell");
                }
                var anchorUsage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(anchor),Direction.UP,anchor,false));
                Items.DEBUG_STICK.useOnBlock(anchorUsage);
                context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE)==CapitalBlock.Size.DOUBLE,"Small and large sizes must continue cycling");
                checked++;
            }
            world.setBlockState(anchor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.assertTrue(checked==48,"Cover all eight cells and six capital styles");
        context.complete();
    }

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
            context.assertTrue(block.getStateManager().getStates().size() == variants * 2 * CapitalTransition.values().length, "Orientation, size and automatic support are the capital states");
            context.assertTrue(DaedalonDebugProperties.ordered(block.getStateManager().getProperties()).size() == 2,
                    "Only size and orientation are player controls");
            BlockState original = block.getDefaultState();
            context.assertTrue(capital.orientation(original) == CapitalOrientation.STRAIGHT, "Old appearance must be the default");
            context.assertTrue(original.get(CapitalBlock.SIZE) == CapitalBlock.Size.STANDARD, "Legacy capitals remain standard size");
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void doubleCapitalOccupiesAndReleasesEightCells(TestContext context) {
        var world = context.getWorld();
        Block block = Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockState large = block.getDefaultState().with(CapitalBlock.SIZE, CapitalBlock.Size.DOUBLE);
        world.setBlockState(anchor, large, Block.NOTIFY_ALL);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos cell = anchor.add(x, y, z);
            context.assertTrue(x == 0 && y == 0 && z == 0
                    ? world.getBlockState(cell).isOf(block)
                    : world.getBlockState(cell).getBlock() instanceof com.oliver.daedalon.block.CapitalPartBlock,
                    "Double capital must occupy all eight cells");
        }
        world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            context.assertTrue(world.getBlockState(anchor.add(x, y, z)).isAir(), "Removed capital must clear its cells");
        world.setBlockState(anchor, block.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos occupied = anchor.add(1, 0, 0);
        world.setBlockState(occupied, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(anchor, large, Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SIZE) == CapitalBlock.Size.STANDARD,
                "Resizing must fail when a neighbouring cell is occupied");
        context.assertTrue(world.getBlockState(occupied).isOf(Blocks.STONE),
                "Resizing must never replace a neighbouring block");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void capitalsAlignToEveryQuadrantOfLargeErydonColumn(TestContext context) {
        var world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos anchor = support.up();
        Block capitalBlock = Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.CREATIVE);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            world.setBlockState(support.add(x, 0, z), LargeColumnFixture.BLOCK.getDefaultState()
                    .with(LargeColumnFixture.X, x).with(LargeColumnFixture.Z, z)
                    .with(LargeColumnFixture.SECTION, LargeColumnFixture.Section.CAPITAL_UPPER), Block.NOTIFY_ALL);

        for (int clickedX = 0; clickedX < 2; clickedX++) for (int clickedZ = 0; clickedZ < 2; clickedZ++) {
            BlockPos clicked = support.add(clickedX, 0, clickedZ);
            player.setStackInHand(Hand.MAIN_HAND, capitalBlock.asItem().getDefaultStack());
            var hit = new BlockHitResult(new Vec3d(clicked.getX() + 0.5, clicked.getY() + 1, clicked.getZ() + 0.5),
                    Direction.UP, clicked, false);
            capitalBlock.asItem().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
            BlockState placed = world.getBlockState(anchor);
            context.assertTrue(placed.isOf(capitalBlock) && placed.get(CapitalBlock.SIZE) == CapitalBlock.Size.DOUBLE,
                    "Capital must be double and anchored at the column corner for every clicked quadrant");
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                context.assertTrue(world.getBlockState(anchor.add(x, y, z)).getBlock() instanceof CapitalPartBlock,
                        "Aligned double capital must reserve the complete two-by-two footprint");
            }
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 400)
    public void squareAndGothicTransitionsCoverEveryCapitalStyleSizeAndFinish(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 2, 2));
        int checked = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof CapitalBlock capital)) continue;
            for (CapitalTransition transition : java.util.List.of(CapitalTransition.SQUARE, CapitalTransition.GOTHIC,
                    CapitalTransition.SQUARE_SHAFT, CapitalTransition.GOTHIC_SHAFT)) {
                Block support = transition == CapitalTransition.SQUARE || transition == CapitalTransition.SQUARE_SHAFT
                        ? LargeColumnFixture.SQUARE : LargeColumnFixture.GOTHIC;
                BlockState supportState = support.getDefaultState();
                if (transition == CapitalTransition.SQUARE_SHAFT || transition == CapitalTransition.GOTHIC_SHAFT)
                    supportState = supportState.with(LargeColumnFixture.CAPITAL, LargeColumnFixture.Capital.NONE);
                for (CapitalBlock.Size size : CapitalBlock.Size.values()) {
                    int width = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
                    for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                        world.setBlockState(anchor.add(x, -1, z), supportState, Block.NOTIFY_ALL);
                    world.setBlockState(anchor, capital.getDefaultState().with(CapitalBlock.SIZE, size), Block.NOTIFY_ALL);
                    BlockState fitted = world.getBlockState(anchor);
                    context.assertTrue(fitted.get(CapitalBlock.SUPPORT) == transition, "Commands and structure-style placements must fit automatically");
                    var encoded = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, fitted).result().orElseThrow();
                    context.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow() == fitted,
                            "Automatic support must survive save/load and structure copies");
                    context.assertTrue(fitted.rotate(BlockRotation.CLOCKWISE_90).get(CapitalBlock.SUPPORT) == transition,
                            "A tool rotation must preserve the automatic support profile");
                    var shape = capital.shapeForCell(fitted, 0, 0, 0);
                    context.assertTrue(shape == capital.shapeForCell(fitted, 0, 0, 0), "Collar shapes must be cached across lookups");
                    context.assertTrue(shape.getMin(Direction.Axis.X) <= width * transition.profile(size).minX()
                                    && shape.getMax(Direction.Axis.X) >= width * transition.profile(size).maxX(),
                            "Selection and collision must include the collar footprint");
                    world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                        world.setBlockState(anchor.add(x, -1, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    checked++;
                }
            }
        }
        context.assertTrue(checked == 2592, "Test six capital styles, 54 finishes, two sizes and all head/shaft supports");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void transitionsRefreshWhenAnySupportCellChangesAndStayOffCircularColumns(TestContext context) {
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var capital = (CapitalBlock) Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            world.setBlockState(anchor.add(x, -1, z), LargeColumnFixture.SQUARE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(anchor, capital.getDefaultState().with(CapitalBlock.SIZE, CapitalBlock.Size.DOUBLE), Block.NOTIFY_ALL);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos support = anchor.add(x, -1, z);
            world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == CapitalTransition.NONE,
                    "Changing any of four support cells must remove the double collar");
            world.setBlockState(support, LargeColumnFixture.SQUARE.getDefaultState(), Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == CapitalTransition.SQUARE,
                    "Restoring the support must restore the collar without a tick or re-placement");
        }
        world.setBlockState(anchor, capital.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == CapitalTransition.SQUARE,
                "Shrinking must keep the standard square collar");
        world.setBlockState(anchor.down(), LargeColumnFixture.BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == CapitalTransition.NONE,
                "Circular columns must retain their original capital rim");
        world.setBlockState(anchor.down(), LargeColumnFixture.GOTHIC.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == CapitalTransition.GOTHIC,
                "Existing placements must refit from a local downward neighbour update");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void actualErydonColumnControlsRefitHeadsAndBareShaftsWithoutReplacingTheCapital(TestContext context) {
        if (!hasRealErydon(context)) { context.complete(); return; }
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 4, 3));
        var capital = (CapitalBlock) Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        var player = new WarningPlayer(world.getServer(), world);
        for (String form : java.util.List.of("square", "gothic")) {
            Block column = Registries.BLOCK.get(new net.minecraft.util.Identifier("erydon", "aganite_column_" + form));
            context.assertTrue(!column.getDefaultState().isAir(), "The real ERYDON column must be installed");
            world.setBlockState(anchor.down(2), column.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(anchor.down(), column.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(anchor, capital.getDefaultState(), Block.NOTIFY_ALL);
            CapitalTransition head = form.equals("square") ? CapitalTransition.SQUARE : CapitalTransition.GOTHIC;
            CapitalTransition shaft = form.equals("square") ? CapitalTransition.SQUARE_SHAFT : CapitalTransition.GOTHIC_SHAFT;
            context.assertTrue(named(world.getBlockState(anchor.down()), "part").equals("capital"), "Real stack must expose its top capital part");
            context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == head, "Existing native heads must retain the original support value");
            for (int click = 0; click < 3 && !named(world.getBlockState(anchor.down()), "capital").equals("none"); click++) {
                clickColumn(column, world, anchor.down(), player);
            }
            context.assertTrue(named(world.getBlockState(anchor.down()), "capital").equals("none"), "Side clicks must reach the real Capital None option");
            context.assertTrue(world.getBlockState(anchor).isOf(capital)
                            && world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == shaft,
                    "The existing Daedalon capital must refit immediately after the real None control");
            // A stored pillar is bare regardless of its remembered capital choice.
            BlockState pillar = withNamed(world.getBlockState(anchor.down()), "part", "pillar");
            pillar = withNamed(pillar, "capital", "georgian");
            context.assertTrue(com.oliver.daedalon.block.CapitalSupportPlacement.fitted(
                    capital.getDefaultState(), new SingleSupportView(world, anchor.down(), pillar), anchor)
                    .get(CapitalBlock.SUPPORT) == shaft, "A real bare pillar must not be mistaken for its remembered head style");
            clickColumn(column, world, anchor.down(), player);
            context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == head,
                    "Cycling back to the native head must restore the legacy profile without a tick");
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(anchor.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(anchor.down(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void actualLargeErydonSupportsRequireFourMatchingProfilesNotOnlyTheSameBlock(TestContext context) {
        if (!hasRealErydon(context)) { context.complete(); return; }
        var world = context.getWorld();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(3, 4, 3));
        var capital = (CapitalBlock) Registries.BLOCK.get(new net.minecraft.util.Identifier("daedalon", "aganite_tuscan_capital"));
        var player = new WarningPlayer(world.getServer(), world);
        for (String form : java.util.List.of("square", "gothic")) {
            Block column = Registries.BLOCK.get(new net.minecraft.util.Identifier("erydon", "aganite_column_" + form));
            CapitalTransition head = form.equals("square") ? CapitalTransition.SQUARE : CapitalTransition.GOTHIC;
            CapitalTransition shaft = form.equals("square") ? CapitalTransition.SQUARE_SHAFT : CapitalTransition.GOTHIC_SHAFT;
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                world.setBlockState(anchor.add(x, -2, z), column.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(anchor.add(x, -1, z), column.getDefaultState(), Block.NOTIFY_ALL);
            }
            world.setBlockState(anchor, capital.getDefaultState().with(CapitalBlock.SIZE, CapitalBlock.Size.DOUBLE), Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT) == head, "Four native heads must retain their existing large collar");
            int changed = 0;
            for (int x = 1; x >= 0; x--) for (int z = 1; z >= 0; z--) {
                BlockPos support = anchor.add(x, -1, z);
                for (int click = 0; click < 3 && !named(world.getBlockState(support), "capital").equals("none"); click++)
                    clickColumn(column, world, support, player);
                changed++;
                context.assertTrue(world.getBlockState(anchor).get(CapitalBlock.SUPPORT)
                                == (changed == 4 ? shaft : CapitalTransition.NONE),
                        "Every helper must refresh profile agreement when a real supporting column changes");
            }
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                context.assertTrue(world.getBlockState(anchor.add(x, -1, z)).isOf(column), "Mixed-profile rejection must not rely on different block identities");
            world.setBlockState(anchor, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                world.setBlockState(anchor.add(x, -1, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(anchor.add(x, -2, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        context.complete();
    }

    private static void clickColumn(Block column, ServerWorld world, BlockPos pos, WarningPlayer player) {
        column.onUse(world.getBlockState(pos), world, pos, player, Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ()), Direction.NORTH, pos, false));
    }

    private static boolean hasRealErydon(TestContext context) {
        boolean loaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("erydon");
        context.assertTrue(loaded || !Boolean.getBoolean("daedalon.test.requireErydon"),
                "This integration run requires the real ERYDON companion");
        return loaded;
    }

    private static String named(BlockState state, String propertyName) {
        var property = state.getBlock().getStateManager().getProperty(propertyName);
        Object value = state.getEntries().get(property);
        return value instanceof net.minecraft.util.StringIdentifiable named ? named.asString() : "";
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState state, net.minecraft.state.property.Property<T> property, String value) {
        return state.with(property, property.parse(value).orElseThrow());
    }

    private static BlockState withNamed(BlockState state, String property, String value) {
        return withNamed(state, state.getBlock().getStateManager().getProperty(property), value);
    }

    private record SingleSupportView(net.minecraft.world.BlockView delegate, BlockPos position, BlockState state)
            implements net.minecraft.world.BlockView {
        @Override public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }
        @Override public BlockState getBlockState(BlockPos pos) { return pos.equals(position) ? state : delegate.getBlockState(pos); }
        @Override public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return delegate.getHeight(); }
        @Override public int getBottomY() { return delegate.getBottomY(); }
    }
}
