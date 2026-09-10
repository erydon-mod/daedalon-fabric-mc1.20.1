package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.FriezeBlock;
import com.oliver.daedalon.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtOps;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class FriezeGameTests {
    private final FriezeBlock.Style style;
    public FriezeGameTests() { this(FriezeBlock.Style.CORINTHIAN); }
    public FriezeGameTests(FriezeBlock.Style style) { this.style=style; }

    private FriezeBlock block() {
        return (FriezeBlock) ModBlocks.blocks().stream().filter(b -> b instanceof FriezeBlock f && f.style()==style).findFirst().orElseThrow();
    }

    private static float yaw(Direction direction) {
        return switch (direction) { case SOUTH -> 0; case WEST -> 90; case NORTH -> 180; case EAST -> 270;
            default -> throw new IllegalArgumentException(); };
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void angledPlacementFinishesStraightWallsAndPreservesTheChosenCorner(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction wall:Direction.Type.HORIZONTAL) for (boolean left:new boolean[]{true,false}) {
            Direction aimed=left?wall.rotateYClockwise():wall.rotateYCounterclockwise();
            Direction incoming=aimed.getOpposite();
            BlockPos support=pos.offset(wall.getOpposite()), neighbor=pos.offset(incoming);
            for (int i=-1;i<=1;i++) world.setBlockState(support.offset(incoming,i),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var run=block().getDefaultState().with(FriezeBlock.FACING,wall).with(FriezeBlock.PATTERN,2);
            world.setBlockState(neighbor,run,Block.NOTIFY_ALL);
            player.setYaw(yaw(incoming)+20); // Oblique approach, not an exact cardinal angle.
            player.setStackInHand(Hand.MAIN_HAND,block().asItem().getDefaultStack());
            var hit=new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(wall.getVector()).multiply(.5)),wall,support,false);
            var placement=new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            BlockState corner=block().getPlacementState(placement);
            context.assertTrue(corner.get(FriezeBlock.MANUAL_CORNER),"Angle did not select a corner");
            context.assertTrue(corner.get(FriezeBlock.FACING)==aimed && corner.get(FriezeBlock.PATTERN)==2,"Corner lost the placement direction or run offset");
            context.assertTrue(FriezeBlock.armFacing(corner,1)==wall,"Return did not match the wall run");
            world.setBlockState(pos,corner,Block.NOTIFY_ALL);
            context.assertTrue(FriezeBlock.connected(world,pos,corner,1),"Corner did not join the existing run");
            context.assertTrue(FriezeBlock.connected(world,neighbor,run,0) || FriezeBlock.connected(world,neighbor,run,1),"Run did not connect back to the corner");
            var bounds=corner.getCollisionShape(world,pos).getBoundingBox();
            context.assertTrue(bounds.maxX-bounds.minX<=style.depth()+1e-6 && bounds.maxZ-bounds.minZ<=style.depth()+1e-6,"End return projected a full metre");
            world.setBlockState(neighbor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(support,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(FriezeBlock.refresh(world,pos,world.getBlockState(pos))==corner,"Wall or run edits erased the chosen corner");
            var encoded=BlockState.CODEC.encodeStart(NbtOps.INSTANCE,corner).result().orElseThrow();
            context.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE,encoded).result().orElseThrow()==corner,"Corner choice did not survive serialization");
            context.assertTrue(corner.rotate(BlockRotation.CLOCKWISE_90).get(FriezeBlock.MANUAL_CORNER),"Rotation lost manual choice");
            context.assertTrue(corner.mirror(BlockMirror.LEFT_RIGHT).get(FriezeBlock.JOIN)==corner.get(FriezeBlock.JOIN).mirrored(),"Mirror lost handedness");
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            for (int i=-1;i<=1;i++) world.setBlockState(support.offset(incoming,i),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void wallSurfacesFormCornersWithoutAnotherFrieze(TestContext context) {
        var world=context.getWorld();
        var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction facing:Direction.Type.HORIZONTAL) for (boolean left:new boolean[]{true,false}) {
            Direction edge=left?facing.rotateYClockwise():facing.rotateYCounterclockwise();
            BlockPos back=pos.offset(facing.getOpposite()), side=pos.offset(edge), diagonal=back.offset(edge);
            BlockState state=block().getDefaultState().with(FriezeBlock.FACING,facing);
            world.setBlockState(back,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos,state,Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Flat wall must stay flat");
            world.setBlockState(side,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var inner=left?FriezeBlock.Join.INNER_LEFT:FriezeBlock.Join.INNER_RIGHT;
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==inner,"Wall alone must form an inside corner");
            world.setBlockState(side,Blocks.OAK_FENCE.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Fence must not act as a full wall");
            world.setBlockState(side,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(back,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(diagonal,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var outer=left?FriezeBlock.Join.OUTER_LEFT:FriezeBlock.Join.OUTER_RIGHT;
            context.assertTrue(FriezeBlock.refresh(world,pos,state).get(FriezeBlock.JOIN)==outer,"Diagonal wall must form an outside corner");
            var bounds=state.getCollisionShape(world,pos).getBoundingBox();
            context.assertTrue((bounds.maxX-bounds.minX)<=style.depth()+1e-6 && (bounds.maxZ-bounds.minZ)<=style.depth()+1e-6,"Collision missed diagonal wall edit");
            world.setBlockState(diagonal,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(FriezeBlock.refresh(world,pos,state).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Removing the diagonal wall must restore the straight shape");
            bounds=state.getCollisionShape(world,pos).getBoundingBox();
            context.assertTrue(Math.max((bounds.maxX-bounds.minX),(bounds.maxZ-bounds.minZ))==1,"Collision stayed stuck as a corner");
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void backingWallPreventsSpuriousTurnAndTwoSideWallsStayFlat(TestContext context) {
        var world=context.getWorld(); var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction facing:Direction.Type.HORIZONTAL) {
            Direction left=facing.rotateYClockwise(),right=left.getOpposite();
            BlockState main=block().getDefaultState().with(FriezeBlock.FACING,facing);
            world.setBlockState(pos.offset(facing.getOpposite()),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos.offset(facing),main.with(FriezeBlock.FACING,left),Block.NOTIFY_ALL);
            world.setBlockState(pos,main,Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Unrelated frieze in front overrode flat supporting wall");
            world.setBlockState(pos.offset(left),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos.offset(right),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Narrow recess arbitrarily chose one side");
            for (Direction d:Direction.Type.HORIZONTAL) world.setBlockState(pos.offset(d),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void anyLengthRunsJoinAndBrokenEndsCloseInEveryFacing(TestContext context) {
        var world=context.getWorld();
        var center=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction facing:Direction.Type.HORIZONTAL) {
            Direction along=facing.rotateYCounterclockwise();
            BlockState state=block().getDefaultState().with(FriezeBlock.FACING,facing);
            // Reverse placement order must not affect the design phase.
            for (int i=2;i>=-2;i--) world.setBlockState(center.offset(along,i),state,Block.NOTIFY_ALL);
            for (int i=-2;i<=2;i++) {
                BlockPos pos=center.offset(along,i);
                BlockState actual=world.getBlockState(pos);
                context.assertTrue(actual.get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Straight run became a corner");
                context.assertTrue(FriezeBlock.connected(world,pos,actual,0)==(i>-2),"Wrong starting cap");
                context.assertTrue(FriezeBlock.connected(world,pos,actual,1)==(i<2),"Wrong ending cap");
                context.assertTrue(world.getBlockEntity(pos)==null,"Friezes must not allocate block entities");
            }
            world.setBlockState(center,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(!FriezeBlock.connected(world,center.offset(along,-1),world.getBlockState(center.offset(along,-1)),1),"Break did not expose left run end");
            context.assertTrue(!FriezeBlock.connected(world,center.offset(along,1),world.getBlockState(center.offset(along,1)),0),"Break did not expose right run end");
            for (int i=-2;i<=2;i++) world.setBlockState(center.offset(along,i),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void automaticCornersHaveReciprocalConnectionsAndRecoverOnRemoval(TestContext context) {
        var world=context.getWorld();
        var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction facing:Direction.Type.HORIZONTAL) {
            for (FriezeBlock.Join join:FriezeBlock.Join.values()) {
                if (join==FriezeBlock.Join.STRAIGHT) continue;
                boolean inner=join==FriezeBlock.Join.INNER_LEFT || join==FriezeBlock.Join.INNER_RIGHT;
                BlockPos neighbor=pos.offset(inner?facing:facing.getOpposite());
                Direction side=join.left?facing.rotateYCounterclockwise():facing.rotateYClockwise();
                BlockState main=block().getDefaultState().with(FriezeBlock.FACING,facing);
                BlockState other=block().getDefaultState().with(FriezeBlock.FACING,side);
                for (boolean reverse:new boolean[]{false,true}) {
                    world.setBlockState(reverse?neighbor:pos,reverse?other:main,Block.NOTIFY_ALL);
                    world.setBlockState(reverse?pos:neighbor,reverse?main:other,Block.NOTIFY_ALL);
                    BlockState actual=world.getBlockState(pos);
                    context.assertTrue(actual.get(FriezeBlock.JOIN)==join,"Wrong corner "+facing+" "+join);
                    context.assertTrue(FriezeBlock.connected(world,pos,actual,1),"Corner return did not connect");
                    BlockState neighborState=world.getBlockState(neighbor);
                    context.assertTrue(FriezeBlock.connected(world,neighbor,neighborState,0)
                            || FriezeBlock.connected(world,neighbor,neighborState,1),"Return connection was one sided");
                    world.setBlockState(neighbor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Corner did not recover");
                    world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                }
            }
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void allFinishesStayOneMetreHighAndPhaseSurvivesNegativeCoordinates(TestContext context) {
        var world=context.getWorld();
        var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        var blocks=ModBlocks.blocks().stream().filter(b -> b instanceof FriezeBlock f && f.style()==style).toList();
        context.assertTrue(blocks.size()==54,"Missing material variants");
        for (var block:blocks) for (var state:block.getStateManager().getStates()) {
            var shape=state.getCollisionShape(world,pos); var bounds=shape.getBoundingBox();
            context.assertTrue(bounds.minY==0 && bounds.maxY==1,"Height must be exactly 1m");
            context.assertTrue(bounds.minX>=0 && bounds.maxX<=1 && bounds.minZ>=0 && bounds.maxZ<=1,"Geometry must remain within its owner cell");
        }
        for (Direction facing:Direction.Type.HORIZONTAL) for (int coordinate=-34;coordinate<=34;coordinate++) {
            BlockPos p=new BlockPos(coordinate,0,coordinate);
            Direction along=facing.rotateYCounterclockwise();
            for (int offset=0;offset<3;offset++) {
                int a=style.section(p,facing,offset);
                int b=style.section(p.offset(along),facing,offset);
                context.assertTrue(b==(a+1)%style.sections(),"Broken pattern across zero/chunk boundary");
            }
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void wallAndSideClicksPlaceFacingCorrectlyAndInheritPattern(TestContext context) {
        var world=context.getWorld(); var player=context.createMockCreativeServerPlayerInWorld();
        var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        for (Direction facing:Direction.Type.HORIZONTAL) {
            player.setYaw(yaw(facing.getOpposite()));
            BlockPos support=pos.offset(facing.getOpposite());
            world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var stack=block().asItem().getDefaultStack(); player.setStackInHand(Hand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(facing.getVector()).multiply(.5)),facing,support,false);
            var placement=new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            context.assertTrue(block().getPlacementState(placement).get(FriezeBlock.FACING)==facing,"Wall click used player direction");
            world.setBlockState(pos,block().getDefaultState().with(FriezeBlock.FACING,facing).with(FriezeBlock.PATTERN,2),Block.NOTIFY_ALL);
            Direction along=facing.rotateYCounterclockwise();
            var sideHit=new BlockHitResult(Vec3d.ofCenter(pos).add(Vec3d.of(along.getVector()).multiply(.5)),along,pos,false);
            var extension=block().getPlacementState(new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,sideHit)));
            context.assertTrue(extension.get(FriezeBlock.FACING)==facing && extension.get(FriezeBlock.PATTERN)==2,"Extending a run changed its facing/offset");
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(support,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }
}
