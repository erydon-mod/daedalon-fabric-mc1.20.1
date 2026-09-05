package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.math.BlockPos;
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
}
