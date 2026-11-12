package com.techgenius.tvoslauncher;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Bounds retained references by their cost; eviction never destroys values owned by a caller. */
final class WeightedLruCache<K, V> {
    private final long maxWeight;
    private final ToLongFunction<V> weigh;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long weight;

    private static final class Entry<V> {
        final V value;
        final long weight;
        Entry(V value, long weight) { this.value = value; this.weight = weight; }
    }

    WeightedLruCache(long maxWeight, ToLongFunction<V> weigh) {
        if (maxWeight <= 0) throw new IllegalArgumentException("Cache budget must be positive");
        this.maxWeight = maxWeight;
        this.weigh = weigh;
    }

    synchronized V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    synchronized void put(K key, V value) {
        remove(key);
        long cost = Math.max(0, weigh.applyAsLong(value));
        if (cost > maxWeight) return;
        entries.put(key, new Entry<>(value, cost));
        weight += cost;
        trimToSize(maxWeight);
    }

    synchronized void remove(K key) {
        Entry<V> old = entries.remove(key);
        if (old != null) weight -= old.weight;
    }

    synchronized void trimToSize(long target) {
        Iterator<Map.Entry<K, Entry<V>>> it = entries.entrySet().iterator();
        while (weight > Math.max(0, target) && it.hasNext()) {
            weight -= it.next().getValue().weight;
            it.remove();
        }
    }

    synchronized void clear() { entries.clear(); weight = 0; }
    synchronized long weight() { return weight; }
}
