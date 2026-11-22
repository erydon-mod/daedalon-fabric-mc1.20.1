package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ObjTerrainFailureReporterTest {
    @Test
    void onlyTheFirstFailureIsReportedAndEveryCallerKeepsItsOriginalError() {
        var reporter = new ObjTerrainFailureReporter();
        var calls = new AtomicInteger();
        var first = new OutOfMemoryError("first native allocation");
        var second = new OutOfMemoryError("another worker");
        assertSame(first, reporter.report(first, calls::incrementAndGet));
        assertSame(second, reporter.report(second, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void anAllocationFailureWhileReportingNeverReplacesTheOriginal() {
        var reporter = new ObjTerrainFailureReporter();
        var original = new OutOfMemoryError("terrain buffer");
        assertSame(original, reporter.report(original, () -> { throw new OutOfMemoryError("log formatter"); }));
        assertSame(original, reporter.report(original, () -> { throw new AssertionError("must not retry"); }));
    }
}
