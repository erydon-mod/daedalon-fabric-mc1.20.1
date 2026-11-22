package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxTextLimitsTest {
    @Test void pasteAtCapacityDoesNotCreateHalfOfAnEmoji() {
        String question = "a".repeat(1999) + "🦉";
        assertEquals("a".repeat(1999), GlauxTextLimits.prefix(question, 2000));
        assertEquals("", GlauxTextLimits.prefix("🦉", 1));
    }

    @Test void completeUnicodeAndPunctuationRemainExactWhenTheyFit() {
        String question = "屋根 — not Gothic? 🦉";
        assertEquals(question, GlauxTextLimits.prefix(question, 2000));
        assertEquals("a".repeat(1998) + "🦉", GlauxTextLimits.prefix("a".repeat(1998) + "🦉!", 2000));
    }
}
