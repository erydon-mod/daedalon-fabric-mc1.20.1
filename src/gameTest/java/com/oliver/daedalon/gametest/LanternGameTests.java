package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.*;
import com.oliver.daedalon.client.compat.AxiomDecorBillboards;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.DecorMaterial;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.LightType;

public final class LanternGameTests {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void axiomCopyCanTrackVerticalLayersWithoutAWorld(TestContext context) {
        var billboards = new AxiomDecorBillboards();
        for (Block layer : LanternLayerFixture.blocks()) {
            BlockState state = layer.getDefaultState()
                    .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                    .with(Properties.LAYERS, 1);
            // Axiom's copy buffer supplies local coordinates and a state, without a world.
            billboards.update(2, 3, 4, state);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void lightingQueriesNeverReadLanternSupports(TestContext context) {
        net.minecraft.world.BlockView noReads=new net.minecraft.world.BlockView() {
            @Override public BlockState getBlockState(BlockPos pos) { throw new AssertionError("Lighting queried an adjacent chunk"); }
            @Override public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) { throw new AssertionError("Lighting queried a block entity"); }
            @Override public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) { throw new AssertionError("Lighting queried a fluid"); }
            @Override public int getHeight() { return 384; }
            @Override public int getBottomY() { return -64; }
        };
        int checked=0;
        for(var style:LanternBlock.Style.values()) for(BlockState state:lantern(style).getStateManager().getStates()) {
            context.assertTrue(state.getOpacity(noReads,BlockPos.ORIGIN)==0,"Lantern opacity must be independent of its interaction shape");
            context.assertTrue(state.isTransparent(noReads,BlockPos.ORIGIN),"Lantern transparency must not inspect neighbouring chunks");
            context.assertTrue(state.getCullingShape(noReads,BlockPos.ORIGIN).isEmpty(),"Lanterns must not cull neighbouring faces");
            checked++;
        }
        context.assertTrue(checked==576,"Every style, mount, size and light state needs a safe lighting path");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void backgroundShapeQueriesNeverWaitForServerChunkWork(TestContext context) {
        var world=context.getWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(2,3,2)),owner=support.south();
        BlockState layer=LanternLayerFixture.SOLID.getDefaultState()
                .with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,Direction.NORTH)
                .with(net.minecraft.state.property.Properties.LAYERS,1);
        world.setBlockState(support,layer,Block.NOTIFY_ALL);
        BlockState lamp=lantern(LanternBlock.Style.GOTHIC).getDefaultState()
                .with(LanternBlock.MOUNT,LanternBlock.Mount.WALL).with(LanternBlock.FACING,Direction.SOUTH);
        var expected=LanternSupport.offset(world,owner,lamp);
        context.assertTrue(Math.abs(expected.z+.875)<1e-7,"Server-thread geometry lost the loaded eighth-layer support");
        BlockPos unavailableSupport=support.add(1_000_000,0,1_000_000);
        int chunkX=unavailableSupport.getX()>>4,chunkZ=unavailableSupport.getZ()>>4;
        context.assertTrue(world.getChunkManager().getWorldChunk(chunkX,chunkZ)==null,"Unavailable-chunk fixture was unexpectedly loaded");
        context.assertTrue(LanternSupport.offset(world,unavailableSupport.south(),lamp).equals(Vec3d.ZERO),"Unavailable support must use the ordinary wall offset");
        context.assertTrue(world.getChunkManager().getWorldChunk(chunkX,chunkZ)==null,"A support-shape query loaded an unavailable chunk");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
            Thread thread=new Thread(task,"Daedalon lighting-shape regression");thread.setDaemon(true);return thread;
        });
        try {
            var result=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                if(!LanternSupport.offset(world,owner,lamp).equals(Vec3d.ZERO)) return false;
                if(lamp.getOutlineShape(world,owner).isEmpty()) return false;
                if(lamp.getCollisionShape(world,owner).isEmpty()) return false;
                var part=LanternPartBlock.withCellYOffset(ModBlocks.lanternPart().getDefaultState(),1);
                return LanternPartBlock.resolveAnchorPos(world,owner.up(),part)==null
                        && part.getOutlineShape(world,owner.up()).isEmpty();
            },executor);
            // The server deliberately waits here: a worker must use the get-now
            // fallback rather than schedule chunk work back onto this thread.
            context.assertTrue(result.get(3,java.util.concurrent.TimeUnit.SECONDS),"Background shape query did not use the safe cached fallback");
        } catch(Exception exception) {
            throw new AssertionError("Background lantern shapes waited for server chunk work",exception);
        } finally { executor.shutdownNow(); }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void allStonesAndAgedFinishesShareTheirStyleAndOnlyPublicControls(TestContext context) {
        int count=0;
        for (var style:LanternBlock.Style.values()) for (var material:DecorMaterial.values()) for (boolean aged:new boolean[]{false,true}) {
            Block block=Registries.BLOCK.get(new Identifier("daedalon",material.id()+(aged?"_aged":"")+"_"+style.id()+"_lantern"));
            context.assertTrue(block instanceof LanternBlock,"Missing lantern finish");
            context.assertTrue(((LanternBlock)block).style()==style,"Style profile must be shared by all stone variants");
            context.assertTrue(block.getDefaultState().get(LanternBlock.SIZE)==LanternBlock.Size.LARGE,"Default body must be one metre high");
            context.assertTrue(block.getDefaultState().get(LanternBlock.LIT),"New lanterns must be on by default");
            context.assertTrue(block.getDefaultState().get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.FULL,"New lanterns must default to full brightness");
            context.assertTrue(block.getStateManager().getStates().size()==192,"On/off and two brightness settings must not introduce chain-length states");
            context.assertTrue(DaedalonDebugProperties.ordered(block.getDefaultState()).stream().map(p->p.getName()).toList().equals(java.util.List.of("size","mount","facing","light")),"Mount is editable while canopy stays automatic; one light control cycles Low/Bright/Off");
            for (BlockState state:block.getStateManager().getStates()) context.assertTrue(state.getLuminance()==(state.get(LanternBlock.LIT)?state.get(LanternBlock.BRIGHTNESS).lightLevel:0),"Every stone, size and mount must emit level15/10 only while on");
            count++;
        }
        context.assertTrue(count==162,"Three designs must cover 27 stone and 27 aged finishes each");
        context.assertTrue(ModBlocks.lanternPart().asItem()==net.minecraft.item.Items.AIR && ModBlocks.lanternChain().asItem()==net.minecraft.item.Items.AIR,"Helper cells must not create additional items");
        for (Block helper:new Block[]{ModBlocks.lanternPart(),ModBlocks.lanternChain()}) for (BlockState state:helper.getStateManager().getStates()) context.assertTrue(!state.contains(LanternBlock.LIT) && !state.contains(LanternBlock.BRIGHTNESS) && state.getLuminance()==0,"Suspension and interaction cells must never become extra light sources");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void supportFaceWinsOverPlayerYawPitchAndHitPosition(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(5,8,5));
        world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        for (var style:LanternBlock.Style.values()) {
            LanternBlock block=lantern(style);
            for (var size:LanternBlock.Size.values()) for (Direction side:Direction.values()) for (float yaw:new float[]{0,90,180,270}) for (float pitch:new float[]{-89,89}) {
                ItemStack stack=block.asItem().getDefaultStack();stack.getOrCreateSubNbt("BlockStateTag").putString("size",size.asString());
                player.setStackInHand(Hand.MAIN_HAND,stack);player.setYaw(yaw);player.setPitch(pitch);
                var hit=new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(side.getVector()).multiply(.5)),side,support,false);
                var state=block.getPlacementState(new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit)));
                context.assertTrue(state!=null,"Placement unexpectedly failed");
                context.assertTrue(state.get(LanternBlock.MOUNT)==LanternBlock.mountForSide(side),"Player look changed clicked-face mounting");
                context.assertTrue(state.get(LanternBlock.FACING)==LanternBlock.facingForSide(side),"Player look changed deterministic orientation");
                context.assertTrue(state.get(LanternBlock.SIZE)==size,"Explicit size was lost");
            }
            BlockState sample=block.getDefaultState().with(LanternBlock.SIZE,LanternBlock.Size.SMALL).with(LanternBlock.MOUNT,LanternBlock.Mount.WALL).with(LanternBlock.FACING,Direction.EAST).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW);
            ItemStack picked=block.getPickStack(world,support,sample);var pickedTag=picked.getSubNbt("BlockStateTag");
            context.assertTrue(pickedTag!=null && "small".equals(pickedTag.getString("size")) && "false".equals(pickedTag.getString("lit")) && "low".equals(pickedTag.getString("brightness")) && !pickedTag.contains("mount") && !pickedTag.contains("facing"),"Pick block should preserve size and lighting, while each new clicked face chooses mounting afresh");
            player.setStackInHand(Hand.MAIN_HAND,picked);
            var replaced=block.getPlacementState(new ItemPlacementContext(use(player,support,Direction.UP)));
            context.assertTrue(replaced!=null && replaced.get(LanternBlock.SIZE)==LanternBlock.Size.SMALL && replaced.get(LanternBlock.MOUNT)==LanternBlock.Mount.PIER,"Picked wall lantern did not become a pier lantern on a top face");
            context.assertTrue(!replaced.get(LanternBlock.LIT),"Picked off lantern unexpectedly switched itself on");
            context.assertTrue(replaced.get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.LOW,"Picked lantern lost its low brightness setting");
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void largeHangingBodyHasRealLowerSelectionAndOneOwner(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(12,12,12));
        LanternBlock block=lantern(LanternBlock.Style.GOTHIC);
        BlockState state=block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING);
        world.setBlockState(anchor,state,Block.NOTIFY_ALL);
        BlockPos part=anchor.down();BlockState lower=world.getBlockState(part);
        context.assertTrue(lower.getBlock() instanceof LanternPartBlock,"Large hanging body requires its lower interaction cell");
        context.assertTrue(lower.get(LanternPartBlock.OFFSET_Y)==0 && LanternPartBlock.cellYOffset(lower)==-1,"Default encoded zero must preserve legacy lower-part saves");
        context.assertTrue(LanternPartBlock.resolveAnchorPos(world,part,lower).equals(anchor),"Lower half must resolve the original owner");
        context.assertTrue(world.getBlockEntity(anchor)==null && world.getBlockEntity(part)==null,"Lanterns and parts need no ticking block entity");
        Vec3d start=Vec3d.of(anchor).add(.5,-2,.5),end=Vec3d.of(anchor).add(.5,1,.5);
        var hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        context.assertTrue(hit.getBlockPos().equals(part),"Raycast must hit the exposed lower body, not miss below its anchor");
        context.assertTrue(lower.getBlock().getPickStack(world,part,lower).isOf(block.asItem()),"Picking the lower body must return its material lantern");
        context.assertTrue(LanternPlacement.assemblyCells(world,part).size()==2,"Hanging helper must belong to exactly one owner");
        context.assertTrue(DecorAssemblySelection.cells(world,part).isEmpty(),"Lanterns must retain ordinary mixed-block Axiom box selection");
        lower.getBlock().onBreak(world,part,lower,player);world.removeBlock(part,false);
        context.assertTrue(world.getBlockState(anchor).isAir() && world.getBlockState(part).isAir(),"Breaking the lower half must remove the complete lantern");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void repeatedBottomClicksExtendChainWithoutDuplicatingLanternsOrSpendingItems(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        BlockPos top=context.getAbsolutePos(new BlockPos(22,18,22));
        LanternBlock block=lantern(LanternBlock.Style.BYZANTINE);
        for (var size:LanternBlock.Size.values()) {
            BlockState state=block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.SIZE,size).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW);
            world.setBlockState(top,state,Block.NOTIFY_ALL);
            ItemStack stack=new ItemStack(lantern(LanternBlock.Style.GEORGIAN).asItem(),4);player.setStackInHand(Hand.MAIN_HAND,stack);
            BlockPos owner=top;
            for (int i=1;i<=3;i++) {
                BlockPos hit=owner.down();
                var result=stack.useOnBlock(use(player,hit,Direction.DOWN));
                context.assertTrue(result.isAccepted(),"Bottom click did not extend chain");
                owner=owner.down();BlockState lowered=world.getBlockState(owner);
                context.assertTrue(lowered.isOf(block) && lowered.get(LanternBlock.SIZE)==size,"Extension changed or duplicated the original lantern style/size");
                context.assertTrue(!lowered.get(LanternBlock.CANOPY),"Moved body must leave its ceiling rosette at the original ceiling");
                context.assertTrue(!lowered.get(LanternBlock.LIT) && lowered.getLuminance()==0,"Chain extension must retain the lantern's off state");
                context.assertTrue(lowered.get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.LOW,"Chain extension must retain the lantern's brightness setting");
                context.assertTrue(stack.getCount()==4,"A chain-length adjustment must not consume a second lantern");
                context.assertTrue(world.getBlockState(top).get(LanternBlock.CANOPY),"Original ceiling rosette moved down with the body");
                for (int j=1;j<=i;j++) context.assertTrue(world.getBlockState(owner.up(j)).getBlock() instanceof LanternChainBlock,"Chain extension left a gap");
                context.assertTrue(LanternPlacement.assemblyCells(world,top).size()==2+i,"Chain edit traversal omitted a hanging assembly cell");
            }
            world.removeBlock(owner,false);
            for (int i=0;i<=4;i++) context.assertTrue(world.getBlockState(top.down(i)).isAir(),"Removal left a chain or helper behind");
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void sneakBottomClickAddsASeparateLanternAndConsumesOneItem(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);player.setSneaking(true);
        BlockPos anchor=context.getAbsolutePos(new BlockPos(32,18,32));
        LanternBlock block=lantern(LanternBlock.Style.GEORGIAN);
        for (var size:LanternBlock.Size.values()) {
            BlockState state=block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.SIZE,size);
            world.setBlockState(anchor,state,Block.NOTIFY_ALL);
            ItemStack stack=new ItemStack(block.asItem(),3);stack.getOrCreateSubNbt("BlockStateTag").putString("size",size.asString());
            player.setStackInHand(Hand.MAIN_HAND,stack);
            BlockPos hit=anchor.down();
            var result=stack.useOnBlock(use(player,hit,Direction.DOWN));
            BlockPos added=anchor.down(2);
            context.assertTrue(result.isAccepted() && world.getBlockState(anchor)==state,"Sneaking must retain the upper lantern");
            context.assertTrue(world.getBlockState(added).isOf(block),"Sneaking did not add the requested second lantern");
            context.assertTrue(world.getBlockState(added).get(LanternBlock.MOUNT)==LanternBlock.Mount.HANGING,"Second lantern must hang below its support");
            context.assertTrue(!world.getBlockState(added).get(LanternBlock.CANOPY),"A secondary lantern must not add a ceiling rosette in midair");
            context.assertTrue(stack.getCount()==2,"Only the actual additional lantern should consume an item");
            world.removeBlock(added,false);world.removeBlock(anchor,false);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void blockedExtensionsAndLargePlacementNeverOverwriteBuilds(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(42,18,42));
        LanternBlock block=lantern(LanternBlock.Style.GOTHIC);
        BlockState state=block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING);
        world.setBlockState(anchor.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        context.assertTrue(!LanternBlock.canOccupy(world,anchor,state),"Large placement must reject an occupied lower body cell");
        world.removeBlock(anchor.down(),false);world.setBlockState(anchor,state,Block.NOTIFY_ALL);
        world.setBlockState(anchor.down(2),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        ItemStack stack=block.asItem().getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stack);
        var result=stack.useOnBlock(use(player,anchor.down(),Direction.DOWN));
        context.assertTrue(!result.isAccepted(),"Blocked chain extension must fail");
        context.assertTrue(world.getBlockState(anchor)==state,"Blocked edit moved its owner");
        context.assertTrue(world.getBlockState(anchor.down()).getBlock() instanceof LanternPartBlock,"Blocked edit removed its original lower body");
        context.assertTrue(world.getBlockState(anchor.down(2)).isOf(Blocks.STONE),"Blocked edit overwrote a neighbouring build");
        world.removeBlock(anchor,false);world.removeBlock(anchor.down(2),false);
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void debugStickTargetsOwnerFromChainAndRejectsBlockedExpansion(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        BlockPos top=context.getAbsolutePos(new BlockPos(52,18,52));
        LanternBlock block=lantern(LanternBlock.Style.BYZANTINE);
        world.setBlockState(top,block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.SIZE,LanternBlock.Size.SMALL).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW),Block.NOTIFY_ALL);
        ItemStack lantern=block.asItem().getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,lantern);
        lantern.useOnBlock(use(player,top,Direction.DOWN));
        BlockPos owner=top.down(),obstacle=owner.down();
        world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        ItemStack stick=net.minecraft.item.Items.DEBUG_STICK.getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stick);
        stick.useOnBlock(use(player,top,Direction.DOWN));
        context.assertTrue(world.getBlockState(owner).get(LanternBlock.SIZE)==LanternBlock.Size.SMALL,"Blocked resize must preserve its old size");
        context.assertTrue(world.getBlockState(obstacle).isOf(Blocks.STONE),"Debug resize overwrote the lower neighbour");
        world.removeBlock(obstacle,false);
        stick.useOnBlock(use(player,top,Direction.DOWN));
        context.assertTrue(world.getBlockState(owner).get(LanternBlock.SIZE)==LanternBlock.Size.LARGE,"Chain click must resize its actual lantern owner");
        context.assertTrue(world.getBlockState(obstacle).getBlock() instanceof LanternPartBlock,"Large resize must add its lower interaction cell");
        context.assertTrue(world.getBlockState(top).get(LanternBlock.SIZE)==LanternBlock.Size.LARGE,"Extension stock must follow the resized lantern");
        stick.useOnBlock(use(player,top,Direction.DOWN));
        context.assertTrue(world.getBlockState(owner).get(LanternBlock.SIZE)==LanternBlock.Size.SMALL && world.getBlockState(obstacle).getBlock() instanceof LanternPartBlock,"The enlarged Small body still needs its lower interaction cell");
        context.assertTrue(world.getBlockState(top).get(LanternBlock.SIZE)==LanternBlock.Size.SMALL,"Shrinking must update the suspended chain stock");
        context.assertTrue(!world.getBlockState(owner).get(LanternBlock.LIT) && world.getBlockState(owner).get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.LOW,"Debug size changes must retain both lighting settings");
        world.removeBlock(owner,false);
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void paletteReplacementPreservesSuspensionAcrossStoneAndDesignChanges(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos top=context.getAbsolutePos(new BlockPos(62,18,62));
        LanternBlock first=lantern(LanternBlock.Style.BYZANTINE);
        world.setBlockState(top,first.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW),Block.NOTIFY_ALL);
        ItemStack held=first.asItem().getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,held);
        held.useOnBlock(use(player,top.down(),Direction.DOWN));
        held.useOnBlock(use(player,top.down(2),Direction.DOWN));
        BlockPos owner=top.down(2);
        var second=(LanternBlock)Registries.BLOCK.get(new Identifier("daedalon","glacium_aged_gothic_lantern"));
        BlockState next=second.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.CANOPY,false).with(LanternBlock.FACING,Direction.WEST).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW);
        world.setBlockState(owner,next,Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(owner)==next,"Palette replacement lost the requested design/material state");
        context.assertTrue(!world.getBlockState(owner).get(LanternBlock.LIT) && world.getBlockState(owner).get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.LOW,"Palette replacement lost the copied lighting settings");
        context.assertTrue(world.getBlockState(owner.down()).getBlock() instanceof LanternPartBlock,"Palette replacement removed the retained lower body cell");
        context.assertTrue(LanternPartBlock.resolveAnchorPos(world,owner.down(),world.getBlockState(owner.down())).equals(owner),"Replacement lower cell lost its owner");
        context.assertTrue(world.getBlockState(top).getBlock() instanceof LanternChainBlock && world.getBlockState(top.down()).getBlock() instanceof LanternChainBlock,"Palette replacement deleted a retained chain extension");
        context.assertTrue(world.getBlockState(top).get(LanternBlock.CANOPY) && !world.getBlockState(top.down()).get(LanternBlock.CANOPY),"Palette replacement moved the ceiling rosette");
        var third=lantern(LanternBlock.Style.GEORGIAN);
        BlockState small=third.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING).with(LanternBlock.SIZE,LanternBlock.Size.SMALL).with(LanternBlock.CANOPY,false).with(LanternBlock.LIT,false).with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW);
        world.setBlockState(owner,small,Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(owner.down()).getBlock() instanceof LanternPartBlock,"The enlarged Small hanging lantern must retain a lower helper");
        context.assertTrue(world.getBlockState(top).get(LanternBlock.SIZE)==LanternBlock.Size.SMALL && world.getBlockState(top.down()).get(LanternBlock.SIZE)==LanternBlock.Size.SMALL,"Palette replacement failed to update chain stock size");
        world.setBlockState(owner,third.getDefaultState(),Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(top).isAir(),"Changing to a pier mount must release the old upper chain");
        context.assertTrue(world.getBlockState(owner.up()).getBlock() instanceof LanternPartBlock
                && LanternPartBlock.resolveAnchorPos(world,owner.up(),world.getBlockState(owner.up())).equals(owner),"Large pier must replace the old lower chain with its own upper body continuation");
        context.assertTrue(world.getBlockState(owner.down()).isAir(),"Changing to a pier mount must release its old lower hanging continuation");
        context.assertTrue(world.getBlockState(owner).isOf(third) && world.getBlockState(owner).get(LanternBlock.MOUNT)==LanternBlock.Mount.PIER,"Removing obsolete chain cells must preserve the replacement pier lantern");
        world.removeBlock(owner,false);
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void singleLightControlCyclesBrightOffLowAndUpdatesRealBlockLight(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        BlockPos pos=context.getAbsolutePos(new BlockPos(120,40,120));
        LanternBlock block=lantern(LanternBlock.Style.GEORGIAN);
        world.setBlockState(pos,block.getDefaultState(),Block.NOTIFY_ALL);
        ItemStack stick=net.minecraft.item.Items.DEBUG_STICK.getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stick);
        context.runAtTick(5,()->{
            context.assertTrue(world.getLightLevel(LightType.BLOCK,pos)==15 && world.getLightLevel(LightType.BLOCK,pos.up())==14,"Default full lantern must illuminate its surroundings through the vanilla block-light engine");
            stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"light");
            stick.useOnBlock(use(player,pos,Direction.UP));
            context.assertTrue(LanternBlock.light(world.getBlockState(pos))==LanternBlock.Light.OFF,"Bright must cycle to Off");
        });
        context.runAtTick(10,()->{
            context.assertTrue(world.getLightLevel(LightType.BLOCK,pos)==0 && world.getLightLevel(LightType.BLOCK,pos.up())==0,"Off must remove real block light");
            stick.useOnBlock(use(player,pos,Direction.UP));
            context.assertTrue(LanternBlock.light(world.getBlockState(pos))==LanternBlock.Light.LOW,"Off must cycle to Low");
        });
        context.runAtTick(15,()->{
            context.assertTrue(world.getLightLevel(LightType.BLOCK,pos)==10 && world.getLightLevel(LightType.BLOCK,pos.up())==9,"Low must emit real level10 block light");
            stick.useOnBlock(use(player,pos,Direction.UP));
            context.assertTrue(LanternBlock.light(world.getBlockState(pos))==LanternBlock.Light.BRIGHT,"Low must cycle to Bright");
        });
        context.runAtTick(20,()->{
            context.assertTrue(world.getBlockState(pos).get(LanternBlock.LIT) && world.getLightLevel(LightType.BLOCK,pos)==15,"Switching on must restore the selected full brightness");
            world.removeBlock(pos,false);
            context.complete();
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void structuresPreserveMountSizeAndCanopyThroughRotationsAndMirrors(TestContext context) {
        for (var style:LanternBlock.Style.values()) for (BlockState original:lantern(style).getStateManager().getStates()) {
            BlockState rotated=original;
            for (int i=0;i<4;i++) rotated=rotated.rotate(BlockRotation.CLOCKWISE_90);
            context.assertTrue(rotated==original,"Full rotation changed lantern state");
            for (var mirror:new BlockMirror[]{BlockMirror.FRONT_BACK,BlockMirror.LEFT_RIGHT}) context.assertTrue(original.mirror(mirror).mirror(mirror)==original,"Double mirror changed lantern mount/size/canopy");
        }
        context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void actualBlockItemUsePreservesEveryOriginalSupportFaceAndHit(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(context.getAbsolutePos(new BlockPos(1,1,1)).toCenterPos());
        int index=0;
        for(var style:LanternBlock.Style.values()) for(var size:LanternBlock.Size.values()) for(Direction side:Direction.values()) {
            BlockPos support=context.getAbsolutePos(new BlockPos(140+index++*4,16,140));
            world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            LanternBlock block=lantern(style);ItemStack stack=block.asItem().getDefaultStack();
            stack.getOrCreateSubNbt("BlockStateTag").putString("size",size.asString());
            stack.getOrCreateSubNbt("BlockStateTag").putString("mount","pier");
            stack.getOrCreateSubNbt("BlockStateTag").putString("facing","north");
            player.setStackInHand(Hand.MAIN_HAND,stack);player.setYaw(127);player.setPitch(-83);
            Vec3d point=Vec3d.ofCenter(support).add(Vec3d.of(side.getVector()).multiply(.5));
            var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(point,side,support,false)));
            BlockPos owner=support.offset(side);BlockState placed=world.getBlockState(owner);
            context.assertTrue(result.isAccepted() && placed.isOf(block),"BlockItem placement refused a clear plain support face: "+side+" "+size);
            context.assertTrue(placed.get(LanternBlock.MOUNT)==LanternBlock.mountForSide(side) && placed.get(LanternBlock.FACING)==LanternBlock.facingForSide(side),"Original clicked support face was lost by the item placement path");
            context.assertTrue(world.getBlockState(support).isOf(Blocks.STONE),"Placement replaced its support");
            for(int dy:new int[]{-1,1}) context.assertTrue((world.getBlockState(owner.up(dy)).getBlock() instanceof LanternPartBlock)==LanternBlock.needsPart(placed,dy),"Enlarged body helper does not match its actual vertical extent");
            world.removeBlock(owner,false);world.removeBlock(support,false);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void upperPierAndWallHelpersPickAndBreakExactlyOneOwner(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos owner=context.getAbsolutePos(new BlockPos(145,30,145));
        LanternBlock block=lantern(LanternBlock.Style.GEORGIAN);
        for(var mount:new LanternBlock.Mount[]{LanternBlock.Mount.PIER,LanternBlock.Mount.WALL}) {
            BlockState state=block.getDefaultState().with(LanternBlock.MOUNT,mount);
            world.setBlockState(owner,state,Block.NOTIFY_ALL);
            BlockPos upper=owner.up();BlockState part=world.getBlockState(upper);
            context.assertTrue(part.getBlock() instanceof LanternPartBlock && part.get(LanternPartBlock.OFFSET_Y)==1 && LanternPartBlock.cellYOffset(part)==1,"Large pier/wall needs an upper continuation");
            context.assertTrue(LanternPartBlock.resolveAnchorPos(world,upper,part).equals(owner),"Upper continuation resolved the wrong owner");
            context.assertTrue(part.getBlock().getPickStack(world,upper,part).isOf(block.asItem()),"Upper continuation pick lost the lantern item");
            context.assertTrue(LanternPlacement.assemblyCells(world,upper).size()==(mount==LanternBlock.Mount.WALL?3:2),"Physical assembly traversal missed an enlarged continuation");
            context.assertTrue(DecorAssemblySelection.cells(world,upper).isEmpty(),"Upper parts must retain ordinary mixed-block Axiom box selection");
            part.getBlock().onBreak(world,upper,part,player);world.removeBlock(upper,false);
            context.assertTrue(world.getBlockState(owner).isAir() && world.getBlockState(owner.down()).isAir() && world.getBlockState(owner.up()).isAir(),"Breaking an upper continuation left an owner or helper behind");
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void everyThinLayerBroadAndEdgeFaceKeepsFlushCentredGeometryAndRayTargets(TestContext context) {
        // Server-only fixtures represent optional ERYDON collision contracts;
        // their states are used by this bounded view, not a player's world.
        var player=context.createMockCreativeServerPlayerInWorld();
        for(Block fixture:LanternLayerFixture.blocks())
            for(Direction anchored:Direction.Type.HORIZONTAL) for(int count=1;count<=8;count++) for(Direction face:Direction.Type.HORIZONTAL) {
                BlockState support=fixture.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,anchored)
                        .with(net.minecraft.state.property.Properties.LAYERS,count);
                BlockPos owner=BlockPos.ORIGIN.offset(face);
                BlockState lamp=lantern(LanternBlock.Style.GOTHIC).getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.WALL)
                        .with(LanternBlock.FACING,face).with(LanternBlock.SIZE,LanternBlock.Size.SMALL);
                net.minecraft.world.BlockView view=new net.minecraft.world.BlockView() {
                    @Override public BlockState getBlockState(BlockPos pos) { return pos.equals(BlockPos.ORIGIN)?support:pos.equals(owner)?lamp:Blocks.AIR.getDefaultState(); }
                    @Override public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return null; }
                    @Override public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
                    @Override public int getHeight() { return 384; }
                    @Override public int getBottomY() { return -64; }
                };
                var offset=LanternSupport.offset(view,owner,lamp);
                var supportBox=support.getCollisionShape(view,BlockPos.ORIGIN).getBoundingBox();
                var body=LanternGeometry.fullShape(LanternBlock.Style.GOTHIC,LanternBlock.Mount.WALL,LanternBlock.Size.SMALL,face,false)
                        .offset(owner.getX()+offset.x,offset.y,owner.getZ()+offset.z).getBoundingBox();
                Direction.Axis axis=face.getAxis();
                double rear=face.getDirection()==Direction.AxisDirection.POSITIVE?body.getMin(axis):body.getMax(axis);
                double plane=face.getDirection()==Direction.AxisDirection.POSITIVE?supportBox.getMax(axis):supportBox.getMin(axis);
                context.assertTrue(Math.abs(rear-plane)<1e-7,"Wall body left the actual thin support plane");
                Direction.Axis across=axis==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
                context.assertTrue(Math.abs((body.getMin(across)+body.getMax(across))-(supportBox.getMin(across)+supportBox.getMax(across)))<1e-7,"Thin exposed edge did not centre its wall lantern");
                Vec3d middle=body.getCenter(),normal=Vec3d.of(face.getVector());
                for(double portion:new double[]{-.35,0,.35}) {
                    double travel=(body.getMax(across)-body.getMin(across))*portion;
                    Vec3d aim=middle.add(across==Direction.Axis.X?travel:0,0,across==Direction.Axis.Z?travel:0);
                    var hit=view.raycast(new RaycastContext(aim.add(normal.multiply(2)),aim.subtract(normal.multiply(.1)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                    context.assertTrue(hit.getBlockPos().equals(owner),"Recessed or transverse wall geometry lost its real owner ray target");
                }
            }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void translatedOwnerAndHelperRaysCannotSkipAnOpaqueDdaCell(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var fixture=LanternLayerFixture.SOLID;
        BlockState support=fixture.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,Direction.NORTH)
                .with(net.minecraft.state.property.Properties.LAYERS,1);
        BlockPos owner=new BlockPos(1,0,0);
        for(var style:LanternBlock.Style.values()) for(var size:LanternBlock.Size.values()) {
            BlockState lamp=lantern(style).getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.WALL)
                    .with(LanternBlock.FACING,Direction.EAST).with(LanternBlock.SIZE,size);
            java.util.Map<BlockPos,BlockState> states=wallRayFixture(owner,lamp,support);
            var view=lanternCollisionView(context,states);
            for(int dy:new int[]{-1,0,1}) {
                if(dy!=0 && !LanternBlock.needsPart(lamp,dy)) continue;
                BlockPos cell=owner.up(dy);BlockState state=states.get(cell);
                var bounds=state.getOutlineShape(view,cell).getBoundingBox().offset(cell);
                context.assertTrue(bounds.minZ<0,"Negative control no longer overhangs its owner cell");
                double y=(bounds.minY+bounds.maxY)/2;
                Vec3d start=new Vec3d(bounds.maxX+.21,y,-5*bounds.minZ);
                Vec3d end=new Vec3d(bounds.maxX-.01,y,bounds.minZ/2);
                var raw=state.getOutlineShape(view,cell).raycast(start,end,cell);
                context.assertTrue(raw!=null && raw.getPos().z<0,"Negative control does not reproduce vanilla's out-of-cell hit");
                context.assertTrue(LanternRaycast.closest(view,start,end,cell,state,raw)==null,
                        "Translated owner/helper ended ray traversal before its hit's DDA cell");
                BlockPos opaque=cell.north();states.put(opaque,Blocks.STONE.getDefaultState());
                var hit=view.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,player));
                context.assertTrue(hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(opaque),
                        "Translated lantern outline tunnelled through the opaque intervening cell");
                states.remove(opaque);
            }
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void actualCollisionIteratorFindsLayerMountedOwnersAndVerticalHelpers(TestContext context) {
        for(Block fixture:LanternLayerFixture.blocks())
            for(var style:LanternBlock.Style.values()) for(var size:LanternBlock.Size.values())
                for(Direction anchored:Direction.Type.HORIZONTAL) for(int count=1;count<=8;count++)
                    for(Direction face:Direction.Type.HORIZONTAL) {
                        BlockState support=fixture.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,anchored)
                                .with(net.minecraft.state.property.Properties.LAYERS,count);
                        BlockPos owner=BlockPos.ORIGIN.offset(face);
                        BlockState lamp=lantern(style).getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.WALL)
                                .with(LanternBlock.FACING,face).with(LanternBlock.SIZE,size);
                        var states=wallRayFixture(owner,lamp,support);
                        var view=lanternCollisionView(context,states);
                        for(int dy:new int[]{-1,0,1}) {
                            if(dy!=0 && !LanternBlock.needsPart(lamp,dy)) continue;
                            BlockPos cell=owner.up(dy);BlockState state=states.get(cell);
                            context.assertTrue(state.exceedsCube(),"Lantern owner/helper lost dynamic overhang bounds");
                            var box=state.getCollisionShape(view,cell).getBoundingBox().offset(cell);
                            for(double fx:new double[]{.1,.9}) for(double fz:new double[]{.1,.9}) {
                                double x=box.minX+(box.maxX-box.minX)*fx,z=box.minZ+(box.maxZ-box.minZ)*fz,y=(box.minY+box.maxY)/2;
                                var query=new net.minecraft.util.math.Box(x-.0001,y-.0001,z-.0001,x+.0001,y+.0001,z+.0001);
                                var collisions=new net.minecraft.world.BlockCollisionSpliterator<BlockPos>(view,null,query,false,(pos,shape)->pos.toImmutable());
                                boolean found=false;while(collisions.hasNext()) found|=collisions.next().equals(cell);
                                context.assertTrue(found,"Actual collision iterator skipped a translated lantern owner/helper: "+style+" "+size+" "+face+" "+count);
                            }
                        }
                    }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void debugMountCycleRetainsLightAndFacingAcrossEveryDesignAndSize(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        ItemStack stick=net.minecraft.item.Items.DEBUG_STICK.getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stick);
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"mount");
        int sample=0;
        for(var style:LanternBlock.Style.values()) for(var size:LanternBlock.Size.values()) {
            BlockPos owner=context.getAbsolutePos(new BlockPos(200+sample++*4,40,200));
            LanternBlock block=lantern(style);
            world.setBlockState(owner,block.getDefaultState().with(LanternBlock.SIZE,size)
                    .with(LanternBlock.FACING,Direction.WEST).with(LanternBlock.LIT,false)
                    .with(LanternBlock.BRIGHTNESS,LanternBlock.Brightness.LOW),Block.NOTIFY_ALL);
            for(var mount:new LanternBlock.Mount[]{LanternBlock.Mount.WALL,LanternBlock.Mount.HANGING,LanternBlock.Mount.PIER}) {
                stick.useOnBlock(use(player,owner,Direction.UP));
                BlockState selected=world.getBlockState(owner);
                context.assertTrue(selected.get(LanternBlock.MOUNT)==mount,"Debug mount must cycle seated, wall, hanging for every design and size");
                context.assertTrue(selected.get(LanternBlock.SIZE)==size && selected.get(LanternBlock.FACING)==Direction.WEST
                        && !selected.get(LanternBlock.LIT) && selected.get(LanternBlock.BRIGHTNESS)==LanternBlock.Brightness.LOW,
                        "Changing mount must preserve size, facing and both saved lighting settings");
                for(int dy:new int[]{-1,1}) {
                    BlockPos part=owner.up(dy);BlockState found=world.getBlockState(part);
                    context.assertTrue(LanternBlock.needsPart(selected,dy)
                                    ? found.getBlock() instanceof LanternPartBlock && owner.equals(LanternPartBlock.resolveAnchorPos(world,part,found))
                                    : found.isAir(),"Mount cycle left a missing, stale or incorrectly owned body helper");
                }
                world.setBlockState(owner.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                world.removeBlock(owner.east(),false);
                context.assertTrue(world.getBlockState(owner)==selected,"Neighbour changes must preserve the manually selected mount");
            }
            world.removeBlock(owner,false);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void debugMountReplacesItsOwnSuspensionButRejectsOccupiedBodyCells(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        BlockPos top=context.getAbsolutePos(new BlockPos(230,40,210));
        LanternBlock block=lantern(LanternBlock.Style.GOTHIC);
        world.setBlockState(top,block.getDefaultState().with(LanternBlock.MOUNT,LanternBlock.Mount.HANGING),Block.NOTIFY_ALL);
        ItemStack item=block.asItem().getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,item);
        item.useOnBlock(use(player,top,Direction.DOWN));
        item.useOnBlock(use(player,top.down(),Direction.DOWN));
        BlockPos owner=top.down(2);
        ItemStack stick=net.minecraft.item.Items.DEBUG_STICK.getDefaultStack();player.setStackInHand(Hand.MAIN_HAND,stick);
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"mount");
        stick.useOnBlock(use(player,top,Direction.DOWN));
        BlockState seated=world.getBlockState(owner);
        context.assertTrue(seated.getBlock()==block && seated.get(LanternBlock.MOUNT)==LanternBlock.Mount.PIER,
                "A chain hit must change its lantern to seated even when the new upper body replaces its old chain");
        context.assertTrue(world.getBlockState(top).isAir() && world.getBlockState(owner.down()).isAir()
                && owner.equals(LanternPartBlock.resolveAnchorPos(world,owner.up(),world.getBlockState(owner.up()))),
                "Changing away from hanging must release the chain and lower body while preserving the new upper body");
        world.setBlockState(owner.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        stick.useOnBlock(use(player,owner,Direction.UP));
        context.assertTrue(world.getBlockState(owner)==seated && world.getBlockState(owner.down()).isOf(Blocks.STONE),
                "A blocked wall mount must preserve its original state and the neighbouring build");
        world.removeBlock(owner.down(),false);
        stick.useOnBlock(use(player,owner,Direction.UP));
        context.assertTrue(world.getBlockState(owner).get(LanternBlock.MOUNT)==LanternBlock.Mount.WALL,"Unblocked wall mount did not apply");
        stick.useOnBlock(use(player,owner,Direction.UP));
        context.assertTrue(world.getBlockState(owner).get(LanternBlock.MOUNT)==LanternBlock.Mount.HANGING
                && world.getBlockState(owner).get(LanternBlock.CANOPY),"A fresh manually selected suspension must restore its ceiling rosette");
        world.removeBlock(owner,false);
        context.complete();
    }

    private static java.util.Map<BlockPos,BlockState> wallRayFixture(BlockPos owner,BlockState lamp,BlockState support) {
        java.util.Map<BlockPos,BlockState> states=new java.util.HashMap<>();
        states.put(BlockPos.ORIGIN,support);states.put(owner,lamp);
        for(int dy:new int[]{-1,1}) if(LanternBlock.needsPart(lamp,dy))
            states.put(owner.up(dy),LanternPartBlock.withCellYOffset(ModBlocks.lanternPart().getDefaultState(),dy));
        return states;
    }

    private static net.minecraft.world.CollisionView lanternCollisionView(TestContext context,java.util.Map<BlockPos,BlockState> states) {
        return (net.minecraft.world.CollisionView)java.lang.reflect.Proxy.newProxyInstance(
                net.minecraft.world.CollisionView.class.getClassLoader(),new Class[]{net.minecraft.world.CollisionView.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "getBlockState"->states.getOrDefault(args[0],Blocks.AIR.getDefaultState());
                    case "getFluidState"->states.getOrDefault(args[0],Blocks.AIR.getDefaultState()).getFluidState();
                    case "getBlockEntity"->null;
                    case "getChunkAsView"->proxy;
                    case "getWorldBorder"->context.getWorld().getWorldBorder();
                    case "getEntityCollisions"->java.util.List.of();
                    case "getHeight"->384;
                    case "getBottomY"->-64;
                    case "hashCode"->System.identityHashCode(proxy);
                    case "equals"->proxy==args[0];
                    case "toString"->"LanternCollisionFixture";
                    default->{
                        if(method.isDefault()) yield java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                        throw new UnsupportedOperationException(method.toString());
                    }
                });
    }

    private static LanternBlock lantern(LanternBlock.Style style) { return (LanternBlock)Registries.BLOCK.get(new Identifier("daedalon","aganite_"+style.id()+"_lantern")); }
    private static ItemUsageContext use(ServerPlayerEntity player,BlockPos hit,Direction side) {
        return new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(hit),side,hit,false));
    }
}
