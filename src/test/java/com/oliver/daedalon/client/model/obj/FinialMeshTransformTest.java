package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FinialSupport;
import com.oliver.daedalon.block.TwoSizeDecorBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class FinialMeshTransformTest {
    @Test void mountedOrnamentsKeepVerticalNormalsHeightAndStoneDensityAtEveryWallAngle() {
        for(double angle:new double[]{0,-Math.PI/4,-Math.atan(2),Math.atan(2)})
            for(int quarter=0;quarter<4;quarter++) for(var size:TwoSizeDecorBlock.Size.values()) {
                double yaw=angle+quarter*Math.PI/2;
                var fit=new FinialSupport.Fit(-.15,.3,-1.4,-2,0,yaw,1.3,.0625,true);
                float scale=size==TwoSizeDecorBlock.Size.SMALL ? .5F : 1;
                float[][] xyz={{0,0,0},{1,0,0},{1,2,1},{0,2,1}};
                float[][] uv={{.5F,.5F},{2F/3,.5F},{2F/3,5F/6},{.5F,5F/6}};
                float[][] normals={{.6F,.8F,0},{.6F,.8F,0},{.6F,.8F,0},{.6F,.8F,0}};
                Direction[] nominal={Direction.EAST},cull={Direction.EAST};
                MutableQuadView quad=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                        new Class<?>[]{MutableQuadView.class},(proxy,method,args) -> switch(method.getName()) {
                            case "x","y","z" -> xyz[(int)args[0]]["xyz".indexOf(method.getName())];
                            case "u","v" -> uv[(int)args[0]][method.getName().equals("u") ? 0 : 1];
                            case "normalX","normalY","normalZ" -> normals[(int)args[0]]["XYZ".indexOf(method.getName().charAt(6))];
                            case "hasNormal" -> true;
                            case "pos","uv","normal" -> {
                                float[] values=(method.getName().equals("pos") ? xyz : method.getName().equals("uv") ? uv : normals)[(int)args[0]];
                                for(int i=0;i<values.length;i++) values[i]=(float)args[i+1];
                                yield proxy;
                            }
                            case "nominalFace" -> { if(args==null || args.length==0) yield nominal[0]; nominal[0]=(Direction)args[0]; yield proxy; }
                            case "cullFace" -> { cull[0]=(Direction)args[0]; yield proxy; }
                            default -> throw new AssertionError("Unexpected finial quad operation: "+method.getName());
                        });
                var transform=FinialMeshTransform.forFit(fit,size);
                assertSame(transform,FinialMeshTransform.forFit(fit,size),"Material variants must reuse the mounting transform");
                assertTrue(transform.transform(quad));
                assertEquals(fit.bodyY(),xyz[0][1],1e-6);
                assertEquals(2*scale,xyz[2][1]-xyz[0][1],1e-6,"A sloped mounting plane must never pitch the ornament");
                assertEquals(fit.centreX()-.5*scale*Math.cos(yaw)+.5*scale*Math.sin(yaw),xyz[0][0],1e-6);
                assertEquals(fit.centreZ()-.5*scale*Math.sin(yaw)-.5*scale*Math.cos(yaw),xyz[0][2],1e-6);
                for(float[] normal:normals) {
                    assertEquals(.8,normal[1],1e-6,"Yaw must preserve the original vertical normal");
                    assertEquals(.6*Math.cos(yaw),normal[0],1e-6);
                    assertEquals(.6*Math.sin(yaw),normal[2],1e-6);
                }
                assertEquals(scale/6,uv[1][0]-uv[0][0],1e-6);
                assertEquals(2*scale/6,uv[2][1]-uv[1][1],1e-6);
                for(float[] point:uv) for(float component:point) assertTrue(component>=0 && component<=1);
                assertNull(cull[0],"An overhanging ornament must not be culled against an unrelated owner face");
            }
    }

    @Test void editableSquareBaseKeepsAHorizontalTopAndSeatsEveryLowerCornerOnItsPlane() {
        for(double gradient:new double[]{0,.5,1,2}) for(double yaw:new double[]{0,Math.PI/2,-Math.PI/4,Math.atan(2)}) {
            double width=1.3;
            double gx=-gradient*Math.cos(yaw),gz=-gradient*Math.sin(yaw);
            var fit=new FinialSupport.Fit(-.15,.3,-1.4,gx,gz,yaw,width,-1.4+gradient*width/2+.125,true);
            double[][] points=FinialBaseMesh.points(fit);
            for(int i=0;i<4;i++) {
                assertEquals(fit.bottom(points[i][0],points[i][2]),points[i][1],1e-10);
                assertEquals(fit.bodyY(),points[i+4][1],1e-10);
                assertEquals(points[i][0],points[i+4][0],1e-10);
                assertEquals(points[i][2],points[i+4][2],1e-10);
                double[] next=points[(i+1)%4];
                assertEquals(width,Math.hypot(next[0]-points[i][0],next[2]-points[i][2]),1e-10);
                assertTrue(points[i+4][1]-points[i][1]>=.125-1e-10);
            }
        }
    }
}
