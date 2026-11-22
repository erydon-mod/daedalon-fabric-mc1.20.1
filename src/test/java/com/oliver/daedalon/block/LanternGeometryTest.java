package com.oliver.daedalon.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LanternGeometryTest {
    private static final double EPS=1e-7;

    @Test void clickedFaceAloneSelectsMountAndOrientation() {
        assertEquals(LanternBlock.Mount.PIER,LanternGeometry.mountForSide(Direction.UP));
        assertEquals(LanternBlock.Mount.HANGING,LanternGeometry.mountForSide(Direction.DOWN));
        for (Direction side:Direction.Type.HORIZONTAL) {
            assertEquals(LanternBlock.Mount.WALL,LanternGeometry.mountForSide(side));
            assertEquals(side,LanternGeometry.facingForSide(side));
        }
        assertEquals(Direction.SOUTH,LanternGeometry.facingForSide(Direction.UP));
        assertEquals(Direction.SOUTH,LanternGeometry.facingForSide(Direction.DOWN));
    }

    @Test void pierGroundContactAndBodyHeightsAreExactForBothSizes() {
        for (var style:LanternBlock.Style.values()) for (var size:LanternBlock.Size.values()) for (Direction facing:Direction.Type.HORIZONTAL) {
            var box=shape(style,LanternBlock.Mount.PIER,size,facing).getBoundingBox();
            assertEquals(0,box.minY,EPS);
            assertEquals(size.metres,box.maxY,EPS);
            assertEquals(.5,(box.minX+box.maxX)/2,EPS);
            assertEquals(.5,(box.minZ+box.maxZ)/2,EPS);
        }
    }

    @Test void wallLanternsStayCentredOnClickedVerticalFaceAndFlushToTheirSupport() {
        for (var style:LanternBlock.Style.values()) for (var size:LanternBlock.Size.values()) for (Direction facing:Direction.Type.HORIZONTAL) {
            var box=shape(style,LanternBlock.Mount.WALL,size,facing).getBoundingBox();
            assertEquals(.5,(box.minY+box.maxY)/2,EPS);
            assertEquals(size.metres,box.maxY-box.minY,EPS);
            switch (facing) {
                case SOUTH -> { assertEquals(0,box.minZ,EPS);assertEquals(.5,(box.minX+box.maxX)/2,EPS); }
                case NORTH -> { assertEquals(1,box.maxZ,EPS);assertEquals(.5,(box.minX+box.maxX)/2,EPS); }
                case EAST -> { assertEquals(0,box.minX,EPS);assertEquals(.5,(box.minZ+box.maxZ)/2,EPS); }
                case WEST -> { assertEquals(1,box.maxX,EPS);assertEquals(.5,(box.minZ+box.maxZ)/2,EPS); }
            }
        }
    }

    @Test void initialHangingDropIncludesHalfMetreChainAndFullEnlargedBody() {
        for (var style:LanternBlock.Style.values()) for (var size:LanternBlock.Size.values()) {
            VoxelShape main=shape(style,LanternBlock.Mount.HANGING,size,Direction.SOUTH);
            VoxelShape lower=LanternGeometry.shape(style,LanternBlock.Mount.HANGING,size,Direction.SOUTH,true,true);
            VoxelShape assembled=VoxelShapes.union(main,lower.offset(0,-1,0));
            var box=assembled.getBoundingBox();
            assertEquals(1,box.maxY,EPS,"Ceiling contact must remain at the top of the anchor cell");
            assertEquals(.5-size.metres,box.minY,EPS);
            assertEquals(.5+size.metres,box.maxY-box.minY,EPS);
            assertFalse(lower.isEmpty());
        }
    }

    @Test void chainAndCanopyNeverFillTheEmptySpaceBesideThem() {
        for (var size:LanternBlock.Size.values()) {
            var chain=LanternGeometry.chain(size,false).getBoundingBox();
            assertEquals(0,chain.minY,EPS);assertEquals(1,chain.maxY,EPS);
            assertTrue(chain.maxX-chain.minX<.05);
            assertTrue(chain.maxZ-chain.minZ<.05);
            var canopy=LanternGeometry.chain(size,true).getBoundingBox();
            assertEquals(1,canopy.maxY,EPS);
            assertTrue(canopy.maxX-canopy.minX>chain.maxX-chain.minX);
        }
    }

    @Test void upperAndLowerSlicesMatchTheWholeEnlargedBodyAndQueriesReuseCaches() {
        for (var style:LanternBlock.Style.values()) for (var mount:LanternBlock.Mount.values()) for (var size:LanternBlock.Size.values()) for (Direction facing:Direction.Type.HORIZONTAL) {
            VoxelShape a=shape(style,mount,size,facing),b=shape(style,mount,size,facing);
            assertSame(a,b);
            VoxelShape combined=VoxelShapes.empty();
            for(int offset=-1;offset<=1;offset++) combined=VoxelShapes.union(combined,LanternGeometry.partShape(style,mount,size,facing,true,offset).offset(0,offset,0));
            assertEquals(LanternGeometry.fullShape(style,mount,size,facing,true).getBoundingBox(),combined.getBoundingBox());
        }
    }
    private static VoxelShape shape(LanternBlock.Style style,LanternBlock.Mount mount,LanternBlock.Size size,Direction facing) {
        return LanternGeometry.fullShape(style,mount,size,facing,true);
    }
}
