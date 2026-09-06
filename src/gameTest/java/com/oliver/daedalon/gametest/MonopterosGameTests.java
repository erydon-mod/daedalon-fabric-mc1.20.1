package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.MonopterosBlock;
import com.oliver.daedalon.block.MonopterosPartBlock;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import com.oliver.daedalon.registry.DecorMaterial;
import com.oliver.daedalon.registry.ModBlocks;
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
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void highParticlesAreOptionalBoundedAndMaterialPreserving(TestContext context) {
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof MonopterosBlock)) continue;
            for (BlockState state : block.getStateManager().getStates()) {
                var effect = new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK, state);
                var normal = new net.minecraft.network.packet.s2c.play.ParticleS2CPacket(effect, false, 1, 2, 3, .2f, .2f, .2f, .05f, 16);
                context.assertTrue(com.oliver.daedalon.client.DomeParticleBurst.apply(normal, false) == normal, "Normal must reuse the unchanged packet");
                var high = com.oliver.daedalon.client.DomeParticleBurst.apply(normal, true);
                context.assertTrue(high.getCount() == 64 && high.getParameters() == effect, "High must quadruple only the count, retaining material and age");
                context.assertTrue(high.getX() == normal.getX() && high.getY() == normal.getY() && high.getZ() == normal.getZ()
                        && high.getOffsetX() == normal.getOffsetX() && high.getSpeed() == normal.getSpeed(), "Burst position and motion must remain unchanged");
                context.assertTrue(com.oliver.daedalon.client.DomeParticleBurst.apply(high, true) == high, "Main-thread dispatch must not multiply twice");
                context.assertTrue(normal.getCount() == 16, "Other clients must retain the original count");
            }
        }
        var unrelated = new net.minecraft.network.packet.s2c.play.ParticleS2CPacket(net.minecraft.particle.ParticleTypes.FLAME, false, 0, 0, 0, 0, 0, 0, 0, 16);
        context.assertTrue(com.oliver.daedalon.client.DomeParticleBurst.apply(unrelated, true) == unrelated, "Other effects must remain unchanged");
        context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void onlyMaterialOwnersEmitBreakParticles(TestContext context) {
        for (BlockState part : ModBlocks.monopterosPart().getStateManager().getStates()) {
            context.assertTrue(!part.hasBlockBreakParticles(), "Invisible cells must not emit Aganite fallback particles");
        }
        int owners = 0;
        for (Block block : Registries.BLOCK) {
            if (!(block instanceof MonopterosBlock)) continue;
            owners++;
            for (BlockState state : block.getStateManager().getStates()) {
                context.assertTrue(state.hasBlockBreakParticles(), "Every dome material and diameter must retain its particles");
            }
        }
        context.assertTrue(owners == 55, "Check all stone, aged stone and bronze domes");
        context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400)
    public void flushCrownsAcceptBlocksAndEveryBronzeFinial(TestContext context) {
        var world=context.getWorld();
        BlockPos anchor=context.getAbsolutePos(new BlockPos(110,10,110));
        var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(anchor.add(12,8,12)));
        Block dome=Registries.BLOCK.get(new Identifier("daedalon","bronze_monopteros_dome"));
        String[] crowns={"minecraft:stone","daedalon:bronze_balanos_finial","daedalon:bronze_kynara_finial",
                "daedalon:bronze_phlox_finial","daedalon:bronze_sphaira_finial","daedalon:bronze_strobilos_finial"};
        for (var size:MonopterosBlock.Diameter.values()) {
            world.setBlockState(anchor,dome.getDefaultState().with(MonopterosBlock.DIAMETER,size),Block.NOTIFY_ALL);
            BlockPos target=anchor.up(size.height);
            Vec3d crown=Vec3d.of(target).add(.5,0,.5);
            for (String id:crowns) for (String finialSize:new String[]{"small","large"}) {
                // Emulate an existing save's now-empty built-in ornament cell.
                world.setBlockState(target,ModBlocks.monopterosPart().stateForOffset(new BlockPos(0,size.height,0)),Block.NOTIFY_ALL);
                BlockHitResult hit=world.raycast(new RaycastContext(crown.add(0,3,0),crown.add(0,-1,0),
                        RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                context.assertTrue(hit.getSide()==Direction.UP && hit.getBlockPos().equals(target.down()),"Crown ray must hit the highest occupied block's top: "+size);
                context.assertTrue(Math.abs(hit.getPos().y-crown.y)<1e-6,"Crown shape must end exactly at the integer mesh height");
                Block block=Registries.BLOCK.get(new Identifier(id));
                context.assertTrue(block!=Blocks.AIR,"Missing crown block: "+id);
                ItemStack stack=block.asItem().getDefaultStack();
                stack.getOrCreateSubNbt("BlockStateTag").putString("size",finialSize);
                player.setStackInHand(Hand.MAIN_HAND,stack);
                var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
                context.assertTrue(result.isAccepted() && world.getBlockState(target).isOf(block),"Normal placement must replace the old ornament cell with "+id);
                context.assertTrue(world.getBlockState(anchor).isOf(dome),"Placing a crown must preserve its roof");
                world.removeBlock(target,false);
            }
            // The old tallest 8m ornament occupied Y=5; edits/removal clean it too.
            BlockPos legacy=anchor.up(5);
            world.setBlockState(legacy,ModBlocks.monopterosPart().stateForOffset(new BlockPos(0,5,0)),Block.NOTIFY_ALL);
            world.removeBlock(anchor,false);
            context.assertTrue(parts(world,anchor).isEmpty(),"Removal must clear legacy ornament cells");
        }
        context.complete();
    }
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
