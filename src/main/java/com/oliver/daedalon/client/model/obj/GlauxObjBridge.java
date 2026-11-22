package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.client.glaux.model.GlauxHeadMeshes;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Glaux-only adapter to the existing OBJ parser, UV projector and mesh baker. */
public final class GlauxObjBridge {
    public static final Identifier BRONZE=new Identifier("daedalon","block/bronze");
    private GlauxObjBridge() { }
    public static Prepared prepare(ResourceManager manager) throws IOException {
        Map<String,Source> sources=new LinkedHashMap<>();
        for(String name:List.of("body_lower","body_upper","item","head_near","head_mid","head_far")) {
            ObjMeshDefinition definition=ObjMeshDefinition.load(manager,new Identifier("daedalon","models/mesh/glaux_"+name+".json"));
            sources.put(name,new Source(definition,ObjMeshParser.parse(manager,definition)));
        }
        return new Prepared(sources);
    }
    /** Opt-in live proof that REI/Creative use the shared silhouette cache. */
    public static Map<String,Object> itemIconDiagnostics(BakedModel baked){
        BakedModel unwrapped=ObjMeshBakedModel.unwrapAttachedModel(baked);
        if(!(unwrapped instanceof ObjMeshBakedModel model)||model.guiIconTemplate()==null)
            throw new IllegalStateException("Glaux item is missing its shared GUI silhouette template");
        ObjGuiIconTemplate icon=model.guiIconTemplate();
        return Map.of("sourceQuads",icon.sourceQuadCount(),"coveredPixels",icon.coveredSampleCount(),"resolution",icon.resolution());
    }
    private record Source(ObjMeshDefinition definition,ObjMeshData data) { }
    public static final class Prepared {
        private final Map<String,Source> sources;
        private final Map<String,ObjMeshBakedModel.GeometryBakeResult> staticGeometry=new LinkedHashMap<>();
        private GlauxHeadMeshes heads;
        private ObjGuiIconTemplate itemIcon;
        private Prepared(Map<String,Source> sources){this.sources=sources;}
        public synchronized BakedModel staticModel(String kind,BakedModel metadata,Function<SpriteIdentifier,Sprite> getter,Identifier modelId) {
            ObjMeshBakedModel.GeometryBakeResult geometry=staticGeometry.get(kind);
            if(geometry==null){Source source=java.util.Objects.requireNonNull(sources.remove(kind),"Missing Glaux source "+kind);geometry=ObjMeshBakedModel.bakeGeometry(source.definition,source.data);staticGeometry.put(kind,geometry);}
            if(kind.equals("item")&&itemIcon==null){
                itemIcon=ObjGuiIconTemplate.create(geometry.mesh(),
                        metadata.getTransformation().getTransformation(ModelTransformationMode.GUI),
                        geometry.emittedQuadCount(),1.0F);
            }
            Sprite sprite=getter.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,BRONZE));
            List<Sprite> sprites=geometry.materialNames().stream().map(ignored->sprite).toList();
            return ObjMeshBakedModel.materialize(geometry,null,metadata,sprite,sprites,
                    geometry.materialNames().stream().map(ignored->false).toList(),WorldTexturePhase.blockSurface(),kind.equals("item")?itemIcon:null,
                    geometry.materialNames().stream().map(ignored->BRONZE).toList(),modelId,null,null);
        }
        public synchronized GlauxHeadMeshes heads(Function<SpriteIdentifier,Sprite> getter) {
            if(heads!=null)return heads;
            Sprite sprite=getter.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,BRONZE));
            heads=new GlauxHeadMeshes(head("head_near",sprite),head("head_mid",sprite),head("head_far",sprite));
            return heads;
        }
        private List<GlauxHeadMeshes.Part> head(String kind,Sprite sprite) {
            Source source=java.util.Objects.requireNonNull(sources.remove(kind),"Missing Glaux head source "+kind);Map<String,List<ObjMeshData.Face>> groups=new LinkedHashMap<>();
            for(ObjMeshData.Face face:source.data.faces())groups.computeIfAbsent(face.objectName(),ignored->new ArrayList<>()).add(face);
            List<GlauxHeadMeshes.Part> result=new ArrayList<>();
            for(Map.Entry<String,List<ObjMeshData.Face>> group:groups.entrySet()) {
                ObjMeshData original=source.data;
                ObjMeshData part=new ObjMeshData(original.objId(),original.positions(),original.textureCoordinates(),original.normals(),group.getValue(),original.mtlIds(),original.materialTextures(),group.getValue().size(),0,0,0,1,1,original.originalBounds(),0);
                Mesh raw=ObjMeshBakedModel.bakeGeometry(source.definition,part).mesh();
                MeshBuilder textured=java.util.Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer()).meshBuilder();
                raw.forEach(quad->{
                    for(int v=0;v<4;v++)if(!Float.isFinite(quad.u(v))||!Float.isFinite(quad.v(v))||quad.u(v)<0||quad.u(v)>1||quad.v(v)<0||quad.v(v)>1)
                        throw new IllegalStateException("Glaux authored UV escaped normalized bounds: "+kind+"/"+group.getKey());
                    // Exact same sprite bake as the shared static OBJ texture transform.
                    textured.getEmitter().copyFrom(quad).spriteBake(sprite,MutableQuadView.BAKE_NORMALIZED).emit();
                });
                Mesh mesh=textured.build();
                float[] vertices=new float[group.getValue().size()*4*8];int[] cursor={0};
                mesh.forEach(quad->{for(int v=0;v<4;v++){
                    vertices[cursor[0]++]=quad.x(v);vertices[cursor[0]++]=quad.y(v);vertices[cursor[0]++]=quad.z(v);
                    vertices[cursor[0]++]=quad.normalX(v);vertices[cursor[0]++]=quad.normalY(v);vertices[cursor[0]++]=quad.normalZ(v);
                    vertices[cursor[0]++]=quad.u(v);
                    vertices[cursor[0]++]=quad.v(v);
                }});
                result.add(new GlauxHeadMeshes.Part(group.getKey(),cursor[0]==vertices.length?vertices:java.util.Arrays.copyOf(vertices,cursor[0])));
            }
            return List.copyOf(result);
        }
    }
}
