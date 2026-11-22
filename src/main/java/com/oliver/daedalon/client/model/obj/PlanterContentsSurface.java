package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.AnthophorosFillProfile;
import com.oliver.daedalon.block.BenchBlock;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

/** Four tiny cached meshes per width, shared by all stone and bronze finishes. */
final class PlanterContentsSurface {
    private final Mesh[] earth = new Mesh[4];

    PlanterContentsSurface(AnthophorosFillProfile profile, Sprite sprite) {
        var renderer = java.util.Objects.requireNonNull(RendererAccess.INSTANCE.getRenderer());
        var material = renderer.materialFinder().blendMode(BlendMode.SOLID).find();
        for (int turn = 0; turn < 4; turn++) {
            var builder = renderer.meshBuilder();
            emitEarthQuads(profile, sprite, material, turn, builder.getEmitter());
            earth[turn] = builder.build();
        }
    }

    static void emitEarthQuads(AnthophorosFillProfile profile, Sprite sprite, RenderMaterial material,
                              int turn, QuadEmitter emitter) {
        for (int tile = profile.firstTileIndex(); tile <= profile.lastTileIndex(); tile++) {
            emitStrip(profile, sprite, material, turn, tile, emitter);
        }
    }

    private static void emitStrip(AnthophorosFillProfile profile, Sprite sprite, RenderMaterial material,
                                  int turn, int tile, QuadEmitter emitter) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float v = vertex == 1 || vertex == 2 ? 1 : 0;
            float x = vertex >= 2 ? profile.tileMaxX(tile) : profile.tileMinX(tile);
            float z = (2 * v - 1) * profile.halfDepth();
            // One seamless 64x64 tile repeats every metre. Clip the geometry at
            // each repeat boundary instead of wrapping UVs outside the sprite.
            float textureU = profile.textureU(x, tile);
            float textureV = profile.textureV(z);
            for (int step = 0; step < turn; step++) {
                float oldX = x;
                x = -z;
                z = oldX;
            }
            emitter.pos(vertex, .5F + x, profile.surfaceY(), .5F + z);
            emitter.normal(vertex, 0, 1, 0);
            emitter.uv(vertex, textureU, textureV);
        }
        emitter.color(-1, -1, -1, -1);
        emitter.material(material);
        emitter.cullFace(null);
        emitter.nominalFace(Direction.UP);
        emitter.tag(ShaderTerrainNormalBridge.encodeObjTag(0));
        // Exact repeat edges keep density and phase continuous across strips.
        // The tile and its companions are periodic; no padding or second inset.
        emitter.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
        emitter.emit();
    }

    void emitEarth(Direction facing, RenderContext context) {
        earth[BenchBlock.rotationStepsFromSouth(facing)].outputTo(context.getEmitter());
    }
}
