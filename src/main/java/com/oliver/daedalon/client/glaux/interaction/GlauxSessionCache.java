package com.oliver.daedalon.client.glaux.interaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Per-world, memory-only LRU. Drafts cannot grow with every placed owl. */
public final class GlauxSessionCache<T> {
    private final int capacity;
    private final Map<Long,T> entries = new LinkedHashMap<>(16, .75f, true);
    public GlauxSessionCache(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    public T get(long pos, Supplier<T> create) {
        T value = entries.computeIfAbsent(pos, ignored -> create.get());
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        return value;
    }
    public T peek(long pos) { return entries.get(pos); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
}
