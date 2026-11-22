package com.oliver.daedalon.client.config;

/** Screen-independent bounds for the shared compact settings pages. */
final class DaedalonConfigLayout {
    static final int PANEL_WIDTH = 360;
    static final int PANEL_HEIGHT = 220;
    static final int BODY_TOP = 70;
    static final int FOOTER_HEIGHT = 42;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_GAP = 6;
    static final int SCROLLBAR_WIDTH = 7;
    static final int MIN_SCROLLBAR_HANDLE_HEIGHT = 15;
    private static final int CONTROL_TEXTURE_SIZE = 1024;

    private DaedalonConfigLayout() { }

    static Frame frame(int screenWidth, int screenHeight) {
        int width = Math.max(1, Math.min(PANEL_WIDTH, screenWidth - 24));
        int height = Math.max(1, Math.min(PANEL_HEIGHT, screenHeight - 16));
        return new Frame(Math.max(0, (screenWidth - width) / 2),
                Math.max(0, (screenHeight - height) / 2), width, height);
    }

    static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(1, viewportHeight));
    }

    /** Keep a complete button row in the overlap so keyboard paging cannot skip a control. */
    static int pageStep(int viewportHeight) {
        return Math.max(1, viewportHeight - BUTTON_HEIGHT);
    }

    static int clampScroll(int scroll, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(maxScroll(contentHeight, viewportHeight), scroll));
    }

    static boolean controlVisible(int relativeTop, int height, int scroll, int viewportHeight) {
        return relativeTop >= scroll && relativeTop + height <= scroll + viewportHeight;
    }

    /** Capture this once at the button's unscrolled layout position, never from its moving screen Y. */
    static ControlSample controlSample(int seedX, int seedY, int width, int height) {
        int sourceWidth = Math.min(CONTROL_TEXTURE_SIZE, Math.max(1, width * 4));
        int sourceHeight = Math.min(CONTROL_TEXTURE_SIZE, Math.max(1, height * 4));
        int sourceX = (int) Math.floorMod((long) seedX * 17 + (long) seedY * 7, 1025L - sourceWidth);
        int sourceY = (int) Math.floorMod((long) seedX * 5 + (long) seedY * 13, 1025L - sourceHeight);
        return new ControlSample(sourceX, sourceY, sourceWidth, sourceHeight);
    }

    static ScrollBar scrollBar(Frame panel, int scroll, int maximum) {
        int max = Math.max(0, maximum);
        int trackHeight = panel.contentHeight();
        int handleHeight = Math.min(trackHeight, Math.max(MIN_SCROLLBAR_HANDLE_HEIGHT,
                (int) ((long) trackHeight * trackHeight / Math.max(1L, (long) trackHeight + max))));
        int value = Math.max(0, Math.min(max, scroll));
        int handleTop = panel.contentTop() + (max > 0
                ? (int) ((long) (trackHeight - handleHeight) * value / max)
                : (trackHeight - handleHeight) / 2);
        return new ScrollBar(panel.right() - 22, panel.contentTop(), SCROLLBAR_WIDTH,
                trackHeight, handleTop, handleHeight, max);
    }

    record ControlSample(int sourceX, int sourceY, int sourceWidth, int sourceHeight) { }

    record ScrollBar(int left, int top, int width, int height, int handleTop, int handleHeight, int maxScroll) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        int handleBottom() { return handleTop + handleHeight; }
        boolean contains(double x, double y) { return x >= left && x < right() && y >= top && y < bottom(); }
        int valueAt(double mouseY) {
            double fraction = (mouseY - top - handleHeight / 2.0) / Math.max(1, height - handleHeight);
            return (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * maxScroll);
        }
    }

    record Frame(int left, int top, int width, int height) {
        int right() { return left + width; }
        int bottom() { return top + height; }
        int contentLeft() { return left + 24; }
        int contentWidth() { return Math.max(1, width - 48); }
        int contentTop() { return top + BODY_TOP; }
        int contentBottom() { return bottom() - FOOTER_HEIGHT; }
        int contentHeight() { return Math.max(1, contentBottom() - contentTop()); }
        int footerTop() { return bottom() - 29; }
    }
}
