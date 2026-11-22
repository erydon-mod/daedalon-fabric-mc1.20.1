package com.oliver.daedalon.client.glaux.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GlauxMotionTest {
    @Test void attentionStopsAtThreeBlocks(){
        assertTrue(GlauxMotion.withinAwareness(0));
        assertTrue(GlauxMotion.withinAwareness(3*3));
        assertFalse(GlauxMotion.withinAwareness(3.001*3.001));
        assertFalse(GlauxMotion.withinAwareness(8*8));
        assertFalse(GlauxMotion.withinAwareness(Double.NaN));
    }
    @Test void rearSeamUsesTwoDegreeTurnInsteadOfFullCircle(){
        assertEquals(2,Math.toDegrees(GlauxMotion.shortestDelta(Math.toRadians(179),Math.toRadians(-179))),1e-9);
        assertEquals(-2,Math.toDegrees(GlauxMotion.shortestDelta(Math.toRadians(-179),Math.toRadians(179))),1e-9);
    }
    @Test void canLookBehindInBothDirections(){
        for(double target:new double[]{Math.PI,-Math.PI}){GlauxMotion motion=new GlauxMotion();for(int i=0;i<200;i++)motion.advance(target,.05);assertTrue(Math.abs(GlauxMotion.shortestDelta(motion.yaw(),target))<Math.toRadians(1.6));}
    }
    @Test void speedAndAccelerationAreBoundedDuringLongTurn(){
        GlauxMotion motion=new GlauxMotion();double old=motion.yaw(),oldSpeed=0;
        for(int i=0;i<70;i++){motion.advance(Math.PI,.05);double speed=GlauxMotion.shortestDelta(old,motion.yaw())/.05;assertTrue(Math.abs(speed)<=Math.toRadians(45)+1e-9);assertTrue(Math.abs(speed-oldSpeed)<=Math.toRadians(100)*.05+1e-8);old=motion.yaw();oldSpeed=speed;}
    }
    @Test void returnsToRestWithoutOvershoot(){
        GlauxMotion motion=new GlauxMotion();for(int i=0;i<200;i++)motion.advance(2,.05);for(int i=0;i<200;i++)motion.advance(0,.05);assertTrue(Math.abs(motion.yaw())<Math.toRadians(1.6));motion.reset();assertEquals(0,motion.yaw());
    }
}
