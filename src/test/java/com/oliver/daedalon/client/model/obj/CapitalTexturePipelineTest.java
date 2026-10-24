package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalTransition;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Replays real collar vertices through the capital's actual one-tile material window. */
final class CapitalTexturePipelineTest {
    @Test void actualCapitalMaterialPipelinePreservesNativeAnd64xTexelDensity() throws Exception {
        Sprite sprite = spriteSentinel();
        var material = materialTransform(sprite);
        for (var support : CapitalTransition.values()) {
            if (support == CapitalTransition.NONE) continue;
            for (var size : CapitalBlock.Size.values()) {
                double[][][] rings = CapitalTransitionMesh.rings(support, size);
                for (int i = 0; i < rings[0].length; i++) {
                    int next = (i + 1) % rings[0].length;
                    for (Direction face : Direction.values()) {
                        Quad quad = new Quad(new double[][]{rings[0][i], rings[1][i], rings[1][next], rings[1][next]}, face);
                        CapitalTransitionUvTransform.forCell(BlockPos.ORIGIN, 0, 0).transform(quad.view);
                        material.transform(quad.view);
                        assertSame(sprite, quad.bakedSprite);
                        assertEquals(1, quad.bakes);
                        // Compare texture-pixel distances against physical projected edge lengths,
                        // after BOTH production transforms, rather than assuming a six-tile layout.
                        for (int resolution : new int[]{16, 64}) {
                            for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
                                double du = switch (face.getAxis()) {
                                    case X -> quad.xyz[b][2] - quad.xyz[a][2];
                                    default -> quad.xyz[b][0] - quad.xyz[a][0];
                                };
                                double dv = face.getAxis() == Direction.Axis.Y
                                        ? quad.xyz[b][2] - quad.xyz[a][2] : quad.xyz[b][1] - quad.xyz[a][1];
                                assertEquals(Math.abs(du) * resolution,
                                        Math.abs(quad.uv[b][0] - quad.uv[a][0]) * resolution * 12, 2e-4);
                                assertEquals(Math.abs(dv) * resolution,
                                        Math.abs(quad.uv[b][1] - quad.uv[a][1]) * resolution * 6, 2e-4);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void fullCollarHeightUsesTwoOrFourNativePixelsAfterSpriteMapping() throws Exception {
        var material = materialTransform(spriteSentinel());
        for (var size : CapitalBlock.Size.values()) {
            float height = size == CapitalBlock.Size.STANDARD ? 2F / 16 : 4F / 16;
            Quad quad = new Quad(new double[][]{{.25, 0, .5}, {.25, height, .5}, {.5, height, .5}, {.5, height, .5}}, Direction.SOUTH);
            CapitalTransitionUvTransform.forCell(new BlockPos(-7, 13, 5), 0, 0).transform(quad.view);
            material.transform(quad.view);
            // Sprite UVs are floats; allow less than 0.0002 of one texture pixel.
            assertEquals(height * 16, Math.abs(quad.uv[1][1] - quad.uv[0][1]) * 96, 2e-4);
            assertEquals(height * 64, Math.abs(quad.uv[1][1] - quad.uv[0][1]) * 384, 2e-4);
        }
    }

    private static RenderContext.QuadTransform materialTransform(Sprite sprite) throws Exception {
        Class<?> type = Class.forName(ObjMeshBakedModel.class.getName() + "$TextureTransform");
        var constructor = type.getDeclaredConstructor(List.class, List.class, WorldTexturePhase.Layout.class, int.class, boolean.class);
        constructor.setAccessible(true);
        // createArchitecturalVariants -> variant(material + "_block") uses blockSurface.
        return (RenderContext.QuadTransform) constructor.newInstance(List.of(sprite), List.of(true),
                WorldTexturePhase.blockSurface(), 0, false);
    }

    private static Sprite spriteSentinel() throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        var field = type.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Sprite) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), Sprite.class);
    }

    private static final class Quad {
        final double[][] xyz;
        final float[][] uv = new float[4][2];
        final Direction face;
        Sprite bakedSprite;
        int bakes;
        final MutableQuadView view;

        Quad(double[][] xyz, Direction face) {
            this.xyz = xyz;
            this.face = face;
            view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                    new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "x", "y", "z" -> (float) this.xyz[(int) args[0]]["xyz".indexOf(method.getName())];
                        case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                        case "uv" -> { uv[(int) args[0]] = new float[]{(float) args[1], (float) args[2]}; yield proxy; }
                        case "nominalFace" -> this.face;
                        case "tag" -> ShaderTerrainNormalBridge.encodeObjTag(0);
                        case "spriteBake" -> { bakedSprite = (Sprite) args[0]; bakes++; yield proxy; }
                        default -> throw new AssertionError("UV correction must not mutate geometry or shading: " + method.getName());
                    });
        }
    }
}
