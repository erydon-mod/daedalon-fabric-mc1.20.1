package com.oliver.daedalon.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CapitalFitTest {
    private static final String[] STYLES = {"byzantine", "corinthian", "gothic", "greek_ionic", "roman_composite", "tuscan"};

    @Test void everyStyleFitsBothBareShaftsAtBothSizesWithoutChangingExistingHeadJoins() {
        for (String style : STYLES) for (CapitalBlock.Size size : CapitalBlock.Size.values()) {
            assertEquals(1, CapitalFit.scale(style, CapitalTransition.NONE, size));
            assertEquals(1, CapitalFit.scale(style, CapitalTransition.SQUARE, size));
            assertEquals(1, CapitalFit.scale(style, CapitalTransition.GOTHIC, size));
            for (CapitalTransition support : new CapitalTransition[]{CapitalTransition.SQUARE_SHAFT, CapitalTransition.GOTHIC_SHAFT}) {
                float fit = CapitalFit.scale(style, support, size);
                assertTrue(fit > 1 && fit <= CapitalFit.MAX_SCALE, style + "/" + support + "/" + size);
            }
        }
    }

    @Test void renderedEnvelopeGrowsDownwardWithoutMovingTheTopOrFootprintCentre() {
        for (String style : STYLES) for (CapitalBlock.Size size : CapitalBlock.Size.values()) {
            double multiple = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
            for (CapitalTransition support : CapitalTransition.values()) for (CapitalOrientation orientation : CapitalOrientation.values()) {
                float fit = CapitalFit.scale(style, support, size);
                var box = CapitalFit.bounds(1.317061573, .941511460, orientation, size, fit);
                assertEquals(multiple, box.maxY, 2e-5, "Capital must meet the same block above");
                assertEquals(multiple * (1 - fit), box.minY, 2e-5);
                assertEquals(multiple * .5, (box.minX + box.maxX) * .5, 1e-9);
                assertEquals(multiple * .5, (box.minZ + box.maxZ) * .5, 1e-9);
                if (fit > 1) assertTrue(box.minY < 0, "The shaft must receive the extra rendered height");
            }
        }
    }
}
