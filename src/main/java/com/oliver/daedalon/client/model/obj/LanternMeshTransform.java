package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/** Immutable body transforms, shared by every finish and evaluated only on chunk rebuild. */
final class LanternMeshTransform implements RenderContext.QuadTransform {
    private static final LanternMeshTransform[][][] BODY = new LanternMeshTransform[3][2][4];
    private static final LanternMeshTransform[][][] FLAME = new LanternMeshTransform[3][2][4];
    static final LanternMeshTransform HALF_CHAIN = new LanternMeshTransform(1, 0, .5F, 0, false);
    static final LanternMeshTransform FULL_CHAIN = new LanternMeshTransform(1, 0, 0, 0, false);
    static final LanternMeshTransform CANOPY = new LanternMeshTransform(1, 0, 1, 0, false);
    static {
        for (var mount : LanternBlock.Mount.values()) for (var size : LanternBlock.Size.values()) for (int r=0;r<4;r++) {
            float scale=size.metres;
            float y=mount==LanternBlock.Mount.PIER?0:mount==LanternBlock.Mount.WALL?.5F-scale/2:.5F-scale;
            BODY[mount.ordinal()][size.ordinal()][r]=new LanternMeshTransform(scale,r,y,mount==LanternBlock.Mount.WALL?-.5F:0,false);
            FLAME[mount.ordinal()][size.ordinal()][r]=new LanternMeshTransform(scale,r,y,mount==LanternBlock.Mount.WALL?-.5F:0,false);
        }
    }
    private final float scale,yOffset,zOffset;
    private final int steps;
    private final boolean scaleUvs;
    private LanternMeshTransform(float scale,int steps,float yOffset,float zOffset,boolean scaleUvs) {
        this.scale=scale;this.steps=steps;this.yOffset=yOffset;this.zOffset=zOffset;this.scaleUvs=scaleUvs;
    }
    static LanternMeshTransform forState(BlockState state) {
        return forMount(state.get(LanternBlock.MOUNT),state.get(LanternBlock.SIZE),state.get(LanternBlock.FACING));
    }
    static LanternMeshTransform forMount(LanternBlock.Mount mount,LanternBlock.Size size,Direction facing) {
        return BODY[mount.ordinal()][size.ordinal()][steps(facing)];
    }
    static LanternMeshTransform forFlame(LanternBlock.Mount mount,LanternBlock.Size size,Direction facing) {
        return FLAME[mount.ordinal()][size.ordinal()][steps(facing)];
    }
    static int steps(Direction face) { return switch(face) {case SOUTH->0;case WEST->1;case NORTH->2;default->3;}; }
    float x(float x,float z) { return .5F+rotateX((x-.5F)*scale,(z-.5F)*scale+zOffset); }
    float y(float y) { return y*scale+yOffset; }
    float z(float x,float z) { return .5F+rotateZ((x-.5F)*scale,(z-.5F)*scale+zOffset); }
    private float rotateX(float x,float z) { return switch(steps) {case 1->-z;case 2->-x;case 3->z;default->x;}; }
    private float rotateZ(float x,float z) { return switch(steps) {case 1->x;case 2->-z;case 3->-x;default->z;}; }
    // World surfaces receive metric UVs after placement. Authored glazing/flame
    // coordinates stay inside their complete sprites at either physical size.
    private float uv(float value) { return .5F+(value-.5F)*scale; }
    @Override public boolean transform(MutableQuadView quad) {
        var face=quad.nominalFace();
        for(int v=0;v<4;v++) {
            float x=quad.x(v),z=quad.z(v);
            quad.pos(v,x(x,z),y(quad.y(v)),z(x,z));
            if(scaleUvs) quad.uv(v,uv(quad.u(v)),uv(quad.v(v)));
            if(quad.hasNormal(v)) {
                float nx=quad.normalX(v),nz=quad.normalZ(v);
                quad.normal(v,rotateX(nx,nz),quad.normalY(v),rotateZ(nx,nz));
            }
        }
        quad.cullFace(null);
        if(face!=null && face.getAxis().isHorizontal()) for(int i=0;i<steps;i++) face=face.rotateYClockwise();
        quad.nominalFace(face);
        return true;
    }
}
