package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GlauxComposerViewportTest {
    private static final GlauxComposerViewport.Metrics MONOSPACE = new GlauxComposerViewport.Metrics() {
        @Override public int width(String value) { return value.codePointCount(0, value.length()) * 6; }
        @Override public String trim(String value, int width, boolean backwards) {
            int count = Math.min(value.codePointCount(0, value.length()), Math.max(0, width / 6));
            return backwards ? value.substring(value.offsetByCodePoints(value.length(), -count))
                    : value.substring(0, value.offsetByCodePoints(0, count));
        }
    };

    @Test void longDraftScrollsToCaretWithoutLosingOrChangingTheDraft() {
        String text = "How can I rotate this lantern?";
        var end = GlauxComposerViewport.layout(text, text.length(), text.length(), 0, 61, MONOSPACE);
        assertEquals("s lantern?", end.text());
        assertEquals(text.length(), end.end());
        assertTrue(end.caret() < 61);
        var home = GlauxComposerViewport.layout(text, 0, 0, end.start(), 61, MONOSPACE);
        assertEquals("How can I ", home.text());
        assertEquals(0, home.caret());
    }

    @Test void selectionIsClippedToVisibleTextInEitherDirection() {
        String text = "abcdefghijklmno";
        var backwards = GlauxComposerViewport.layout(text, 0, text.length(), 0, 31, MONOSPACE);
        assertEquals(0, backwards.selectionLeft());
        assertEquals(30, backwards.selectionRight());
        var forwards = GlauxComposerViewport.layout(text, text.length(), 0, 0, 31, MONOSPACE);
        assertEquals(0, forwards.selectionLeft());
        assertEquals(30, forwards.selectionRight());
    }

    @Test void resizingAndSupplementaryCharactersKeepTheCaretInsideTheField() {
        String text = "oak 🦉 bronze";
        var wide = GlauxComposerViewport.layout(text, text.length(), text.length(), 0, 121, MONOSPACE);
        assertEquals(text, wide.text());
        var narrow = GlauxComposerViewport.layout(text, text.length(), text.length(), wide.start(), 25, MONOSPACE);
        assertEquals("onze", narrow.text());
        assertEquals(24, narrow.caret());
        var owl = GlauxComposerViewport.layout(text, 6, 6, 5, 25, MONOSPACE);
        assertFalse(owl.text().isEmpty());
        assertFalse(Character.isLowSurrogate(owl.text().charAt(0)));
        assertTrue(owl.caret() < 25);
    }

    @Test void veryNarrowFieldCanStillDisplayAClippedCaret() {
        var tiny = GlauxComposerViewport.layout("owl", 3, 3, 0, 1, MONOSPACE);
        assertEquals("", tiny.text());
        assertEquals(0, tiny.caret());
    }
}
