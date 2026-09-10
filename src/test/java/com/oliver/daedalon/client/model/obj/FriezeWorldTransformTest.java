package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class FriezeWorldTransformTest {
    private static final class Quad {
        final float[] position,normal={0,0,1},uv=new float[2];
        Direction face;
        final MutableQuadView view;
        Quad(float x,float y,float z,Direction face) {
            position=new float[]{x,y,z}; this.face=face;
            view=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),new Class[]{MutableQuadView.class},(proxy,method,args) -> {
                String name=method.getName();
                return switch (name) {
                    case "x","y","z" -> position["xyz".indexOf(name)];
                    case "normalX","normalY","normalZ" -> normal["XYZ".indexOf(name.charAt(6))];
                    case "hasNormal" -> true;
                    case "pos","normal" -> {
                        // The test vertex is identical for every corner.
                        if ((int)args[0]==0) {float[] dst=name.equals("pos")?position:normal; for(int i=0;i<3;i++)dst[i]=(float)args[i+1];}
                        yield proxy;
                    }
                    case "uv" -> {if ((int)args[0]==0) {uv[0]=(float)args[1];uv[1]=(float)args[2];} yield proxy;}
                    case "nominalFace" -> { if(args==null || args.length==0)yield this.face;this.face=(Direction)args[0];yield proxy;}
                    case "cullFace" -> proxy;
                    default -> throw new AssertionError("Unexpected vertex edit: "+name);
                };
            });
        }
    }

    @Test void projectionMatchesWorldCoordinatesAcrossAllFacesAndNegativeChunks() {
        for(int steps=0;steps<4;steps++) for(Direction face:Direction.values()) for(int n=-17;n<=17;n++) {
            BlockPos pos=new BlockPos(n,n+1,n-1);
            Quad q=new Quad(.17F,.41F,.23F,face);
            var transform=FriezeWorldTransform.forBlock(steps,pos);
            assertSame(transform,FriezeWorldTransform.forBlock(steps,pos.add(6,6,6)));
            transform.transform(q.view);
            float x=q.position[0],y=q.position[1],z=q.position[2];
            double wx=pos.getX()+x,wy=pos.getY()+y,wz=pos.getZ()+z;
            double expectedU=switch(q.face){case NORTH -> -wx;case EAST -> -wz;case WEST -> wz;default -> wx;};
            double expectedV=switch(q.face){case UP -> wz;case DOWN -> -wz;default -> -wy;};
            assertEquals(mod6(expectedU),q.uv[0],1e-6);
            assertEquals(mod6(expectedV),q.uv[1],1e-6);
            assertTrue(q.uv[0]>=0 && q.uv[0]<=1 && q.uv[1]>=0 && q.uv[1]<=1);
            assertEquals(1,Math.sqrt(q.normal[0]*q.normal[0]+q.normal[1]*q.normal[1]+q.normal[2]*q.normal[2]),1e-6);
        }
    }

    @Test void sourceBlockBoundariesRemainAtlasSafeIncludingTexturePeriodEdges() {
        for(int steps=0;steps<4;steps++) for(Direction face:Direction.values())
            for(int n=-7;n<=7;n++) for(float x:new float[]{0,1}) for(float z:new float[]{0,1}) {
                Quad q=new Quad(x,1,z,face);
                FriezeWorldTransform.forBlock(steps,new BlockPos(n,n,n)).transform(q.view);
                for(float uv:q.uv) assertTrue(uv>=0 && uv<=1,"UV escaped shared texture surface");
            }
    }
    private static double mod6(double value) {return (value-6*Math.floor(value/6))/6;}
}
