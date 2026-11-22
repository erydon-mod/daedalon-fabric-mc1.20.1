package com.oliver.daedalon.client.glaux.model;

import com.oliver.daedalon.glaux.GlauxBlock;
import com.oliver.daedalon.glaux.GlauxRegistry;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Opt-in probes against actual baked chunk/head meshes and the current atlas. */
public final class GlauxRenderChecks {
    private GlauxRenderChecks() { }
    public static Map<String,Object> inspect(MinecraftClient client){
        Map<String,Object> report=new LinkedHashMap<>();Sprite bronze=client.getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(new Identifier("daedalon","block/bronze"));
        require(bronze.getContents().getId().equals(new Identifier("daedalon","block/bronze")),"Missing Glaux bronze sprite");
        report.put("sprite",bronze.getContents().getId().toString());report.put("spriteWidth",bronze.getContents().getWidth());report.put("spriteHeight",bronze.getContents().getHeight());
        report.put("textureProvider",client.getResourceManager().getResource(new Identifier("daedalon","textures/block/bronze.png")).orElseThrow().getResourcePackName());
        Map<String,Object> body=new LinkedHashMap<>();
        for(Direction facing:Direction.Type.HORIZONTAL){Set<String> lower=null,upper=null;for(DoubleBlockHalf half:DoubleBlockHalf.values()){
            var state=GlauxRegistry.BLOCK.getDefaultState().with(GlauxBlock.FACING,facing).with(GlauxBlock.HALF,half);var model=client.getBlockRenderManager().getModel(state);
            require(model instanceof FabricBakedModel&&!((FabricBakedModel)model).isVanillaAdapter(),"Glaux body did not resolve a Fabric OBJ model: "+state);
            Capture capture=new Capture(bronze,half==DoubleBlockHalf.LOWER?1:0);((FabricBakedModel)model).emitBlockQuads(null,state,half==DoubleBlockHalf.LOWER?BlockPos.ORIGIN:BlockPos.ORIGIN.up(),()->Random.create(0),capture.context);capture.flush();
            require(capture.transforms.isEmpty(),"Unbalanced Glaux body transforms");require(capture.quads>0,"Glaux static body is empty");require(capture.minY>=-1e-5&&capture.maxY<=1.00001,"Glaux static body crosses its vertical chunk cell");
            body.put(facing.asString()+"_"+half.asString(),Map.of("quads",capture.quads,"minY",capture.minY,"maxY",capture.maxY,"seamSamples",capture.seam.size()));if(half==DoubleBlockHalf.LOWER)lower=capture.seam;else upper=capture.seam;
        }require(lower!=null&&!lower.isEmpty()&&lower.equals(upper),"Loaded Glaux body seam UV phase differs: "+facing);}
        report.put("body",body);report.put("itemGuiIcon",com.oliver.daedalon.client.model.obj.GlauxObjBridge.itemIconDiagnostics(client.getItemRenderer().getModel(GlauxRegistry.ITEM.getDefaultStack(),null,null,0)));GlauxHeadMeshes heads=Objects.requireNonNull(GlauxHeadMeshes.current,"Glaux head cache missing");GlauxRig rig=Objects.requireNonNull(GlauxRig.current,"Glaux pose recipe missing");require(rig.hinges.length==29,"Glaux feather hinge recipe changed");
        int near=inspectHeads(heads.near(),bronze),middle=inspectHeads(heads.middle(),bronze),far=inspectHeads(heads.far(),bronze);require(near>middle&&middle>far&&near<=20000&&middle<3500&&far<600,"Glaux cached LOD budgets/order failed");
        report.put("headQuads",Map.of("near",near,"middle",middle,"far",far));report.put("headCacheIdentity",System.identityHashCode(heads));report.put("headParts",Map.of("near",heads.near().size(),"middle",heads.middle().size(),"far",heads.far().size()));report.put("bodySeamAllFourFacings",true);report.put("materialPolicy","Fixed canonical bronze sprite; authored common-space UV; no Continuity requirement");return report;
    }
    private static int inspectHeads(java.util.List<GlauxHeadMeshes.Part> parts,Sprite bronze){int quads=0;for(var part:parts){float[] v=part.vertices();require(v.length%32==0,"Glaux head buffer is not complete quads");quads+=v.length/32;for(int i=0;i<v.length;i+=8){for(int j=0;j<8;j++)require(Float.isFinite(v[i+j]),"Non-finite Glaux head vertex");require(v[i+6]>=bronze.getMinU()-1e-6&&v[i+6]<=bronze.getMaxU()+1e-6&&v[i+7]>=bronze.getMinV()-1e-6&&v[i+7]<=bronze.getMaxV()+1e-6,"Glaux head UV escaped bronze sprite");double normal=v[i+3]*v[i+3]+v[i+4]*v[i+4]+v[i+5]*v[i+5];require(normal>.8&&normal<1.2,"Glaux head normal lost rigid surface orientation");}}return quads;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class Capture {
        final ArrayDeque<RenderContext.QuadTransform> transforms=new ArrayDeque<>();
        final net.fabricmc.fabric.api.renderer.v1.Renderer renderer=Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer());
        MeshBuilder pending=renderer.meshBuilder();final QuadEmitter scratch=renderer.meshBuilder().getEmitter();
        final Set<String> seam=new HashSet<>();final Sprite sprite;final double seamY;
        int quads;double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        final RenderContext context=(RenderContext)Proxy.newProxyInstance(RenderContext.class.getClassLoader(),new Class<?>[]{RenderContext.class},(proxy,method,args)->switch(method.getName()){
            case "getEmitter"->pending.getEmitter();case "pushTransform"->{flush();transforms.push((RenderContext.QuadTransform)args[0]);yield null;}case "popTransform"->{flush();transforms.pop();yield null;}default->throw new AssertionError("Unexpected Glaux render operation: "+method.getName());});
        Capture(Sprite sprite,double seamY){this.sprite=sprite;this.seamY=seamY;}
        void flush(){var mesh=pending.build();pending=renderer.meshBuilder();mesh.forEach(q->{scratch.copyFrom(q);for(var transform:transforms)if(!transform.transform(scratch))return;quads++;for(int i=0;i<4;i++){float y=scratch.y(i),u=scratch.u(i),v=scratch.v(i);minY=Math.min(minY,y);maxY=Math.max(maxY,y);require(u>=sprite.getMinU()-1e-6&&u<=sprite.getMaxU()+1e-6&&v>=sprite.getMinV()-1e-6&&v<=sprite.getMaxV()+1e-6,"Glaux body UV escaped bronze sprite");if(Math.abs(y-seamY)<1e-6)seam.add(Math.round(scratch.x(i)*1e6)+","+Math.round(scratch.z(i)*1e6)+","+Math.round(u*1e7)+","+Math.round(v*1e7));}});}
    }
}
