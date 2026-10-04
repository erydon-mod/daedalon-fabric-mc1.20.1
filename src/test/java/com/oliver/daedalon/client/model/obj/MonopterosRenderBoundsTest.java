package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonopterosRenderBoundsTest {
    @AfterEach void reset() { MonopterosRenderBounds.clear(); }
    @Test void roofFitsAtEverySectionCornerWithoutChangingOtherSections() {
        MonopterosRenderBounds.mark(new BlockPos(15,15,15));
        var box=MonopterosRenderBounds.get(0,0,0);
        assertNotNull(box);
        assertTrue(box.contains(19.5,20.610613,19.5));
        assertTrue(box.contains(-3.5,0,-3.5));
        assertNull(MonopterosRenderBounds.get(1,0,0));
        MonopterosRenderBounds.mark(new BlockPos(0,0,0));
        assertSame(box,MonopterosRenderBounds.get(0,0,0));
    }
    @Test void unloadAndWorldChangeClearOnlyTheirTrackedSections() {
        MonopterosRenderBounds.mark(new BlockPos(-1,-49,-1));
        MonopterosRenderBounds.mark(new BlockPos(-1,-33,-1));
        MonopterosRenderBounds.mark(new BlockPos(20,0,20));
        MonopterosRenderBounds.forgetChunk(-1,-1);
        assertNull(MonopterosRenderBounds.get(-1,-4,-1));
        assertNull(MonopterosRenderBounds.get(-1,-3,-1));
        assertNotNull(MonopterosRenderBounds.get(1,0,1));
        MonopterosRenderBounds.clear();
        assertNull(MonopterosRenderBounds.get(1,0,1));
    }
    @Test void fittedFinialOverhangAndRoofBoundsCoexistRegardlessOfEmissionOrder() {
        var pos=new BlockPos(15,0,15);
        var finial=new Box(-1.04,-2.74,-.6,1.34,3.1,1.34);
        MonopterosRenderBounds.mark(pos,finial);
        var fitted=MonopterosRenderBounds.get(0,0,0);
        assertTrue(fitted.minY<=-2.74 && fitted.maxZ>=16.34);
        assertNull(MonopterosRenderBounds.get(1,0,0));
        MonopterosRenderBounds.mark(pos,finial);
        assertSame(fitted,MonopterosRenderBounds.get(0,0,0));
        MonopterosRenderBounds.mark(pos);
        var combined=MonopterosRenderBounds.get(0,0,0);
        assertTrue(combined.minY<=-2.74 && combined.maxY>=23.125 && combined.maxX>=20.125);
        MonopterosRenderBounds.clear();
        MonopterosRenderBounds.mark(pos);
        MonopterosRenderBounds.mark(pos,finial);
        assertEquals(combined,MonopterosRenderBounds.get(0,0,0));
    }
    @Test void integerViewportCentresConservativelyContainFractionalNegativeFinialBounds() {
        for(double[] limits:new double[][]{{-18.7400003,-.37},{-2.7357123,16},{-1.03629,16.3400031},
                {-4.125,20.125},{-1.125,23.125}}) {
            int centre=MonopterosRenderBounds.frustumCentre(limits[0],limits[1]);
            float extent=MonopterosRenderBounds.frustumExtent(limits[0],limits[1],centre);
            assertTrue(centre-(double)extent<=limits[0] && centre+(double)extent>=limits[1],
                    "Rounded Sodium centre and float radius must contain both exact bounds");
        }
        assertEquals(8,MonopterosRenderBounds.frustumCentre(-4.125,20.125));
        assertEquals(11,MonopterosRenderBounds.frustumCentre(-1.125,23.125));
        assertEquals(12.125F,MonopterosRenderBounds.frustumExtent(-4.125,20.125,8));
        assertEquals(12.125F,MonopterosRenderBounds.frustumExtent(-1.125,23.125,11));
    }
}
