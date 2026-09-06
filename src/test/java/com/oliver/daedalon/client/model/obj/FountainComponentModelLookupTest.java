package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FountainComponentModelLookupTest {
    @Test
    void bareAttachmentIsUnchanged() {
        BakedModel model = new GeometryModel();
        assertSame(model, ObjMeshBakedModel.unwrapAttachedModel(model));
    }

    @Test
    void ctmAndEmissiveStyleLayersExposeTheSameAttachment() {
        // Continuity's CTM and emissive models both extend ForwardingBakedModel.
        for (int depth : new int[]{1, 2, 5}) {
            BakedModel geometry = new GeometryModel();
            BakedModel wrapped = geometry;
            for (int i = 0; i < depth; i++) wrapped = new Layer(wrapped);
            BakedModel outerChild = ((Layer) wrapped).getWrappedModel();
            assertSame(geometry, ObjMeshBakedModel.unwrapAttachedModel(wrapped));
            assertSame(outerChild, ((Layer) wrapped).getWrappedModel(),
                    "Lookup must not replace or remove the installed wrappers");
        }
    }

    @Test
    void resourceReloadUsesTheNewGeometryWithoutRetainingOldModels() {
        BakedModel first = new GeometryModel();
        BakedModel reloaded = new GeometryModel();
        assertSame(first, ObjMeshBakedModel.unwrapAttachedModel(new Layer(first)));
        assertSame(reloaded, ObjMeshBakedModel.unwrapAttachedModel(new Layer(reloaded)));
    }

    @Test
    void emptyAndForeignModelsRemainSafeForTheAttachmentTypeCheck() {
        assertNull(ObjMeshBakedModel.unwrapAttachedModel(null));
        Layer empty = new Layer(null);
        assertSame(empty, ObjMeshBakedModel.unwrapAttachedModel(empty));
        assertFalse(ObjMeshBakedModel.unwrapAttachedModel(new Layer(new GeometryModel()))
                instanceof ObjMeshBakedModel);
    }

    private static final class Layer extends ForwardingBakedModel {
        Layer(BakedModel child) { wrapped = child; }
    }

    private static final class GeometryModel implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) { return List.of(); }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean hasDepth() { return false; }
        @Override public boolean isSideLit() { return false; }
        @Override public boolean isBuiltin() { return false; }
        @Override public Sprite getParticleSprite() { return null; }
        @Override public ModelTransformation getTransformation() { return ModelTransformation.NONE; }
        @Override public ModelOverrideList getOverrides() { return ModelOverrideList.EMPTY; }
    }
}
