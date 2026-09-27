package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.PanelBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/** Cached orientation/size transforms; all finishes and mounts reuse one mesh. */
final class PanelMeshTransform implements RenderContext.QuadTransform {
    private static final PanelMeshTransform[][][] CACHE=new PanelMeshTransform[2][3][4];
    static {
        for (int c=0;c<2;c++) for (int s=1;s<=3;s++) for (int r=0;r<4;r++)
            CACHE[c][s-1][r]=new PanelMeshTransform(new PanelBlock.Transform(s,c==1,r));
    }
    private final PanelBlock.Transform mapping;
    PanelMeshTransform(PanelBlock.Transform mapping) { this.mapping=mapping; }
    static PanelMeshTransform forState(BlockState state) {
        var mapping=((PanelBlock)state.getBlock()).transform(state);
        return CACHE[mapping.ceiling()?1:0][mapping.size()-1][mapping.steps()];
    }
    private float scaleUv(float uv) {
        return uv<=.5F?uv*mapping.size():1-(1-uv)*mapping.size();
    }
    @Override public boolean transform(MutableQuadView quad) {
        Direction face=quad.nominalFace();
        for (int v=0;v<4;v++) {
            float x=quad.x(v),y=quad.y(v),z=quad.z(v);
            quad.pos(v,mapping.x(x,y,z),mapping.y(x,y,z),mapping.z(x,y,z));
            // Box projection starts at 0 (or mirrored at 1), not at the sheet centre.
            // Scale that one-metre window before TextureTransform packs it into the atlas.
            quad.uv(v,scaleUv(quad.u(v)),scaleUv(quad.v(v)));
            if (quad.hasNormal(v)) {
                float nx=quad.normalX(v),ny=quad.normalY(v),nz=quad.normalZ(v);
                quad.normal(v,mapping.normalX(nx,ny,nz),mapping.normalY(nx,ny,nz),mapping.normalZ(nx,ny,nz));
            }
        }
        quad.cullFace(null);
        if (face!=null) quad.nominalFace(mapping.direction(face));
        return true;
    }
}
