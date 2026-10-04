package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FinialBlock;
import com.oliver.daedalon.block.FinialSupport;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/** The ornament stays upright: mounting changes yaw and position, never its pitch. */
final class FinialMeshTransform implements RenderContext.QuadTransform {
    private static final java.util.Map<FinialSupport.Fit,FinialMeshTransform[]> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private final FinialSupport.Fit fit;
    private final float scale,cos,sin;
    private FinialMeshTransform(FinialSupport.Fit fit,float scale) {
        this.fit=fit; this.scale=scale; cos=(float)Math.cos(fit.yaw()); sin=(float)Math.sin(fit.yaw());
    }
    static FinialMeshTransform forState(BlockState state,FinialBlock block) {
        FinialSupport.Fit fit=FinialSupport.fit(state,block.style());
        return forFit(fit,state.get(TwoSizeDecorBlock.SIZE));
    }
    static FinialMeshTransform forFit(FinialSupport.Fit fit,TwoSizeDecorBlock.Size size) {
        var values=CACHE.computeIfAbsent(fit,key -> new FinialMeshTransform[]{
                new FinialMeshTransform(key,.5F),new FinialMeshTransform(key,1)});
        return values[size.ordinal()];
    }
    @Override public boolean transform(MutableQuadView quad) {
        for(int vertex=0;vertex<4;vertex++) {
            float x=(quad.x(vertex)-.5F)*scale,z=(quad.z(vertex)-.5F)*scale;
            quad.pos(vertex,(float)fit.centreX()+x*cos-z*sin,quad.y(vertex)*scale+(float)fit.bodyY(),
                    (float)fit.centreZ()+x*sin+z*cos);
            quad.uv(vertex,.5F+(quad.u(vertex)-.5F)*scale,.5F+(quad.v(vertex)-.5F)*scale);
            if(quad.hasNormal(vertex)) {
                float nx=quad.normalX(vertex),nz=quad.normalZ(vertex);
                quad.normal(vertex,nx*cos-nz*sin,quad.normalY(vertex),nx*sin+nz*cos);
            }
        }
        quad.cullFace(null);
        Direction face=quad.nominalFace();
        if(face!=null && face.getAxis().isHorizontal()) {
            double x=face.getOffsetX()*cos-face.getOffsetZ()*sin,z=face.getOffsetX()*sin+face.getOffsetZ()*cos;
            quad.nominalFace(Math.abs(x)>=Math.abs(z) ? (x>=0 ? Direction.EAST : Direction.WEST)
                    : (z>=0 ? Direction.SOUTH : Direction.NORTH));
        }
        return true;
    }
}
