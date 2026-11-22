package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ComplementaryBasinWaterTest {
    private static final String CUSTOM_PBR_WATER = """
            // Complementary Shaders by EminGT
            bool translucentMultCalculated = false;
            if (mat == 32000) { // Water
                // Step 2: Water Normals
                smoothWater();
                float waterFog = max0(1.0 - exp(lViewPosDifM * 0.075));
                color.a *= 0.25 + 0.75 * waterFog;
                if (mat == 32000) addOpenWaterFoam();
            }
            """;

    @Test
    void admitsStationaryWaterOnlyToTheSmoothFragmentBranch() {
        String adapted = ComplementaryBasinWater.adaptFragment("gbuffers_water", CUSTOM_PBR_WATER);
        String expected = CUSTOM_PBR_WATER.replace("mat == 32000) {", "mat == 32000 || mat == 32001) {")
                .replace("\n    color.a", "\n                if (mat == 32001) waterFog = max(waterFog, 0.48);\n    color.a");
        assertEquals(expected, adapted);
        assertSame(adapted, ComplementaryBasinWater.adaptFragment("gbuffers_water", adapted));
        assertSame(CUSTOM_PBR_WATER, ComplementaryBasinWater.adaptFragment("shadow", CUSTOM_PBR_WATER));
    }

    @Test
    void leavesOtherPacksAndUnfamiliarWaterBranchesUntouched() {
        String otherPack = CUSTOM_PBR_WATER.replace("Complementary Shaders by EminGT", "Other shader pack");
        String ambiguousDispatch = CUSTOM_PBR_WATER + CUSTOM_PBR_WATER;
        String ipbr = CUSTOM_PBR_WATER.replace("mat == 32000) {", "mat < 32004) {");
        assertSame(otherPack, ComplementaryBasinWater.adaptFragment("gbuffers_water", otherPack));
        assertSame(ambiguousDispatch, ComplementaryBasinWater.adaptFragment("gbuffers_water", ambiguousDispatch));
        assertSame(ipbr, ComplementaryBasinWater.adaptFragment("gbuffers_water", ipbr));
    }
}
