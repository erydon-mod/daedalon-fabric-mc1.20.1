package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Projects opaque surfaces after placement; only atlas-crossing faces allocate clipped polygons. */
final class LanternSurfaceEmitter {
    private final List<Boolean> repeatMaterials;
    private final int glassTag, flameTag;
    private final RenderContext.QuadTransform materialPass;

    LanternSurfaceEmitter(List<Boolean> repeatMaterials, int glassTag, int flameTag,
                          RenderContext.QuadTransform materialPass) {
        this.repeatMaterials = List.copyOf(repeatMaterials);
        this.glassTag = glassTag;
        this.flameTag = flameTag;
        this.materialPass = materialPass;
    }

    void emit(Mesh mesh, LanternMeshTransform pose, Vec3d offset, BlockPos origin, RenderContext context) {
        emit(mesh,pose,offset,origin,context,-1);
    }

    void emit(Mesh mesh, LanternMeshTransform pose, Vec3d offset, BlockPos origin, RenderContext context, int outputTag) {
        QuadEmitter target = context.getEmitter();
        double[] s = new double[4], t = new double[4];
        mesh.forEach(source -> {
            target.copyFrom(source);
            pose.transform(target);
            for (int v=0; v<4; v++) target.pos(v, target.x(v)+(float)offset.x,
                    target.y(v)+(float)offset.y, target.z(v)+(float)offset.z);
            int tag = ShaderTerrainNormalBridge.decodeObjMaterialTag(target.tag());
            if (tag == glassTag || tag == flameTag) {
                // Glazing and flame cards have complete authored images, independent of body size.
                materialPass.transform(target);
                target.emit();
                return;
            }
            boolean repeat = tag >= 0 && tag < repeatMaterials.size() && repeatMaterials.get(tag);
            Direction projection = projectionFace(target);
            double minS=Double.POSITIVE_INFINITY, maxS=Double.NEGATIVE_INFINITY;
            double minT=Double.POSITIVE_INFINITY, maxT=Double.NEGATIVE_INFINITY;
            for (int v=0; v<4; v++) {
                s[v] = LanternWorldUv.s(projection, origin, target.x(v), target.z(v));
                t[v] = LanternWorldUv.t(projection, origin, target.y(v), target.z(v));
                minS=Math.min(minS,s[v]); maxS=Math.max(maxS,s[v]);
                minT=Math.min(minT,t[v]); maxT=Math.max(maxT,t[v]);
            }
            double uOrigin=LanternWorldUv.origin(minS,repeat), vOrigin=LanternWorldUv.origin(minT,repeat);
            double period=repeat?6:1;
            if (maxT <= vOrigin+period && (repeat || maxS <= uOrigin+period)) {
                for (int v=0; v<4; v++) target.uv(v,(float)((s[v]-uOrigin)/(repeat?12:1)),
                        (float)((t[v]-vOrigin)/period));
                finish(target,repeat,outputTag);
                return;
            }
            // Do not wrap vertices independently: interpolate geometry at the tile seam instead.
            int count=samePosition(target,2,3)?3:4;
            List<LanternWorldUv.Vertex> vertices=new ArrayList<>(count);
            Direction nominal=target.nominalFace();
            for (int v=0; v<count; v++) vertices.add(new LanternWorldUv.Vertex(
                    target.x(v),target.y(v),target.z(v),target.normalX(v),target.normalY(v),target.normalZ(v),s[v],t[v]));
            for (var patch:LanternWorldUv.split(vertices,repeat)) {
                var polygon=patch.vertices();
                for (int i=1; i<polygon.size()-1; i++) {
                    target.copyFrom(source).nominalFace(nominal).cullFace(null);
                    for (int v=0; v<4; v++) {
                        var point=polygon.get(v==0?0:v==1?i:i+1);
                        target.pos(v,point.x(),point.y(),point.z());
                        target.normal(v,point.nx(),point.ny(),point.nz());
                        target.uv(v,patch.u(point),patch.v(point));
                    }
                    finish(target,repeat,outputTag);
                }
            }
        });
    }

    private void finish(QuadEmitter target, boolean repeat, int outputTag) {
        if (repeat) FriezeAtlasRepeatGuard.protect(target);
        materialPass.transform(target);
        if (outputTag>=0) target.tag(ShaderTerrainNormalBridge.encodeObjTag(outputTag));
        target.emit();
    }

    private static boolean samePosition(QuadView quad,int a,int b) {
        return quad.x(a)==quad.x(b) && quad.y(a)==quad.y(b) && quad.z(a)==quad.z(b);
    }

    static Direction projectionFace(QuadView quad) {
        float ax=quad.x(1)-quad.x(0), ay=quad.y(1)-quad.y(0), az=quad.z(1)-quad.z(0);
        float bx=quad.x(2)-quad.x(0), by=quad.y(2)-quad.y(0), bz=quad.z(2)-quad.z(0);
        float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
        float horizontal=Math.max(Math.abs(nx),Math.abs(nz));
        // Shallow roofs and undersides need X/Z mapping too. A wall projection
        // collapses their short vertical span into stretched rows of pixels.
        if (Math.abs(ny)>=horizontal-1e-5F*(Math.abs(ny)+horizontal))
            return ny>=0?Direction.UP:Direction.DOWN;
        // The plane normal, not a triangle's centroid, chooses its common basis.
        if (Math.abs(nx)>=Math.abs(nz)-1e-5F*(Math.abs(nx)+Math.abs(nz)))
            return nx>=0?Direction.EAST:Direction.WEST;
        return nz>=0?Direction.SOUTH:Direction.NORTH;
    }
}
