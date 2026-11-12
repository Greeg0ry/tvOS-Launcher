package com.techgenius.tvoslauncher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Deterministic checks of the same scheduling and cache modules used by the launcher. */
public final class PerformanceChecks {
    private static int assertions;
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    public static void main(String[] args) {
        WeightedLruCache<Integer, Long> images = new WeightedLruCache<>(24L << 20, bytes -> bytes);
        long fullHd = 1920L * 1080 * 4;
        for (int i = 0; i < 20; i++) images.put(i, fullHd);
        equal(3 * fullHd, images.weight());
        equal(null == images.get(0), true);
        equal(fullHd, images.get(19));
        images.get(17); images.put(20, fullHd);
        equal(true, images.get(18) == null);
        equal(fullHd, images.get(17));
        images.put(21, 100L << 20);
        equal(true, images.get(21) == null);
        Long borrowed = images.get(19);
        images.clear();
        equal(0L, images.weight());
        equal(fullHd, borrowed);

        Queue background = new Queue(), delivery = new Queue();
        List<String> accepted = new ArrayList<>(), discarded = new ArrayList<>();
        LatestTask<String> latest = new LatestTask<>(background, delivery, discarded::add);
        latest.submit(() -> "old", accepted::add); background.runAll();
        latest.submit(() -> "new", accepted::add); background.runAll(); delivery.runAll();
        equal(List.of("new"), accepted); equal(List.of("old"), discarded);
        latest.submit(() -> "cancelled", accepted::add); latest.cancel();
        background.runAll(); delivery.runAll(); equal(List.of("new"), accepted);
        latest.submit(() -> "obsolete", accepted::add);
        latest.submit(() -> "selected", accepted::add);
        equal(1, background.tasks.size()); background.runAll(); delivery.runAll();
        equal(List.of("new", "selected"), accepted);
        latest.submit(() -> "completed before cancel", accepted::add); background.runAll();
        latest.cancel(); delivery.runAll();
        equal(List.of("new", "selected"), accepted);
        equal(List.of("old", "completed before cancel"), discarded);
        latest.submit(() -> { throw new IllegalStateException("decode failed"); }, value -> equal(true, value == null));
        background.runAll(); delivery.runAll();
        latest.submit(() -> "recovered", accepted::add); background.runAll(); delivery.runAll();
        equal(List.of("new", "selected", "recovered"), accepted);

        equal(0L, VolumePollPolicy.delayMillis(true, 1, false));
        equal(0L, VolumePollPolicy.delayMillis(true, 3, false));
        equal(400L, VolumePollPolicy.delayMillis(false, 1, false));
        equal(400L, VolumePollPolicy.delayMillis(true, 2, false));
        equal(45L, VolumePollPolicy.delayMillis(true, 1, true));
        equal(10L, VolumePollPolicy.delayMillis(true, 3, true));
        equal(false, VolumePollPolicy.needsAudioDump(true, 1));
        equal(true, VolumePollPolicy.needsAudioDump(true, 2));
        System.out.println("PASS: " + assertions + " performance behavior checks");
    }
}
