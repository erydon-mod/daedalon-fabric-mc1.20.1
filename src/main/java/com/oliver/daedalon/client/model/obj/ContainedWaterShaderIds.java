package com.oliver.daedalon.client.model.obj;

/** Selects the stationary translucent-water material from Complementary's material vocabulary. */
public final class ContainedWaterShaderIds {
    private static final int COMPLEMENTARY_WATER = 32000;
    private static final int COMPLEMENTARY_CAULDRON_BLOCK = 10049;
    private static final int COMPLEMENTARY_CAULDRON_WATER = 32001;

    private ContainedWaterShaderIds() { }

    public static int stationaryWater(int water, int waterCauldron) {
        // Complementary's cauldron-water ID skips the vertex wave. IPBR already
        // shades it as water; ComplementaryBasinWater covers the custom PBR pass.
        return water == COMPLEMENTARY_WATER && waterCauldron == COMPLEMENTARY_CAULDRON_BLOCK
                ? COMPLEMENTARY_CAULDRON_WATER : water;
    }
}
