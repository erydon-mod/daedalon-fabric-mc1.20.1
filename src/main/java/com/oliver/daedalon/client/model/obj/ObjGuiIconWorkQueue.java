package com.oliver.daedalon.client.model.obj;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/** One worker, bounded outstanding requests, and explicit ownership across resource reloads. */
final class ObjGuiIconWorkQueue<K, V extends AutoCloseable> {
    record Completed<K, V>(K key, V value, Exception failure) {}
    private record Request<K, V>(K key, Callable<V> work, long generation) {}

    private final Executor executor;
    private final int capacity;
    private final Set<K> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Request<K, V>> requests = new ArrayDeque<>();
    private final ArrayDeque<Completed<K, V>> completed = new ArrayDeque<>();
    private long generation;
    private boolean running;

    ObjGuiIconWorkQueue(Executor executor, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.executor = executor;
        this.capacity = capacity;
    }

    synchronized boolean request(K key, Callable<V> work) {
        if (pending.contains(key)) return true;
        if (pending.size() >= capacity) return false;
        pending.add(key);
        requests.addLast(new Request<>(key, work, generation));
        if (!running) {
            running = true;
            executor.execute(this::run);
        }
        return true;
    }

    synchronized Completed<K, V> poll() {
        Completed<K, V> result = completed.pollFirst();
        if (result != null) pending.remove(result.key());
        return result;
    }

    synchronized int pendingCount() { return pending.size(); }

    synchronized void reset() {
        generation++;
        requests.clear();
        pending.clear();
        Completed<K, V> result;
        while ((result = completed.pollFirst()) != null) close(result.value());
        // The one running request closes its result when it observes the new generation.
    }

    private void run() {
        while (true) {
            Request<K, V> request;
            synchronized (this) {
                request = requests.pollFirst();
                if (request == null) {
                    running = false;
                    return;
                }
            }
            V value = null;
            Exception failure = null;
            try {
                value = request.work().call();
            } catch (Exception exception) {
                failure = exception;
            }
            synchronized (this) {
                if (request.generation() == generation) {
                    completed.addLast(new Completed<>(request.key(), value, failure));
                } else {
                    close(value);
                }
            }
        }
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try {
            value.close();
        } catch (Exception ignored) {
            // A failed/stale result must never stop the worker from accepting the next generation.
        }
    }
}
