package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FinialSupport;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.util.math.Direction;

/** Six quads per fitted square base, shared by every material variant. */
final class FinialBaseMesh {
    private static final java.util.Map<FinialSupport.Fit,Mesh> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private FinialBaseMesh() { }
    static Mesh forFit(FinialSupport.Fit fit) { return CACHE.computeIfAbsent(fit,FinialBaseMesh::create); }
    private static Mesh create(FinialSupport.Fit fit) {
        var builder=RendererAccess.INSTANCE.getRenderer().meshBuilder();
        QuadEmitter emitter=builder.getEmitter();
        double[][] points=points(fit);
        for(int[] face:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{2,6,5,1},{1,5,4,0},{3,7,6,2}}) {
            double[] a=points[face[0]],b=points[face[1]],c=points[face[2]];
            double ax=b[0]-a[0],ay=b[1]-a[1],az=b[2]-a[2];
            double bx=c[0]-a[0],by=c[1]-a[1],bz=c[2]-a[2];
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            nx/=length; ny/=length; nz/=length;
            Direction direction=Math.abs(ny)>=Math.abs(nx) && Math.abs(ny)>=Math.abs(nz)
                    ? (ny>=0 ? Direction.UP : Direction.DOWN)
                    : Math.abs(nx)>=Math.abs(nz) ? (nx>=0 ? Direction.EAST : Direction.WEST)
                    : (nz>=0 ? Direction.SOUTH : Direction.NORTH);
            for(int vertex=0;vertex<4;vertex++) {
                double[] p=points[face[vertex]];
                emitter.pos(vertex,(float)p[0],(float)p[1],(float)p[2]);
                // Detailed sheets contain six tiles per texture window; preserve one tile per block.
                double u=direction.getAxis()==Direction.Axis.X ? p[2] : p[0];
                double v=direction.getAxis()==Direction.Axis.Y ? p[2] : p[1];
                double centreU=direction.getAxis()==Direction.Axis.X ? fit.centreZ() : fit.centreX();
                double centreV=direction.getAxis()==Direction.Axis.Y ? fit.centreZ() : fit.planeY();
                emitter.uv(vertex,(float)(.5+(u-centreU)/6),(float)(.5+(v-centreV)/6));
                emitter.normal(vertex,(float)nx,(float)ny,(float)nz);
                emitter.color(vertex,-1);
            }
            emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(0));
            emitter.nominalFace(direction); emitter.cullFace(null); emitter.emit();
        }
        return builder.build();
    }
    static double[][] points(FinialSupport.Fit fit) {
        double[][] points=new double[8][3];
        double half=fit.baseWidth()*.5;
        double[][] corners={{-half,-half},{-half,half},{half,half},{half,-half}};
        for(int i=0;i<4;i++) {
            double x=fit.x(corners[i][0],corners[i][1]),z=fit.z(corners[i][0],corners[i][1]);
            points[i]=new double[]{x,fit.bottom(x,z),z};
            points[i+4]=new double[]{x,fit.bodyY(),z};
        }
        return points;
    }
}
