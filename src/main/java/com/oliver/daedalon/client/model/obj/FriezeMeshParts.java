package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FriezeBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.Map;
import java.util.Objects;

/** Resolved once at bake time; placed friezes submit only their chosen components. */
final class FriezeMeshParts {
    private final Mesh[][] primary, secondary, firstCap, secondCap;
    private final FriezeBlock.Style style;

    FriezeMeshParts(Map<String, Mesh> parts, FriezeBlock.Style style) {
        this.style=style;
        int sections=style.sections();
        primary=new Mesh[5][sections]; secondary=new Mesh[5][sections];
        firstCap=new Mesh[5][sections]; secondCap=new Mesh[5][sections];
        for (FriezeBlock.Join join : FriezeBlock.Join.values()) {
            int j=join.ordinal();
            for (int phase=0;phase<sections;phase++) {
                primary[j][phase]=require(parts, join == FriezeBlock.Join.STRAIGHT ? "straight_"+phase
                        : join.asString()+"_primary_"+phase);
                if (j != 0) secondary[j][phase]=require(parts,join.asString()+"_secondary_"+phase);
            }
        }
        String[] first={"cap_left","cap_right","cap_left","cap_left","cap_right"};
        String[] second={"cap_right","cap_east_low","cap_west_high","cap_east_high","cap_west_low"};
        for (int i=0;i<5;i++) for (int phase=0;phase<sections;phase++) {
            firstCap[i][phase]=require(parts,first[i]+"_"+phase);
            secondCap[i][phase]=require(parts,second[i]+"_"+phase);
        }
    }

    private static Mesh require(Map<String,Mesh> parts,String key) {
        return Objects.requireNonNull(parts.get(key),"Missing frieze component: "+key);
    }

    void emit(BlockView world, BlockPos pos, BlockState state, RenderContext context) {
        state=FriezeBlock.refresh(world,pos,state);
        int join=state.get(FriezeBlock.JOIN).ordinal();
        Direction facing=state.get(FriezeBlock.FACING);
        int offset=state.get(FriezeBlock.PATTERN);
        int mainPhase=style.section(pos,facing,offset);
        int returnPhase=join==0 ? mainPhase : style.section(pos,FriezeBlock.secondaryFacing(state),offset);
        context.pushTransform(FriezeWorldTransform.forBlock(FriezeBlock.rotationSteps(facing),pos));
        try {
            primary[join][mainPhase].outputTo(context.getEmitter());
            if (join != 0) {
                secondary[join][returnPhase].outputTo(context.getEmitter());
            }
            if (!FriezeBlock.connected(world,pos,state,0)) firstCap[join][mainPhase].outputTo(context.getEmitter());
            if (!FriezeBlock.connected(world,pos,state,1)) secondCap[join][returnPhase].outputTo(context.getEmitter());
        } finally { context.popTransform(); }
    }

}
