package com.oliver.daedalon.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorbelWallContactTest {
    @Test
    void everyLargeStyleIsExactlyOneBlockWideAndKeepsItsTopAtY16() {
        for (CorbelBlock.Style style : CorbelBlock.Style.values()) {
            CorbelBlock.CorbelSize size = CorbelBlock.CorbelSize.LARGE;
            float scale = style.modelScale(size);
            float transformedTop = CorbelBlock.TOP_ANCHOR_Y
                    + (CorbelBlock.SOURCE_TOP_Y - CorbelBlock.SOURCE_TOP_Y) * scale;
            float transformedBottom = CorbelBlock.TOP_ANCHOR_Y
                    + (0.0F - CorbelBlock.SOURCE_TOP_Y) * scale;

            assertEquals(
                    CorbelBlock.LARGE_WIDTH_IN_BLOCKS,
                    style.scaledWidth(size),
                    0.000001F,
                    () -> style + " " + size
            );
            assertEquals(
                    CorbelBlock.TOP_ANCHOR_Y,
                    transformedTop,
                    0.000001F,
                    () -> style + " " + size
            );
            assertEquals(
                    style.scaledHeight(size),
                    transformedTop - transformedBottom,
                    0.000001F,
                    () -> style + " " + size
            );
        }
    }

    @Test
    void everyStyleAndSizeAnchorsItsRearPlaneToTheWall() {
        for (CorbelBlock.Style style : CorbelBlock.Style.values()) {
            for (CorbelBlock.CorbelSize size : CorbelBlock.CorbelSize.values()) {
                float depth = style.scaledDepth(size);
                float shift = style.wallAnchorShift(size);
                float rear = 0.5F - depth * 0.5F + shift;
                float front = 0.5F + depth * 0.5F + shift;

                assertTrue(depth > 0.0F, () -> style + " " + size);
                assertEquals(0.0F, rear, 0.000001F, () -> style + " " + size);
                assertEquals(depth, front, 0.000001F, () -> style + " " + size);
            }
        }
    }
}
