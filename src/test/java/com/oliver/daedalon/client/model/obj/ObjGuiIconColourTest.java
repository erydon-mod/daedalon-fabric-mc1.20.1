package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

final class ObjGuiIconColourTest {
    @Test void linearLookupRetainsTheExistingGammaWithinOneChannelStep() {
        for (int sample = 0; sample <= 1_000_000; sample++) {
            double linear = sample / 1_000_000.0;
            int previous = (int) Math.round(Math.pow(linear, 1.0 / 2.2) * 255.0);
            assertTrue(Math.abs(previous - ObjGuiIconCache.linearToSrgb(linear)) <= 1,
                    "Gamma lookup differs at " + linear);
        }
        assertEquals(0, ObjGuiIconCache.linearToSrgb(-1));
        assertEquals(255, ObjGuiIconCache.linearToSrgb(2));
    }

    @Test void immutableSourceSamplesKeepAsymmetricAbgrColoursAndRejectEscapingUvs() throws IOException {
        int red = 0xFF1835E7, blue = 0xFFD96321, green = 0xFF43BA52, other = 0xFFA76938;
        var source = new ObjGuiIconCache.SourceTexture(2, 2, new int[]{red, blue, green, other});
        assertEquals(red, source.sample(0, 0));
        assertEquals(blue, source.sample(1, 0));
        assertEquals(green, source.sample(0, 1));
        assertEquals(other, source.sample(1, 1));
        assertThrows(IOException.class, () -> source.sample(Float.NaN, 0));
        assertThrows(IOException.class, () -> source.sample(-.1F, 0));
        assertThrows(IOException.class, () -> source.sample(0, 1.1F));
    }
}
