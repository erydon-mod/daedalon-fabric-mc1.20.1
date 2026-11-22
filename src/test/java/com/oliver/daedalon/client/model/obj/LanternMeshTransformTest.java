package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LanternMeshTransformTest {
    @Test void pierFootAndHangingTopRemainAtTheirExactSupportHeights() {
        for(var size:LanternBlock.Size.values()) for(Direction facing:Direction.Type.HORIZONTAL) {
            var pier=LanternMeshTransform.forMount(LanternBlock.Mount.PIER,size,facing);
            assertEquals(0,pier.y(0),1e-7);assertEquals(size.metres,pier.y(1),1e-7);
            var hanging=LanternMeshTransform.forMount(LanternBlock.Mount.HANGING,size,facing);
            assertEquals(.5,hanging.y(1),1e-7);
            assertEquals(.5-size.metres,hanging.y(0),1e-7);
            assertEquals(.5,hanging.x(.5F,.5F),1e-7);assertEquals(.5,hanging.z(.5F,.5F),1e-7);
        }
    }
    @Test void wallRearIsFlushAndBodyIsCentredOnEveryClickedFace() {
        for(var size:LanternBlock.Size.values()) for(Direction facing:Direction.Type.HORIZONTAL) {
            var wall=LanternMeshTransform.forMount(LanternBlock.Mount.WALL,size,facing);
            assertEquals(.5,(wall.y(0)+wall.y(1))/2,1e-7);
            float x=wall.x(.5F,.5F),z=wall.z(.5F,.5F);
            switch(facing) {
                case SOUTH->{assertEquals(.5,x,1e-7);assertEquals(0,z,1e-7);}
                case NORTH->{assertEquals(.5,x,1e-7);assertEquals(1,z,1e-7);}
                case EAST->{assertEquals(0,x,1e-7);assertEquals(.5,z,1e-7);}
                case WEST->{assertEquals(1,x,1e-7);assertEquals(.5,z,1e-7);}
            }
            float fx=wall.x(.5F,.75F)-x,fz=wall.z(.5F,.75F)-z;
            assertEquals(facing.getOffsetX()*.25*size.metres,fx,1e-7);
            assertEquals(facing.getOffsetZ()*.25*size.metres,fz,1e-7);
        }
    }
    @Test void chainIntervalsStayHalfOrWholeMetresRegardlessOfBodySize() {
        assertEquals(.5,LanternMeshTransform.HALF_CHAIN.y(0),1e-7);
        assertEquals(1,LanternMeshTransform.HALF_CHAIN.y(.5F),1e-7);
        assertEquals(0,LanternMeshTransform.FULL_CHAIN.y(0),1e-7);
        assertEquals(1,LanternMeshTransform.FULL_CHAIN.y(1),1e-7);
        assertEquals(1,LanternMeshTransform.CANOPY.y(0),1e-7);
    }
}
