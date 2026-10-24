package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class ObjGuiIconWorkQueueTest {
    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.addLast(task); }
        void run() { tasks.removeFirst().run(); }
    }

    private static final class Image implements AutoCloseable {
        int closes;
        @Override public void close() { closes++; }
    }

    @Test void drawRequestsAreLazyIdentityDeduplicatedAndBoundedUntilUpload() {
        ManualExecutor executor = new ManualExecutor();
        var queue = new ObjGuiIconWorkQueue<Object, Image>(executor, 2);
        AtomicInteger generated = new AtomicInteger();
        Object first = new Object(), second = new Object(), third = new Object();
        assertTrue(queue.request(first, () -> { generated.incrementAndGet(); return new Image(); }));
        assertTrue(queue.request(first, () -> { fail("Duplicate draw regenerated an icon"); return null; }));
        assertTrue(queue.request(second, Image::new));
        assertFalse(queue.request(third, Image::new));
        assertEquals(0, generated.get(), "An item draw must never run CPU colour work");
        assertEquals(1, executor.tasks.size(), "One worker services the queue");
        executor.run();
        assertEquals(1, generated.get());
        assertEquals(2, queue.pendingCount(), "Completed images also count against the memory bound");
        assertFalse(queue.request(third, Image::new));
        var completed = queue.poll();
        assertSame(first, completed.key());
        assertEquals(0, completed.value().closes, "Polling transfers image ownership to the upload batch");
        assertTrue(queue.request(third, Image::new));
        completed.value().close();
        assertEquals(1, completed.value().closes);
        queue.reset();
    }

    @Test void reloadClosesCompletedImagesAndDiscardsUnstartedWork() {
        ManualExecutor executor = new ManualExecutor();
        var queue = new ObjGuiIconWorkQueue<Object, Image>(executor, 3);
        Image ready = new Image();
        queue.request(new Object(), () -> ready);
        executor.run();
        queue.request(new Object(), () -> { fail("Reloaded queued work executed"); return null; });
        queue.reset();
        executor.run();
        assertEquals(1, ready.closes);
        assertEquals(0, queue.pendingCount());
        assertNull(queue.poll());
    }

    @Test void imageFinishingAcrossReloadIsClosedAndNewGenerationStillRuns() {
        ManualExecutor executor = new ManualExecutor();
        var queue = new ObjGuiIconWorkQueue<Object, Image>(executor, 2);
        Image stale = new Image(), current = new Image();
        Object key = new Object();
        queue.request(key, () -> {
            // A deterministic interleaving of reload while one worker owns an image.
            queue.reset();
            assertTrue(queue.request(key, () -> current));
            return stale;
        });
        executor.run();
        assertEquals(1, stale.closes);
        var completed = queue.poll();
        assertSame(current, completed.value());
        assertNull(completed.failure());
        assertEquals(0, current.closes);
        assertNull(queue.poll());
        current.close();
        queue.reset();
        assertEquals(1, current.closes, "Reload must not close an image already transferred to the uploader");
    }

    @Test void aBadTextureReportsFailureWithoutStoppingOtherIcons() {
        ManualExecutor executor = new ManualExecutor();
        var queue = new ObjGuiIconWorkQueue<Object, Image>(executor, 2);
        Object bad = new Object(), good = new Object();
        queue.request(bad, () -> { throw new IOException("Missing texture"); });
        queue.request(good, Image::new);
        executor.run();
        var failure = queue.poll();
        assertSame(bad, failure.key());
        assertInstanceOf(IOException.class, failure.failure());
        assertNull(failure.value());
        var image = queue.poll();
        assertSame(good, image.key());
        image.value().close();
        assertEquals(0, queue.pendingCount());
    }
}
