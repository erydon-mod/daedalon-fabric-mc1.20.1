package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Each style has one OBJ and three preselected bodies; the suspension is shared. */
final class LanternMeshParts {
    private final Map<String,Mesh> parts;
    private final Mesh icon;
    private final boolean hardware;
    private final int glassTag;
    private record BodyComponents(List<String> off,List<String> on) {}
    private static final List<BodyComponents> BODY_COMPONENTS=java.util.Arrays.stream(LanternBlock.Mount.values())
            .map(mount->new BodyComponents(List.of(mount.asString()),List.of(mount.asString(),mount.asString()+"_flame"))).toList();
    LanternMeshParts(Map<String,Mesh> parts,boolean hardware,int glassTag) {
        this.parts=Map.copyOf(parts);this.hardware=hardware;this.glassTag=glassTag;
        if(hardware) {
            for(String key:new String[]{"half_chain","full_chain","canopy","half_chain_small","full_chain_small","canopy_small"}) require(key);
            icon=require("full_chain");
        } else {
            for(var mount:LanternBlock.Mount.values()) for(String key:bodyComponents(mount,true)) require(key);
            var renderer=Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer());
            var builder=renderer.meshBuilder();
            require("pier").forEach(q->{
                if(ShaderTerrainNormalBridge.decodeObjMaterialTag(q.tag())!=glassTag) builder.getEmitter().copyFrom(q).emit();
            });
            // Clear panes must not mask the burner in the nearest-surface icon rasterizer.
            icon=builder.build();
        }
    }
    static boolean isPath(String path) { return path.startsWith("models/mesh/lantern_"); }
    static String componentKey(String object) {
        for(var mount:LanternBlock.Mount.values()) if(object.equals(mount.asString()+"_flame")
                || object.startsWith(mount.asString()+"_flame_")) return mount.asString()+"_flame";
        for(var mount:LanternBlock.Mount.values()) if(object.startsWith(mount.asString()+"_")) return mount.asString();
        return object;
    }
    static List<String> bodyComponents(LanternBlock.Mount mount,boolean lit) {
        var components=BODY_COMPONENTS.get(mount.ordinal());
        return lit?components.on():components.off();
    }
    private Mesh require(String key) { return Objects.requireNonNull(parts.get(key),"Missing lantern mesh component: "+key); }
    Mesh displayMesh() { return hardware?require("full_chain"):require("pier"); }
    Mesh iconMesh() { return icon; }

    /** Built only on an Axiom template cache miss, never while drawing each billboard. */
    Mesh previewMesh(LanternPreview preview, LanternMeshParts suspension, int leadTag) {
        var builder=Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer()).meshBuilder();
        QuadEmitter emitter=builder.getEmitter();
        var components=bodyComponents(preview.mount(),preview.lit());
        for(int i=0;i<components.size();i++) {
            var transform=i==0?LanternMeshTransform.forMount(preview.mount(),preview.size(),preview.facing())
                    :LanternMeshTransform.forFlame(preview.mount(),preview.size(),preview.facing());
            require(components.get(i)).forEach(q->{
            if(ShaderTerrainNormalBridge.decodeObjMaterialTag(q.tag())==glassTag) return;
            emitter.copyFrom(q);
            transform.transform(emitter);
            var offset = preview.supportOffset();
            for (int v=0; v<4; v++) emitter.pos(v, emitter.x(v)+(float)offset.x, emitter.y(v)+(float)offset.y, emitter.z(v)+(float)offset.z);
            emitter.emit();
            });
        }
        if(preview.mount()==LanternBlock.Mount.HANGING) {
            Objects.requireNonNull(suspension,"Missing shared lantern suspension for Axiom preview");
            if(leadTag<0) throw new IllegalStateException("Lantern preview has no lead material");
            for(var component:suspensionPreviewComponents(preview))
                suspension.appendPreview(emitter,component.key(),component.yOffset(),leadTag);
        }
        return builder.build();
    }

    static List<PreviewComponent> suspensionPreviewComponents(LanternPreview preview) {
        if(preview.mount()!=LanternBlock.Mount.HANGING) return List.of();
        String suffix=preview.size()==LanternBlock.Size.SMALL?"_small":"";
        List<PreviewComponent> components=new ArrayList<>(preview.chainCount()+2);
        components.add(new PreviewComponent("half_chain"+suffix,.5F));
        for(int cell=1;cell<=preview.chainCount();cell++) components.add(new PreviewComponent("full_chain"+suffix,cell));
        if(preview.canopy()) components.add(new PreviewComponent("canopy"+suffix,preview.chainCount()+1));
        return List.copyOf(components);
    }

    record PreviewComponent(String key,float yOffset) {}

    private void appendPreview(QuadEmitter emitter,String component,float yOffset,int leadTag) {
        require(component).forEach(q->{
            emitter.copyFrom(q).tag(ShaderTerrainNormalBridge.encodeObjTag(leadTag));
            for(int v=0;v<4;v++) emitter.pos(v,q.x(v),q.y(v)+yOffset,q.z(v));
            emitter.emit();
        });
    }
    void emitBody(BlockState state,RenderContext context) {
        var mount=state.get(LanternBlock.MOUNT);
        var components=bodyComponents(mount,state.get(LanternBlock.LIT));
        for(int i=0;i<components.size();i++) {
            context.pushTransform(i==0?LanternMeshTransform.forState(state)
                    :LanternMeshTransform.forFlame(mount,state.get(LanternBlock.SIZE),state.get(LanternBlock.FACING)));
            try { require(components.get(i)).outputTo(context.getEmitter()); }
            finally { context.popTransform(); }
        }
    }
    void emitWorldBody(BlockState state, RenderContext context, BlockPos pos, Vec3d offset, LanternSurfaceEmitter surfaces) {
        var mount=state.get(LanternBlock.MOUNT);
        var components=bodyComponents(mount,state.get(LanternBlock.LIT));
        for(int i=0;i<components.size();i++) surfaces.emit(require(components.get(i)),
                i==0?LanternMeshTransform.forState(state):LanternMeshTransform.forFlame(mount,state.get(LanternBlock.SIZE),state.get(LanternBlock.FACING)),
                offset,pos,context);
    }
    void emitSuspension(LanternBlock.Size size,boolean half,boolean canopy,RenderContext context) {
        String suffix=size==LanternBlock.Size.SMALL?"_small":"";
        context.pushTransform(half?LanternMeshTransform.HALF_CHAIN:LanternMeshTransform.FULL_CHAIN);
        try { require((half?"half_chain":"full_chain")+suffix).outputTo(context.getEmitter()); }
        finally { context.popTransform(); }
        if(canopy) {
            context.pushTransform(LanternMeshTransform.CANOPY);
            try { require("canopy"+suffix).outputTo(context.getEmitter()); }
            finally { context.popTransform(); }
        }
    }
    void emitWorldSuspension(LanternBlock.Size size,boolean half,boolean canopy,RenderContext context,
                             BlockPos pos,LanternSurfaceEmitter surfaces,int leadTag) {
        String suffix=size==LanternBlock.Size.SMALL?"_small":"";
        surfaces.emit(require((half?"half_chain":"full_chain")+suffix),
                half?LanternMeshTransform.HALF_CHAIN:LanternMeshTransform.FULL_CHAIN,Vec3d.ZERO,pos,context,leadTag);
        if(canopy) surfaces.emit(require("canopy"+suffix),LanternMeshTransform.CANOPY,Vec3d.ZERO,pos,context,leadTag);
    }
}
