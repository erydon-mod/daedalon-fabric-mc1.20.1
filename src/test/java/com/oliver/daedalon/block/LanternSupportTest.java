package com.oliver.daedalon.block;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LanternSupportTest {
    @Test void everyEighthLayerDepthKeepsTheBackOnItsActualBroadFace() {
        for(int layers=1;layers<=8;layers++) {
            double thickness=layers/8.;
            var south=LanternSupport.offset(new Box(0,0,0,1,1,thickness),Direction.SOUTH);
            assertEquals(thickness-1,south.z,1e-9);assertEquals(0,south.x,1e-9);assertEquals(0,south.y,1e-9);
            var north=LanternSupport.offset(new Box(0,0,1-thickness,1,1,1),Direction.NORTH);
            assertEquals(1-thickness,north.z,1e-9);assertEquals(0,north.x,1e-9);
            var east=LanternSupport.offset(new Box(0,0,0,thickness,1,1),Direction.EAST);
            assertEquals(thickness-1,east.x,1e-9);assertEquals(0,east.z,1e-9);
            var west=LanternSupport.offset(new Box(1-thickness,0,0,1,1,1),Direction.WEST);
            assertEquals(1-thickness,west.x,1e-9);assertEquals(0,west.z,1e-9);
        }
    }
    @Test void everyEighthExposedEdgeCentresTheBodyAcrossItsActualThinFace() {
        for(int layers=1;layers<=8;layers++) {
            double t=layers/8.;
            for(Direction face:new Direction[]{Direction.EAST,Direction.WEST}) {
                var low=LanternSupport.offset(new Box(0,0,0,1,1,t),face);
                var high=LanternSupport.offset(new Box(0,0,1-t,1,1,1),face);
                assertEquals(0,low.x,1e-9);assertEquals(t/2-.5,low.z,1e-9);
                assertEquals(0,high.x,1e-9);assertEquals(.5-t/2,high.z,1e-9);
            }
            for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH}) {
                var low=LanternSupport.offset(new Box(0,0,0,t,1,1),face);
                var high=LanternSupport.offset(new Box(1-t,0,0,1,1,1),face);
                assertEquals(0,low.z,1e-9);assertEquals(t/2-.5,low.x,1e-9);
                assertEquals(0,high.z,1e-9);assertEquals(.5-t/2,high.x,1e-9);
            }
        }
    }
}
