package com.oliver.daedalon.client.model.obj;

import java.util.Arrays;

/**
 * Small deterministic orthographic rasterizer used only to prepare cached GUI
 * icons. It keeps the complete source mesh out of the per-frame item path
 * without changing or deleting any source geometry.
 */
final class ObjGuiRasterizer {
    private static final float AREA_EPSILON = 1.0E-6F;
    private static final float BARYCENTRIC_EPSILON = 1.0E-5F;
    // Match Minecraft's own GUI model lights and light.glsl exactly. The cached
    // icon therefore reads like a normal vanilla item rather than a separately
    // graded thumbnail.
    private static final float LIGHT_POWER = 0.6F;
    private static final float AMBIENT_LIGHT = 0.4F;
    private static final float KEY_LIGHT_LENGTH_INVERSE =
            (float) (1.0D / Math.sqrt(0.2D * 0.2D + 1.0D + 0.7D * 0.7D));
    private static final float KEY_LIGHT_X = 0.2F * KEY_LIGHT_LENGTH_INVERSE;
    private static final float KEY_LIGHT_Y = KEY_LIGHT_LENGTH_INVERSE;
    private static final float KEY_LIGHT_Z = -0.7F * KEY_LIGHT_LENGTH_INVERSE;
    private static final float FILL_LIGHT_LENGTH_INVERSE =
            (float) (1.0D / Math.sqrt(0.2D * 0.2D + 1.0D + 0.7D * 0.7D));
    private static final float FILL_LIGHT_X = -0.2F * FILL_LIGHT_LENGTH_INVERSE;
    private static final float FILL_LIGHT_Y = FILL_LIGHT_LENGTH_INVERSE;
    private static final float FILL_LIGHT_Z = 0.7F * FILL_LIGHT_LENGTH_INVERSE;

    private final int resolution;
    private final float[] depth;
    private final float[] u;
    private final float[] v;
    private final float[] shade;
    private final int[] material;
    private int coveredSampleCount;

    ObjGuiRasterizer(int resolution) {
        if (resolution <= 0) {
            throw new IllegalArgumentException("Raster resolution must be positive");
        }
        this.resolution = resolution;
        int sampleCount = Math.multiplyExact(resolution, resolution);
        this.depth = new float[sampleCount];
        this.u = new float[sampleCount];
        this.v = new float[sampleCount];
        this.shade = new float[sampleCount];
        this.material = new int[sampleCount];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        Arrays.fill(material, -1);
    }

    void rasterize(Vertex a, Vertex b, Vertex c, int materialTag) {
        float area = edge(a.x(), a.y(), b.x(), b.y(), c.x(), c.y());
        if (!Float.isFinite(area) || Math.abs(area) <= AREA_EPSILON) {
            return;
        }

        int minX = clampPixel((int) Math.floor(Math.min(a.x(), Math.min(b.x(), c.x()))));
        int maxX = clampPixel((int) Math.ceil(Math.max(a.x(), Math.max(b.x(), c.x()))));
        int minY = clampPixel((int) Math.floor(Math.min(a.y(), Math.min(b.y(), c.y()))));
        int maxY = clampPixel((int) Math.ceil(Math.max(a.y(), Math.max(b.y(), c.y()))));

        for (int pixelY = minY; pixelY <= maxY; pixelY++) {
            for (int pixelX = minX; pixelX <= maxX; pixelX++) {
                float sampleX = pixelX + 0.5F;
                float sampleY = pixelY + 0.5F;
                float weightA = edge(b.x(), b.y(), c.x(), c.y(), sampleX, sampleY) / area;
                float weightB = edge(c.x(), c.y(), a.x(), a.y(), sampleX, sampleY) / area;
                float weightC = 1.0F - weightA - weightB;
                if (weightA < -BARYCENTRIC_EPSILON
                        || weightB < -BARYCENTRIC_EPSILON
                        || weightC < -BARYCENTRIC_EPSILON) {
                    continue;
                }

                float candidateDepth = weightA * a.depth()
                        + weightB * b.depth()
                        + weightC * c.depth();
                int index = pixelY * resolution + pixelX;
                if (!Float.isFinite(candidateDepth) || candidateDepth <= depth[index]) {
                    continue;
                }

                if (material[index] < 0) {
                    coveredSampleCount++;
                }
                depth[index] = candidateDepth;
                u[index] = weightA * a.u() + weightB * b.u() + weightC * c.u();
                v[index] = weightA * a.v() + weightB * b.v() + weightC * c.v();
                float normalX = weightA * a.normalX()
                        + weightB * b.normalX()
                        + weightC * c.normalX();
                float normalY = weightA * a.normalY()
                        + weightB * b.normalY()
                        + weightC * c.normalY();
                float normalZ = weightA * a.normalZ()
                        + weightB * b.normalZ()
                        + weightC * c.normalZ();
                shade[index] = guiShade(normalX, normalY, normalZ);
                material[index] = materialTag;
            }
        }
    }

    int resolution() {
        return resolution;
    }

    int sampleCount() {
        return material.length;
    }

    int coveredSampleCount() {
        return coveredSampleCount;
    }

    boolean isCovered(int index) {
        return material[index] >= 0;
    }

    int materialAt(int index) {
        return material[index];
    }

    float uAt(int index) {
        return u[index];
    }

    float vAt(int index) {
        return v[index];
    }

    float shadeAt(int index) {
        return shade[index];
    }

    float depthAt(int index) {
        return depth[index];
    }

    private int clampPixel(int pixel) {
        return Math.max(0, Math.min(resolution - 1, pixel));
    }

    private static float edge(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static float guiShade(float normalX, float normalY, float normalZ) {
        float lengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-20F) {
            return AMBIENT_LIGHT;
        }
        float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
        float x = normalX * inverseLength;
        float y = normalY * inverseLength;
        float z = normalZ * inverseLength;
        float keyLight = Math.max(0.0F,
                KEY_LIGHT_X * x + KEY_LIGHT_Y * y + KEY_LIGHT_Z * z);
        float fillLight = Math.max(0.0F,
                FILL_LIGHT_X * x + FILL_LIGHT_Y * y + FILL_LIGHT_Z * z);
        return Math.min(1.0F, AMBIENT_LIGHT + (keyLight + fillLight) * LIGHT_POWER);
    }

    static final class Vertex {
        private float x;
        private float y;
        private float depth;
        private float u;
        private float v;
        private float normalX;
        private float normalY;
        private float normalZ;

        Vertex() {
        }

        Vertex(float x,
               float y,
               float depth,
               float u,
               float v,
               float normalX,
               float normalY,
               float normalZ) {
            set(x, y, depth, u, v, normalX, normalY, normalZ);
        }

        void set(float x,
                 float y,
                 float depth,
                 float u,
                 float v,
                 float normalX,
                 float normalY,
                 float normalZ) {
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.u = u;
            this.v = v;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }

        float x() {
            return x;
        }

        float y() {
            return y;
        }

        float depth() {
            return depth;
        }

        float u() {
            return u;
        }

        float v() {
            return v;
        }

        float normalX() {
            return normalX;
        }

        float normalY() {
            return normalY;
        }

        float normalZ() {
            return normalZ;
        }
    }
}
