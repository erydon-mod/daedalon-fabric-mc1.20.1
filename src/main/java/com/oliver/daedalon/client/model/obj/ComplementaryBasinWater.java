package com.oliver.daedalon.client.model.obj;

/** Admits the stationary basin ID to Complementary's custom PBR water shading. */
public final class ComplementaryBasinWater {
    private static final String WATER_DISPATCH = "if (mat == 32000) { // Water";
    private static final String BASIN_DISPATCH = "if (mat == 32000 || mat == 32001) { // Water";
    private static final String WATER_FOG = "float waterFog = max0(1.0 - exp(lViewPosDifM * 0.075));";
    private static final String BASIN_FOG = WATER_FOG + "\n                if (mat == 32001) waterFog = max(waterFog, 0.48);";

    private ComplementaryBasinWater() { }

    public static String adaptFragment(String program, String source) {
        if (!"gbuffers_water".equals(program)
                || !source.contains("Complementary Shaders by EminGT")
                || !source.contains("Step 2: Water Normals")
                || !source.contains("translucentMultCalculated")) {
            return source;
        }
        int dispatch = source.indexOf(WATER_DISPATCH);
        if (dispatch < 0 || source.indexOf(WATER_DISPATCH, dispatch + WATER_DISPATCH.length()) >= 0) {
            return source;
        }
        // IPBR already handles 32001. This branch is the custom PBR dispatch.
        // Leave the vertex and shadow programs untouched so the water level cannot wave.
        String adapted = source.substring(0, dispatch) + BASIN_DISPATCH
                + source.substring(dispatch + WATER_DISPATCH.length());
        int opticalDepth = adapted.indexOf(WATER_FOG, dispatch + BASIN_DISPATCH.length());
        if (opticalDepth < 0 || opticalDepth != adapted.lastIndexOf(WATER_FOG)) return adapted;
        // The bowl is shallow enough to disappear against light stone with
        // open-water fog density. Set a floor before CU applies WATER_ALPHA_MULT.
        return adapted.substring(0, opticalDepth) + BASIN_FOG
                + adapted.substring(opticalDepth + WATER_FOG.length());
    }
}
