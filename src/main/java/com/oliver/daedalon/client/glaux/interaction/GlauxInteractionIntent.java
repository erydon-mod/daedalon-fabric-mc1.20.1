package com.oliver.daedalon.client.glaux.interaction;

/** Vanilla placement and debug-stick intent takes precedence over speaking via Use. */
final class GlauxInteractionIntent {
    private GlauxInteractionIntent() { }

    static boolean useMayFocus(boolean sneaking, boolean holdingDebugStick) {
        return !sneaking && !holdingDebugStick;
    }
}
