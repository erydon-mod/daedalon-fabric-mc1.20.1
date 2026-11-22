package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.texture.Sprite;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class LanternTexturePipelineTest {
    @Test void actualMaterialPassSeparatesGlassAndFlameFromStoneLeadAndBronze() throws Exception {
        Sprite bronze=sentinel(),glass=sentinel(),lead=sentinel(),stone=sentinel(),flame=sentinel();
        List<Sprite> sprites=List.of(bronze,glass,lead,stone,flame);
        RenderMaterial translucent=(RenderMaterial)Proxy.newProxyInstance(RenderMaterial.class.getClassLoader(),new Class<?>[]{RenderMaterial.class},
                (proxy,method,args)->{throw new AssertionError("The marker material must only be assigned, never read here");});
        RenderMaterial emissive=(RenderMaterial)Proxy.newProxyInstance(RenderMaterial.class.getClassLoader(),new Class<?>[]{RenderMaterial.class},
                (proxy,method,args)->{throw new AssertionError("The flame marker must only be assigned, never read here");});
        var constructor=Class.forName(ObjMeshBakedModel.class.getName()+"$TextureTransform")
                .getDeclaredConstructor(List.class,List.class,WorldTexturePhase.Layout.class,int.class,boolean.class,
                        RenderMaterial.class,RenderMaterial.class,int.class,RenderMaterial.class,int.class);
        constructor.setAccessible(true);
        var transform=(RenderContext.QuadTransform)constructor.newInstance(sprites,List.of(false,false,false,true,false),WorldTexturePhase.blockSurface(),2,false,null,translucent,1,emissive,4);
        for(int tag=0;tag<5;tag++) {
            Spy quad=new Spy(ShaderTerrainNormalBridge.encodeObjTag(tag));
            assertTrue(transform.transform(quad.view));
            assertSame(sprites.get(tag),quad.sprite);
            assertEquals(tag,ShaderTerrainNormalBridge.decodeObjMaterialTag(quad.tag));
            assertEquals(tag==1,ShaderTerrainNormalBridge.isGlazingObjQuad(quad.tag));
            assertEquals(tag==4,ShaderTerrainNormalBridge.isFlameObjQuad(quad.tag));
            if(tag==1) assertSame(translucent,quad.material);
            else if(tag==4) assertSame(emissive,quad.material);
            else assertNull(quad.material,"Opaque metal/stone acquired a translucent material");
            assertEquals(tag==3?WorldTexturePhase.blockSurface().mapU(.25F,2):.25F,quad.u[0],1e-7);
        }
    }
    private static Sprite sentinel() throws Exception {
        var type=Class.forName("sun.misc.Unsafe");var field=type.getDeclaredField("theUnsafe");field.setAccessible(true);
        return (Sprite)type.getMethod("allocateInstance",Class.class).invoke(field.get(null),Sprite.class);
    }
    private static final class Spy {
        int tag;Sprite sprite;RenderMaterial material;
        float[] u={.25F,.25F,.25F,.25F},v={.75F,.75F,.75F,.75F};
        Spy(int tag) { this.tag=tag; }
        final MutableQuadView view=(MutableQuadView)Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),new Class<?>[]{MutableQuadView.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "tag"->{if(args==null||args.length==0)yield tag;tag=(int)args[0];yield proxy;}
                    case "material"->{if(args==null||args.length==0)yield material;material=(RenderMaterial)args[0];yield proxy;}
                    case "u"->u[(int)args[0]];
                    case "v"->v[(int)args[0]];
                    case "uv"->{u[(int)args[0]]=(float)args[1];v[(int)args[0]]=(float)args[2];yield proxy;}
                    case "spriteBake"->{sprite=(Sprite)args[0];yield proxy;}
                    default->throw new AssertionError("Unexpected material-pass operation: "+method.getName());
                });
    }
}
