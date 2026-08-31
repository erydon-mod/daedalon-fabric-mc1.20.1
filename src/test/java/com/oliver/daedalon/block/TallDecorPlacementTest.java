package com.oliver.daedalon.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TallDecorPlacementTest {
    @Test
    void riseUsesTheFirstWholeCellAboveTheOutline() {
        assertEquals(1, TallDecorPlacement.placementRise(1.0D));
        assertEquals(1, TallDecorPlacement.placementRise(1.00000001D));
        assertEquals(2, TallDecorPlacement.placementRise(1.5D));
        assertEquals(2, TallDecorPlacement.placementRise(2.0D));
        assertEquals(4, TallDecorPlacement.placementRise(3.02D));
    }

    @Test
    void horizontalHitBoundsAcceptEdgesButRejectUnrelatedModels() {
        assertTrue(TallDecorPlacement.contains(0.0D, 0.0D, 1.0D));
        assertTrue(TallDecorPlacement.contains(1.00005D, 0.0D, 1.0D));
        assertFalse(TallDecorPlacement.contains(1.01D, 0.0D, 1.0D));
    }

    @Test
    void extendedOutlineHitIsNormalisedOnlyToVanillasPacketLimit() {
        assertEquals(1.0D, TallDecorPlacement.clampForVanillaPacketCheck(1.5D));
        assertEquals(-1.0D, TallDecorPlacement.clampForVanillaPacketCheck(-2.0D));
        assertEquals(0.625D, TallDecorPlacement.clampForVanillaPacketCheck(0.625D));
    }
}
