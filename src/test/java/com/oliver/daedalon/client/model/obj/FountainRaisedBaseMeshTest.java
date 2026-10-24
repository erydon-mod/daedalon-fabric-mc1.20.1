package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.FountainBaseProfile;
import com.oliver.daedalon.block.FountainBasinBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class FountainRaisedBaseMeshTest {
    @Test void raisingTheExistingBasinOnlyAddsOneToYAndLeavesItsUvsUnchanged() throws Exception {
        var type = Class.forName(ObjMeshBakedModel.class.getName() + "$BasinTransform");
        var constructor = type.getDeclaredConstructor(float.class, float.class); constructor.setAccessible(true);
        for (var size : FountainBasinBlock.Size.values()) {
            Quad plain = new Quad(), raised = new Quad();
            ((RenderContext.QuadTransform) constructor.newInstance(size.renderScale(), 0F)).transform(plain.view);
            ((RenderContext.QuadTransform) constructor.newInstance(size.renderScale(), 1F)).transform(raised.view);
            for (int i = 0; i < 4; i++) {
                assertEquals(plain.xyz[i][0], raised.xyz[i][0]); assertEquals(plain.xyz[i][2], raised.xyz[i][2]);
                assertEquals(plain.xyz[i][1] + 1, raised.xyz[i][1], 1e-6);
                assertArrayEquals(plain.uv[i], raised.uv[i]);
            }
        }
    }

    @Test void supportUvDensityMatchesTheActualDetailedBasinMaterialWindow() {
        var layout = WorldTexturePhase.detailedSurface();
        for (var style : FountainBasinBlock.Style.values()) for (var size : FountainBasinBlock.Size.values()) {
            var profile = FountainBaseProfile.forStyle(style);
            for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                double[] a = FountainRaisedBaseMesh.rawUv(profile, size, face, .25, .25, .25, 0);
                double[] b = FountainRaisedBaseMesh.rawUv(profile, size, face, .35, .35, .35, 0);
                for (int resolution : new int[]{16, 64}) {
                    double expected = .1 * 6 / profile.sourceUvSpan() * resolution;
                    assertEquals(expected, Math.abs(b[0] - a[0]) * layout.uScale() * 12 * resolution, 1e-5);
                    assertEquals(expected, Math.abs(b[1] - a[1]) * layout.vScale() * 6 * resolution, 1e-5);
                }
            }
        }
    }

    @Test void extensionTopContinuesTheSameBasinSideUvFieldAtMeasuredUndersidePoints() {
        for (var style : FountainBasinBlock.Style.values()) for (var size : FountainBasinBlock.Size.values()) {
            var profile = FountainBaseProfile.forStyle(style);
            for (int i = 0; i < profile.vertexCount(); i++) for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                double x = .5 + profile.x(i) * size.modelScale(), z = .5 + profile.z(i) * size.modelScale();
                double y = 1 + profile.topOffset(i) * size.modelScale();
                double sx = .5 + profile.x(i) * 5, sz = .5 + profile.z(i) * 5, sy = profile.topOffset(i) * 5;
                double u = (sx - profile.sourceUvMinX()) / profile.sourceUvSpan();
                if (face == Direction.NORTH) u = 1 - u;
                if (face == Direction.EAST || face == Direction.WEST) {
                    u = (sz - profile.sourceUvMinZ()) / profile.sourceUvSpan();
                    if (face == Direction.EAST) u = 1 - u;
                }
                double v = 1 - sy / profile.sourceUvSpan();
                double[] actual = FountainRaisedBaseMesh.rawUv(profile, size, face, x, y, z, 0);
                assertEquals(.5 + (u - .5) * size.renderScale(), actual[0], 1e-7);
                assertEquals(.5 + (v - .5) * size.renderScale(), actual[1], 1e-7);
                assertTrue(profile.topOffset(i) >= 0 && profile.topOffset(i) < .003);
            }
        }
    }

    @Test void cachedVerticalWrapKeepsEveryEmittedSideUvInsideItsSpriteWindow() {
        for (var style : FountainBasinBlock.Style.values()) for (var size : FountainBasinBlock.Size.values()) {
            var profile = FountainBaseProfile.forStyle(style);
            double cut = FountainRaisedBaseMesh.verticalWrapY(profile, size);
            for (int i = 0; i < profile.vertexCount(); i++) {
                double x = .5 + profile.x(i) * size.modelScale(), z = .5 + profile.z(i) * size.modelScale();
                double top = 1 + profile.topOffset(i) * size.modelScale();
                double[] stops = cut > 1e-6 && cut < top ? new double[]{0, cut, top} : new double[]{0, top};
                for (int segment = 0; segment + 1 < stops.length; segment++) {
                    int row = (int) Math.floor(FountainRaisedBaseMesh.rawV(profile, size, (stops[segment] + stops[segment + 1]) * .5));
                    for (double y : new double[]{stops[segment], stops[segment + 1]}) for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                        double[] uv = FountainRaisedBaseMesh.rawUv(profile, size, face, x, y, z, row);
                        assertTrue(uv[0] >= -1e-6 && uv[0] <= 1 + 1e-6, style + " U outside source window");
                        assertTrue(uv[1] >= -1e-6 && uv[1] <= 1 + 1e-6, style + " V outside source window");
                    }
                }
            }
        }
    }

    private static final class Quad {
        final float[][] xyz = {{-1, 0, -1}, {2, .5F, -1}, {2, .5F, 2}, {-1, 0, 2}};
        final float[][] uv = {{.2F, .2F}, {.8F, .2F}, {.8F, .8F}, {.2F, .8F}};
        final MutableQuadView view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x", "y", "z" -> xyz[(int) args[0]]["xyz".indexOf(method.getName())];
                    case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                    case "pos" -> { for (int i = 0; i < 3; i++) xyz[(int) args[0]][i] = (float) args[i + 1]; yield proxy; }
                    case "uv" -> { uv[(int) args[0]][0] = (float) args[1]; uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                    case "cullFace" -> proxy;
                    default -> throw new AssertionError("Translation altered unrelated vertex metadata: " + method.getName());
                });
    }
}
