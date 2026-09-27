package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.PanelBlock;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PanelGameTests {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void everyFinishHasThreeSquareSizesAndFlushBacking(TestContext context) {
        var panels=ModBlocks.blocks().stream().filter(b->b instanceof PanelBlock).toList();
        context.assertTrue(panels.size()==54,"Expected one dual-purpose panel per finish");
        var pos=context.getAbsolutePos(new BlockPos(2,3,2));
        for (Block block:panels) for (var state:block.getStateManager().getStates()) {
            var panel=(PanelBlock)block;
            var shape=state.getOutlineShape(context.getWorld(),pos);
            var bounds=shape.getBoundingBox();
            double size=state.get(PanelBlock.SIZE).metres;
            var normal=state.get(PanelBlock.CEILING)?Direction.DOWN:state.get(PanelBlock.FACING);
            context.assertTrue(panel.transform(state).direction(Direction.SOUTH)==normal,"Carving faces into its support");
            for (var axis:Direction.Axis.values()) {
                double expected=axis==normal.getAxis()?PanelBlock.DEPTH*size:size;
                context.assertTrue(Math.abs((bounds.getMax(axis)-bounds.getMin(axis))-expected)<1e-6,"Wrong panel dimensions");
                if (axis!=normal.getAxis())
                    context.assertTrue(Math.abs((bounds.getMin(axis)+bounds.getMax(axis))/2-.5)<1e-6,"Resizing moved the panel centre");
            }
            double back=normal.getDirection()==Direction.AxisDirection.POSITIVE?bounds.getMin(normal.getAxis()):bounds.getMax(normal.getAxis());
            context.assertTrue(Math.abs(back-(normal.getDirection()==Direction.AxisDirection.POSITIVE?0:1))<1e-6,"Panel floats away from its supporting plane");
            context.assertTrue(bounds.equals(state.getCollisionShape(context.getWorld(),pos).getBoundingBox()),"Collision differs from the cached panel shape");
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void wallClicksFollowTheSurfaceAndCeilingsFaceDown(TestContext context) {
        var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();
        var support=context.getAbsolutePos(new BlockPos(4,4,4));
        world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        for (Block block:ModBlocks.blocks().stream().filter(b->b instanceof PanelBlock).limit(2).toList()) {
            var panel=(PanelBlock)block;
            for (Direction side:Direction.values()) {
                if (side==Direction.UP)continue;
                player.setStackInHand(Hand.MAIN_HAND,block.asItem().getDefaultStack());
                player.setYaw(137);
                var hit=new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(side.getVector()).multiply(.5)),side,support,false);
                var state=block.getPlacementState(new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit)));
                context.assertTrue(state.get(PanelBlock.SIZE)==PanelBlock.Size.SMALL,"Placement must start at 1x1");
                context.assertTrue(panel.transform(state).direction(Direction.SOUTH)==(side==Direction.DOWN?Direction.DOWN:side),"Wrong mounted orientation");
                if (side==Direction.DOWN) {
                    for (var size:PanelBlock.Size.values()) {
                        var placed=state.with(PanelBlock.SIZE,size);
                        var target=support.down();
                        world.setBlockState(target,placed,Block.NOTIFY_ALL);
                        var stored=world.getBlockState(target);
                        context.assertTrue(stored.get(PanelBlock.CEILING),"Underside placement lost ceiling mount");
                        var bounds=stored.getOutlineShape(world,target).getBoundingBox();
                        context.assertTrue(Math.abs(bounds.maxY-1)<1e-6,"Ceiling back must meet its support");
                        context.assertTrue(Math.abs(bounds.minY-(1-PanelBlock.DEPTH*size.metres))<1e-6,"Ceiling carving must project down");
                    }
                }
            }
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void rotationsAndMirrorsPreserveSizesAndMount(TestContext context) {
        for (Block block:ModBlocks.blocks().stream().filter(b->b instanceof PanelBlock).toList()) {
            for (var original:block.getStateManager().getStates()) {
                var state=original;
                for (int i=0;i<4;i++)state=state.rotate(BlockRotation.CLOCKWISE_90);
                context.assertTrue(state==original,"Four rotations changed the panel");
                for (var mirror:new BlockMirror[]{BlockMirror.LEFT_RIGHT,BlockMirror.FRONT_BACK})
                    context.assertTrue(original.mirror(mirror).mirror(mirror)==original,"Mirroring lost the panel size or facing");
            }
        }
        context.complete();
    }
}
