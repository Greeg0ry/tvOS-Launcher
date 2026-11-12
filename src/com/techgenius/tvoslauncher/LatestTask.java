package com.techgenius.tvoslauncher;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Keeps one pending task and delivers only the current generation, disposing obsolete results. */
final class LatestTask<T> {
    private final Executor worker, delivery;
    private final Consumer<T> discard;
    private long generation;
    private boolean working;
    private Job<T> pending;

    private static final class Job<T> {
        final long generation;
        final Callable<T> load;
        final Consumer<T> accept;
        Job(long generation, Callable<T> load, Consumer<T> accept) {
            this.generation = generation; this.load = load; this.accept = accept;
        }
    }

    LatestTask(Executor worker, Executor delivery, Consumer<T> discard) {
        this.worker = worker; this.delivery = delivery; this.discard = discard;
    }

    synchronized void submit(Callable<T> load, Consumer<T> accept) {
        pending = new Job<>(++generation, load, accept);
        if (working) return;
        working = true;
        try { worker.execute(this::drain); }
        catch (RejectedExecutionException e) { working = false; pending = null; }
    }

    synchronized void cancel() { generation++; pending = null; }

    private void drain() {
        for (;;) {
            Job<T> job;
            synchronized (this) {
                job = pending;
                pending = null;
                if (job == null) { working = false; return; }
            }
            T loaded = null;
            try { loaded = job.load.call(); } catch (Exception | OutOfMemoryError ignored) { }
            final T value = loaded;
            delivery.execute(() -> {
                synchronized (LatestTask.this) {
                    if (generation == job.generation) job.accept.accept(value);
                    else if (value != null) discard.accept(value);
                }
            });
        }
    }
}
