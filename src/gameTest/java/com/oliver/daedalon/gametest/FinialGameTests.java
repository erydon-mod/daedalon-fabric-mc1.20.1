package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.DecorPreviewBounds;
import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FinialSupport;
import com.oliver.daedalon.block.FixedDecorBlock;
import com.oliver.daedalon.block.FinialSupportPlacement;
import com.oliver.daedalon.block.FinialRaycast;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
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
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.mojang.authlib.GameProfile;

public final class FinialGameTests {
    private static final class WarningPlayer extends ServerPlayerEntity {
        private ItemStack held=ItemStack.EMPTY;
        private Text message;
        WarningPlayer(MinecraftServer server,ServerWorld world) {
            super(server,world,new GameProfile(java.util.UUID.randomUUID(),"finial-warning"));
        }
        @Override public boolean isCreativeLevelTwoOp() { return true; }
        @Override public boolean isSneaking() { return false; }
        @Override public ItemStack getStackInHand(Hand hand) { return held==null ? ItemStack.EMPTY : held; }
        @Override public void sendMessage(Text message,boolean overlay) { this.message=message; }
        @Override public void sendMessageToClient(Text message,boolean overlay) { this.message=message; }
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=600)
    public void allFinialMaterialsKeepUprightBoundsAndTransformTheirSavedFits(TestContext context) {
        int blocks=0,cases=0;
        for(var block:Registries.BLOCK) {
            if(!(block instanceof FinialBlock finial)) continue;
            blocks++;
            context.assertTrue(DaedalonDebugProperties.ordered(block.getStateManager().getProperties()).stream()
                    .allMatch(property -> property==TwoSizeDecorBlock.SIZE),"Automatic finial metadata leaked into debug controls");
            for(var size:TwoSizeDecorBlock.Size.values()) for(var profile:FinialSupport.Profile.values())
                for(Direction facing:Direction.Type.HORIZONTAL) {
                    var state=block.getDefaultState().with(TwoSizeDecorBlock.SIZE,size).with(FinialBlock.SUPPORT,profile).with(FinialBlock.FACING,facing);
                    var fit=FinialSupport.fit(state,finial.style());
                    var shape=state.getCollisionShape(context.getWorld(),BlockPos.ORIGIN,ShapeContext.absent());
                    var box=shape.getBoundingBox();
                    var particles=finial.getBreakParticleShape(state);
                    if(fit.mounted()) {
                        context.assertTrue(particles.getBoundingBoxes().size()==1 && particleCount(particles)<=64,
                                "Fitted base detail must never multiply vanilla break debris");
                    } else context.assertTrue(particles==shape,"Ordinary freestanding finial debris must remain unchanged");
                    context.assertTrue(Math.abs(box.maxY-fit.bodyY()-(size==TwoSizeDecorBlock.Size.SMALL ? 1 : 2))<1e-6,
                            "Finial ornament must retain its upright height: "+state);
                    context.assertTrue(DecorPreviewBounds.bounds(state).equals(box),"Axiom preview bounds differ from saved collision");
                    var rotated=FinialSupport.fit(block.rotate(state,BlockRotation.CLOCKWISE_90),finial.style());
                    context.assertTrue(Math.abs(rotated.centreX()-(1-fit.centreZ()))<1e-6
                            && Math.abs(rotated.centreZ()-fit.centreX())<1e-6,"Copied finial fit did not rotate");
                    for(var mirror:new BlockMirror[]{BlockMirror.LEFT_RIGHT,BlockMirror.FRONT_BACK}) {
                        var reflected=FinialSupport.fit(block.mirror(state,mirror),finial.style());
                        context.assertTrue(Math.abs(reflected.centreX()-(mirror==BlockMirror.FRONT_BACK ? 1-fit.centreX() : fit.centreX()))<1e-6
                                && Math.abs(reflected.centreZ()-(mirror==BlockMirror.LEFT_RIGHT ? 1-fit.centreZ() : fit.centreZ()))<1e-6,
                                "Copied finial fit did not mirror: "+state+" / "+mirror);
                    }
                    cases++;
                }
        }
        context.assertTrue(blocks==275 && cases==275*2*FinialSupport.Profile.values().length*4,"Missing material/finish/size coverage");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=600)
    public void realPlacementChoosesEveryCopingProfileAndKeepsTheFittedBaseSelectable(TestContext context) {
        var world=context.getWorld();
        var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(4,3,4)),owner=support.up();
        player.setPosition(Vec3d.ofCenter(owner.add(5,4,5)));
        for(int y=-2;y<=4;y++) for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++)
            world.setBlockState(support.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        for(var style:FixedDecorBlock.Style.values()) {
            if(!style.isFinial()) continue;
            var block=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_"+style.idSuffix()));
            for(var size:TwoSizeDecorBlock.Size.values()) for(var surface:FinialCopingFixture.Surface.values())
                for(Direction facing:Direction.Type.HORIZONTAL) for(boolean offset:new boolean[]{false,true}) {
                    if(offset && surface!=FinialCopingFixture.Surface.STEEP_UPPER) continue;
                    world.removeBlock(owner,false);
                    var coping=FinialCopingFixture.BLOCK.getDefaultState().with(FinialCopingFixture.SURFACE,surface)
                            .with(FinialCopingFixture.OFFSET,offset).with(Properties.HORIZONTAL_FACING,facing);
                    world.setBlockState(support,coping,Block.NOTIFY_ALL);
                    var stack=block.asItem().getDefaultStack();
                    stack.getOrCreateSubNbt("BlockStateTag").putString("size",size.asString());
                    player.setStackInHand(Hand.MAIN_HAND,stack);
                    var hit=new BlockHitResult(Vec3d.ofCenter(support),Direction.NORTH,support,false);
                    var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
                    var placed=world.getBlockState(owner);
                    context.assertTrue(result.isAccepted() && placed.isOf(block),"Side hit must place the finial above the coping: "+style+"/"+size+"/"+surface+"/"+facing+"/offset="+offset+" result="+result);
                    var expected=offset ? FinialSupport.Profile.COPING_STEEP_UPPER_OFFSET : FinialSupport.Profile.find(surface.asString(),true);
                    context.assertTrue(placed.get(TwoSizeDecorBlock.SIZE)==size
                            && placed.get(FinialBlock.SUPPORT)==expected && placed.get(FinialBlock.FACING)==facing,
                            "Placement lost the coping profile or selected size");
                    var fit=FinialSupport.fit(placed,style);
                    Vec3d top=Vec3d.of(owner).add(fit.centreX(),fit.bodyY()+.01,fit.centreZ());
                    var selected=world.raycastBlock(top.add(0,.05,0),top.add(0,-.1,0),support,
                            coping.getCollisionShape(world,support,ShapeContext.absent()),coping);
                    context.assertTrue(selected!=null && selected.getBlockPos().equals(owner),"Fitted lower base must select its owner");
                    world.removeBlock(support,false);
                    context.assertTrue(world.getBlockState(owner).isOf(block),"An unsupported finial must retain its fitted state");
                }
        }
        world.removeBlock(owner,false);
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void fullFootprintIsCheckedBeforeLargePlacementAndStandalonePlacementStaysAvailable(TestContext context) {
        var world=context.getWorld();
        var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(4,3,4)),owner=support.up();
        var block=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_sphaira_finial"));
        var coping=FinialCopingFixture.BLOCK.getDefaultState().with(FinialCopingFixture.SURFACE,FinialCopingFixture.Surface.FLAT);
        world.setBlockState(support,coping,Block.NOTIFY_ALL);
        world.setBlockState(owner.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        var stack=block.asItem().getDefaultStack();
        stack.getOrCreateSubNbt("BlockStateTag").putString("size","large");
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(support),Direction.UP,support,false);
        context.assertTrue(!stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted()
                && world.getBlockState(owner).isAir() && world.getBlockState(owner.up()).isOf(Blocks.STONE),
                "Blocked Large placement must not leave a partial finial or replace another block");
        world.removeBlock(owner.up(),false);
        context.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"Cleared footprint must accept Large");
        var placed=world.getBlockState(owner);
        context.assertTrue(!FinialSupportPlacement.isClear(placed,world,new BlockPos(owner.getX(),world.getTopY()-1,owner.getZ())),
                "Full Large bounds must respect build height");
        world.removeBlock(owner,false);
        world.setBlockState(support,coping.with(FinialCopingFixture.POST,true),Block.NOTIFY_ALL);
        var small=FinialSupportPlacement.fitted(block.getDefaultState(),world,support);
        context.assertTrue(!FinialSupportPlacement.isClear(small,world,owner),
                "Mounting must reject a post above its top plane inside the support cell");
        world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        context.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted()
                && world.getBlockState(owner).get(FinialBlock.SUPPORT)==FinialSupport.Profile.NONE,
                "Ordinary full-block placement must work without an ERYDON support");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=600)
    public void directSlopePlacementFitsEveryGradientAndTheOccupiedSteepUpperOwner(TestContext context) {
        var world=context.getWorld();
        var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(4,5,4));
        int checked=0;
        for(var style:FixedDecorBlock.Style.values()) {
            if(!style.isFinial()) continue;
            var finial=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_"+style.idSuffix()));
            for(var size:TwoSizeDecorBlock.Size.values()) for(var entry:FinialCopingFixture.SLOPES.entrySet())
                for(Direction facing:Direction.Type.HORIZONTAL) for(boolean offset:new boolean[]{false,true}) {
                    if(offset && entry.getKey()!=FinialSupport.Profile.STEEP_UPPER) continue;
                    var slope=entry.getValue().getDefaultState().with(Properties.BLOCK_HALF,BlockHalf.BOTTOM)
                            .with(Properties.HORIZONTAL_FACING,facing);
                    world.setBlockState(support,slope,Block.NOTIFY_ALL);
                    BlockPos owner=offset ? support.up().offset(facing) : support.up();
                    if(offset) world.setBlockState(support.up(),FinialCopingFixture.SLOPES.get(FinialSupport.Profile.STEEP_LOWER)
                            .getDefaultState().with(Properties.BLOCK_HALF,BlockHalf.BOTTOM).with(Properties.HORIZONTAL_FACING,facing),Block.NOTIFY_ALL);
                    var stack=finial.asItem().getDefaultStack();
                    stack.getOrCreateSubNbt("BlockStateTag").putString("size",size.asString());
                    player.setStackInHand(Hand.MAIN_HAND,stack);
                    var hit=new BlockHitResult(Vec3d.ofCenter(support),Direction.NORTH,support,false);
                    context.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),
                            "Direct slope fallback must place from a side hit");
                    var placed=world.getBlockState(owner);
                    context.assertTrue(placed.isOf(finial) && placed.get(TwoSizeDecorBlock.SIZE)==size
                                    && placed.get(FinialBlock.SUPPORT)==(offset ? FinialSupport.Profile.STEEP_UPPER_OFFSET : entry.getKey())
                                    && placed.get(FinialBlock.FACING)==facing,"Direct slope placement lost its fit or steep owner offset");
                    world.removeBlock(owner,false);
                    world.removeBlock(support.up(),false);
                    world.removeBlock(support,false);
                    checked++;
                }
        }
        context.assertTrue(checked==240,"Cover all five styles, both sizes, three gradients/positions and four facings");
        var finial=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_sphaira_finial"));
        world.setBlockState(support,FinialCopingFixture.corner(FinialCopingFixture.SLOPES.get(FinialSupport.Profile.SLOPE)),Block.NOTIFY_ALL);
        context.assertTrue(FinialSupportPlacement.fitted(finial.getDefaultState(),world,support).get(FinialBlock.SUPPORT)==FinialSupport.Profile.NONE,
                "A non-planar slope corner must never be assigned a straight inclined base");
        world.removeBlock(support,false);
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void blockedDebugStickGrowthWarnsAndKeepsTheCompleteFinialFootprint(TestContext context) {
        var world=context.getWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(4,3,4)),owner=support.up();
        var player=new WarningPlayer(world.getServer(),world);
        var stick=Items.DEBUG_STICK.getDefaultStack();
        stick.getOrCreateNbt().putString(DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,"size");
        player.held=stick;
        var usage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(owner),Direction.UP,owner,false));
        world.setBlockState(support,FinialCopingFixture.BLOCK.getDefaultState().with(FinialCopingFixture.SURFACE,FinialCopingFixture.Surface.FLAT),Block.NOTIFY_ALL);
        int checked=0;
        for(var style:FixedDecorBlock.Style.values()) {
            if(!style.isFinial()) continue;
            var finial=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_"+style.idSuffix()));
            var small=FinialSupportPlacement.fitted(finial.getDefaultState(),world,support);
            world.setBlockState(owner,small,Block.NOTIFY_ALL);
            world.setBlockState(owner.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            player.message=null;
            context.assertTrue(Items.DEBUG_STICK.useOnBlock(usage).isAccepted(),"Blocked finial edit must be handled");
            context.assertTrue(player.message!=null && player.message.getContent() instanceof TranslatableTextContent text
                            && text.getKey().equals("message.daedalon.finial_size_blocked"),"Blocked growth must warn with the localized finial message");
            context.assertTrue(world.getBlockState(owner)==small && world.getBlockState(owner.up()).isOf(Blocks.STONE),
                    "Blocked growth must preserve the finial and obstruction");
            world.removeBlock(owner.up(),false);
            player.message=null;
            Items.DEBUG_STICK.useOnBlock(usage);
            context.assertTrue(world.getBlockState(owner).get(TwoSizeDecorBlock.SIZE)==TwoSizeDecorBlock.Size.LARGE
                    && !(player.message!=null && player.message.getContent() instanceof TranslatableTextContent text
                    && text.getKey().equals("message.daedalon.finial_size_blocked")),
                    "Cleared footprint must grow using the same selected size control without a blocked-size warning");
            Items.DEBUG_STICK.useOnBlock(usage);
            context.assertTrue(world.getBlockState(owner).get(TwoSizeDecorBlock.SIZE)==TwoSizeDecorBlock.Size.SMALL,
                    "Successful resize must keep cycling");
            world.removeBlock(owner,false);
            checked++;
        }
        context.assertTrue(checked==5,"Cover every finial family");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200)
    public void indexedAirOverhangSurvivesChunkReloadAndClearsOnResizeOrRemoval(TestContext context) {
        var world=context.getWorld();
        var base=context.getAbsolutePos(new BlockPos(4,12,4));
        BlockPos owner=new BlockPos((base.getX()>>4)*16+16,base.getY(),base.getZ());
        var finial=(FinialBlock)Registries.BLOCK.get(new Identifier("daedalon","bronze_sphaira_finial"));
        for(var profile:new FinialSupport.Profile[]{FinialSupport.Profile.COPING_SHALLOW_NARROW_RIGHT,
                FinialSupport.Profile.COPING_STEEP_UPPER_OFFSET}) {
            var large=finial.getDefaultState().with(TwoSizeDecorBlock.SIZE,TwoSizeDecorBlock.Size.LARGE)
                    .with(FinialBlock.SUPPORT,profile).with(FinialBlock.FACING,Direction.EAST);
            world.setBlockState(owner,large,Block.NOTIFY_LISTENERS);
            var fit=FinialSupport.fit(large,finial.style());
            var box=large.getOutlineShape(world,owner).getBoundingBox();
            // A side ray through the extreme AIR overhang (or the lowest steep base cell).
            double localY=profile==FinialSupport.Profile.COPING_STEEP_UPPER_OFFSET ? box.minY+.025 : fit.bodyY()+.5;
            double localX=profile==FinialSupport.Profile.COPING_STEEP_UPPER_OFFSET ? box.maxX-.025 : box.minX+.025;
            double localZ=fit.centreZ();
            Vec3d through=Vec3d.of(owner).add(localX,localY,localZ);
            Vec3d start=through.add(0,0,-2),end=through.add(0,0,2);
            BlockPos cell=BlockPos.ofFloored(through);
            world.setBlockState(cell,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            context.assertTrue(!cell.equals(owner) && (Math.abs(cell.getX()-owner.getX())>=2
                    || owner.getY()-cell.getY()>=3),"Probe must reach past the old fixed neighbour-search envelope");
            assertIndexedHit(context,world,start,end,cell,owner,true);
            int ownerX=owner.getX()>>4,ownerZ=owner.getZ()>>4;
            FinialRaycast.forgetChunk(world,ownerX-1,ownerZ);
            assertIndexedHit(context,world,start,end,cell,owner,true);
            FinialRaycast.forgetChunk(world,ownerX,ownerZ);
            assertIndexedHit(context,world,start,end,cell,owner,false);
            FinialRaycast.loadChunk(world,world.getWorldChunk(owner));
            assertIndexedHit(context,world,start,end,cell,owner,true);
            // Direct chunk mutation is the same mixed-in return path used by client packet updates.
            world.getWorldChunk(owner).setBlockState(owner,large.with(TwoSizeDecorBlock.SIZE,TwoSizeDecorBlock.Size.SMALL),false);
            assertIndexedHit(context,world,start,end,cell,owner,false);
            world.getWorldChunk(owner).setBlockState(owner,large,false);
            assertIndexedHit(context,world,start,end,cell,owner,true);
            world.removeBlock(owner,false);
            assertIndexedHit(context,world,start,end,cell,owner,false);
        }
        context.complete();
    }

    private static void assertIndexedHit(TestContext context,ServerWorld world,Vec3d start,Vec3d end,
                                         BlockPos cell,BlockPos owner,boolean expected) {
        var hit=world.raycastBlock(start,end,cell,VoxelShapes.empty(),world.getBlockState(cell));
        context.assertTrue((hit!=null && hit.getBlockPos().equals(owner))==expected,
                "Indexed AIR raycast differs after placement/resize/removal/chunk lifecycle at "+cell);
    }
    private static int particleCount(net.minecraft.util.shape.VoxelShape shape) {
        int total=0;
        for(var box:shape.getBoundingBoxes()) {
            int x=Math.max(2,(int)Math.ceil(Math.min(1,box.maxX-box.minX)/.25)),
                    y=Math.max(2,(int)Math.ceil(Math.min(1,box.maxY-box.minY)/.25)),
                    z=Math.max(2,(int)Math.ceil(Math.min(1,box.maxZ-box.minZ)/.25));
            total+=x*y*z;
        }
        return total;
    }
}
