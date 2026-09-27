package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.PanelBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

final class PanelMeshTransformTest {
    @Test void realBoxProjectionWindowsStayInsideEveryMaterialPhaseAtAllSizes() {
        var layout=WorldTexturePhase.detailedSurface();
        // The source projection occupies an edge of the 6m sheet, with mirrored faces at 1.
        for (int size=1;size<=3;size++) for (boolean ceiling:new boolean[]{false,true})
            for (int turn=0;turn<4;turn++) for (boolean mirrored:new boolean[]{false,true}) {
                float[][] uv=new float[4][2];
                float[][] xyz={{0,0,0},{1,0,0},{1,1,0},{0,1,0}};
                for (int i=0;i<4;i++) {
                    uv[i][0]=mirrored?1-xyz[i][0]/6:xyz[i][0]/6;
                    uv[i][1]=1-xyz[i][1]/6;
                }
                MutableQuadView quad=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                        new Class<?>[]{MutableQuadView.class},(proxy,method,args)-> {
                            String name=method.getName();
                            return switch(name) {
                                case "x","y","z" -> xyz[(int)args[0]]["xyz".indexOf(name)];
                                case "u","v" -> uv[(int)args[0]][name.equals("u")?0:1];
                                case "uv","pos" -> {
                                    float[] target=(name.equals("uv")?uv:xyz)[(int)args[0]];
                                    for(int j=0;j<target.length;j++)target[j]=(float)args[j+1];
                                    yield proxy;
                                }
                                case "hasNormal" -> false;
                                case "nominalFace" -> args==null || args.length==0?Direction.SOUTH:proxy;
                                case "cullFace" -> proxy;
                                default -> throw new AssertionError("Unexpected quad edit: "+name);
                            };
                        });
                new PanelMeshTransform(new PanelBlock.Transform(size,ceiling,turn)).transform(quad);
                assertEquals(size/6F,Math.abs(uv[1][0]-uv[0][0]),1e-6,"Stone grain density changed");
                assertEquals(size/6F,Math.abs(uv[2][1]-uv[1][1]),1e-6);
                for (float[] point:uv) for (int phase=0;phase<layout.phaseCount();phase++) {
                    assertTrue(point[0]>=0 && point[0]<=1 && point[1]>=0 && point[1]<=1,"UV escaped source texture");
                    float u=layout.mapU(point[0],phase),v=layout.mapV(point[1]);
                    assertTrue(u>=0 && u<=1 && v>=0 && v<=1,"UV escaped material repeat sheet");
                    // Simulate a sprite surrounded by unrelated atlas textures, away from atlas centre.
                    float atlasU=.125F+u*.0625F,atlasV=.75F+v*.125F;
                    assertTrue(atlasU>=.125F && atlasU<=.1875F && atlasV>=.75F && atlasV<=.875F);
                }
            }
    }
}
