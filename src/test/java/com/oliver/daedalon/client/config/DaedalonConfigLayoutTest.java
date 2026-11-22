package com.oliver.daedalon.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DaedalonConfigLayoutTest {
    @Test
    void compactPanelsAndPinnedFootersStayInsideSmallAndLargeWindows() {
        for (int[] size : new int[][] {{320, 180}, {320, 240}, {384, 216}, {640, 360}, {960, 540}, {1920, 1080}}) {
            var frame = DaedalonConfigLayout.frame(size[0], size[1]);
            assertTrue(frame.left() >= 0 && frame.top() >= 0);
            assertTrue(frame.right() <= size[0] && frame.bottom() <= size[1]);
            assertTrue(frame.width() <= 360 && frame.height() <= 220);
            assertTrue(frame.contentTop() >= frame.top() + 60);
            assertTrue(frame.contentHeight() >= DaedalonConfigLayout.BUTTON_HEIGHT);
            assertTrue(frame.contentBottom() < frame.footerTop());
            assertTrue(frame.footerTop() + DaedalonConfigLayout.BUTTON_HEIGHT <= frame.bottom());
            assertTrue(frame.contentLeft() + frame.contentWidth() <= frame.right());
        }
    }

    @Test
    void everyControlRemainsReachableAfterLongLocalizedParagraphsAtEveryViewport() {
        for (int screenHeight : new int[] {180, 216, 240, 540}) {
            var frame = DaedalonConfigLayout.frame(320, screenHeight);
            int paragraphHeight = 19 * 12 + 16;
            int rows = 14;
            int rowHeight = DaedalonConfigLayout.BUTTON_HEIGHT + DaedalonConfigLayout.ROW_GAP;
            int bodyHeight = paragraphHeight + rows * rowHeight;
            for (int row = 0; row < rows; row++) {
                int top = paragraphHeight + row * rowHeight;
                int scroll = DaedalonConfigLayout.clampScroll(top, bodyHeight, frame.contentHeight());
                assertTrue(DaedalonConfigLayout.controlVisible(top, DaedalonConfigLayout.BUTTON_HEIGHT,
                        scroll, frame.contentHeight()), "A long translation must not hide a setting permanently");
            }
        }
    }

    @Test
    void repeatedPageKeysRevealEveryPossibleControlOffsetInBothDirections() {
        int contentHeight = 900;
        int controlHeight = DaedalonConfigLayout.BUTTON_HEIGHT;
        for (int viewport : new int[] {20, 21, 52, 106, 108}) {
            int maxScroll = DaedalonConfigLayout.maxScroll(contentHeight, viewport);
            for (boolean down : new boolean[] {true, false}) {
                boolean[] reached = new boolean[contentHeight - controlHeight + 1];
                int scroll = down ? 0 : maxScroll;
                while (true) {
                    for (int top = 0; top < reached.length; top++) {
                        if (DaedalonConfigLayout.controlVisible(top, controlHeight, scroll, viewport)) reached[top] = true;
                    }
                    if (scroll == (down ? maxScroll : 0)) break;
                    int direction = down ? 1 : -1;
                    scroll = DaedalonConfigLayout.clampScroll(scroll + direction * DaedalonConfigLayout.pageStep(viewport),
                            contentHeight, viewport);
                }
                for (int top = 0; top < reached.length; top++) {
                    assertTrue(reached[top], "Keyboard paging skipped control at " + top
                            + "; viewport=" + viewport + "; down=" + down);
                }
            }
        }
    }

    @Test
    void scrollLimitsAndPartialControlsCannotOverlapHeaderOrFooter() {
        int viewport = 106;
        assertEquals(0, DaedalonConfigLayout.clampScroll(-100, 900, viewport));
        assertEquals(794, DaedalonConfigLayout.clampScroll(10_000, 900, viewport));
        assertEquals(0, DaedalonConfigLayout.maxScroll(80, viewport));
        assertFalse(DaedalonConfigLayout.controlVisible(100, 20, 0, viewport));
        assertFalse(DaedalonConfigLayout.controlVisible(0, 20, 4, viewport));
        assertTrue(DaedalonConfigLayout.controlVisible(86, 20, 0, viewport));
    }

    @Test
    void slimScrollHandlesStayInsideNormalAndShortBodies() {
        for (int[] size : new int[][] {{320, 180}, {320, 240}, {384, 216}, {960, 540}}) {
            var frame = DaedalonConfigLayout.frame(size[0], size[1]);
            for (int maximum : new int[] {1, 80, 500}) {
                for (int value : new int[] {-10, 0, maximum / 2, maximum, maximum + 10}) {
                    var bar = DaedalonConfigLayout.scrollBar(frame, value, maximum);
                    assertEquals(7, bar.width());
                    assertTrue(bar.handleHeight() >= 15);
                    assertTrue(bar.handleTop() >= frame.contentTop());
                    assertTrue(bar.handleBottom() <= frame.contentBottom());
                    assertTrue(bar.left() >= frame.contentLeft() + frame.contentWidth());
                    assertTrue(bar.right() <= frame.right());
                    assertTrue(bar.bottom() < frame.footerTop());
                }
            }
        }
    }

    @Test
    void slimHandleDrawingAndPointerMappingShareBothEndpoints() {
        for (int height : new int[] {180, 216, 240, 540}) {
            var frame = DaedalonConfigLayout.frame(320, height);
            var first = DaedalonConfigLayout.scrollBar(frame, 0, 500);
            var last = DaedalonConfigLayout.scrollBar(frame, 500, 500);
            assertEquals(frame.contentTop(), first.handleTop());
            assertEquals(frame.contentBottom(), last.handleBottom());
            assertEquals(0, first.valueAt(first.handleTop() + first.handleHeight() / 2.0));
            assertEquals(500, last.valueAt(last.handleTop() + last.handleHeight() / 2.0));
            assertEquals(0, first.valueAt(-1000));
            assertEquals(500, first.valueAt(10000));
            assertTrue(first.contains(first.left(), first.top()));
            assertFalse(first.contains(first.right(), first.top()));
            assertFalse(first.contains(first.left(), first.bottom()));
        }
    }
}
