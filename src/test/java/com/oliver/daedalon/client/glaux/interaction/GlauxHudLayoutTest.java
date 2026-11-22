package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxHudLayoutTest {
    @Test void conversationStaysBesideCentreOwlAtCommonGuiWidths() {
        for (int screen : new int[]{320, 427, 480, 640, 854, 960}) {
            int anchor = screen / 2, width = GlauxHudLayout.width(screen), x = GlauxHudLayout.x(anchor, screen, width);
            assertTrue(width >= 120 && width <= 260);
            assertTrue(x >= 8 && x + width <= screen - 8);
            assertTrue(anchor < x || anchor > x + width, "The chat covered the actual owl at GUI width " + screen);
        }
    }

    @Test void boxSwitchesSideAndStaysOnscreenNearViewEdges() {
        int width = GlauxHudLayout.width(427);
        assertTrue(GlauxHudLayout.x(30, 427, width) > 30);
        assertTrue(GlauxHudLayout.x(397, 427, width) + width < 397);
    }
}
