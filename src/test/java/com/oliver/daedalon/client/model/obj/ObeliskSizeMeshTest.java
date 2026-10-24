package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.SizedDecorBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static com.oliver.daedalon.client.model.obj.ObeliskSizeMesh.*;
import static org.junit.jupiter.api.Assertions.*;

final class ObeliskSizeMeshTest {
    @Test void threeSizeChoicesUseTheApprovedTwoThreeAndFiveBlockProfiles() throws Exception {
        assertEquals(List.of("small", "medium", "large"),
                java.util.Arrays.stream(SizedDecorBlock.Size.values()).map(SizedDecorBlock.Size::asString).toList());
        assertEquals(2, 3F * SizedDecorBlock.Size.SMALL.scale());
        assertEquals(3, 3F * SizedDecorBlock.Size.MEDIUM.scale());
        assertEquals(5, 3F * SizedDecorBlock.Size.LARGE.scale());
        var type = Class.forName(ObjMeshBakedModel.class.getName() + "$SizedDecorTransform");
        var constructor = type.getDeclaredConstructor(float.class); constructor.setAccessible(true);
        for (var size : new SizedDecorBlock.Size[]{SizedDecorBlock.Size.SMALL, SizedDecorBlock.Size.MEDIUM}) {
            Quad quad = new Quad();
            ((RenderContext.QuadTransform) constructor.newInstance(size.scale())).transform(quad.view);
            for (int i = 0; i < 4; i++) {
                assertEquals(.5F + (Quad.XYZ[i][0] - .5F) * size.scale(), quad.xyz[i][0]);
                assertEquals(Quad.XYZ[i][1] * size.scale(), quad.xyz[i][1]);
                assertEquals(.5F + (Quad.XYZ[i][2] - .5F) * size.scale(), quad.xyz[i][2]);
                assertEquals(.5F + (Quad.UV[i][0] - .5F) * size.scale(), quad.uv[i][0]);
                assertEquals(.5F + (Quad.UV[i][1] - .5F) * size.scale(), quad.uv[i][1]);
            }
        }
    }

    @Test void clippingBothUvAxesPreservesEveryTriangleAndTextureDensity() {
        Vertex a = point(-1D / 3, -1D / 3), b = point(4D / 3, -1D / 3), c = point(4D / 3, 4D / 3);
        var patches = splitTriangle(a, b, c);
        assertTrue(patches.size() >= 6);
        assertEquals(.5 * Math.sqrt(areaSquared(a, b, c)), patches.stream().mapToDouble(p -> area(p.vertices())).sum(), 1e-12);
        var layout = WorldTexturePhase.detailedSurface();
        for (Patch patch : patches) {
            for (Vertex vertex : patch.vertices()) {
                assertTrue(vertex.u() - patch.uCell() >= -1e-12 && vertex.u() - patch.uCell() <= 1 + 1e-12);
                assertTrue(vertex.v() - patch.vCell() >= -1e-12 && vertex.v() - patch.vCell() <= 1 + 1e-12);
                assertEquals(vertex.u() * 3, vertex.x(), 1e-12);
                assertEquals(vertex.v() * 3, vertex.y(), 1e-12);
                assertEquals(0, vertex.z());
                assertEquals(-1, vertex.color()); assertEquals(0x00f000f0, vertex.lightmap());
            }
            Vertex first = patch.vertices().get(0);
            for (Vertex other : patch.vertices()) for (int resolution : new int[]{16, 64}) {
                assertEquals(Math.abs(other.x() - first.x()) * 2 * resolution,
                        Math.abs(other.u() - first.u()) * layout.uScale() * 12 * resolution, 1e-9);
                assertEquals(Math.abs(other.y() - first.y()) * 2 * resolution,
                        Math.abs(other.v() - first.v()) * layout.vScale() * 6 * resolution, 1e-9);
            }
        }
    }

    @Test void exactRepeatBoundariesDoNotDuplicateSurfacesAndDegenerateTrianglesStayEmpty() {
        Vertex a = point(1, -.2), b = point(1, .8), c = new Vertex(3, 1.5, 1, 1, .5, 0, 1, 0, -1, 0);
        var patches = splitTriangle(a, b, c);
        assertEquals(2, patches.size());
        assertTrue(patches.stream().allMatch(p -> p.uCell() == 1));
        assertEquals(.5 * Math.sqrt(areaSquared(a, b, c)), patches.stream().mapToDouble(p -> area(p.vertices())).sum(), 1e-12);
        assertTrue(splitTriangle(a, a, b).isEmpty());
    }

    @Test void insertedNormalsAndPackedChannelsKeepTheOriginalLinearInterpolation() {
        Vertex a = new Vertex(0, 0, 0, 0, 0, 1, 0, 0, 0xff000000, 0);
        Vertex b = new Vertex(1, 1, 1, 1, 1, 0, 1, 0, 0xffffffff, 0x00f000f0);
        Vertex midpoint = a.interpolate(b, .5);
        assertEquals(.5, midpoint.nx()); assertEquals(.5, midpoint.ny()); assertEquals(0, midpoint.nz());
        assertEquals(0xff808080, midpoint.color()); assertEquals(0x00780078, midpoint.lightmap());
    }

    private static Vertex point(double u, double v) {
        return new Vertex(u * 3, v * 3, 0, u, v, 0, 0, 1, -1, 0x00f000f0);
    }

    private static final class Quad {
        static final float[][] XYZ = {{.1F, 0, .2F}, {.9F, .4F, .2F}, {.9F, 3, .8F}, {.1F, 0, .2F}};
        static final float[][] UV = {{.1F, .2F}, {.9F, .2F}, {.9F, .8F}, {.1F, .2F}};
        final float[][] xyz = java.util.Arrays.stream(XYZ).map(float[]::clone).toArray(float[][]::new);
        final float[][] uv = java.util.Arrays.stream(UV).map(float[]::clone).toArray(float[][]::new);
        final MutableQuadView view = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x", "y", "z" -> xyz[(int) args[0]]["xyz".indexOf(method.getName())];
                    case "u", "v" -> uv[(int) args[0]][method.getName().equals("u") ? 0 : 1];
                    case "pos" -> { for (int i = 0; i < 3; i++) xyz[(int) args[0]][i] = (float) args[i + 1]; yield proxy; }
                    case "uv" -> { uv[(int) args[0]][0] = (float) args[1]; uv[(int) args[0]][1] = (float) args[2]; yield proxy; }
                    case "cullFace" -> proxy;
                    default -> throw new AssertionError("Legacy size transform changed vertex metadata: " + method.getName());
                });
    }
}
