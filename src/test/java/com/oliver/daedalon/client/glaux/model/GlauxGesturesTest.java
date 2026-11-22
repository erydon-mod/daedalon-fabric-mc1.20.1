package com.oliver.daedalon.client.glaux.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxGesturesTest {
    @Test void shiverIsFiniteAndBounded(){for(double t=-2;t<40;t+=.1)assertTrue(Math.abs(GlauxGestures.shiver(t))<=.025);assertEquals(0,GlauxGestures.shiver(-1));assertEquals(0,GlauxGestures.shiver(12.4));assertEquals(0,GlauxGestures.shiver(500));}
    @Test void nodStopsRatherThanLooping(){assertTrue(GlauxGestures.nod(10)>0);assertEquals(0,GlauxGestures.nod(20));assertEquals(0,GlauxGestures.nod(21));assertEquals(0,GlauxGestures.nod(100));}
    @Test void repeatedInsultsCannotRestartGesture(){assertTrue(GlauxGestures.mayShake(true,false,20_000_000_000L,0));assertFalse(GlauxGestures.mayShake(true,false,21_000_000_000L,20_000_000_000L));assertTrue(GlauxGestures.mayShake(true,false,32_000_000_000L,20_000_000_000L));}
    @Test void stillAndReducedMotionSuppressEveryGesture(){for(boolean attentive:new boolean[]{false,true})for(boolean reduced:new boolean[]{false,true}){assertEquals(attentive&&!reduced,GlauxGestures.mayMove(attentive,reduced));if(!attentive||reduced)assertFalse(GlauxGestures.mayShake(attentive,reduced,40_000_000_000L,0));}}
}
