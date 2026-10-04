package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.texture.Sprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Complements real mesh replay coverage with the exact material transform that rejected tag zero. */
final class FinialTexturePipelineTest {
    @Test void ownedBaseMetadataSurvivesActualMaterialLookupAndEveryWorldTexturePhase() throws Exception {
        WorldTexturePhase.Layout layout = WorldTexturePhase.detailedSurface();
        // Standard stone, aged stone and bronze all bind the finial's material slot zero.
        for (int material = 0; material < 3; material++) {
            Sprite sprite = spriteSentinel();
            Sprite unrelatedSlot = spriteSentinel();
            for (int phase = 0; phase < layout.phaseCount(); phase++) {
                QuadSpy quad = new QuadSpy();
                quad.tag = ShaderTerrainNormalBridge.encodeObjTag(0);
                quad.xyz = new float[][]{{-.15F,-.775F,.3F},{.35F,-.775F,.3F},{.35F,-.7125F,.8F},{-.15F,-.7125F,.8F}};
                quad.normals = new float[][]{{0,1,0},{0,1,0},{0,1,0},{0,1,0}};
                quad.uv = new float[][]{{.4F,.4F},{.6F,.4F},{.6F,.6F},{.4F,.6F}};
                float[][] positions = cloneRows(quad.xyz), normals = cloneRows(quad.normals), uvs = cloneRows(quad.uv);
                assertTrue(transform(List.of(sprite, unrelatedSlot), List.of(true, false), phase).transform(quad.view));
                assertEquals(0, ShaderTerrainNormalBridge.decodeObjMaterialTag(quad.tag));
                assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(quad.tag));
                assertSame(sprite, quad.bakedSprite, "Owned slot zero must use this finial's variant sprite");
                assertEquals(MutableQuadView.BAKE_NORMALIZED, quad.bakeFlags);
                assertEquals(1, quad.bakes);
                for (int vertex = 0; vertex < 4; vertex++) {
                    assertArrayEquals(positions[vertex], quad.xyz[vertex], 0F);
                    assertArrayEquals(normals[vertex], quad.normals[vertex], 0F);
                    assertEquals(layout.mapU(uvs[vertex][0], phase), quad.uv[vertex][0], 1E-6);
                    assertEquals(layout.mapV(uvs[vertex][1]), quad.uv[vertex][1], 1E-6);
                }
            }
        }
    }

    @Test void actualMaterialTransformStillRejectsForeignZeroAndSelectsTheOwnedSlot() throws Exception {
            Sprite sprite = spriteSentinel();
            var transform = transform(List.of(sprite), List.of(true), 5);
            QuadSpy foreign = new QuadSpy();
            foreign.tag = 0;
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> transform.transform(foreign.view));
            assertEquals("Quad is not owned by Daedalon OBJ rendering: 0", failure.getMessage());
            assertEquals(0, foreign.bakes, "Reject foreign geometry before any sprite mapping");
            QuadSpy owned = new QuadSpy();
            owned.tag = ShaderTerrainNormalBridge.encodeObjTag(0);
            assertTrue(transform.transform(owned.view));
            assertSame(sprite, owned.bakedSprite);
            assertEquals(1, owned.bakes);
            QuadSpy item = new QuadSpy();
            item.tag = owned.tag;
            item.uv[0] = new float[]{.6F, .7F};
            assertTrue(transform(List.of(sprite), List.of(false), 5).transform(item.view));
            assertArrayEquals(new float[]{.6F, .7F}, item.uv[0], 0F,
                    "Materials that opt out of world phase must keep their existing UV mapping");
    }

    private static RenderContext.QuadTransform transform(List<Sprite> sprites, List<Boolean> worldPhase, int phase) throws Exception {
        Class<?> type = Class.forName(ObjMeshBakedModel.class.getName() + "$TextureTransform");
        Constructor<?> constructor = type.getDeclaredConstructor(List.class, List.class, WorldTexturePhase.Layout.class, int.class);
        constructor.setAccessible(true);
        return (RenderContext.QuadTransform) constructor.newInstance(sprites, worldPhase,
                WorldTexturePhase.detailedSurface(), phase);
    }

    private static float[][] cloneRows(float[][] values) {
        return Arrays.stream(values).map(float[]::clone).toArray(float[][]::new);
    }

    private static Sprite spriteSentinel() throws Exception {
        // Only object identity is needed: the spy intercepts spriteBake before calling any sprite methods.
        // Allocating this test sentinel avoids starting Minecraft or requiring native/GPU libraries in plain JUnit.
        Class<?> type = Class.forName("sun.misc.Unsafe");
        var field = type.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Sprite) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), Sprite.class);
    }

    /** The production material transform mutates UVs and calls spriteBake on this spy. */
    private static final class QuadSpy {
        float[][] xyz = new float[4][3], normals = new float[4][3], uv = new float[4][2];
        int tag, bakes, bakeFlags;
        Sprite bakedSprite;
        final MutableQuadView view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "tag" -> { if (args == null || args.length == 0) yield tag; tag = (int) args[0]; yield proxy; }
                    case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                    case "uv" -> { uv[(int) args[0]] = new float[]{(float) args[1], (float) args[2]}; yield proxy; }
                    case "spriteBake" -> { bakedSprite = (Sprite) args[0]; bakeFlags = (int) args[1]; bakes++; yield proxy; }
                    default -> throw new AssertionError("Unexpected material-pipeline quad operation: " + method.getName());
                });

    }
}
