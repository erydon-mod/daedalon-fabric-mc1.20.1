package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;
import net.minecraft.util.Identifier;

import static org.junit.jupiter.api.Assertions.*;

final class ObjGuiMetalLightingTest {
    @Test void onlyAuthoredMetalMaterialsReceiveReflections() {
        for (String texture : new String[]{"block/bronze", "block/silver", "block/urn_diota_bronze_handles"}) {
            assertTrue(ObjGuiMetalLighting.isMetalTexture(new Identifier("daedalon", texture)));
        }
        assertFalse(ObjGuiMetalLighting.isMetalTexture(new Identifier("daedalon", "block/statue_spartan_promachos_glacium")));
        assertFalse(ObjGuiMetalLighting.isMetalTexture(new Identifier("daedalon", "block/white")));
        assertFalse(ObjGuiMetalLighting.isMetalTexture(new Identifier("other", "block/silver")));
    }

    @Test void reflectionsFollowSurfaceDirectionAndRemainBoundedAcrossTheHemisphere() {
        float darkest = 1, brightest = 0;
        for (int latitude = 0; latitude <= 90; latitude++) {
            double angle = Math.toRadians(latitude);
            for (int longitude = 0; longitude < 360; longitude += 3) {
                double azimuth = Math.toRadians(longitude);
                float x = (float) (Math.sin(angle) * Math.cos(azimuth));
                float y = (float) (Math.sin(angle) * Math.sin(azimuth));
                float z = (float) Math.cos(angle);
                int reflection = ObjGuiMetalLighting.reflection(x, y, z);
                for (int shift : new int[]{0, 8, 16}) {
                    float value = ObjGuiMetalLighting.channel(0.625F, reflection, shift, 0.7F);
                    assertTrue(Float.isFinite(value) && value >= 0 && value < 1);
                    darkest = Math.min(darkest, value);
                    brightest = Math.max(brightest, value);
                }
            }
        }
        assertTrue(darkest > 0.2F && darkest < 0.3F, "Dark reflections must retain readable model midtones");
        assertTrue(brightest > 0.6F, "Metal needs clear bright studio reflections");
        assertTrue(brightest - darkest > 0.3F);
    }

    @Test void equivalentNormalsRetainTheSameReflectionAndBadNormalsStayFinite() {
        int unit = ObjGuiMetalLighting.reflection(0, 0.6F, 0.8F);
        assertEquals(unit, ObjGuiMetalLighting.reflection(0, 6, 8));
        assertEquals(unit, ObjGuiMetalLighting.reflection(0, -0.6F, -0.8F));
        for (float invalid : new float[]{0, Float.NaN, Float.POSITIVE_INFINITY}) {
            int reflection = ObjGuiMetalLighting.reflection(invalid, 0, 0);
            assertTrue(Float.isFinite(ObjGuiMetalLighting.channel(0.625F, reflection, 0, 0.7F)));
        }
    }

    @Test void sourceMetalTintIsRetainedAndHighlightsDoNotClip() {
        int reflection = ObjGuiMetalLighting.reflection(-0.2F, 0.2F, 0.96F);
        float bronzeRed = ObjGuiMetalLighting.channel(0.79F, reflection, 0, 0.7F);
        float bronzeGreen = ObjGuiMetalLighting.channel(0.36F, reflection, 8, 0.7F);
        float bronzeBlue = ObjGuiMetalLighting.channel(0.008F, reflection, 16, 0.7F);
        assertTrue(bronzeRed > bronzeGreen && bronzeGreen > bronzeBlue);
        assertTrue(bronzeRed < 1);
        assertTrue(ObjGuiMetalLighting.channel(0.5F, reflection, 0, 0.7F)
                > ObjGuiMetalLighting.channel(0.1F, reflection, 0, 0.7F));
    }

    @Test void modelLightStillDescribesTheFormInsideDarkAndBrightReflections() {
        for (float normalX : new float[]{-0.2F, 0.0F, 0.7F}) {
            int reflection = ObjGuiMetalLighting.reflection(normalX, 0.2F, 0.7F);
            float shadow = ObjGuiMetalLighting.channel(0.625F, reflection, 0, 0.4F);
            float light = ObjGuiMetalLighting.channel(0.625F, reflection, 0, 1.0F);
            assertTrue(light - shadow > 0.15F, "Reflections must retain enough form lighting to read faces and clothing");
        }
    }
}
