package com.oliver.daedalon.client.model.obj;

import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Replays the real Astragalos diagonal-bevel failure through the production loader/projector. */
final class AstragalosProjectionTest {
    private static final Identifier DEFINITION=new Identifier("daedalon","models/mesh/plinth_astragalos.json");

    @Test void actualAdjacentBevelTrianglesKeepOneBasisAndContinuousSharedEdgeUvs() throws Exception {
        ResourceManager resources=resources(false);
        ObjMeshDefinition definition=ObjMeshDefinition.load(resources,DEFINITION);
        assertEquals(ObjMeshDefinition.UvProjection.AXIS_STABILIZED_BOX,definition.uvProjection());
        assertEquals(ObjMeshDefinition.UvProjection.BOX,definition.materialUvProjections().get("none"));
        ObjMeshData data=ObjMeshParser.parse(resources,definition);
        List<ObjMeshData.Vec3> positions=transformed(definition,data);
        // Zero-based source faces34/35 (OBJ triangles35/36) are the two halves
        // of one bevel quad. Their centroids fall on opposite radial axes.
        var a=data.faces().get(34);var b=data.faces().get(35);
        Method normal=method(ObjMeshBakedModel.class,"geometricNormal",List.class,List.class);
        var na=(ObjMeshData.Vec3)normal.invoke(null,positions,a.vertices());
        var nb=(ObjMeshData.Vec3)normal.invoke(null,positions,b.vertices());
        assertEquals(na.x(),nb.x(),1e-6);assertEquals(na.y(),nb.y(),1e-6);assertEquals(na.z(),nb.z(),1e-6);
        Method closest=method(ObjMeshBakedModel.class,"closestAxis",ObjMeshData.Vec3.class);
        Direction nominal=(Direction)closest.invoke(null,na);
        Object current=projector(definition,positions);
        Object ca=choice(current,positions,a,na,nominal),cb=choice(current,positions,b,nb,nominal);
        assertEquals(face(ca),face(cb),"Coplanar triangles must share one projection basis");
        assertEquals(nominal,face(ca));
        List<Integer> shared=a.vertices().stream().map(ObjMeshData.VertexRef::positionIndex)
                .filter(index->b.vertices().stream().anyMatch(ref->ref.positionIndex()==index)).toList();
        assertEquals(2,shared.size(),"Regression pair must still share an actual geometry edge");
        for(int index:shared) assertArrayEquals(uv(current,ca,positions.get(index)),uv(current,cb,positions.get(index)),1e-7F,
                "Shared bevel edge acquired a diagonal texture discontinuity");

        // Removing only the authored material override recreates the original
        // production failure, proving this is not a generic box-mapping test.
        ObjMeshDefinition old=ObjMeshDefinition.load(resources(true),DEFINITION);
        Object previous=projector(old,positions);
        Object oldA=choice(previous,positions,a,na,nominal),oldB=choice(previous,positions,b,nb,nominal);
        assertNotEquals(face(oldA),face(oldB),"The actual source pair must reproduce the old centroid-dependent basis change");
        float[] previousA=uv(previous,oldA,positions.get(shared.get(0))),previousB=uv(previous,oldB,positions.get(shared.get(0)));
        assertTrue(Math.abs(previousA[0]-previousB[0])>.01,"Counterfactual source mapping must retain the visible UV mismatch");
    }

    private static List<ObjMeshData.Vec3> transformed(ObjMeshDefinition definition,ObjMeshData data) throws Exception {
        Class<?> type=Class.forName(ObjMeshBakedModel.class.getName()+"$Transform");
        Object transform=method(type,"create",ObjMeshDefinition.class,ObjMeshData.Bounds.class).invoke(null,definition,data.originalBounds());
        Method position=method(type,"position",ObjMeshData.Vec3.class);
        List<ObjMeshData.Vec3> result=new ArrayList<>();
        for(var p:data.positions()) result.add((ObjMeshData.Vec3)position.invoke(transform,p));
        return result;
    }
    private static Object projector(ObjMeshDefinition definition,List<ObjMeshData.Vec3> positions) throws Exception {
        Class<?> type=Class.forName(ObjMeshBakedModel.class.getName()+"$UvProjector");
        return method(type,"create",ObjMeshDefinition.class,List.class,ObjMeshData.Bounds.class)
                .invoke(null,definition,positions,ObjMeshData.Bounds.of(positions));
    }
    private static Object choice(Object projector,List<ObjMeshData.Vec3> positions,ObjMeshData.Face face,ObjMeshData.Vec3 normal,Direction nominal) throws Exception {
        return method(projector.getClass(),"chooseProjection",List.class,List.class,String.class,ObjMeshData.Vec3.class,Direction.class)
                .invoke(projector,positions,face.vertices(),face.material(),normal,nominal);
    }
    private static Direction face(Object choice) throws Exception { return (Direction)method(choice.getClass(),"face").invoke(choice); }
    private static float[] uv(Object projector,Object choice,ObjMeshData.Vec3 position) throws Exception {
        float[] u={0},v={0};
        method(projector.getClass(),"project",float[].class,float[].class,int.class,ObjMeshData.Vec3.class,choice.getClass())
                .invoke(projector,u,v,0,position,choice);
        return new float[]{u[0],v[0]};
    }
    private static Method method(Class<?> owner,String name,Class<?>... arguments) throws Exception {
        Method method=owner.getDeclaredMethod(name,arguments);method.setAccessible(true);return method;
    }
    private static ResourceManager resources(boolean omitMaterialOverride) {
        ResourcePack pack=(ResourcePack)Proxy.newProxyInstance(ResourcePack.class.getClassLoader(),new Class<?>[]{ResourcePack.class},
                (proxy,method,args)->method.getName().equals("getName")?"actual Astragalos source assets":null);
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(proxy,method,args)->{
            if(!method.getName().equals("getResource")) throw new AssertionError("Unexpected resource operation: "+method.getName());
            Identifier id=(Identifier)args[0];
            Path path=Path.of("src/main/resources/assets",id.getNamespace(),id.getPath());
            if(!Files.isRegularFile(path)) return Optional.empty();
            return Optional.of(new Resource(pack,()->{
                if(omitMaterialOverride && id.equals(DEFINITION)) {
                    var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.remove("material_uv_projections");
                    return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
                }
                return Files.newInputStream(path);
            }));
        });
    }
}
