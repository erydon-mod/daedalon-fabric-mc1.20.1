package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.LanternBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;

/** Opt-in launch assertions against the loaded renderer and atlas; never registered normally. */
public final class LanternFlameClientChecks {
    private LanternFlameClientChecks() { }

    public static void register() {
        boolean[] completed={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(completed[0] || client.getOverlay()!=null) return;
            completed[0]=true;
            run(client);
        });
    }

    private static void run(MinecraftClient client) {
        var renderer=Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer(),"Client renderer unavailable");
        var material=LanternFlame.material(renderer.materialFinder());
        require(material.blendMode()==BlendMode.TRANSLUCENT && material.emissive()
                && material.disableDiffuse() && material.ambientOcclusion()==TriState.FALSE,"Flame material flags changed");
        Sprite sprite=client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(LanternFlame.TEXTURE);
        Map<String,Integer> flameCounts=new HashMap<>();
        int cases=0;
        BlockPos[] origins={BlockPos.ORIGIN,new BlockPos(-6,5,6),new BlockPos(5,-6,-1),new BlockPos(6,-1,-6),new BlockPos(-1,6,5)};
        Direction[] facings={Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
        int finishIndex=0;
        for(String finish:List.of("aganite","bronze","silver")) {
            for(var style:LanternBlock.Style.values()) {
                var block=Registries.BLOCK.get(new Identifier("daedalon",finish+"_"+style.id()+"_lantern"));
                require(block instanceof LanternBlock,"Missing lantern finish/design: "+finish+"/"+style);
                for(var mount:LanternBlock.Mount.values()) for(var size:LanternBlock.Size.values()) {
                    BlockState base=block.getDefaultState().with(LanternBlock.MOUNT,mount).with(LanternBlock.SIZE,size);
                    int seed=finishIndex+style.ordinal()+mount.ordinal()+size.ordinal();
                    Capture hidden=capture(client,base.with(LanternBlock.FACING,Direction.SOUTH).with(LanternBlock.LIT,false),sprite,origins[seed%origins.length]);
                    require(hidden.flames==0,"OFF emitted flame geometry");
                    cases++;
                    for(int facing=0;facing<facings.length;facing++) {
                        var brightness=facing%2==0?LanternBlock.Brightness.FULL:LanternBlock.Brightness.LOW;
                        BlockState on=base.with(LanternBlock.FACING,facings[facing]).with(LanternBlock.BRIGHTNESS,brightness).with(LanternBlock.LIT,true);
                        BlockPos origin=origins[(seed+facing)%origins.length];
                        Capture shown=capture(client,on,sprite,origin);
                        require(shown.flames>0,"ON has no flame geometry");
                        if(facing==0) require(hidden.other==shown.other && hidden.glass==shown.glass,"Lighting changed the original body or panes");
                        require(Math.abs(shown.minU-sprite.getMinU())<1e-5F && Math.abs(shown.maxU-sprite.getMaxU())<1e-5F
                                && Math.abs(shown.minV-sprite.getMinV())<1e-5F && Math.abs(shown.maxV-sprite.getMaxV())<1e-5F,
                                "Flame UVs cropped or escaped the stitched sprite: "+finish+"/"+style+"/"+mount+"/"+size+"/"+facings[facing]+"/"+origin);
                        Integer previous=flameCounts.put(style+"/"+mount+"/"+size,shown.flames);
                        require(previous==null || previous==shown.flames,"Brightness changed flame geometry");
                        cases++;
                    }
                }
            }
            finishIndex++;
        }
        LanternItemRenderClientChecks.run(client);
        LanternIconClientChecks.run(client);
        Daedalon.LOGGER.info("DAEDALON_LANTERN_FLAME_CHECKS_OK: {} ON/OFF cases; aganite/bronze/silver, all designs/mounts/sizes/facings, atlas filter clearance, opaque AO and isolated glass/flame flags",cases);
    }

    private static Capture capture(MinecraftClient client,BlockState state,Sprite sprite,BlockPos origin) {
        var baked=ObjMeshBakedModel.unwrapAttachedModel(client.getBlockRenderManager().getModel(state));
        require(baked instanceof ObjMeshBakedModel,"Lantern has no loaded OBJ model: "+state);
        ObjMeshBakedModel model=(ObjMeshBakedModel)baked;
        Capture capture=new Capture(client,model,sprite);
        model.emitBlockQuads(null,state,origin,()->Random.create(0),capture.context);
        capture.flush();
        require(capture.transforms.isEmpty(),"Unbalanced lantern render transforms");
        return capture;
    }

    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }

    /** Uses the real mutable quad and material implementation, discarding output after inspection. */
    private static final class Capture {
        final ArrayDeque<RenderContext.QuadTransform> transforms=new ArrayDeque<>();
        final ObjMeshBakedModel model;
        final Sprite sprite;
        final List<Sprite> bodySprites;
        int flames,glass,other;
        float minU=Float.POSITIVE_INFINITY,minV=Float.POSITIVE_INFINITY;
        float maxU=Float.NEGATIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        final Renderer renderer=Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer());
        MeshBuilder pending=renderer.meshBuilder();
        final QuadEmitter raw=renderer.meshBuilder().getEmitter();
        final RenderContext context=(RenderContext)Proxy.newProxyInstance(RenderContext.class.getClassLoader(),new Class<?>[]{RenderContext.class},(proxy,method,args)->switch(method.getName()) {
            case "getEmitter"->pending.getEmitter();
            case "pushTransform"->{flush();transforms.push((RenderContext.QuadTransform)args[0]);yield null;}
            case "popTransform"->{flush();transforms.pop();yield null;}
            default->throw new AssertionError("Unexpected lantern render operation: "+method.getName());
        });

        Capture(MinecraftClient client,ObjMeshBakedModel model,Sprite sprite) {
            this.model=model;this.sprite=sprite;
            var atlas=client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            bodySprites=model.materialTextureIds().stream().map(atlas).toList();
        }

        private void flush() {
            // Mesh.outputTo may require the renderer's concrete emitter type. Accumulate
            // into it, then inspect a real scratch quad before changing the transform stack.
            var mesh=pending.build();
            pending=renderer.meshBuilder();
            mesh.forEach(quad->{
                raw.copyFrom(quad);
                for(var transform:transforms) if(!transform.transform(raw)) return;
                // Shared hanging hardware is baked with its lead sprite and
                // retagged into this parent model's material namespace.
                inspect(bodySprites);
            });
        }

        private void inspect(List<Sprite> sprites) {
            var material=raw.material();
            int tag=raw.tag();
            int materialTag=ShaderTerrainNormalBridge.decodeObjMaterialTag(tag);
            require(materialTag>=0 && materialTag<sprites.size(),"Emitted lantern has no chosen material sprite");
            Sprite chosen=sprites.get(materialTag);
            for(int v=0;v<4;v++) require(Float.isFinite(raw.u(v)) && Float.isFinite(raw.v(v))
                    && raw.u(v)>=chosen.getMinU()-1e-6F && raw.u(v)<=chosen.getMaxU()+1e-6F
                    && raw.v(v)>=chosen.getMinV()-1e-6F && raw.v(v)<=chosen.getMaxV()+1e-6F,
                    "Lantern UV left its actual chosen sprite: "+model.materialTextureIds()+" tag="+materialTag+" uv="+raw.u(v)+","+raw.v(v));
            if(ShaderTerrainNormalBridge.isFlameObjQuad(tag)) {
                flames++;
                require(model.materialTextureIds().get(ShaderTerrainNormalBridge.decodeObjMaterialTag(tag)).equals(LanternFlame.TEXTURE),"Flame tag has the wrong sprite");
                require(material.blendMode()==BlendMode.TRANSLUCENT && material.emissive()
                        && material.disableDiffuse() && material.ambientOcclusion()==TriState.FALSE,"Emitted flame lost its render flags");
                for(int v=0;v<4;v++) {
                    minU=Math.min(minU,raw.u(v));maxU=Math.max(maxU,raw.u(v));
                    minV=Math.min(minV,raw.v(v));maxV=Math.max(maxV,raw.v(v));
                    require(raw.u(v)>=sprite.getMinU()-1e-5F && raw.u(v)<=sprite.getMaxU()+1e-5F
                            && raw.v(v)>=sprite.getMinV()-1e-5F && raw.v(v)<=sprite.getMaxV()+1e-5F,"Flame left its atlas sprite");
                }
            } else if(ShaderTerrainNormalBridge.isGlazingObjQuad(tag)) {
                glass++;
                require(material.blendMode()==BlendMode.TRANSLUCENT && !material.emissive(),"Glass acquired flame emission");
            } else {
                other++;
                require(material.blendMode()!=BlendMode.TRANSLUCENT && !material.emissive(),"Opaque hardware/stone acquired flame flags");
                require(material.ambientOcclusion()==TriState.TRUE && !material.disableDiffuse(),
                        "Block-light emission changed the lantern's ordinary surface shading");
                Identifier texture=model.materialTextureIds().get(materialTag);
                if (LanternGlazing.LEAD_TEXTURE.equals(texture) || ObjGuiMetalLighting.isMetalTexture(texture)) {
                    float halfU=(chosen.getMaxU()-chosen.getMinU())*.5F/chosen.getContents().getWidth();
                    float halfV=(chosen.getMaxV()-chosen.getMinV())*.5F/chosen.getContents().getHeight();
                    for(int v=0;v<4;v++) require(raw.u(v)>=chosen.getMinU()+halfU-1e-7F
                            && raw.u(v)<=chosen.getMaxU()-halfU+1e-7F
                            && raw.v(v)>=chosen.getMinV()+halfV-1e-7F
                            && raw.v(v)<=chosen.getMaxV()-halfV+1e-7F,
                            "Opaque lantern filter footprint reached an adjacent atlas sprite: "+texture);
                }
            }
        }
    }
}
