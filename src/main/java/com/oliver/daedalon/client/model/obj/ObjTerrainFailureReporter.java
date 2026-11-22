package com.oliver.daedalon.client.model.obj;

import java.util.concurrent.atomic.AtomicBoolean;

/** A failed diagnostic must never replace the original allocation failure. */
final class ObjTerrainFailureReporter {
    private final AtomicBoolean reported = new AtomicBoolean();

    OutOfMemoryError report(OutOfMemoryError original, Runnable diagnostic) {
        if (reported.compareAndSet(false, true)) {
            try {
                diagnostic.run();
            } catch (Throwable ignored) {
                // Log formatting and native error handling may both be out of memory.
            }
        }
        return original;
    }
}
