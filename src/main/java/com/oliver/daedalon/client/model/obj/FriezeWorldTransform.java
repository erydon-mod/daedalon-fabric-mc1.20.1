package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** World-projected repeat UVs after rotation, always within the shared 6x6 surface. */
final class FriezeWorldTransform implements RenderContext.QuadTransform {
    private static final FriezeWorldTransform[] CACHE = createCache();
    private final int steps, xTile, yTile, zTile;

    private FriezeWorldTransform(int steps,int xTile,int yTile,int zTile) {
        this.steps=steps; this.xTile=xTile; this.yTile=yTile; this.zTile=zTile;
    }

    private static FriezeWorldTransform[] createCache() {
        FriezeWorldTransform[] result=new FriezeWorldTransform[4*216];
        for (int r=0;r<4;r++) for (int x=0;x<6;x++) for (int y=0;y<6;y++) for (int z=0;z<6;z++)
            result[r*216+x*36+y*6+z]=new FriezeWorldTransform(r,x,y,z);
        return result;
    }

    static FriezeWorldTransform forBlock(int steps,BlockPos pos) {
        return CACHE[steps*216+Math.floorMod(pos.getX(),6)*36+Math.floorMod(pos.getY(),6)*6+Math.floorMod(pos.getZ(),6)];
    }

    @Override public boolean transform(MutableQuadView quad) {
        Direction face=quad.nominalFace();
        if (face != null && face.getAxis().isHorizontal()) {
            for (int i=0;i<steps;i++) face=face.rotateYClockwise();
        }
        for (int v=0;v<4;v++) {
            float x=quad.x(v)-.5F,z=quad.z(v)-.5F;
            float nx=quad.hasNormal(v)?quad.normalX(v):0,nz=quad.hasNormal(v)?quad.normalZ(v):0;
            for (int i=0;i<steps;i++) { float t=x;x=-z;z=t; t=nx;nx=-nz;nz=t; }
            x+=.5F; z+=.5F; float y=quad.y(v);
            quad.pos(v,x,y,z);
            if (quad.hasNormal(v)) quad.normal(v,nx,quad.normalY(v),nz);
            float u=(xTile+x)/6F, textureV=1F-(yTile+y)/6F;
            if (face != null) switch (face) {
                case NORTH -> u=(5-xTile+1-x)/6F;
                case EAST -> u=(5-zTile+1-z)/6F;
                case WEST -> u=(zTile+z)/6F;
                case UP -> textureV=(zTile+z)/6F;
                case DOWN -> textureV=(5-zTile+1-z)/6F;
                default -> { }
            }
            quad.uv(v,u,textureV);
        }
        quad.cullFace(null);
        if (face != null) quad.nominalFace(face);
        return true;
    }
}
