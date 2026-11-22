package com.oliver.daedalon.client.glaux.model;

import com.oliver.daedalon.client.model.obj.GlauxObjBridge;
import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import com.oliver.daedalon.glaux.GlauxBlock;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GlauxModelLoadingPlugin {
    private static final Identifier DISPLAY=new Identifier("daedalon","block/mesh/glaux_display");
    private GlauxModelLoadingPlugin() { }
    public static void register() {
        PreparableModelLoadingPlugin.register((manager,executor)->CompletableFuture.supplyAsync(()->{try{return GlauxObjBridge.prepare(manager);}catch(Exception e){throw new CompletionException(e);}},executor),(prepared,context)->{
            context.resolveModel().register(request->{String path=request.id().getPath();if(!request.id().getNamespace().equals("daedalon"))return null;
                if(path.equals("glaux")&&(!(request.id() instanceof ModelIdentifier id)||!id.getVariant().equals("inventory")))return null;
                String kind=switch(path){case "block/glaux/glaux_body_lower"->"body_lower";case "block/glaux/glaux_body_upper"->"body_upper";case "item/glaux","glaux"->"item";default->null;};
                return kind==null?null:new Model(prepared,kind);
            });
        });
    }
    private record Model(GlauxObjBridge.Prepared prepared,String kind) implements UnbakedModel {
        @Override public Collection<Identifier> getModelDependencies(){return List.of(DISPLAY);}
        @Override public void setParents(Function<Identifier,UnbakedModel> loader) { }
        @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> getter,ModelBakeSettings settings,Identifier modelId) {
            BakedModel metadata=baker.bake(DISPLAY,settings);
            GlauxHeadMeshes.current=prepared.heads(getter);
            BakedModel baked=prepared.staticModel(kind,metadata,getter,modelId);
            return kind.equals("item")?baked:new Facing(baked);
        }
    }
    private static final class Facing implements BakedModel,FabricBakedModel {
        private final BakedModel delegate;
        private Facing(BakedModel delegate){this.delegate=delegate;}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
            // Reuse the established section-bounds path for the owl's slight
            // horizontal overhang. This runs during chunk bake, never per frame.
            MonopterosRenderBounds.mark(pos,state.getOutlineShape(world,pos).getBoundingBox());
            context.pushTransform(Turn.CACHE[state.get(GlauxBlock.FACING).getHorizontal()]);
            ((FabricBakedModel)delegate).emitBlockQuads(world,state,pos,random,context);context.popTransform();
        }
        @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){((FabricBakedModel)delegate).emitItemQuads(stack,random,context);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){return delegate.getQuads(state,face,random);}
        @Override public boolean useAmbientOcclusion(){return delegate.useAmbientOcclusion();}
        @Override public boolean hasDepth(){return delegate.hasDepth();}
        @Override public boolean isSideLit(){return delegate.isSideLit();}
        @Override public boolean isBuiltin(){return delegate.isBuiltin();}
        @Override public Sprite getParticleSprite(){return delegate.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return delegate.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return delegate.getOverrides();}
    }
    private static final class Turn implements RenderContext.QuadTransform {
        private static final Turn[] CACHE=new Turn[4];
        static{for(Direction d:Direction.Type.HORIZONTAL)CACHE[d.getHorizontal()]=new Turn(GlauxBlock.rotationDegrees(d));}
        private final float c,s;
        private Turn(float degrees){double a=Math.toRadians(degrees);c=(float)Math.rint(Math.cos(a));s=(float)Math.rint(Math.sin(a));}
        @Override public boolean transform(MutableQuadView q){for(int v=0;v<4;v++){float x=q.x(v)-.5F,z=q.z(v)-.5F;q.pos(v,.5F+c*x+s*z,q.y(v),.5F+c*z-s*x);if(q.hasNormal(v)){float nx=q.normalX(v),nz=q.normalZ(v);q.normal(v,c*nx+s*nz,q.normalY(v),c*nz-s*nx);}}q.cullFace(null);Direction f=q.nominalFace();if(f!=null)q.nominalFace(Direction.getFacing(c*f.getOffsetX()+s*f.getOffsetZ(),f.getOffsetY(),c*f.getOffsetZ()-s*f.getOffsetX()));return true;}
    }
}
