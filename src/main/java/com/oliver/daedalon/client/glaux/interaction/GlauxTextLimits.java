package com.oliver.daedalon.client.glaux.interaction;

/** UI capacity counts UTF-16 like Minecraft, without splitting a code point. */
final class GlauxTextLimits {
    private GlauxTextLimits() { }
    static String prefix(String text, int capacity) {
        if (capacity <= 0) return "";
        int end = Math.min(capacity, text.length());
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
}
