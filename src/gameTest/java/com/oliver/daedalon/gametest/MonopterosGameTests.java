package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.MonopterosBlock;
import com.oliver.daedalon.block.MonopterosPartBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.DecorMaterial;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;

public final class MonopterosGameTests {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400)
    public void diametersHollowInteriorRemoteRimAndDebugStick(TestContext context) {
        var world=context.getWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(80,10,80));
        var player=context.createMockCreativeServerPlayerInWorld();
        world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));
        player.changeGameMode(GameMode.CREATIVE);
        ItemStack stick=Items.DEBUG_STICK.getDefaultStack();
        player.setStackInHand(Hand.MAIN_HAND,stick);
        for (DecorMaterial material:DecorMaterial.values()) for (boolean aged:new boolean[]{false,true}) {
            Block b=Registries.BLOCK.get(new Identifier("daedalon",material.id()+(aged?"_aged":"")+"_monopteros_dome"));
            context.assertTrue(b instanceof MonopterosBlock,"Missing stone dome");
            context.assertTrue(b.getStateManager().getStates().size()==3,"Only three diameter states are needed");
        }
        Block block=Registries.BLOCK.get(new Identifier("daedalon","bronze_monopteros_dome"));
        context.assertTrue(block instanceof MonopterosBlock,"Missing bronze dome");
        context.assertTrue(block.getDefaultState().get(MonopterosBlock.DIAMETER)==MonopterosBlock.Diameter.SIX,"Default diameter must be 6m");
        for (var size:MonopterosBlock.Diameter.values()) {
            world.setBlockState(anchor,block.getDefaultState().with(MonopterosBlock.DIAMETER,size),Block.NOTIFY_ALL);
            List<BlockPos> parts=parts(world,anchor);
            context.assertTrue(!parts.isEmpty(),"The remote rim needs interaction cells");
            context.assertTrue(world.getBlockEntity(anchor)==null,"No dome block entity");
            for (BlockPos pos:parts) context.assertTrue(world.getBlockEntity(pos)==null,"No part block entity");
            context.assertTrue(world.getBlockState(anchor.up()).isAir(),"The inside must stay hollow");
            Vec3d start=Vec3d.of(anchor).add(.5,.125,size.metres/2.0+2);
            Vec3d end=Vec3d.of(anchor).add(.5,.125,.5);
            BlockHitResult hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
            context.assertTrue(world.getBlockState(hit.getBlockPos()).getBlock() instanceof MonopterosPartBlock,"Vanilla ray must select the outer rim");
            context.assertTrue(MonopterosPartBlock.resolveAnchorPos(world,hit.getBlockPos(),world.getBlockState(hit.getBlockPos())).equals(anchor),"Remote rim must resolve the dome");
            var collision=world.getBlockState(hit.getBlockPos()).getCollisionShape(world,hit.getBlockPos());
            context.assertTrue(!collision.isEmpty(),"Outer rim collision must be present");
            BlockState selected=world.getBlockState(hit.getBlockPos());
            context.assertTrue(selected.getBlock().getPickStack(world,hit.getBlockPos(),selected).isOf(block.asItem()),"Pick block must return the bronze dome");
            world.removeBlock(anchor,false);
            context.assertTrue(parts(world,anchor).isEmpty(),"Removal must clear every owned cell");
        }
        world.setBlockState(anchor,block.getDefaultState(),Block.NOTIFY_ALL);
        for (int i=0;i<9;i++) {
            BlockPos part=parts(world,anchor).get(0);
            BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(part),Direction.UP,part,false);
            stick.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            var expected=new MonopterosBlock.Diameter[]{MonopterosBlock.Diameter.EIGHT,MonopterosBlock.Diameter.FOUR,MonopterosBlock.Diameter.SIX}[i%3];
            context.assertTrue(world.getBlockState(anchor).get(MonopterosBlock.DIAMETER)==expected,"Debug stick must cycle 4/6/8 from the remote rim");
        }
        // A neighbouring build at the future 8m rim must prevent expansion atomically.
        BlockPos obstacle=anchor.add(4,1,0);
        world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        context.assertTrue(!MonopterosBlock.canOccupy(world,anchor,block.getDefaultState().with(MonopterosBlock.DIAMETER,MonopterosBlock.Diameter.EIGHT)),"Expansion must not overwrite a neighbouring build");
        BlockPos part=parts(world,anchor).get(0);
        stick.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(part),Direction.UP,part,false)));
        context.assertTrue(world.getBlockState(anchor).get(MonopterosBlock.DIAMETER)==MonopterosBlock.Diameter.SIX,"Blocked edit must retain the old size");
        context.assertTrue(world.getBlockState(obstacle).isOf(Blocks.STONE),"Obstacle must remain intact");
        world.removeBlock(obstacle,false);
        var partState=world.getBlockState(part);
        partState.getBlock().onBreak(world,part,partState,player);
        world.removeBlock(part,false);
        context.assertTrue(world.getBlockState(anchor).isAir() && parts(world,anchor).isEmpty(),"Breaking any rim cell must remove one complete dome");
        context.complete();
    }
    private static List<BlockPos> parts(net.minecraft.world.World world,BlockPos anchor) {
        List<BlockPos> parts=new ArrayList<>();
        for (int y=0;y<6;y++) for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
            BlockPos pos=anchor.add(x,y,z);
            if (MonopterosPartBlock.isOwnedBy(world.getBlockState(pos),pos,anchor)) parts.add(pos);
        }
        return parts;
    }
}
