package com.oliver.daedalon.client.fountain;

import net.minecraft.client.render.VertexConsumer;

/** One upward-facing, water-aligned quad. No camera rotation or per-frame allocations. */
final class FountainRippleGeometry {
    private FountainRippleGeometry() { }

    static void emit(VertexConsumer vertices, float x, float y, float z, float radius,
                     float minU, float maxU, float minV, float maxV,
                     float red, float green, float blue, float alpha, int light) {
        vertex(vertices, x - radius, y, z - radius, minU, minV, red, green, blue, alpha, light);
        vertex(vertices, x - radius, y, z + radius, minU, maxV, red, green, blue, alpha, light);
        vertex(vertices, x + radius, y, z + radius, maxU, maxV, red, green, blue, alpha, light);
        vertex(vertices, x + radius, y, z - radius, maxU, minV, red, green, blue, alpha, light);
    }

    private static void vertex(VertexConsumer vertices, float x, float y, float z, float u, float v,
                               float red, float green, float blue, float alpha, int light) {
        vertices.vertex(x, y, z).texture(u, v).color(red, green, blue, alpha).light(light).next();
    }
}
