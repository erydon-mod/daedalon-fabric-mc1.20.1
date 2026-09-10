package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Texture-independent full-mesh projection shared by every material variant
 * in one OBJ family. The supersample grid is resolved into the
 * dynamic icon atlas only when a particular material icon is first shown.
 */
final class ObjGuiIconTemplate {
    // Keep four times Minecraft's logical 16 px item resolution so fine OBJ
    // details survive the final GUI reduction. One sample per output pixel
    // retains the same 64x64 family projection and 4,096 material samples as
    // the former 32 px / 2x path, so this does not restore per-item mesh work.
    static final int ICON_RESOLUTION = 64;
    static final int SUPERSAMPLE = 1;
    static final int SAMPLE_RESOLUTION = ICON_RESOLUTION * SUPERSAMPLE;
    static final int FRAME_PADDING = SUPERSAMPLE;

    private final ObjGuiRasterizer rasterizer;
    private final int sourceQuadCount;
    private final int resolution;

    private ObjGuiIconTemplate(ObjGuiRasterizer rasterizer, int sourceQuadCount, int resolution) {
        this.rasterizer = rasterizer;
        this.sourceQuadCount = sourceQuadCount;
        this.resolution = resolution;
    }

    static ObjGuiIconTemplate create(Mesh mesh,
                                     Transformation guiTransformation,
                                     int expectedQuadCount,
                                     float frameScale) {
        return create(mesh, guiTransformation, expectedQuadCount, frameScale, ICON_RESOLUTION);
    }

    static ObjGuiIconTemplate create(Mesh mesh, Transformation guiTransformation,
                                     int expectedQuadCount, float frameScale, int resolution) {
        if (resolution < 16 || resolution > 256) throw new IllegalArgumentException("Icon resolution out of bounds");
        MatrixStack matrices = new MatrixStack();
        guiTransformation.apply(false, matrices);
        matrices.translate(-0.5D, -0.5D, -0.5D);
        Matrix4f positionMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
        ObjGuiRasterizer rasterizer = new ObjGuiRasterizer(resolution * SUPERSAMPLE);
        ObjGuiRasterizer.Vertex a = new ObjGuiRasterizer.Vertex();
        ObjGuiRasterizer.Vertex b = new ObjGuiRasterizer.Vertex();
        ObjGuiRasterizer.Vertex c = new ObjGuiRasterizer.Vertex();
        ObjGuiRasterizer.Vertex d = new ObjGuiRasterizer.Vertex();
        Vector3f positionScratch = new Vector3f();
        Vector3f normalScratch = new Vector3f();
        ProjectionBounds bounds = ProjectionBounds.measure(mesh, positionMatrix, positionScratch, frameScale, resolution * SUPERSAMPLE);
        int[] visitedQuads = {0};

        mesh.forEach(quad -> {
            project(quad, 0, positionMatrix, normalMatrix, bounds, positionScratch, normalScratch, a);
            project(quad, 1, positionMatrix, normalMatrix, bounds, positionScratch, normalScratch, b);
            project(quad, 2, positionMatrix, normalMatrix, bounds, positionScratch, normalScratch, c);
            project(quad, 3, positionMatrix, normalMatrix, bounds, positionScratch, normalScratch, d);
            int materialTag = ShaderTerrainNormalBridge.decodeObjMaterialTag(quad.tag());
            rasterizer.rasterize(a, b, c, materialTag);
            rasterizer.rasterize(a, c, d, materialTag);
            visitedQuads[0]++;
        });

        if (visitedQuads[0] != expectedQuadCount) {
            throw new IllegalStateException(
                    "Full OBJ GUI projection visited " + visitedQuads[0]
                            + " quads but the geometry bake reported " + expectedQuadCount
            );
        }
        if (rasterizer.coveredSampleCount() == 0) {
            throw new IllegalStateException("Full OBJ GUI projection produced no covered samples");
        }
        return new ObjGuiIconTemplate(rasterizer, visitedQuads[0], resolution);
    }

    int resolution() { return resolution; }

    int sourceQuadCount() {
        return sourceQuadCount;
    }

    int coveredSampleCount() {
        return rasterizer.coveredSampleCount();
    }

    boolean isCovered(int index) {
        return rasterizer.isCovered(index);
    }

    int materialAt(int index) {
        return rasterizer.materialAt(index);
    }

    float uAt(int index) {
        return rasterizer.uAt(index);
    }

    float vAt(int index) {
        return rasterizer.vAt(index);
    }

    float shadeAt(int index) {
        return rasterizer.shadeAt(index);
    }

    private static void project(QuadView quad,
                                int vertex,
                                Matrix4f positionMatrix,
                                Matrix3f normalMatrix,
                                ProjectionBounds bounds,
                                Vector3f position,
                                Vector3f normal,
                                ObjGuiRasterizer.Vertex target) {
        position.set(quad.x(vertex), quad.y(vertex), quad.z(vertex));
        positionMatrix.transformPosition(position);

        if (quad.hasNormal(vertex)) {
            normal.set(quad.normalX(vertex), quad.normalY(vertex), quad.normalZ(vertex));
        } else {
            normal.set(quad.faceNormal());
        }
        normalMatrix.transform(normal);
        if (normal.lengthSquared() > 1.0E-20F) {
            normal.normalize();
        }

        target.set(
                bounds.sampleResolution() * 0.5F + (position.x - bounds.centerX()) * bounds.scale(),
                bounds.sampleResolution() * 0.5F - (position.y - bounds.centerY()) * bounds.scale(),
                position.z,
                quad.u(vertex),
                quad.v(vertex),
                normal.x,
                normal.y,
                normal.z
        );
    }

    /** Auto-frames the projected full mesh so narrow decor does not get lost in a slot. */
    private record ProjectionBounds(float centerX, float centerY, float scale, int sampleResolution) {
        private static ProjectionBounds measure(Mesh mesh, Matrix4f matrix, Vector3f scratch, float frameScale, int sampleResolution) {
            float[] values = {
                    Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
            };
            int[] vertices = {0};
            mesh.forEach(quad -> {
                for (int vertex = 0; vertex < 4; vertex++) {
                    scratch.set(quad.x(vertex), quad.y(vertex), quad.z(vertex));
                    matrix.transformPosition(scratch);
                    values[0] = Math.min(values[0], scratch.x);
                    values[1] = Math.min(values[1], scratch.y);
                    values[2] = Math.max(values[2], scratch.x);
                    values[3] = Math.max(values[3], scratch.y);
                    vertices[0]++;
                }
            });
            float width = values[2] - values[0];
            float height = values[3] - values[1];
            if (vertices[0] == 0 || !Float.isFinite(width) || !Float.isFinite(height)
                    || width <= 1.0E-6F || height <= 1.0E-6F) {
                throw new IllegalStateException("Full OBJ GUI projection has no finite two-dimensional bounds");
            }
            float available = sampleResolution - FRAME_PADDING * 2.0F;
            return new ProjectionBounds(
                    (values[0] + values[2]) * 0.5F,
                    (values[1] + values[3]) * 0.5F,
                    Math.min(available / width, available / height) * frameScale,
                    sampleResolution
            );
        }
    }
}
