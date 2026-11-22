package com.oliver.daedalon.client.glaux.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxFocusStateTest {
    @Test void passingCrosshairDoesNotAcquireKeyboard() {
        GlauxFocusState state = new GlauxFocusState();
        state.tick(1L, 0, false);
        assertNull(state.hint(199));
        assertEquals(1L, state.hint(200));
        assertNull(state.active());
    }

    @Test void briefGazeJitterKeepsOneLockedOwl() {
        GlauxFocusState state = new GlauxFocusState();
        state.activate(1L, 0);
        assertFalse(state.tick(2L, 100, false));
        assertEquals(1L, state.active());
        assertFalse(state.tick(1L, 200, false));
        assertFalse(state.tick(null, 250, false));
        assertFalse(state.tick(null, 499, false));
        assertTrue(state.tick(null, 500, false));
        assertNull(state.active());
    }

    @Test void pinnedAccessibilityFocusDoesNotDemandCrosshair() {
        GlauxFocusState state = new GlauxFocusState();
        state.activate(4L, 0);
        assertFalse(state.tick(null, 100, true));
        assertFalse(state.tick(null, 900, true));
        assertEquals(4L, state.active());
        state.release(true);
        assertNull(state.active());
    }

    @Test void escapeDoesNotImmediatelyReopenHintOnSameOwl() {
        GlauxFocusState state = new GlauxFocusState();
        state.activate(1L, 0); state.release(true);
        state.tick(1L, 300, false);
        assertNull(state.hint(900));
        state.tick(null, 1000, false);
        state.tick(1L, 1100, false);
        assertEquals(1L, state.hint(1300));
    }

    @Test void memorySessionsHaveBoundedLruAndWorldReset() {
        GlauxSessionCache<String> cache = new GlauxSessionCache<>(2);
        cache.get(1L, () -> "first draft");
        cache.get(2L, () -> "second draft");
        assertEquals("first draft", cache.get(1L, () -> "wrong"));
        cache.get(3L, () -> "third draft");
        assertNull(cache.peek(2L));
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }
}
