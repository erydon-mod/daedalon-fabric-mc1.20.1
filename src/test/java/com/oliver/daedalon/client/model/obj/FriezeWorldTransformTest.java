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
            double expectedV=switch(q.face){case UP -> wz;case DOWN -> -wz;default -> 1-wy;};
            assertEquals(mod6(expectedU),q.uv[0],1e-6);
            assertEquals(mod6(expectedV),q.uv[1],1e-6);
            assertTrue(q.uv[0]>=0 && q.uv[0]<=1 && q.uv[1]>=0 && q.uv[1]<=1);
            assertEquals(1,Math.sqrt(q.normal[0]*q.normal[0]+q.normal[1]*q.normal[1]+q.normal[2]*q.normal[2]),1e-6);
        }
    }

    @Test void sourceBlockBoundariesRemainAtlasSafeIncludingTexturePeriodEdges() {
        for(int steps=0;steps<4;steps++) for(Direction face:Direction.values())
            for(int n=-7;n<=7;n++) for(float x:new float[]{0,1})
                for(float y:new float[]{0,.5F,1}) for(float z:new float[]{0,1}) {
                Quad q=new Quad(x,y,z,face);
                FriezeWorldTransform.forBlock(steps,new BlockPos(n,n,n)).transform(q.view);
                for(float uv:q.uv) assertTrue(uv>=0 && uv<=1,"UV escaped shared texture surface");
            }
    }

    @Test void projectionMatchesErydonRepeatTileAndCroppedUvsForEveryFaceAndFacing() {
        for(int steps=0;steps<4;steps++) for(Direction face:Direction.values()) for(int n=-17;n<=17;n++) {
            BlockPos pos=new BlockPos(n,n+1,n-1);
            Quad q=worldQuad(steps,face,.17F,.41F,.23F);
            FriezeWorldTransform.forBlock(steps,pos).transform(q.view);
            int tileX=switch(face){case NORTH -> -pos.getX()-1;case EAST -> -pos.getZ()-1;
                case WEST -> pos.getZ();default -> pos.getX();};
            int tileY=switch(face){case UP -> pos.getZ();case DOWN -> -pos.getZ()-1;default -> -pos.getY();};
            float localU=switch(face){case NORTH -> 1-q.position[0];case EAST -> 1-q.position[2];
                case WEST -> q.position[2];default -> q.position[0];};
            float localV=switch(face){case UP -> q.position[2];case DOWN -> 1-q.position[2];default -> 1-q.position[1];};
            assertEquals((Math.floorMod(tileX,6)+localU)/6F,q.uv[0],1e-6);
            assertEquals((Math.floorMod(tileY,6)+localV)/6F,q.uv[1],1e-6);
        }
    }

    @Test void coplanarJoinsPreservePhaseAcrossHorizontalAndVerticalTexturePeriodEdges() {
        for(int steps=0;steps<4;steps++) for(Direction face:Direction.values()) for(int n=-7;n<=7;n++) {
            BlockPos pos=new BlockPos(n,n+1,n-1);
            // Each pair describes the same physical point from the two adjacent owner cells.
            if(face.getAxis()==Direction.Axis.X) {
                assertJoin(steps,face,pos,pos.south(),.5F,.41F,1,.5F,.41F,0);
            } else {
                assertJoin(steps,face,pos,pos.east(),1,.41F,.5F,0,.41F,.5F);
            }
            if(face.getAxis().isHorizontal()) {
                assertJoin(steps,face,pos,pos.up(),.17F,1,.23F,.17F,0,.23F);
            } else {
                assertJoin(steps,face,pos,pos.south(),.17F,.41F,1,.17F,.41F,0);
            }
        }
    }

    private static void assertJoin(int steps,Direction face,BlockPos first,BlockPos second,
                                   float ax,float ay,float az,float bx,float by,float bz) {
        Quad a=worldQuad(steps,face,ax,ay,az),b=worldQuad(steps,face,bx,by,bz);
        FriezeWorldTransform.forBlock(steps,first).transform(a.view);
        FriezeWorldTransform.forBlock(steps,second).transform(b.view);
        for(int axis=0;axis<2;axis++) {
            float difference=Math.abs(a.uv[axis]-b.uv[axis]);
            assertEquals(0,Math.min(difference,Math.abs(1-difference)),1e-6);
        }
    }

    private static Quad worldQuad(int steps,Direction worldFace,float worldX,float y,float worldZ) {
        float x=worldX-.5F,z=worldZ-.5F;
        Direction sourceFace=worldFace;
        for(int i=0;i<steps;i++) {
            float previousX=x; x=z; z=-previousX;
            if(sourceFace.getAxis().isHorizontal()) sourceFace=sourceFace.rotateYCounterclockwise();
        }
        return new Quad(x+.5F,y,z+.5F,sourceFace);
    }

    private static double mod6(double value) {return (value-6*Math.floor(value/6))/6;}
}
