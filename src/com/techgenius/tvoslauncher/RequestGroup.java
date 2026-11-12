package com.techgenius.tvoslauncher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Shares in-flight work by key and backs off failures; null means no fresh data. */
final class RequestGroup<T> {
    private final Executor worker, delivery;
    private final LongSupplier clock;
    private final long retryDelay;
    private final Map<String, List<Consumer<T>>> pending = new HashMap<>();
    private final LinkedHashMap<String, Long> failures = new LinkedHashMap<>();

    RequestGroup(Executor worker, Executor delivery, LongSupplier clock, long retryDelay) {
        this.worker = worker;
        this.delivery = delivery;
        this.clock = clock;
        this.retryDelay = retryDelay;
    }

    void request(String key, boolean force, Callable<T> load, Consumer<T> callback) {
        synchronized (this) {
            List<Consumer<T>> listeners = pending.get(key);
            if (listeners != null) {
                if (callback != null) listeners.add(callback);
                return;
            }
            Long until = failures.get(key);
            if (!force && until != null && clock.getAsLong() < until) {
                if (callback != null) delivery.execute(() -> callback.accept(null));
                return;
            }
            listeners = new ArrayList<>();
            if (callback != null) listeners.add(callback);
            pending.put(key, listeners);
        }
        try {
            worker.execute(() -> {
                T value = null;
                try { value = load.call(); } catch (Exception ignored) { }
                finish(key, value);
            });
        } catch (RejectedExecutionException e) {
            finish(key, null);
        }
    }

    private void finish(String key, T value) {
        List<Consumer<T>> listeners;
        synchronized (this) {
            listeners = pending.remove(key);
            if (value == null) {
                failures.put(key, clock.getAsLong() + retryDelay);
                while (failures.size() > 64) failures.remove(failures.keySet().iterator().next());
            } else failures.remove(key);
        }
        for (Consumer<T> callback : listeners) delivery.execute(() -> callback.accept(value));
    }
}
