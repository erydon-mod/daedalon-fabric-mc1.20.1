package com.oliver.daedalon.client.glaux.interaction;

/** Font-aware horizontal scrolling for the single-line, world-overlay composer. */
final class GlauxComposerViewport {
    interface Metrics {
        int width(String value);
        String trim(String value, int width, boolean backwards);
    }

    record Window(String text, int start, int end, int caret, int selectionLeft, int selectionRight) { }

    private GlauxComposerViewport() { }

    static Window layout(String text, int cursor, int selectionEnd, int previousStart, int width, Metrics metrics) {
        int usable = Math.max(0, width - 1); // Keep the one-pixel caret inside the composer.
        cursor = boundary(text, cursor);
        selectionEnd = boundary(text, selectionEnd);
        int start = Math.min(cursor, boundary(text, previousStart));
        String visible = metrics.trim(text.substring(start), usable, false);
        if (cursor > start + visible.length()) {
            String beforeCaret = metrics.trim(text.substring(0, cursor), usable, true);
            start = cursor - beforeCaret.length();
            visible = metrics.trim(text.substring(start), usable, false);
        }
        int end = start + visible.length();
        int caret = metrics.width(text.substring(start, cursor));
        int anchor = metrics.width(text.substring(start, Math.max(start, Math.min(end, selectionEnd))));
        return new Window(visible, start, end, caret, Math.min(caret, anchor), Math.max(caret, anchor));
    }

    private static int boundary(String text, int index) {
        index = Math.max(0, Math.min(text.length(), index));
        if (index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) index--;
        return index;
    }
}
