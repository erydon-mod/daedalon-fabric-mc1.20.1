package com.oliver.daedalon.client.glaux.interaction;

import java.util.HashSet;
import java.util.Set;

/** Keyboard ownership without a Screen or unlocked mouse. No Minecraft types. */
public final class GlauxInputState {
    public static final int RELEASE = 0;
    public static final int PRESS = 1;
    public static final int REPEAT = 2;
    public static final int MOUSE_BASE = 0x20000;
    private final Set<Integer> physical = new HashSet<>();
    private final Set<Integer> quarantined = new HashSet<>();
    private boolean focused;

    /** Observe every transition, including releases which never reach vanilla. */
    public boolean observe(int token, int action) {
        if (action == RELEASE) {
            physical.remove(token);
            boolean blocked = quarantined.remove(token);
            return focused || blocked;
        }
        // A repeat after a lost focus/release callback is never a fresh game press.
        if (action == REPEAT && !physical.contains(token)) {
            quarantined.add(token);
            return true;
        }
        if (action == PRESS) physical.add(token);
        if (quarantined.contains(token)) return true;
        if (focused) {
            quarantined.add(token);
            return true;
        }
        return false;
    }

    public void begin() {
        focused = true;
        quarantined.addAll(physical);
    }

    public void end() {
        quarantined.addAll(physical);
        focused = false;
    }

    /** A polled physical release may be needed after alt-tab lost callbacks. */
    public void releaseIfUp(int token) {
        physical.remove(token);
        quarantined.remove(token);
    }

    public boolean focused() { return focused; }
    public boolean isPhysicalDown(int token) { return physical.contains(token); }
    public boolean isQuarantined(int token) { return quarantined.contains(token); }
    public Set<Integer> blockedTokens() { return Set.copyOf(quarantined); }
    public Set<Integer> physicalTokens() { return Set.copyOf(physical); }
    public static int mouse(int button) { return MOUSE_BASE + button; }
}
