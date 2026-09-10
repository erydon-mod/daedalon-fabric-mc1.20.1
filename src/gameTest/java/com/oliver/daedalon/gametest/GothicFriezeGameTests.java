package com.oliver.daedalon.gametest;

import com.oliver.daedalon.block.FriezeBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Run the same placement contract against the independently authored Gothic mesh. */
public final class GothicFriezeGameTests {
    private final FriezeGameTests contract=new FriezeGameTests(FriezeBlock.Style.GOTHIC);

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void oneBlockRepeatAlwaysSelectsItsOnlySection(TestContext context) {
        for (Direction facing:Direction.Type.HORIZONTAL) for (int x=-12;x<=12;x++) for (int offset=0;offset<3;offset++) {
            context.assertTrue(FriezeBlock.Style.GOTHIC.section(new BlockPos(x,2,-x),facing,offset)==0,
                    "Gothic selected a missing section");
        }
        context.assertTrue(FriezeBlock.Style.GOTHIC.displaySection()==0,"Gothic item selected an empty mesh");
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void materialChangesJoinButDifferentDesignsKeepTheirEnds(TestContext context) {
        var world=context.getWorld(); var pos=context.getAbsolutePos(new BlockPos(3,2,3));
        var gothic=ModBlocks.blocks().stream().filter(b -> b instanceof FriezeBlock f && f.style()==FriezeBlock.Style.GOTHIC).toList();
        var corinthian=ModBlocks.blocks().stream().filter(b -> b instanceof FriezeBlock f && f.style()==FriezeBlock.Style.CORINTHIAN).findFirst().orElseThrow();
        for (Direction facing:Direction.Type.HORIZONTAL) {
            var state=gothic.get(0).getDefaultState().with(FriezeBlock.FACING,facing);
            var edge=facing.rotateYCounterclockwise(); var next=pos.offset(edge);
            world.setBlockState(pos,state,Block.NOTIFY_ALL);
            world.setBlockState(next,gothic.get(1).getDefaultState().with(FriezeBlock.FACING,facing),Block.NOTIFY_ALL);
            context.assertTrue(FriezeBlock.connected(world,pos,state,1),"Same design failed across finishes");
            world.setBlockState(next,corinthian.getDefaultState().with(FriezeBlock.FACING,facing),Block.NOTIFY_ALL);
            context.assertTrue(!FriezeBlock.connected(world,pos,state,1),"Gothic hid its end against a different profile");
            context.assertTrue(!FriezeBlock.connected(world,next,world.getBlockState(next),0),"Corinthian hid its end against Gothic");
            world.setBlockState(next,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos.offset(facing),corinthian.getDefaultState().with(FriezeBlock.FACING,edge),Block.NOTIFY_ALL);
            context.assertTrue(world.getBlockState(pos).get(FriezeBlock.JOIN)==FriezeBlock.Join.STRAIGHT,"Different design forced a corner");
            world.setBlockState(pos.offset(facing),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void angledPlacementFinishesStraightWallsAndPreservesTheChosenCorner(TestContext context) { contract.angledPlacementFinishesStraightWallsAndPreservesTheChosenCorner(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void wallSurfacesFormCornersWithoutAnotherFrieze(TestContext context) { contract.wallSurfacesFormCornersWithoutAnotherFrieze(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void backingWallPreventsSpuriousTurnAndTwoSideWallsStayFlat(TestContext context) { contract.backingWallPreventsSpuriousTurnAndTwoSideWallsStayFlat(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void anyLengthRunsJoinAndBrokenEndsCloseInEveryFacing(TestContext context) { contract.anyLengthRunsJoinAndBrokenEndsCloseInEveryFacing(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void automaticCornersHaveReciprocalConnectionsAndRecoverOnRemoval(TestContext context) { contract.automaticCornersHaveReciprocalConnectionsAndRecoverOnRemoval(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void allFinishesStayOneMetreHighAndPhaseSurvivesNegativeCoordinates(TestContext context) { contract.allFinishesStayOneMetreHighAndPhaseSurvivesNegativeCoordinates(context); }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void wallAndSideClicksPlaceFacingCorrectlyAndInheritPattern(TestContext context) { contract.wallAndSideClicksPlaceFacingCorrectlyAndInheritPattern(context); }
}
