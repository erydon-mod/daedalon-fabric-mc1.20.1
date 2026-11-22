package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxInputStateTest {
    private static final int W = 87, ENTER = 257, ONE = 49;

    @Test void hotbarAndMovementAreUntouchedWithoutDeliberateFocus() {
        GlauxInputState state = new GlauxInputState();
        assertFalse(state.observe(W, GlauxInputState.PRESS));
        assertFalse(state.observe(ONE, GlauxInputState.PRESS));
        assertFalse(state.observe(ONE, GlauxInputState.REPEAT));
        assertFalse(state.observe(ONE, GlauxInputState.RELEASE));
    }

    @Test void walkingKeyCannotResumeUntilReleasedAfterTyping() {
        GlauxInputState state = new GlauxInputState();
        state.observe(W, GlauxInputState.PRESS);
        state.begin();
        assertTrue(state.observe(W, GlauxInputState.REPEAT));
        state.end();
        assertTrue(state.observe(W, GlauxInputState.REPEAT));
        assertTrue(state.observe(W, GlauxInputState.PRESS));
        assertTrue(state.observe(W, GlauxInputState.RELEASE));
        assertFalse(state.isQuarantined(W));
        assertFalse(state.observe(W, GlauxInputState.PRESS));
    }

    @Test void heldMouseAndTypedNumberShareReleaseGuard() {
        GlauxInputState state = new GlauxInputState();
        int right = GlauxInputState.mouse(1);
        state.observe(right, GlauxInputState.PRESS);
        state.begin();
        assertTrue(state.observe(ONE, GlauxInputState.PRESS));
        state.end();
        assertTrue(state.isQuarantined(right));
        assertTrue(state.isQuarantined(ONE));
        state.observe(right, GlauxInputState.RELEASE);
        state.observe(ONE, GlauxInputState.RELEASE);
        assertFalse(state.observe(right, GlauxInputState.PRESS));
        assertFalse(state.observe(ONE, GlauxInputState.PRESS));
    }

    @Test void releaseWhileComposingIsObservedDespiteCancellation() {
        GlauxInputState state = new GlauxInputState();
        state.begin();
        state.observe(W, GlauxInputState.PRESS);
        assertTrue(state.observe(W, GlauxInputState.RELEASE));
        state.end();
        assertFalse(state.isQuarantined(W));
        assertFalse(state.observe(W, GlauxInputState.PRESS));
    }

    @Test void enterRepeatAndDuplicatePressHaveOnlyOneFreshEdge() {
        GlauxInputState state = new GlauxInputState();
        state.begin();
        int submissions = 0;
        for (int action : new int[]{GlauxInputState.PRESS, GlauxInputState.REPEAT, GlauxInputState.REPEAT, GlauxInputState.PRESS}) {
            if (action == GlauxInputState.PRESS && !state.isPhysicalDown(ENTER)) submissions++;
            assertTrue(state.observe(ENTER, action));
        }
        assertEquals(1, submissions);
        state.observe(ENTER, GlauxInputState.RELEASE);
        assertFalse(state.isPhysicalDown(ENTER));
        assertTrue(state.observe(ENTER, GlauxInputState.PRESS));
    }

    @Test void missingAltTabReleaseCanBeReconciledFromPhysicalState() {
        GlauxInputState state = new GlauxInputState();
        state.begin(); state.observe(W, GlauxInputState.PRESS); state.end();
        assertTrue(state.isQuarantined(W));
        state.releaseIfUp(W);
        assertFalse(state.isQuarantined(W));
        assertFalse(state.observe(W, GlauxInputState.PRESS));
    }

    @Test void orphanRepeatAfterWindowFocusCannotStartMovement() {
        GlauxInputState state = new GlauxInputState();
        assertTrue(state.observe(W, GlauxInputState.REPEAT));
        assertTrue(state.isQuarantined(W));
        state.observe(W, GlauxInputState.RELEASE);
        assertFalse(state.observe(W, GlauxInputState.PRESS));
    }
}
