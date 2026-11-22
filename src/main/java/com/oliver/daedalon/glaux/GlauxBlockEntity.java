package com.oliver.daedalon.glaux;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** No server tick, transcript or player information is stored on the sculpture. */
public final class GlauxBlockEntity extends BlockEntity {
    private final Box renderBounds;
    public GlauxBlockEntity(BlockPos pos, BlockState state) {
        super(GlauxRegistry.blockEntityType(), pos, state);
        // Covers the off-centre neck through every facing and +/-180 degree turn.
        renderBounds=new Box(pos.getX()-.40,pos.getY()+1.14,pos.getZ()-.40,
                pos.getX()+1.40,pos.getY()+2.12,pos.getZ()+1.40);
    }
    public Box renderBounds(){return renderBounds;}
}
