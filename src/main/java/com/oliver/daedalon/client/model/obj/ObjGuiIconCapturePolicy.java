package com.oliver.daedalon.client.model.obj;

/** A one-shot external cache must wait for upload, but must not retry terminal fallbacks forever. */
final class ObjGuiIconCapturePolicy {
    private ObjGuiIconCapturePolicy() { }

    static boolean shouldWait(boolean ready, boolean unavailable, Runnable request) {
        if (ready || unavailable) return false;
        request.run();
        return true;
    }
}
