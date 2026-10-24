package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.Identifier;

/** Fixed studio reflections, prepared once with the shared icon projection. */
final class ObjGuiMetalLighting {
    private static final float RADIANCE_RANGE = 4.0F;
    private static final Identifier BRONZE = new Identifier("daedalon", "block/bronze");
    private static final Identifier SILVER = new Identifier("daedalon", "block/silver");
    private static final Identifier BRONZE_HANDLES = new Identifier("daedalon", "block/urn_diota_bronze_handles");

    private ObjGuiMetalLighting() { }

    static boolean isMetalTexture(Identifier texture) {
        // Keep classification independent of model-plugin/registry bootstrap.
        return BRONZE.equals(texture) || SILVER.equals(texture) || BRONZE_HANDLES.equals(texture);
    }

    static int reflection(float normalX, float normalY, float normalZ) {
        float lengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (!Float.isFinite(lengthSquared) || lengthSquared <= 1.0E-20F) {
            return pack(0.12F, 0.12F, 0.12F, 0.0F);
        }
        float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
        float x = normalX * inverseLength;
        float y = normalY * inverseLength;
        float z = normalZ * inverseLength;
        // Orthographic camera looks down -Z. Reflect its view direction in the
        // actual interpolated surface normal; no image-space shine or sparkle.
        float reflectedX = 2.0F * x * z;
        float reflectedY = 2.0F * y * z;
        float reflectedZ = 2.0F * z * z - 1.0F;
        float front = smoothstep(-0.25F, 0.25F, reflectedZ);
        float key = softbox(reflectedX + 0.4F, 0.16F, 0.65F)
                * softbox(reflectedY - 0.35F, 0.25F, 0.9F) * front;
        float fill = softbox(reflectedX - 0.75F, 0.08F, 0.35F)
                * softbox(reflectedY + 0.1F, 0.6F, 1.1F) * front;
        float ceiling = smoothstep(0.55F, 0.9F, reflectedY);
        float room = 0.12F + 0.035F * Math.max(0.0F, reflectedY);
        float grazing = 1.0F - Math.min(1.0F, Math.abs(z));
        float grazingSquared = grazing * grazing;
        float fresnel = grazingSquared * grazingSquared * grazing;
        return pack(room + 2.6F * key + 0.85F * fill + 0.55F * ceiling,
                room + 2.5F * key + 1.0F * fill + 0.58F * ceiling,
                room + 2.35F * key + 1.15F * fill + 0.65F * ceiling, fresnel);
    }

    static float channel(float sourceLinear, int reflection, int shift) {
        float grazing = (reflection >>> 24) / 255.0F;
        float reflectance = sourceLinear + (1.0F - sourceLinear) * grazing;
        float radiance = (reflection >>> shift & 255) * (RADIANCE_RANGE / 255.0F);
        float exposed = reflectance * radiance * 1.8F;
        // Compress bright reflections without clipping their colour or detail.
        return exposed / (1.0F + exposed);
    }

    private static float softbox(float distance, float inner, float outer) {
        return 1.0F - smoothstep(inner, outer, Math.abs(distance));
    }

    private static float smoothstep(float low, float high, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - low) / (high - low)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int pack(float red, float green, float blue, float grazing) {
        return quantize(grazing) << 24 | quantize(blue / RADIANCE_RANGE) << 16
                | quantize(green / RADIANCE_RANGE) << 8 | quantize(red / RADIANCE_RANGE);
    }

    private static int quantize(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}
