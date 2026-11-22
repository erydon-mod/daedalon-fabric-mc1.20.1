package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class LanternFlameTransformTest {
    @Test void flameFollowsEveryBodyTransformButKeepsItsCompleteAuthoredTextureAtBothSizes() {
        for(var mount:LanternBlock.Mount.values()) for(var size:LanternBlock.Size.values())
            for(var facing:Direction.Type.HORIZONTAL) {
                Spy body=new Spy(),flame=new Spy();
                LanternMeshTransform.forMount(mount,size,facing).transform(body.view);
                LanternMeshTransform.forFlame(mount,size,facing).transform(flame.view);
                for(int v=0;v<4;v++) {
                    assertArrayEquals(body.points[v],flame.points[v],1e-7F);
                    assertArrayEquals(body.normals[v],flame.normals[v],1e-7F);
                    assertEquals(v==0||v==3?0:1,flame.u[v],1e-7F,"Small flames must not crop the centre of the PNG");
                    assertEquals(v<2?0:1,flame.v[v],1e-7F);
                    assertEquals(flame.u[v],body.u[v],1e-7F,"Placement must preserve authored panes; opaque world UVs are projected after placement");
                }
                assertEquals(body.face,flame.face);
            }
    }

    private static final class Spy {
        final float[][] points={{.4F,.3F,.6F},{.6F,.3F,.6F},{.6F,.4F,.6F},{.4F,.4F,.6F}};
        final float[][] normals={{0,0,1},{0,0,1},{0,0,1},{0,0,1}};
        final float[] u={0,1,1,0},v={0,0,1,1};
        Direction face=Direction.SOUTH;
        final MutableQuadView view=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class},(proxy,method,args)->switch(method.getName()) {
                    case "x","y","z"->points[(int)args[0]]["xyz".indexOf(method.getName())];
                    case "pos"->{for(int i=0;i<3;i++)points[(int)args[0]][i]=(float)args[i+1];yield proxy;}
                    case "u"->u[(int)args[0]];
                    case "v"->v[(int)args[0]];
                    case "uv"->{u[(int)args[0]]=(float)args[1];v[(int)args[0]]=(float)args[2];yield proxy;}
                    case "hasNormal"->true;
                    case "normalX","normalY","normalZ"->normals[(int)args[0]][method.getName().charAt(6)-'X'];
                    case "normal"->{for(int i=0;i<3;i++)normals[(int)args[0]][i]=(float)args[i+1];yield proxy;}
                    case "nominalFace"->{if(args==null||args.length==0)yield face;face=(Direction)args[0];yield proxy;}
                    case "cullFace"->proxy;
                    default->throw new AssertionError(method.getName());
                });
    }
}
