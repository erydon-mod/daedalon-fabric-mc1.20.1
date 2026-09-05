package com.oliver.daedalon.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FountainWaterShapeTest {
    @Test
    void waterMatchesRoundAndLobedInteriorsInsteadOfTheOldOctagons() {
        FountainWaterShape round = FountainWaterShape.GEORGIAN;
        FountainWaterShape lobed = FountainWaterShape.GREEK;
        assertTrue(round.contains(0.28, 0.28));
        assertTrue(lobed.contains(0.37, 0.0));
        assertTrue(lobed.contains(0.0, -0.37));
        assertFalse(round.contains(0.45, 0.45));
        assertFalse(lobed.contains(0.45, 0.45));
        assertTrue(round.vertexCount() < 128);
        assertTrue(lobed.vertexCount() <= 256);
    }

    @Test
    void immersionCoversInsideAndCrossingBoxesButRejectsOutside() {
        for (FountainWaterShape shape : new FountainWaterShape[]{FountainWaterShape.GEORGIAN, FountainWaterShape.GREEK}) {
            assertTrue(shape.intersects(-0.02, -0.02, 0.02, 0.02));
            assertTrue(shape.intersects(-1, -1, 1, 1));
            assertTrue(shape.intersects(0.2, -0.01, 0.6, 0.01));
            assertFalse(shape.intersects(0.49, 0.49, 0.6, 0.6));
            assertFalse(shape.intersects(-0.6, -0.6, -0.49, -0.49));
        }
    }

    @Test
    void bowlSwapIncludesTheSourceGeometryAndStackingProfile() {
        assertEquals(FountainBowlModel.Style.GREEK, FountainBasinBlock.Style.GEORGIAN.bowlStyle());
        assertEquals(FountainBowlModel.Style.GEORGIAN, FountainBasinBlock.Style.GREEK.bowlStyle());
        assertEquals(FountainBowlModel.Style.GOTHIC, FountainBasinBlock.Style.GOTHIC.bowlStyle());
    }
}
