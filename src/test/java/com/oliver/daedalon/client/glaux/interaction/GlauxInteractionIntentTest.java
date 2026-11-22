package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxInteractionIntentTest {
    @Test void ordinaryUseCanDeliberatelyOpenTheConversation() {
        assertTrue(GlauxInteractionIntent.useMayFocus(false, false));
    }

    @Test void sneakingOrDebugStickUseKeepsVanillaToolAndPlacementControl() {
        assertFalse(GlauxInteractionIntent.useMayFocus(true, false));
        assertFalse(GlauxInteractionIntent.useMayFocus(false, true));
        assertFalse(GlauxInteractionIntent.useMayFocus(true, true));
    }
}
