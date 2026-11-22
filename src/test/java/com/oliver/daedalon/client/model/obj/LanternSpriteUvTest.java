package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LanternSpriteUvTest {
    @Test void roofSeamVerticesCannotSampleAnAdjacentAtlasSprite() {
        for (int size : new int[]{1, 16, 32, 64, 128}) {
            for (float original : new float[]{0, .000055194F, .014F, .5F, .998331875F, 1}) {
                // Texture lookup coordinates address pixel centres at n + 0.5.
                // Both taps of bilinear filtering must belong to this sprite.
                float texel = LanternSpriteUv.inset(original, size) * size - .5F;
                assertTrue(texel >= -1e-5F && texel <= size - 1 + 1e-5F);
            }
        }
        assertTrue(0 * 16 - .5F < 0, "The former UV edge reaches the neighbouring sprite");
    }

    @Test void interiorMappingStaysAffineWithoutFoldingOrMovingTheCentre() {
        for (int size : new int[]{16, 64}) {
            float left = LanternSpriteUv.inset(.15F, size), right = LanternSpriteUv.inset(.85F, size);
            assertTrue(left < right);
            assertEquals(.5F, LanternSpriteUv.inset(.5F, size));
            assertEquals((left + right) / 2, LanternSpriteUv.inset((.15F + .85F) / 2, size), 1e-6F);
        }
        assertThrows(IllegalArgumentException.class, () -> LanternSpriteUv.inset(1.25F, 16));
        assertThrows(IllegalArgumentException.class, () -> LanternSpriteUv.inset(Float.NaN, 16));
    }
}
